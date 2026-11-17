import arc.Core;
import orbital.content.Blocks;
import orbital.content.Items;
import orbital.content.Liquids;
import orbital.type.ItemStack;
import orbital.type.LiquidStack;
import orbital.world.blocks.production.GenericCrafter;
import orbital.world.Block;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;
import orbital.entities.units.BuildPlan;
import orbital.engine.world.AutoInfrastructure;
import orbital.engine.world.TransportTier;
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
    void multipleItemOutputsUseAnOverflowGate(){
        GenericCrafter multi = new GenericCrafter("test-multi-item"){{
            outputItems = new ItemStack[]{
                new ItemStack(Items.copper, 1), new ItemStack(Items.lead, 1)};
        }};
        BuildPlan factory = new BuildPlan(20, 20, 0, multi);

        arc.struct.Seq<BuildPlan> plans = AutoInfrastructure.plan(factory, Team.sharded);

        boolean overflow = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.overflowGate) overflow = true;
        }
        assertTrue(overflow, "multiple item outputs need an overflow gate");
    }

    @Test
    void multipleLiquidOutputsUseARouter(){
        GenericCrafter multi = new GenericCrafter("test-multi-liquid"){{
            outputLiquids = new LiquidStack[]{
                new LiquidStack(Liquids.water, 1f), new LiquidStack(Liquids.oil, 1f)};
        }};
        BuildPlan factory = new BuildPlan(20, 20, 0, multi);

        arc.struct.Seq<BuildPlan> plans = AutoInfrastructure.plan(factory, Team.sharded);

        boolean router = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.liquidRouter) router = true;
        }
        assertTrue(router, "multiple liquid outputs need a liquid router");
    }

    @Test
    void powerChainAimsAtTheNearestGenerator(){
        CoreBuild coreBuild = (CoreBuild)world.tile(10, 10).build;
        assertNotNull(coreBuild);
        coreBuild.items.add(Items.copper, 200);
        coreBuild.items.add(Items.lead, 200);
        coreBuild.items.add(Items.titanium, 200);
        coreBuild.items.add(Items.silicon, 200);

        world.tile(25, 45).setBlock(Blocks.combustionGenerator, Team.sharded);
        state.teams.updateTeamStats();

        BuildPlan factory = new BuildPlan(45, 45, 0, Blocks.siliconSmelter);
        arc.struct.Seq<BuildPlan> plans = AutoInfrastructure.plan(factory, Team.sharded);

        boolean towardsGenerator = false;
        for(BuildPlan plan : plans){
            if(plan.block != Blocks.powerNode && plan.block != Blocks.powerNodeLarge) continue;
            float toGenerator = MathfDist(plan.x - 25, plan.y - 45);
            float toCore = MathfDist(plan.x - 10, plan.y - 10);
            if(toGenerator < toCore) towardsGenerator = true;
        }
        assertTrue(towardsGenerator, "the node chain must be laid toward the generator, not the core");
    }

    private static float MathfDist(int dx, int dy){
        return dx * dx + dy * dy;
    }

    @Test
    void fastestTransportFollowsTheThroughputRanking(){
        CoreBuild core = (CoreBuild)world.tile(10, 10).build;
        assertNotNull(core);
        for(var item : content.items()){
            core.items.add(item, 500);
        }

        Block shortHaul = TransportTier.fastestItem(core, 0);
        float bestShort = 0f;
        for(TransportTier.Method method : TransportTier.ITEMS){
            if(method.unlockedAndAffordable(core)) bestShort = Math.max(bestShort, method.unitsPerSecond());
        }
        float chosenShort = -1f;
        for(TransportTier.Method method : TransportTier.ITEMS){
            if(method.block() == shortHaul) chosenShort = method.unitsPerSecond();
        }
        assertTrue(chosenShort >= 0f);
        assertEquals(bestShort, chosenShort, 0f, "short routes must use the fastest ready method");

        Block longHaul = TransportTier.fastestItem(core, TransportTier.longHaulTiles + 1);
        assertEquals(Blocks.massDriver, longHaul, "long routes use the mass driver when payable");
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