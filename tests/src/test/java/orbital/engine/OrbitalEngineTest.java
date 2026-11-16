package orbital.engine;

import orbital.engine.threading.OrbitalThreadConfig;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OrbitalEngineTest{

    @AfterEach
    void stopSharedEngine(){
        OrbitalEngine.shutdownIfStarted();
    }

    @Test
    void smallWorkloadsStaySequential(){
        AtomicInteger runs = new AtomicInteger();
        assertFalse(OrbitalEngine.parallelForIfUseful("test.small", 10, 1_000, i -> runs.incrementAndGet()));
        assertEquals(0, runs.get(), "a workload below the threshold must not touch the pool");
        assertFalse(OrbitalEngine.isStarted());
    }

    @Test
    void usefulWorkloadsRunOnTheSharedEngine(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine is not a parallel target");

        AtomicInteger runs = new AtomicInteger();
        assertTrue(OrbitalEngine.parallelForIfUseful("test.engine", 5_000, 16, i -> runs.incrementAndGet()));

        assertEquals(5_000, runs.get());
        assertTrue(OrbitalEngine.isStarted());
        assertTrue(OrbitalEngine.activeWorkerCount() >= 1);
        assertTrue(OrbitalEngine.shared().pipeline() != null);
        assertTrue(OrbitalEngine.shared().simulation() != null);
        assertTrue(OrbitalEngine.shared().workerCount() >= 1);
    }

    @Test
    void reconfigureRebuildsOnlyAStartedEngine(){
        OrbitalEngine.reconfigure(2);
        assertFalse(OrbitalEngine.isStarted(), "reconfigure must not start a pool by itself");

        OrbitalEngine engine = OrbitalEngine.shared();
        int initial = engine.workerCount();
        assertTrue(initial >= 1);

        OrbitalEngine.reconfigure(2);
        assertEquals(2, OrbitalEngine.activeWorkerCount());
        assertNotSame(engine, OrbitalEngine.shared());
    }

    @Test
    void shutdownIsSafeWithoutAStartedEngine(){
        OrbitalEngine.shutdownIfStarted();
        OrbitalEngine.shutdownIfStarted();
        assertFalse(OrbitalEngine.isStarted());
    }
}
