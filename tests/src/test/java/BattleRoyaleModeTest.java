import arc.struct.ObjectSet;
import orbital.content.Blocks;
import orbital.game.*;
import orbital.gen.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the 8x8 battle-royale sector: 64 cores across 8 teams, with RTS AI rules. */
class BattleRoyaleModeTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void gridHas64CoresAcross8Teams(){
        assertEquals(8, BattleRoyaleMode.grid);
        assertEquals(8, BattleRoyaleMode.teams().length);
        assertEquals(64, BattleRoyaleMode.grid * BattleRoyaleMode.grid);
        assertEquals(512, BattleRoyaleMode.mapSize);

        Tiles tiles = new Tiles(BattleRoyaleMode.mapSize, BattleRoyaleMode.mapSize);
        world.tiles = tiles;
        world.beginMapLoad();
        tiles.fill();
        BattleRoyaleMode.generate(tiles);
        world.endMapLoad();

        ObjectSet<Team> teamsWithCores = new ObjectSet<>();
        int cores = 0;
        for(Tile t : tiles){
            //coreShard is 3x3: count only the anchor tile of each building
            if(t.build != null && t.build.block == Blocks.coreShard
                && t.build.tileX() == t.x && t.build.tileY() == t.y){
                cores++;
                teamsWithCores.add(t.team());
            }
        }
        assertEquals(64, cores, "one core per 8x8 grid cell");
        assertEquals(8, teamsWithCores.size, "the 64 cores belong to 8 distinct teams");
    }

    @Test
    void rulesConfigureAttackModeAndRtsAi(){
        Rules rules = Gamemode.battleRoyale.apply(new Rules());

        assertEquals(BattleRoyaleMode.rulesName, rules.modeName);
        assertTrue(rules.attackMode, "attack mode gives last-team-standing victory");
        assertFalse(rules.waves);
        assertFalse(rules.pvp, "pvp must stay off so the RTS AI keeps working");
        assertEquals(Team.sharded, rules.defaultTeam);

        //7 opposing teams have RTS AI; the player's team does not.
        for(int i = 0; i < BattleRoyaleMode.teams().length; i++){
            Team team = BattleRoyaleMode.teams()[i];
            assertEquals(i != 0, rules.teams.get(team).rtsAi,
                "RTS AI should be on for AI teams, off for the player");
        }
    }
}
