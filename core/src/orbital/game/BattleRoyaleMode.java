package orbital.game;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.noise.*;
import orbital.content.*;
import orbital.entities.units.WeaponMount;
import orbital.game.Rules.TeamRule;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.graphics.LootRenderer;
import orbital.maps.*;
import orbital.type.*;
import orbital.ui.dialogs.*;
import orbital.world.*;
import orbital.world.blocks.environment.*;

import static orbital.Vars.*;

/**
 * 8x8 battle royale: a 512x512 sector split into eight team regions. There are no cores - every
 * team starts with a squad of units grouped inside its own region. Vanilla core-based victory is
 * disabled; the last team with living units wins.
 */
public final class BattleRoyaleMode{
    /** Name used by the Gamemode preset / rules.modeName. */
    public static final String rulesName = "battle-royale";
    /** Grid is 8x8 cells, used only to size the map. */
    public static final int grid = 8;
    /** Number of competing teams, and of spawn regions. */
    public static final int teams = 8;
    /** Size of one cell, in tiles. */
    public static final int cellSize = 64;
    /** Full map size, in tiles. */
    public static final int mapSize = grid * cellSize;
    /** Regions are laid out as 4 columns x 2 rows. */
    public static final int regionColumns = 4, regionRows = 2;
    /** Units each team starts with in its region. */
    public static final int squadSize = 6;

    /** The teams in the battle; index 0 is the player's team. */
    private static final Team[] battleTeams = {
        Team.sharded, Team.crux, Team.malis, Team.green,
        Team.blue, Team.neoplastic, Team.get(7), Team.get(8)
    };

    private static Map battleMap;
    private static boolean listenerInstalled;
    private static final int showcaseSeed = 20261120;

    /** The pre-battle loadout wizard is open while the player picks one hull and its modules. */
    private static boolean setupOpen;
    /** Set once the player leaves the wizard; the last team with living units then wins. */
    private static boolean battleStarted;
    private static BattleRoyaleSetupDialog setupDialog;

    private BattleRoyaleMode(){
    }

    /** @return the team assigned to a given grid cell (kept for map-table compatibility). */
    public static Team teamForCell(int row, int col){
        return battleTeams[(row * grid + col) % teams];
    }

    /** @return the competing teams, index 0 being the player's. */
    public static Team[] teams(){
        return battleTeams;
    }

    /** @return the centre of a team's spawn region, in world coordinates. */
    public static float[] regionCenter(int index){
        int col = index % regionColumns, row = index / regionColumns;
        float width = mapSize / (float)regionColumns, height = mapSize / (float)regionRows;
        return new float[]{(col * width + width / 2f) * tilesize, (row * height + height / 2f) * tilesize};
    }

    /**
     * Rules for the local 1-player + 7-AI showcase. Multiplayer matches pass {@code false} so all
     * eight teams are controlled by humans.
     */
    public static void applyRules(Rules rules){
        applyRules(rules, true); //local showcase: 1 human + 7 AI squads
    }

    /** @param aiTeams whether the seven non-player teams should be driven by the combat AI. */
    public static void applyRules(Rules rules, boolean aiTeams){
        rules.modeName = rulesName;
        //attackMode keeps non-default teams flagged as AI, which gives their units the built-in
        //combat controller. The core-based victory check is replaced by checkVictory() below.
        rules.attackMode = true;
        rules.waves = false;
        rules.waveTimer = false;
        rules.pvp = false;
        rules.defaultTeam = Team.sharded;
        rules.enemyCoreBuildRadius = 600f;
        rules.buildCostMultiplier = 1f;
        rules.unitCostMultiplier = 1f;
        //There is no core to raise the cap, so squads need an unlimited cap.
        rules.disableUnitCap = true;
        rules.canGameOver = false;
        //no possession: every pilot fights only its own hull in the 8x8 arena
        rules.possessionAllowed = false;

        //In multiplayer every team is human; pvp also makes Team.isAI() false for all teams.
        if(!aiTeams){
            rules.pvp = true;
            for(Team team : battleTeams){
                TeamRule rule = rules.teams.get(team);
                rule.rtsAi = false;
                rule.infiniteResources = false;
                rule.aiCoreSpawn = false;
            }
            return;
        }

        //AI squads use the ordinary combat controller, which attacks nearby enemies without
        //needing buildings. RTS AI needs cores/buildings to find targets, so it stays off.
        for(Team team : battleTeams){
            TeamRule rule = rules.teams.get(team);
            rule.rtsAi = false;
            rule.aiCoreSpawn = false;
            if(team != Team.sharded) rule.infiniteResources = true;
        }
    }

