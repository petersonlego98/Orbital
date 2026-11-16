package orbital.engine.threading;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.IntConsumer;

/**
 * A set of related ranges dispatched together.
 *
 * <p>Grouping is what makes the missing priority support of {@code ForkJoinPool} usable: the
 * scheduler sorts the group's ranges by {@link OrbitalTaskPriority} before dispatching them, so
 * the critical work enters the workers first. A group can also be cancelled as a unit, which is
 * how a frame that is already late can drop optional work without touching required work.</p>
 */
public final class OrbitalTaskGroup{
    private static final AtomicLong ids = new AtomicLong();

    public final long id = ids.incrementAndGet();
    public final String name;

    private final ArrayList<Range> ranges = new ArrayList<>();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private long sequence;

    public OrbitalTaskGroup(String name){
        this.name = name == null || name.isBlank() ? "group-" + id : name;
    }

    /** Adds a range to this group. Ranges are dispatched when the group is run. */
    public OrbitalTaskGroup add(int start, int end, OrbitalTaskPriority priority, IntConsumer operation){
        if(end <= start) return this;
        Objects.requireNonNull(operation, "operation");
        synchronized(ranges){
            ranges.add(new Range(start, end, priority == null ? OrbitalTaskPriority.NORMAL : priority, sequence++, operation));
        }
        return this;
    }

    public OrbitalTaskGroup add(int start, int end, IntConsumer operation){
        return add(start, end, OrbitalTaskPriority.NORMAL, operation);
    }

    /** Prevents any range that has not started yet from running, and stops running leaves early. */
    public void cancel(){
        cancelled.set(true);
    }

    public boolean isCancelled(){
        return cancelled.get();
    }

    public boolean isCompleted(){
        return completed.get();
    }

    public boolean isEmpty(){
        synchronized(ranges){
            return ranges.isEmpty();
        }
    }

    public int size(){
        synchronized(ranges){
            return ranges.size();
        }
    }

    List<Range> drain(){
        synchronized(ranges){
            ArrayList<Range> copy = new ArrayList<>(ranges);
            copy.sort(Comparator
                .comparingInt((Range range) -> range.priority().weight)
                .thenComparingLong(Range::sequence));
            ranges.clear();
            return copy;
        }
    }

    void markCompleted(){
        completed.set(true);
    }

    /** Internal immutable range description. */
    record Range(int start, int end, OrbitalTaskPriority priority, long sequence, IntConsumer operation){}
}
