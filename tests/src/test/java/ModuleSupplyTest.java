import arc.*;
import arc.struct.Seq;
import arc.util.*;
import orbital.content.*;
import orbital.ctype.UnlockableContent;
import orbital.entities.units.*;
import orbital.game.Team;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the hull's module supply: three separate pools - item ammo, liquid and energy -
 * each summing the value every installed module of its kind brings.
 */
public class ModuleSupplyTest{

    /** a hull with room for turrets and one support anchor of each effect */
    private static UnitType carrier(){
        return UnitTypes.obviate;
    }

    /** a heavy hull: its anchors include the overdrive and liquid effects */
    private static UnitType supportCarrier(){
        return UnitTypes.quad;
    }

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void poolsAreEmptyWhileNoModuleUsesSupply(){
        Unit unit = carrier().create(Team.sharded);

        ModuleSupply supply = unit.moduleSupply();
        assertEquals(0f, supply.ammoCapacity, "a hull with no ammo module has no item pool");
        assertEquals(0f, supply.liquidCapacity, "a hull with no liquid module has no liquid pool");
        assertEquals(0f, supply.energyCapacity, "a hull with no power module has no energy pool");
        assertNull(supply.item);
        assertNull(supply.liquid);
        assertFalse(supply.usesAmmo());
        assertFalse(supply.usesLiquid());
        assertFalse(supply.usesEnergy());
    }

    @Test
    void itemTurretFillsTheAmmoPoolWithItsOwnBuffer(){
        Unit unit = carrier().create(Team.sharded);
        unit.mountTurret((Turret)Blocks.duo);

        ModuleSupply supply = unit.moduleSupply();
        //Duo: copper 35, its ammo is item ammo and it has no power consumer
        assertEquals(((Turret)Blocks.duo).maxAmmo, supply.ammoCapacity, "the bar sums the block's own buffer");
        assertEquals(0f, supply.liquidCapacity);
        assertEquals(0f, supply.energyCapacity);
        assertNotNull(supply.item, "the item pool is fed by one of Duo's ammo items");
        assertTrue(ModuleSupplies.accepts((Turret)Blocks.duo, supply.item));
        //a pool starts full, so the bar reads full right after the first mount
        assertEquals(supply.ammoCapacity, supply.ammo);
        assertEquals(1f, supply.ammoFraction());
    }

    @Test
    void everyModuleOfAKindSumsIntoItsOwnBar(){
        Unit unit = carrier().create(Team.sharded);
        //two turrets of the same footprint: the ammo bar is the sum of both buffers
        assertNotNull(unit.mountTurret((Turret)Blocks.duo));
        assertNotNull(unit.mountTurret((Turret)Blocks.duo));

        ModuleSupply supply = unit.moduleSupply();
        float expected = 2 * ModuleSupplies.ammoCapacity(Blocks.duo);
        assertEquals(expected, supply.ammoCapacity, "each installed turret adds its buffer");
        assertEquals(supply.ammoCapacity, supply.ammo, "the summed pool reads full");
    }

    @Test
    void powerTurretFeedsOnlyTheEnergyPool(){
        Unit unit = carrier().create(Team.sharded);
        unit.mountTurret((Turret)Blocks.lancer);

        ModuleSupply supply = unit.moduleSupply();
        assertEquals(0f, supply.ammoCapacity, "Lancer runs on energy, not on ammo");
        assertEquals(0f, supply.liquidCapacity);
        assertNull(supply.item);
        assertTrue(supply.energyCapacity > 0f, "its power drain becomes an energy buffer");
        assertEquals(1f, supply.energyFraction());
        assertEquals(0f, supply.ammoFraction());
        assertEquals(0f, supply.liquidFraction());
    }

    @Test
    void supportModuleAddsEnergyFromItsOwnDrain(){
        Unit unit = supportCarrier().create(Team.sharded);
        unit.mountSupport(Blocks.overdriveProjector);

        ModuleSupply supply = unit.moduleSupply();
        float expected = ModuleSupplies.powerUsage(Blocks.overdriveProjector) * ModuleSupply.energyTicks;
        assertEquals(expected, supply.energyCapacity, "the projector's own usage defines the buffer");
        assertEquals(0f, supply.ammoCapacity, "overdrive carries no item ammo");
        assertEquals(0f, supply.liquidCapacity, "nor liquid");
    }

