package orbital.engine;

import arc.util.*;
import orbital.diagnostics.*;
import orbital.engine.simulation.*;
import orbital.engine.threading.*;

import java.util.function.IntConsumer;

/**
 * Composition root for the Orbital engine.
 *
 * <p>One job system, one simulation pipeline and one set of diagnostics are shared by every
 * subsystem. Systems do not own threads and do not allocate pools: they submit ranges here. The
 * engine starts lazily, so merely loading the class costs nothing and mobile builds never spawn
 * workers.</p>
 *
 * <p>The pool size is resolved from {@link OrbitalThreadConfig} (Settings &gt; Advanced Graphics)
 * and can be changed at runtime; the old pool is drained before it is discarded.</p>
 */
public final class OrbitalEngine implements AutoCloseable{
    private static final Object lock = new Object();
    private static volatile OrbitalEngine instance;

    private final OrbitalJobSystem jobs;
    private final SimulationPipeline pipeline;
    private final OrbitalSimulation simulation;
    private final CpuMonitor cpuMonitor;
    private final ThreadMonitor threadMonitor;
    private final TaskProfiler taskProfiler;
    private final SimulationProfiler simulationProfiler;

    public OrbitalEngine(int workers, int batchSize){
        this.cpuMonitor = new CpuMonitor();
        this.threadMonitor = new ThreadMonitor();
        this.taskProfiler = new TaskProfiler();
        this.simulationProfiler = new SimulationProfiler();
        this.jobs = new OrbitalJobSystem(workers, batchSize);
        this.pipeline = new SimulationPipeline(jobs, taskProfiler, simulationProfiler);
        this.simulation = new OrbitalSimulation(pipeline);
    }

    public OrbitalEngine(int workers){
        this(workers, OrbitalJobSystem.defaultBatchSize);
    }

    /** @return true when this platform and machine can profit from the shared pool. */
    public static boolean parallelCapable(){
        try{
            return !OS.isAndroid && !OS.isIos && OrbitalThreadConfig.availableProcessors() > 2;
        }catch(Throwable unavailable){
            return false;
        }
    }

    /** Lazily creates the process-wide engine with the configured worker count. */
    public static OrbitalEngine shared(){
        OrbitalEngine current = instance;
        if(current != null) return current;

        synchronized(lock){
            if(instance == null){
                instance = new OrbitalEngine(OrbitalThreadConfig.configured(), OrbitalJobSystem.defaultBatchSize);
            }
            return instance;
        }
    }

    /**
     * Runs {@code operation} for {@code [0, items)} in parallel when it is worth it.
     *
     * @return false when the caller should run its sequential path (mobile, small workload, or the
     * pool could not be created). A failure inside the operation is not swallowed: it propagates,
     * because the parallel loop may already have written part of the output.
     */
    public static boolean parallelForIfUseful(String name, int items, int threshold, IntConsumer operation){
        if(items <= 0 || items < threshold || !parallelCapable()) return false;

        OrbitalEngine engine;
        try{
            engine = shared();
        }catch(Throwable creationFailure){
            Log.err("Could not start the Orbital engine; staying on the sequential path", creationFailure);
            return false;
        }

        engine.jobs.parallelFor(name, 0, items, OrbitalTaskPriority.NORMAL, operation);
        return true;
    }

    /** Applies a new worker preference immediately, draining the previous pool first. */
    public static void reconfigure(int requested){
        synchronized(lock){
            OrbitalEngine old = instance;
            if(old == null) return; //nothing has started; the next use reads the stored value

            int workers = OrbitalThreadConfig.resolve(requested);
            instance = new OrbitalEngine(workers, old.jobs.batchSize());
            old.close();
        }
    }

    /** Stops the shared pool on a graceful exit. Safe to call when nothing started. */
    public static void shutdownIfStarted(){
        synchronized(lock){
            OrbitalEngine old = instance;
            instance = null;
            if(old != null) old.close();
        }
    }

    /** @return true when the shared engine has already been started. */
    public static boolean isStarted(){
        return instance != null;
    }

    /** @return the running pool size, or 0 when nothing has started. Does not create the engine. */
    public static int activeWorkerCount(){
        OrbitalEngine current = instance;
        return current == null ? 0 : current.workerCount();
    }

    public OrbitalJobSystem jobSystem(){
        return jobs;
    }

    public SimulationPipeline pipeline(){
        return pipeline;
    }

    public OrbitalSimulation simulation(){
        return simulation;
    }

    public TaskProfiler taskProfiler(){
        return taskProfiler;
    }

    public SimulationProfiler simulationProfiler(){
        return simulationProfiler;
    }

    public CpuMonitor cpuMonitor(){
        return cpuMonitor;
    }

    public ThreadMonitor threadMonitor(){
        return threadMonitor;
    }

    public int workerCount(){
        return jobs.workerCount();
    }

    @Override
    public void close(){
        jobs.close();
    }
}
