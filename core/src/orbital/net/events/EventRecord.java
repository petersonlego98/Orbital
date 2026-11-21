package orbital.net.events;

import java.util.Arrays;

/**
 * One entry of the authoritative event log.
 *
 * <p>The server owns {@code seq}: it is monotonic across the whole session and is the only cursor a
 * client needs to catch up ({@code "send me everything after seq X"}). {@code tick} is the fixed-step
 * server tick at which the event was committed, so a replay can place it exactly in time. {@code
 * playerId} is -1 for events produced by the server itself (waves, weather, AI).</p>
 *
 * <p>The payload is a compact binary blob whose meaning is fixed by {@link #typeId}; it is kept
 * opaque here so the codec stays independent of game content. The array is copied defensively:
 * a record must never change after it has been appended.</p>
 */
public record EventRecord(long seq, long tick, int playerId, int typeId, byte[] payload){
    public static final int SERVER_SOURCE = -1;

    public EventRecord{
        if(seq < 0 || tick < 0 || typeId < 0) throw new IllegalArgumentException("negative event field");
        payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override public byte[] payload(){
        return payload.clone();
    }

    /** @return true when this event was produced by the server, not by a player. */
    public boolean fromServer(){
        return playerId == SERVER_SOURCE;
    }

    @Override public boolean equals(Object other){
        if(this == other) return true;
        if(!(other instanceof EventRecord e)) return false;
        return seq == e.seq && tick == e.tick && playerId == e.playerId
            && typeId == e.typeId && Arrays.equals(payload, e.payload);
    }

    @Override public int hashCode(){
        int result = Long.hashCode(seq) * 31 + Long.hashCode(tick);
        result = result * 31 + playerId;
        result = result * 31 + typeId;
        return result * 31 + Arrays.hashCode(payload);
    }

    @Override public String toString(){
        return "EventRecord{seq=" + seq + ", tick=" + tick + ", player=" + playerId
            + ", type=" + typeId + ", bytes=" + payload.length + "}";
    }
}
