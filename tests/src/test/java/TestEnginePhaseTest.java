import orbital.game.*;
import orbital.gen.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the Fase 1 stress-battle generator and its live diagnostics contract. */
public class TestEnginePhaseTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void battleOnlyStartsInTheTestMode(){
        Rules previous = state.rules;
        try{
            state.rules = Gamemode.sandbox.apply(new Rules());
            assertFalse(TestEnginePhase.start(100), "the stress battle must refuse to run outside the test mode");
            assertFalse(TestEnginePhase.active());
        }finally{
            state.rules = previous;
        }
    }

    @Test
    void stressBattleSpawnsBothTeamsInterleavedAndStopRemovesEveryFighter(){
        createFlatMap(400, 400);
        Rules previous = state.rules;
        state.rules = Gamemode.testFeatures.apply(new Rules());
        try{
            assertTrue(TestNewFeaturesMode.active());

            assertTrue(TestEnginePhase.start(200));
            assertTrue(TestEnginePhase.active());
            assertTrue(TestEnginePhase.generating());
            assertTrue(state.rules.disableUnitCap, "the stress battle must lift the unit cap, or the cap death eats the fighters");

            //96 units per frame: ten updates over-fill a 200-unit battle
            for(int i = 0; i < 10 && TestEnginePhase.generating(); i++){
                TestEnginePhase.update();
            }

            assertFalse(TestEnginePhase.generating());
            assertEquals(200, TestEnginePhase.spawnedUnits());

            int crux = 0, sharded = 0;
            for(Unit unit : Groups.unit){
                if(unit.team == Team.crux) crux++;
                else if(unit.team == Team.sharded) sharded++;
            }
            assertTrue(crux > 0 && sharded > 0, "both fighting teams must be present: " + crux + " vs " + sharded);

            //the diagnostics the panel shows must be produced without a live renderer
            assertNotNull(TestEnginePhase.statsLine());
            assertNotNull(TestEnginePhase.msLine1());
            assertNotNull(TestEnginePhase.msLine2());
            assertNotNull(TestEnginePhase.engineStatus());
            assertNotNull(TestEnginePhase.enginePhases());

            TestEnginePhase.stop();
            assertFalse(TestEnginePhase.active());
            assertFalse(TestEnginePhase.generating());
            for(Unit unit : Groups.unit){
                assertFalse(unit.team == Team.crux, "stop() must remove every battle unit");
            }
        }finally{
            state.rules = previous;
            TestEnginePhase.reset();
        }
    }

    @Test
    void engineToggleWritesThePreferenceLive(){
        boolean before = TestEnginePhase.engineToggle();
        try{
            TestEnginePhase.setEngineToggle(true);
            assertTrue(TestEnginePhase.engineToggle());
            TestEnginePhase.setEngineToggle(false);
            assertFalse(TestEnginePhase.engineToggle());
        }finally{
            TestEnginePhase.setEngineToggle(before);
        }
    }

    @Test
    void startRefusesInvalidCounts(){
        createFlatMap(40, 40);
        Rules previous = state.rules;
        state.rules = Gamemode.testFeatures.apply(new Rules());
        try{
            assertFalse(TestEnginePhase.start(0));
            assertFalse(TestEnginePhase.start(-50));
            assertFalse(TestEnginePhase.active());
        }finally{
            state.rules = previous;
        }
    }

    private static void createFlatMap(int width, int height){
        world.resize(width, height);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
    }
}
