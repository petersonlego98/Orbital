package orbital.engine.threading;

import java.util.concurrent.*;
import java.util.function.IntConsumer;

/**
 * One splittable range of engine work.
 *
 * <p>The task extends {@link RecursiveAction} so the pool can steal its halves. The operation is
 * expected to be side-effect free with respect to the game: ranges must write only their own slots
 * of a snapshot or output buffer. The authoritative commit stays on the calling thread.</p>
 */
public final class OrbitalTask extends RecursiveAction{
    private final String name;
    private final int start;
    private final int end;
    private final int batchSize;
    private final OrbitalTaskPriority priority;
    private final IntConsumer operation;
    private final OrbitalTaskMetrics metrics;
    private final OrbitalTaskGroup group;
    private final int depth;

    public OrbitalTask(String name, int start, int end, int batchSize, OrbitalTaskPriority priority,
                       IntConsumer operation, OrbitalTaskMetrics metrics, OrbitalTaskGroup group, int depth){
        this.name = name;
        this.start = start;
        this.end = end;
        this.batchSize = Math.max(1, batchSize);
        this.priority = priority == null ? OrbitalTaskPriority.NORMAL : priority;
        this.operation = operation;
        this.metrics = metrics;
        this.group = group;
        this.depth = depth;
    }

    public String name(){
        return name;
    }

    public int start(){
        return start;
    }

    public int end(){
        return end;
    }

    public OrbitalTaskPriority priority(){
        return priority;
    }

    public int depth(){
        return depth;
    }

    @Override
    protected void compute(){
        if(cancelled()) return;

        int length = end - start;
        if(length <= 0) return;

        if(length <= batchSize){
            runLeaf();
            return;
        }

        int middle = start + (length >>> 1);
        //split on a batch boundary, otherwise one side can be one item larger forever
        int aligned = start + (((middle - start) + batchSize - 1) / batchSize) * batchSize;
        if(aligned > start && aligned < end){
            middle = aligned;
        }

        OrbitalTask left = new OrbitalTask(name, start, middle, batchSize, priority, operation, metrics, group, depth + 1);
        OrbitalTask right = new OrbitalTask(name, middle, end, batchSize, priority, operation, metrics, group, depth + 1);
        invokeAll(left, right);
    }

    private void runLeaf(){
        if(cancelled()) return;

        long started = System.nanoTime();
        int processed = 0;
        try{
            for(int i = start; i < end; i++){
                if((i & 63) == 0 && cancelled()) break;
                operation.accept(i);
                processed++;
            }
        }finally{
            if(metrics != null){
                metrics.recordLeaf(name, priority, processed, System.nanoTime() - started);
            }
        }
    }

    private boolean cancelled(){
        return group != null && group.isCancelled();
    }
}
