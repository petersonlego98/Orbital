package orbital.game;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.gen.Unit;
import orbital.graphics.*;
import orbital.type.*;
import orbital.world.*;

import static orbital.Vars.*;

/**
 * Ground loot of the 8x8 battle royale: the authoritative local registry of dropped modules.
 *
 * <p>Drops are neutral: the player installs one by clicking it, and every AI squad scans for a
 * compatible module nearby. Nothing here is persisted or serialized yet - the online stack is a
 * later phase - so all of this stays local and is reset on every new match.</p>
 */
public final class BattleRoyaleLoot{
    /** One new module appears every 30 seconds of match time, anywhere on the sector. */
    private static final float periodicInterval = 30f;
    /** Fresh sequence per match: initial loot is no longer identical on every run. */
    private static Rand random = new Rand(1);
    private static float spawnTimer = periodicInterval;
    /** World units a unit must be within to install a clicked module. */
    public static final float pickupRange = 32f;

    /** Every region gets one repair, one boost, one weapon and one rotating extra category. */
    private static final ModuleDropKind[][] pattern = {
        {ModuleDropKind.repair, ModuleDropKind.overdrive, ModuleDropKind.weapon, ModuleDropKind.weapon},
        {ModuleDropKind.repair, ModuleDropKind.force, ModuleDropKind.weapon, ModuleDropKind.unitRepair},
        {ModuleDropKind.repair, ModuleDropKind.overdrive, ModuleDropKind.weapon, ModuleDropKind.tractor},
        {ModuleDropKind.repair, ModuleDropKind.force, ModuleDropKind.weapon, ModuleDropKind.pointDefense},
        {ModuleDropKind.repair, ModuleDropKind.overdrive, ModuleDropKind.weapon, ModuleDropKind.liquid},
        {ModuleDropKind.repair, ModuleDropKind.force, ModuleDropKind.weapon, ModuleDropKind.transport},
        {ModuleDropKind.repair, ModuleDropKind.overdrive, ModuleDropKind.weapon, ModuleDropKind.armor},
        {ModuleDropKind.repair, ModuleDropKind.force, ModuleDropKind.weapon, ModuleDropKind.weapon},
    };

    private static final Seq<DroppedModule> drops = new Seq<>();
    private static final ObjectFloatMap<Unit> aiCooldown = new ObjectFloatMap<>();
    private static int nextId = 1;

    private BattleRoyaleLoot(){
    }

    /** @return the live list of drops; only the renderer and the hit-test should iterate it. */
    public static Seq<DroppedModule> all(){
        return drops;
    }

    /** Clears every drop and the AI timers. Called when a match is loaded. */
    public static void reset(){
        drops.clear();
        aiCooldown.clear();
        nextId = 1;
        random = new Rand((int)(System.nanoTime() & 0x7fffffff));
        spawnTimer = periodicInterval;
        LootRenderer.clearInspection();
    }

    /** Scatters four modules inside each of the eight cleared team pockets, randomly per match. */
    public static void spawn(){
        spawn(random);
    }

    /** Same spawn with an explicit sequence, useful for deterministic tests. */
    public static void spawn(Rand rand){
        if(!BattleRoyaleMode.active() || !drops.isEmpty() || world.tiles == null) return;

        for(int i = 0; i < BattleRoyaleMode.teams().length; i++){
            ModuleDropKind[] kinds = pattern[i % pattern.length];
            float[] centre = BattleRoyaleMode.regionCenter(i);
            for(ModuleDropKind kind : kinds){
                DroppedModule drop = create(kind, centre[0], centre[1], rand);
                if(drop != null){
                    drop.id = nextId++;
                    drops.add(drop);
                }
            }
        }
        Log.info("[battle-royale] @ modules dropped on the sector floor", drops.size);
    }

    /** Finds a free spot in the team pocket and builds a drop of the requested kind there. */
    private static @Nullable DroppedModule create(ModuleDropKind kind, float cx, float cy, Rand rand){
        for(int attempt = 0; attempt < 24; attempt++){
            float angle = rand.random(360f), distance = rand.random(3f, 10f) * tilesize;
            float x = cx + Angles.trnsx(angle, distance), y = cy + Angles.trnsy(angle, distance);
            Tile tile = world.tileWorld(x, y);
            if(tile == null || tile.block() != Blocks.air || tile.floor().isLiquid) continue;

            DroppedModule drop = createAt(kind, x, y, rand);
            if(drop != null) return drop;
        }
        return null;
    }

    /** Builds a drop of a kind at an already chosen free world position. */
    private static @Nullable DroppedModule createAt(ModuleDropKind kind, float x, float y, Rand rand){
        return switch(kind){
            case weapon -> createWeapon(rand, x, y);
            default -> DroppedModule.fromBlock(blockFor(kind, rand), x, y);
        };
    }

    /**
     * Drops one random module on a random free tile anywhere in the sector. Called every 30
     * seconds of match time by {@link #update()}.
     */
    public static void spawnRandomOne(){
        if(!BattleRoyaleMode.active() || world.tiles == null) return;
        ModuleDropKind[] kinds = ModuleDropKind.values();

        for(int attempt = 0; attempt < 40; attempt++){
            int tx = random.random(1, world.width() - 1);
            int ty = random.random(1, world.height() - 1);
            Tile tile = world.tile(tx, ty);
            if(tile == null || tile.block() != Blocks.air || tile.floor().isLiquid) continue;

            float x = tx * tilesize, y = ty * tilesize;
            DroppedModule drop = createAt(kinds[random.random(kinds.length - 1)], x, y, random);
            if(drop == null) continue;
            drop.id = nextId++;
            drops.add(drop);
            Fx.spawn.at(x, y);
            return;
        }
    }

