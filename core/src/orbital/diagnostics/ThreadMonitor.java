package orbital.diagnostics;

import orbital.engine.threading.*;

import java.lang.management.*;

/** Snapshot of the engine pool plus the JVM thread count, for the diagnostics overlay. */
public final class ThreadMonitor{
    public Sample sample(OrbitalJobSystem jobs){
        if(jobs == null){
            return new Sample(0, 0, 0, 0, 0, ManagementFactory.getThreadMXBean().getThreadCount());
        }

        OrbitalWorkerPool pool = jobs.workerPool();
        return new Sample(
            jobs.workerCount(),
            pool.activeThreads(),
            pool.runningThreads(),
            pool.queuedTasks(),
            pool.steals(),
            ManagementFactory.getThreadMXBean().getThreadCount());
    }

    public record Sample(int configuredWorkers, int activeWorkers, int runningWorkers,
                         int queuedTasks, long steals, int jvmThreads){
    }
}
