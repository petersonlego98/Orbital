package orbital.engine.simd;

import orbital.engine.threading.OrbitalJobSystem;
import orbital.engine.units.UnitMovementKernel;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests the native SoA kernels against the Java formula, including offsets and batches. */
class SimdUnitsTest{

    @Test
    void packagedSoaKernelLoadsOnWindowsX64(){
        assumeTrue(arc.util.OS.isWindows, "the SIMD backend ships for Windows only");
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        assumeTrue(arch.equals("amd64") || arch.equals("x86_64"), "the SIMD backend targets x86_64");
        assertTrue(SimdNative.loaded(), "the native library with the SoA kernels did not load");
    }

    @Test
    void unitKernelMatchesScalarForEveryTailSize(){
        for(int count = 0; count <= 40; count++){
            assertMatches(create(count, 700L + count), 0, count, 0.5f, true);
        }
    }

    @Test
    void projectileKernelMatchesScalarForEveryTailSize(){
        for(int count = 0; count <= 40; count++){
            assertMatches(create(count, 1300L + count), 0, count, 0.25f, false);
        }
    }

    @Test
    void offsetIsHonoured(){
        float[][] expected = create(64, 99L);
        float[][] actual = clone(expected);

        scalar(expected, 8, 40, 0.35f, true);
        SimdOperations.updateUnits(actual[0], actual[1], actual[2], actual[3], actual[4],
            8, 40, 0.35f, true);

        for(int array = 0; array < 5; array++){
            assertArrayEquals(expected[array], actual[array], 0f, "array " + array);
        }
    }

    @Test
    void directBufferSingleCallMatchesScalar(){
        int count = 1000;
        float[][] initial = create(count, 555L);
        float[][] expected = clone(initial);
        float[][] actual = clone(initial);

        scalar(expected, 0, count, 0.2f, true);

        SimdSoaBuffer soa = new SimdSoaBuffer();
        soa.upload(actual[0], actual[1], actual[2], actual[3], actual[4], count);
        assertEquals(count, SimdNative.updateUnitsDirect(
            soa.xAddress(), soa.yAddress(), soa.vxAddress(), soa.vyAddress(), soa.dragAddress(),
            0, count, 0.2f, true));
        soa.download(actual[0], actual[1], actual[2], actual[3], count);

        for(int array = 0; array < 5; array++){
            assertArrayEquals(expected[array], actual[array], 0f, "array " + array);
        }
    }

    @Test
    void batchedParallelCallsMatchTheWholeScalarRun(){
        int count = 10_000;
        float[][] source = create(count, 4242L);
        float[][] expected = clone(source);
        float[][] actual = clone(source);

        scalar(expected, 0, count, 0.125f, true);

        SimdSoaBuffer soa = new SimdSoaBuffer();
        soa.upload(actual[0], actual[1], actual[2], actual[3], actual[4], count);
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 256)){
            jobs.parallelRanges("simd.units", 0, count, 256, (from, to) ->
                SimdNative.updateUnitsDirect(
                    soa.xAddress(), soa.yAddress(), soa.vxAddress(), soa.vyAddress(),
                    soa.dragAddress(), from, to - from, 0.125f, true));
        }
        soa.download(actual[0], actual[1], actual[2], actual[3], count);

        for(int array = 0; array < 5; array++){
            assertArrayEquals(expected[array], actual[array], 0f, "array " + array);
        }
    }

    private static void assertMatches(float[][] initial, int offset, int count, float delta, boolean zeroUnmoved){
        float[][] expected = clone(initial);
        float[][] actual = clone(initial);

        scalar(expected, offset, count, delta, zeroUnmoved);
        SimdOperations.updateUnits(actual[0], actual[1], actual[2], actual[3], actual[4],
            offset, count, delta, zeroUnmoved);

        for(int array = 0; array < 5; array++){
            assertArrayEquals(expected[array], actual[array], 0f,
                "array " + array + " with count " + count);
        }
    }

    private static void scalar(float[][] data, int offset, int count, float delta, boolean zeroUnmoved){
        for(int i = offset; i < offset + count; i++){
            UnitMovementKernel.integrate(data[0], data[1], data[2], data[3], data[4],
                i, delta, zeroUnmoved);
        }
    }

    private static float[][] create(int count, long seed){
        Random random = new Random(seed);
        float[][] data = new float[5][count];
        for(int i = 0; i < count; i++){
            data[0][i] = random.nextFloat() * 2000f;
            data[1][i] = random.nextFloat() * 2000f;
            //mix normal velocities with a few at the epsilon boundary
            data[2][i] = i % 11 == 0 ? 1e-9f : (random.nextFloat() - 0.5f) * 20f;
            data[3][i] = i % 13 == 0 ? 1e-9f : (random.nextFloat() - 0.5f) * 20f;
            data[4][i] = i % 64 == 0 ? random.nextFloat() * 5f : random.nextFloat() * 0.6f;
        }
        return data;
    }

    private static float[][] clone(float[][] source){
        float[][] copy = new float[source.length][];
        for(int i = 0; i < source.length; i++){
            copy[i] = source[i].clone();
        }
        return copy;
    }
}