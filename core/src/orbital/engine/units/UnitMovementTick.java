package orbital.engine.units;

import arc.Core;
import arc.math.Mathf;
import arc.util.Time;
import orbital.Vars;
import orbital.engine.OrbitalEngine;
import orbital.engine.memory.ProjectileData;
import orbital.engine.memory.UnitData;
import orbital.engine.simulation.SimulationPipeline;
import orbital.engine.simulation.SimulationSnapshot;
import orbital.gen.Groups;
import orbital.gen.Unit;

/**
 * Parallel velocity integration for units: the first real unit phase wired into the game loop.
 *
 * <p>Vanilla integration lives in {@code VelComp.update()}, which runs first for every unit and is
 * a pure per-unit function: {@code x += vel.x * delta}, zero a velocity axis that did not move the
 * unit ({@code Mathf.equal}), then scale velocity by {@code max(1 - drag * delta, 0)}.</p>
 *
 * <p><b>Only units whose move is a plain position add are captured.</b> {@code VelComp.move()}
 * delegates to the collision system whenever the unit has a solidity predicate - legs, crawlers,
 * water units and elevation units all do - and that collision step cannot be reproduced by a
 * snapshot, because it depends on the world and on the other entities. Those units keep the
 * vanilla path. The captured set is therefore the air units, exactly the case the engine
 * architecture lists as the first priority.</p>
 *
 * <p>The flow follows the engine contract: capture on the game thread, simulate on workers (each
 * index touches only its own row), commit on the game thread. Commit writes the integrated
 * position and velocity back and raises the {@code engineMoved} flag so {@code VelComp.update()}
 * skips its own integration for that tick - without it every unit would move twice. A flag that
 * survives the tick (the engine stopped engaging, the unit was removed mid-frame) is cleared on
 * the next tick, so a unit can never lose a frame of movement.</p>
 *
 * <p>Everything stays vanilla below {@link #parallelThreshold} eligible units, when the engine is
 * not parallel-capable, or while the preference is off, which is the default.</p>
 */
public final class UnitMovementTick{
    /** Persisted preference key used by Settings > Advanced Graphics. */
    public static final String key = "orbitalparallelmovement";
    /** Below this many eligible units the vanilla sequential integration is cheaper than pool overhead. */
    public static final int parallelThreshold = 2048;

    /** Diagnostics evidence: whether the most recent tick actually ran the parallel integration. */
    public static volatile boolean lastEngaged;
    /** Diagnostics evidence: how many unit rows the most recent engaged tick committed. */
    public static volatile int lastEngagedUnits;
    /** Diagnostics evidence: how many units the most recent tick found eligible for the engine. */
    public static volatile int lastEligibleUnits;

    private static final UnitMovementTick instance = new UnitMovementTick();

    private final UnitData staging = new UnitData();
    private final ProjectileData emptyProjectiles = new ProjectileData();
    private final SimulationSnapshot snapshot = new SimulationSnapshot();
    private long capturedFrames;

    //captured entity references for the commit step; workers never touch these
    private Unit[] refs = new Unit[0];
    private float[] drag = new float[0];
    //units flagged in the previous tick, so a stale flag can be cleared before the next capture
    private Unit[] flagged = new Unit[0];
    private int flaggedCount;

    //test-only threshold override; -1 keeps the production threshold
    private static int thresholdOverride = -1;

    private UnitMovementTick(){
    }

    /** @return whether the preference turns the parallel integration on. */
    public static boolean enabled(){
        return Core.settings != null && Core.settings.getBool(key, false);
    }

    /** @return the unit count the engine needs before it engages. */
    public static int threshold(){
        return thresholdOverride > 0 ? thresholdOverride : parallelThreshold;
    }

    /**
     * Lowers the engagement threshold for a test, so the phase can be exercised without spawning
     * thousands of units. Pass -1 to restore the production threshold.
     */
    public static void overrideThreshold(int units){
        thresholdOverride = units;
    }

    /**
     * @return whether the unit may be integrated by the engine. Units with a solidity predicate
     * move through the collision system in vanilla and are never captured.
     */
    public static boolean capturable(Unit unit){
        return unit != null && unit.solidity() == null;
    }

    /**
     * Runs capture, parallel simulation and commit for this tick's unit movement. Must be called
     * from the game thread, right before {@code Groups.unit.update()}. Does nothing when the
     * preference is off, the platform cannot run the pool, or the group is below the threshold.
     */
    public static void tick(){
        lastEngaged = false;
        lastEngagedUnits = 0;
        //a flag left over from a previous tick must never freeze a unit for a frame
        instance.clearStaleFlags();
        if(!enabled() || !OrbitalEngine.parallelCapable()) return;
        instance.run();
    }

    private void clearStaleFlags(){
        for(int i = 0; i < flaggedCount; i++){
            Unit unit = flagged[i];
            if(unit != null) unit.engineMoved(false);
        }
        flaggedCount = 0;
    }

    private void run(){
        int total = Groups.unit.size();

        //first pass: count the units the engine may integrate, without touching any buffer
        int eligible = 0;
        for(Unit unit : Groups.unit){
            if(Vars.net.client() && !unit.isLocal()) continue; //same gate as VelComp.update
            if(!capturable(unit)) continue; //ground, water and elevation units move with collisions
            eligible++;
        }
        lastEligibleUnits = eligible;
        if(eligible < threshold()) return;

        SimulationPipeline pipeline = OrbitalEngine.shared().pipeline();

        //capture: copy the authoritative fields the phase needs into SoA, keeping entity order
        staging.clear();
        if(refs.length < total){
            refs = new Unit[total];
            drag = new float[total];
            flagged = new Unit[total];
        }

        int count = 0;
        for(Unit unit : Groups.unit){
            if(Vars.net.client() && !unit.isLocal()) continue;
            if(!capturable(unit)) continue;

            refs[count] = unit;
            drag[count] = unit.drag();
            staging.add(unit.id(), unit.x(), unit.y(), unit.vel().x, unit.vel().y, 0f, 0f, 0);
            count++;
        }
        if(count == 0) return;

        //per-unit drag rides in a frame-scoped scratch buffer; UnitData carries position and velocity
        float[] dragBuffer = pipeline.buffers().floatScratch(count);
        System.arraycopy(drag, 0, dragBuffer, 0, count);

        if(snapshot.isOpen()) snapshot.clear();
        pipeline.capture(snapshot, capturedFrames++, staging, emptyProjectiles);

        float delta = Time.delta;
        pipeline.simulateUnits(snapshot, (state, index) -> step(state.units(), dragBuffer, index, delta));

        pipeline.commit(snapshot, (units, index) -> {
            Unit unit = refs[index];
            unit.x(units.x[index]);
            unit.y(units.y[index]);
            unit.vel().set(units.velocityX[index], units.velocityY[index]);
            unit.engineMoved(true);
        }, null);

        pipeline.endFrame();

        System.arraycopy(refs, 0, flagged, 0, count);
        flaggedCount = count;

        lastEngaged = true;
        lastEngagedUnits = count;
    }

    /**
     * The vanilla {@code VelComp.update()} math for one unit row, applied in place.
     *
     * <p>Order matters and mirrors the comp exactly: move by {@code vel * delta}, zero a velocity
     * axis whose move did not change the position ({@code Mathf.equal}), then apply drag. The
     * implementation lives in {@link UnitMovementKernel} so the phase, the game loop and the
     * validation harness cannot drift apart.</p>
     */
    static void step(UnitData units, float[] dragBuffer, int index, float delta){
        UnitMovementKernel.integrate(units, dragBuffer, index, delta);
    }
}
