package orbital.engine.simulation;

import orbital.engine.memory.*;

/**
 * The read side of a simulation frame: primitive SoA buffers copied from the authoritative world.
 *
 * <p>Lifecycle: {@code EMPTY -> CAPTURED -> SIMULATED -> COMMITTED}. The owner thread is recorded
 * during capture, and {@link SimulationPipeline#commit} refuses to run anywhere else. That is the
 * core safety rule of the engine: workers may fill these buffers, but only the thread that took
 * the snapshot may apply results to the game.</p>
 *
 * <p>One instance is reused between frames; {@link #units()} and {@link #projectiles()} keep their
 * backing arrays so a steady-state frame allocates nothing here.</p>
 */
public final class SimulationSnapshot{
    public enum State{ EMPTY, CAPTURED, SIMULATED, COMMITTED }

    private final UnitData units = new UnitData();
    private final ProjectileData projectiles = new ProjectileData();

    private State state = State.EMPTY;
    private long frame;
    private Thread owner;
    private long captureNanos;
    private long simulationNanos;

    void capture(long frame, Thread owner, UnitData sourceUnits, ProjectileData sourceProjectiles){
        this.frame = frame;
        this.owner = owner;
        this.captureNanos = 0L;
        this.simulationNanos = 0L;
        units.copyFrom(sourceUnits);
        projectiles.copyFrom(sourceProjectiles);
        state = State.CAPTURED;
    }

    void markSimulated(){
        if(state == State.CAPTURED) state = State.SIMULATED;
    }

    void markCommitted(){
        state = State.COMMITTED;
    }

    void addCaptureNanos(long nanos){
        captureNanos += Math.max(0L, nanos);
    }

    void addSimulationNanos(long nanos){
        simulationNanos += Math.max(0L, nanos);
    }

    public boolean isOpen(){
        return state == State.CAPTURED || state == State.SIMULATED;
    }

    public boolean isCaptured(){
        return state != State.EMPTY;
    }

    public State state(){
        return state;
    }

    public long frame(){
        return frame;
    }

    public Thread owner(){
        return owner;
    }

    public UnitData units(){
        return units;
    }

    public ProjectileData projectiles(){
        return projectiles;
    }

    public long captureNanos(){
        return captureNanos;
    }

    public long simulationNanos(){
        return simulationNanos;
    }

    public void clear(){
        units.clear();
        projectiles.clear();
        state = State.EMPTY;
        owner = null;
        captureNanos = 0L;
        simulationNanos = 0L;
    }
}
