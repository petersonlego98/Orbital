package orbital.net.online;

import orbital.net.events.*;

import arc.util.Nullable;

/** Process-wide holders for the authoritative online stack, installed by the server at startup. */
public final class OnlineHolder{
    private static @Nullable EventStore store;
    private static @Nullable EventRecorder recorder;
    private static @Nullable EventReplicator replicator;

    private OnlineHolder(){
    }

    public static void install(EventStore store, EventRecorder recorder){
        OnlineHolder.store = store;
        OnlineHolder.recorder = recorder;
        OnlineHolder.replicator = new EventReplicator(store);
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
