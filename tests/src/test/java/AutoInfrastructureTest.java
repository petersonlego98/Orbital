import arc.Core;
import orbital.content.Blocks;
import orbital.content.Items;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;
import orbital.entities.units.BuildPlan;
import orbital.engine.world.AutoInfrastructure;
import orbital.game.Team;
import orbital.world.Tile;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Headless tests for the auto-infrastructure planner. */
class AutoInfrastructureTest{

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
        world.tile(10, 10).setBlock(Blocks.coreShard, Team.sharded);
        state.teams.updateTeamStats();
    }

    @AfterEach
    void cleanup(){
        Core.settings.put(AutoInfrastructure.key, false);
    }

    @Test
    void factoryGetsTransportRoutesBeforeItself(){
        BuildPlan factory = new BuildPlan(20, 20, 0, Blocks.siliconSmelter);

        arc.struct.Seq<BuildPlan> plans = AutoInfrastructure.plan(factory, Team.sharded);

        assertFalse(plans.isEmpty(), "a consuming/producing factory must get transport routes");
        for(BuildPlan plan : plans){
            assertFalse(plan.breaking);
            assertTrue(plan.block == Blocks.conveyor || plan.block == Blocks.titaniumConveyor
                || plan.block == Blocks.conduit || plan.block == Blocks.pulseConduit
                || plan.block == Blocks.duct || plan.block == Blocks.armoredDuct
                || plan.block == Blocks.plastaniumConveyor || plan.block == Blocks.surgeConveyor
                || plan.block == Blocks.junction || plan.block == Blocks.ductRouter
                || plan.block == Blocks.powerNode || plan.block == Blocks.powerNodeLarge
                || plan.block == Blocks.overflowGate || plan.block == Blocks.liquidRouter,
                "only transport blocks may be planned, got " + plan.block);
        }

        //no duplicate tiles, and the factory itself is never in the list
        for(int i = 0; i < plans.size; i++){
            for(int j = i + 1; j < plans.size; j++){
                assertFalse(plans.get(i).samePos(plans.get(j)), "route must not plan the same tile twice");
            }
            assertFalse(plans.get(i).samePos(factory));
        }
    }

    @Test
    void blockWithNoTransportNeedsGetsNoPlans(){
        BuildPlan wall = new BuildPlan(20, 20, 0, Blocks.sandWall);

        assertTrue(AutoInfrastructure.plan(wall, Team.sharded).isEmpty());
    }

    @Test
    void teamWithoutACoreGetsNoPlans(){
        BuildPlan factory = new BuildPlan(20, 20, 0, Blocks.siliconSmelter);

        assertTrue(AutoInfrastructure.plan(factory, Team.crux).isEmpty(),
            "without a destination there is no route to plan");
    }

    @Test
    void routeAcrossAWallUsesABridge(){
        CoreBuild coreBuild = (CoreBuild)world.tile(10, 10).build;
        assertNotNull(coreBuild);
        coreBuild.items.add(Items.copper, 200);
        coreBuild.items.add(Items.lead, 200);
        coreBuild.items.add(Items.titanium, 200);
        coreBuild.items.add(Items.silicon, 200);

        //a wall across the whole map forces the straight fallback route to cross an obstacle
        for(int y = 0; y < world.height(); y++){
            world.tile(15, y).setBlock(Blocks.sandWall);
        }

        BuildPlan factory = new BuildPlan(20, 20, 0, Blocks.siliconSmelter);
        arc.struct.Seq<BuildPlan> plans = AutoInfrastructure.plan(factory, Team.sharded);

        boolean bridged = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.itemBridge) bridged = true;
        }
        assertTrue(bridged, "a gap in the route must be closed with an item bridge");
    }

    @Test
    void factoryFarFromAnyPowerNetworkGetsPowerNodes(){
        var core = world.tile(10, 10).build;
        assertNotNull(core);
        CoreBuild coreBuild = (CoreBuild)core;
        coreBuild.items.add(Items.copper, 200);
        coreBuild.items.add(Items.lead, 200);
        coreBuild.items.add(Items.titanium, 200);
        coreBuild.items.add(Items.silicon, 200);

        BuildPlan factory = new BuildPlan(45, 45, 0, Blocks.siliconSmelter);

        arc.struct.Seq<BuildPlan> plans = AutoInfrastructure.plan(factory, Team.sharded);

        boolean hasNode = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.powerNode || plan.block == Blocks.powerNodeLarge) hasNode = true;
        }
        assertTrue(hasNode, "a power-hungry factory far from the network must get nodes");
    }

    @Test
    void normalPlacementToggleIsOptIn(){
        Core.settings.put(AutoInfrastructure.key, false);
        assertFalse(AutoInfrastructure.normalPlacementEnabled());

        Core.settings.put(AutoInfrastructure.key, true);
        assertTrue(AutoInfrastructure.normalPlacementEnabled());
    }

    @Test
    void sameSeedProducesTheSamePlan(){
        BuildPlan factory = new BuildPlan(20, 20, 0, Blocks.siliconSmelter);

        arc.struct.Seq<BuildPlan> first = AutoInfrastructure.plan(factory, Team.sharded);
        arc.struct.Seq<BuildPlan> second = AutoInfrastructure.plan(factory, Team.sharded);

        assertEquals(first.size, second.size);
        for(int i = 0; i < first.size; i++){
            assertTrue(first.get(i).samePos(second.get(i)));
            assertEquals(first.get(i).block, second.get(i).block);
            assertEquals(first.get(i).rotation, second.get(i).rotation);
        }
    }
}