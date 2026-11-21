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
        Log.info("[online] event channel installed");
    }

    private void receive(byte[] batch){
        int applied = applier.apply(batch);
        if(applied > 0){
            //Acknowledge the cursor so the server knows how far this client has applied.
            Call.clientLogicDataReliable("orbital.event.ack", applier.appliedSeq());
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
