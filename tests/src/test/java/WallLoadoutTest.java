import orbital.content.*;
import orbital.entities.abilities.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.ui.dialogs.TurretLoadoutDialog;
import orbital.world.Block;
import orbital.world.blocks.defense.*;
import orbital.world.blocks.defense.ShieldWall;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static orbital.Vars.*;

/**
 * Regression tests for walls and ramparts as dorsal armour modules.
 *
 * <p>Covers the placement rules chosen for this feature (one single armour category, plates share
 * the turret slots and the dorsal budget, many plates per hull, identical attributes do not stack
 * but hit points add up) and the effects every derived ability drives.</p>
 */
public class WallLoadoutTest{

    /**
     * Resolved lazily: {@code Blocks.*} is populated by launchApplication(), so a static
     * field initialized at class-load time would still hold null references.
     */
    private static Block[] walls(){
        return new Block[]{
            Blocks.copperWall, Blocks.copperWallLarge,
            Blocks.titaniumWall, Blocks.titaniumWallLarge,
            Blocks.plastaniumWall, Blocks.plastaniumWallLarge,
            Blocks.thoriumWall, Blocks.thoriumWallLarge,
            Blocks.phaseWall, Blocks.phaseWallLarge,
            Blocks.surgeWall, Blocks.surgeWallLarge,
            Blocks.scrapWall, Blocks.scrapWallLarge, Blocks.scrapWallHuge, Blocks.scrapWallGigantic,
            Blocks.berylliumWall, Blocks.berylliumWallLarge,
            Blocks.tungstenWall, Blocks.tungstenWallLarge,
            Blocks.carbideWall, Blocks.carbideWallLarge,
            Blocks.reinforcedSurgeWall, Blocks.reinforcedSurgeWallLarge,
            Blocks.shieldedWall
        };
    }

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    //region classification

    @Test
    void everyWallIsTheSingleArmourCategory(){
        for(Block block : walls()){
            assertEquals(SupportModuleCategory.armor, SupportModuleCategory.of(block), block.name);
            assertTrue(SupportModuleCategory.isWallModule(block), block.name);
        }
    }

    @Test
    void doorsAndSandboxToolsAreNotPlates(){
        //doors are passable walls, the thruster is a sandbox tool: neither is an armour plate
        for(Block block : new Block[]{Blocks.door, Blocks.doorLarge, Blocks.blastDoor, Blocks.thruster}){
            assertNull(SupportModuleCategory.of(block), block.name);
            assertFalse(SupportModuleCategory.isWallModule(block), block.name);
        }
    }

    @Test
    void wallsAreExposedOnlyAsModules(){
        for(Block block : walls()){
            assertTrue(block.isHidden(), block.name + " must be hidden from construction");
            assertTrue(block.moduleOnly, block.name + " must stay available as a module");
            assertFalse(block.inEditor, block.name + " must not be editable in the editor");
        }
    }

    @Test
    void theArmourCatalogueKeepsTheHiddenPlates(){
        //the same exception the turret catalogue uses: moduleOnly blocks are hidden from
        //construction but still listed for loadouts
        for(Block block : walls()){
            assertTrue(TurretLoadoutDialog.wallCatalogEligible(block, UnitTypes.quad), block.name);
        }
        //support projectors and doors stay out of the wall catalogue
        assertFalse(TurretLoadoutDialog.wallCatalogEligible(Blocks.mender, UnitTypes.quad));
        assertFalse(TurretLoadoutDialog.wallCatalogEligible(Blocks.blastDoor, UnitTypes.quad));
        //and walls are never offered as support modules
        assertFalse(TurretLoadoutDialog.supportCatalogEligible(Blocks.copperWall, UnitTypes.quad));
    }

    //endregion

    //region placement rules

    @Test
    void aPlateCannotMountOnASupportAnchor(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertFalse(unit.canMountSupport(Blocks.copperWall));
        assertNull(unit.mountSupport(Blocks.copperWall));
        assertEquals(0, unit.supportMounts.length);
    }

    @Test
    void platesTakeTurretSlotsAndBudget(){
        UnitType type = UnitTypes.quad;
        Unit unit = type.create(Team.sharded);
        float cost = unit.loadoutCost();

        WallMount plate = unit.mountWall(Blocks.tungstenWall);
        assertNotNull(plate, "a 1x1 plate must fit an empty dorsal slot");

        //the plate is not a support module and not a turret: it has its own counters
        assertEquals(0, unit.supportMounts.length);
        assertEquals(0, unit.mountedTurrets());
        assertEquals(1, unit.wallMounts.length);

        //but it pays for its area out of the same dorsal budget
        float side = Blocks.tungstenWall.size * TurretSlot.pxTile;
        assertEquals(cost + side * side, unit.loadoutCost() + unit.wallLoadoutCost(), 0.001f);
    }

