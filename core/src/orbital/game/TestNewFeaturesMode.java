package orbital.game;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.Nullable;
import orbital.content.*;
import orbital.entities.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.maps.*;
import orbital.type.*;
import orbital.world.*;
import orbital.world.meta.BuildVisibility;

import static orbital.Vars.*;

/** Isolated, fixed-map sandbox used to test newly-added unit and turret features. */
public final class TestNewFeaturesMode{
    public static final String rulesName = "test-new-features";
    public static final int mapSize = 160;

    private static Map testMap;
    private static boolean initializeMono;
    private static boolean listenerInstalled;

    /** Team that receives structures placed with the enemy-building tool. */
    public static Team enemyTeam = Team.crux;
    /** Block armed by the enemy-building tool; null means nothing is selected. */
    public static @Nullable Block enemyBlock;
    /** When true, the enemy-building tool removes whatever is under the cursor instead of placing. */
    public static boolean enemyRemoving;

    private TestNewFeaturesMode(){
    }

    public static boolean active(){
        return state != null && state.rules != null && rulesName.equals(state.rules.modeName);
    }

    /** Starts a fresh, non-persistent test session on the built-in flat map. */
    public static void start(){
        if(testMap == null){
            testMap = new Map(StringMap.of(
                "name", Core.bundle.get("mode.testfeatures.map", "Teste Novos Recursos"),
                "author", "Orbital"
            ));
            testMap.width = mapSize;
            testMap.height = mapSize;
            world.addMapLoader(testMap, () -> {
                state.map = testMap;
                world.loadGenerator(mapSize, mapSize, tiles -> {
                    tiles.fill();
                    for(Tile tile : tiles){
                        tile.setFloor(Blocks.grass.asFloor());
                    }
                    tiles.getn(mapSize / 2, mapSize / 2).setBlock(Blocks.coreShard, Team.sharded);
                });
            });
        }

        if(!listenerInstalled){
            Events.run(Trigger.newGame, TestNewFeaturesMode::spawnInitialMono);
            listenerInstalled = true;
        }

        Rules rules = Gamemode.testFeatures.apply(new Rules());
        rules.planet = Planets.qatia;
        rules.waves = false;
        rules.waveTimer = false;
        rules.loadout.clear();
        //unit creation is free, but the normal unit cap still applies
        rules.disableUnitCap = false;

        initializeMono = true;
        //createSave = false: this is a disposable test session, it must never touch the save list
        control.playMap(testMap, rules, false, false);
    }

    private static void spawnInitialMono(){
        if(!initializeMono || !active()) return;
        initializeMono = false;

        player.team(Team.sharded);
        Unit mono = UnitTypes.mono.spawn(Team.sharded, world.unitWidth() / 2f, world.unitHeight() / 2f);
        player.unit(mono);
        player.set(mono);
        Core.camera.position.set(mono);
    }

    /** Spawns an aerial unit beside the player's unit, without taking control of it. */
    public static boolean spawn(UnitType type){
        if(!active() || type == null || !type.flying || type.isHidden()) return false;
        if(!Units.canCreate(player.team(), type)) return false;

        Unit source = player.unit();
        if(source == null || source.dead) return false;

        float distance = source.hitSize / 2f + type.hitSize / 2f + tilesize * 2f;
        float angle = Mathf.random(360f);
        float x = Mathf.clamp(source.x + Angles.trnsx(angle, distance), type.hitSize, world.unitWidth() - type.hitSize);
        float y = Mathf.clamp(source.y + Angles.trnsy(angle, distance), type.hitSize, world.unitHeight() - type.hitSize);

        type.spawn(player.team(), x, y, source.rotation);
        return true;
    }

    /** @return every aerial unit in the content list, regardless of unlock state. */
    public static Seq<UnitType> aerialTypes(){
        Seq<UnitType> result = content.units().select(type -> type.flying && !type.isHidden());
        result.sort((a, b) -> a.localizedName.compareToIgnoreCase(b.localizedName));
        return result;
    }

