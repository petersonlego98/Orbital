package orbital.engine.memory;

/** SoA storage for independent mining/resource calculations. */
public final class ResourceData{
    private static final int minimumCapacity = 16;

    public float[] x = new float[0];
    public float[] y = new float[0];
    public float[] rate = new float[0];
    public float[] amount = new float[0];
    public float[] capacity = new float[0];
    public float[] extracted = new float[0];
    public int[] entityIds = new int[0];
    public int count;

    public ResourceData(){
    }

    public ResourceData(int capacity){
        ensureCapacity(capacity);
    }

    public int ensureCapacity(int capacity){
        if(capacity <= x.length) return x.length;
        int grown = Math.max(capacity, Math.max(minimumCapacity, x.length + (x.length >> 1)));
        x = grow(x, grown);
        y = grow(y, grown);
        rate = grow(rate, grown);
        amount = grow(amount, grown);
        this.capacity = grow(this.capacity, grown);
        extracted = grow(extracted, grown);
        entityIds = growInt(entityIds, grown);
        return grown;
    }

    public int add(int entityId, float x, float y, float rate, float amount, float capacity){
        int index = count;
        ensureCapacity(index + 1);
        this.entityIds[index] = entityId;
        this.x[index] = x;
        this.y[index] = y;
        this.rate[index] = rate;
        this.amount[index] = amount;
        this.capacity[index] = capacity;
        count = index + 1;
        return index;
    }

    public void clear(){
        count = 0;
    }

    private static float[] grow(float[] array, int length){
        float[] grown = new float[length];
        System.arraycopy(array, 0, grown, 0, array.length);
        return grown;
    }

    private static int[] growInt(int[] array, int length){
        int[] grown = new int[length];
        System.arraycopy(array, 0, grown, 0, array.length);
        return grown;
    }
}