    @Test
    void manyPlatesStackOnOneHull(){
        UnitType type = UnitTypes.quad;
        Unit unit = type.create(Team.sharded);
        int slots = type.turretSlots.size;

        int mounted = 0;
        for(int slot = 0; slot < slots; slot++){
            assertNotNull(unit.mountWall(Blocks.copperWall), "slot " + slot);
            mounted++;
        }
        assertEquals(slots, unit.wallMounts.length);
        assertEquals(mounted, unit.mountedTurrets() + unit.wallMounts.length);

        //the rule is space, not category exclusivity
        assertFalse(unit.canMountWall(Blocks.copperWall), "no dorsal slot left");
        assertNull(unit.mountWall(Blocks.copperWall));
        assertEquals(slots, unit.wallMounts.length);
    }

    @Test
    void aTLargerThanTheSlotCannotMount(){
        //poly carries a single 1x1 dorsal slot: a 3x3 scrap wall is rejected outright
        assertEquals(1, UnitTypes.poly.turretSlots.size);
        Unit unit = UnitTypes.poly.create(Team.sharded);
        assertFalse(unit.canMountWall(Blocks.scrapWallHuge));
        assertNull(unit.mountWall(Blocks.scrapWallHuge));
        assertEquals(0, unit.wallMounts.length);

        //while a 1x1 plate fits
        assertTrue(unit.canMountWall(Blocks.copperWall));
        assertNotNull(unit.mountWall(Blocks.copperWall));
    }

    @Test
    void removingAPlateFreesItsSlot(){
        UnitType type = UnitTypes.quad;
        Unit unit = type.create(Team.sharded);

        //fill every dorsal slot with plates
        for(int i = 0; i < type.turretSlots.size; i++){
            assertNotNull(unit.mountWall(Blocks.copperWall), "slot " + i);
        }
        assertFalse(unit.canMountWall(Blocks.carbideWall), "no dorsal slot left");

        //removing one plate frees exactly one slot
        WallMount plate = unit.wallMounts[0];
        assertTrue(unit.removeWallMount(plate));
        assertEquals(type.turretSlots.size - 1, unit.wallMounts.length);
        assertTrue(unit.canMountWall(Blocks.carbideWall), "the slot is free again");

        //and the plate can be removed by block name too
        assertNotNull(unit.mountWall(Blocks.carbideWall));
        assertTrue(unit.removeWall(Blocks.carbideWall));
        assertFalse(unit.wallMounts.length > 0 && unit.wallMounts[0].block == Blocks.carbideWall);
    }

    @Test
    void platesAndTurretsCompeteForTheSameSlot(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WallMount plate = unit.mountWall(Blocks.copperWall);
        assertNotNull(plate);

        //a turret can no longer use that slot: the loadout says it is already full
        assertNull(unit.mountWallAt(Blocks.copperWall, plate.slotIndex), "the plate's slot is taken");
        assertTrue(unit.canMountWall(Blocks.copperWall) == unit.mountedTurrets() + unit.wallMounts.length < unit.type.turretSlots.size);
    }

    //endregion

    //region gameplay effects

    @Test
    void plateHitPointsAddToTheHullPool(){
        UnitType type = UnitTypes.quad;
        Unit unit = type.create(Team.sharded);
        float base = unit.maxHealth;

        assertNotNull(unit.mountWall(Blocks.copperWall));
        WallMount plate = unit.wallMounts[0];
        //one frame is enough: the effect recomputes the pool from the mounted plates
        plate.update(unit);
        assertEquals(base + Blocks.copperWall.health, unit.maxHealth, 0.01f);

        //a second plate adds its own hit points to the same pool
        assertNotNull(unit.mountWall(Blocks.tungstenWall));
        unit.wallMounts[0].update(unit);
        assertEquals(base + Blocks.copperWall.health + Blocks.tungstenWall.health, unit.maxHealth, 0.01f);
        assertEquals(Blocks.copperWall.health + Blocks.tungstenWall.health,
            WallModuleAbility.carriedHealth(unit), 0.01f);
    }

    @Test
    void identicalAttributesDoNotStack(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertNotNull(unit.mountWall(Blocks.tungstenWall));
        assertNotNull(unit.mountWall(Blocks.carbideWall));

        WallMount plate = unit.wallMounts[0];
        plate.update(unit);

        //armour keeps the strongest plate, it is never the sum
        assertEquals(Blocks.carbideWall.armor, unit.armor, 0.001f);
        assertNotEquals(Blocks.tungstenWall.armor + Blocks.carbideWall.armor, unit.armor, 0.001f);
    }

    @Test
    void aPlateSetsTheHullArmour(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertNotNull(unit.mountWall(Blocks.tungstenWall));

        WallMount plate = unit.wallMounts[0];
        plate.update(unit);
        assertEquals(Blocks.tungstenWall.armor, unit.armor, 0.001f);
        assertTrue(unit.armor > unit.type.armor);

        //removing the armoured plate restores the hull's own armour
        assertTrue(unit.removeWallMount(plate));
        assertNotNull(unit.mountWall(Blocks.scrapWall));
        unit.wallMounts[0].update(unit);
        assertEquals(unit.type.armor, unit.armor, 0.001f);
    }

