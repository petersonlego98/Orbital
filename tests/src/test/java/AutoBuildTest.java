import arc.*;
import orbital.ai.*;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.input.AutoBuildSync;
import orbital.*;
import orbital.core.*;
import orbital.core.GameState.*;
import orbital.type.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static orbital.Vars.*;

/**
 * Tests for the construction automation ("Construções" menu): the player's queued plans are
 * handed over to the team builder units, and every withdrawal path returns what was taken.
 *
 * <p>The tests drive {@link AutoBuildSync} on the same paths the real game uses: the player's
 * own builder reaches {@code onPlayerPlanAdded/onPlayerPlanRemoved} from
 * {@code BuilderComp.addBuild/removeBuild}, and the cleanup runs from the input handler
 * ({@code update}).</p>
 */
public class AutoBuildTest{

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
        //headless Rules start with a zero unit cap; spawning would kill every builder
        //through Call.unitCapDeath before the sync ever sees them
        state.rules.unitCap = 100;
        Groups.unit.clear();
        Core.settings.put("autobuild", true);
        AutoBuildSync.resetState();
    }

    @AfterEach
    void cleanup(){
        AutoBuildSync.withdrawAll(Team.sharded);
        Core.settings.remove("autobuild");
        state.set(State.menu);
    }

    private static int teamPlansAt(int x, int y){
        int count = 0;
        for(Teams.BlockPlan p : Team.sharded.data().plans){
            if(p.x == x && p.y == y) count++;
        }
        return count;
    }

    /** @return the team plan the sync placed at this tile, or null. */
    private static Teams.BlockPlan trackedEntry(int x, int y){
        return Team.sharded.data().plans.find(p -> p.x == x && p.y == y);
    }

    @Test
    void theSwitchDefaultsToTheUnitsBuilding(){
        //no value stored: the default answer is the units building
        Core.settings.remove("autobuild");
        assertTrue(Core.settings.getBool("autobuild", true), "units building must be the default");
        assertTrue(AutoBuildSync.active(), "the forwarding must be active with the default switch");
    }

    @Test
    void theSwitchOffStopsTheForwarding(){
        Core.settings.put("autobuild", false);
        assertFalse(AutoBuildSync.active(), "switch off means no forwarding");
    }

    @Test
    void aPlacedPlanReachesTheTeamQueue(){
        BuildPlan plan = new BuildPlan(10, 10, 0, Blocks.conveyor);

        AutoBuildSync.onPlayerPlanAdded(plan, Team.sharded);

        assertEquals(1, teamPlansAt(10, 10), "exactly one team plan at the placed tile");
        Teams.BlockPlan entry = trackedEntry(10, 10);
        assertNotNull(entry, "the entry must be tracked for withdrawal");
        assertEquals(Blocks.conveyor, entry.block, "the team plan carries the same block");
        assertEquals(plan.rotation, entry.rotation, "the team plan carries the same rotation");
    }

    @Test
    void aReplacedPlanDoesNotQueueTwice(){
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(10, 10, 0, Blocks.conveyor), Team.sharded);
        //the player replaces the plan with another block on the same tile
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(10, 10, 0, Blocks.duo), Team.sharded);

        assertEquals(1, teamPlansAt(10, 10), "the stale entry is replaced, not stacked");
        assertEquals(Blocks.duo, trackedEntry(10, 10).block, "the newer block wins");
    }

    @Test
    void cancellingThePlanWithdrawsIt(){
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(10, 10, 0, Blocks.conveyor), Team.sharded);
        assertEquals(1, teamPlansAt(10, 10));

        AutoBuildSync.onPlayerPlanRemoved(10, 10, false, Team.sharded);

        assertEquals(0, teamPlansAt(10, 10), "a cancelled plan leaves the team queue");
        assertNull(trackedEntry(10, 10), "nothing stays tracked");
    }

    @Test
    void aCommandedBuilderPicksUpThePlanOnTheNextFrame(){
        //on the player team, poly is a CommandAI unit whose default command is "rebuild":
        //the BuilderAI that consumes the team queue lives *inside* the command controller
        Unit poly = UnitTypes.poly.spawn(Team.sharded, 10 * 8f, 12 * 8f);
        assertTrue(poly.controller() instanceof orbital.ai.types.CommandAI, "poly uses CommandAI on the player team");
        assertNull(poly.buildPlan());

        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(10, 10, 0, Blocks.conveyor), Team.sharded);
        poly.controller().updateUnit();

        assertNotNull(poly.buildPlan(), "the delegated builder picks the plan without waiting for the 120s rebuild timer");
        assertEquals(10, poly.buildPlan().x, "the unit took the plan the player queued");
        assertEquals(10, poly.buildPlan().y);
    }

    @Test
    void aBuilderOnMoveCommandKeepsItsOrders(){
        //decision: auto-build respects RTS commands - it does not draft units ordered elsewhere
        world.tile(20, 20).setBlock(Blocks.duo, Team.sharded);
        Unit mover = UnitTypes.oct.spawn(Team.sharded, 20 * 8f, 14 * 8f);
        assertTrue(mover.controller() instanceof orbital.ai.types.CommandAI, "oct uses CommandAI on the player team");
        assertEquals(UnitCommand.moveCommand, ((orbital.ai.types.CommandAI)mover.controller()).currentCommand(), "oct starts on the move command");

        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(20, 20), Team.sharded);

        assertNull(mover.buildPlan(), "a unit ordered to move is not drafted into demolition");
    }

    @Test
    void theBestBuilderGetsTheDemolition(){
        world.tile(20, 20).setBlock(Blocks.duo, Team.sharded);
        //a slow builder near the site and a fast one equally near: the fast one takes it
        Unit slow = UnitTypes.poly.spawn(Team.sharded, 20 * 8f, 26 * 8f);
        slow.controller(new orbital.ai.types.BuilderAI());
        Unit fast = UnitTypes.oct.spawn(Team.sharded, 20 * 8f, 14 * 8f);
        fast.controller(new orbital.ai.types.BuilderAI());

        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(20, 20), Team.sharded);

        assertNotNull(fast.buildPlan(), "the fastest available builder receives the demolition");
        assertTrue(fast.buildPlan().breaking, "the received plan is a demolition");
        assertNull(slow.buildPlan(), "the slower builder stays free");
    }

    @Test
    void cancellingTheDemolitionReleasesTheBuilder(){
        world.tile(20, 20).setBlock(Blocks.duo, Team.sharded);
        Unit fast = UnitTypes.oct.spawn(Team.sharded, 20 * 8f, 14 * 8f);
        fast.controller(new orbital.ai.types.BuilderAI());
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(20, 20), Team.sharded);
        assertNotNull(fast.buildPlan());

        AutoBuildSync.onPlayerPlanRemoved(20, 20, true, Team.sharded);

        assertNull(fast.buildPlan(), "the builder drops the demolition the player cancelled");
    }

    @Test
    void finishedDemolitionReleasesTheBuilderOnCleanup(){
        world.tile(20, 20).setBlock(Blocks.duo, Team.sharded);
        Unit fast = UnitTypes.oct.spawn(Team.sharded, 20 * 8f, 14 * 8f);
        fast.controller(new orbital.ai.types.BuilderAI());
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(20, 20), Team.sharded);

        //the tile is gone: the demolition finished
        world.tile(20, 20).setBlock(Blocks.air);
        AutoBuildSync.update(Team.sharded);

        assertNull(fast.buildPlan(), "the unit does not keep a demolition plan for a finished tile");
        assertFalse(teamPlansAt(20, 20) > 0, "demolitions never enter the team queue");
    }

    @Test
    void switchingToPlayerBuildsWithdrawsEverything(){
        world.tile(20, 20).setBlock(Blocks.duo, Team.sharded);
        Unit fast = UnitTypes.oct.spawn(Team.sharded, 20 * 8f, 14 * 8f);
        fast.controller(new orbital.ai.types.BuilderAI());
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(20, 20), Team.sharded);
        AutoBuildSync.onPlayerPlanAdded(new BuildPlan(10, 10, 0, Blocks.conveyor), Team.sharded);
        assertEquals(1, teamPlansAt(10, 10));
        assertNotNull(fast.buildPlan());

        //the owner switches the menu to "I build everything myself"
        Core.settings.put("autobuild", false);
        AutoBuildSync.update(Team.sharded);

        assertEquals(0, teamPlansAt(10, 10), "the team queue loses the forwarded plan");
        assertNull(fast.buildPlan(), "the builder drops the demolition");
        assertTrue(AutoBuildSync.active() == false);
    }

    @Test
    void aPlayerPlanPlacedByTheRealBuilderHookReachesTheQueue(){
        //the real path: BuilderComp.addBuild of the local player's unit forwards by itself
        Player pilot = Player.create();
        pilot.team(Team.sharded);
        Unit unit = UnitTypes.gamma.spawn(Team.sharded, 10 * 8f, 10 * 8f);
        unit.controller(pilot);
        pilot.unit(unit);
        Player previous = Vars.player;
        Vars.player = pilot;
        try{
            unit.addBuild(new BuildPlan(10, 10, 0, Blocks.conveyor));
            assertEquals(1, teamPlansAt(10, 10), "the local player's builder forwards to the team queue");
        }finally{
            unit.removeBuild(10, 10, false);
            Vars.player = previous;
        }
    }
}
