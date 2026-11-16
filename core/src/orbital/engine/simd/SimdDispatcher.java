package orbital.engine.simd;

import arc.util.*;

import java.util.concurrent.atomic.*;

/**
 * Chooses one backend once per process and keeps a single fallback path.
 *
 * <p>Selection happens after {@link SimdNative} has probed the CPU, so a machine without AVX2 gets
 * the SSE2 backend and a machine without the native library gets the Java scalar kernel. If a
 * loaded backend fails at runtime, the dispatcher switches to scalar for the rest of the process
 * instead of throwing from the render thread every frame.</p>
 */
public final class SimdDispatcher{
    private static final SimdCapabilities capabilities = SimdNative.capabilities();
    private static final AtomicReference<SimdBackend> backend = new AtomicReference<>(select(capabilities));

    private SimdDispatcher(){
    }

    private static SimdBackend select(SimdCapabilities caps){
        if(caps.nativeLoaded() && caps.backendLevel() >= SimdCapabilities.AVX2) return new Avx2Backend();
        if(caps.nativeLoaded() && caps.backendLevel() >= SimdCapabilities.SSE2) return new Sse4Backend();
        return new ScalarBackend();
    }

    public static SimdCapabilities capabilities(){
        return capabilities;
    }

    public static SimdBackend backend(){
        return backend.get();
    }

    public static int updateParticles(float[] data, int count, float delta){
        SimdBackend active = backend.get();
        int result;
        try{
            result = active.updateParticles(data, count, delta);
        }catch(LinkageError linkage){
            //a library that unloads cannot be used again; keep the frame alive with scalar code
            result = -1;
        }

        if(result >= 0) return result;

        if(active.nativeBackend()){
            if(backend.compareAndSet(active, new ScalarBackend())){
                Log.err("Orbital SIMD backend '@' failed; falling back to the scalar kernel", active.name());
            }
        }
        return SimdOperations.updateParticlesScalar(data, count, delta);
    }
}
