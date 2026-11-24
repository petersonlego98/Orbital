import arc.struct.Seq;
import orbital.content.Blocks;
import orbital.content.Items;
import orbital.content.Liquids;
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
import orbital.world.blocks.power.SolarGenerator;
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
        orbital.engine.world.SectorResources.invalidate();

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
        BuildPlan sourcePlan = null;
        for(BuildPlan plan : plans){
            if(plan.block instanceof orbital.world.blocks.production.Drill) drill = true;
            if(plan.block == Blocks.unloader){
                unloader = true;
                sourcePlan = plan;
            }
            if(plan.block == Blocks.conveyor || plan.block == Blocks.titaniumConveyor
                || plan.block == Blocks.plastaniumConveyor || plan.block == Blocks.surgeConveyor) belt = true;
            if(plan.block == Blocks.graphitePress){
                factory = true;
                factoryPlan = plan;
            }
        }

        assertFalse(drill, "ore drills are dorsal modules and must never be planned as buildings");
        assertTrue(unloader, "ingredients must come from the core stock through an unloader");
        assertTrue(belt, "the chain must include transport");
        assertTrue(factory, "the chain must include the chosen factory");
        assertNotNull(factoryPlan);
        assertNotNull(sourcePlan);

        boolean exitsSourceFootprint = false;
        for(BuildPlan plan : plans){
            if(!isBelt(plan.block)) continue;
            int minX = sourcePlan.x - (sourcePlan.block.size - 1) / 2;
            int minY = sourcePlan.y - (sourcePlan.block.size - 1) / 2;
            int dx = Math.max(0, Math.max(minX - plan.x,
                plan.x - (minX + sourcePlan.block.size - 1)));
            int dy = Math.max(0, Math.max(minY - plan.y,
                plan.y - (minY + sourcePlan.block.size - 1)));
            assertFalse(dx == 0 && dy == 0, "belts must not overlap the planned unloader footprint");
            if(dx + dy == 1) exitsSourceFootprint = true;
        }
        assertTrue(exitsSourceFootprint, "a belt must connect directly to the unloader's outer edge");

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
    void missingExactOreUsesOnlyTheRequiredItemFromCoreStock(){
        //no thorium on this map; it must not mine coal and pretend that coal feeds this recipe
        orbital.world.blocks.production.GenericCrafter rare =
            new orbital.world.blocks.production.GenericCrafter("test-rare"){{
                //a test-only block still needs the same filters content init would create
                itemFilter = new boolean[content.items().size];
                itemFilter[Items.thorium.id] = true;
                liquidFilter = new boolean[content.liquids().size];
                hasItems = true;
                outputItem = new orbital.type.ItemStack(Items.graphite, 1);
            }};

        Seq<BuildPlan> plans = FactoryChainPlanner.build(rare, 0, Team.sharded);

        boolean drill = false, unloader = false, belt = false, factory = false;
        BuildPlan factoryPlan = null;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.mechanicalDrill || plan.block == Blocks.pneumaticDrill
                || plan.block == Blocks.laserDrill) drill = true;
            if(plan.block == Blocks.unloader){
                unloader = true;
                assertEquals(Items.thorium, plan.config,
                    "the core unloader must be configured for the exact ingredient");
            }
            if(isBelt(plan.block)) belt = true;
            if(plan.block == rare){
                factory = true;
                factoryPlan = plan;
            }
        }
        assertFalse(drill, "a different ore cannot satisfy the missing thorium input");
        assertTrue(unloader, "core-stock fallback requires an item-configured unloader");
        assertTrue(belt, "core stock should be routed into the factory when the ore is absent");
        assertTrue(factory, "the factory is always planned");
        assertNotNull(factoryPlan);

        CoreBuild core = (CoreBuild)world.tile(10, 10).build;
        core.items.set(Items.thorium, 0);
        //Petrônio is a wildcard, so it must be empty too for the factory to be unusable
        core.items.set(Items.petronio, 0);
        assertTrue(FactoryChainPlanner.build(rare, 0, Team.sharded).isEmpty(),
            "without the exact ore or core stock, an unusable factory must not be queued");
    }

    @Test
    void liquidFeedFlowsFromPumpTowardFactory(){
        for(int y = 28; y <= 34; y++){
            for(int x = 28; x <= 34; x++){
                world.tile(x, y).setFloor((Floor)Blocks.water);
            }
        }
        orbital.world.blocks.production.GenericCrafter liquidFactory =
            new orbital.world.blocks.production.GenericCrafter("test-water-factory"){{
                itemFilter = new boolean[content.items().size];
                liquidFilter = new boolean[content.liquids().size];
                liquidFilter[orbital.content.Liquids.water.id] = true;
                hasLiquids = true;
                liquidCapacity = 40f;
            }};

        Seq<BuildPlan> plans = FactoryChainPlanner.build(liquidFactory, 0, Team.sharded);

        BuildPlan pump = null, factory = null;
        boolean duct = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.mechanicalPump || plan.block == Blocks.rotaryPump
                || plan.block == Blocks.impulsePump) pump = plan;
            if(plan.block == liquidFactory) factory = plan;
            if(plan.block.isDuct) duct = true;
        }
        assertNotNull(pump, "the exact liquid body must get a pump");
        assertNotNull(factory, "the liquid consumer must be planned");
        assertTrue(duct, "a liquid route must connect pump to factory");
    }

    @Test
    void poweredFactoryGetsItsOwnConnectedSolarSupply(){
        assertTrue(Blocks.siliconSmelter.consumesPower);

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.siliconSmelter, 0, Team.sharded);

        boolean factory = false, solar = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.siliconSmelter) factory = true;
            if(plan.block == Blocks.solarPanel || plan.block == Blocks.largeSolarPanel) solar = true;
        }
        assertTrue(factory, "the powered factory should only be queued with a power supply");
        assertTrue(solar, "when the map has no generator, build a connected solar supply");
    }

    @Test
    void heatCrafterGetsAnAdjacentHeatSource(){
        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.carbideCrucible, 0, Team.sharded);

        BuildPlan factory = null;
        int heaters = 0;
        boolean heaterFacesFactory = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.carbideCrucible) factory = plan;
            if(plan.block == Blocks.heatSource){
                heaters++;
                if(factory != null){
                    int size = Math.max(1, factory.block.size);
                    int minX = factory.x - (size - 1) / 2, minY = factory.y - (size - 1) / 2;
                    int cx = minX + (size - 1) / 2, cy = minY + (size - 1) / 2;
                    int facing = Tile.relativeTo((float)plan.x, (float)plan.y,
                        cx + 0.5f, cy + 0.5f);
                    if(facing >= 0 && facing == plan.rotation) heaterFacesFactory = true;
                }
            }
        }

        assertNotNull(factory, "the heat crafter itself must be queued");
        assertTrue(heaters > 0, "a heat crafter needs an adjacent heat source to actually run");
        assertTrue(heaterFacesFactory, "the heat source must face the factory to transfer heat");
    }

    @Test
    void sectorResourcesIndexOresAndLiquidsOfTheWholeMap(){
        //the base map has a coal patch; add a water body
        for(int y = 28; y <= 32; y++){
            for(int x = 28; x <= 32; x++){
                world.tile(x, y).setFloor((Floor)Blocks.water);
            }
        }

        orbital.engine.world.SectorResources.invalidate();
        orbital.engine.world.SectorResources resources = orbital.engine.world.SectorResources.get();

        assertTrue(resources.has(Items.petronio), "Petrônio must be indexed as present in the sector");
        assertFalse(resources.has(Items.thorium), "thorium is absent from this map");
        assertTrue(resources.has(orbital.content.Liquids.water), "water must be indexed as present");
        assertTrue(resources.ores(Items.thorium).isEmpty(),
            "querying an absent ore returns an empty list, never null");
        assertTrue(resources.summary().contains("petronio"));
    }

    @Test
    void hardOreUsesACapableDrillOrCoreStockInsteadOfADeadDrill(){
        //tungsten has hardness 5; no available drill (max tier 4 without power) can mine it,
        //so the unit must take it from core stock rather than placing a useless drill on the ore
        for(int y = 20; y <= 24; y++){
            for(int x = 13; x <= 18; x++){
                world.tile(x, y).setFloor((Floor)Blocks.oreTungsten);
            }
        }
        orbital.engine.world.SectorResources.invalidate();

        orbital.world.blocks.production.GenericCrafter hard =
            new orbital.world.blocks.production.GenericCrafter("test-hard-ore"){{
                itemFilter = new boolean[content.items().size];
                itemFilter[Items.tungsten.id] = true;
                liquidFilter = new boolean[content.liquids().size];
                hasItems = true;
                outputItem = new orbital.type.ItemStack(Items.graphite, 1);
            }};

        Seq<BuildPlan> plans = FactoryChainPlanner.build(hard, 0, Team.sharded);

        boolean unloader = false;
        for(BuildPlan plan : plans){
            assertFalse(plan.block instanceof orbital.world.blocks.production.Drill,
                "a drill that cannot mine tungsten must not be planned");
            if(plan.block == Blocks.unloader){
                unloader = true;
                assertEquals(Items.tungsten, plan.config);
            }
        }
        assertTrue(unloader, "tungsten must come from the stocked core when no drill can mine it");
    }

    @Test
    void mineableOreUsesCoreStockWhenDrillsAreModules(){
        //coal is minable by units, but ore drills are dorsal modules, never build plans
        orbital.engine.world.SectorResources.invalidate();

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);

        boolean drill = false, unloader = false;
        for(BuildPlan plan : plans){
            if(plan.block instanceof orbital.world.blocks.production.Drill) drill = true;
            if(plan.block == Blocks.unloader) unloader = true;
        }
        assertFalse(drill, "module-only drills must not be queued as buildings");
        assertTrue(unloader, "the factory must draw its input from the core stock");
    }

    @Test
    void phaseWeaverPowerDemandDoesNotCancelTheAutomaticChain(){
        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.phaseWeaver, 0, Team.sharded);

        float generation = 0f;
        boolean factory = false;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.phaseWeaver) factory = true;
            if(plan.block instanceof SolarGenerator solar){
                generation += solar.powerProduction;
            }
        }

        assertTrue(factory, "the actual phase-weaver selection must produce a build queue");
        assertTrue(generation >= Blocks.phaseWeaver.consPower.usage * 1.25f,
            "the connected solar farm must cover the phase weaver's continuous power demand");
    }

    @Test
    void choosingPhaseWeaverActuallyQueuesItsChainOnTheUnit(){
        TestNewFeaturesMode.armPlayerBlock(Blocks.phaseWeaver);

        boolean queuedFactory = false;
        for(BuildPlan plan : orbital.Vars.player.unit().plans){
            if(plan.block == Blocks.phaseWeaver) queuedFactory = true;
        }
        assertTrue(queuedFactory,
            "choosing phase-weaver in the test-mode catalog must enqueue the factory on the unit");
    }

    @Test
    void ordinaryCatalogStructuresAreQueuedWithoutFactoryRecipeExpansion(){
        for(Block block : new Block[]{Blocks.duo, Blocks.copperWall, Blocks.conveyor}){
            Seq<BuildPlan> plans = FactoryChainPlanner.build(block, 0, Team.sharded);

            assertEquals(1, plans.size, block.name + " should queue as a single structure");
            assertSame(block, plans.first().block);
        }

        assertTrue(FactoryChainPlanner.build(Blocks.mechanicalDrill, 0, Team.sharded).isEmpty(),
            "a dorsal drill module must never be queued as a building");
    }

    @Test
    void pumpSelectionFindsWaterAndPowerTurretGetsPower(){
        for(int y = 24; y <= 30; y++){
            for(int x = 24; x <= 30; x++){
                world.tile(x, y).setFloor((Floor)Blocks.water);
            }
        }

        BuildPlan pump = FactoryChainPlanner.build(Blocks.mechanicalPump, 0, Team.sharded).first();
        assertNotNull(pump);
        assertSame(Liquids.water, world.tile(pump.x, pump.y).floor().liquidDrop,
            "the unit must choose water for a mechanical pump");

        Seq<BuildPlan> turretPlans = FactoryChainPlanner.build(Blocks.lancer, 0, Team.sharded);
        assertTrue(turretPlans.contains(plan -> plan.block == Blocks.lancer),
            "the power-consuming structure itself must be queued");
        assertTrue(turretPlans.contains(plan -> plan.block == Blocks.solarPanel
            || plan.block == Blocks.largeSolarPanel),
            "a power-consuming structure gets a power farm when the map has no generator");
    }

    @Test
    void catalogChoiceEnqueuesTheChainWithoutAPlayerLocation(){
        int before = orbital.Vars.player.unit().plans.size;

        TestNewFeaturesMode.armPlayerBlock(Blocks.graphitePress);

        assertNull(TestNewFeaturesMode.playerBlock, "the player must not keep a placement cursor");
        assertFalse(TestNewFeaturesMode.playerToolArmed(),
            "the full-screen map placer must stay off, or clicking the map dereferences a null block");
        assertEquals(Blocks.graphitePress, TestNewFeaturesMode.lastPlayerBlock);
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
