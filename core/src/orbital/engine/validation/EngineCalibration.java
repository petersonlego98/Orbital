package orbital.engine.validation;

import arc.Core;
import orbital.engine.ai.AIDecisionKernel;
import orbital.engine.combat.ProjectileKernel;
import orbital.engine.memory.*;
import orbital.engine.particles.ParticleVertexBuilder;
import orbital.engine.threading.OrbitalJobSystem;
import orbital.engine.units.UnitMovementKernel;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Measures the real cost of one row in each subsystem and derives the worker-count threshold from
 * it, instead of trusting a fixed number copied from a benchmark on someone else's machine.
 *
 * <p>The model is deliberately simple and honest: a pool handoff has a fixed cost, so parallel work
 * only pays when the sequential cost of the rows exceeds that cost. Both numbers are measured on
 * the machine running the game, cached, and clamped so a noisy sample cannot turn the engine on for
 * 50 rows or off for a million.</p>
 */
public final class EngineCalibration{
    /** Could not be a good idea to parallelize fewer rows than this, whatever the measurement. */
    public static final int minimumThreshold = 256;
    /** Never let a noisy sample move a threshold beyond four times its documented default. */
    public static final int maximumFactor = 4;
    /** Persisted switch; on by default because it only affects the opt-in parallel phases. */
    public static final String key = "orbitalautocalibrate";

    private static final int sampleRows = 4096;
    private static final int passes = 3;
    private static final ConcurrentHashMap<String, Integer> thresholds = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Double> perRowNanos = new ConcurrentHashMap<>();
    private static volatile long dispatchOverheadNanos = -1L;

    private EngineCalibration(){
    }

    public static boolean enabled(){
        //settings may be absent at startup; the safe default is to calibrate
        return Core.settings == null || Core.settings.getBool(key, true);
    }

    /** @return the threshold for a subsystem, clamped around its documented fallback. */
    public static int thresholdFor(String subsystem, int fallback){
        if(!enabled()) return fallback;

        return thresholds.computeIfAbsent(subsystem, name -> compute(name, Math.max(1, fallback)));
    }

    /** @return the measured cost of one row, for diagnostics. Triggers the measurement. */
    public static double perRowNanos(String subsystem){
        return perRowNanos.getOrDefault(subsystem, 0.0);
    }

    public static long dispatchOverheadNanos(){
        if(dispatchOverheadNanos < 0L){
            measureDispatchOverhead();
        }
        return dispatchOverheadNanos;
    }

    /** Drops cached measurements; tests and the debug panel use this after changing settings. */
    public static void reset(){
        thresholds.clear();
        perRowNanos.clear();
        dispatchOverheadNanos = -1L;
    }

    public static String report(){
        StringBuilder out = new StringBuilder("Orbital calibration: ");
        out.append(enabled() ? "on" : "off");
        out.append(String.format(Locale.ROOT, " | handoff %.1f us", dispatchOverheadNanos() / 1000.0));
        for(String name : new String[]{"units", "projectiles", "ai", "particles"}){
            out.append(String.format(Locale.ROOT, " | %s %.1f ns/row", name, perRowNanos(name)));
        }
        return out.toString();
    }

    private static int compute(String subsystem, int fallback){
        double rowNanos = measureRow(subsystem);
        perRowNanos.put(subsystem, rowNanos);
        if(rowNanos <= 0.0) return fallback;

        long overhead = dispatchOverheadNanos();
        double rows = Math.ceil(overhead / rowNanos) * 2.0; //safety factor for jitter
        int result = (int)Math.ceil(rows);
        result = Math.max(Math.min(minimumThreshold, fallback), result);
        return Math.min(result, fallback * maximumFactor);
    }

