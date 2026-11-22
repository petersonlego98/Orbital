package orbital.net.online;

import orbital.net.events.*;

import arc.util.Nullable;

/** Process-wide holders for the authoritative online stack, installed by the server at startup. */
public final class OnlineHolder{
    private static @Nullable EventStore store;
    private static @Nullable EventRecorder recorder;
    private static @Nullable EventReplicator replicator;
    private static @Nullable KeyframeManager keyframes;
    private static @Nullable InputReplicator inputs;
    private static @Nullable SquadSectorRegistry squads;

    private OnlineHolder(){
    }

    public static void install(EventStore store, EventRecorder recorder){
        OnlineHolder.store = store;
        OnlineHolder.recorder = recorder;
        OnlineHolder.replicator = new EventReplicator(store);
        OnlineHolder.keyframes = new KeyframeManager(store.directory().resolveSibling("keyframes"));
        OnlineHolder.inputs = new InputReplicator();
        if(AuthoritativeMode.enabled()) OnlineHolder.inputs.install();
        installSquads();
    }

    /** Installs only the persistent squad registry (works without the full event log). */
    public static void installSquads(){
        if(squads == null) squads = new SquadSectorRegistry();
    }

    public static @Nullable SquadSectorRegistry squads(){
        return squads;
    }

    public static @Nullable InputReplicator inputs(){
        return inputs;
    }

    public static @Nullable KeyframeManager keyframes(){
        return keyframes;
    }

    public static @Nullable EventStore store(){
        return store;
    }

    public static @Nullable EventRecorder recorder(){
        return recorder;
    }

    public static @Nullable EventReplicator replicator(){
        return replicator;
    }

    public static void clear(){
        store = null;
        recorder = null;
        replicator = null;
    }
}
