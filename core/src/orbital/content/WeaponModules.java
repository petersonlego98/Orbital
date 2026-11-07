package orbital.content;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import orbital.entities.bullet.*;
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
        /** display name: ship name, tier suffix and shot type, e.g. "Risso T1 · Missile". */
        public final String name;
        /** hull name with its tier suffix, e.g. {@code "Risso T1"}; plain hull name without a tier. */
        public final String ship;
        /** localized shot type of the weapon, e.g. {@code "Missile"}. */
        public final String shot;
        /** research tier of the hull, {@code 1-5}; {@code 0} when the hull has none. */
        public final int tier;

        Definition(UnitType type, Weapon weapon, int size, float cost, String name, String ship, String shot, int tier){
            this.unit = type;
            this.weapon = weapon;
            this.size = size;
            this.cost = cost;
            this.name = name;
            this.ship = ship;
            this.shot = shot;
            this.tier = tier;
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
            copy.moduleDisplayName = name;
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

    /**
     * Tier suffix for the hulls whose weapons become modules.
     *
     * <p>Ground and naval hulls use the T1-T5 research tiers documented in
     * {@code docs/RELATORIO_ARMAS_TERRESTRES_NAVAIS.md}. Air hulls use the N0-N4 hull
     * levels of {@code docs/RELATORIO_TORRES_COMO_MODULOS.md}, shifted to T1-T5.
     * Internal, hidden and special hulls are absent from both tables and get no suffix.</p>
     */
    private static final ObjectMap<String, Integer> unitTiers = new ObjectMap<>();

    static{
        //Serpulo/Erekir research tiers: mecha, walker, naval and tank lines.
        setTier(1, "dagger", "nova", "crawler", "risso", "retusa", "stell", "merui", "elude");
        setTier(2, "mace", "pulsar", "atrax", "minke", "oxynoe", "locus", "cleroi");
        setTier(3, "fortress", "quasar", "spiroct", "bryde", "cyerce", "precept", "anthicus");
        setTier(4, "scepter", "vela", "arkyid", "sei", "aegires", "vanquish", "tecta");
        setTier(5, "reign", "corvus", "toxopid", "omura", "navanax", "conquer", "collaris");

        //Air hull levels: N0 -> T1 ... N4 -> T5.
        setTier(1, "mono", "flare", "poly");
        setTier(2, "alpha", "beta", "gamma", "evoke", "incite", "emanate");
        setTier(3, "horizon", "avert", "mega", "zenith");
        setTier(4, "obviate", "quell", "quad");
        setTier(5, "antumbra", "disrupt", "eclipse", "oct");
    }

    private static void setTier(int tier, String... names){
        for(String name : names) unitTiers.put(name, tier);
    }

    private WeaponModules(){}

    /**
     * @return the research tier of a hull, {@code 1-5}; {@code 0} when it has none.
     * Used to order the module grids in the loadout dialog.
     */
    public static int tierOf(UnitType type){
        return type == null || type.name == null ? 0 : unitTiers.get(type.name, 0);
    }

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
        return list;
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
        int tier = tierOf(type);
        String hull = type.localizedName == null ? type.name : type.localizedName;
        String ship = tier == 0 ? hull : hull + " T" + tier;
        String shot = shotLabel(weapon);
        return new Definition(type, weapon, size, cost * cost, ship + " · " + shot, ship, shot, tier);
    }

    /**
     * Classifies a weapon by its bullet type. Subclasses of {@link BasicBulletType} are
     * checked before the generic case, so a missile or an artillery shell is never reduced
     * to "bullet". Generic bullets have no distinguishing class; burning and healing flags
     * separate flame shots and healing plasma from ordinary rounds.
     */
    private static String shotKey(Weapon weapon){
        BulletType bullet = weapon.bullet;
        if(bullet == null) return "bullet";

        if(bullet instanceof MissileBulletType) return "missile";
        if(bullet instanceof BombBulletType) return "bomb";
        if(bullet instanceof ShrapnelBulletType) return "shrapnel";
        if(bullet instanceof EmpBulletType) return "emp";
        if(bullet instanceof FlakBulletType) return "flak";
        if(bullet instanceof ArtilleryBulletType) return "artillery";
        if(bullet instanceof LaserBoltBulletType) return "laser";
        if(bullet instanceof ContinuousLaserBulletType || bullet instanceof LaserBulletType ||
            bullet instanceof PointLaserBulletType) return "laser";
        if(bullet instanceof ContinuousFlameBulletType || bullet instanceof FireBulletType) return "flame";
        if(bullet instanceof RailBulletType) return "railgun";
        if(bullet instanceof LightningBulletType) return "lightning";
        if(bullet instanceof SapBulletType) return "sap";
        if(bullet instanceof LiquidBulletType || bullet instanceof SpaceLiquidBulletType) return "liquid";
        if(bullet instanceof ExplosionBulletType) return "explosion";
        if(bullet instanceof InterceptorBulletType) return "interceptor";
        if(bullet instanceof ContinuousBulletType) return "continuous";
        if(bullet.status == StatusEffects.burning) return "flame";
        if(bullet.healPercent > 0f) return "plasma";
        return "bullet";
    }

    /** English fallbacks, used when the bundle has no key for the shot type. */
    private static String shotFallback(String key){
        return switch(key){
            case "missile" -> "Missile";
            case "laser" -> "Laser";
            case "artillery" -> "Artillery";
            case "flak" -> "Flak";
            case "shrapnel" -> "Shrapnel";
            case "railgun" -> "Railgun";
            case "lightning" -> "Lightning";
            case "emp" -> "EMP";
            case "bomb" -> "Bomb";
            case "sap" -> "Sap";
            case "liquid" -> "Liquid";
            case "flame" -> "Flame";
            case "plasma" -> "Plasma";
            case "explosion" -> "Explosion";
            case "interceptor" -> "Interceptor";
            case "continuous" -> "Continuous";
            default -> "Bullet";
        };
    }

    /** @return the localized shot type of any weapon, including native hull weapons. */
    public static String shotLabel(Weapon weapon){
        String key = shotKey(weapon);
        String fallback = shotFallback(key);
        return Core.bundle == null ? fallback : Core.bundle.get("weapon.module.shot." + key, fallback);
    }

    static float spritePx(Weapon weapon){
        if(weapon.region != null && weapon.region.found()){
            return Math.max(1f, Math.max(weapon.region.width, weapon.region.height));
        }
        return TurretSlot.pxTile;
    }
}
