package orbital.engine.simd;

/** Native AVX2 backend: eight particles per batch, with SSE2 and scalar tails. */
public final class Avx2Backend implements SimdBackend{
    @Override
    public int level(){
        return SimdCapabilities.AVX2;
    }

    @Override
    public String name(){
        return "AVX2 (native)";
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