    /** @return the shared coreless map, registering its generator once. */
    public static Map map(){
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
        return battleMap;
    }

    /** Installs the new-game spawn hook and the unit-based victory check. */
    public static void installListener(){
        if(!listenerInstalled){
            Events.run(Trigger.newGame, BattleRoyaleMode::onNewGame);
            Events.run(Trigger.update, BattleRoyaleMode::checkVictory);
            Events.run(Trigger.update, BattleRoyaleLoot::update);
            Events.run(Trigger.draw, LootRenderer::draw);
            //The host hands each joining player a hull of their own team; clients never spawn.
            Events.on(PlayerJoin.class, e -> {
                if(active() && net.server() && battleStarted) assignPlayerUnit(e.player);
            });
            listenerInstalled = true;
        }
    }

    /**
     * Authoritative start used by the dedicated server (host-br): spawns every squad and the loot,
     * marks the match live and hands a hull to whoever is already connected. Joining players are
     * served by the PlayerJoin hook. No Cloudflare or other relay is involved - plain ArcNet.
     */
    public static void startServerMatch(){
        installListener();
        setupOpen = false;
        battleStarted = true;
        spawnSquads();
        BattleRoyaleLoot.reset();
        BattleRoyaleLoot.spawn();
        for(Player p : Groups.player){
            assignPlayerUnit(p);
        }
        Log.info("[battle-royale] server match started: @ squads, no cores", teams);
    }

    /** @return the index of a battle team, or 0 when unknown. */
    public static int teamIndex(Team team){
        for(int i = 0; i < battleTeams.length; i++){
            if(battleTeams[i] == team) return i;
        }
        return 0;
    }

    /** Gives a player a living hull of their own team, spawning one at the region if needed. */
    public static void assignPlayerUnit(Player p){
        if(p == null || p.team() == null) return;
        Unit pick = null;
        for(Unit u : Groups.unit){
            if(u.team == p.team() && !u.dead()){
                pick = u;
                break;
            }
        }
        if(pick == null){
            float[] c = regionCenter(teamIndex(p.team()));
            pick = UnitTypes.mono.spawn(p.team(), c[0], c[1]);
            stripRemovable(pick);
        }
        p.unit(pick);
        p.set(pick);
    }

    /** Starts a fresh battle-royale match from the menu, opening the loadout wizard after loading. */
    public static void start(){
        Map map = map();
        installListener();

        setupOpen = true;
        battleStarted = false;
        Rules rules = Gamemode.battleRoyale.apply(new Rules());
        control.playMap(map, rules, false, false);
    }

    private static void onNewGame(){
        if(!active()) return;

        //Hosted matches have no local wizard: the fight is live as soon as the map loads.
        if(!setupOpen) battleStarted = true;

        //Only the authority (single-player or host) spawns squads; clients receive them synced.
        if(!net.client()){
            spawnSquads();
            if(!setupOpen){
                BattleRoyaleLoot.reset();
                BattleRoyaleLoot.spawn();
            }
        }

        //Position the local player in its OWN team's region (single-player or joining client).
        if(player != null && !net.server()){
            float[] center = regionCenter(teamIndex(player.team()));
            player.set(center[0], center[1]);
            Core.camera.position.set(center[0], center[1]);
            if(!net.client() && !setupOpen) assignPlayerUnit(player);
        }

        if(setupOpen && !net.client()){
            Core.app.post(() -> {
                if(!setupOpen || !active()) return;
                openSetup();
            });
        }
    }

    /** Spawns one squad per team, clustered inside the team's region. */
    private static void spawnSquads(){
        for(int i = 0; i < battleTeams.length; i++){
            Team team = battleTeams[i];
            float[] centre = regionCenter(i);
            //AI squads need dorsal anchors so they can collect every support category too.
            //Antumbra carries all eight anchors and, unlike quell/quad, targets flying units.
            UnitType type = i == 0 ? UnitTypes.mono : UnitTypes.antumbra;

            for(int k = 0; k < squadSize; k++){
                float offset = k == 0 ? 0f : 9f * tilesize;
                float angle = k * 360f / squadSize;
                Unit unit = type.spawn(team,
                    centre[0] + Angles.trnsx(angle, offset),
                    centre[1] + Angles.trnsy(angle, offset));
                stripRemovable(unit);
            }
        }
        Log.info("[battle-royale] @ squads of @ units spawned, no cores", teams, squadSize);
    }

