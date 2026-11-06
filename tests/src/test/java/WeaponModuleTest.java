import orbital.content.*;
import orbital.game.*;
import orbital.type.*;
import orbital.type.weapons.*;
import org.junit.jupiter.api.*;

import java.util.HashMap;
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
