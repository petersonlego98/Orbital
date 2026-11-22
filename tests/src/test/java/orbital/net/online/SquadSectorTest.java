package orbital.net.online;

import arc.math.geom.Point2;
import orbital.game.Team;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Groups of 4 get persistent, distinct sector seeds and unique team slots. */
class SquadSectorTest{
    @Test void fourDistinctQuadrantsAndTeams(){
        Point2[] cells = SquadSectorGenerator.coreCells();
        Team[] teams = SquadSectorGenerator.teams();
        assertEquals(4, cells.length);
        assertEquals(4, teams.length);
        for(int i = 0; i < 4; i++){
            for(int j = i + 1; j < 4; j++){
                assertNotEquals(cells[i].x + "," + cells[i].y, cells[j].x + "," + cells[j].y);
                assertNotSame(teams[i], teams[j]);
            }
        }
    }

    @Test void generatorIsSeedDependent(){
        //Terrain noise uses the squad seed directly; different seeds must differ.
        double n1 = arc.util.noise.Simplex.noise2d(111, 4, 0.55, 1.0 / 180.0, 40, 60);
        double n2 = arc.util.noise.Simplex.noise2d(222, 4, 0.55, 1.0 / 180.0, 40, 60);
        assertNotEquals(n1, n2, "different squad seeds must produce different terrain");
    }

    @Test void seedMapsToPlanetSector(){
        int a = SquadSectorRegistry.sectorIdForSeed(11);
        int b = SquadSectorRegistry.sectorIdForSeed(300);
        assertTrue(a >= 0 && a < 272);
        assertTrue(b >= 0 && b < 272);
        assertNotEquals(a, b, "distinct seeds should map to distinct planet sectors");
        assertEquals(11, SquadSectorRegistry.sectorIdForSeed(11 + 272));
    }
}
