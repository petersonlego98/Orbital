package orbital.engine.simd;

/** Pure-Java reference path, used on mobile and whenever the native library cannot load. */
public final class ScalarBackend implements SimdBackend{
    @Override
    public int level(){
        return SimdCapabilities.SCALAR;
    }

    @Override
    public String name(){
        return "scalar (Java)";
    }

    @Override
    public int updateParticles(float[] data, int count, float delta){
        return SimdOperations.updateParticlesScalar(data, count, delta);
    }
}
