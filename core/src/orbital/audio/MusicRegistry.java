package orbital.audio;

import arc.audio.*;
import arc.files.*;
import arc.util.*;
import orbital.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Holds the music tracks that the asset manager registered but did not decode.
 *
 * <p>Decoding an OGG track is CPU heavy: the fifteen tracks of the game add up to 25 MB and cost
 * roughly 30 CPU-seconds to decode. Decoding all of them while the game boots makes the loading
 * screen freeze for seconds and steals several cores from the asset loader and the render thread,
 * which is what turned the boot into a slow, choppy experience on weak machines.
 *
 * <p>Instead, the loader only registers the file of each track and the decode is started in the
 * background the first time a track is actually needed. The track starts as soon as the decode
 * finishes, without blocking the render thread.</p>
 */
public class MusicRegistry{
    private static final Map<Music, Fi> files = new ConcurrentHashMap<>();
    private static final Set<Music> loading = ConcurrentHashMap.newKeySet();
    private static final Set<Music> ready = ConcurrentHashMap.newKeySet();

    /** Registers the file of a track that the asset manager loaded. */
    public static void register(Music music, Fi file){
        if(music == null || file == null) return;
        files.put(music, file);
    }

    /**
     * Starts the decode of a track when it has not been decoded yet.
     *
     * @return whether the track is decoded and can be played
     */
    public static boolean request(Music music){
        if(music == null) return false;
        if(ready.contains(music)) return true;
        if(loading.contains(music)) return false;

        Fi file = files.get(music);
        //not registered by the loader: nothing to decode, the track is already usable
        if(file == null) return true;

        loading.add(music);
        Vars.mainExecutor.submit(() -> {
            try{
                music.load(file);
                ready.add(music);
            }catch(Throwable error){
                Log.err("[audio] Could not decode the music " + file.name() + ": " + error);
            }finally{
                loading.remove(music);
            }
        });
        return false;
    }

    /** Whether the track was decoded and can be played. */
    public static boolean isReady(Music music){
        return music != null && ready.contains(music);
    }

    /** Whether the track is being decoded right now. */
    public static boolean isLoading(Music music){
        return music != null && loading.contains(music);
    }
}
