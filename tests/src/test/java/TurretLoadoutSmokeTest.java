import orbital.content.*;
import orbital.type.*;
import orbital.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

public class TurretLoadoutSmokeTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void monoHasOneSmallDorsalSlot(){
        assertEquals(1, UnitTypes.mono.turretSlots.size);
        assertEquals(1152f, UnitTypes.mono.dorsalBudget(), 0.1f);
        //the only slot starts occupied by the removable Duo
        assertFalse(UnitTypes.mono.canMount((Turret)Blocks.duo));
        assertFalse(UnitTypes.mono.canMount((Turret)Blocks.scatter));
    }

    @Test
    void monoAndFlareStartWithRemovableDuo(){
        assertTrue(hasRemovableDuo(UnitTypes.mono));
        assertTrue(hasRemovableDuo(UnitTypes.flare));
        assertEquals(1, UnitTypes.mono.mountedTurrets());
        assertEquals(1, UnitTypes.flare.mountedTurrets());
        assertTrue(UnitTypes.mono.loadoutCost() > 0f);
        assertTrue(UnitTypes.flare.loadoutCost() > 0f);
    }

    private static boolean hasRemovableDuo(UnitType type){
        return type.weapons.contains(weapon -> weapon instanceof TurretWeapon turret &&
            turret.turret == Blocks.duo && turret.removable);
    }

    @Test
    void demoVariantHasRemovableDuo(){
        assertEquals(1, UnitTypes.monoDuo.weapons.size);
        assertTrue(UnitTypes.monoDuo.weapons.first() instanceof TurretWeapon);

        TurretWeapon turret = (TurretWeapon)UnitTypes.monoDuo.weapons.first();
        assertTrue(turret.removable);
        assertEquals(1024f, turret.cost(), 0.1f);
        assertEquals(1024f, UnitTypes.monoDuo.loadoutCost(), 0.1f);

        //the only slot is already occupied
        assertFalse(UnitTypes.monoDuo.canMount((Turret)Blocks.duo));
    }

    @Test
    void flareDemoVariantHasRemovableDuo(){
        assertEquals(2, UnitTypes.flareDuo.weapons.size);
        assertTrue(UnitTypes.flareDuo.weapons.contains(weapon -> !(weapon instanceof TurretWeapon)));

        TurretWeapon turret = UnitTypes.flareDuo.weapons.find(weapon -> weapon instanceof TurretWeapon) instanceof TurretWeapon t ? t : null;
        assertNotNull(turret);
        assertTrue(turret.removable);
        assertEquals(1024f, turret.cost(), 0.1f);
        assertEquals(1, UnitTypes.flareDuo.mountedTurrets());
    }

    @Test
    void nativeWeaponsAreNotRemovable(){
        assertTrue(UnitTypes.flare.weapons.any());
        assertTrue(UnitTypes.flare.weapons.contains(weapon -> !(weapon instanceof TurretWeapon)));
        //Flare now ships with a removable Duo alongside its native weapon
        assertEquals(1, UnitTypes.flare.mountedTurrets());
        assertTrue(UnitTypes.flare.weapons.contains(weapon -> weapon instanceof TurretWeapon turret && turret.removable));
    }

    @Test
    void capitalBudgetEnforcesHierarchy(){
        assertTrue(UnitTypes.eclipse.canMount((Turret)Blocks.smite));
        assertFalse(UnitTypes.antumbra.canMount((Turret)Blocks.smite));
        assertTrue(UnitTypes.antumbra.canMount((Turret)Blocks.spectre));
    }

    @Test
    void allAirUnitsHaveABudget(){
        UnitType[] air = {
            UnitTypes.mono, UnitTypes.flare, UnitTypes.poly, UnitTypes.mega, UnitTypes.horizon,
            UnitTypes.zenith, UnitTypes.antumbra, UnitTypes.eclipse, UnitTypes.quad, UnitTypes.oct,
            UnitTypes.avert, UnitTypes.obviate, UnitTypes.quell, UnitTypes.disrupt, UnitTypes.alpha,
            UnitTypes.beta, UnitTypes.gamma, UnitTypes.evoke, UnitTypes.incite, UnitTypes.emanate
        };

        for(UnitType type : air){
            assertTrue(type.dorsalBudget() > 0f, type.name + " has no dorsal budget");
            assertFalse(type.turretSlots.isEmpty(), type.name + " has no dorsal slots");
        }
    }
}
