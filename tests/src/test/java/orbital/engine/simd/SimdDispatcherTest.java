package orbital.engine.simd;

import arc.util.OS;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SimdDispatcherTest{
    private static final int stride = SimdOperations.particleStride;

    @Test
    void packagedBackendLoadsOnWindowsX64(){
        assumeTrue(OS.isWindows, "the SIMD backend ships for Windows only");
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        assumeTrue(arch.equals("amd64") || arch.equals("x86_64"), "the SIMD backend targets x86_64");

        SimdCapabilities capabilities = SimdOperations.capabilities();
        assertTrue(capabilities.nativeLoaded(), "the packaged SIMD library did not load");
        assertTrue(capabilities.backendLevel() >= SimdCapabilities.SSE2,
            "an x86_64 CPU must be able to run at least the SSE2 backend");
        assertTrue(SimdOperations.backend().nativeBackend());
        assertTrue(SimdOperations.backend().level() >= SimdCapabilities.SSE2);
    }

    @Test
    void capabilitiesAreAConsistentTriple(){
        SimdCapabilities capabilities = SimdOperations.capabilities();
        assertTrue(capabilities.backendLevel() >= SimdCapabilities.SCALAR);
        assertTrue(capabilities.backendLevel() <= SimdCapabilities.AVX2);
        assertEquals(capabilities.nativeLoaded() && capabilities.backendLevel() >= SimdCapabilities.SSE2,
            capabilities.sse2());
        assertEquals(capabilities.nativeLoaded() && capabilities.backendLevel() >= SimdCapabilities.AVX2,
            capabilities.avx2());

        SimdBackend backend = SimdOperations.backend();
        assertNotNull(backend.name());
        if(capabilities.nativeLoaded() && capabilities.backendLevel() >= SimdCapabilities.SSE2){
            assertTrue(backend.level() >= SimdCapabilities.SSE2);
        }else{
            assertTrue(backend instanceof ScalarBackend);
        }
    }

    @Test
    void dispatcherMatchesTheScalarReferenceForLiveParticlesAndTails(){
        for(int count = 0; count <= 40; count++){
            assertMatchesScalar(createParticles(count, 7L + count, false), count, 0.125f);
        }
    }

    @Test
    void dispatcherMatchesScalarWhenParticlesExpire(){
        for(int count = 1; count <= 40; count++){
            assertMatchesScalar(createParticles(count, 91L + count, true), count, 0.25f);
        }
    }

    @Test
    void dispatcherMatchesScalarForAllExpiredParticles(){
        int count = 24;
        float[] initial = createParticles(count, 1234L, false);
        for(int i = 0; i < count; i++){
            initial[i * stride + 1] = 0f;
        }
        assertMatchesScalar(initial, count, 0.1f);
    }

    @Test
    void scalarBackendRejectsInvalidCounts(){
        assertThrows(IllegalArgumentException.class,
            () -> SimdOperations.updateParticlesScalar(new float[10], 2, 0.1f));
        assertThrows(IllegalArgumentException.class,
            () -> SimdOperations.updateParticlesScalar(new float[10], -1, 0.1f));
        assertThrows(NullPointerException.class,
            () -> SimdOperations.updateParticlesScalar(null, 0, 0.1f));
    }

    private static void assertMatchesScalar(float[] initial, int count, float delta){
        float[] expected = initial.clone();
        float[] actual = initial.clone();

        int expectedCount = SimdOperations.updateParticlesScalar(expected, count, delta);
        int actualCount = SimdOperations.updateParticles(actual, count, delta);

        assertEquals(expectedCount, actualCount);
        assertArrayEquals(expected, actual, 0f);
    }

    private static float[] createParticles(int count, long seed, boolean expirations){
        Random random = new Random(seed);
        float[] data = new float[count * stride];
        for(int particle = 0; particle < count; particle++){
            int offset = particle * stride;
            data[offset] = random.nextFloat() * 0.3f;
            data[offset + 1] = expirations && particle % 3 == 0 ? 0.05f : 1f + random.nextFloat();
            data[offset + 2] = random.nextFloat() * 100f - 50f;
            data[offset + 3] = random.nextFloat() * 100f - 50f;
            data[offset + 4] = random.nextFloat() * 12f - 6f;
            data[offset + 5] = random.nextFloat() * 12f - 6f;
            data[offset + 6] = random.nextFloat() * 4f;
            data[offset + 7] = random.nextFloat() * 4f;
            data[offset + 8] = random.nextFloat();
        }
        return data;
    }
}
