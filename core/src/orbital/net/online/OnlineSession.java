package orbital.net.online;

import arc.util.*;
import orbital.core.*;
import orbital.gen.Call;
import orbital.net.events.*;

import static orbital.Vars.*;

/**
 * Client-side coordinator for the authoritative online mode.
 *
 * <p>Installs the binary packet handler for the event channel, feeds incoming batches through
 * {@link EventApplier} into the world, and acknowledges applied events so the server can prune.
 * One session per process; created by the client when it connects to an authoritative server.</p>
 */
public final class OnlineSession{
    private final EventApplier applier;
    private final WorldEventApplier world;
    private boolean installed;

    public OnlineSession(@Nullable EventStore store){
        world = new WorldEventApplier(store);
        applier = new EventApplier(world::apply);
    }

    /** Registers the handler once. Must be called after the client exists. */
    public void install(){
        if(installed) return;
        installed = true;
        //Server -> client binary channel (see EventReplicator).
        netClient.addBinaryPacketHandler(EventReplicator.CHANNEL, this::receive);
        //Keyframe payload sent during recovery.
        netClient.addBinaryPacketHandler("orbital.keyframe.world", this::applyKeyframeWorld);
        netClient.getPacketHandlers("orbital.keyframe.response").add(value -> {
            try{
                pendingKeyframeSeq = Long.parseLong(value);
            }catch(NumberFormatException invalid){
                Log.warn("[online] invalid keyframe seq: @", value);
            }
        });
        Log.info("[online] event channel installed");
    }

    private long pendingKeyframeSeq = -1;

    private void applyKeyframeWorld(byte[] worldData){
        if(pendingKeyframeSeq < 0) return;
        applyKeyframe(pendingKeyframeSeq, worldData);
        pendingKeyframeSeq = -1;
    }

    private void receive(byte[] batch){
        int applied = applier.apply(batch);
        if(applied > 0){
            //Acknowledge the cursor so the server knows how far this client has applied.
            Call.clientLogicDataReliable("orbital.event.ack", applier.appliedSeq());
        }
    }

    /**
     * Requests recovery from a keyframe when the event stream has a gap. The server sends the
     * newest keyframe with seq <= the client cursor, then the client replays newer events.
     * @return true if a recovery was requested.
     */
    public boolean requestRecovery(){
        long cursor = applier.appliedSeq();
        if(cursor < 0) return false;
        Call.clientLogicDataReliable("orbital.keyframe.request", cursor);
        Log.info("[online] requested keyframe after seq @", cursor);
        return true;
    }

    /** Handles a keyframe frame received from the server: load it, then reset the cursor. */
    public void applyKeyframe(long seq, byte[] worldData){
        try{
            //The payload is the NetworkIO deflated world; load it through the same path used at join.
            orbital.net.NetworkIO.loadWorld(new java.io.ByteArrayInputStream(worldData));
            applier.keyframe(seq);
            Log.info("[online] keyframe @ applied", seq);
        }catch(Exception e){
            Log.err("[online] could not apply keyframe: @", e.getMessage());
        }
    }

    /** Called after a keyframe has been applied to the world. */
    public void keyframe(long seq){
        applier.keyframe(seq);
    }

    public long appliedSeq(){
        return applier.appliedSeq();
    }

    public EventApplier applier(){
        return applier;
    }
}
