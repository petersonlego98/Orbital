package orbital.engine.ai;

import orbital.engine.memory.AIData;
import orbital.engine.simulation.SimulationPipeline;
import orbital.engine.simulation.SimulationSnapshot;
import orbital.engine.threading.OrbitalJobSystem;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Equivalence and parallel-agreement tests for independent AI steering decisions. */
class AIDecisionKernelTest{

    @Test
    void seeksTheTargetAndSlowsDownOnArrival(){
        AIData data = new AIData(3);
        data.add(1, 0f, 0f, 100f, 0f, 0);   //far target
        data.add(2, 0f, 0f, 0f, -10f, 0);   //inside arrival radius
        data.add(3, 5f, 5f, 5f, 5f, 0);     //already there

        for(int i = 0; i < data.count; i++){
            AIDecisionKernel.decide(data, i);
        }

        assertEquals(1f, data.desiredX[0], 0f);
        assertEquals(0f, data.desiredY[0], 0f);
        assertEquals(100f, data.distance[0], 0f);

        assertEquals(0f, data.desiredX[1], 0f);
        assertEquals(-0.3125f, data.desiredY[1], 0f, "inside the arrival radius the speed is distance / radius (10 / 32)");
        assertEquals(10f, data.distance[1], 0f);

        assertEquals(0f, data.desiredX[2], 0f);
        assertEquals(0f, data.desiredY[2], 0f, "an arrived unit must not keep steering");
    }

    @Test
    void diagonalDecisionsAreNormalised(){
        AIData data = new AIData();
        data.add(1, 0f, 0f, 30f, 40f, 0);

        AIDecisionKernel.decide(data, 0);

        assertEquals(50f, data.distance[0], 0f);
        assertEquals(0.6f, data.desiredX[0], 1e-6f);
        assertEquals(0.8f, data.desiredY[0], 1e-6f);
    }

    @Test
    void parallelDecisionsMatchTheSequentialReference(){
        Random random = new Random(777L);
        int count = 12_000;
        AIData sequential = randomData(count, random);
        AIData parallel = new AIData();
        parallel.copyFrom(sequential);

        for(int i = 0; i < count; i++){
            AIDecisionKernel.decide(sequential, i);
        }

        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 128)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            SimulationSnapshot snapshot = new SimulationSnapshot();
            pipeline.capture(snapshot, 0, null, null);
            pipeline.parallelFor("simulation.ai", count,
                index -> AIDecisionKernel.decide(parallel, index));
            pipeline.commit(snapshot, null, null);
        }

        assertArrayEquals(sequential.desiredX, parallel.desiredX, 0f);
        assertArrayEquals(sequential.desiredY, parallel.desiredY, 0f);
        assertArrayEquals(sequential.distance, parallel.distance, 0f);
    }

    private static AIData randomData(int count, Random random){
        AIData data = new AIData(count);
        for(int i = 0; i < count; i++){
            data.add(i,
                (random.nextFloat() - 0.5f) * 2000f,
                (random.nextFloat() - 0.5f) * 2000f,
                (random.nextFloat() - 0.5f) * 2000f,
                (random.nextFloat() - 0.5f) * 2000f,
                0);
        }
        return data;
    }
}