    @Test
    void theShieldedPlateStartsWithItsOwnShieldPool(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WallMount plate = unit.mountWall(Blocks.shieldedWall);
        assertNotNull(plate);

        //the wall's own shield health and regen/break values travel with the module
        ShieldWall wall = (ShieldWall)Blocks.shieldedWall;
        assertEquals(wall.shieldHealth, plate.ability.shieldHealth, 0.001f);
        assertEquals(wall.breakCooldown, plate.ability.shieldCooldown, 0.001f);
        assertEquals(wall.regenSpeed, plate.ability.shieldRegen, 0.001f);

        plate.update(unit);
        assertEquals(plate.ability.shieldHealth, unit.shield, 0.001f);
    }

    @Test
    void aDeflectPlateAggregatesItsChance(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WallMount phase = unit.mountWall(Blocks.phaseWall);
        assertNotNull(phase);
        assertEquals(10f, phase.ability.chanceDeflect, 0.001f);

        //the shield plate also deflects: both plates together keep the largest chance
        WallMount shielded = unit.mountWall(Blocks.shieldedWall);
        assertNotNull(shielded);
        assertEquals(8f, shielded.ability.chanceDeflect, 0.001f);
        assertEquals(10f,
            Math.max(unit.wallMounts[0].ability.chanceDeflect, unit.wallMounts[1].ability.chanceDeflect), 0.001f);
    }

    @Test
    void lightningRetaliationKeepsTheWallDamage(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WallMount reinforced = unit.mountWall(Blocks.reinforcedSurgeWall);
        assertNotNull(reinforced);

        assertEquals(0.05f, reinforced.ability.lightningChance, 0.0001f);
        assertEquals(30f, reinforced.ability.lightningDamage, 0.001f);
        assertEquals(17, reinforced.ability.lightningLength);
        //a surge wall without an explicit damage value falls back to the wall default
        WallMount plain = unit.mountWall(Blocks.surgeWall);
        assertEquals(20f, plain.ability.lightningDamage, 0.001f);
    }

    @Test
    void aPlateCanBeSwitchedOffWithoutBeingUnmounted(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WallMount plate = unit.mountWall(Blocks.tungstenWall);
        assertNotNull(plate);
        assertTrue(plate.active);

        plate.setActive(unit, false);
        assertFalse(plate.active, "the plate stays mounted");
        assertEquals(1, unit.wallMounts.length, "the slot is still reserved");

        plate.setActive(unit, true);
        assertTrue(plate.active);
    }

    @Test
    void mountedPlatesNeverOverlapEachOther(){
        UnitType type = UnitTypes.quad;
        Unit unit = type.create(Team.sharded);
        for(int i = 0; i < type.turretSlots.size; i++){
            unit.mountWall(Blocks.copperWall);
        }

        for(int i = 0; i < unit.wallMounts.length; i++){
            for(int j = i + 1; j < unit.wallMounts.length; j++){
                WallMount a = unit.wallMounts[i], b = unit.wallMounts[j];
                assertFalse(DorsalGeometry.overlaps(a.x, a.y, a.visualWidth(), b.x, b.y, b.visualWidth()),
                    type.name + " plates on slots " + a.slotIndex + " and " + b.slotIndex + " overlap");
            }
        }
    }

    //endregion

    //region every hull

    @Test
    void everyHullTakesAPlateOnlyWhenAHullSlotIsFree(){
        UnitType[] air = {
            UnitTypes.mono, UnitTypes.flare, UnitTypes.poly, UnitTypes.mega, UnitTypes.horizon,
            UnitTypes.zenith, UnitTypes.antumbra, UnitTypes.eclipse, UnitTypes.quad, UnitTypes.oct,
            UnitTypes.avert, UnitTypes.obviate, UnitTypes.quell, UnitTypes.disrupt, UnitTypes.alpha,
            UnitTypes.beta, UnitTypes.gamma, UnitTypes.evoke, UnitTypes.incite, UnitTypes.emanate,
            UnitTypes.monoDuo, UnitTypes.flareDuo
        };

        for(UnitType type : air){
            Unit unit = type.create(Team.sharded);
            //a 1x1 plate fits every silhouette that accepts a 1x1 turret, so what remains is
            //space: a free dorsal slot and dorsal area to pay for it
            boolean fits = type.hasSlotFor(1) && type.fitsSilhouette(1);
            boolean freeSlot = unit.mountedTurrets() < type.turretSlots.size;
            float area = TurretSlot.pxTile * TurretSlot.pxTile;
            boolean budget = unit.loadoutCost() + area <= type.dorsalBudget();

            assertEquals(fits && freeSlot && budget, unit.canMountWall(Blocks.copperWall), type.name);
        }
    }
}
