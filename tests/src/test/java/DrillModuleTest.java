import orbital.game.Team;
import orbital.content.*;
import orbital.entities.units.SupportMount;
import orbital.game.ModuleDropKind;
import orbital.gen.Unit;
import orbital.type.SupportModuleCategory;
import orbital.world.Block;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato das brocas como módulos dorsais: uma por casco, com bônus de velocidade e alcance. */
class DrillModuleTest{
    private static final String[] drillNames = {
        "mechanical-drill", "pneumatic-drill", "laser-drill", "blast-drill",
        "plasma-bore", "large-plasma-bore", "impact-drill", "eruption-drill"
    };

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void everyOreDrillIsAMiningModuleAndCannotBeBuilt(){
        for(String name : drillNames){
            Block drill = content.block(name);
            assertNotNull(drill, name);
            assertEquals(SupportModuleCategory.mining, SupportModuleCategory.of(drill), name);
            assertTrue(drill.moduleOnly, name + " must stay in the module catalogue");
            assertTrue(drill.isHidden(), name + " must not be buildable");
        }
    }

    @Test
    void scanningAndSandDrillsAreNotMiningModules(){
        assertNull(SupportModuleCategory.of(Blocks.planetDrill));
        assertNull(SupportModuleCategory.of(Blocks.cliffCrusher));
        assertNull(SupportModuleCategory.of(Blocks.largeCliffCrusher));
    }

    @Test
    void mountingADrillBoostsMiningSpeedAndRange(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertEquals(1f, unit.miningSpeedMultiplier(), 0.0001f);
        assertEquals(0f, unit.miningRangeBonus(), 0.0001f);

        SupportMount mount = unit.mountSupport(Blocks.mechanicalDrill);
        assertNotNull(mount, "quad has a mining anchor");
        assertTrue(unit.miningSpeedMultiplier() > 1f, "a drill must actually mine faster");
        assertTrue(unit.miningRangeBonus() > 0f, "a drill must actually reach further");

        mount.setActive(unit, false);
        assertEquals(1f, unit.miningSpeedMultiplier(), 0.0001f, "a deactivated drill gives no bonus");
        assertEquals(0f, unit.miningRangeBonus(), 0.0001f);
    }

    @Test
    void onlyOneDrillFitsPerHull(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertNotNull(unit.mountSupport(Blocks.mechanicalDrill));
        assertFalse(unit.canMountSupport(Blocks.laserDrill), "mining is one exclusive category");
        assertNull(unit.mountSupport(Blocks.laserDrill));
    }

    @Test
    void higherTierDrillsGiveBiggerBonuses(){
        Unit small = UnitTypes.quad.create(Team.sharded);
        small.mountSupport(Blocks.mechanicalDrill);
        Unit capital = UnitTypes.oct.create(Team.sharded);
        capital.mountSupport(Blocks.eruptionDrill);

        assertTrue(capital.miningSpeedMultiplier() > small.miningSpeedMultiplier());
        assertTrue(capital.miningRangeBonus() > small.miningRangeBonus());
    }

    @Test
    void miningModulesDropInBattleRoyale(){
        assertEquals(ModuleDropKind.mining, ModuleDropKind.of(SupportModuleCategory.mining));
    }
}
