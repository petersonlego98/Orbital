package orbital.diagnostics;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Per-task-name timing aggregate.
 *
 * <p>Names are a bounded set owned by the engine ("simulation.units", "graphics.particles.vertices"
 * and so on), so a concurrent map is enough; there is no per-frame allocation on the worker path
 * beyond a {@code computeIfAbsent} miss.</p>
 */
public final class TaskProfiler{
    private final ConcurrentHashMap<String, Cell> cells = new ConcurrentHashMap<>();

    public void record(String task, int items, long nanos){
        if(task == null) task = "unnamed";
        cells.computeIfAbsent(task, ignored -> new Cell()).record(items, Math.max(0L, nanos));
    }

    public Map<String, Entry> snapshot(){
        HashMap<String, Entry> copy = new HashMap<>();
        cells.forEach((name, cell) -> copy.put(name, cell.snapshot()));
        return Collections.unmodifiableMap(copy);
    }

    public void reset(){
        cells.clear();
    }

    private static final class Cell{
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

        Entry snapshot(){
            long count = calls.sum();
            long total = nanos.sum();
            return new Entry(count, items.sum(), total, count == 0 ? 0L : total / count, maxNanos.get());
        }
    }

    public record Entry(long calls, long items, long totalNanos, long averageNanos, long maxNanos){
        public double averageMillis(){
            return averageNanos / 1_000_000.0;
        }

        public double maxMillis(){
            return maxNanos / 1_000_000.0;
        }
    }
}
