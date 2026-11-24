import orbital.content.Blocks;
import orbital.content.Items;
import orbital.game.Team;
import orbital.gen.*;
import orbital.world.*;
import orbital.world.blocks.production.PlanetDrill;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the planetary drill discovers the ores present in the sector. */
class PlanetDrillTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void drillIsRegisteredAndScanningFindsSectorOres(){
        assertNotNull(Blocks.planetDrill, "planet drill must be registered");
        assertTrue(Blocks.planetDrill.fogRadius > 0, "it must reveal the sector via fog");

        //a fresh map with a known coal patch
        Tiles tiles = new Tiles(64, 64);
        world.tiles = tiles;
        world.beginMapLoad();
        tiles.fill();
        for(Tile t : tiles) t.setFloor((orbital.world.blocks.environment.Floor)Blocks.stone);
        for(int y = 10; y < 20; y++){
            for(int x = 10; x < 20; x++) tiles.getn(x, y).setFloor((orbital.world.blocks.environment.Floor)Blocks.orePetronio);
        }
        world.endMapLoad();
        orbital.engine.world.SectorResources.invalidate();

        Tile drillTile = world.tile(32, 32);
        drillTile.setBlock(Blocks.planetDrill, Team.sharded);
        Building drill = drillTile.build;
        assertNotNull(drill, "drill building must be created");
        assertTrue(drill instanceof PlanetDrill.PlanetDrillBuild);

        ((PlanetDrill.PlanetDrillBuild)drill).performScan();

        PlanetDrill.PlanetDrillBuild b = (PlanetDrill.PlanetDrillBuild)drill;
        assertTrue(b.scanned, "scan must be marked complete");
        assertTrue(b.foundItems.contains(Items.petronio), "the Petrônio in the sector must be discovered");
    }
}
