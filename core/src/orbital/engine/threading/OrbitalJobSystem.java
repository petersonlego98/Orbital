package orbital.engine.threading;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * Main entry point for engine parallelism.
 *
 * <p>Systems hand ranges to this object; they never create threads. {@link #parallelFor} may run
 * the range inline when there is only one worker or when the range is smaller than a batch, which
 * keeps tiny jobs from paying pool overhead. Everything else is split into work-stealing
 * {@link OrbitalTask}s.</p>
 *
 * <p>Contract for callers: the operation must touch only the slot it is given (a snapshot index or
 * its own slice of an output buffer). Mutating {@code Unit}, {@code Building}, {@code Groups} or
 * Arc structures from a worker is forbidden; those writes belong to the commit step on the calling
 * thread.</p>
 */
public final class OrbitalJobSystem implements AutoCloseable{
    public static final int defaultBatchSize = 256;
    public static final int minimumWorkers = 1;
    public static final int maximumWorkers = OrbitalThreadConfig.maxWorkers;

    private final OrbitalWorkerPool workers;
    private final OrbitalTaskScheduler scheduler;
    private final OrbitalTaskMetrics metrics = new OrbitalTaskMetrics();
    private final int batchSize;
    private final AtomicBoolean closed = new AtomicBoolean();

    public OrbitalJobSystem(int workers, int batchSize){
        this.workers = new OrbitalWorkerPool(Math.max(minimumWorkers, Math.min(maximumWorkers, workers)));
        this.batchSize = Math.max(1, batchSize);
        this.scheduler = new OrbitalTaskScheduler(this.batchSize);
    }

    public OrbitalJobSystem(int workers){
        this(workers, defaultBatchSize);
    }

    public void parallelFor(int start, int end, IntConsumer operation){
        parallelFor("unnamed", start, end, OrbitalTaskPriority.NORMAL, operation);
    }

    public void parallelFor(String name, int start, int end, IntConsumer operation){
        parallelFor(name, start, end, OrbitalTaskPriority.NORMAL, operation);
    }

    public void parallelFor(String name, int start, int end, OrbitalTaskPriority priority, IntConsumer operation){
        ensureOpen();
        Objects.requireNonNull(operation, "operation");
        dispatch(name, start, end, priority == null ? OrbitalTaskPriority.NORMAL : priority, operation, null);
    }

    /**
     * Reduces a range in parallel, returning the sum. Unlike {@code parallelFor}, this is a
     * {@link RecursiveTask} because the caller needs a result - useful for aggregating per-row
     * counters such as mined resources or profiler totals.
     */
    public long parallelSum(int start, int end, IntToLongFunction mapper){
        ensureOpen();
        Objects.requireNonNull(mapper, "mapper");

        int length = end - start;
        if(length <= 0) return 0L;

        int batch = scheduler.batchSize(length, workerCount());
        if(workerCount() <= 1 || length <= batch){
            long sum = 0L;
            for(int i = start; i < end; i++){
                sum += mapper.applyAsLong(i);
            }
            return sum;
        }

        return workers.invoke(new SumTask(start, end, batch, mapper));
    }

    /** Creates an empty group that can collect several ranges before a single dispatch. */
    public OrbitalTaskGroup newGroup(String name){
        ensureOpen();
        return new OrbitalTaskGroup(name);
    }

    /** Dispatches every range in the group, highest priority first. */
    public void run(OrbitalTaskGroup group){
        ensureOpen();
        Objects.requireNonNull(group, "group");

        for(OrbitalTaskGroup.Range range : scheduler.prioritize(group.drain())){
            if(group.isCancelled()) break;
            dispatch(group.name, range.start(), range.end(), range.priority(), range.operation(), group);
        }
        group.markCompleted();
    }

    private void dispatch(String name, int start, int end, OrbitalTaskPriority priority,
                          IntConsumer operation, OrbitalTaskGroup group){
        int length = end - start;
        if(length <= 0 || (group != null && group.isCancelled())) return;

        metrics.recordSubmission();

        int batch = scheduler.batchSize(length, workerCount());
        if(workerCount() <= 1 || length <= batch){
            runSequential(name, start, end, priority, operation, group);
            return;
        }

        OrbitalTask task = new OrbitalTask(name, start, end, batch, priority, operation, metrics, group, 0);
        this.<Void>invoke(task);
    }

    private void runSequential(String name, int start, int end, OrbitalTaskPriority priority,
                               IntConsumer operation, OrbitalTaskGroup group){
        long started = System.nanoTime();
        int processed = 0;
        try{
            for(int i = start; i < end; i++){
                if((i & 63) == 0 && group != null && group.isCancelled()) break;
                operation.accept(i);
                processed++;
            }
        }finally{
            metrics.recordLeaf(name, priority, processed, System.nanoTime() - started);
        }
    }

    private <T> T invoke(java.util.concurrent.ForkJoinTask<T> task){
        return workers.invoke(task);
    }

    private void ensureOpen(){
        if(closed.get()) throw new IllegalStateException("the Orbital job system is closed");
    }

    /** Result-carrying counterpart of {@link OrbitalTask}. */
    private static final class SumTask extends RecursiveTask<Long>{
        private final int start;
        private final int end;
        private final int batch;
        private final IntToLongFunction mapper;

        SumTask(int start, int end, int batch, IntToLongFunction mapper){
            this.start = start;
            this.end = end;
            this.batch = batch;
            this.mapper = mapper;
        }

        @Override
        protected Long compute(){
            int length = end - start;
            if(length <= batch){
                long sum = 0L;
                for(int i = start; i < end; i++){
                    sum += mapper.applyAsLong(i);
                }
                return sum;
            }

            int middle = start + (length >>> 1);
            SumTask left = new SumTask(start, middle, batch, mapper);
            SumTask right = new SumTask(middle, end, batch, mapper);
            invokeAll(left, right);
            return left.join() + right.join();
        }
    }

    public int workerCount(){
        return workers.workers();
    }

    public int batchSize(){
        return batchSize;
    }

    public boolean isWorkerThread(){
        return workers.isWorkerThread();
    }

    public boolean isClosed(){
        return closed.get();
    }

    public OrbitalTaskMetrics metrics(){
        return metrics;
    }

    public OrbitalWorkerPool workerPool(){
        return workers;
    }

    @Override
    public void close(){
        if(closed.compareAndSet(false, true)){
            workers.shutdown();
        }
    }
}
