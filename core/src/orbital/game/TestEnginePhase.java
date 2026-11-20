package orbital.game;

import arc.Core;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import orbital.content.UnitTypes;
import orbital.core.PerfCounter;
import orbital.engine.OrbitalEngine;
import orbital.engine.units.UnitMovementTick;
import orbital.gen.Groups;
import orbital.gen.Unit;
import orbital.type.UnitType;

import static orbital.Vars.*;

/**
 * Fase 1 of the test mode: a live stress battle that measures every claim of the Orbital engine
 * work, so the next parallelization step is chosen from numbers instead of impressions.
 *
 * <p>What it tests, all at once, with thousands of units actually fighting (AI controllers,
 * weapons, bullets, physics and movement all active):</p>
 *
 * <ul>
 *   <li>per-subsystem frame cost: {@code unitUpdate}, {@code bulletUpdate}, {@code entityMisc},
 *   {@code buildingUpdate}, {@code powerUpdate} and the whole frame, straight from
 *   {@link PerfCounter#valueMs()};</li>
 *   <li>whether the parallel movement engine actually engaged this tick (the 2048-unit threshold
 *   is the gate), and how many rows it committed;</li>
 *   <li>the engine's own capture / simulate / commit averages from the task profiler;</li>
 *   <li>an A/B switch that flips {@code orbitalparallelmovement} live, so the same battle can be
 *   watched with the engine on and off.</li>
 * </ul>
 *
 * <p>The battle spawns flying combat units of two opposing teams, interleaved inside one disc
 * placed away from the player, in batches spread over several frames so the generation itself
 * never freezes the game. Unit cap is disabled for the session - it is a disposable test
 * environment.</p>
 */
public final class TestEnginePhase{
    /** Battle sizes offered by the panel. 2048 is exactly the engine engagement threshold. */
    public static final int[] sizes = {2048, 4096, 8000};
    /** Units spawned per frame, so filling 8000 slots never stalls a single frame. */
    static final int spawnBatch = 96;
    /** How often the evidence line is written to the log, in milliseconds. */
    static final long logIntervalMillis = 10_000L;
    /** The combat unit used for both sides of the stress battle. */
    static final UnitType battleType = UnitTypes.flare;

    private static final Seq<Unit> battleUnits = new Seq<>(false, 512, Unit.class);

    private static boolean running;
    private static int target, spawned;
    private static float radius;
    private static long lastLogMillis;

    private TestEnginePhase(){
    }

    /** @return whether a stress battle is live (generating or being fought). */
    public static boolean active(){
        return running;
    }

    /** @return true while units are still being spawned for the current battle. */
    public static boolean generating(){
        return running && spawned < target;
    }

    public static int targetUnits(){
        return target;
    }

    public static int spawnedUnits(){
        return spawned;
    }

    /** Clears every phase trace; called when the test mode starts a fresh session. */
    public static void reset(){
        running = false;
        target = 0;
        spawned = 0;
        battleUnits.clear();
    }

    /**
     * Starts a stress battle with {@code count} combat units fighting each other.
     *
     * @return whether the battle could be started (test mode active and a positive count).
     */
    /** Cap for integrated/Intel GPUs: thousands of armed AI units collapse their frame rate. */
    private static final int intelUnitCap = 256;

    public static boolean start(int count){
        //the profiling hook runs while playMap() is still switching worlds, so allow a controlled
        //bypass there; normal callers still require the test mode
        if(count <= 0 || (!TestNewFeaturesMode.active() && System.getProperty("orbital.profileunits") == null)) return false;

        //-Dorbital.profileunits=1 lifts the cap for a controlled A/B measurement only
        if(orbital.graphics.IntelGpuCheck.wasIntel() && count > intelUnitCap
            && System.getProperty("orbital.profileunits") == null){
            count = intelUnitCap;
            arc.util.Log.info("[Fase 1] Intel GPU detected: limiting stress battle to @ units", count);
        }

        stop();
        state.rules.disableUnitCap = true;
        target = count;
        spawned = 0;
        //one interleaved disc per battle; the area grows with the unit count so the fighters
        //never pile up in a single spot
        radius = 300f + count / 8f;
        running = true;
        lastLogMillis = Time.millis();
        return true;
    }

    /** Removes every unit this phase spawned, wherever the battle got to. */
    public static void stop(){
        running = false;
        target = 0;
        spawned = 0;

        for(int i = 0; i < battleUnits.size; i++){
            Unit unit = battleUnits.items[i];
            if(unit != null && unit.isAdded()) unit.remove();
        }
        battleUnits.clear();
    }

