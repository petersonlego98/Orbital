package orbital.engine.simd;

import orbital.engine.units.UnitMovementKernel;

/**
 * Reference particle kernel and the stable facade over the SIMD layer.
 *
 * <p>The scalar loop is the specification the native backends are tested against: same update
 * order, same compaction on expiry, same float operations. Keeping it here rather than in the
 * renderer means the engine, the backend tests and the fallback all share one definition.</p>
 */
public final class SimdOperations{
    public static final int particleStride = 9;
    public static final float globalDrag = 0.05f;

    private SimdOperations(){
    }

    public static int updateParticles(float[] data, int count, float delta){
        return SimdDispatcher.updateParticles(data, count, delta);
    }

    /**
     * Integrates a slice of unit or projectile SoA arrays in one native call.
     *
     * @param zeroUnmoved true for units (zero an axis that did not move), false for projectiles
     * @return the number of rows integrated
     */
    public static int updateUnits(float[] x, float[] y, float[] vx, float[] vy, float[] drag,
                                  int offset, int count, float delta, boolean zeroUnmoved){
        return SimdDispatcher.updateUnits(x, y, vx, vy, drag, offset, count, delta, zeroUnmoved);
    }

    /** Scalar reference implementation. */
    public static int updateParticlesScalar(float[] data, int count, float delta){
        if(data == null) throw new NullPointerException("data");
        if(count < 0 || (long)count * particleStride > data.length){
            throw new IllegalArgumentException("particle count " + count + " does not fit " + data.length + " floats");
        }

        int head = count * particleStride;
        float dragValue = Math.max(1f - globalDrag * delta, 0f);

        for(int i = 0; i < head; i += particleStride){
            data[i] += delta;
            if(data[i] >= data[i + 1]){
                if(head > particleStride){
                    //swap the last live particle into this slot and re-check it
                    System.arraycopy(data, head - particleStride, data, i, particleStride);
                }
                head -= particleStride;
                i -= particleStride;
            }else{
                data[i + 2] += data[i + 4] * delta;
                data[i + 3] += data[i + 5] * delta;
                data[i + 4] *= dragValue;
                data[i + 5] *= dragValue;
            }
        }

        return head / particleStride;
    }

    public static SimdCapabilities capabilities(){
        return SimdDispatcher.capabilities();
    }

    public static SimdBackend backend(){
        return SimdDispatcher.backend();
    }
}
