package orbital.game;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.noise.*;
import orbital.content.*;
import orbital.game.Rules.TeamRule;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.maps.*;
import orbital.type.*;
import orbital.world.*;
import orbital.world.blocks.environment.*;

import static orbital.Vars.*;

/**
 * 8x8 battle royale: a 512x512 sector divided into an 8x8 grid of 64 cells. Each cell holds one
 * core; the 64 cores are shared between 8 teams (8 cores each), scattered across the map so every
 * team starts with several footholds. The player commands one team; the other seven are driven by
 * RTS AI. Last team with a living core wins.
 */
public final class BattleRoyaleMode{
    /** Name used by the Gamemode preset / rules.modeName. */
    public static final String rulesName = "battle-royale";
    /** Grid is 8x8 cells, one core per cell. */
    public static final int grid = 8;
    /** Number of competing teams. */
    public static final int teams = 8;
    /** Size of one cell, in tiles. */
    public static final int cellSize = 64;
    /** Full map size, in tiles. */
    public static final int mapSize = grid * cellSize;

    /** The teams in the battle; index 0 is the player's team. */
    private static final Team[] battleTeams = {
        Team.sharded, Team.crux, Team.malis, Team.green,
        Team.blue, Team.neoplastic, Team.get(7), Team.get(8)
    };

    private static Map battleMap;
    private static boolean listenerInstalled;
    private static final int showcaseSeed = 20261120;

    private BattleRoyaleMode(){
    }

    /** @return the team assigned to a given grid cell (row-major). */
    public static Team teamForCell(int row, int col){
        return battleTeams[(row * grid + col) % teams];
    }

    /** @return the competing teams, index 0 being the player's. */
    public static Team[] teams(){
        return battleTeams;
    }

    /** Rules preset applied by the Gamemode entry. */
    public static void applyRules(Rules rules){
        rules.modeName = rulesName;
        //attackMode makes Team.isAI() true for non-default teams and enables last-team-standing
        //victory in Logic.checkGameState; pvp stays false so the RTS AI still controls them.
        rules.attackMode = true;
        rules.waves = false;
        rules.waveTimer = false;
        rules.pvp = false;
        rules.defaultTeam = Team.sharded;
        rules.enemyCoreBuildRadius = 600f;
        rules.buildCostMultiplier = 1f;
        rules.unitCostMultiplier = 1f;
        rules.disableUnitCap = false;

        //RTS AI for the seven opposing teams, configured before playMap so Logic picks it up.
        for(int i = 1; i < battleTeams.length; i++){
            TeamRule rule = rules.teams.get(battleTeams[i]);
            rule.rtsAi = true;
            rule.infiniteResources = true; //AI teams get free resources (no micro-management)
            rule.aiCoreSpawn = true;
        }
        rules.teams.get(Team.sharded).rtsAi = false;
    }

    /** Starts a fresh battle-royale match from the menu. */
    public static void start(){
        if(battleMap == null){
            battleMap = new Map(StringMap.of(
                "name", Core.bundle.get("mode.battleRoyale.map", "Battle Royale 8x8"),
                "author", "Orbital"
            ));
            battleMap.width = mapSize;
            battleMap.height = mapSize;
            world.addMapLoader(battleMap, () -> {
                state.map = battleMap;
                world.loadGenerator(mapSize, mapSize, tiles -> {
                    tiles.fill();
                    generate(tiles);
                });
            });
        }

        if(!listenerInstalled){
            Events.run(Trigger.newGame, BattleRoyaleMode::onNewGame);
            listenerInstalled = true;
        }

        Rules rules = Gamemode.battleRoyale.apply(new Rules());
        control.playMap(battleMap, rules, false, false);
    }

    private static void onNewGame(){
        //Drop the player onto one of its team's cores (RTS AI is already configured in the rules).
        Team playerTeam = player.team();
        if(playerTeam != null && playerTeam.core() != null){
            player.set(playerTeam.core());
            Core.camera.position.set(playerTeam.core());
        }
    }

    /** Generates the 8x8 grid: one core per cell, natural terrain, starter resources per cell. */
    public static void generate(Tiles tiles){
        Rand rand = new Rand(showcaseSeed);

        //natural base terrain with soft biome transitions
        for(Tile tile : tiles){
            double n = Simplex.noise2d(showcaseSeed, 3, 0.55, 1f / 90f, tile.x, tile.y);
            Floor floor = n > 0.25f ? Blocks.stone.asFloor()
                : n < -0.25f ? Blocks.dirt.asFloor()
                : Blocks.grass.asFloor();
            tile.setFloor(floor);
            tile.setBlock(Blocks.air);
            tile.setOverlay(Blocks.air);
        }

        //one core per cell, shared between teams
        for(int row = 0; row < grid; row++){
            for(int col = 0; col < grid; col++){
                Team team = teamForCell(row, col);
                int cx = col * cellSize + cellSize / 2;
                int cy = row * cellSize + cellSize / 2;

                //clear a buildable pocket around the core
                for(int y = cy - 10; y <= cy + 10; y++){
                    for(int x = cx - 10; x <= cx + 10; x++){
                        if(!tiles.in(x, y) || !Mathf.within(x, y, cx, cy, 10f)) continue;
                        Tile t = tiles.getn(x, y);
                        t.setBlock(Blocks.air);
                        t.setOverlay(Blocks.air);
                        if(t.floor().isLiquid) t.setFloor(Blocks.grass.asFloor());
                    }
                }

                //starter ore deposits near the core
                for(int k = 0; k < 3; k++){
                    int ox = cx + rand.random(-8, 8), oy = cy + rand.random(-8, 8);
                    Block ore = k == 0 ? Blocks.oreCopper : k == 1 ? Blocks.oreLead : Blocks.oreCoal;
                    for(int y = oy - 3; y <= oy + 3; y++){
                        for(int x = ox - 3; x <= ox + 3; x++){
                            if(tiles.in(x, y) && Mathf.within(x, y, ox, oy, 3f)){
                                tiles.getn(x, y).setFloor((Floor)ore);
                            }
                        }
                    }
                }

                tiles.getn(cx, cy).setBlock(Blocks.coreShard, team);
            }
        }

        orbital.engine.world.SectorResources.invalidate();
        arc.util.Log.info("[battle-royale] 8x8 grid generated: @ cores across @ teams",
            grid * grid, teams);
    }
}
