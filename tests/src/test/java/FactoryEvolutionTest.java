import orbital.content.*;
import orbital.game.Team;
import orbital.gen.Building;
import orbital.gen.Groups;
import orbital.gen.Unit;
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
        assertEquals(2, air.evolutionChains.size);
        assertSame(UnitTypes.poly, air.evolutionChains.get(0)[1]);
        assertSame(UnitTypes.oct, air.evolutionChains.get(0)[4]);
        assertSame(UnitTypes.eclipse, air.evolutionChains.get(1)[4]);
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
    void factoryUpgradeReplacesEveryPlayerUnitInTheLine(){
        UnitFactory.UnitFactoryBuild build = (UnitFactory.UnitFactoryBuild)place(Blocks.airFactory, 12, 12);
        Unit friendly = UnitTypes.mono.spawn(Team.sharded, 100f, 120f, 45f, null);
        UnitTypes.mono.spawn(Team.crux, 200f, 220f, 0f, null);
        friendly.health = friendly.maxHealth * 0.5f;

        build.replaceAllUnits(1, 2);

        Unit evolved = Groups.unit.find(u -> u.team == Team.sharded && u.type == UnitTypes.poly);
        assertNotNull(evolved, "every player unit of the line becomes the next form");
        assertEquals(100f, evolved.x, 0.01f, "the new hull takes the old position");
        assertEquals(120f, evolved.y, 0.01f);
        assertTrue(evolved.health < evolved.maxHealth, "the health ratio is preserved");
        assertNull(Groups.unit.find(u -> u.team == Team.sharded && u.type == UnitTypes.mono));
        assertNotNull(Groups.unit.find(u -> u.team == Team.crux && u.type == UnitTypes.mono),
            "enemy units are not affected");
    }

    @Test
    void shipFabricatorUsesErekirRefabricatorVisuals(){
        UnitFactory ship = (UnitFactory)Blocks.shipFabricator;
        assertTrue(ship.leveled);
        assertEquals(3, ship.maxLevel);
        assertSame(Blocks.shipRefabricator, ship.evolutionVisuals[0]);
        assertSame(Blocks.primeRefabricator, ship.evolutionVisuals[1]);
        assertSame(UnitTypes.obviate, ship.evolutionChains.get(0)[2]);
    }

    private static Building place(Block block, int x, int y){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded);
        return tile.build;
    }
}
