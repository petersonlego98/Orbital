import orbital.content.*;
import orbital.type.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every non-enemy unit shares the starter unit's mining laser and can mine every ore. */
public class UniversalMiningTest{
    @BeforeAll
    public static void init(){
        ApplicationTests.launchApplication();
    }

    @Test
    void nonEnemyUnitsMineEverything(){
        boolean found = false;
        for(UnitType unit : content.units()){
            if(unit.isEnemy || !unit.playerControllable){
                //Enemy units and projectiles keep their original capabilities.
                continue;
            }
            found = true;
            assertEquals(6, unit.mineTier, unit.name + " must mine Petrônio, the hardest ore");
            assertEquals(6.5f, unit.mineSpeed, 0.001f, unit.name + " must match the starter unit");
            assertTrue(unit.mineFloor, unit.name + " must mine floor deposits");
            assertTrue(unit.mineWalls, unit.name + " must mine wall veins");
            assertSame(Items.universalItems, unit.mineItems, unit.name + " AI must consider every item");
        }
        assertTrue(found, "there must be at least one non-enemy unit");
    }

    @Test
    void starterUnitIsNotEnemy(){
        assertFalse(UnitTypes.alpha.isEnemy, "the starter unit must be included");
    }
}
