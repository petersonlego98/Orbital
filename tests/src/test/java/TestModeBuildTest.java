import arc.*;
import arc.struct.*;
import orbital.*;
import orbital.ai.*;
import orbital.ai.types.*;
import orbital.content.*;
import orbital.core.*;
import orbital.core.GameState.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.input.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static orbital.Vars.*;

/**
 * Tests for building in the test-features mode and for the command tablet's construction mode.
 *
 * <p>Every flying unit in this fork gets a 0.5 build speed by default, so the Mono hull used by
 * the test mode can already build: what blocked construction there was the hidden block menu.
 * The player's plans also reach the team queue, and the tablet button is what puts a group
 * into the rebuild command that consumes it.</p>
 */
public class TestModeBuildTest{

    private Player previousPlayer;
    private String previousMode;

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
        state.set(State.playing);
        //headless Rules start with a zero unit cap; spawning would kill every unit
        state.rules.unitCap = 100;
        Groups.unit.clear();

        previousMode = state.rules.modeName;
        state.rules.modeName = TestNewFeaturesMode.rulesName;
        state.rules.infiniteResources = true;

        previousPlayer = Vars.player;
        Player pilot = Player.create();
        pilot.team(Team.sharded);
        Vars.player = pilot;

        Core.settings.put("autobuild", true);
        AutoBuildSync.resetState();
    }

    @AfterEach
    void cleanup(){
        AutoBuildSync.withdrawAll(Team.sharded);
        Core.settings.remove("autobuild");
        state.rules.modeName = previousMode;
        state.rules.infiniteResources = false;
        Vars.player = previousPlayer;
        state.set(State.menu);
    }

    /** Player + a non-builder hull, wired exactly like TestNewFeaturesMode.spawnInitialMono does. */
    private Unit pilotMono(){
        Unit mono = UnitTypes.mono.spawn(Team.sharded, 10 * 8f, 10 * 8f);
        mono.controller(Vars.player);
        Vars.player.unit(mono);
        Vars.player.set(mono);
        return mono;
    }

    private static int teamPlansAt(int x, int y){
        int count = 0;
        for(Teams.BlockPlan p : Team.sharded.data().plans){
            if(p.x == x && p.y == y) count++;
        }
        return count;
    }

    @Test
    void testModeIsKeyedToTheRulesName(){
        assertTrue(TestNewFeaturesMode.active());
        state.rules.modeName = null;
        assertFalse(TestNewFeaturesMode.active(), "outside the mode the build catalog and the menu return to normal rules");
        state.rules.modeName = TestNewFeaturesMode.rulesName;
        assertTrue(TestNewFeaturesMode.active());
    }

    @Test
    void theTestPilotQueuesPlansThatReachTheBuilderUnits(){
        Unit mono = pilotMono();

        assertTrue(TestNewFeaturesMode.active(), "test mode is active by rules name");
        assertTrue(mono.canBuild(), "flying hulls build in this fork, including Mono");
        assertTrue(Vars.player.isBuilder(), "the input handlers see the pilot as a builder");

        mono.addBuild(new BuildPlan(10, 10, 0, Blocks.conveyor));

        assertEquals(1, mono.plans.size, "the plan stays on the player's unit so he can help");
        assertEquals(1, teamPlansAt(10, 10), "and reaches the team queue for the builder units");
    }

    @Test
    void finishedPlansLeaveThePilotQueueInTestMode(){
        Unit mono = pilotMono();
        mono.addBuild(new BuildPlan(10, 10, 0, Blocks.conveyor));
        assertEquals(1, mono.plans.size);

        //a builder unit finished the block; the pilot's queue must not keep the plan forever
        world.tile(10, 10).setBlock(Blocks.conveyor, Team.sharded);
        mono.updateBuildLogic();

        assertEquals(0, mono.plans.size, "a finished plan leaves the pilot's queue");
    }

    @Test
    void normalModeDoesNotOfferTheTestCatalog(){
        state.rules.modeName = null;
        assertFalse(TestNewFeaturesMode.active());

        //the same hull still builds normally; what changes is the mode-gated catalog rule
        //used by PlacementFragment.unlocked, not the unit's build capability
        Unit mono = pilotMono();
        assertTrue(mono.canBuild());
        mono.addBuild(new BuildPlan(10, 10, 0, Blocks.conveyor));
        assertEquals(1, mono.plans.size, "normal placement keeps working outside the test mode");
        assertEquals(1, teamPlansAt(10, 10), "auto-build forwarding is not mode-gated");
    }

    @Test
    void testModeCatalogOffersBothPlanetsRegardlessOfEnvironment(){
        int env = state.rules.env;
        try{
            state.rules.env = 0;
            assertTrue(TestNewFeaturesMode.blockAvailable(Blocks.conveyor), "Serpulo block");
            assertTrue(TestNewFeaturesMode.blockAvailable(Blocks.duct), "Erekir block");

            state.rules.env = env;
            assertTrue(TestNewFeaturesMode.blockAvailable(Blocks.conveyor), "environment bits do not filter the test catalog");
            assertTrue(TestNewFeaturesMode.blockAvailable(Blocks.duct));
        }finally{
            state.rules.env = env;
        }
    }

    @Test
    void constructionCommandSwitchesTheGroupAndBack(){
        Unit oct = UnitTypes.oct.spawn(Team.sharded, 20 * 8f, 20 * 8f);
        assertTrue(oct.controller() instanceof CommandAI, "oct is RTS-commandable on the player team");
        CommandAI ai = (CommandAI)oct.controller();
        assertEquals(UnitCommand.moveCommand, ai.currentCommand(), "oct starts with the move command");
        assertTrue(oct.type.allowCommand(oct, UnitCommand.rebuildCommand), "oct can take the rebuild command");

        IntSeq ids = new IntSeq();
        ids.add(oct.id);

        //a plan queued *before* the group was put into construction mode is still picked up
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(10, 10, 0, Blocks.conveyor), Team.sharded);

        //this is the same call the tablet's CONSTRUIR button makes
        InputHandler.setUnitCommandChunked(ids, UnitCommand.rebuildCommand);
        assertEquals(UnitCommand.rebuildCommand, ai.currentCommand(), "the group is now in construction mode");
        AutoBuildSync.pingBuildersNow(Team.sharded);

        oct.controller().updateUnit();
        assertNotNull(oct.buildPlan(), "the delegated builder takes the pending plan immediately");
        assertEquals(10, oct.buildPlan().x);

        //toggling off returns to move and clears the group's plans (CommandAI.command does it)
        InputHandler.setUnitCommandChunked(ids, UnitCommand.moveCommand);
        assertEquals(UnitCommand.moveCommand, ai.currentCommand());
        assertNull(oct.buildPlan(), "turning construction mode off leaves no plan behind");
    }
}