    /**
     * @return every team that can hold enemy structures: all editor base teams except the player's
     * own and the derelicts. On a planet map these normally include the campaign enemies.
     */
    public static Seq<Team> enemyTeams(){
        Seq<Team> result = new Seq<>();
        for(Team team : Team.baseTeams){
            if(team == Team.derelict || (player != null && !player.dead() && team == player.team())) continue;
            result.add(team);
        }
        return result;
    }

    /**
     * @return the editor block catalog, sorted by name, for the enemy-structure picker.
     * Floors, cliffs and debug-only content are left out, so the list holds structures (turrets,
     * power, walls, cores...) exactly like the map editor palette.
     */
    public static Seq<Block> enemyBlocks(){
        Seq<Block> result = content.blocks().select(block ->
            block.inEditor
            && block.buildVisibility != BuildVisibility.debugOnly
            && block.buildVisibility != BuildVisibility.hidden
            && !block.isAir() && !block.isFloor() && block != Blocks.cliff
        );
        result.sort((a, b) -> a.localizedName.compareToIgnoreCase(b.localizedName));
        return result;
    }

    /** @return whether the enemy-building tool is armed, either placing or removing. */
    public static boolean enemyToolArmed(){
        return active() && (enemyBlock != null || enemyRemoving);
    }

    /**
     * Places one enemy structure at this tile.
     *
     * <p>Unlike the normal build path, the campaign restrictions (enemy core build radius,
     * placement overlap, darkness) are skipped: the test map only has the player's own core, and
     * the point of the tool is to put an enemy base <b>anywhere</b> it is needed to aim at, not
     * inside a protected radius around a core that does not exist.</p>
     *
     * @return whether the structure was placed.
     */
    public static boolean placeEnemyBuilding(Block block, Team team, int x, int y, int rotation, @Nullable Object config){
        if(block == null || team == null || !active()) return false;

        //multiblocks are anchored at their center tile, so pull the cursor back into the map
        if(block.isMultiblock()){
            x = Mathf.clamp(x, (block.size - 1) / 2, world.width() - block.size / 2 - 1);
            y = Mathf.clamp(y, (block.size - 1) / 2, world.height() - block.size / 2 - 1);
        }

        Tile tile = world.tile(x, y);
        if(tile == null) return false;

        tile.setBlock(block, team, rotation);

        if(tile.build != null){
            tile.build.health = block.health;
            //power links and other proximity-dependent state are resolved from here; in a local
            //session this is the same call a regular instant build ends up performing
            tile.build.placed();
            if(config != null) tile.build.configured(null, config);
        }

        block.placeEnded(tile, null, rotation, config);
        Events.fire(new BlockBuildEndEvent(tile, null, team, false, config));

        //the normal path plays the placement effect when the tile is visible to the team; do the
        //same here so placing a structure feels like the regular instant build
        if(!headless && state.isGame() && fogControl.isVisibleTile(team, tile.x, tile.y)){
            block.placeEffect.at(tile.drawx(), tile.drawy(), block.size);
        }
        return true;
    }

    /** Removes the structure at this tile, however big or whose team it is. @return whether one was removed. */
    public static boolean removeEnemyBuilding(int x, int y){
        if(!active()) return false;
        Tile tile = world.tile(x, y);
        if(tile == null || tile.build == null || tile.block() == Blocks.air) return false;
        tile.remove();
        return true;
    }

    /** Removes every structure of one team from the test map. @return how many were removed. */
    public static int clearEnemyBuildings(Team team){
        if(!active() || team == null) return 0;

        Seq<Tile> targets = new Seq<>();
        //collect first: removing a multiblock silently clears its neighbors while iterating
        for(Tile tile : world.tiles){
            if(tile.build != null && tile.isCenter() && tile.build.team == team && tile.block() != Blocks.air){
                targets.add(tile);
            }
        }
        for(Tile tile : targets){
            tile.remove();
        }
        return targets.size;
    }
}
