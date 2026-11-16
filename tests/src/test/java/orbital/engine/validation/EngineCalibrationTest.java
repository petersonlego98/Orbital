package orbital.engine.validation;

import orbital.engine.ai.AITick;
import orbital.engine.combat.ProjectileTick;
import orbital.engine.particles.ParticleVertexBuilder;
import orbital.engine.units.UnitMovementTick;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the measured thresholds and their settings wiring. */
class EngineCalibrationTest{

    @Test
    void thresholdsAreMeasuredClampedAndCached(){
        assertFalse(EngineCalibration.enabled() == false, "calibration is on by default without settings");
        assertTrue(EngineCalibration.dispatchOverheadNanos() > 0L);

        String[][] subsystems = {
            {"units", String.valueOf(UnitMovementTick.parallelThreshold)},
            {"projectiles", String.valueOf(ProjectileTick.parallelThreshold)},
            {"ai", String.valueOf(AITick.parallelThreshold)},
            {"particles", String.valueOf(ParticleVertexBuilder.parallelThreshold)}
        };

        for(String[] entry : subsystems){
            int fallback = Integer.parseInt(entry[1]);
            int first = EngineCalibration.thresholdFor(entry[0], fallback);
            int second = EngineCalibration.thresholdFor(entry[0], fallback);

            assertEquals(first, second, entry[0] + " threshold must be cached");
            assertTrue(first >= Math.min(EngineCalibration.minimumThreshold, fallback), entry[0] + " too small");
            assertTrue(first <= fallback * EngineCalibration.maximumFactor, entry[0] + " too large");
            assertTrue(EngineCalibration.perRowNanos(entry[0]) > 0.0, entry[0] + " was not measured");
        }
    }

    @Test
    void reportNamesEveryMeasuredSubsystem(){
        String text = EngineCalibration.report();
        assertTrue(text.contains("Orbital calibration"));
        assertTrue(text.contains("handoff"));
        for(String name : new String[]{"units", "projectiles", "ai", "particles"}){
            assertTrue(text.contains(name), "report dropped " + name);
        }
    }

    @Test
    void autoCalibrationIsAnOptOutRowInEveryBundle() throws Exception{
        String menu = Files.readString(
            Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java"),
            StandardCharsets.UTF_8);
        assertTrue(menu.contains("advancedgraphics.checkPref(EngineCalibration.key, true"),
            "auto-calibration must default on in advanced graphics");

        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            Path file = Paths.get("bundles", "bundle" + locale + ".properties");
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for(String key : new String[]{
                "setting.orbitalautocalibrate.name", "setting.orbitalautocalibrate.description"}){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }
}