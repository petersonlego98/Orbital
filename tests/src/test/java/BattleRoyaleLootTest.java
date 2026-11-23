import arc.math.*;
import arc.struct.ObjectSet;
import orbital.content.UnitTypes;
import orbital.entities.units.SupportMount;
import orbital.game.*;
import orbital.gen.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato do loot 8x8: cobertura de categorias, aleatoriedade, loot periódico e ciclo instalar/soltar. */
class BattleRoyaleLootTest{
    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        Tiles tiles = new Tiles(BattleRoyaleMode.mapSize, BattleRoyaleMode.mapSize);
        world.tiles = tiles;
        world.beginMapLoad();
        tiles.fill();
        BattleRoyaleMode.generate(tiles);
        world.endMapLoad();
        state.rules = Gamemode.battleRoyale.apply(new Rules());
        BattleRoyaleLoot.reset();
    }

    @AfterEach
    void clear(){
        BattleRoyaleLoot.reset();
    }

    @Test
    void spawnPlacesFourModulesInEveryRegionAndCoversEveryCategory(){
        BattleRoyaleLoot.spawn();

        assertEquals(32, BattleRoyaleLoot.all().size, "4 drops per region x 8 regions");

        ObjectSet<ModuleDropKind> kinds = new ObjectSet<>();
        for(DroppedModule drop : BattleRoyaleLoot.all()){
            kinds.add(drop.kind);
            assertTrue(insideSomeRegion(drop), "every drop must sit inside a team pocket");
            assertFalse(drop.name().isEmpty(), "the world label needs a name");
            assertFalse(drop.type().isEmpty(), "the world label needs a type");
        }
        assertEquals(ModuleDropKind.values().length, kinds.size, "all ten categories must drop somewhere");
    }

    @Test
    void spawnIsDeterministicForAnExplicitSeed(){
        BattleRoyaleLoot.spawn(new Rand(123));
        String first = signature();

        BattleRoyaleLoot.reset();
        BattleRoyaleLoot.spawn(new Rand(123));
        assertEquals(first, signature(), "an explicit seed must reproduce the same loot");
    }

    @Test
    void initialLootIsRandomPerMatch(){
        BattleRoyaleLoot.spawn();
        String first = signature();

        BattleRoyaleLoot.reset();
        BattleRoyaleLoot.spawn();
        assertNotEquals(first, signature(), "different matches must not drop the same modules in the same spots");
    }

    @Test
    void periodicSpawnAddsOneModuleAnywhere(){
        BattleRoyaleLoot.spawn(new Rand(5));
        int before = BattleRoyaleLoot.all().size;

        BattleRoyaleLoot.spawnRandomOne();
        assertEquals(before + 1, BattleRoyaleLoot.all().size);

        DroppedModule last = BattleRoyaleLoot.all().get(BattleRoyaleLoot.all().size - 1);
        assertTrue(last.x >= 0f && last.x <= world.width() * tilesize);
        assertTrue(last.y >= 0f && last.y <= world.height() * tilesize);
    }

    @Test
    void hitTestFindsTheDropUnderTheCursor(){
        BattleRoyaleLoot.spawn();
        DroppedModule drop = BattleRoyaleLoot.all().first();

        assertSame(drop, BattleRoyaleLoot.findAt(drop.x, drop.y, 14f));
        //far outside the sector: +1000 can land on a neighbouring region's drop
        assertNull(BattleRoyaleLoot.findAt(drop.x + 100000f, drop.y + 100000f, 14f));
    }

    @Test
    void installedModuleCanBeDroppedBackOnTheFloor(){
        BattleRoyaleLoot.spawn();
        DroppedModule repair = BattleRoyaleLoot.all().find(d -> d.kind == ModuleDropKind.repair);
        assertNotNull(repair, "every region has a repair module");

        //antumbra has an anchor for every support category and can target flying units
        Unit unit = UnitTypes.antumbra.spawn(Team.sharded, repair.x, repair.y);
        assertTrue(repair.canInstall(unit));
        assertTrue(repair.install(unit));

        int afterInstall = BattleRoyaleLoot.all().size;
        SupportMount[] mounts = unit.supportMounts;
        assertEquals(1, mounts.length);

        BattleRoyaleLoot.dropSupport(unit, mounts[0]);
        assertEquals(0, unit.supportMounts.length);
        assertEquals(afterInstall + 1, BattleRoyaleLoot.all().size, "the X button must leave a new drop");
    }

    private static boolean insideSomeRegion(DroppedModule drop){
        for(int i = 0; i < BattleRoyaleMode.teams().length; i++){
            float[] centre = BattleRoyaleMode.regionCenter(i);
            if(Mathf.within(drop.x, drop.y, centre[0], centre[1], 10f * tilesize)) return true;
        }
        return false;
    }

    private static String signature(){
        StringBuilder out = new StringBuilder();
        for(DroppedModule drop : BattleRoyaleLoot.all()){
            out.append(drop.kind).append(':').append((int)drop.x).append(',').append((int)drop.y).append(';');
        }
        return out.toString();
    }
}
