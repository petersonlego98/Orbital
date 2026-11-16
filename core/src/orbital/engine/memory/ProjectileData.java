package orbital.engine.memory;

/**
 * Structure-of-arrays storage for projectiles, the first combat phase intended for batching.
 *
 * <p>Rows are valid for {@code [0, count)}; each index is independent, so a worker may compute one
 * row without synchronising with the others. The commit step is what applies damage or removes an
 * entity back in the authoritative world.</p>
 */
public final class ProjectileData{
    private static final int minimumCapacity = 16;

    public float[] x = new float[0];
    public float[] y = new float[0];
    public float[] velocityX = new float[0];
    public float[] velocityY = new float[0];
    public float[] damage = new float[0];
    public float[] lifetime = new float[0];

    public int[] entityIds = new int[0];
    public int[] teams = new int[0];
    public int[] flags = new int[0];

    public int count;

    public ProjectileData(){
    }

    public ProjectileData(int capacity){
        ensureCapacity(capacity);
    }

    public int ensureCapacity(int capacity){
        if(capacity <= x.length) return x.length;

        int grown = Math.max(capacity, Math.max(minimumCapacity, x.length + (x.length >> 1)));
        x = grow(x, grown);
        y = grow(y, grown);
        velocityX = grow(velocityX, grown);
        velocityY = grow(velocityY, grown);
        damage = grow(damage, grown);
        lifetime = grow(lifetime, grown);
        entityIds = grow(entityIds, grown);
        teams = grow(teams, grown);
        flags = grow(flags, grown);
        return grown;
    }

    public int add(int entityId, float x, float y, float velocityX, float velocityY,
                   float damage, float lifetime, int team, int flags){
        int index = count;
        ensureCapacity(index + 1);
        this.entityIds[index] = entityId;
        this.x[index] = x;
        this.y[index] = y;
        this.velocityX[index] = velocityX;
        this.velocityY[index] = velocityY;
        this.damage[index] = damage;
        this.lifetime[index] = lifetime;
        this.teams[index] = team;
        this.flags[index] = flags;
        count = index + 1;
        return index;
    }

    public void set(int index, int entityId, float x, float y, float velocityX, float velocityY,
                    float damage, float lifetime, int team, int flags){
        if(index < 0 || index >= count) throw new IndexOutOfBoundsException("projectile row " + index + " of " + count);
        this.entityIds[index] = entityId;
        this.x[index] = x;
        this.y[index] = y;
        this.velocityX[index] = velocityX;
        this.velocityY[index] = velocityY;
        this.damage[index] = damage;
        this.lifetime[index] = lifetime;
        this.teams[index] = team;
        this.flags[index] = flags;
    }

    public void copyFrom(ProjectileData source){
        if(source == null || source.count <= 0){
            count = 0;
            return;
        }
        ensureCapacity(source.count);
        System.arraycopy(source.x, 0, x, 0, source.count);
        System.arraycopy(source.y, 0, y, 0, source.count);
        System.arraycopy(source.velocityX, 0, velocityX, 0, source.count);
        System.arraycopy(source.velocityY, 0, velocityY, 0, source.count);
        System.arraycopy(source.damage, 0, damage, 0, source.count);
        System.arraycopy(source.lifetime, 0, lifetime, 0, source.count);
        System.arraycopy(source.entityIds, 0, entityIds, 0, source.count);
        System.arraycopy(source.teams, 0, teams, 0, source.count);
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
