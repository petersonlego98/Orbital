import orbital.content.*;
import orbital.game.Team;
import orbital.gen.Building;
import orbital.type.*;
import orbital.world.*;
import orbital.world.blocks.units.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato das fabricas aereas niveladas: tiers, custo acumulado e evolucao de payload. */
class FactoryEvolutionTest{
    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(64, 64);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
    }

    @Test
    void airHullsHaveResearchTiers(){
        assertEquals(1, UnitTypes.mono.tier);
        assertEquals(1, UnitTypes.flareDuo.tier, "the demo hull inherits the stock hull's tier");
        assertEquals(2, UnitTypes.poly.tier, "mono -> poly is the second evolution level");
        assertEquals(2, UnitTypes.horizon.tier);
        assertEquals(4, UnitTypes.quad.tier);
        assertEquals(5, UnitTypes.oct.tier);
        assertEquals(1, WeaponModules.tierOf(UnitTypes.poly),
            "weapon-module tiers stay cosmetic and must not follow the evolution chain");
    }

    @Test
    void airFactoryUsesReconstructorSpritesPerTier(){
        UnitFactory air = (UnitFactory)Blocks.airFactory;
        assertTrue(air.leveled);
        assertEquals(5, air.maxLevel);
        assertEquals(4, air.evolutionVisuals.length);
        assertSame(Blocks.additiveReconstructor, air.evolutionVisuals[0]);
        assertSame(Blocks.tetrativeReconstructor, air.evolutionVisuals[3]);
    }

    @Test
    void lowerTierHullsAreSelectableWithCumulativeCost(){
        UnitFactory air = (UnitFactory)Blocks.airFactory;
        assertTrue(air.plans.contains(p -> p.unit == UnitTypes.poly), "T2 hull becomes a plan");
        assertTrue(air.plans.contains(p -> p.unit == UnitTypes.oct), "T5 hull becomes a plan");

        UnitFactory.UnitFactoryBuild build = (UnitFactory.UnitFactoryBuild)place(Blocks.airFactory, 10, 10);
        assertTrue(build.levelCost(2).length > 0, "evolution cost comes from the full chain");
    }

    @Test
    void factoryEvolvesAFedLowerTierUnit(){
        UnitFactory air = (UnitFactory)Blocks.airFactory;
        UnitFactory.UnitFactoryBuild build = (UnitFactory.UnitFactoryBuild)place(Blocks.airFactory, 12, 12);
        build.level = 2;
        build.evolveTarget = air.plans.indexOf(p -> p.unit == UnitTypes.poly);

        assertTrue(build.acceptUnitPayload(UnitTypes.mono.create(Team.sharded)),
            "a T1 hull can be evolved by a T2 factory");
        assertFalse(build.acceptUnitPayload(UnitTypes.poly.create(Team.sharded)),
            "a hull of the same tier cannot be evolved again");
        assertSame(UnitTypes.poly, build.evolutionPlan().unit);
    }

    @Test
    void shipFabricatorUsesErekirRefabricatorVisuals(){
        UnitFactory ship = (UnitFactory)Blocks.shipFabricator;
        assertTrue(ship.leveled);
        assertEquals(3, ship.maxLevel);
        assertSame(Blocks.shipRefabricator, ship.evolutionVisuals[0]);
        assertSame(Blocks.primeRefabricator, ship.evolutionVisuals[1]);
    }

    private static Building place(Block block, int x, int y){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded);
        return tile.build;
    }
}
