package orbital.engine.simulation;

import orbital.diagnostics.*;
import orbital.engine.memory.*;
import orbital.engine.threading.*;

/**
 * Runs one simulation frame through the three safe stages.
 *
 * <ol>
 *   <li><b>Capture</b> on the owner thread: copies authoritative fields into SoA buffers.</li>
 *   <li><b>Simulate</b>: workers read the snapshot and write only their own index.</li>
 *   <li><b>Commit</b> on the owner thread: applies results in a defined order (units, then
 *   projectiles), exactly once, and never inside a worker.</li>
 * </ol>
 *
 * <p>Commit order is deterministic on purpose. Multiplayer consistency depends on every client
 * applying the same changes in the same order, and a work-stealing pool does not promise any
 * order at all.</p>
 */
public final class SimulationPipeline{
    private final OrbitalJobSystem jobs;
    private final TaskProfiler taskProfiler;
    private final SimulationProfiler simulationProfiler;
    private final BufferManager buffers;

    public SimulationPipeline(OrbitalJobSystem jobs){
        this(jobs, new TaskProfiler(), new SimulationProfiler());
    }

    public SimulationPipeline(OrbitalJobSystem jobs, TaskProfiler taskProfiler, SimulationProfiler simulationProfiler){
        this.jobs = jobs;
        this.taskProfiler = taskProfiler;
        this.simulationProfiler = simulationProfiler;
        this.buffers = new BufferManager();
    }

    /** Copies authoritative state into the snapshot. Must run on the thread that will commit. */
    public void capture(SimulationSnapshot snapshot, long frame, UnitData units, ProjectileData projectiles){
        if(snapshot.isOpen()){
            throw new IllegalStateException("snapshot " + snapshot.frame() + " is still open; commit it first");
        }

        long started = System.nanoTime();
        snapshot.capture(frame, Thread.currentThread(), units, projectiles);
        long elapsed = System.nanoTime() - started;
        snapshot.addCaptureNanos(elapsed);
        taskProfiler.record("simulation.capture", snapshot.units().count + snapshot.projectiles().count, elapsed);
        simulationProfiler.recordCapture(elapsed);
    }

    public void simulateUnits(SimulationSnapshot snapshot, UnitStep step){
        requireOpen(snapshot);
        if(step == null) return;

        long started = System.nanoTime();
        jobs.parallelFor("simulation.units", 0, snapshot.units().count,
            index -> step.step(snapshot, index));
        long elapsed = System.nanoTime() - started;
        snapshot.addSimulationNanos(elapsed);
        snapshot.markSimulated();
        taskProfiler.record("simulation.units", snapshot.units().count, elapsed);
        simulationProfiler.recordSimulation(elapsed);
    }

    public void simulateProjectiles(SimulationSnapshot snapshot, ProjectileStep step){
        requireOpen(snapshot);
        if(step == null) return;

        long started = System.nanoTime();
        jobs.parallelFor("simulation.projectiles", 0, snapshot.projectiles().count,
            index -> step.step(snapshot, index));
        long elapsed = System.nanoTime() - started;
        snapshot.addSimulationNanos(elapsed);
        snapshot.markSimulated();
        taskProfiler.record("simulation.projectiles", snapshot.projectiles().count, elapsed);
        simulationProfiler.recordSimulation(elapsed);
    }

    /**
     * Applies the snapshot to the game on the owner thread.
     *
     * @throws IllegalStateException when called from any other thread, or when the snapshot is not
     * open. Both mean a caller violated the capture/simulate/commit transaction.
     */
    public SimulationCommit commit(SimulationSnapshot snapshot, UnitApplier units, ProjectileApplier projectiles){
        requireOpen(snapshot);
        if(Thread.currentThread() != snapshot.owner()){
            throw new IllegalStateException("simulation commit must run on the capturing thread '"
                + snapshot.owner().getName() + "', not '" + Thread.currentThread().getName() + "'");
        }

        long started = System.nanoTime();
        int unitCount = snapshot.units().count;
        if(units != null){
            //sequential on purpose: entity mutation and event order belong to the owner thread
            for(int i = 0; i < unitCount; i++){
                units.apply(snapshot.units(), i);
            }
        }

        int projectileCount = snapshot.projectiles().count;
        if(projectiles != null){
            for(int i = 0; i < projectileCount; i++){
                projectiles.apply(snapshot.projectiles(), i);
            }
        }

        long elapsed = System.nanoTime() - started;
        snapshot.markCommitted();
        taskProfiler.record("simulation.commit", unitCount + projectileCount, elapsed);
        simulationProfiler.recordCommit(elapsed);

        return new SimulationCommit(snapshot.frame(), unitCount, projectileCount,
            snapshot.captureNanos(), snapshot.simulationNanos(), elapsed, true);
    }

    /** Generic parallel range for a phase that does not use the unit/projectile buffers. */
    public void parallelFor(String name, int items, java.util.function.IntConsumer operation){
        jobs.parallelFor(name, 0, items, operation);
    }

    /** Returns scratch space tied to the current frame; call {@link #endFrame()} when done. */
    public BufferManager buffers(){
        return buffers;
    }

    /** Releases frame scratch buffers. Call once per frame after the commit. */
    public void endFrame(){
        buffers.endFrame();
    }

    private void requireOpen(SimulationSnapshot snapshot){
        if(snapshot == null) throw new NullPointerException("snapshot");
        if(!snapshot.isOpen()){
            throw new IllegalStateException("snapshot is " + snapshot.state() + "; capture a new one first");
        }
    }

    public OrbitalJobSystem jobs(){
        return jobs;
    }

    public TaskProfiler taskProfiler(){
        return taskProfiler;
    }

    public SimulationProfiler simulationProfiler(){
        return simulationProfiler;
    }

    @FunctionalInterface
    public interface UnitStep{
        void step(SimulationSnapshot snapshot, int index);
    }

    @FunctionalInterface
    public interface ProjectileStep{
        void step(SimulationSnapshot snapshot, int index);
    }

    @FunctionalInterface
    public interface UnitApplier{
        void apply(UnitData units, int index);
    }

    @FunctionalInterface
    public interface ProjectileApplier{
        void apply(ProjectileData projectiles, int index);
    }
}
