package orbital.engine.simd;

/**
 * Native SSE2 particle backend.
 *
 * <p>The architecture document called this file {@code Sse4Backend}; the name is kept for the
 * planned layout, but the implementation below is honestly an SSE2 one. SSE4.2 was never used by
 * the kernel, and requiring it excluded older x86-64 CPUs for no gain. The native translation unit
 * picks AVX2 inside its own batches where available, so this backend is also the correct choice for
 * an AVX2 machine whose eight-lane batch cannot fit the remaining tail.</p>
 */
public final class Sse4Backend implements SimdBackend{
    @Override
    public int level(){
        return SimdCapabilities.SSE2;
    }

    @Override
    public String name(){
        return "SSE2 (native)";
    }

    @Override
    public int updateParticles(float[] data, int count, float delta){
        return SimdNative.updateParticles(data, count, delta);
    }

    @Override
    public boolean nativeBackend(){
        return true;
    }
}
