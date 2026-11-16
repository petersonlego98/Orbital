import arc.Core;
import arc.util.Time;
import orbital.content.UnitTypes;
import orbital.engine.OrbitalEngine;
import orbital.engine.units.UnitMovementTick;
import orbital.game.Team;
import orbital.gen.Groups;
import orbital.gen.Unit;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end tests for the parallel unit movement phase, against the real game entities.
 *
 * <p>The unit level tests prove the math; these prove the wiring: which units the engine is
 * allowed to touch, that a unit never moves twice, and that a leftover flag can never freeze a
 * unit for a frame. It lives in the test root package because the headless harness
 * {@link ApplicationTests} is declared there, like every other test that launches the game.</p>
 */
class UnitMovementTickIntegrationTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(96, 96);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();

        Groups.unit.clear();
        Time.delta = 1f;
        Core.settings.put(UnitMovementTick.key, true);
        UnitMovementTick.overrideThreshold(1);
    }

    @AfterEach
    void cleanup(){
        UnitMovementTick.overrideThreshold(-1);
        Core.settings.put(UnitMovementTick.key, false);
        Groups.unit.clear();
        OrbitalEngine.shutdownIfStarted();
    }

    @Test
    void onlyUnitsThatMoveByAPositionAddAreCaptured(){
        Unit air = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        Unit payload = UnitTypes.quad.spawn(Team.sharded, 120f, 100f);
        Unit ground = UnitTypes.corvus.spawn(Team.sharded, 140f, 100f);
        Unit naval = UnitTypes.risso.spawn(Team.sharded, 160f, 100f);

        assertTrue(UnitMovementTick.capturable(air), "an air unit moves by a plain position add");
        assertTrue(UnitMovementTick.capturable(payload), "an air payload unit moves by a plain position add");
        assertFalse(UnitMovementTick.capturable(ground), "a legs unit moves through the collision system");
        assertFalse(UnitMovementTick.capturable(naval), "a naval unit moves through the collision system");
    }

    @Test
    void anAirUnitMovesExactlyOncePerTick(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine cannot run the engine pool");

        Unit air = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        air.vel().set(10f, 0f);

        UnitMovementTick.tick();

        assertTrue(UnitMovementTick.lastEngaged, "the engine should engage with one eligible unit");
        assertEquals(1, UnitMovementTick.lastEngagedUnits);

        Groups.unit.update();

        //one integration only: a second one would land on 119.6
        assertEquals(110f, air.x(), 0.001f, "the air unit was integrated twice");
        assertEquals(100f, air.y(), 0.001f);
    }

    @Test
    void groundUnitsKeepTheVanillaPath(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine cannot run the engine pool");

        Unit air = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        Unit ground = UnitTypes.corvus.spawn(Team.sharded, 200f, 200f);
        air.vel().set(10f, 0f);
        ground.vel().set(10f, 0f);

        UnitMovementTick.tick();

        assertTrue(UnitMovementTick.lastEngaged);
        assertEquals(1, UnitMovementTick.lastEngagedUnits, "only the air unit may be captured");
        assertEquals(1, UnitMovementTick.lastEligibleUnits);

        Groups.unit.update();

        assertEquals(110f, air.x(), 0.001f, "the air unit was integrated twice");
        assertEquals(210f, ground.x(), 0.001f, "the ground unit must move through the vanilla path");
    }

    @Test
    void withThePreferenceOffNothingIsCaptured(){
        Core.settings.put(UnitMovementTick.key, false);

        Unit air = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        air.vel().set(10f, 0f);

        UnitMovementTick.tick();

        assertFalse(UnitMovementTick.lastEngaged);
        assertEquals(0, UnitMovementTick.lastEngagedUnits);

        Groups.unit.update();

        assertEquals(110f, air.x(), 0.001f, "the vanilla path must still move the unit once");
    }

    @Test
    void aFlagLeftOverFromAnInterruptedTickNeverFreezesAUnit(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine cannot run the engine pool");

        Unit air = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        air.vel().set(10f, 0f);

        //the engine flags the unit, but the group update never happens (an exception, a paused
        //frame, a crash in another system): the flag is still set when the next tick starts
        UnitMovementTick.tick();
        assertTrue(UnitMovementTick.lastEngaged);
        assertEquals(110f, air.x(), 0.001f, "the engine integrated the unit once");

        //the engine is off for the next tick, so nothing consumes the flag - the tick still has to
        //clear it, otherwise the vanilla integration would be skipped and the unit would freeze
        Core.settings.put(UnitMovementTick.key, false);
        UnitMovementTick.tick();
        assertFalse(UnitMovementTick.lastEngaged);

        Groups.unit.update();

        assertEquals(119.6f, air.x(), 0.001f, "a stale flag must not swallow a frame of movement");
    }

    @Test
    void aUnitIsNotCapturedBelowTheThreshold(){
        UnitMovementTick.overrideThreshold(64);

        Unit air = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        air.vel().set(10f, 0f);

        UnitMovementTick.tick();

        assertFalse(UnitMovementTick.lastEngaged, "the engine must stay out below the threshold");
        assertEquals(1, UnitMovementTick.lastEligibleUnits, "the eligible count is still reported");
    }
}