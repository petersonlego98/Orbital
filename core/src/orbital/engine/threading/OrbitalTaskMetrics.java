package orbital.engine.threading;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Lock-free counters for the task system.
 *
 * <p>Workers only ever increment {@link LongAdder}s and a small name map, so recording a task is
 * cheaper than a lock and never serialises the pool. A snapshot copies the values into immutable
 * records for the HUD, the profiler or a test.</p>
 */
public final class OrbitalTaskMetrics{
    private final LongAdder submitted = new LongAdder();
    private final LongAdder leaves = new LongAdder();
    private final LongAdder items = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxLeafNanos = new AtomicLong();

    private final ConcurrentHashMap<String, Counter> tasks = new ConcurrentHashMap<>();
    private final EnumMap<OrbitalTaskPriority, Counter> priorities = new EnumMap<>(OrbitalTaskPriority.class);

    public OrbitalTaskMetrics(){
        for(OrbitalTaskPriority priority : OrbitalTaskPriority.values()){
            priorities.put(priority, new Counter());
        }
    }

    /** Records a logical job that is about to be dispatched. */
    public void recordSubmission(){
        submitted.increment();
    }

    /** Records one completed leaf range. Called from worker threads. */
    public void recordLeaf(String name, OrbitalTaskPriority priority, int itemCount, long nanos){
        long elapsed = Math.max(0L, nanos);
        leaves.increment();
        items.add(Math.max(0, itemCount));
        totalNanos.add(elapsed);
        maxLeafNanos.accumulateAndGet(elapsed, Math::max);

        tasks.computeIfAbsent(name == null ? "unnamed" : name, ignored -> new Counter())
            .record(itemCount, elapsed);
        priorities.get(priority == null ? OrbitalTaskPriority.NORMAL : priority)
            .record(itemCount, elapsed);
    }

    public Snapshot snapshot(){
        Map<String, TaskSnapshot> byName = new HashMap<>();
        tasks.forEach((name, counter) -> byName.put(name, counter.snapshot()));

        Map<OrbitalTaskPriority, TaskSnapshot> byPriority = new EnumMap<>(OrbitalTaskPriority.class);
        priorities.forEach((priority, counter) -> byPriority.put(priority, counter.snapshot()));

        return new Snapshot(
            submitted.sum(), leaves.sum(), items.sum(), totalNanos.sum(), maxLeafNanos.get(),
            Collections.unmodifiableMap(byName), Collections.unmodifiableMap(byPriority));
    }

    /** Clears all counters; safe between benchmark runs. */
    public void reset(){
        submitted.reset();
        leaves.reset();
        items.reset();
        totalNanos.reset();
        maxLeafNanos.set(0L);
        tasks.clear();
        priorities.values().forEach(Counter::reset);
    }

    private static final class Counter{
        private final LongAdder calls = new LongAdder();
        private final LongAdder items = new LongAdder();
        private final LongAdder nanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        void record(int itemCount, long elapsed){
            calls.increment();
            items.add(Math.max(0, itemCount));
            nanos.add(elapsed);
            maxNanos.accumulateAndGet(elapsed, Math::max);
        }

        void reset(){
            calls.reset();
            items.reset();
            nanos.reset();
            maxNanos.set(0L);
        }

        TaskSnapshot snapshot(){
            long count = calls.sum();
            long total = nanos.sum();
            return new TaskSnapshot(count, items.sum(), total, count == 0 ? 0L : total / count, maxNanos.get());
        }
    }

    /** Immutable totals for the whole engine. */
    public record Snapshot(long submitted, long leaves, long items, long totalNanos, long maxLeafNanos,
                           Map<String, TaskSnapshot> byName,
                           Map<OrbitalTaskPriority, TaskSnapshot> byPriority){
        public double averageLeafMillis(){
            return leaves == 0 ? 0.0 : totalNanos / 1_000_000.0 / leaves;
        }
    }

    /** Immutable totals for one task name or priority. */
    public record TaskSnapshot(long calls, long items, long totalNanos, long averageNanos, long maxNanos){
        public double totalMillis(){
            return totalNanos / 1_000_000.0;
        }
    }
}
