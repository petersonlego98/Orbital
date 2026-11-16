package orbital.engine.memory;

import java.util.*;

/**
 * Frame-scoped scratch buffers on top of {@link OrbitalMemoryPool}.
 *
 * <p>A pipeline phase asks for scratch space with {@link #floatScratch(int)}; everything leased
 * during a frame is returned by {@link #endFrame()}. That makes ownership boring, which is what
 * concurrent code needs: a buffer cannot be handed out twice while the frame is alive.</p>
 */
public final class BufferManager{
    private final OrbitalMemoryPool pool;
    private final ArrayList<float[]> floats = new ArrayList<>();
    private final ArrayList<int[]> ints = new ArrayList<>();
    private long frame;

    public BufferManager(OrbitalMemoryPool pool){
        this.pool = Objects.requireNonNull(pool, "pool");
    }

    public BufferManager(){
        this(new OrbitalMemoryPool());
    }

    public float[] floatScratch(int minimumLength){
        float[] buffer = pool.acquireFloat(minimumLength);
        floats.add(buffer);
        return buffer;
    }

    public int[] intScratch(int minimumLength){
        int[] buffer = pool.acquireInt(minimumLength);
        ints.add(buffer);
        return buffer;
    }

    /** Returns every buffer leased this frame and advances the frame counter. */
    public void endFrame(){
        for(int i = 0; i < floats.size(); i++){
            pool.release(floats.get(i));
        }
        for(int i = 0; i < ints.size(); i++){
            pool.release(ints.get(i));
        }
        floats.clear();
        ints.clear();
        frame++;
    }

    public long frame(){
        return frame;
    }

    public int leasedBuffers(){
        return floats.size() + ints.size();
    }

    public OrbitalMemoryPool pool(){
        return pool;
    }

    public OrbitalMemoryPool.Stats stats(){
        return pool.stats();
    }
}
