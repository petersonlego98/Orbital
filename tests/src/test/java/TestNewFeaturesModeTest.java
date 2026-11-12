import orbital.content.*;
import orbital.game.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
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

    @Test
    void enemyStructureCatalogOnlyHasStructures(){
        var blocks = TestNewFeaturesMode.enemyBlocks();
        assertFalse(blocks.isEmpty());
        for(var block : blocks){
            assertFalse(block.isFloor(), block.name + " is a floor, not a structure");
            assertFalse(block.isAir(), block.name + " is air");
            assertNotEquals(Blocks.cliff, block);
            assertFalse(block.localizedName.isEmpty());
        }
    }

    @Test
    void enemyTeamsNeverContainThePlayerTeam(){
        var teams = TestNewFeaturesMode.enemyTeams();
        assertFalse(teams.isEmpty());
        for(var team : teams){
            assertNotEquals(Team.derelict, team);
            if(player != null && !player.dead()){
                assertNotEquals(player.team(), team, "the player's own team is not an enemy");
            }
        }
    }

    @Test
    void enemyStructuresCanBePlacedAnywhereOnTheMap(){
        createFlatMap(12, 12);

        Rules previous = state.rules;
        state.rules = Gamemode.testFeatures.apply(new Rules());
        try{
            assertTrue(TestNewFeaturesMode.active());

            //far away from the player core: a normal build would refuse this, the tool does not
            assertTrue(TestNewFeaturesMode.placeEnemyBuilding(Blocks.duo, Team.crux, 2, 2, 0, null));
            assertEquals(Team.crux, world.tile(2, 2).team());
            assertEquals(Blocks.duo, world.tile(2, 2).block());

            //another team can take the same spot over
            assertTrue(TestNewFeaturesMode.placeEnemyBuilding(Blocks.scatter, Team.malis, 2, 2, 0, null));
            assertEquals(Team.malis, world.tile(2, 2).team());
            assertEquals(Blocks.scatter, world.tile(2, 2).block());

            //a multiblock near an edge is pulled back into the map instead of failing
            assertTrue(TestNewFeaturesMode.placeEnemyBuilding(Blocks.ripple, Team.green, 11, 11, 0, null));
            assertEquals(Blocks.ripple, world.tile(10, 10).block());
            assertEquals(Team.green, world.tile(9, 9).team());
            assertEquals(world.tile(10, 10).build, world.tile(9, 9).build);

            //out of bounds is still a no-op
            assertFalse(TestNewFeaturesMode.placeEnemyBuilding(Blocks.duo, Team.crux, -5, -5, 0, null));

            //removal and bulk clearing
            assertTrue(TestNewFeaturesMode.placeEnemyBuilding(Blocks.duo, Team.crux, 6, 6, 0, null));
            assertTrue(TestNewFeaturesMode.removeEnemyBuilding(2, 2));
            assertEquals(Blocks.air, world.tile(2, 2).block());
            assertFalse(TestNewFeaturesMode.removeEnemyBuilding(2, 2));
            assertEquals(1, TestNewFeaturesMode.clearEnemyBuildings(Team.crux));
            assertEquals(Blocks.air, world.tile(6, 6).block());
            assertEquals(0, TestNewFeaturesMode.clearEnemyBuildings(Team.crux));
            assertEquals(Team.green, world.tile(10, 10).team());
        }finally{
            state.rules = previous;
        }

        assertFalse(TestNewFeaturesMode.active(), "the rules must be restored so other modes are unaffected");
    }

    @Test
    void enemyToolIsOnlyArmedInTestMode(){
        Rules previous = state.rules;
        try{
            state.rules = Gamemode.sandbox.apply(new Rules());
            TestNewFeaturesMode.enemyBlock = Blocks.duo;
            assertFalse(TestNewFeaturesMode.enemyToolArmed());

            state.rules = Gamemode.testFeatures.apply(new Rules());
            assertTrue(TestNewFeaturesMode.enemyToolArmed());

            TestNewFeaturesMode.enemyBlock = null;
            TestNewFeaturesMode.enemyRemoving = true;
            assertTrue(TestNewFeaturesMode.enemyToolArmed());
        }finally{
            state.rules = previous;
            TestNewFeaturesMode.enemyBlock = null;
            TestNewFeaturesMode.enemyRemoving = false;
        }
    }

    @Test
    void resourceLoadoutCoversBothPlanetsWithoutHiddenItems(){
        var loadout = TestNewFeaturesMode.resourceLoadout();
        assertFalse(loadout.isEmpty());

        for(var stack : loadout){
            assertTrue(stack.amount > 0, stack.item.name + " has a non-positive amount");
            assertFalse(stack.item.isHidden(), stack.item.name + " is hidden but was added to the loadout");
        }

        for(var item : Items.serpuloItems){
            if(!item.isHidden()){
                assertTrue(loadout.contains(stack -> stack.item == item), "missing Serpulo item " + item.name);
            }
        }
        for(var item : Items.erekirItems){
            if(!item.isHidden()){
                assertTrue(loadout.contains(stack -> stack.item == item), "missing Erekir item " + item.name);
            }
        }
    }

    @Test
    void airWaveGroupsOnlyContainFlyingUnits(){
        var groups = TestNewFeaturesMode.airWaveGroups();
        assertFalse(groups.isEmpty());
        for(var group : groups){
            assertNotNull(group.type);
            assertTrue(group.type.flying, group.type.name + " is not an air unit");
        }

        //both planets' air fleets must be represented, otherwise the validation is incomplete
        assertTrue(groups.contains(group -> group.type instanceof orbital.type.unit.ErekirUnitType),
            "the Erekir air fleet is missing from the wave progression");
        assertTrue(groups.contains(group -> !(group.type instanceof orbital.type.unit.ErekirUnitType)),
            "the Serpulo air fleet is missing from the wave progression");
    }

    @Test
    void setupIntervalsArePositiveAndWavesStayDisabledUntilStarted(){
        assertTrue(TestNewFeaturesMode.initialWaveSpacing > 0f);
        assertTrue(TestNewFeaturesMode.waveSpacing > TestNewFeaturesMode.initialWaveSpacing);
        assertEquals(0.55f, TestNewFeaturesMode.waveDifficulty, 0.0001f);

        //beginWaves must refuse to run outside the test environment
        assertFalse(TestNewFeaturesMode.beginWaves());
    }

    private static void createFlatMap(int width, int height){
        world.resize(width, height);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
    }
}
