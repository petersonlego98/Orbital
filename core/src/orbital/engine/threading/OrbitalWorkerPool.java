package orbital.engine.threading;

import arc.util.*;
import orbital.engine.threading.OrbitalTaskMetrics;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Owns the engine's {@link ForkJoinPool}.
 *
 * <p>This is deliberately not the JVM common pool and not {@code parallelStream()}: the engine
 * must be able to size, name, count and shut down its workers without interfering with mods or
 * with any other library that happens to use the common pool.</p>
 *
 * <p>Worker threads are daemon threads named {@code Orbital-Worker-N}. A daemon worker never keeps
 * a test JVM or a closing game alive, and the name makes a thread dump readable.</p>
 */
public final class OrbitalWorkerPool{
    public static final String threadNamePrefix = "Orbital-Worker-";

    private final int workers;
    private final ForkJoinPool pool;
    private final AtomicInteger threadIds = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    public OrbitalWorkerPool(int workers){
        this.workers = Math.max(1, workers);
        ForkJoinPool.ForkJoinWorkerThreadFactory factory = forkJoinPool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(forkJoinPool);
            thread.setName(threadNamePrefix + threadIds.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.pool = new ForkJoinPool(
            this.workers,
            factory,
            (thread, error) -> Log.err("Orbital worker failed", error),
            false
        );
    }

    public ForkJoinPool pool(){
        return pool;
    }

    public int workers(){
        return workers;
    }

    public boolean isWorkerThread(){
        Thread thread = Thread.currentThread();
        return thread instanceof ForkJoinWorkerThread worker && worker.getPool() == pool;
    }

    public int activeThreads(){
        return pool.getActiveThreadCount();
    }

    public int runningThreads(){
        return pool.getRunningThreadCount();
    }

    public int queuedTasks(){
        return pool.getQueuedSubmissionCount();
    }

    public long steals(){
        return pool.getStealCount();
    }

    public int poolSize(){
        return pool.getPoolSize();
    }

    public boolean isShutdown(){
        return pool.isShutdown();
    }

    public boolean isTerminated(){
        return pool.isTerminated();
    }

    /** Stops accepting work and waits a bounded amount of time for in-flight tasks. */
    public void shutdown(){
        if(!closed.compareAndSet(false, true)) return;

        pool.shutdown();
        //a worker cannot wait for its own termination; let the remaining workers drain it
        if(isWorkerThread()) return;
        try{
            if(!pool.awaitTermination(5, TimeUnit.SECONDS)){
                pool.shutdownNow();
                pool.awaitTermination(2, TimeUnit.SECONDS);
            }
        }catch(InterruptedException interrupted){
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Test and diagnostics helper: runs a task on the pool and waits for it. */
    <T> T invoke(ForkJoinTask<T> task){
        return pool.invoke(task);
    }

    ForkJoinPool poolOrNull(){
        return pool;
    }
}
