package orbital.engine.memory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Reusable primitive buffers, bucketed by power of two.
 *
 * <p>Simulation runs every frame, so allocating a fresh {@code float[]} per phase would hand the
 * garbage collector megabytes per second. Buffers returned by {@link #acquireFloat(int)} are at
 * least the requested length and may be longer; callers must treat {@code buffer.length} as
 * capacity and their own requested length as the logical size.</p>
 *
 * <p>The pool is intentionally conservative: arrays are never zeroed on acquire, and only
 * power-of-two arrays within the supported range are retained. Scratch data is not state - a
 * buffer must be fully overwritten before it is read.</p>
 */
public final class OrbitalMemoryPool{
    private static final int minimumBucket = 16;
    private static final int maximumBucket = 1 << 26;
    private static final int maxBuffersPerBucket = 8;

    private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<float[]>> floatBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<int[]>> intBuckets = new ConcurrentHashMap<>();

    private final AtomicLong floatAllocations = new AtomicLong();
    private final AtomicLong floatReuses = new AtomicLong();
    private final AtomicLong intAllocations = new AtomicLong();
    private final AtomicLong intReuses = new AtomicLong();

    public float[] acquireFloat(int minimumLength){
        int bucket = bucketSize(minimumLength);
        if(bucket < 0){
            floatAllocations.incrementAndGet();
            return new float[Math.max(0, minimumLength)];
        }

        ConcurrentLinkedQueue<float[]> queue = floatBuckets.computeIfAbsent(bucket, ignored -> new ConcurrentLinkedQueue<>());
        float[] buffer = queue.poll();
        if(buffer != null){
            floatReuses.incrementAndGet();
            return buffer;
        }

        floatAllocations.incrementAndGet();
        return new float[bucket];
    }

    public int[] acquireInt(int minimumLength){
        int bucket = bucketSize(minimumLength);
        if(bucket < 0){
            intAllocations.incrementAndGet();
            return new int[Math.max(0, minimumLength)];
        }

        ConcurrentLinkedQueue<int[]> queue = intBuckets.computeIfAbsent(bucket, ignored -> new ConcurrentLinkedQueue<>());
        int[] buffer = queue.poll();
        if(buffer != null){
            intReuses.incrementAndGet();
            return buffer;
        }

        intAllocations.incrementAndGet();
        return new int[bucket];
    }

    /** Returns a buffer to its bucket. Null and non-poolable arrays are ignored. */
    public void release(float[] buffer){
        if(buffer == null) return;
        int bucket = bucketOfArray(buffer.length);
        if(bucket < 0) return;

        ConcurrentLinkedQueue<float[]> queue = floatBuckets.computeIfAbsent(bucket, ignored -> new ConcurrentLinkedQueue<>());
        if(queue.size() < maxBuffersPerBucket){
            queue.offer(buffer);
        }
    }

    public void release(int[] buffer){
        if(buffer == null) return;
        int bucket = bucketOfArray(buffer.length);
        if(bucket < 0) return;

        ConcurrentLinkedQueue<int[]> queue = intBuckets.computeIfAbsent(bucket, ignored -> new ConcurrentLinkedQueue<>());
        if(queue.size() < maxBuffersPerBucket){
            queue.offer(buffer);
        }
    }

    /** Drops every retained buffer. Useful between maps or after a benchmark. */
    public void clear(){
        floatBuckets.clear();
        intBuckets.clear();
    }

    public Stats stats(){
        long pooledFloats = floatBuckets.values().stream().mapToLong(ConcurrentLinkedQueue::size).sum();
        long pooledInts = intBuckets.values().stream().mapToLong(ConcurrentLinkedQueue::size).sum();
        return new Stats(floatAllocations.get(), floatReuses.get(), intAllocations.get(), intReuses.get(),
            (int)pooledFloats, (int)pooledInts);
    }

    /** @return the bucket length, or -1 when the request is outside the pooled range. */
    public static int bucketSize(int minimumLength){
        if(minimumLength <= 0) return minimumBucket;
        if(minimumLength > maximumBucket) return -1;

        int bucket = minimumBucket;
        while(bucket < minimumLength){
            bucket <<= 1;
        }
        return bucket;
    }

    private static int bucketOfArray(int length){
        if(length < minimumBucket || length > maximumBucket) return -1;
        return Integer.bitCount(length) == 1 ? length : -1;
    }

    public record Stats(long floatAllocations, long floatReuses, long intAllocations, long intReuses,
                        int pooledFloatBuffers, int pooledIntBuffers){
        public long floatReuseRatioPercent(){
            long total = floatAllocations + floatReuses;
            return total == 0 ? 0 : floatReuses * 100 / total;
        }
    }
}