    /** Time of one pool dispatch with no useful work, after the workers are warm. */
    private static synchronized void measureDispatchOverhead(){
        if(dispatchOverheadNanos >= 0L) return;

        long best = Long.MAX_VALUE;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(2, 64)){
            for(int pass = 0; pass < passes; pass++){
                long started = System.nanoTime();
                jobs.parallelFor("calibration.handoff", 0, 256, ignored -> {});
                best = Math.min(best, System.nanoTime() - started);
            }
        }
        dispatchOverheadNanos = Math.max(1L, best);
    }

    private static double measureRow(String subsystem){
        switch(subsystem){
            case "units": return measureUnits();
            case "projectiles": return measureProjectiles();
            case "ai": return measureAI();
            case "particles": return measureParticles();
            default: return 0.0;
        }
    }

    private static double measureUnits(){
        long best = Long.MAX_VALUE;
        for(int pass = 0; pass < passes; pass++){
            UnitData units = units(sampleRows);
            float[] drag = drag(sampleRows);
            long started = System.nanoTime();
            for(int i = 0; i < sampleRows; i++){
                UnitMovementKernel.integrate(units, drag, i, 1f / 60f);
            }
            best = Math.min(best, System.nanoTime() - started);
        }
        return best / (double)sampleRows;
    }

    private static double measureProjectiles(){
        long best = Long.MAX_VALUE;
        for(int pass = 0; pass < passes; pass++){
            ProjectileData bullets = projectiles(sampleRows);
            float[] drag = drag(sampleRows);
            long started = System.nanoTime();
            for(int i = 0; i < sampleRows; i++){
                ProjectileKernel.integrate(bullets, drag, i, 1f / 60f);
            }
            best = Math.min(best, System.nanoTime() - started);
        }
        return best / (double)sampleRows;
    }

    private static double measureAI(){
        long best = Long.MAX_VALUE;
        for(int pass = 0; pass < passes; pass++){
            AIData data = ai(sampleRows);
            long started = System.nanoTime();
            for(int i = 0; i < sampleRows; i++){
                AIDecisionKernel.decide(data, i);
            }
            best = Math.min(best, System.nanoTime() - started);
        }
        return best / (double)sampleRows;
    }

    private static double measureParticles(){
        long best = Long.MAX_VALUE;
        float[] data = particles(sampleRows);
        float[] vertices = new float[sampleRows * ParticleVertexBuilder.vertexStride];
        for(int pass = 0; pass < passes; pass++){
            long started = System.nanoTime();
            for(int i = 0; i < sampleRows; i++){
                ParticleVertexBuilder.writeVertex(data, vertices, i);
            }
            best = Math.min(best, System.nanoTime() - started);
        }
        return best / (double)sampleRows;
    }

    private static float[] particles(int count){
        float[] data = new float[count * ParticleVertexBuilder.particleStride];
        Random random = new Random(3456L);
        for(int i = 0; i < count; i++){
            int offset = i * ParticleVertexBuilder.particleStride;
            data[offset] = random.nextFloat();
            data[offset + 1] = 1f;
            data[offset + 2] = random.nextFloat() * 100f;
            data[offset + 3] = random.nextFloat() * 100f;
            data[offset + 6] = 1f;
            data[offset + 7] = 2f;
            data[offset + 8] = random.nextFloat();
        }
        return data;
    }

    private static UnitData units(int count){
        UnitData data = new UnitData(count);
        Random random = new Random(1234L);
        for(int i = 0; i < count; i++){
            data.add(i, random.nextFloat() * 100f, random.nextFloat() * 100f,
                random.nextFloat() * 10f, random.nextFloat() * 10f, 0f, 100f, 0);
        }
        return data;
    }

    private static ProjectileData projectiles(int count){
        ProjectileData data = new ProjectileData(count);
        Random random = new Random(5678L);
        for(int i = 0; i < count; i++){
            data.add(i, random.nextFloat() * 100f, random.nextFloat() * 100f,
                random.nextFloat() * 10f, random.nextFloat() * 10f, 0f, 0f, 0, 0);
        }
        return data;
    }

    private static AIData ai(int count){
        AIData data = new AIData(count);
        Random random = new Random(9012L);
        for(int i = 0; i < count; i++){
            data.add(i, random.nextFloat() * 100f, random.nextFloat() * 100f,
                random.nextFloat() * 100f, random.nextFloat() * 100f, 0);
        }
        return data;
    }

    private static float[] drag(int count){
        float[] drag = new float[count];
        for(int i = 0; i < count; i++){
            drag[i] = 0.05f;
        }
        return drag;
    }
}