package orbital.net.events;

import java.io.*;
import java.util.zip.CRC32;

/**
 * Wire and on-disk format of the event log.
 *
 * <p>Everything is varint encoded: a typical event (small seq/tick deltas, one id, a handful of
 * bytes) fits in ~10-20 bytes, versus the full-state snapshots the legacy protocol sends 5 times a
 * second. A CRC32 closes each record so a truncated or half-written append is detected instead of
 * replayed as a valid event.</p>
 *
 * <p>Record layout on disk / on the wire:</p>
 * <pre>
 *   varint  body length (everything after this field, including the CRC)
 *   varint  seq (absolute)
 *   varint  tick
 *   zigzag  playerId
 *   varint  typeId
 *   varint  payload length
 *   bytes   payload
 *   u32     CRC32 of all bytes from seq through payload
 * </pre>
 */
public final class EventCodec{
    /** Magic bytes at the start of every log segment. */
    public static final byte[] MAGIC = {'O', 'R', 'B', 'E', 'V', '1'};
    /** Bumped when the record layout changes; readers must refuse older/newer versions. */
    public static final int VERSION = 1;

    private EventCodec(){
    }

    /** Writes the segment header. Call once at the beginning of each file. */
    public static void writeHeader(DataOutput out) throws IOException{
        out.write(MAGIC);
        out.writeByte(VERSION);
    }

    /** Reads and validates a segment header. */
    public static void readHeader(DataInput in) throws IOException{
        byte[] magic = new byte[MAGIC.length];
        in.readFully(magic);
        for(int i = 0; i < MAGIC.length; i++){
            if(magic[i] != MAGIC[i]) throw new IOException("not an Orbital event log");
        }
        int version = in.readUnsignedByte();
        if(version != VERSION) throw new IOException("unsupported event log version: " + version);
    }

    /** Writes one record, length-prefixed and CRC protected. */
    public static void write(DataOutput out, EventRecord event) throws IOException{
        byte[] body = body(event);
        writeVarInt(out, body.length);
        out.write(body);
    }

    /** Reads one record. Returns null at a clean end of stream. */
    public static EventRecord read(DataInput in) throws IOException{
        long length;
        try{
            length = readVarInt(in);
        }catch(EOFException end){
            return null;
        }
        if(length < 0 || length > Integer.MAX_VALUE) throw new IOException("invalid event length: " + length);
        byte[] body = new byte[(int)length];
        in.readFully(body);

        CRC32 crc = new CRC32();
        crc.update(body, 0, body.length - 4);
        long stored = readU32(body, body.length - 4);
        if(stored != crc.getValue()) throw new IOException("event CRC mismatch at seq " + "?");

        ByteArrayInputStream bytes = new ByteArrayInputStream(body, 0, body.length - 4);
        DataInputStream data = new DataInputStream(bytes);
        long seq = readVarInt(data);
        long tick = readVarInt(data);
        int playerId = (int)readZigZag(data);
        int typeId = (int)readVarInt(data);
        int payloadLength = (int)readVarInt(data);
        if(payloadLength < 0 || payloadLength > bytes.available()) throw new IOException("invalid event payload length");
        byte[] payload = new byte[payloadLength];
        data.readFully(payload);
        return new EventRecord(seq, tick, playerId, typeId, payload);
    }

    private static byte[] body(EventRecord event) throws IOException{
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(bytes);
        writeVarInt(out, event.seq());
        writeVarInt(out, event.tick());
        writeZigZag(out, event.playerId());
        writeVarInt(out, event.typeId());
        byte[] payload = event.payload();
        writeVarInt(out, payload.length);
        out.write(payload);

        byte[] content = bytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(content, 0, content.length);
        byte[] result = new byte[content.length + 4];
        System.arraycopy(content, 0, result, 0, content.length);
        writeU32(result, content.length, crc.getValue());
        return result;
    }

    /** Unsigned LEB128. At most 10 bytes for a long. */
    public static void writeVarInt(DataOutput out, long value) throws IOException{
        long v = value;
        while((v & ~0x7FL) != 0){
            out.writeByte((int)(v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte((int)v);
    }

    public static long readVarInt(DataInput in) throws IOException{
        long result = 0;
        int shift = 0;
        while(true){
            int b = in.readUnsignedByte();
            result |= (long)(b & 0x7F) << shift;
            if((b & 0x80) == 0) return result;
            shift += 7;
            if(shift >= 70) throw new IOException("varint too long");
        }
    }

    /** Signed values with small magnitudes cost one byte. */
    public static void writeZigZag(DataOutput out, long value) throws IOException{
        writeVarInt(out, (value << 1) ^ (value >> 63));
    }

    public static long readZigZag(DataInput in) throws IOException{
        long n = readVarInt(in);
        return (n >>> 1) ^ -(n & 1);
    }

    private static long readU32(byte[] bytes, int offset){
        return ((long)(bytes[offset] & 0xFF))
            | ((long)(bytes[offset + 1] & 0xFF) << 8)
            | ((long)(bytes[offset + 2] & 0xFF) << 16)
            | ((long)(bytes[offset + 3] & 0xFF) << 24);
    }

    private static void writeU32(byte[] bytes, int offset, long value){
        bytes[offset] = (byte)value;
        bytes[offset + 1] = (byte)(value >>> 8);
        bytes[offset + 2] = (byte)(value >>> 16);
        bytes[offset + 3] = (byte)(value >>> 24);
    }
}
