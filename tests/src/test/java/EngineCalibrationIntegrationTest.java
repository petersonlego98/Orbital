import arc.Core;
import orbital.engine.units.UnitMovementTick;
import orbital.engine.validation.EngineCalibration;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies that turning calibration off restores the documented fixed thresholds. */
class EngineCalibrationIntegrationTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @AfterEach
    void restore(){
        Core.settings.put(EngineCalibration.key, true);
        EngineCalibration.reset();
    }

    @Test
    void disabledCalibrationUsesTheFixedThreshold(){
        Core.settings.put(EngineCalibration.key, false);
        EngineCalibration.reset();

        assertFalse(EngineCalibration.enabled());
        assertEquals(UnitMovementTick.parallelThreshold,
            EngineCalibration.thresholdFor("units", UnitMovementTick.parallelThreshold));
    }

    @Test
    void enabledCalibrationUsesAMeasuredThreshold(){
        Core.settings.put(EngineCalibration.key, true);
        EngineCalibration.reset();

        assertTrue(EngineCalibration.enabled());
        int measured = EngineCalibration.thresholdFor("units", UnitMovementTick.parallelThreshold);
        assertTrue(measured >= EngineCalibration.minimumThreshold);
    }
}