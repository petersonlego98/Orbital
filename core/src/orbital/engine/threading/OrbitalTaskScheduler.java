package orbital.engine.threading;

import java.util.*;

/**
 * Turns a range into pool-sized chunks and dispatches groups in priority order.
 *
 * <p>The chunk size is adaptive: a configured maximum keeps per-task overhead low, while the
 * number of workers decides how many chunks are useful. A fixed batch would either create too few
 * chunks on a 32-core machine or too many on a dual-core one.</p>
 */
public final class OrbitalTaskScheduler{
    /** Smallest useful chunk; below this the task bookkeeping costs more than the loop. */
    public static final int minimumBatch = 8;
    /** How many chunks each worker should see, to smooth out unequal per-item costs. */
    public static final int chunksPerWorker = 8;

    private final int maximumBatch;

    public OrbitalTaskScheduler(int maximumBatch){
        this.maximumBatch = Math.max(minimumBatch, maximumBatch);
    }

    /**
     * @return a chunk size no larger than the configured maximum, and small enough that the
     * pool gets roughly {@link #chunksPerWorker} chunks per worker.
     */
    public int batchSize(int length, int workers){
        if(length <= 0) return 1;

        int chunks = Math.max(1, Math.max(1, workers) * chunksPerWorker);
        int adaptive = (length + chunks - 1) / chunks;
        return Math.max(minimumBatch, Math.min(maximumBatch, adaptive));
    }

    public int maximumBatch(){
        return maximumBatch;
    }

    /** Sorts pending ranges by priority without disturbing insertion order within a priority. */
    public static List<OrbitalTaskGroup.Range> prioritize(Collection<OrbitalTaskGroup.Range> ranges){
        ArrayList<OrbitalTaskGroup.Range> ordered = new ArrayList<>(ranges);
        ordered.sort(Comparator
            .comparingInt((OrbitalTaskGroup.Range range) -> range.priority().weight)
            .thenComparingLong(OrbitalTaskGroup.Range::sequence));
        return ordered;
    }
}
