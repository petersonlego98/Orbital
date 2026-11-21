package orbital.net.online;

import arc.util.*;
import orbital.gen.*;
import orbital.net.events.*;

import java.io.*;
import java.nio.file.*;

/**
 * Coordinates keyframe generation on the server and recovery on the client.
 *
 * <p>The server writes a keyframe every {@code intervalEvents} events (plus one at session start).
 * A client that detects a gap, or that has no state after joining, requests the newest keyframe
 * with {@code seq <= its cursor} and then applies the remaining events. This is what keeps the
 * event stream bounded: old events can be pruned once every client is past them.</p>
 */
public final class KeyframeManager{
    /** Write a keyframe at session start and then every N events. */
    public static final int DEFAULT_INTERVAL_EVENTS = 500;

    private final KeyframeStore store;
    private final int intervalEvents;
    private long lastKeyframeSeq = -1;
    private int sinceKeyframe;

    public KeyframeManager(Path directory){
        this(directory, DEFAULT_INTERVAL_EVENTS);
    }

    public KeyframeManager(Path directory, int intervalEvents){
        this.store = new KeyframeStore(directory);
        this.intervalEvents = Math.max(1, intervalEvents);
    }

    /** Called by the server after each replicated batch. */
    public void onEvents(int count, long currentSeq, Player player){
        sinceKeyframe += count;
        if(lastKeyframeSeq < 0 || sinceKeyframe >= intervalEvents){
            try{
                store.write(currentSeq, player);
                lastKeyframeSeq = currentSeq;
                sinceKeyframe = 0;
                store.prune(3);
            }catch(IOException e){
                Log.err("[online] keyframe write failed: @", e.getMessage());
            }
        }
    }

    /** @return the newest usable keyframe for a client cursor, or null. */
    public @arc.util.Nullable KeyframeStore.Keyframe forClient(long clientSeq){
        try{
            return store.latest(clientSeq);
        }catch(IOException e){
            Log.err("[online] keyframe lookup failed: @", e.getMessage());
            return null;
        }
    }

    /** Loads a keyframe and returns the seq the client should set before replaying events. */
    public long apply(KeyframeStore.Keyframe keyframe){
        try{
            store.load(keyframe);
            return keyframe.seq();
        }catch(IOException e){
            Log.err("[online] keyframe load failed: @", e.getMessage());
            return -1;
        }
    }

    public KeyframeStore store(){
        return store;
    }
}
