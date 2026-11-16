package orbital.engine.navigation;

import orbital.engine.OrbitalEngine;
import orbital.engine.threading.OrbitalJobSystem;

import java.util.Arrays;

/**
 * Grid navigation field: a distance map from one goal plus the flow direction derived from it.
 *
 * <p>The distance pass is a deterministic four-connected BFS, so it stays on the calling thread.
 * The flow pass is independent per cell - each cell looks at its neighbours' distances and points
 * at the cheapest one - which makes it a natural work-stealing job. Keeping both passes here means
 * the navigation system produces the same field whichever worker count is configured, a
 * requirement for authoritative multiplayer state.</p>
 */
public final class FlowField{
    public static final int unreachable = Integer.MAX_VALUE;

    private final int width;
    private final int height;
    private final int[] distance;
    private final float[] flowX;
    private final float[] flowY;
    private final boolean[] blocked;

    public FlowField(int width, int height){
        if(width <= 0 || height <= 0) throw new IllegalArgumentException("grid must be positive");
        this.width = width;
        this.height = height;
        int cells = width * height;
        this.distance = new int[cells];
        this.flowX = new float[cells];
        this.flowY = new float[cells];
        this.blocked = new boolean[cells];
        Arrays.fill(distance, unreachable);
    }

    public int width(){
        return width;
    }

    public int height(){
        return height;
    }

    /** @return the number of cells reachable from the goal. */
    public int computeDistance(int goalX, int goalY){
        Arrays.fill(distance, unreachable);
        if(!inside(goalX, goalY) || blocked[index(goalX, goalY)]) return 0;

        int[] queue = new int[width * height];
        int head = 0, tail = 0;
        int goal = index(goalX, goalY);
        distance[goal] = 0;
        queue[tail++] = goal;
        int reached = 1;

        while(head < tail){
            int cell = queue[head++];
            int x = cell % width;
            int y = cell / width;
            int next = distance[cell] + 1;

            if(x > 0 && enqueueIfBetter(cell - 1, next)){
                queue[tail++] = cell - 1;
                reached++;
            }
            if(x < width - 1 && enqueueIfBetter(cell + 1, next)){
                queue[tail++] = cell + 1;
                reached++;
            }
            if(y > 0 && enqueueIfBetter(cell - width, next)){
                queue[tail++] = cell - width;
                reached++;
            }
            if(y < height - 1 && enqueueIfBetter(cell + width, next)){
                queue[tail++] = cell + width;
                reached++;
            }
        }
        return reached;
    }

    private boolean enqueueIfBetter(int cell, int next){
        if(blocked[cell] || distance[cell] <= next) return false;
        distance[cell] = next;
        return true;
    }

    /** Uses the shared engine when the field is large enough, otherwise runs inline. */
    public void computeFlow(){
        int cells = width * height;
        boolean parallel = OrbitalEngine.parallelForIfUseful(
            "navigation.flow", cells, 4096, this::writeFlow);
        if(!parallel){
            for(int i = 0; i < cells; i++){
                writeFlow(i);
            }
        }
    }

    /** Reference path: every cell on the calling thread. */
    public void computeFlowSequential(){
        for(int i = 0, cells = width * height; i < cells; i++){
            writeFlow(i);
        }
    }

    /** Explicit pool variant, used by the validation harness. */
    public void computeFlow(OrbitalJobSystem jobs){
        jobs.parallelFor("navigation.flow", 0, width * height, this::writeFlow);
    }

    /** One cell's flow: a unit vector toward the cheapest neighbour, or zero at the goal. */
    private void writeFlow(int cell){
        int x = cell % width;
        int y = cell / width;

        int best = distance[cell];
        int chosen = -1;
        if(x > 0 && distance[cell - 1] < best){ best = distance[cell - 1]; chosen = cell - 1; }
        if(x < width - 1 && distance[cell + 1] < best){ best = distance[cell + 1]; chosen = cell + 1; }
        if(y > 0 && distance[cell - width] < best){ best = distance[cell - width]; chosen = cell - width; }
        if(y < height - 1 && distance[cell + width] < best){ best = distance[cell + width]; chosen = cell + width; }

        if(chosen < 0 || best >= unreachable){
            flowX[cell] = 0f;
            flowY[cell] = 0f;
            return;
        }

        float dx = (chosen % width) - x;
        float dy = (chosen / width) - y;
        float length = Math.abs(dx) + Math.abs(dy);
        flowX[cell] = dx / length;
        flowY[cell] = dy / length;
    }

    public void setBlocked(int x, int y, boolean value){
        if(!inside(x, y)) return;
        blocked[index(x, y)] = value;
    }

    public boolean isBlocked(int x, int y){
        return inside(x, y) && blocked[index(x, y)];
    }

    public boolean inside(int x, int y){
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public int distanceAt(int x, int y){
        return inside(x, y) ? distance[index(x, y)] : unreachable;
    }

    public float flowXAt(int x, int y){
        return inside(x, y) ? flowX[index(x, y)] : 0f;
    }

    public float flowYAt(int x, int y){
        return inside(x, y) ? flowY[index(x, y)] : 0f;
    }

    private int index(int x, int y){
        return y * width + x;
    }
}