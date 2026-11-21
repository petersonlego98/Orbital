package orbital.net.online;

import orbital.net.events.EventCodec;

import java.io.*;

/**
 * Player input for one authoritative tick.
 *
 * <p>Only intentions are sent: aim point, movement vector and the shoot/boost/build flags. The
 * server re-simulates movement from these (it never trusts the client's position), then publishes
 * the resulting events. This is the input frame that makes server-side re-simulation possible —
 * the legacy {@code clientSnapshot} sent the resulting position, which is untrusted and not
 * replayable.</p>
 *
 * <p>Wire format (all varint/zigzag): tick delta, aimX, aimY, moveX, moveY, flags byte.
 * ~10-16 bytes per tick — far below a full snapshot.</p>
 */
public record InputCommand(
    long tick,
    float aimX, float aimY,
    float moveX, float moveY,
    boolean shooting, boolean boosting, boolean building
){
    /** Bit flags packed into one byte. */
    private static final int SHOOT = 1, BOOST = 2, BUILD = 4;

    public byte[] encode(){
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(bytes);
        try{
            EventCodec.writeVarInt(out, tick);
            EventCodec.writeZigZag(out, (long)(aimX * 100f));
            EventCodec.writeZigZag(out, (long)(aimY * 100f));
            EventCodec.writeZigZag(out, (long)(moveX * 100f));
            EventCodec.writeZigZag(out, (long)(moveY * 100f));
            int flags = (shooting ? SHOOT : 0) | (boosting ? BOOST : 0) | (building ? BUILD : 0);
            out.writeByte(flags);
        }catch(IOException impossible){
        }
        return bytes.toByteArray();
    }

    public static InputCommand decode(byte[] data) throws IOException{
        DataInput in = new DataInputStream(new ByteArrayInputStream(data));
        long tick = EventCodec.readVarInt(in);
        float aimX = EventCodec.readZigZag(in) / 100f;
        float aimY = EventCodec.readZigZag(in) / 100f;
        float moveX = EventCodec.readZigZag(in) / 100f;
        float moveY = EventCodec.readZigZag(in) / 100f;
        int flags = in.readUnsignedByte();
        return new InputCommand(tick, aimX, aimY, moveX, moveY,
            (flags & SHOOT) != 0, (flags & BOOST) != 0, (flags & BUILD) != 0);
    }
}
