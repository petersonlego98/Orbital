package orbital.content;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import orbital.type.*;
import orbital.type.weapons.*;

import static orbital.Vars.*;

/**
 * Catalog of native unit weapons that can be mounted as combat modules.
 *
 * <p>A weapon module is a runtime copy of a unit's weapon definition. It keeps the weapon's
 * behavior and bullet, but is placed on a dorsal turret slot like a turret block module:
 * one footprint, one slot and dorsal budget cost.</p>
 *
 * <p>The list is built from {@link Vars#content} after units have been created, so it also
 * covers modded units that copy a weapon into another hull.</p>
 */
public final class WeaponModules{
    /** A mountable unit weapon module. */
    public static final class Definition{
        public final UnitType unit;
        public final Weapon weapon;
        /** dorsal footprint size in blocks; derived from the weapon sprite. */
        public final int size;
        /** dorsal footprint cost in px²; {@code size * TurretSlot.pxTile} squared. */
        public final float cost;
        /** bundle-less display name; {@code unit} plus weapon label. */
        public final String name;

        Definition(UnitType unit, Weapon weapon, int size, float cost, String name){
            this.unit = unit;
            this.weapon = weapon;
            this.size = size;
            this.cost = cost;
            this.name = name;
        }

        /** @return the weapon's native sprite side in atlas pixels. */
        public float regionPx(){
            if(weapon.region != null && weapon.region.found()){
                return Math.max(1f, Math.max(weapon.region.width, weapon.region.height));
            }
            return size * TurretSlot.pxTile;
        }

        /** @return a runtime copy of the weapon, ready to be installed on a dorsal slot. */
        public Weapon copy(){
            Weapon copy = weapon.copy();
            copy.name = weapon.name;
            copy.removable = true;
            copy.dorsalSource = unit;
            copy.dorsalSourceWeapon = weapon;
            copy.dorsalSize = size;
            copy.dorsalCost = cost;

            //A mirrored hull weapon is one of a pair whose two entries alternate fire, and
            //UnitType.init() doubles reload AND recoilTime on both of them so the pair keeps
            //the authored rate. A dorsal module is a single mount: without undoing that
            //compensation every module taken from a mirrored weapon would fire at half speed.
            if(weapon.mirror){
                copy.reload = weapon.reload / 2f;
                copy.recoilTime = weapon.recoilTime / 2f;
            }

            //Alternation also gates the shot: Weapon#update only fires a mount when
            //wasFlipped == flipSprite, so a copy that kept the mirrored arm's flags
            //(alternate = true, flipSprite = true) never fired at all. otherSide pointed
            //into the *carrier's* mount array too, flipping an unrelated weapon's side.
            //A module is a single weapon, so none of that applies.
            copy.alternate = false;
            copy.otherSide = -1;
            copy.flipSprite = false;
            copy.mirror = false;
            return copy;
        }
    }

    private static @Nullable Seq<Definition> cached;

    private WeaponModules(){}

    /** @return every combat-capable unit weapon that can become a module. */
    public static Seq<Definition> all(){
        if(cached != null) return cached;

        Seq<Definition> list = new Seq<>();
        ObjectSet<String> seen = new ObjectSet<>();
        if(content != null){
            for(UnitType type : content.units()){
                if(type == null || type.name == null || type.internal || type.hidden) continue;

                int index = 0;
                for(Weapon weapon : type.weapons){
                    index++;
                    if(!usable(weapon)) continue;
                    //UnitType.init() appends a flipped copy of every mirrored weapon, so a hull
                    //that carries the weapon in both arms lists it twice - once per arm. A
                    //dorsal module is a single mount, and it is the flipped half that could
                    //never fire (see Definition.copy()), so only the authored entry is kept.
                    if(weapon.flipSprite) continue;
                    //the same weapon defined twice on one hull (mods copying a weapon into both
                    //arms, or two entries sharing a name) is still one module
                    if(!seen.add(type.name + "#" + weapon.name)) continue;
                    list.add(create(type, weapon, index));
                }
            }
        }

        cached = list;
        return cached;
    }

    /**
     * Rebuilds the catalog for the current content set.
     * Call it after all mods and content have been created and units have been loaded.
     */
    public static void reload(){
        cached = null;
        all();
    }

    private static boolean usable(Weapon weapon){
        if(weapon == null || weapon.name == null || weapon.name.isEmpty()) return false;
        if(weapon instanceof TurretWeapon) return false;
        //support, mining and build tools are not combat modules
        if(weapon instanceof RepairBeamWeapon || weapon instanceof BuildWeapon ||
            weapon instanceof MineWeapon || weapon instanceof PointDefenseWeapon ||
            weapon instanceof PointDefenseBulletWeapon) return false;
        return weapon.bullet != null;
    }

    private static Definition create(UnitType type, Weapon weapon, int index){
        float regionPx = spritePx(weapon);
        int size = (int)Mathf.clamp(Math.round(regionPx / TurretSlot.pxTile), 1, 5);
        float cost = size * TurretSlot.pxTile;
        return new Definition(type, weapon, size, cost * cost,
            (type.localizedName == null ? type.name : type.localizedName) + " · " + label(weapon));
    }

    private static String label(Weapon weapon){
        if(Core.bundle == null) return weapon.name;
        return Core.bundle.get("weapon." + weapon.name + ".name", weapon.name);
    }

    static float spritePx(Weapon weapon){
        if(weapon.region != null && weapon.region.found()){
            return Math.max(1f, Math.max(weapon.region.width, weapon.region.height));
        }
        return TurretSlot.pxTile;
    }
}
