package orbital.engine.ai;

import arc.Core;
import orbital.engine.OrbitalEngine;
import orbital.engine.memory.*;
import orbital.engine.simulation.SimulationPipeline;
import orbital.engine.simulation.SimulationSnapshot;
import orbital.engine.validation.EngineCalibration;
import orbital.gen.Groups;
import orbital.gen.Unit;

/**
 * Parallel AI decisions for air units, gated by an opt-in preference.
 *
 * <p>Captures each air unit's aim point into an SoA buffer on the game thread, runs
 * {@link AIDecisionKernel} on the shared pool and commits the desired vectors on the game thread.
 * The vanilla AI controllers, weapons, events and construction are left untouched, so enabling this
 * phase never changes multiplayer authority: the decisions are exposed through
 * {@link #lastDecisions()} for diagnostics and for the next integration step, not applied to
 * entities. Wiring them into a controller would be a separate, explicit change.</p>
 */
public final class AITick{
    /** Persisted preference key used by Settings > Advanced Graphics. */
    public static final String key = "orbitalparallelai";
    /** Starting threshold; the benchmark decides whether it should move. */
    public static final int parallelThreshold = 1024;

    public static volatile boolean lastEngaged;
    public static volatile int lastEngagedUnits;
    public static volatile int lastEligibleUnits;

    private static final AITick instance = new AITick();

    private final AIData staging = new AIData();
    private final AIData decisions = new AIData();
    private final UnitData emptyUnits = new UnitData();
    private final ProjectileData emptyProjectiles = new ProjectileData();
    private final SimulationSnapshot snapshot = new SimulationSnapshot();
    private long capturedFrames;

    private static int thresholdOverride = -1;

    private AITick(){
    }

    public static boolean enabled(){
        return Core.settings != null && Core.settings.getBool(key, false);
    }

    public static int threshold(){
        if(thresholdOverride > 0) return thresholdOverride;
        return EngineCalibration.thresholdFor("ai", parallelThreshold);
    }

    public static void overrideThreshold(int units){
        thresholdOverride = units;
    }

    /** @return the most recently committed steering decisions, valid for [0, count). */
    public static AIData lastDecisions(){
        return instance.decisions;
    }

    public static void tick(){
        lastEngaged = false;
        lastEngagedUnits = 0;
        if(!enabled() || !OrbitalEngine.parallelCapable()) return;
        instance.run();
    }

    private void run(){
        int eligible = 0;
        for(Unit unit : Groups.unit){
            if(!unitMovementEligible(unit)) continue;
            if(unit.aimX == 0f && unit.aimY == 0f) continue; //no target for this unit yet
            eligible++;
        }
        lastEligibleUnits = eligible;
        if(eligible < threshold()) return;

        SimulationPipeline pipeline = OrbitalEngine.shared().pipeline();

        staging.clear();
        for(Unit unit : Groups.unit){
            if(!unitMovementEligible(unit)) continue;
            if(unit.aimX == 0f && unit.aimY == 0f) continue;
            staging.add(unit.id(), unit.x(), unit.y(), unit.aimX, unit.aimY, 0);
        }
        if(staging.count == 0) return;

        //the snapshot carries the owner thread, so the commit cannot drift to a worker
        if(snapshot.isOpen()) snapshot.clear();
        pipeline.capture(snapshot, capturedFrames++, emptyUnits, emptyProjectiles);
        pipeline.parallelFor("simulation.ai", staging.count,
            index -> AIDecisionKernel.decide(staging, index));

        decisions.copyFrom(staging);
        pipeline.commit(snapshot, null, null);

        lastEngaged = true;
        lastEngagedUnits = staging.count;
    }

    /** AI decisions use the same air-only gate as the movement phase. */
    private static boolean unitMovementEligible(Unit unit){
        return unit != null && unit.solidity() == null;
    }
}