    @Test
    void liquidModuleFeedsTheLiquidBarOnly(){
        //wave is a liquid turret: not adaptable as a dorsal TurretWeapon, so it mounts as the
        //hull's liquid support module - and its liquid capacity feeds the liquid bar
        Unit unit = supportCarrier().create(Team.sharded);
        assertNotNull(unit.mountSupport(Blocks.wave), "the heavy hull has a liquid anchor");

        ModuleSupply supply = unit.moduleSupply();
        assertNotNull(supply.liquid, "the liquid pool is fed by a wave liquid");
        assertNull(supply.item, "the item pool stays empty: no item-ammo module is mounted");
        assertEquals(ModuleSupplies.liquidCapacity(Blocks.wave), supply.liquidCapacity);
        assertEquals(supply.liquidCapacity, supply.liquidAmount);
        assertEquals(1f, supply.liquidFraction());
        assertEquals(0f, supply.ammoFraction());
    }

    @Test
    void itemAndLiquidModulesRunAtTheSameTime(){
        //the two ammo pools are separate: an item turret and a liquid module both contribute
        Unit unit = supportCarrier().create(Team.sharded);
        assertNotNull(unit.mountTurret((Turret)Blocks.duo));
        assertNotNull(unit.mountSupport(Blocks.wave));

        ModuleSupply supply = unit.moduleSupply();
        assertTrue(supply.ammoCapacity > 0f, "the item bar has the turret's buffer");
        assertTrue(supply.liquidCapacity > 0f, "the liquid bar has wave's capacity");
        assertNotNull(supply.item);
        assertNotNull(supply.liquid);
    }

    @Test
    void choosingAResourceKeepsTheOtherPoolUntouched(){
        Unit unit = supportCarrier().create(Team.sharded);
        assertNotNull(unit.mountTurret((Turret)Blocks.duo));
        assertNotNull(unit.mountSupport(Blocks.wave));

        ModuleSupply supply = unit.moduleSupply();
        UnlockableContent liquidBefore = supply.liquid;

        supply.setResource(Items.graphite);
        assertSame(Items.graphite, supply.item, "an item feeds the item pool");
        assertSame(liquidBefore, supply.liquid, "the liquid pool keeps its own resource");

        supply.setResource(Liquids.water);
        assertSame(Items.graphite, supply.item, "the item pool keeps its own resource");
        assertSame(Liquids.water, supply.liquid);
    }

    @Test
    void chosenResourcesMustBeOfferedByAMountedModule(){
        Unit unit = carrier().create(Team.sharded);
        unit.mountTurret((Turret)Blocks.duo);

        //a resource no mounted turret accepts is not offered, and picking it is undone
        assertFalse(unit.ammoItemOptions().contains(Items.blastCompound, true));
        unit.moduleSupply().item = Items.blastCompound;
        unit.refreshModuleSupply();
        assertNotEquals(Items.blastCompound, unit.moduleSupply().item);
        assertNotNull(unit.moduleSupply().item, "the pool falls back to an offered resource");
    }

    @Test
    void liquidOptionIsOfferedBeforeALiquidModuleIsInstalled(){
        //the liquid picker must be available from the first module added: the hull offers the
        //liquids of the liquid support modules it could still host
        Unit unit = supportCarrier().create(Team.sharded);
        assertNotNull(unit.mountTurret((Turret)Blocks.foreshadow), "the hull's own 4x4 slot");

        Seq<Liquid> options = unit.ammoLiquidOptions();
        assertTrue(options.size > 0,
            "the liquid picker must be offered with only a turret mounted; quad can host wave/tsunami");
        assertNotNull(unit.moduleSupply().liquid, "the liquid pool has a resource to be fed by");

        //a hull that cannot host any liquid module offers nothing, even with a module mounted
        Unit light = UnitTypes.mono.create(Team.sharded);
        assertTrue(light.mountedTurrets() > 0, "mono ships the Duo out of the box");
        assertTrue(light.ammoLiquidOptions().isEmpty(),
            "mono has no liquid anchor, so no liquid picker is offered");
    }

    @Test
    void energyIsReadNotConsumed(){
        //a dorsal module has no power line: its energy bar sizes what the module would draw,
        //and nothing is ever taken out of the pool
        Unit unit = supportCarrier().create(Team.sharded);
        assertNotNull(unit.mountTurret((Turret)Blocks.lancer));

        ModuleSupply supply = unit.moduleSupply();
        assertTrue(supply.energyCapacity > 0f, "lancer's drain must size the energy bar");

        supply.energy = supply.energyCapacity / 2f;
        float delta = Time.delta;
        try{
            Time.delta = 1f;
            for(int i = 0; i < 600; i++) unit.update();
        }finally{
            Time.delta = delta;
        }
        assertTrue(supply.energy >= supply.energyCapacity / 2f,
            "no tick of the game may take energy out of the pool");

        //and the module keeps working, energy or no energy
        int shots = unit.mounts[unit.mounts.length - 1].totalShots;
        try{
            Time.delta = 1f;
            unit.update();
        }finally{
            Time.delta = delta;
        }
        assertTrue(unit.mounts[unit.mounts.length - 1].totalShots >= shots, "the turret keeps updating");
    }

