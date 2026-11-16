package orbital.graphics;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ParticleRendererSimdTest{
    private static final int particleSize = 9;

    /**
     * The comparisons below are only meaningful when the native library actually runs: without
     * it, {@link ParticleRenderer#update} *is* {@link ParticleRenderer#updateScalar}, and every
     * assertion passes without touching a single SIMD instruction. This test makes the packaged
     * backend itself fail loudly instead of silently degrading.
     */
    @Test
    void packagedSimdLibraryLoadsOnWindowsX64(){
        assumeTrue(arc.util.OS.isWindows, "the SIMD backend ships for Windows only");
        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        assumeTrue(arch.equals("amd64") || arch.equals("x86_64"), "the SIMD backend targets x86_64");

        assertNotNull(ParticleRenderer.class.getResource("/native/windows-x86_64/orbital_particlesimd.dll"),
            "the SIMD DLL is missing from the packaged resources");
        assertTrue(ParticleRenderer.nativeSimdEnabled(),
            "the packaged SIMD DLL did not load; the update-vs-scalar checks above would compare a loop with itself");
    }

    @Test
    void updateMatchesScalarForLiveParticlesAndTailSizes(){
        for(int count = 0; count <= 40; count++){
            float[] initial = createParticles(count, 7L + count, false);
            assertMatchesScalar(initial, count, 0.125f);
        }
    }

    @Test
    void updateMatchesScalarWhenExpirationsTriggerSwapAndRecheck(){
        for(int count = 1; count <= 40; count++){
            float[] initial = createParticles(count, 91L + count, true);
            assertMatchesScalar(initial, count, 0.25f);
        }
    }

    @Test
    void updateMatchesScalarForAllExpiredParticles(){
        int count = 24;
        float[] initial = createParticles(count, 1234L, false);
        for(int i = 0; i < count; i++) initial[i * particleSize + 1] = 0f;
        assertMatchesScalar(initial, count, 0.1f);
    }

    private static void assertMatchesScalar(float[] initial, int count, float delta){
        float[] expected = initial.clone();
        float[] actual = initial.clone();

        int expectedCount = ParticleRenderer.updateScalar(expected, count, delta);
        int actualCount = ParticleRenderer.update(actual, count, delta);

        assertEquals(expectedCount, actualCount);
        assertArrayEquals(expected, actual, 0f);
    }

    private static float[] createParticles(int count, long seed, boolean expirations){
        Random random = new Random(seed);
        float[] data = new float[count * particleSize];
        for(int particle = 0; particle < count; particle++){
            int offset = particle * particleSize;
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
