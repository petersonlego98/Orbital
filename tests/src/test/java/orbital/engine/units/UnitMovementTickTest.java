package orbital.engine.units;

import orbital.engine.memory.UnitData;
import orbital.engine.simulation.SimulationPipeline;
import orbital.engine.simulation.SimulationSnapshot;
import orbital.engine.threading.OrbitalJobSystem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.regex.Pattern;

import static arc.math.Mathf.equal;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Equivalence and wiring tests for the parallel unit movement phase.
 *
 * <p>The phase only exists if its math is byte-for-byte the vanilla {@code VelComp.update()}
 * integration, so every test here compares against an independent transcription of the comp's
 * formula, not against {@link UnitMovementTick#step} itself.</p>
 */
class UnitMovementTickTest{
    private static final Path dialog = Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java");
    private static final Path logic = Paths.get("../../core/src/orbital/core/Logic.java");

    @Test
    void stepMatchesTheVanillaVelocityIntegration(){
        Random random = new Random(12345L);
        int count = 4096;
        UnitData units = units(count, random);
        float[] drag = drag(count, random);
        float delta = 0.7f;

        //independent transcription of VelComp.update(): move, zero unmoved axes, then drag
        float[] expectedX = new float[count];
        float[] expectedY = new float[count];
        float[] expectedVx = new float[count];
        float[] expectedVy = new float[count];
        for(int i = 0; i < count; i++){
            float px = units.x[i], py = units.y[i];
            float vx = units.velocityX[i], vy = units.velocityY[i];

            float nx = px + vx * delta, ny = py + vy * delta;
            if(equal(px, nx)) vx = 0f;
            if(equal(py, ny)) vy = 0f;

            float scale = Math.max(1f - drag[i] * delta, 0f);
            expectedX[i] = nx;
            expectedY[i] = ny;
            expectedVx[i] = vx * scale;
            expectedVy[i] = vy * scale;
        }

        for(int i = 0; i < count; i++){
            UnitMovementTick.step(units, drag, i, delta);
        }

        assertArrayEquals(expectedX, units.x, 0f, "x diverged from the vanilla formula");
        assertArrayEquals(expectedY, units.y, 0f, "y diverged from the vanilla formula");
        assertArrayEquals(expectedVx, units.velocityX, 0f, "vel.x diverged from the vanilla formula");
        assertArrayEquals(expectedVy, units.velocityY, 0f, "vel.y diverged from the vanilla formula");
    }

    @Test
    void parallelAndSequentialRunsAgreeOnEveryRow(){
        Random random = new Random(777L);
        int count = 10_000;
        UnitData sequential = units(count, random);
        float[] drag = drag(count, random);
        float delta = 0.5f;

        UnitData parallel = new UnitData();
        parallel.copyFrom(sequential);

        //sequential reference: every row in order on the calling thread
        for(int i = 0; i < count; i++){
            UnitMovementTick.step(sequential, drag, i, delta);
        }

        //parallel run: the real pool splits the range, workers only touch their own index;
        //the commit applies the simulated rows back into the parallel buffer, as the game does
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(4, 64)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            SimulationSnapshot snapshot = new SimulationSnapshot();
            pipeline.capture(snapshot, 0, parallel, null);
            pipeline.simulateUnits(snapshot, (state, index) -> UnitMovementTick.step(state.units(), drag, index, delta));
            pipeline.commit(snapshot, (units, index) -> {
                parallel.x[index] = units.x[index];
                parallel.y[index] = units.y[index];
                parallel.velocityX[index] = units.velocityX[index];
                parallel.velocityY[index] = units.velocityY[index];
            }, null);
        }

        assertArrayEquals(sequential.x, parallel.x, 0f, "parallel x differs from sequential");
        assertArrayEquals(sequential.y, parallel.y, 0f, "parallel y differs from sequential");
        assertArrayEquals(sequential.velocityX, parallel.velocityX, 0f, "parallel vel.x differs from sequential");
        assertArrayEquals(sequential.velocityY, parallel.velocityY, 0f, "parallel vel.y differs from sequential");
    }

    @Test
    void dragAboveOneClampsToAFullStopAndZeroVelocityStaysZero(){
        UnitData units = new UnitData();
        units.add(1, 10f, 20f, 30f, 40f, 0f, 100f, 0); //moves
        units.add(2, 10f, 20f, 0f, 0f, 0f, 100f, 0); //already stopped
        float[] drag = {10f, 10f};

        UnitMovementTick.step(units, drag, 0, 0.5f);
        UnitMovementTick.step(units, drag, 1, 0.5f);

        assertEquals(25f, units.x[0], 0f);
        assertEquals(40f, units.y[0], 0f);
        assertEquals(0f, units.velocityX[0], 0f, "drag above one must stop the unit");
        assertEquals(0f, units.velocityY[0], 0f, "drag above one must stop the unit");
        assertEquals(10f, units.x[1], 0f, "a stopped unit must not move");
        assertEquals(0f, units.velocityX[1], 0f);
    }

    @Test
    void movementBelowTheEpsilonZerosTheVelocityAxis(){
        //VelComp zeroes an axis when Mathf.equal says the position did not change;
        //a velocity this tiny must therefore end up as exactly zero, not as a scaled value
        UnitData units = new UnitData();
        units.add(1, 0f, 0f, 1e-9f, 1e-9f, 0f, 100f, 0);
        float[] drag = {0f};

        UnitMovementTick.step(units, drag, 0, 0.5f);

        assertEquals(0f, units.velocityX[0], 0f);
        assertEquals(0f, units.velocityY[0], 0f);
    }

    @Test
    void parallelMovementIsRegisteredAsAnOptInRow(){
        assertDoesNotThrow(() -> {
            String source = Files.readString(dialog, StandardCharsets.UTF_8);
            assertTrue(Pattern.compile("advancedgraphics\\.checkPref\\(UnitMovementTick\\.key, false").matcher(source).find(),
                "the parallel movement row must be an opt-in check box in advanced graphics");
        });
    }

    @Test
    void logicCallsTheTickBeforeTheUnitGroupUpdate(){
        assertDoesNotThrow(() -> {
            String source = Files.readString(logic, StandardCharsets.UTF_8);
            int tick = source.indexOf("UnitMovementTick.tick()");
            int update = source.indexOf("Groups.unit.update();");
            assertTrue(tick >= 0, "Logic must call UnitMovementTick.tick()");
            assertTrue(update > tick, "the tick must run before Groups.unit.update()");
        });
    }

    @Test
    void parallelMovementKeysExistInEveryBundledLocale(){
        Path bundles = Paths.get("bundles");
        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            Path file = bundles.resolve("bundle" + locale + ".properties");
            assertTrue(Files.isRegularFile(file), "missing bundle " + file);

            assertDoesNotThrow(() -> {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                for(String suffix : new String[]{".name", ".description"}){
                    String key = "setting." + UnitMovementTick.key + suffix;
                    assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                        "missing " + key + " in " + file);
                }
                assertTrue(content.contains("\nsettings." + UnitMovementTick.key + ".unavailable ="),
                    "missing the unavailable notice in " + file);
            });
        }
    }

    private static UnitData units(int count, Random random){
        UnitData data = new UnitData(count);
        for(int i = 0; i < count; i++){
            float x = (random.nextFloat() - 0.5f) * 40_000f;
            float y = (random.nextFloat() - 0.5f) * 40_000f;
            float vx = (random.nextFloat() - 0.5f) * 20f;
            float vy = (random.nextFloat() - 0.5f) * 20f;
            data.add(i, x, y, vx, vy, 0f, 100f, 0);
        }
        return data;
    }

    private static float[] drag(int count, Random random){
        float[] drag = new float[count];
        for(int i = 0; i < count; i++){
            //typical Mindustry drag range, plus extreme values to exercise the clamp
            drag[i] = i % 64 == 0 ? random.nextFloat() * 8f : random.nextFloat() * 0.6f;
        }
        return drag;
    }
}
