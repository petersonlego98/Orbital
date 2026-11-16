import arc.Core;
import orbital.content.UnitTypes;
import orbital.engine.OrbitalEngine;
import orbital.engine.ai.AITick;
import orbital.engine.memory.AIData;
import orbital.game.Team;
import orbital.gen.Groups;
import orbital.gen.Unit;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Headless integration test: AI decisions are computed in parallel without touching entities. */
class AITickIntegrationTest{

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
        Core.settings.put(AITick.key, true);
        AITick.overrideThreshold(1);
    }

    @AfterEach
    void cleanup(){
        AITick.overrideThreshold(-1);
        Core.settings.put(AITick.key, false);
        Groups.unit.clear();
        OrbitalEngine.shutdownIfStarted();
    }

    @Test
    void decisionsAreComputedForAirUnitsWithATarget(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine cannot run the engine pool");
        Unit unit = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        unit.aimX = 200f;
        unit.aimY = 100f;
        unit.vel().set(3f, 4f);

        AITick.tick();

        assertTrue(AITick.lastEngaged);
        assertEquals(1, AITick.lastEngagedUnits);
        assertEquals(1, AITick.lastEligibleUnits);

        AIData decisions = AITick.lastDecisions();
        assertEquals(1, decisions.count);
        assertEquals(unit.id(), decisions.entityIds[0]);
        assertEquals(1f, decisions.desiredX[0], 0f);
        assertEquals(0f, decisions.desiredY[0], 0f);
        assertEquals(100f, decisions.distance[0], 0f);

        //the engine never mutates the authoritative unit in this phase
        assertEquals(100f, unit.x(), 0f);
        assertEquals(100f, unit.y(), 0f);
        assertEquals(3f, unit.vel().x, 0f);
        assertEquals(4f, unit.vel().y, 0f);
    }

    @Test
    void unitsWithoutATargetAreIgnored(){
        Unit unit = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        unit.aimX = 0f;
        unit.aimY = 0f;

        AITick.tick();

        assertFalse(AITick.lastEngaged);
        assertEquals(0, AITick.lastEligibleUnits);
    }

    @Test
    void groundUnitsAreIgnored(){
        Unit ground = UnitTypes.corvus.spawn(Team.sharded, 100f, 100f);
        ground.aimX = 200f;
        ground.aimY = 100f;

        AITick.tick();

        assertFalse(AITick.lastEngaged);
        assertEquals(0, AITick.lastEligibleUnits);
    }

    @Test
    void belowTheThresholdNoDecisionsAreCommitted(){
        AITick.overrideThreshold(64);
        Unit unit = UnitTypes.flare.spawn(Team.sharded, 100f, 100f);
        unit.aimX = 200f;
        unit.aimY = 100f;

        AITick.tick();

        assertFalse(AITick.lastEngaged);
        assertEquals(1, AITick.lastEligibleUnits);
    }
}