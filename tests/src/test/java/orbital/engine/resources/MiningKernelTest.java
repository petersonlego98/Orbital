package orbital.engine.resources;

import orbital.engine.memory.ResourceData;
import orbital.engine.threading.OrbitalJobSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Equivalence and parallel-agreement tests for mining proposals. */
class MiningKernelTest{

    @Test
    void extractsAtMostWhatRemains(){
        ResourceData data = new ResourceData();
        data.add(1, 0f, 0f, 10f, 5f, 100f);   //plenty of room, extracts the full rate
        data.add(2, 0f, 0f, 10f, 99f, 100f);  //only 1 left, must be capped
        data.add(3, 0f, 0f, 10f, 100f, 100f); //depleted deposit (amount reached capacity)
        float[] efficiency = {1f, 1f, 2f};

        MiningKernel.compute(data, efficiency, 0, 1f);
        MiningKernel.compute(data, efficiency, 1, 1f);
        MiningKernel.compute(data, efficiency, 2, 1f);

        assertEquals(10f, data.extracted[0], 0f);
        assertEquals(15f, data.amount[0], 0f);
        assertEquals(1f, data.extracted[1], 0f, "extraction must stop at the deposit capacity");
        assertEquals(100f, data.amount[1], 0f);
        assertEquals(0f, data.extracted[2], 0f);
    }

    @Test
    void parallelProposalsMatchTheSequentialReference(){
        int count = 10_000;
        ResourceData source = new ResourceData(count);
        float[] efficiency = new float[count];
        for(int i = 0; i < count; i++){
            source.add(i, i, i, 1f + i % 7, i % 50, 100f);
            efficiency[i] = 0.5f + (i % 13) * 0.1f;
        }

        ResourceData sequential = new ResourceData();
        copy(source, sequential);
        for(int i = 0; i < count; i++){
            MiningKernel.compute(sequential, efficiency, i, 0.75f);
        }

        ResourceData parallel = new ResourceData();
        copy(source, parallel);
        long total;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 128)){
            jobs.parallelFor("resources.mining", 0, count,
                i -> MiningKernel.compute(parallel, efficiency, i, 0.75f));
            total = jobs.parallelSum(0, count, i -> (long)parallel.extracted[i]);
        }

        assertArrayEquals(sequential.extracted, parallel.extracted, 0f);
        assertArrayEquals(sequential.amount, parallel.amount, 0f);
        long expected = 0;
        for(int i = 0; i < count; i++) expected += (long)sequential.extracted[i];
        assertEquals(expected, total, "RecursiveTask reduction must match the sequential sum");
    }

    private static void copy(ResourceData from, ResourceData to){
        to.ensureCapacity(from.count);
        System.arraycopy(from.rate, 0, to.rate, 0, from.count);
        System.arraycopy(from.amount, 0, to.amount, 0, from.count);
        System.arraycopy(from.capacity, 0, to.capacity, 0, from.count);
        System.arraycopy(from.extracted, 0, to.extracted, 0, from.count);
        to.count = from.count;
    }
}