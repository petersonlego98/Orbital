package orbital.engine.simulation;

import orbital.engine.memory.*;
import orbital.engine.threading.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

class SimulationPipelineTest{

    @Test
    void captureSimulateAndCommitApplyEveryRowExactlyOnce(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 8)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            UnitData source = units(500);
            ProjectileData projectiles = projectiles(40);
            SimulationSnapshot snapshot = new SimulationSnapshot();

            pipeline.capture(snapshot, 7, source, projectiles);

            assertEquals(500, snapshot.units().count);
            assertEquals(40, snapshot.projectiles().count);
            assertEquals(7, snapshot.frame());
            assertEquals(Thread.currentThread(), snapshot.owner());
            assertTrue(snapshot.isOpen());

            AtomicIntegerArray visited = new AtomicIntegerArray(500);
            pipeline.simulateUnits(snapshot, (state, index) -> {
                visited.incrementAndGet(index);
                state.units().health[index] += 5f;
            });

            for(int i = 0; i < 500; i++){
                assertEquals(1, visited.get(i), "unit " + i + " was simulated more than once");
                assertEquals(105f, snapshot.units().health[i]);
            }

            AtomicInteger committed = new AtomicInteger();
            SimulationCommit commit = pipeline.commit(snapshot,
                (units, index) -> {
                    assertEquals(105f, units.health[index]);
                    committed.incrementAndGet();
                },
                (projectilesData, index) -> {
                    assertEquals(index, projectilesData.entityIds[index]);
                });

            assertEquals(500, committed.get());
            assertEquals(500, commit.units());
            assertEquals(40, commit.projectiles());
            assertEquals(7, commit.frame());
            assertTrue(commit.successful());
            assertEquals(SimulationSnapshot.State.COMMITTED, snapshot.state());
        }
    }

    @Test
    void capturingAgainBeforeCommitIsRejected(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(2, 8)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            SimulationSnapshot snapshot = new SimulationSnapshot();

            pipeline.capture(snapshot, 0, units(4), projectiles(0));
            assertThrows(IllegalStateException.class,
                () -> pipeline.capture(snapshot, 1, units(4), projectiles(0)));
        }
    }

    @Test
    void commitFromAnotherThreadIsRejected(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(2, 8)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            SimulationSnapshot snapshot = new SimulationSnapshot();
            pipeline.capture(snapshot, 0, units(16), projectiles(0));

            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "commit-probe");
                thread.setDaemon(true);
                return thread;
            });

            try{
                Future<?> future = executor.submit(() -> pipeline.commit(snapshot, null, null));
                ExecutionException failure = assertThrows(ExecutionException.class, future::get);
                assertTrue(failure.getCause() instanceof IllegalStateException,
                    "expected the owner-thread guard, got " + failure.getCause());
            }finally{
                executor.shutdownNow();
            }

            //the failed remote commit must not have consumed the snapshot
            assertTrue(snapshot.isOpen());
            SimulationCommit commit = pipeline.commit(snapshot, null, null);
            assertTrue(commit.successful());
        }
    }

    @Test
    void committingTwiceIsRejected(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(2, 8)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            SimulationSnapshot snapshot = new SimulationSnapshot();
            pipeline.capture(snapshot, 0, units(8), projectiles(0));
            pipeline.commit(snapshot, null, null);

            assertThrows(IllegalStateException.class, () -> pipeline.commit(snapshot, null, null));
        }
    }

    @Test
    void simulationFacadeReusesOneSnapshotAcrossFrames(){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(2, 8)){
            OrbitalSimulation simulation = new OrbitalSimulation(new SimulationPipeline(jobs));

            for(int frame = 0; frame < 3; frame++){
                simulation.capture(units(20), projectiles(5));
                simulation.simulateUnits((snapshot, index) -> snapshot.units().velocityX[index] += 1f);

                AtomicInteger applied = new AtomicInteger();
                SimulationCommit commit = simulation.commit((units, index) -> applied.incrementAndGet(), null);

                assertEquals(20, commit.units());
                assertEquals(20, applied.get());
                assertEquals(1f, simulation.snapshot().units().velocityX[0]);
            }

            assertEquals(3, simulation.capturedFrames());
        }
    }

    private static UnitData units(int count){
        UnitData data = new UnitData();
        for(int i = 0; i < count; i++){
            data.add(i, i, -i, 0f, 0f, 0f, 100f, 0);
        }
        return data;
    }

    private static ProjectileData projectiles(int count){
        ProjectileData data = new ProjectileData();
        for(int i = 0; i < count; i++){
            data.add(i, i, i, 0f, 0f, 10f, 1f, 0, 0);
        }
        return data;
    }
}
