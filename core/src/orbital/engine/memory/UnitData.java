package orbital.engine.memory;

/**
 * Structure-of-arrays storage for simulated units.
 *
 * <p>The engine never iterates {@code Unit} objects in a worker. It captures the fields a phase
 * needs into these parallel arrays, lets workers write only their own index, and commits the result
 * on the game thread. SoA also makes a native batch kernel possible later without chasing object
 * graphs.</p>
 *
 * <p>Rows are valid for {@code [0, count)}. Growing preserves data and never shrinks unless
 * {@link #clear()} is called, which only resets the logical count.</p>
 */
public final class UnitData{
    private static final int minimumCapacity = 16;

    public float[] x = new float[0];
    public float[] y = new float[0];
    public float[] velocityX = new float[0];
    public float[] velocityY = new float[0];
    public float[] rotation = new float[0];
    public float[] health = new float[0];

    public int[] entityIds = new int[0];
    public int[] flags = new int[0];

    public int count;

    public UnitData(){
    }

    public UnitData(int capacity){
        ensureCapacity(capacity);
    }

    /** Grows the backing arrays so that at least {@code capacity} rows fit. @return actual capacity. */
    public int ensureCapacity(int capacity){
        if(capacity <= x.length) return x.length;

        int grown = Math.max(capacity, Math.max(minimumCapacity, x.length + (x.length >> 1)));
        x = grow(x, grown);
        y = grow(y, grown);
        velocityX = grow(velocityX, grown);
        velocityY = grow(velocityY, grown);
        rotation = grow(rotation, grown);
        health = grow(health, grown);
        entityIds = grow(entityIds, grown);
        flags = grow(flags, grown);
        return grown;
    }

    public int add(int entityId, float x, float y, float velocityX, float velocityY,
                   float rotation, float health, int flags){
        int index = count;
        ensureCapacity(index + 1);
        this.entityIds[index] = entityId;
        this.x[index] = x;
        this.y[index] = y;
        this.velocityX[index] = velocityX;
        this.velocityY[index] = velocityY;
        this.rotation[index] = rotation;
        this.health[index] = health;
        this.flags[index] = flags;
        count = index + 1;
        return index;
    }

    public void set(int index, int entityId, float x, float y, float velocityX, float velocityY,
                    float rotation, float health, int flags){
        if(index < 0 || index >= count) throw new IndexOutOfBoundsException("unit row " + index + " of " + count);
        this.entityIds[index] = entityId;
        this.x[index] = x;
        this.y[index] = y;
        this.velocityX[index] = velocityX;
        this.velocityY[index] = velocityY;
        this.rotation[index] = rotation;
        this.health[index] = health;
        this.flags[index] = flags;
    }

    /** Copies every row from another buffer, growing this one as needed. */
    public void copyFrom(UnitData source){
        if(source == null || source.count <= 0){
            count = 0;
            return;
        }
        ensureCapacity(source.count);
        System.arraycopy(source.x, 0, x, 0, source.count);
        System.arraycopy(source.y, 0, y, 0, source.count);
        System.arraycopy(source.velocityX, 0, velocityX, 0, source.count);
        System.arraycopy(source.velocityY, 0, velocityY, 0, source.count);
        System.arraycopy(source.rotation, 0, rotation, 0, source.count);
        System.arraycopy(source.health, 0, health, 0, source.count);
        System.arraycopy(source.entityIds, 0, entityIds, 0, source.count);
        System.arraycopy(source.flags, 0, flags, 0, source.count);
        count = source.count;
    }

    /** Resets the logical size. Backing arrays stay allocated for the next frame. */
    public void clear(){
        count = 0;
    }

    public int capacity(){
        return x.length;
    }

    private static float[] grow(float[] array, int length){
        float[] grown = new float[length];
        System.arraycopy(array, 0, grown, 0, array.length);
        return grown;
    }

    private static int[] grow(int[] array, int length){
        int[] grown = new int[length];
        System.arraycopy(array, 0, grown, 0, array.length);
        return grown;
    }
}
