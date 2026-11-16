package orbital.engine.threading;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class OrbitalThreadConfigTest{

    @Test
    void automaticLeavesOneProcessorForTheGameThread(){
        assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
            OrbitalThreadConfig.automaticWorkers());
    }

    @Test
    void storedRequestsAreResolvedWithoutFakeCeilings(){
        assertEquals(OrbitalThreadConfig.automaticWorkers(), OrbitalThreadConfig.resolve(0));
        assertEquals(OrbitalThreadConfig.automaticWorkers(), OrbitalThreadConfig.resolve(-8));
        assertEquals(2, OrbitalThreadConfig.resolve(2));
        assertEquals(4, OrbitalThreadConfig.resolve(4));
        assertEquals(8, OrbitalThreadConfig.resolve(8));
        assertEquals(OrbitalThreadConfig.maxWorkers, OrbitalThreadConfig.resolve(OrbitalThreadConfig.maxWorkers + 1000));
    }

    @Test
    void sliderCanReachWideMachines(){
        assertTrue(OrbitalThreadConfig.maxSelectable() >= 32,
            "the worker slider must scale to 32+ logical processors");
        assertTrue(OrbitalThreadConfig.maxSelectable() <= OrbitalThreadConfig.maxWorkers);
    }

    @Test
    void jobSystemClampsRequestsIntoTheSupportedRange(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(0, 8)){
            assertEquals(OrbitalJobSystem.minimumWorkers, jobs.workerCount());
        }
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(10_000, 8)){
            assertEquals(OrbitalThreadConfig.maxWorkers, jobs.workerCount());
        }
    }
}
