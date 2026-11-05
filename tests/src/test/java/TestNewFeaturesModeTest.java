import orbital.game.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies that the isolated feature-test mode cannot leak into the regular gamemodes. */
public class TestNewFeaturesModeTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void modeIsHiddenAndDoesNotShiftExistingOrdinals(){
        assertTrue(Gamemode.testFeatures.hidden);
        //Gamemode ordinals are serialized in network data; the test mode must stay last.
        assertEquals(Gamemode.all.length - 1, Gamemode.testFeatures.ordinal());
        assertEquals(0, Gamemode.survival.ordinal());
        assertEquals(1, Gamemode.sandbox.ordinal());
        assertEquals(2, Gamemode.attack.ordinal());
        assertEquals(3, Gamemode.pvp.ordinal());
        assertEquals(4, Gamemode.editor.ordinal());
    }

    @Test
    void rulesAreFreeButKeepTheUnitCap(){
        Rules rules = Gamemode.testFeatures.apply(new Rules());

        assertEquals(TestNewFeaturesMode.rulesName, rules.modeName);
        assertEquals(Gamemode.testFeatures, rules.mode());
        assertFalse(rules.waves);
        assertFalse(rules.waveTimer);
        assertEquals(0f, rules.unitCostMultiplier, 0.0001f);
        assertEquals(0f, rules.buildCostMultiplier, 0.0001f);
        assertFalse(rules.disableUnitCap);
    }

    @Test
    void otherModesDoNotReportTestMode(){
        assertNotEquals(Gamemode.testFeatures, new Rules().mode());
        assertNotEquals(Gamemode.testFeatures, Gamemode.sandbox.apply(new Rules()).mode());
        assertNotEquals(Gamemode.testFeatures, Gamemode.editor.apply(new Rules()).mode());
    }

    @Test
    void aerialCatalogOnlyContainsVisibleFlyingUnits(){
        var types = TestNewFeaturesMode.aerialTypes();
        assertFalse(types.isEmpty());
        for(var type : types){
            assertTrue(type.flying);
            assertFalse(type.isHidden());
        }
    }
}
