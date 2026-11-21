package orbital.net.online;

import orbital.net.events.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Applier must deduplicate, keep order and report gaps. */
class EventApplierTest{
    private static byte[] batch(EventRecord... records){
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(bytes);
        for(EventRecord record : records){
            try{
                EventCodec.write(out, record);
            }catch(IOException e){
                throw new AssertionError(e);
            }
        }
        return bytes.toByteArray();
    }

    @Test void appliesInOrder(){
        EventRecord a = new EventRecord(0, 1, -1, EventTypeIds.WAVE, new byte[]{1});
        EventRecord b = new EventRecord(1, 2, -1, EventTypeIds.WAVE, new byte[]{2});
        List<Integer> seen = new ArrayList<>();
        EventApplier applier = new EventApplier((type, payload) -> seen.add(payload[0] & 0xFF));

        assertEquals(2, applier.apply(batch(a, b)));
        assertEquals(List.of(1, 2), seen);
        assertEquals(1, applier.appliedSeq());
    }

    @Test void dropsDuplicates(){
        EventRecord a = new EventRecord(5, 1, -1, EventTypeIds.WAVE, new byte[0]);
        EventRecord b = new EventRecord(6, 2, -1, EventTypeIds.WAVE, new byte[0]);
        int[] calls = {0};
        EventApplier applier = new EventApplier((type, payload) -> calls[0]++);

        applier.apply(batch(a, b));
        applier.apply(batch(a, b)); //reconnect replay
        assertEquals(2, calls[0], "each event must be applied exactly once");
        assertEquals(2, applier.duplicateCount());
    }

    @Test void keyframeSkipsOldEvents(){
        EventRecord old = new EventRecord(3, 1, -1, EventTypeIds.WAVE, new byte[0]);
        EventRecord newer = new EventRecord(4, 2, -1, EventTypeIds.WAVE, new byte[0]);
        int[] calls = {0};
        EventApplier applier = new EventApplier((type, payload) -> calls[0]++);

        applier.keyframe(3);
        applier.apply(batch(old, newer));
        assertEquals(1, calls[0]);
        assertEquals(4, applier.appliedSeq());
    }

    @Test void resetClearsState(){
        EventRecord a = new EventRecord(0, 1, -1, EventTypeIds.WAVE, new byte[0]);
        EventApplier applier = new EventApplier((type, payload) -> {});
        applier.apply(batch(a));
        applier.reset();
        assertEquals(-1, applier.appliedSeq());
        assertEquals(0, applier.appliedCount());
    }
}
