package orbital.engine.simd;

/** One selectable SIMD implementation of the particle kernel. */
public interface SimdBackend{
    /** @return {@link SimdCapabilities#SCALAR}, {@link SimdCapabilities#SSE2} or {@link SimdCapabilities#AVX2}. */
    int level();

    /** Human-readable backend name for diagnostics. */
    String name();

    /**
     * Advances particles in place and compacts expired ones.
     *
     * @return the new particle count, or a negative value when this backend cannot run the data
     * (the dispatcher then falls back and records the failure).
     */
    int updateParticles(float[] data, int count, float delta);

    /** Whether the work is executed by native code. */
    default boolean nativeBackend(){
        return false;
    }
}