    /**
     * Drives batch spawning and the periodic evidence log. Called from the test panel every
     * frame; the headless tests call it directly to fill a battle synchronously.
     */
    public static void update(){
        if(!running) return;

        if(spawned < target){
            //the disc sits away from the player's spawn point, so the fighter swarm is not
            //dropped on top of the hull
            float cx = world.unitWidth() / 2f;
            float cy = Math.max(world.unitHeight() / 4f, world.unitHeight() / 2f - radius * 1.8f);

            int batch = Math.min(spawnBatch, target - spawned);
            for(int i = 0; i < batch; i++){
                //interleaved teams: adjacent fighters are enemies, so combat starts immediately
                Team team = (spawned & 1) == 0 ? Team.crux : Team.sharded;
                float angle = Mathf.random(360f);
                float dist = Mathf.sqrt(Mathf.random()) * radius;
                float x = Mathf.clamp(cx + Angles.trnsx(angle, dist), 8f, world.unitWidth() - 8f);
                float y = Mathf.clamp(cy + Angles.trnsy(angle, dist), 8f, world.unitHeight() - 8f);

                battleUnits.add(battleType.spawn(team, x, y));
                spawned++;
            }
            return;
        }

        //periodic evidence line: numbers to read after the session, not impressions
        if(Time.timeSinceMillis(lastLogMillis) > logIntervalMillis){
            lastLogMillis = Time.millis();
            Log.info("[Fase 1] unid=@ proj=@ | unitUpdate=@ ms | bulletUpdate=@ ms | motor=@",
                Groups.unit.size(), Groups.bullet.size(),
                fmt(PerfCounter.unitUpdate.valueMs()), fmt(PerfCounter.bulletUpdate.valueMs()),
                engineStatus());
        }
    }

    /** @return units, bullets and fps for the live panel. */
    public static String statsLine(){
        return Core.bundle.format("mode.testfase1.stats.units",
            Groups.unit.size(), Groups.bullet.size(), Core.graphics.getFramesPerSecond());
    }

    /** @return the three cheapest-to-read subsystem timings for the live panel. */
    public static String msLine1(){
        return Core.bundle.format("mode.testfase1.stats.ms",
            fmt(PerfCounter.unitUpdate.valueMs()), fmt(PerfCounter.bulletUpdate.valueMs()),
            fmt(PerfCounter.entityMisc.valueMs()));
    }

    /** @return the remaining subsystem timings plus the whole frame for the live panel. */
    public static String msLine2(){
        return Core.bundle.format("mode.testfase1.stats.ms2",
            fmt(PerfCounter.buildingUpdate.valueMs()), fmt(PerfCounter.powerUpdate.valueMs()),
            fmt(PerfCounter.frame.valueMs()));
    }

    /** @return why the engine is or is not running the parallel movement this tick. */
    public static String engineStatus(){
        if(!OrbitalEngine.parallelCapable()){
            return Core.bundle.get("mode.testfase1.engine.unsupported");
        }
        if(!UnitMovementTick.enabled()){
            return Core.bundle.get("mode.testfase1.engine.off");
        }
        if(!UnitMovementTick.lastEngaged){
            return Core.bundle.format("mode.testfase1.engine.threshold",
                UnitMovementTick.lastEligibleUnits, UnitMovementTick.threshold());
        }
        return Core.bundle.format("mode.testfase1.engine.engaged", UnitMovementTick.lastEngagedUnits);
    }

    /** @return the engine's own capture/simulate/commit averages, or an empty string when idle. */
    public static String enginePhases(){
        if(!OrbitalEngine.isStarted()) return "";

        var profiler = OrbitalEngine.shared().taskProfiler().snapshot();
        double capture = phase(profiler, "simulation.capture");
        double simulate = phase(profiler, "simulation.units");
        double commit = phase(profiler, "simulation.commit");
        return Core.bundle.format("mode.testfase1.engine.phases", capture, simulate, commit);
    }

    private static double phase(java.util.Map<String, orbital.diagnostics.TaskProfiler.Entry> snapshot, String name){
        var entry = snapshot.get(name);
        return entry == null ? 0.0 : entry.averageMillis();
    }

    /** @return the persisted A/B state of the parallel movement preference. */
    public static boolean engineToggle(){
        return UnitMovementTick.enabled();
    }

    /** Flips the parallel movement preference live; the next tick already honors it. */
    public static void setEngineToggle(boolean on){
        if(Core.settings != null){
            Core.settings.put(UnitMovementTick.key, on);
        }
    }

    private static String fmt(float ms){
        return String.format("%.2f", ms);
    }
}
