import arc.*;
import arc.struct.*;
import arc.util.*;
import java.util.Arrays;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the dorsal ammo rule: one dorsal loadout shares a single item supply, so a
 * turret may only be mounted when every item it accepts is already on the unit.
 *
 * <p>Covers the chosen behaviour - free choice while nothing defines the ammo, subset
 * compatibility afterwards, power turrets never competing for items, and the lock being released
 * again when the last item-ammo turret is removed.</p>
 */
public class TurretAmmoCompatibilityTest{

    /**
     * A hull with two 2x2 slots: big enough for the 1x1 and 2x2 turrets the rule is checked
     * against, so a refused mount can only be about ammo and never about footprint.
     */
    private static UnitType carrier(){
        return UnitTypes.obviate;
    }

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void noTurretMountedMeansAnyAmmoIsAllowed(){
        Unit unit = carrier().create(Team.sharded);
        assertTrue(unit.loadoutAmmo().length == 0, "a fresh unit carries no ammo");

        //both an item turret and a power turret are legal first picks
        assertTrue(unit.ammoCompatible((Turret)Blocks.duo));
        assertTrue(unit.ammoCompatible((Turret)Blocks.scatter));
        assertTrue(unit.ammoCompatible((Turret)Blocks.lancer));
    }

    @Test
    void mountedTurretDefinesTheUnitAmmo(){
        Unit unit = carrier().create(Team.sharded);

        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "the first turret must still fit");

        Item[] ammo = unit.loadoutAmmo();
        assertEquals(3, ammo.length, "Duo takes copper, graphite and silicon");
        assertTrue(Arrays.asList(ammo).contains(Items.copper));
        assertTrue(Arrays.asList(ammo).contains(Items.graphite));
        assertTrue(Arrays.asList(ammo).contains(Items.silicon));
        assertFalse(Arrays.asList(ammo).contains(Items.lead), "lead is not Duo ammo");
    }

    @Test
    void onlyCompatibleTurretsMayBeAdded(){
        Unit unit = carrier().create(Team.sharded);
        unit.mountTurret((Turret)Blocks.duo);

        //same ammo: accepted, both as a check and as a real mount
        assertTrue(unit.ammoCompatible((Turret)Blocks.duo));
        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "a second Duo shares the ammo");

        //foreign ammo: refused by both the catalogue rule and the real mount path
        assertFalse(unit.ammoCompatible((Turret)Blocks.scatter), "Scatter runs on scrap/lead/metaglass");
        assertFalse(unit.canMount((Turret)Blocks.scatter));
        assertFalse(unit.canMountAt((Turret)Blocks.scatter, -1));
        assertNull(unit.mountTurret((Turret)Blocks.scatter));

        //power turrets draw no items, so they never break the rule
        assertTrue(unit.ammoCompatible((Turret)Blocks.lancer));
    }

    @Test
    void removingTheLastItemTurretFreesTheAmmoAgain(){
        Unit unit = carrier().create(Team.sharded);
        TurretWeapon mounted = unit.mountTurret((Turret)Blocks.duo);
        assertNotNull(mounted);
        assertFalse(unit.ammoCompatible((Turret)Blocks.scatter));

        //mounts[0] is the hull's native gun: the removable turret is the one just mounted
        assertTrue(unit.removeWeapon(mounted), "the mounted turret must be removable");
        assertEquals(0, unit.loadoutAmmo().length, "no turret left, so no ammo is defined");
        assertTrue(unit.ammoCompatible((Turret)Blocks.scatter));
        assertNotNull(unit.mountTurret((Turret)Blocks.scatter), "the ammo rule must not block the first turret again");
    }

    @Test
    void ammoItemsOfATurretMatchItsBlockDefinition(){
        Seq<Item> duo = TurretWeapon.ammoItems((Turret)Blocks.duo);
        assertEquals(((ItemTurret)Blocks.duo).ammoTypes.size, duo.size);
        assertTrue(duo.contains(Items.copper, true));

        //power and non-item turrets accept no item ammo at all
        assertTrue(TurretWeapon.ammoItems((Turret)Blocks.lancer).isEmpty());
        assertTrue(TurretWeapon.ammoItems(null).isEmpty());
    }

    @Test
    void nativeTurretAmmoAlsoCounts(){
        //mono ships the Duo as a content-defined hardpoint, which is what defines its ammo
        Unit unit = UnitTypes.mono.create(Team.sharded);
        assertTrue(unit.loadoutAmmo().length > 0, "the native Duo must define the hull ammo");

        assertTrue(unit.ammoCompatible((Turret)Blocks.duo));
        assertFalse(unit.ammoCompatible((Turret)Blocks.scatter), "a hull hardpoint locks the supply too");
    }
}
