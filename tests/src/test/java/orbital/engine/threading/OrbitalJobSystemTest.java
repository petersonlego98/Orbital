package orbital.engine.threading;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalJobSystemTest{

    @Test
    void parallelForVisitsEveryIndexExactlyOnce(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 32)){
            int items = 20_000;
            AtomicIntegerArray hits = new AtomicIntegerArray(items);

            jobs.parallelFor("test.range", 0, items, hits::incrementAndGet);

            for(int i = 0; i < items; i++){
                assertEquals(1, hits.get(i), "index " + i + " was not processed exactly once");
            }

            OrbitalTaskMetrics.Snapshot metrics = jobs.metrics().snapshot();
            assertEquals(1, metrics.submitted());
            assertEquals(items, metrics.items());
            assertTrue(metrics.leaves() > 1, "the range should have been split into several leaves");
            assertTrue(metrics.byName().containsKey("test.range"));
        }
    }

    @Test
    void workRunsOnNamedOrbitalWorkers(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 16)){
            Set<String> names = ConcurrentHashMap.newKeySet();
            jobs.parallelFor("test.threads", 0, 10_000, i -> names.add(Thread.currentThread().getName()));

            assertFalse(names.isEmpty());
            for(String name : names){
                assertTrue(name.startsWith(OrbitalWorkerPool.threadNamePrefix),
                    "work escaped to an unexpected thread: " + name);
            }
        }
    }

    @Test
    void exceptionsAreNotSwallowed(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 8)){
            RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                jobs.parallelFor(0, 10_000, i -> {
                    if(i == 5_000) throw new IllegalStateException("boom");
                }));
            assertTrue(chainContains(thrown, "boom"), "original failure was lost: " + thrown);
        }
    }

    @Test
    void groupIsDispatchedInPriorityOrder(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(1, 8)){
            List<String> order = new ArrayList<>();
            OrbitalTaskGroup group = jobs.newGroup("order");
            group.add(0, 1, OrbitalTaskPriority.LOW, i -> order.add("low"));
            group.add(0, 1, OrbitalTaskPriority.CRITICAL, i -> order.add("critical"));
            group.add(0, 1, OrbitalTaskPriority.NORMAL, i -> order.add("normal"));

            jobs.run(group);

            assertEquals(List.of("critical", "normal", "low"), order);
            assertTrue(group.isCompleted());
            assertTrue(group.isEmpty());
        }
    }

    @Test
    void cancelledGroupStopsBeforeTheWholeRangeRuns(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(1, 8)){
            AtomicInteger hits = new AtomicInteger();
            OrbitalTaskGroup group = jobs.newGroup("cancel");
            group.add(0, 5_000, i -> {
                hits.incrementAndGet();
                if(i == 100) group.cancel();
            });

            jobs.run(group);

            assertTrue(group.isCancelled());
            assertTrue(hits.get() >= 101, "the cancelled group stopped before the cancel point");
            assertTrue(hits.get() < 5_000, "the cancelled group ran to the end anyway");
        }
    }

    @Test
    void closedSystemRejectsNewWork(){
        OrbitalJobSystem jobs = new OrbitalJobSystem(2, 8);
        jobs.close();
        jobs.close();

        assertTrue(jobs.isClosed());
        assertThrows(IllegalStateException.class, () -> jobs.parallelFor(0, 10, i -> {}));
        assertThrows(IllegalStateException.class, () -> jobs.newGroup("late"));
    }

    @Test
    void schedulerSizesBatchesForTheMachine(){
        OrbitalTaskScheduler scheduler = new OrbitalTaskScheduler(256);
        assertEquals(1, scheduler.batchSize(0, 4));
        assertEquals(OrbitalTaskScheduler.minimumBatch, scheduler.batchSize(10, 4));
        assertTrue(scheduler.batchSize(1_000_000, 4) <= 256);
        assertTrue(scheduler.batchSize(1_000_000, 32) <= scheduler.batchSize(1_000_000, 2),
            "more workers should not increase the chunk size");
    }

    private static boolean chainContains(Throwable error, String text){
        for(Throwable current = error; current != null; current = current.getCause()){
            if(current.getMessage() != null && current.getMessage().contains(text)) return true;
        }
        return false;
    }
}
