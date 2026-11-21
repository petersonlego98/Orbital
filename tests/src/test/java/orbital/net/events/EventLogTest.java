package orbital.net.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Codec and append-only store round trips, including recovery and corruption detection. */
class EventLogTest{
    @TempDir Path temp;

    @Test void varintsRoundTrip() throws IOException{
        long[] values = {0, 1, 127, 128, 255, 256, 16_383, 16_384, Integer.MAX_VALUE,
            (long)Integer.MAX_VALUE + 1, Long.MAX_VALUE};
        for(long value : values){
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(bytes);
            EventCodec.writeVarInt(out, value);
            DataInput in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(value, EventCodec.readVarInt(in), "varint " + value);
        }
    }

    @Test void zigzagRoundTrip() throws IOException{
        long[] values = {0, -1, 1, -2, 2, -1000, 1000, Long.MIN_VALUE, Long.MAX_VALUE};
        for(long value : values){
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(bytes);
            EventCodec.writeZigZag(out, value);
            DataInput in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(value, EventCodec.readZigZag(in), "zigzag " + value);
        }
    }

    @Test void recordRoundTripPreservesPayload() throws IOException{
        EventRecord original = new EventRecord(42, 1234, 7, EventTypeIds.BUILD_END,
            new byte[]{1, 2, 3, (byte)255});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(bytes);
        EventCodec.write(out, original);
        DataInput in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(original, EventCodec.read(in));
    }

    @Test void recordRejectsCorruption() throws IOException{
        EventRecord original = new EventRecord(1, 5, -1, EventTypeIds.WAVE, new byte[]{9, 8, 7});
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(bytes);
        EventCodec.write(out, original);
        byte[] data = bytes.toByteArray();
        data[data.length - 1] ^= 0x20; //flip a CRC byte
        DataInput in = new DataInputStream(new ByteArrayInputStream(data));
        assertThrows(IOException.class, () -> EventCodec.read(in));
    }

    @Test void storeAppendsReadsAndRecovers() throws IOException{
        Path dir = temp.resolve("events");
        EventStore store = new EventStore(dir, 4096);
        store.open();
        store.append(10, -1, EventTypeIds.SESSION_START, new byte[0]);
        store.append(11, 3, EventTypeIds.CHAT, "oi".getBytes());
        store.append(12, 3, EventTypeIds.BUILD_END, new byte[]{4, 5});
        store.flush();

        List<EventRecord> all = new ArrayList<>();
        store.forEachFrom(0, all::add);
        assertEquals(3, all.size());
        assertEquals(0, all.get(0).seq());
        assertEquals(2, all.get(2).seq());
        assertArrayEquals(new byte[]{4, 5}, all.get(2).payload());

        List<EventRecord> tail = new ArrayList<>();
        store.forEachFrom(2, tail::add);
        assertEquals(1, tail.size());
        assertEquals(2, tail.get(0).seq());

        store.close();

        EventStore reopened = new EventStore(dir, 4096);
        reopened.open();
        assertEquals(3, reopened.nextSeq(), "recovery must continue the sequence");
        assertEquals(12, reopened.lastTick());
        reopened.append(13, -1, EventTypeIds.WAVE, new byte[0]);
        reopened.flush();
        reopened.close();

        EventStore finalStore = new EventStore(dir, 4096);
        finalStore.open();
        List<EventRecord> finalAll = new ArrayList<>();
        finalStore.forEachFrom(0, finalAll::add);
        assertEquals(4, finalAll.size());
        finalStore.close();
    }

    @Test void storeRotatesSegments() throws IOException{
        Path dir = temp.resolve("rotating");
        EventStore store = new EventStore(dir, 1024);
        store.open();
        for(int i = 0; i < 200; i++){
            store.append(i, i % 2, EventTypeIds.UNIT_COMMAND, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        }
        store.flush();
        store.close();

        long files;
        try(var stream = java.nio.file.Files.list(dir)){
            files = stream.count();
        }catch(IOException e){
            throw new AssertionError(e);
        }
        assertTrue(files > 1, "expected more than one segment, got " + files);

        EventStore reopened = new EventStore(dir, 1024);
        reopened.open();
        List<EventRecord> all = new ArrayList<>();
        reopened.forEachFrom(0, all::add);
        assertEquals(200, all.size());
        reopened.close();
    }

    @Test void defensivePayloadCopy() throws IOException{
        byte[] payload = {1, 2, 3};
        EventRecord record = new EventRecord(0, 0, 0, 0, payload);
        payload[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, record.payload());
        byte[] returned = record.payload();
        returned[1] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, record.payload());
    }
}
