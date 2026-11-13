package orbital.game;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.Nullable;
import orbital.content.*;
import orbital.entities.*;
import orbital.entities.units.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.maps.*;
import orbital.type.*;
import orbital.type.unit.ErekirUnitType;
import orbital.ui.dialogs.ModuleTestSetupDialog;
import orbital.world.*;
import orbital.world.meta.BuildVisibility;

import static orbital.Vars.*;

/** Isolated, fixed-map environment used to validate aerial modules against waves of air units. */
public final class TestNewFeaturesMode{
    public static final String rulesName = "test-new-features";
    public static final int mapSize = 160;
    /** Grace period after the setup closes, in ticks. */
    public static final float initialWaveSpacing = 12f * 60f;
    /** Time between air waves, in ticks. */
    public static final float waveSpacing = 45f * 60f;
    /** Difficulty used to build the base air-only wave progression. */
    public static final float waveDifficulty = 0.55f;

    private static Map testMap;
    private static boolean initializeMono;
    private static boolean listenerInstalled;
    private static @Nullable ModuleTestSetupDialog setupDialog;

    /** Air unit chosen in the setup menu, or null before a choice is made. */
    public static @Nullable UnitType selectedType;
    /** True while the setup menu is still open; waves stay disabled until it closes. */
    public static boolean setupOpen;
    /** True once the air-wave defense has started. */
    public static boolean wavesStarted;

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

                    //Air waves need somewhere to enter. Four edge markers keep the whole border
                    //in use instead of piling every wave on one side of the map.
                    int center = mapSize / 2;
                    int edge = 3;
                    tiles.getn(center, edge).setOverlay(Blocks.spawn);
                    tiles.getn(center, mapSize - edge - 1).setOverlay(Blocks.spawn);
                    tiles.getn(edge, center).setOverlay(Blocks.spawn);
                    tiles.getn(mapSize - edge - 1, center).setOverlay(Blocks.spawn);
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
        rules.loadout = resourceLoadout();
        //unit creation is free, but the normal unit cap still applies
        rules.disableUnitCap = false;

        selectedType = null;
        setupOpen = true;
        wavesStarted = false;

        //the tutorial belongs to this round, not to the save: replay the four hints on every
        //fresh visit so a player testing a different combination sees the explanations again
        if(ui != null && ui.hints != null) ui.hints.resetModuleHints();

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
        selectedType = UnitTypes.mono;

        //the setup menu is the way into the environment: choose the hull, install modules,
        //then let the air waves start. Nothing is persisted, so it must be shown every start.
        Core.app.post(() -> {
            if(!active() || !setupOpen) return;
            openSetup();
        });
    }

    /** Reopens the setup menu, unless the defense has already started. */
    public static void openSetup(){
        if(!active() || wavesStarted) return;
        if(setupDialog == null) setupDialog = new ModuleTestSetupDialog();
        setupDialog.openSetup();
    }

    /** @return the resources of both planets, one stack of each visible item. */
    public static Seq<ItemStack> resourceLoadout(){
        Seq<Item> items = new Seq<>();
        for(Item item : Items.serpuloItems){
            if(!item.isHidden() && !items.contains(item)) items.add(item);
        }
        for(Item item : Items.erekirItems){
            if(!item.isHidden() && !items.contains(item)) items.add(item);
        }

        Seq<ItemStack> stacks = new Seq<>();
        for(Item item : items){
            //play() caps every stack at the core's storage capacity, so a large amount simply
            //means "fill this resource to the brim"
            stacks.add(new ItemStack(item, 9999));
        }
        return stacks;
    }

    /** @return the player unit, or null when it is missing or dead. */
    public static @Nullable Unit playerUnit(){
        return player == null || player.dead() || player.unit() == null ? null : player.unit();
    }

    /**
     * @return how many parts are fitted on the player's ship, counting mounted turrets, armour
     * plates and support parts. Used by the tutorial hints to notice the player's first part.
     */
    public static int mountedCount(){
        Unit unit = playerUnit();
        if(unit == null) return 0;

        int count = unit.mountedTurrets() + (unit.wallMounts == null ? 0 : unit.wallMounts.length);
        if(unit.supportMounts != null){
            for(SupportMount mount : unit.supportMounts){
                if(mount != null && mount.block != null) count++;
            }
        }
        return count;
    }

    /**
     * Replaces the current player hull with the chosen air unit, keeping its position.
     * Modules installed on the previous hull are intentionally not transferred: the setup
     * menu asks for the modules only after this choice.
     */
    public static boolean selectUnit(UnitType type){
        if(!active() || type == null || !type.flying || type.isHidden()) return false;

        player.team(Team.sharded);
        Unit previous = playerUnit();
        float x = previous == null ? world.unitWidth() / 2f : previous.x;
        float y = previous == null ? world.unitHeight() / 2f : previous.y;

        if(previous != null && previous.type == type){
            selectedType = type;
            return true;
        }

        Unit unit = type.spawn(Team.sharded, x, y);
        player.unit(unit);
        player.set(unit);
        if(previous != null && previous != unit) previous.remove();
        Core.camera.position.set(unit);

        selectedType = type;
        return true;
    }

    /**
     * Starts the defense phase: fills the rules with air-only spawn groups, enables the wave
     * timer and resets the timer so the first wave arrives after the setup grace period.
     */
    public static boolean beginWaves(){
        if(!active() || wavesStarted || playerUnit() == null) return false;

        Rules rules = state.rules;
        rules.spawns = airWaveGroups();
        rules.waves = true;
        rules.waveTimer = true;
        rules.waveSending = true;
        rules.waitEnemies = true;
        rules.waveSpacing = waveSpacing;
        rules.initialWaveSpacing = initialWaveSpacing;
        rules.airUseSpawns = true;
        rules.hideSpawns = false;

        state.wave = 1;
        state.wavetime = initialWaveSpacing;

        selectedType = playerUnit().type;
        setupOpen = false;
        wavesStarted = true;
        return true;
    }

    /**
     * @return the air wave progression: the engine's air-only generator plus one late group
     * for each visible Erekir air unit, so the defense covers both planets' air fleets.
     */
    public static Seq<SpawnGroup> airWaveGroups(){
        Seq<SpawnGroup> groups = Waves.generate(waveDifficulty, new Rand(), false, true);

        Seq<UnitType> erekirAir = content.units().select(type ->
            type.flying && !type.isHidden() && type instanceof ErekirUnitType);
        erekirAir.sort((a, b) -> Float.compare(a.health, b.health));

        int index = 0;
        for(UnitType type : erekirAir){
            int tier = index++;
            groups.add(new SpawnGroup(type){{
                begin = 4 + tier * 2;
                spacing = 3;
                unitAmount = 1;
                unitScaling = 18f;
                max = 3;
                shields = tier * 40f;
                shieldScaling = 20f;
            }});
        }

        return groups;
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
