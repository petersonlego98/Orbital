import arc.Core;
import orbital.content.*;
import orbital.entities.bullet.BulletType;
import orbital.game.*;
import orbital.type.*;
import orbital.type.weapons.*;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static orbital.Vars.content;

/**
 * Regression tests for native unit weapons mounted as dorsal combat modules.
 *
 * <p>UnitType.init() appends a flipped copy of every mirrored weapon, so a hull that carries
 * the weapon on both arms lists it twice. A dorsal module is a single mount: the catalog must
 * offer one entry per weapon, the copy must actually fire, and the sprite must show the pair
 * of arms as one joined drawing.</p>
 */
public class WeaponModuleTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    /** @return how many mirrored authored weapons shipped in the content set. */
    private static int mirroredWeapons(){
        int count = 0;
        for(UnitType type : content.units()){
            if(type == null || type.weapons == null) continue;
            for(Weapon weapon : type.weapons){
                if(weapon.mirror && !weapon.flipSprite) count++;
            }
        }
        return count;
    }

    /** @return how many weapons a hull lists in total, mirrors included. */
    private static int rawWeaponCount(){
        int count = 0;
        for(UnitType type : content.units()){
            if(type == null || type.weapons == null) continue;
            count += type.weapons.size;
        }
        return count;
    }

    /** @return the first catalog entry taken from a mirrored weapon, or null. */
    private static WeaponModules.Definition pairedModule(){
        for(WeaponModules.Definition module : WeaponModules.all()){
            if(module.weapon.mirror) return module;
        }
        return null;
    }

    @Test
    void catalogOffersOneModulePerWeapon(){
        assumeTrue(mirroredWeapons() > 0, "the content set has no mirrored weapon");

        Map<String, Integer> perUnit = new HashMap<>();
        for(WeaponModules.Definition module : WeaponModules.all()){
            assertNotNull(module);
            assertNotNull(module.weapon);
            assertNotNull(module.unit);
            assertNotNull(module.name);

            //the flipped half of every mirrored pair never reaches the catalog: it is both the
            //duplicate the player sees in the dialog and the copy that could not fire
            assertFalse(module.weapon.flipSprite,
                module.unit.name + " lists a mirrored twin of " + module.weapon.name);

            perUnit.merge(module.unit.name + "#" + module.weapon.name, 1, Integer::sum);
        }

        for(Map.Entry<String, Integer> entry : perUnit.entrySet()){
            assertEquals(1, (int)entry.getValue(), entry.getKey() + " is offered more than once");
        }

        //dropping the twins must actually shrink the list
        assertTrue(WeaponModules.all().size < content.units().size + rawWeaponCount(),
            "no duplicate weapon was removed from the catalog");
    }

    @Test
    void mirroredModuleDoublesBackItsRate(){
        WeaponModules.Definition module = pairedModule();
        assumeTrue(module != null, "the content set has no mirrored weapon");

        Weapon source = module.weapon;
        assertTrue(source.reload > 0f);

        //UnitType.init() doubles reload/recoilTime on both entries of an alternating pair so
        //the pair keeps the authored rate; a module is a single mount and must undo that
        Weapon copy = module.copy();
        assertEquals(source.reload / 2f, copy.reload, 0.0001f,
            "a single module must fire at the authored rate, not at the pair's halved rate");
        assertEquals(source.recoilTime / 2f, copy.recoilTime, 0.0001f);
    }

    @Test
    void mirroredModuleNeverAlternates(){
        WeaponModules.Definition module = pairedModule();
        assumeTrue(module != null, "the content set has no mirrored weapon");

        Weapon source = module.weapon;
        assertTrue(source.mirror);
        assertFalse(source.flipSprite, "only the authored side is kept");

        Weapon copy = module.copy();
        //alternate + a copied otherSide/flipSprite gated the shot in Weapon#update (which only
        //fires when wasFlipped == flipSprite) and otherSide indexed the carrier's mounts
        assertFalse(copy.alternate, "a single module must not alternate fire");
        assertEquals(-1, copy.otherSide, "otherSide must not point at the carrier's mounts");
        assertFalse(copy.flipSprite, "the module must draw the authored sprite side");
        assertFalse(copy.mirror, "a mounted module is never mirrored again");
        assertTrue(copy.removable);
        assertSame(module.unit, copy.dorsalSource);
        assertSame(source, copy.dorsalSourceWeapon);
        assertNotNull(copy.bullet);
    }

    @Test
    void pairedModuleDrawsBothArmsJoined(){
        WeaponModules.Definition module = pairedModule();
        assumeTrue(module != null, "the content set has no mirrored weapon");

        //the pairing flag drives the joined drawing, not the mirrored state of the copy
        assertTrue(module.copy().pairedModule(), "a module from a mirrored weapon must draw both arms");
        assertTrue(module.copy().pairedModule());

        //native hull weapons and plain modules stay single sprites
        assertFalse(new Weapon("native-test").pairedModule());

        WeaponModules.Definition plain = null;
        for(WeaponModules.Definition candidate : WeaponModules.all()){
            if(!candidate.weapon.mirror){
                plain = candidate;
                break;
            }
        }
        assumeTrue(plain != null, "the content set has no single-sided weapon");
        assertFalse(plain.copy().pairedModule(), "a non-mirrored weapon must stay a single sprite");
    }

    @Test
    void moduleNamesUseShipNameTierAndShotType(){
        //Ground/naval hulls use the research tiers T1-T5; air hulls use N0-N4 shifted to T1-T5.
        Map<String, String> tiers = Map.ofEntries(
            Map.entry("dagger", "T1"), Map.entry("nova", "T1"), Map.entry("crawler", "T1"),
            Map.entry("risso", "T1"), Map.entry("retusa", "T1"), Map.entry("stell", "T1"),
            Map.entry("merui", "T1"), Map.entry("elude", "T1"),
            Map.entry("mace", "T2"), Map.entry("pulsar", "T2"), Map.entry("atrax", "T2"),
            Map.entry("minke", "T2"), Map.entry("oxynoe", "T2"), Map.entry("locus", "T2"),
            Map.entry("cleroi", "T2"),
            Map.entry("fortress", "T3"), Map.entry("quasar", "T3"), Map.entry("spiroct", "T3"),
            Map.entry("bryde", "T3"), Map.entry("cyerce", "T3"), Map.entry("precept", "T3"),
            Map.entry("anthicus", "T3"),
            Map.entry("scepter", "T4"), Map.entry("vela", "T4"), Map.entry("arkyid", "T4"),
            Map.entry("sei", "T4"), Map.entry("aegires", "T4"), Map.entry("vanquish", "T4"),
            Map.entry("tecta", "T4"),
            Map.entry("reign", "T5"), Map.entry("corvus", "T5"), Map.entry("toxopid", "T5"),
            Map.entry("omura", "T5"), Map.entry("navanax", "T5"), Map.entry("conquer", "T5"),
            Map.entry("collaris", "T5"),
            Map.entry("mono", "T1"), Map.entry("flare", "T1"), Map.entry("poly", "T1"),
            Map.entry("alpha", "T2"), Map.entry("beta", "T2"), Map.entry("gamma", "T2"),
            Map.entry("evoke", "T2"), Map.entry("incite", "T2"), Map.entry("emanate", "T2"),
            Map.entry("horizon", "T3"), Map.entry("avert", "T3"), Map.entry("mega", "T3"),
            Map.entry("zenith", "T3"),
            Map.entry("obviate", "T4"), Map.entry("quell", "T4"), Map.entry("quad", "T4"),
            Map.entry("antumbra", "T5"), Map.entry("disrupt", "T5"),
            Map.entry("eclipse", "T5"), Map.entry("oct", "T5")
        );

        int checked = 0;
        boolean sawT1 = false, sawT5 = false;
        for(WeaponModules.Definition module : WeaponModules.all()){
            String base = module.unit.localizedName == null ? module.unit.name : module.unit.localizedName;
            String suffix = tiers.get(module.unit.name);
            String prefix = suffix == null ? base : base + " " + suffix;

            assertTrue(module.name.startsWith(prefix + " · "),
                module.unit.name + "#" + module.weapon.name + " -> " + module.name);
            String shot = module.name.substring(prefix.length() + 3);
            assertFalse(shot.isEmpty(), "a module must name its shot type");
            assertFalse(shot.contains(" · "), "the shot label must be a single segment");
            assertEquals(module.name, module.copy().moduleDisplayName,
                "an installed module must carry the same display name");
            sawT1 |= "T1".equals(suffix);
            sawT5 |= "T5".equals(suffix);
            checked++;
        }

        assertTrue(checked > 0, "the catalog is empty");
        assertTrue(sawT1 && sawT5, "both ends of the tier table must be exercised");
    }

    @Test
    void shotSuffixDistinguishesWeaponsOfOneHull(){
        Map<String, Map<String, String>> names = new HashMap<>();
        for(WeaponModules.Definition module : WeaponModules.all()){
            names.computeIfAbsent(module.unit.name, k -> new HashMap<>())
                .put(module.weapon.name, module.name);
        }

        boolean multi = false, distinct = false;
        for(Map<String, String> group : names.values()){
            if(group.size() < 2) continue;
            multi = true;
            if(new HashSet<>(group.values()).size() > 1) distinct = true;
        }
        assumeTrue(multi, "no hull offers two weapon modules in this content set");
        assertTrue(distinct, "the shot suffix must give different weapons on one hull different names");
    }

    /**
     * Checks the catalog's classification against an independent, class-name based mapping.
     * Weapon names are mod-prefixed at runtime (e.g. "allure-missiles-mount"), so no
     * assertion here depends on a weapon's internal name.
     */
    @Test
    void shotTypesFollowBulletClasses(){
        int checked = 0;
        boolean missile = false, laser = false, bullet = false;
        for(WeaponModules.Definition module : WeaponModules.all()){
            String key = expectedShotKey(module.weapon.bullet);
            if(key.equals("unknown")) continue; //custom bullet class from a loaded mod

            String fallback = shotDefaults.getOrDefault(key, "Bullet");
            assertEquals(shotLabel(key, fallback), suffix(module.name),
                module.unit.name + "#" + module.weapon.name + " -> " + key);
            missile |= key.equals("missile");
            laser |= key.equals("laser");
            bullet |= key.equals("bullet");
            checked++;
        }

        assertTrue(checked > 0, "the catalog is empty");
        assertTrue(missile && laser && bullet,
            "the catalog must exercise missile, laser and bullet shots");
    }

    private static String suffix(String name){
        int sep = name.lastIndexOf(" · ");
        assertTrue(sep >= 0, "module name has no shot segment: " + name);
        return name.substring(sep + 3);
    }

    /** @return the same localized label the catalog resolves for a shot key. */
    private static String shotLabel(String key, String fallback){
        return Core.bundle == null ? fallback : Core.bundle.get("weapon.module.shot." + key, fallback);
    }

    private static final Map<String, String> shotDefaults = Map.ofEntries(
        Map.entry("bullet", "Bullet"), Map.entry("missile", "Missile"),
        Map.entry("laser", "Laser"), Map.entry("artillery", "Artillery"),
        Map.entry("flak", "Flak"), Map.entry("shrapnel", "Shrapnel"),
        Map.entry("railgun", "Railgun"), Map.entry("lightning", "Lightning"),
        Map.entry("emp", "EMP"), Map.entry("bomb", "Bomb"),
        Map.entry("sap", "Sap"), Map.entry("liquid", "Liquid"),
        Map.entry("flame", "Flame"), Map.entry("plasma", "Plasma"),
        Map.entry("explosion", "Explosion"), Map.entry("interceptor", "Interceptor"),
        Map.entry("continuous", "Continuous")
    );

    /** Independent classification by class name, so the catalog's instanceof chain is checked. */
    private static String expectedShotKey(BulletType bullet){
        if(bullet == null) return "bullet";
        Class<?> type = bullet.getClass();
        while(type.getSimpleName().isEmpty() && type.getSuperclass() != null){
            type = type.getSuperclass();
        }
        String name = type.getSimpleName();

        return switch(name){
            case "MissileBulletType" -> "missile";
            case "BombBulletType" -> "bomb";
            case "ShrapnelBulletType" -> "shrapnel";
            case "EmpBulletType" -> "emp";
            case "FlakBulletType" -> "flak";
            case "ArtilleryBulletType" -> "artillery";
            case "LaserBoltBulletType", "LaserBulletType", "ContinuousLaserBulletType",
                "PointLaserBulletType" -> "laser";
            case "ContinuousFlameBulletType", "FireBulletType" -> "flame";
            case "RailBulletType" -> "railgun";
            case "LightningBulletType" -> "lightning";
            case "SapBulletType" -> "sap";
            case "LiquidBulletType", "SpaceLiquidBulletType" -> "liquid";
            case "ExplosionBulletType" -> "explosion";
            case "InterceptorBulletType" -> "interceptor";
            case "ContinuousBulletType" -> "continuous";
            case "BulletType", "BasicBulletType", "MassDriverBolt", "MultiBulletType",
                "PointBulletType", "EmptyBulletType" -> {
                if(bullet.status == StatusEffects.burning) yield "flame";
                if(bullet.healPercent > 0f) yield "plasma";
                yield "bullet";
            }
            default -> "unknown";
        };
    }

    @Test
    void supportAndBuildToolsAreNotModules(){
        for(WeaponModules.Definition module : WeaponModules.all()){
            Weapon weapon = module.weapon;
            assertFalse(weapon instanceof TurretWeapon,
                module.unit.name + " " + weapon.name + " is a turret, not a unit weapon module");
            assertFalse(weapon instanceof RepairBeamWeapon);
            assertFalse(weapon instanceof BuildWeapon);
            assertFalse(weapon instanceof MineWeapon);
            assertFalse(weapon instanceof PointDefenseWeapon);
            assertFalse(weapon instanceof PointDefenseBulletWeapon);
            assertNotNull(weapon.bullet);
            assertNotNull(weapon.name);
            assertTrue(module.cost > 0f);
            assertTrue(module.size >= 1 && module.size <= 5);
        }
    }
}
