package orbital.engine.simulation;

import orbital.engine.memory.*;

/**
 * Stateful facade over {@link SimulationPipeline} for the systems that will be wired first.
 *
 * <p>It owns one reusable snapshot and the frame counter, so callers only supply the authoritative
 * buffers from the current world. Nothing here touches Arc or Mindustry types: capture and commit
 * are callbacks, which keeps the transaction explicit and testable without a running game.</p>
 */
public final class OrbitalSimulation{
    private final SimulationPipeline pipeline;
    private final SimulationSnapshot snapshot = new SimulationSnapshot();
    private long capturedFrames;

    public OrbitalSimulation(SimulationPipeline pipeline){
        this.pipeline = pipeline;
    }

    /** Owner thread: copies the authoritative SoA state into the snapshot. */
    public SimulationSnapshot capture(UnitData units, ProjectileData projectiles){
        pipeline.capture(snapshot, capturedFrames++, units, projectiles);
        return snapshot;
    }

    public void simulateUnits(SimulationPipeline.UnitStep step){
        pipeline.simulateUnits(snapshot, step);
    }

    public void simulateProjectiles(SimulationPipeline.ProjectileStep step){
        pipeline.simulateProjectiles(snapshot, step);
    }

    /** Owner thread: applies the snapshot and returns the transaction record. */
    public SimulationCommit commit(SimulationPipeline.UnitApplier units,
                                   SimulationPipeline.ProjectileApplier projectiles){
        SimulationCommit commit = pipeline.commit(snapshot, units, projectiles);
        pipeline.endFrame();
        return commit;
    }

    public SimulationSnapshot snapshot(){
        return snapshot;
    }

    public long capturedFrames(){
        return capturedFrames;
    }

    public SimulationPipeline pipeline(){
        return pipeline;
    }
}
