import arc.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for mounted turret modules actually firing.
 *
 * <p>Covers the two ways a dorsal turret could be drawn on a hull and never shoot: the AI picking
 * the main target with the <b>hull's</b> own filters and range instead of the turret's, and a
 * command controller (repair) sharing the weapon-retarget timer slot, which starved
 * {@code retarget()} so weapons never looked for a target again.</p>
 */
public class TurretModuleShootingTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(96, 96);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
    }

    /** enemy turret block, so the test target is a ground structure like the placed ones in test mode */
    private void enemyBuilding(int tileX, int tileY){
        world.tile(tileX, tileY).setBlock(Blocks.duo, Team.crux, 0);
        state.teams.updateTeamStats();
    }

    /** Drives the unit exactly like the generated Unit.update() does: controller, then weapons. */
    private int runAndCountShots(Unit unit, int frames){
        for(int i = 0; i < frames; i++){
            Time.update();
            if(unit.controller() != null) unit.controller().updateUnit();
            for(int k = 0; k < unit.mounts.length; k++){
                unit.mounts[k].weapon.update(unit, unit.mounts[k]);
            }
            Groups.bullet.update();
        }

        int shots = 0;
        for(int k = 0; k < unit.mounts.length; k++){
            if(unit.mounts[k].weapon instanceof TurretWeapon) shots = unit.mounts[k].totalShots;
        }
        return shots;
    }

    @Test
    void mountedTurretFiresOnAPlainHull(){
        Unit attacker = UnitTypes.gamma.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        enemyBuilding(58, 48);

        assertNotNull(attacker.mountTurret((Turret)Blocks.duo), "gamma must accept the module");
        assertTrue(runAndCountShots(attacker, 600) > 0, "a mounted turret must fire at an enemy block");
    }

    @Test
    void mountedTurretFiresOnARepairCommandHull(){
        //mega's default command is repair: its controller must neither silence the module nor
        //starve weapon retargeting with its own timer slot
        Unit attacker = UnitTypes.mega.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        enemyBuilding(58, 48);

        assertNotNull(attacker.mountTurret((Turret)Blocks.duo), "mega must accept the module");
        assertTrue(runAndCountShots(attacker, 600) > 0, "a mounted turret must fire while the hull repairs");
    }

    @Test
    void mountedTurretStaysSilentBeyondItsOwnRange(){
        Unit attacker = UnitTypes.gamma.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        //30 tiles away: outside the duo's range, so no target may be engaged
        enemyBuilding(78, 48);

        assertNotNull(attacker.mountTurret((Turret)Blocks.duo));
        assertEquals(0, runAndCountShots(attacker, 600), "the module must not fire beyond its own range");
    }
}
