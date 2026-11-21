package orbital.net.online;

import arc.util.*;
import orbital.net.events.*;

import java.io.*;
import java.util.function.BiConsumer;

/**
 * Client side of the authoritative event stream.
 *
 * <p>Receives batched {@link EventRecord}s from the server, drops duplicates (a reconnect can
 * replay events already applied), applies each one in commit order and tracks the last applied
 * {@code seq}. The game layer supplies the actual "apply this event to the world" callback; this
 * class only guarantees ordering, deduplication and acknowledgement, so it stays testable.</p>
 */
public final class EventApplier{
    /** Callback: (typeId, payload) -> apply to the authoritative client world. */
    private final BiConsumer<Integer, byte[]> handler;

    private long appliedSeq = -1;
    private int appliedCount;
    private int duplicateCount;

    public EventApplier(BiConsumer<Integer, byte[]> handler){
        this.handler = handler;
    }

    /** Applies every event in a batch that is newer than the last one applied. */
    public int apply(byte[] batch){
        int count = 0;
        try{
            DataInput in = new DataInputStream(new ByteArrayInputStream(batch));
            while(true){
                EventRecord event = EventCodec.read(in);
                if(event == null) break;
                if(event.seq() <= appliedSeq){
                    duplicateCount++;
                    continue;
                }
                //A gap means a reliable packet was lost or the server restarted its sequence.
                //Log it rather than silently corrupting the world; the caller can request a
                //keyframe/resync.
                if(appliedSeq >= 0 && event.seq() > appliedSeq + 1){
                    Log.warn("[online] event gap: expected @, got @ — resync recommended",
                        appliedSeq + 1, event.seq());
                }
                handler.accept(event.typeId(), event.payload());
                appliedSeq = event.seq();
                appliedCount++;
                count++;
            }
        }catch(IOException e){
            Log.err("[online] could not apply event batch: @", e.getMessage());
        }
        return count;
    }

    /** Called after a keyframe: events up to {@code seq} are already reflected in the world. */
    public void keyframe(long seq){
        appliedSeq = Math.max(appliedSeq, seq);
    }

    public long appliedSeq(){
        return appliedSeq;
    }

    public int appliedCount(){
        return appliedCount;
    }

    public int duplicateCount(){
        return duplicateCount;
    }

    public void reset(){
        appliedSeq = -1;
        appliedCount = 0;
        duplicateCount = 0;
    }
}
