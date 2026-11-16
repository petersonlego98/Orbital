package orbital.engine.memory;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalMemoryTest{

    @Test
    void unitDataGrowsWithoutLosingRows(){
        UnitData units = new UnitData(1);
        for(int i = 0; i < 200; i++){
            units.add(i, i * 2f, i * 3f, 1f, -1f, i * 0.5f, 100f - i, i % 2);
        }

        assertEquals(200, units.count);
        assertTrue(units.capacity() >= 200);
        assertEquals(398f, units.x[199]);
        assertEquals(597f, units.y[199]);
        assertEquals(199, units.entityIds[199]);

        UnitData copy = new UnitData();
        copy.copyFrom(units);
        assertEquals(200, copy.count);
        assertEquals(units.x[150], copy.x[150]);
        assertEquals(units.health[150], copy.health[150]);

        int capacity = copy.capacity();
        copy.clear();
        assertEquals(0, copy.count);
        assertEquals(capacity, copy.capacity(), "clearing must keep the reusable buffer");
    }

    @Test
    void projectileDataStoresIndependentRows(){
        ProjectileData projectiles = new ProjectileData(1);
        for(int i = 0; i < 100; i++){
            projectiles.add(i, i, -i, 0.5f, 0.25f, 12f + i, 3f, 1, 0);
        }

        assertEquals(100, projectiles.count);
        assertTrue(projectiles.capacity() >= 100);
        assertEquals(50f, projectiles.x[50]);
        assertEquals(-50f, projectiles.y[50]);
        assertEquals(62f, projectiles.damage[50]);

        ProjectileData copy = new ProjectileData();
        copy.copyFrom(projectiles);
        assertEquals(100, copy.count);
        assertEquals(projectiles.damage[99], copy.damage[99]);
    }

    @Test
    void poolReusesReleasedBuffers(){
        OrbitalMemoryPool pool = new OrbitalMemoryPool();
        float[] first = pool.acquireFloat(100);
        assertEquals(128, first.length);
        pool.release(first);

        float[] second = pool.acquireFloat(100);
        assertSame(first, second, "the released buffer should be reused");
        assertEquals(1, pool.stats().floatReuses());
        assertEquals(1, pool.stats().floatAllocations());

        int[] ints = pool.acquireInt(17);
        assertEquals(32, ints.length);
        pool.release(ints);
        assertSame(ints, pool.acquireInt(17));
    }

    @Test
    void bucketSizesArePowerOfTwoAndBounded(){
        assertEquals(16, OrbitalMemoryPool.bucketSize(0));
        assertEquals(16, OrbitalMemoryPool.bucketSize(16));
        assertEquals(32, OrbitalMemoryPool.bucketSize(17));
        assertEquals(128, OrbitalMemoryPool.bucketSize(100));
        assertEquals(-1, OrbitalMemoryPool.bucketSize(Integer.MAX_VALUE));
    }

    @Test
    void bufferManagerReturnsScratchAtTheEndOfTheFrame(){
        BufferManager buffers = new BufferManager();
        float[] scratch = buffers.floatScratch(40);
        buffers.intScratch(8);

        assertEquals(2, buffers.leasedBuffers());
        assertEquals(0, buffers.frame());

        buffers.endFrame();

        assertEquals(0, buffers.leasedBuffers());
        assertEquals(1, buffers.frame());
        assertSame(scratch, buffers.floatScratch(40), "the next frame should get the buffer back");
    }
}