    /**
     * A battle-royale hull starts with its fixed armament only. Every removable dorsal module -
     * including content-defined ones such as the Mono's Duo - must be picked up from the floor.
     */
    private static void stripRemovable(Unit unit){
        if(unit == null || unit.mounts == null) return;
        Seq<Weapon> removable = new Seq<>();
        for(WeaponMount mount : unit.mounts){
            if(mount != null && mount.weapon != null && mount.weapon.removable) removable.add(mount.weapon);
        }
        for(Weapon weapon : removable) unit.removeWeapon(weapon);
    }

    /**
     * Core-based victory is disabled for this mode (Rules.canGameOver = false), so decide the winner
     * here: the last team with living units wins.
     */
    private static void checkVictory(){
        if(!active() || !battleStarted || state.gameOver || net.client()) return;

        Team last = null;
        int alive = 0;
        for(Team team : battleTeams){
            Teams.TeamData data = team.data();
            if(data == null || data.units == null || data.units.size == 0) continue;
            alive++;
            last = team;
        }

        //Only one survivor ends the match. Zero means every squad is still being created
        //(or was wiped out simultaneously), so the fight simply continues.
        if(alive == 1){
            Events.fire(new GameOverEvent(last));
            state.gameOver = true;
        }
    }

    /** @return true while a battle-royale map is loaded. */
    public static boolean active(){
        return state != null && state.rules != null && rulesName.equals(state.rules.modeName);
    }

    /** @return the unit the player currently controls, or null. */
    public static Unit playerUnit(){
        return player == null ? null : player.unit();
    }

    /**
     * Replaces the player's hull with exactly one chosen airframe. Modules are intentionally not
     * carried over: the player fits them again on the new hull in step 2.
     */
    public static boolean selectUnit(UnitType type){
        if(!active() || type == null || !type.flying || type.isHidden()) return false;

        Unit previous = playerUnit();
        Team team = player.team();
        if(team == null) return false;

        float x = previous == null ? player.x : previous.x;
        float y = previous == null ? player.y : previous.y;
        Unit unit = type.spawn(team, x, y);
        stripRemovable(unit);
        player.unit(unit);
        player.set(unit);
        if(previous != null && previous != unit) previous.remove();
        Core.camera.position.set(unit);
        return true;
    }

    /** Opens the three-step loadout wizard after the map has loaded. */
    public static void openSetup(){
        if(!active() || !setupOpen || battleStarted) return;
        if(setupDialog == null) setupDialog = new BattleRoyaleSetupDialog();
        setupDialog.openSetup();
    }

    /** Leaves the wizard and starts the match. Last team with living units wins. */
    public static boolean beginBattle(){
        if(!active() || playerUnit() == null) return false;
        setupOpen = false;
        battleStarted = true;
        if(!net.client()){
            BattleRoyaleLoot.reset();
            BattleRoyaleLoot.spawn();
        }
        return true;
    }

    /** @return true once the loadout wizard is closed and the fight is live. */
    public static boolean battleStarted(){
        return battleStarted && active();
    }

    /** Generates natural terrain and one open pocket per team region; the map has no cores. */
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

        //clear a pocket and scatter starter ore in each team's region
        for(int i = 0; i < teams; i++){
            float[] centre = regionCenter(i);
            int cx = (int)(centre[0] / tilesize), cy = (int)(centre[1] / tilesize);

            for(int y = cy - 12; y <= cy + 12; y++){
                for(int x = cx - 12; x <= cx + 12; x++){
                    if(!tiles.in(x, y) || !Mathf.within(x, y, cx, cy, 12f)) continue;
                    Tile t = tiles.getn(x, y);
                    t.setBlock(Blocks.air);
                    t.setOverlay(Blocks.air);
                    if(t.floor().isLiquid) t.setFloor(Blocks.grass.asFloor());
                }
            }

            for(int k = 0; k < 5; k++){
                int ox = cx + rand.random(-11, 11), oy = cy + rand.random(-11, 11);
                Block ore = switch(k % 3){
                    case 0 -> Blocks.oreCopper;
                    case 1 -> Blocks.oreLead;
                    default -> Blocks.oreCoal;
                };
                for(int y = oy - 3; y <= oy + 3; y++){
                    for(int x = ox - 3; x <= ox + 3; x++){
                        if(tiles.in(x, y) && Mathf.within(x, y, ox, oy, 3f)){
                            tiles.getn(x, y).setFloor((Floor)ore);
                        }
                    }
                }
            }
        }

        orbital.engine.world.SectorResources.invalidate();
        Log.info("[battle-royale] @ team regions generated, no cores", teams);
    }
}
