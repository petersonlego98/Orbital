import arc.struct.Seq;
import orbital.content.Blocks;
import orbital.content.Items;
import orbital.content.UnitTypes;
import orbital.entities.units.BuildPlan;
import orbital.engine.world.FactoryChainPlanner;
import orbital.game.Team;
import orbital.game.TestNewFeaturesMode;
import orbital.gen.Groups;
import orbital.gen.Player;
import orbital.gen.Unit;
import orbital.world.Block;
import orbital.world.Tile;
import orbital.world.blocks.environment.Floor;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the autonomous site choice and the end-to-end connected factory chain. */
class FactoryChainPlannerTest{

    private Player previousPlayer;

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(64, 64);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();

        for(Tile tile : world.tiles){
            tile.setBlock(Blocks.air);
        }

        //coal patch within reach of the core, plus a stocked core
        for(int y = 7; y <= 14; y++){
            for(int x = 13; x <= 20; x++){
                world.tile(x, y).setFloor((Floor)Blocks.oreCoal);
            }
        }
        world.tile(10, 10).setBlock(Blocks.coreShard, Team.sharded);
        state.teams.updateTeamStats();

        CoreBuild core = (CoreBuild)world.tile(10, 10).build;
        for(var item : content.items()){
            core.items.add(item, 500);
        }

        previousPlayer = orbital.Vars.player;
        orbital.Vars.player = Player.create();
        orbital.Vars.player.team(Team.sharded);
        Unit unit = UnitTypes.mono.spawn(Team.sharded, 10f * 8f, 10f * 8f);
        orbital.Vars.player.unit(unit);
        state.rules.modeName = TestNewFeaturesMode.rulesName;
    }

    @AfterEach
    void cleanup(){
        state.rules.modeName = "";
        orbital.Vars.player = previousPlayer;
        Groups.unit.clear();
    }

    @Test
    void unitChoosesTheSiteAndBuildsTheWholeChain(){
        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);

        assertFalse(plans.isEmpty(), "a factory with a reachable ore must produce a chain");

        boolean drill = false, unloader = false, belt = false, factory = false;
        BuildPlan factoryPlan = null;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.mechanicalDrill || plan.block == Blocks.pneumaticDrill
                || plan.block == Blocks.laserDrill) drill = true;
            if(plan.block == Blocks.unloader) unloader = true;
            if(plan.block == Blocks.conveyor || plan.block == Blocks.titaniumConveyor
                || plan.block == Blocks.plastaniumConveyor || plan.block == Blocks.surgeConveyor) belt = true;
            if(plan.block == Blocks.graphitePress){
                factory = true;
                factoryPlan = plan;
            }
        }

        assertTrue(drill, "the chain must include a drill on the ore");
        assertTrue(unloader, "the chain must include an unloader next to the drill");
        assertTrue(belt, "the chain must include transport");
        assertTrue(factory, "the chain must include the chosen factory");
        assertNotNull(factoryPlan);

        //the unit chooses a free site near the core, not a tile picked by the player
        assertTrue(Math.abs(factoryPlan.x - 10) <= 24 && Math.abs(factoryPlan.y - 10) <= 24);
    }

    @Test
    void transportIsAlignedAndConnectedToTheFactory(){
        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);
        BuildPlan factory = null;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.graphitePress){
                factory = plan;
                break;
            }
        }
        assertNotNull(factory);

        int size = Blocks.graphitePress.size;
        boolean input = false, output = false;
        for(BuildPlan plan : plans){
            if(plan.block == null || !plan.block.isDuct && !isBelt(plan.block)) continue;
            if(!adjacent(plan, factory, size)) continue;

            //rotation 0 right, 1 down, 2 left, 3 up
            boolean pointsIn = pointsAt(plan, factory.x, factory.y);
            boolean pointsOut = !pointsIn;
            if(pointsIn) input = true;
            if(pointsOut) output = true;
        }

        assertTrue(input, "an input belt must point into the factory");
        assertTrue(output, "an output belt must point away from the factory");
    }

    @Test
    void catalogChoiceEnqueuesTheChainWithoutAPlayerLocation(){
        int before = orbital.Vars.player.unit().plans.size;

        TestNewFeaturesMode.armPlayerBlock(Blocks.graphitePress);

        assertNull(TestNewFeaturesMode.playerBlock, "the player must not keep a placement cursor");
        assertEquals(Blocks.graphitePress, TestNewFeaturesMode.lastFactory);
        assertTrue(orbital.Vars.player.unit().plans.size > before,
            "choosing the type must enqueue the whole chain on the unit");
    }

    private static boolean isBelt(Block block){
        return block == Blocks.conveyor || block == Blocks.titaniumConveyor
            || block == Blocks.plastaniumConveyor || block == Blocks.surgeConveyor
            || block == Blocks.armoredConveyor;
    }

    private static boolean adjacent(BuildPlan plan, BuildPlan factory, int size){
        int dx = Math.max(factory.x - plan.x, plan.x - (factory.x + size - 1));
        int dy = Math.max(factory.y - plan.y, plan.y - (factory.y + size - 1));
        return dx <= 1 && dy <= 1;
    }

    private static boolean pointsAt(BuildPlan plan, int x, int y){
        return switch(plan.rotation){
            case 0 -> plan.x < x;
            case 1 -> plan.y < y;
            case 2 -> plan.x > x;
            case 3 -> plan.y > y;
            default -> false;
        };
    }
}