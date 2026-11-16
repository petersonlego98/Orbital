package orbital.engine.physics;

import orbital.engine.memory.UnitData;
import orbital.engine.threading.OrbitalJobSystem;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Uniform spatial hash built from a unit snapshot.
 *
 * <p>The build is split so every phase is safe: computing each row's cell and filling the buckets
 * are independent per entity, while the prefix sum that turns counts into cell ranges is sequential
 * and deterministic. Cell contents are sorted by entity index at the end, so worker scheduling
 * never changes the observed order - collisions replay the same way whatever the core count.</p>
 */
public final class SpatialHash{
    private final int cols;
    private final int rows;
    private final float cellSize;
    private final int[] counts;
    private final int[] starts;
    private final AtomicIntegerArray cursors;

    private int[] cellsOfEntity = new int[0];
    private int[] items = new int[0];
    private int itemCount;

    public SpatialHash(float worldWidth, float worldHeight, float cellSize){
        if(cellSize <= 0f || worldWidth <= 0f || worldHeight <= 0f){
            throw new IllegalArgumentException("invalid spatial hash dimensions");
        }
        this.cellSize = cellSize;
        this.cols = Math.max(1, (int)Math.ceil(worldWidth / cellSize));
        this.rows = Math.max(1, (int)Math.ceil(worldHeight / cellSize));
        int cells = cols * rows;
        this.counts = new int[cells];
        this.starts = new int[cells];
        this.cursors = new AtomicIntegerArray(cells);
    }

    /** Builds the hash for the given snapshot; {@code jobs} may be null for the sequential path. */
    public void build(UnitData units, OrbitalJobSystem jobs){
        int n = units.count;
        if(cellsOfEntity.length < n){
            cellsOfEntity = new int[n];
            items = new int[n];
        }

        if(jobs == null){
            for(int i = 0; i < n; i++) cellsOfEntity[i] = cellOf(units.x[i], units.y[i]);
        }else{
            jobs.parallelFor("physics.spatial.cells", 0, n,
                i -> cellsOfEntity[i] = cellOf(units.x[i], units.y[i]));
        }

        Arrays.fill(counts, 0);
        for(int i = 0; i < n; i++) counts[cellsOfEntity[i]]++;

        int total = 0;
        for(int cell = 0; cell < counts.length; cell++){
            starts[cell] = total;
            cursors.set(cell, total);
            total += counts[cell];
        }

        if(jobs == null){
            for(int i = 0; i < n; i++){
                items[cursors.getAndIncrement(cellsOfEntity[i])] = i;
            }
        }else{
            jobs.parallelFor("physics.spatial.fill", 0, n,
                i -> items[cursors.getAndIncrement(cellsOfEntity[i])] = i);
        }

        //deterministic order within a bucket, independent of worker scheduling
        for(int cell = 0; cell < counts.length; cell++){
            Arrays.sort(items, starts[cell], starts[cell] + counts[cell]);
        }
        itemCount = total;
    }

    public int cols(){
        return cols;
    }

    public int rows(){
        return rows;
    }

    public float cellSize(){
        return cellSize;
    }

    public int itemCount(){
        return itemCount;
    }

    public int countInCell(int cellX, int cellY){
        if(cellX < 0 || cellY < 0 || cellX >= cols || cellY >= rows) return 0;
        return counts[cellY * cols + cellX];
    }

    /** @return the entity row at {@code slot} inside the cell, or -1 when out of range. */
    public int itemAt(int cellX, int cellY, int slot){
        int count = countInCell(cellX, cellY);
        if(slot < 0 || slot >= count) return -1;
        return items[starts[cellY * cols + cellX] + slot];
    }

    public int cellX(float x){
        return Math.max(0, Math.min(cols - 1, (int)(x / cellSize)));
    }

    public int cellY(float y){
        return Math.max(0, Math.min(rows - 1, (int)(y / cellSize)));
    }

    private int cellOf(float x, float y){
        return cellY(y) * cols + cellX(x);
    }
}