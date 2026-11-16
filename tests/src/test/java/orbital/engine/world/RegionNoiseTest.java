package orbital.engine.world;

import orbital.engine.threading.OrbitalJobSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests deterministic, region-parallel world noise. */
class RegionNoiseTest{

    @Test
    void tileIsPureAndSeeded(){
        float a = RegionNoise.tile(12, 34, 99L);
        assertEquals(a, RegionNoise.tile(12, 34, 99L), 0f);
        assertNotEquals(a, RegionNoise.tile(12, 34, 100L));
        assertTrue(a >= 0f && a < 1f, "noise must stay in [0, 1)");
    }

    @Test
    void regionsMatchTheFullGridWhateverTheWorkerCount(){
        int width = 128, height = 128;
        float[] expected = new float[width * height];
        RegionNoise.fill(expected, width, height, 0, 0, width, height, 4242L, 0, height);

        float[] actual = new float[width * height];
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 16)){
            jobs.parallelFor("world.region", 0, height,
                row -> RegionNoise.fill(actual, width, height,
                    0, 0, width, height, 4242L, row, row + 1));
        }

        assertArrayEquals(expected, actual, 0f, "regioning must not change a single tile");
    }
}