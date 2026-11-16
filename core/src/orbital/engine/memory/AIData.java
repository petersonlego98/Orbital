package orbital.engine.memory;

/**
 * Structure-of-arrays storage for independent AI decisions.
 *
 * <p>Workers read a unit's current position and its target point and write only their own row of
 * the desired steering vector. Nothing here touches the authoritative {@code Unit} objects; the
 * commit step, on the game thread, is the only place that may apply the result.</p>
 */
public final class AIData{
    private static final int minimumCapacity = 16;

    public float[] x = new float[0];
    public float[] y = new float[0];
    public float[] targetX = new float[0];
    public float[] targetY = new float[0];
    public float[] desiredX = new float[0];
    public float[] desiredY = new float[0];
    public float[] distance = new float[0];

    public int[] entityIds = new int[0];
    public int[] flags = new int[0];

    public int count;

    public AIData(){
    }

    public AIData(int capacity){
        ensureCapacity(capacity);
    }

    public int ensureCapacity(int capacity){
        if(capacity <= x.length) return x.length;

        int grown = Math.max(capacity, Math.max(minimumCapacity, x.length + (x.length >> 1)));
        x = grow(x, grown);
        y = grow(y, grown);
        targetX = grow(targetX, grown);
        targetY = grow(targetY, grown);
        desiredX = grow(desiredX, grown);
        desiredY = grow(desiredY, grown);
        distance = grow(distance, grown);
        entityIds = grow(entityIds, grown);
        flags = grow(flags, grown);
        return grown;
    }

    public int add(int entityId, float x, float y, float targetX, float targetY, int flags){
        int index = count;
        ensureCapacity(index + 1);
        this.entityIds[index] = entityId;
        this.x[index] = x;
        this.y[index] = y;
        this.targetX[index] = targetX;
        this.targetY[index] = targetY;
        this.flags[index] = flags;
        count = index + 1;
        return index;
    }

    public void copyFrom(AIData source){
        if(source == null || source.count <= 0){
            count = 0;
            return;
        }
        ensureCapacity(source.count);
        System.arraycopy(source.x, 0, x, 0, source.count);
        System.arraycopy(source.y, 0, y, 0, source.count);
        System.arraycopy(source.targetX, 0, targetX, 0, source.count);
        System.arraycopy(source.targetY, 0, targetY, 0, source.count);
        System.arraycopy(source.desiredX, 0, desiredX, 0, source.count);
        System.arraycopy(source.desiredY, 0, desiredY, 0, source.count);
        System.arraycopy(source.distance, 0, distance, 0, source.count);
        System.arraycopy(source.entityIds, 0, entityIds, 0, source.count);
        System.arraycopy(source.flags, 0, flags, 0, source.count);
        count = source.count;
    }

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