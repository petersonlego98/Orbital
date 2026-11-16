package orbital.engine.combat;

import arc.Core;
import arc.util.Time;
import orbital.engine.OrbitalEngine;
import orbital.engine.memory.ProjectileData;
import orbital.engine.memory.UnitData;
import orbital.engine.simulation.SimulationPipeline;
import orbital.engine.simulation.SimulationSnapshot;
import orbital.engine.simd.SimdNative;
import orbital.engine.simd.SimdOperations;
import orbital.engine.simd.SimdSoaBuffer;
import orbital.engine.validation.EngineCalibration;
import orbital.gen.Bullet;
import orbital.gen.Groups;

/**
 * Parallel velocity integration for bullets, gated by an opt-in preference.
 *
 * <p>The flow is the same as for units: capture on the game thread, simulate on workers (each index
 * touches only its own row), commit on the game thread. Collisions, lifetime, movers, acceleration
 * and bullet behaviour stay in the vanilla {@code BulletComp.update()}; only the movement block is
 * moved to the engine, and the {@code engineMoved} flag prevents a second integration. The commit
 * applies rows in group order so the resulting state does not depend on worker scheduling.</p>
 *
 * <p>Bullets flagged {@code justSpawned} are never captured: vanilla deliberately leaves them at the
 * weapon's muzzle for their first frame. Everything stays vanilla below the threshold, when the
 * platform cannot run the pool, or while the preference is off, which is the default.</p>
 */
public final class ProjectileTick{
    /** Persisted preference key used by Settings > Advanced Graphics. */
    public static final String key = "orbitalparallelbullets";
    /** Starting threshold; the test mode's numbers decide whether it should move. */
    public static final int parallelThreshold = 1024;
    /** Rows per native SIMD call inside a worker's chunk. */
    public static final int simdBatch = 256;

    /** Diagnostics evidence: whether the most recent tick actually ran the parallel integration. */
    public static volatile boolean lastEngaged;
    /** Diagnostics evidence: how many bullet rows the most recent engaged tick committed. */
    public static volatile int lastEngagedBullets;
    /** Diagnostics evidence: how many bullets the most recent tick found eligible. */
    public static volatile int lastEligibleBullets;

    private static final ProjectileTick instance = new ProjectileTick();

    private final ProjectileData staging = new ProjectileData();
    private final UnitData emptyUnits = new UnitData();
    private final SimulationSnapshot snapshot = new SimulationSnapshot();
    private final SimdSoaBuffer simd = new SimdSoaBuffer();
    private long capturedFrames;

    private Bullet[] refs = new Bullet[0];
    private float[] drag = new float[0];
    private Bullet[] flagged = new Bullet[0];
    private int flaggedCount;

    private static int thresholdOverride = -1;

    private ProjectileTick(){
    }

    public static boolean enabled(){
        return Core.settings != null && Core.settings.getBool(key, false);
    }

    public static int threshold(){
        if(thresholdOverride > 0) return thresholdOverride;
        return EngineCalibration.thresholdFor("projectiles", parallelThreshold);
    }

    /** Test-only threshold override; pass -1 to restore the production threshold. */
    public static void overrideThreshold(int bullets){
        thresholdOverride = bullets;
    }

    /**
     * Runs capture, parallel simulation and commit for this tick's bullet movement. Must be called
     * from the game thread, right before {@code Groups.bullet.update()}.
     */
    public static void tick(){
        lastEngaged = false;
        lastEngagedBullets = 0;
        instance.clearStaleFlags();
        if(!enabled() || !OrbitalEngine.parallelCapable()) return;
        instance.run();
    }

    private void clearStaleFlags(){
        for(int i = 0; i < flaggedCount; i++){
            Bullet bullet = flagged[i];
            if(bullet != null) bullet.engineMoved(false);
        }
        flaggedCount = 0;
    }

    private void run(){
        int total = Groups.bullet.size();

        //first pass: bullets still at the muzzle this frame are not integrated
        int eligible = 0;
        for(Bullet bullet : Groups.bullet){
            if(!bullet.justSpawned()) eligible++;
        }
        lastEligibleBullets = eligible;
        if(eligible < threshold()) return;

        SimulationPipeline pipeline = OrbitalEngine.shared().pipeline();

        staging.clear();
        if(refs.length < total){
            refs = new Bullet[total];
            drag = new float[total];
            flagged = new Bullet[total];
        }

        int count = 0;
        for(Bullet bullet : Groups.bullet){
            if(bullet.justSpawned()) continue;

            refs[count] = bullet;
            drag[count] = bullet.type().drag;
            //the kernel uses only position, velocity and drag; the other SoA columns stay unused
            staging.add(bullet.id(), bullet.x(), bullet.y(),
                bullet.vel().x, bullet.vel().y, 0f, 0f, 0, 0);
            count++;
        }
        if(count == 0) return;

        float[] dragBuffer = pipeline.buffers().floatScratch(count);
        System.arraycopy(drag, 0, dragBuffer, 0, count);

        if(snapshot.isOpen()) snapshot.clear();
        pipeline.capture(snapshot, capturedFrames++, emptyUnits, staging);

        float delta = Time.delta;
        if(count >= simdBatch && SimdOperations.capabilities().nativeLoaded() && simd.usable()){
            ProjectileData bullets = snapshot.projectiles();
            simd.upload(bullets.x, bullets.y, bullets.velocityX, bullets.velocityY, dragBuffer, count);
            pipeline.simulateRanges(snapshot, "simulation.projectiles.simd", count, simdBatch,
                (from, to) -> SimdNative.updateUnitsDirect(
                    simd.xAddress(), simd.yAddress(), simd.vxAddress(), simd.vyAddress(),
                    simd.dragAddress(), from, to - from, delta, false));
            simd.download(bullets.x, bullets.y, bullets.velocityX, bullets.velocityY, count);
        }else{
            pipeline.simulateProjectiles(snapshot,
                (state, index) -> ProjectileKernel.integrate(state.projectiles(), dragBuffer, index, delta));
        }

        pipeline.commit(snapshot, null, (bullets, index) -> {
            Bullet bullet = refs[index];
            bullet.x(bullets.x[index]);
            bullet.y(bullets.y[index]);
            bullet.vel().set(bullets.velocityX[index], bullets.velocityY[index]);
            bullet.engineMoved(true);
        });

        pipeline.endFrame();

        System.arraycopy(refs, 0, flagged, 0, count);
        flaggedCount = count;

        lastEngaged = true;
        lastEngagedBullets = count;
    }
}