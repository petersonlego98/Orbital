package orbital.engine.combat;

import orbital.engine.memory.ProjectileData;
import orbital.engine.simulation.SimulationPipeline;
import orbital.engine.simulation.SimulationSnapshot;
import orbital.engine.threading.OrbitalJobSystem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Equivalence, parallel agreement and wiring tests for the projectile phase. */
class ProjectileKernelTest{
    private static final Path logic = Paths.get("../../core/src/orbital/core/Logic.java");
    private static final Path dialog = Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java");

    @Test
    void integrateMatchesTheVanillaBulletFormula(){
        Random random = new Random(9911L);
        int count = 4096;
        ProjectileData bullets = new ProjectileData(count);
        float[] drag = new float[count];
        for(int i = 0; i < count; i++){
            bullets.add(i, random.nextFloat() * 1000f, random.nextFloat() * 1000f,
                (random.nextFloat() - 0.5f) * 40f, (random.nextFloat() - 0.5f) * 40f,
                0f, 0f, 0, 0);
            drag[i] = i % 64 == 0 ? random.nextFloat() * 8f : random.nextFloat() * 0.6f;
        }
        float delta = 0.75f;

        float[] expectedX = new float[count], expectedY = new float[count];
        float[] expectedVx = new float[count], expectedVy = new float[count];
        for(int i = 0; i < count; i++){
            //independent transcription of BulletComp.update()
            float px = bullets.x[i], py = bullets.y[i];
            float vx = bullets.velocityX[i], vy = bullets.velocityY[i];
            expectedX[i] = px + vx * delta;
            expectedY[i] = py + vy * delta;
            float scale = Math.max(1f - drag[i] * delta, 0f);
            expectedVx[i] = vx * scale;
            expectedVy[i] = vy * scale;
        }

        for(int i = 0; i < count; i++){
            ProjectileKernel.integrate(bullets, drag, i, delta);
        }

        assertArrayEquals(expectedX, bullets.x, 0f, "x diverged from the vanilla formula");
        assertArrayEquals(expectedY, bullets.y, 0f, "y diverged from the vanilla formula");
        assertArrayEquals(expectedVx, bullets.velocityX, 0f, "vel.x diverged from the vanilla formula");
        assertArrayEquals(expectedVy, bullets.velocityY, 0f, "vel.y diverged from the vanilla formula");
    }

    @Test
    void sequentialAndParallelBulletIntegrationAgree(){
        Random random = new Random(4242L);
        int count = 10_000;
        ProjectileData source = new ProjectileData(count);
        float[] drag = new float[count];
        for(int i = 0; i < count; i++){
            source.add(i, i * 2f, -i, random.nextFloat() * 20f, random.nextFloat() * 20f, 0f, 0f, 0, 0);
            drag[i] = random.nextFloat();
        }

        ProjectileData sequential = new ProjectileData();
        sequential.copyFrom(source);
        for(int i = 0; i < count; i++){
            ProjectileKernel.integrate(sequential, drag, i, 0.5f);
        }

        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 64)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            SimulationSnapshot snapshot = new SimulationSnapshot();
            pipeline.capture(snapshot, 0, null, source);
            pipeline.simulateProjectiles(snapshot,
                (state, index) -> ProjectileKernel.integrate(state.projectiles(), drag, index, 0.5f));
            pipeline.commit(snapshot, null, (bullets, index) -> {
                source.x[index] = bullets.x[index];
                source.y[index] = bullets.y[index];
                source.velocityX[index] = bullets.velocityX[index];
                source.velocityY[index] = bullets.velocityY[index];
            });
        }

        assertArrayEquals(sequential.x, source.x, 0f);
        assertArrayEquals(sequential.y, source.y, 0f);
        assertArrayEquals(sequential.velocityX, source.velocityX, 0f);
        assertArrayEquals(sequential.velocityY, source.velocityY, 0f);
    }

    @Test
    void bulletMovementIsOptInAndRunsBeforeTheVanillaUpdate() throws Exception{
        String menu = Files.readString(dialog, StandardCharsets.UTF_8);
        assertTrue(Pattern.compile("advancedgraphics\\.checkPref\\(orbital\\.engine\\.combat\\.ProjectileTick\\.key, false")
            .matcher(menu).find(), "bullet movement must stay opt-in in advanced graphics");

        String source = Files.readString(logic, StandardCharsets.UTF_8);
        int tick = source.indexOf("ProjectileTick.tick()");
        int update = source.indexOf("Groups.bullet.update();");
        assertTrue(tick >= 0, "Logic must call the projectile tick");
        assertTrue(update > tick, "the tick must run before the bullet group update");
    }

    @Test
    void bulletMovementKeysExistInEveryBundledLocale() throws Exception{
        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            Path file = Paths.get("bundles", "bundle" + locale + ".properties");
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for(String key : new String[]{
                "setting.orbitalparallelbullets.name",
                "setting.orbitalparallelbullets.description",
                "settings.orbitalparallelbullets.unavailable"}){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }
}