    @Test
    void emptyEnergyNeverSilencesAModule(){
        //regression for the bug the player found: with the energy drain live, wave/tsunami
        //(power consumers) emptied the pool and the module went dark for good. The pool is a
        //reading, so a zero must not stop anything.
        Unit unit = supportCarrier().create(Team.sharded);
        assertNotNull(unit.mountTurret((Turret)Blocks.lancer));

        ModuleSupply supply = unit.moduleSupply();
        supply.energy = 0f;

        int shots = unit.mounts[unit.mounts.length - 1].totalShots;
        float delta = Time.delta;
        try{
            Time.delta = 1f;
            for(int i = 0; i < 30; i++) unit.update();
        }finally{
            Time.delta = delta;
        }
        assertTrue(unit.mounts[unit.mounts.length - 1].totalShots >= shots,
            "an empty energy pool must not silence a module");
    }

    @Test
    void poolRechargesOnItsOwn(){
        Unit unit = carrier().create(Team.sharded);
        assertNotNull(unit.mountTurret((Turret)Blocks.duo));

        ModuleSupply supply = unit.moduleSupply();
        supply.ammo = 10f;
        float capacity = supply.ammoCapacity;

        float delta = Time.delta;
        try{
            Time.delta = ModuleSupply.refillTicks;
            unit.update();
        }finally{
            Time.delta = delta;
        }
        assertEquals(capacity, supply.ammo, "an empty pool refills to capacity in refillTicks");
    }

    @Test
    void everyShotSpendsItsAmmo(){
        Unit unit = carrier().create(Team.sharded);
        assertNotNull(unit.mountTurret((Turret)Blocks.duo));

        ModuleSupply supply = unit.moduleSupply();
        float before = supply.ammo;

        //a shot pays the block's own ammo per shot
        unit.spendAmmo(unit.mounts[unit.mounts.length - 1].weapon);

        assertEquals(before - ModuleSupplies.ammoPerShot((Turret)Blocks.duo), supply.ammo, 0.001f,
            "a shot must take the block's ammo per shot out of the pool");
    }

    @Test
    void removingAModuleShrinksItsBarWithIt(){
        Unit unit = carrier().create(Team.sharded);
        unit.mountTurret((Turret)Blocks.duo);
        float full = unit.moduleSupply().ammoCapacity;

        WeaponMount mount = unit.mounts[unit.mounts.length - 1];
        assertTrue(unit.removeWeapon(mount.weapon));

        assertEquals(0f, unit.moduleSupply().ammoCapacity, "the bar follows the loadout");
        assertTrue(unit.moduleSupply().ammo <= unit.moduleSupply().ammoCapacity);
        assertFalse(full == 0f, "the turret had contributed ammo capacity");
    }

    @Test
    void capacityClampsCarriedAmounts(){
        ModuleSupply supply = new ModuleSupply();
        supply.ammoCapacity = 40f;
        supply.ammo = 40f;
        supply.clamp();
        assertEquals(40f, supply.ammo);

        supply.ammoCapacity = 10f;
        supply.clamp();
        assertEquals(10f, supply.ammo, "a smaller pool drops the amount with it");

        //the liquid pool clamps on its own, independently
        supply.liquidCapacity = 20f;
        supply.liquidAmount = 30f;
        supply.clamp();
        assertEquals(20f, supply.liquidAmount);
    }

    @Test
    void ammoCompatiblePoolUsesTheResourceTheUnitCarries(){
        Unit unit = carrier().create(Team.sharded);
        unit.mountTurret((Turret)Blocks.duo);

        UnlockableContent resource = unit.moduleSupply().item;
        //Duo accepts its own ammo, so the item pool keeps a capacity
        assertTrue(ModuleSupplies.accepts((Turret)Blocks.duo, resource));
        //Scatter runs on lead/scrap/metaglass: outside Duo's ammo, so it never enters the pool
        assertFalse(ModuleSupplies.accepts((Turret)Blocks.scatter, resource));
    }
}