    private static @Nullable DroppedModule createWeapon(Rand rand, float x, float y){
        //half the weapon drops are adapted turret blocks, half are native unit weapons
        if(rand.nextBoolean()){
            String[] names = {"duo", "scatter", "hail", "arc", "lancer", "swarmer", "salvo", "ripple"};
            return DroppedModule.fromBlock(content.block(names[rand.random(names.length - 1)]), x, y);
        }

        Seq<WeaponModules.Definition> eligible = new Seq<>();
        for(WeaponModules.Definition definition : WeaponModules.all()){
            if(definition.size <= 2 && definition.tier <= 3 && definition.weapon.region != null) eligible.add(definition);
        }
        if(eligible.isEmpty()) return DroppedModule.fromBlock(content.block("duo"), x, y);
        return DroppedModule.fromDefinition(eligible.random(rand), x, y);
    }

    private static @Nullable Block blockFor(ModuleDropKind kind, Rand rand){
        String[] names = switch(kind){
            case repair -> new String[]{"mender", "mend-projector"};
            case unitRepair -> new String[]{"repair-point", "repair-turret", "unit-repair-tower"};
            case overdrive -> new String[]{"overdrive-projector", "overdrive-dome"};
            case force -> new String[]{"force-projector"};
            case tractor -> new String[]{"parallax"};
            case pointDefense -> new String[]{"segment"};
            case liquid -> new String[]{"wave", "tsunami"};
            case transport -> new String[]{"mass-driver"};
            case armor -> new String[]{"copper-wall-large", "titanium-wall-large", "plastanium-wall-large", "surge-wall-large"};
            default -> new String[]{"duo"};
        };
        return content.block(names[rand.random(names.length - 1)]);
    }

    /** @return the closest drop within a screen-space radius, or null. */
    public static @Nullable DroppedModule findAt(float x, float y, float radius){
        float best = radius * radius;
        DroppedModule found = null;
        for(DroppedModule drop : drops){
            float distance = Mathf.dst2(x, y, drop.x, drop.y);
            if(distance <= best){
                best = distance;
                found = drop;
            }
        }
        return found;
    }

    /** Removes a drop after a successful install or an AI pickup. */
    public static void remove(DroppedModule drop){
        if(drop == null) return;
        drops.remove(drop);
        LootRenderer.clearInspection(drop);
    }

    /** Removes a mounted module from its unit and leaves it on the floor at the unit's position. */
    public static void dropSupport(Unit unit, SupportMount mount){
        if(unit == null || mount == null || !unit.removeSupportMount(mount)) return;
        leave(DroppedModule.fromSupport(mount, unit.x, unit.y));
    }

    /** Removes a wall plate from its unit and leaves it on the floor at the unit's position. */
    public static void dropWall(Unit unit, WallMount mount){
        if(unit == null || mount == null || !unit.removeWallMount(mount)) return;
        leave(DroppedModule.fromWall(mount, unit.x, unit.y));
    }

    /** Removes a weapon module from its unit and leaves it on the floor at the unit's position. */
    public static void dropWeapon(Unit unit, Weapon weapon){
        if(unit == null || weapon == null || !unit.removeWeapon(weapon)) return;
        leave(DroppedModule.fromWeapon(weapon, unit.x, unit.y));
    }

    private static void leave(@Nullable DroppedModule drop){
        if(drop == null) return;
        drop.id = nextId++;
        drops.add(drop);
        Fx.select.at(drop.x, drop.y);
        if(ui != null) ui.showInfoToast(Core.bundle.format("module.drop.dropped", drop.name()), 3f);
    }

    /** Per-frame upkeep: tooltip lifetime, periodic loot and AI collection. */
    public static void update(){
        if(!BattleRoyaleMode.active()) return;

        LootRenderer.tick();
        if(!BattleRoyaleMode.battleStarted() || player == null) return;

        spawnTimer -= Time.delta;
        if(spawnTimer <= 0f){
            spawnTimer = periodicInterval;
            spawnRandomOne();
        }
        if(drops.isEmpty()) return;

        for(Team team : BattleRoyaleMode.teams()){
            if(team == player.team()) continue;
            Teams.TeamData data = team.data();
            if(data == null || data.units == null) continue;

            for(Unit unit : data.units){
                if(unit == null || unit.dead) continue;
                float remaining = aiCooldown.get(unit, 0f) - Time.delta;
                if(remaining > 0f){
                    aiCooldown.put(unit, remaining);
                    continue;
                }

                DroppedModule best = null;
                float bestDistance = 28f * 28f;
                for(DroppedModule drop : drops){
                    if(!drop.canInstall(unit)) continue;
                    float distance = Mathf.dst2(unit.x, unit.y, drop.x, drop.y);
                    if(distance < bestDistance){
                        bestDistance = distance;
                        best = drop;
                    }
                }

                if(best != null && best.install(unit)){
                    remove(best);
                    Fx.pickup.at(best.x, best.y);
                    aiCooldown.put(unit, 7f + Mathf.random(3f));
                }else{
                    aiCooldown.put(unit, 2f);
                }
            }
        }
    }
}
