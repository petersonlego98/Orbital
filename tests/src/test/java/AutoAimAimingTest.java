import arc.*;
import arc.math.Angles;
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
 * Regression tests for what an <b>automatic</b> dorsal module aims at.
 *
 * <p>The user reported that switching a module to auto-aim left the weapon pointing at the
 * cursor, on Windows and on Android alike. The frame order in the real game is: entities update
 * first (the weapon update lets an automatic module pick its own target and aim at it), and the
 * input handler writes the pilot's aim point <b>after</b> that, every frame. A module that
 * inherited that write ended up aiming at the mouse whenever it had no target of its own - and
 * that aim point is what a laser beam draws to and what a scaled bullet's lifetime is measured
 * from.</p>
 */
public class AutoAimAimingTest{

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

    /** Re-arms the target building, so a long run never runs out of something to shoot at. */
    private void enemy(){
        world.tile(54, 48).setBlock(Blocks.duo, Team.crux, 0);
        state.teams.updateTeamStats();
    }

    /** @return a quad carrying two dorsal Duos, both pointing at the pilot's hand. */
    private Unit twoDorsalDuos(){
        Unit unit = UnitTypes.quad.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        enemy();
        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "quad must accept the first module");
        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "quad must accept the second module");
        return unit;
    }

    /**
     * The frame order of the real game, so the driver cannot lie: weapons first, then the pilot's
     * aim. The mouse moves every frame and points away from the enemy.
     */
    private void runFrames(Unit unit, float mouseX, float mouseY, int frames){
        for(int frame = 0; frame < frames; frame++){
            Time.update();
            enemy();
            for(WeaponMount mount : unit.mounts){
                if(!mount.weapon.isTurretMount()) continue;
                //the pilot's controller re-arms every controllable mount each frame
                mount.rotate = true;
                mount.shoot = true;
                mount.weapon.update(unit, mount);
            }
            unit.aim(mouseX, mouseY, true);
        }
    }

    /** @return how far a mount's rotation is from the direction of one point, in degrees. */
    private static float distTo(Unit unit, WeaponMount mount, float x, float y){
        return Math.abs(Angles.angleDist(mount.rotation, Angles.angle(unit.x, unit.y, x, y) - unit.rotation));
    }

    private int mountOf(Unit unit, int index){
        int i = 0, found = -1;
        for(int k = 0; k < unit.mounts.length; k++){
            if(!unit.mounts[k].weapon.isTurretMount()) continue;
            if(i++ == index) found = k;
        }
        return found;
    }

    @Test
    void anAutomaticModuleAimsAtItsOwnTargetAndNotAtThePilot(){
        Unit unit = twoDorsalDuos();
        int auto = unit.moduleCount() - 1;
        assertTrue(unit.setModuleAuto(auto, true), "the pilot may hand the module over");

        float mouseX = 48 * 8f - 400f, mouseY = 48 * 8f - 300f;
        runFrames(unit, mouseX, mouseY, 200);

        WeaponMount mount = unit.mounts[mountOf(unit, auto)];
        assertTrue(mount.totalShots > 0, "an automatic module picks a target and fires at it");
        assertTrue(distTo(unit, mount, 54 * 8f, 48 * 8f) < 20f,
            "the automatic module must aim at the enemy it found, not at the pilot's hand");
        assertFalse(mount.aimX == mouseX && mount.aimY == mouseY,
            "the pilot's hand is the aiming input of a manual module only");
    }

    @Test
    void aManualModuleStillFollowsThePilot(){
        Unit unit = twoDorsalDuos();
        int auto = unit.moduleCount() - 1;
        unit.setModuleAuto(auto, true);

        float mouseX = 48 * 8f - 400f, mouseY = 48 * 8f - 300f;
        runFrames(unit, mouseX, mouseY, 200);

        WeaponMount manual = unit.mounts[mountOf(unit, auto - 1)];
        assertTrue(distTo(unit, manual, mouseX, mouseY) < 20f,
            "a manual module is still driven by the pilot's hand");
        assertTrue(manual.aimX == mouseX && manual.aimY == mouseY,
            "the pilot's aim is written to every manual module");
    }

    @Test
    void anAutomaticModuleKeepsItsAimWhenTheTargetGoesAway(){
        Unit unit = twoDorsalDuos();
        int auto = unit.moduleCount() - 1;
        unit.setModuleAuto(auto, true);

        float mouseX = 48 * 8f - 400f, mouseY = 48 * 8f - 300f;
        runFrames(unit, mouseX, mouseY, 120);

        WeaponMount mount = unit.mounts[mountOf(unit, auto)];
        assertTrue(mount.totalShots > 0, "an automatic module finds the enemy and fires at it");

        //the enemy disappears and the pilot keeps moving the mouse: the module must not start
        //aiming at the hand now that it has no target of its own
        world.tile(54, 48).setBlock(Blocks.air, Team.crux, 0);
        float aimX = mount.aimX, aimY = mount.aimY;
        runFrames(unit, mouseX - 900f, mouseY - 900f, 60);

        assertFalse(mount.aimX == mouseX - 900f && mount.aimY == mouseY - 900f,
            "an automatic module never inherits the pilot's hand, with or without a target");
        assertEquals(aimX, mount.aimX, 1e-4f, "with no target the module holds the aim it had");
        assertEquals(aimY, mount.aimY, 1e-4f, "with no target the module holds the aim it had");
    }
}
