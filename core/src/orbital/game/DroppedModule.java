package orbital.game;

import arc.*;
import arc.graphics.g2d.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.gen.Unit;
import orbital.type.*;
import orbital.world.*;
import orbital.world.blocks.defense.turrets.Turret;

import static orbital.Vars.*;

/**
 * One module lying on the sector floor in the 8x8 battle royale.
 *
 * <p>This is plain data, not an entity: it holds the content definition it came from and a world
 * position. Installation always goes through the existing {@code WeaponsComp} mounting API, so no
 * dorsal rule (slot size, budget, category exclusivity, ammo compatibility) is duplicated here.
 * The same representation is used when a player presses the red X on an equipped module - the
 * mount is removed and a drop with the same definition is created at the unit's position.</p>
 */
public final class DroppedModule{
    public final ModuleDropKind kind;
    /** Block behind this drop (support module, wall plate or turret), or null for weapon modules. */
    public final @Nullable Block block;
    /** Native weapon module definition, or null when {@link #block} is set. */
    public final @Nullable WeaponModules.Definition definition;
    public final float x, y;
    /** Stable identity, used for the pulsing phase and, later, for network replication. */
    public int id;

    private DroppedModule(ModuleDropKind kind, @Nullable Block block, @Nullable WeaponModules.Definition definition, float x, float y){
        this.kind = kind;
        this.block = block;
        this.definition = definition;
        this.x = x;
        this.y = y;
    }

    /** @return a drop backed by a block, or null when that block is not a mountable module. */
    public static @Nullable DroppedModule fromBlock(@Nullable Block block, float x, float y){
        if(block == null) return null;

        ModuleDropKind kind = ModuleDropKind.of(SupportModuleCategory.of(block));
        if(kind == null && block instanceof Turret turret && TurretWeapon.canAdapt(turret)){
            kind = ModuleDropKind.weapon;
        }
        if(kind == null) return null;
        return new DroppedModule(kind, block, null, x, y);
    }

    /** @return a drop backed by a native weapon module definition. */
    public static @Nullable DroppedModule fromDefinition(@Nullable WeaponModules.Definition definition, float x, float y){
        if(definition == null || definition.weapon == null) return null;
        return new DroppedModule(ModuleDropKind.weapon, null, definition, x, y);
    }

    /** @return the drop that represents a support module being taken off a hull. */
    public static @Nullable DroppedModule fromSupport(@Nullable SupportMount mount, float x, float y){
        return mount == null ? null : fromBlock(mount.block, x, y);
    }

    /** @return the drop that represents a wall plate being taken off a hull. */
    public static @Nullable DroppedModule fromWall(@Nullable WallMount mount, float x, float y){
        return mount == null ? null : fromBlock(mount.block, x, y);
    }

    /**
     * @return the drop that represents a weapon being taken off a hull. Both turret modules and
     * native weapon modules carry enough fields on the runtime {@link Weapon} to find the
     * definition they were copied from.
     */
    public static @Nullable DroppedModule fromWeapon(@Nullable Weapon weapon, float x, float y){
        if(weapon == null) return null;
        if(weapon instanceof TurretWeapon turret) return fromBlock(turret.turret, x, y);

        if(weapon.dorsalSource != null && weapon.dorsalSourceWeapon != null){
            for(WeaponModules.Definition definition : WeaponModules.all()){
                if(definition.unit == weapon.dorsalSource && definition.weapon == weapon.dorsalSourceWeapon){
                    return fromDefinition(definition, x, y);
                }
            }
        }
        return null;
    }

    /** @return the localized name shown on the world label. */
    public String name(){
        if(block != null) return block.localizedName;
        if(definition != null) return definition.name;
        return Core.bundle.get("module.drop.unknown");
    }

    /** @return the localized category / weapon type. */
    public String type(){
        return Core.bundle.get(kind.typeKey);
    }

    /** @return a short, wrapped description of what the module does. */
    public String effect(){
        if(block != null){
            String description = block.displayDescription();
            return wrap(description == null ? "" : description, 42);
        }
        if(definition != null){
            return wrap(Core.bundle.format("module.drop.effect.weapon", definition.shot), 42);
        }
        return "";
    }

    /**
     * @return the actual module sprite (the same art the hull carries), falling back to the UI
     * icon only when the atlas region is missing, e.g. on headless runs.
     */
    public @Nullable TextureRegion icon(){
        if(block != null){
            if(block.region != null && block.region.found()) return block.region;
            return block.uiIcon;
        }
        if(definition != null){
            if(definition.weapon.region != null && definition.weapon.region.found()){
                return definition.weapon.region;
            }
            if(Core.atlas != null){
                TextureRegion region = Core.atlas.find(definition.weapon.name);
                if(region != null && region.found()) return region;
            }
        }
        return null;
    }

    /** @return true when this module can be mounted on the unit right now. */
    public boolean canInstall(Unit unit){
        if(unit == null || unit.dead || unit.type == null) return false;

        if(block != null){
            SupportModuleCategory category = SupportModuleCategory.of(block);
            if(category == SupportModuleCategory.armor) return unit.canMountWall(block);
            if(category != null) return unit.canMountSupport(block);
            if(block instanceof Turret turret) return unit.canMount(turret);
            return false;
        }
        if(definition != null) return unit.canMountWeaponModule(definition);
        return false;
    }

    /** Installs the module on a unit. @return true when the mount succeeded. */
    public boolean install(Unit unit){
        if(unit == null || unit.dead) return false;

        if(block != null){
            SupportModuleCategory category = SupportModuleCategory.of(block);
            if(category == SupportModuleCategory.armor) return unit.mountWall(block) != null;
            if(category != null) return unit.mountSupport(block) != null;
            if(block instanceof Turret turret) return unit.mountTurret(turret) != null;
            return false;
        }
        if(definition != null) return unit.mountWeaponModule(definition) != null;
        return false;
    }

    /** @return the reason shown when {@link #install(Unit)} failed. */
    public String reason(Unit unit){
        SupportModuleCategory category = block == null ? null : SupportModuleCategory.of(block);
        if(category != null && category != SupportModuleCategory.armor && unit.type != null
            && (unit.type.supportAnchors == null || unit.type.supportAnchors.isEmpty())){
            return Core.bundle.get("module.drop.reason.hull");
        }
        if(unit.type != null && unit.mountedTurrets() + (unit.wallMounts == null ? 0 : unit.wallMounts.length)
            >= unit.type.turretSlots.size){
            return Core.bundle.get("module.drop.reason.slot");
        }
        if(block instanceof Turret turret && !unit.ammoCompatible(turret)){
            return Core.bundle.get("module.drop.reason.ammo");
        }
        if(category != null && unit.hasSupportCategory(category)){
            return Core.bundle.get("module.drop.reason.category");
        }
        return Core.bundle.get("module.drop.reason.slot");
    }

    /** Simple word wrap so a long block description becomes a readable floating tooltip. */
    private static String wrap(String text, int max){
        if(text == null || text.isEmpty()) return "";
        //world labels are drawn straight through BitmapFont, so scene2d colour tags would show up
        //as literal text; strip them before wrapping
        text = Strings.stripColors(text);
        StringBuilder out = new StringBuilder();
        int line = 0;
        for(String word : text.split("\\s+")){
            if(word.isEmpty()) continue;
            if(line > 0 && line + word.length() + 1 > max){
                out.append('\n');
                line = 0;
            }else if(line > 0){
                out.append(' ');
                line++;
            }
            out.append(word);
            line += word.length();
        }
        return out.toString();
    }
}
