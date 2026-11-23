import arc.struct.ObjectSet;
import orbital.content.Blocks;
import orbital.game.*;
import orbital.gen.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the coreless 8x8 battle-royale sector: eight team regions and combat-AI rules. */
class BattleRoyaleModeTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void mapHasEightTeamRegionsAndNoCores(){
        assertEquals(8, BattleRoyaleMode.grid);
        assertEquals(8, BattleRoyaleMode.teams().length);
        assertEquals(64, BattleRoyaleMode.grid * BattleRoyaleMode.grid);
        assertEquals(512, BattleRoyaleMode.mapSize);
        assertEquals(8, BattleRoyaleMode.regionColumns * BattleRoyaleMode.regionRows);

        Tiles tiles = new Tiles(BattleRoyaleMode.mapSize, BattleRoyaleMode.mapSize);
        world.tiles = tiles;
        world.beginMapLoad();
        tiles.fill();
        BattleRoyaleMode.generate(tiles);
        world.endMapLoad();

        int cores = 0;
        for(Tile t : tiles){
            if(t.build != null && t.build.block == Blocks.coreShard
                && t.build.tileX() == t.x && t.build.tileY() == t.y){
                cores++;
            }
        }
        assertEquals(0, cores, "the battle-royale map must not contain cores");

        ObjectSet<String> regions = new ObjectSet<>();
        for(int i = 0; i < BattleRoyaleMode.teams().length; i++){
            float[] centre = BattleRoyaleMode.regionCenter(i);
            assertTrue(centre[0] > 0f && centre[0] < BattleRoyaleMode.mapSize * tilesize);
            assertTrue(centre[1] > 0f && centre[1] < BattleRoyaleMode.mapSize * tilesize);
            regions.add((int)centre[0] + ":" + (int)centre[1]);
        }
        assertEquals(8, regions.size, "each team must have its own spawn region");
    }

    @Test
    void multiplayerRulesGiveEightHumanTeams(){
        Rules rules = Gamemode.battleRoyale.apply(new Rules());
        BattleRoyaleMode.applyRules(rules, false);

        assertTrue(rules.pvp, "multiplayer must disable AI-controlled teams");
        assertTrue(rules.attackMode, "attack mode keeps non-default teams flagged as AI");
        assertFalse(rules.canGameOver, "the vanilla core-based victory check must stay off");
        assertTrue(rules.disableUnitCap, "squads need a cap because there are no cores");
        assertFalse(rules.possessionAllowed, "multiplayer pilots must not possess other hulls");
        for(Team team : BattleRoyaleMode.teams()){
            assertFalse(rules.teams.get(team).rtsAi, team + " must be human-controlled");
            assertFalse(rules.teams.get(team).infiniteResources, team + " must not get free resources");
        }
    }

    @Test
    void rulesConfigureCombatAiAndUnitVictory(){
        Rules rules = Gamemode.battleRoyale.apply(new Rules());

        assertEquals(BattleRoyaleMode.rulesName, rules.modeName);
        assertTrue(rules.attackMode, "attack mode keeps non-default teams AI-controlled");
        assertFalse(rules.waves);
        assertFalse(rules.pvp, "pvp must stay off so the combat AI keeps working");
        assertEquals(Team.sharded, rules.defaultTeam);
        assertFalse(rules.canGameOver, "victory is decided by living units in BattleRoyaleMode");
        assertTrue(rules.disableUnitCap, "there is no core to raise the unit cap");
        assertFalse(rules.possessionAllowed, "no pilot may take over another hull in 8x8");

        //AI squads use the built-in combat controller, which attacks units without buildings.
        for(Team team : BattleRoyaleMode.teams()){
            assertFalse(rules.teams.get(team).rtsAi, team + " must use the combat controller");
            assertFalse(rules.teams.get(team).aiCoreSpawn, "there are no cores to spawn from");
        }
    }
}
