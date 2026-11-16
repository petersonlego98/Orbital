import arc.util.*;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Presentation rules for weapon modules mounted on a hull.
 *
 * <p>A dorsal module is drawn on top of the carrier hull, so its sprite, its muzzle and its
 * shots all have to follow the module's <b>drawn</b> size, not the source block's size. These
 * tests pin the three places that used to disagree:</p>
 *
 * <ul>
 *     <li>a copy of a native unit weapon kept the source sprite size and covered the hull;</li>
 *     <li>the shot offset was authored for the full block, so bullets spawned past the barrel;</li>
 *     <li>on high-altitude hulls the bullet layer sits under the hull, so the shot appeared to
 *     leave the ship from below instead of from the module on top.</li>
 * </ul>
 */
public class DorsalModulePresentationTest{

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

    /** an enemy structure, so the mounted module has something to shoot at */
    private static void enemyBuilding(int tileX, int tileY){
        world.tile(tileX, tileY).setBlock(Blocks.duo, Team.crux, 0);
        state.teams.updateTeamStats();
    }

    /**
     * Drives the hull like the generated update loop does and returns the mount that fired,
     * inspected <b>before</b> the bullet group updates (a pooled bullet may die on that frame).
     */
    private static WeaponMount firstShot(Unit unit, int frames){
        for(int i = 0; i < frames; i++){
            Time.update();
            if(unit.controller() != null) unit.controller().updateUnit();
            for(WeaponMount mount : unit.mounts){
                mount.weapon.update(unit, mount);
            }
            for(WeaponMount mount : unit.mounts){
                if(mount.weapon instanceof TurretWeapon && mount.bullet != null) return mount;
            }
            Groups.bullet.update();
        }
        return null;
    }

    @Test
    void turretSpriteAndMuzzleShrinkWithTheHull(){
        Unit unit = UnitTypes.gamma.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        Weapon turret = unit.mountTurret((Turret)Blocks.duo);

        assertNotNull(turret, "gamma must accept the Duo module");

        float scale = turret.visualScale(unit);
        assertTrue(scale > 0f && scale < 1f, "a mounted module must be drawn smaller than its block");

        //the muzzle follows the drawn sprite instead of the block's old edge
        assertEquals(turret.shootY * scale, turret.muzzleY(unit), 0.0001f);
        assertTrue(turret.muzzleY(unit) < turret.shootY,
            "the authored block muzzle would sit outside the shrunk turret");

        float drawnHalf = DorsalGeometry.footprint(turret.dorsalSize) * scale / 2f;
        assertTrue(turret.muzzleY(unit) <= drawnHalf + 0.01f,
            "the bullet must leave the drawn barrel, not empty space beside the module");
    }

    @Test
    void nativeUnitWeaponModulesAlsoShrinkWithTheHull(){
        Unit hull = UnitTypes.mono.spawn(Team.sharded, 48 * 8f, 48 * 8f);

        //pick a module that is actually larger than the dorsal target on this small hull; headless
        //runs resolve every sprite to its block size, so at least one entry must qualify
        Weapon copy = null;
        for(WeaponModules.Definition module : WeaponModules.all()){
            Weapon candidate = module.copy();
            if(candidate.visualScale(hull) < 1f){
                copy = candidate;
                break;
            }
        }
        assumeTrue(copy != null, "no weapon module is larger than the carrier hull");

        assertTrue(copy.removable);
        float scale = copy.visualScale(hull);
        assertTrue(scale < 1f,
            "a weapon module copied from another hull must shrink to the carrier's dorsal scale");
        assertEquals(copy.shootY * scale, copy.muzzleY(hull), 0.0001f,
            "the muzzle must follow the shrunk sprite");
    }

    @Test
    void dorsalShotIsDrawnAboveAHighAltitudeHull(){
        //avert is a high-altitude hull: drawn above the bullet layer
        Unit attacker = UnitTypes.avert.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        enemyBuilding(58, 48);

        assertNotNull(attacker.type, "the hull must exist");
        assertFalse(attacker.type.lowAltitude, "this test needs a high-altitude hull");
        assertTrue(attacker.type.flyingLayer > Layer.bullet);

        assertNotNull(attacker.mountTurret((Turret)Blocks.duo), "avert must accept the Duo module");
        WeaponMount mount = firstShot(attacker, 900);
        assertNotNull(mount, "the mounted module must fire at the enemy structure");

        assertTrue(mount.bullet.layerOffset > 0f,
            "a dorsal shot must be raised above the carrier hull");
        assertTrue(mount.bullet.type.layer + mount.bullet.layerOffset > attacker.type.flyingLayer,
            "the shot must render over the hull that carries the module");
    }

    @Test
    void dorsalShotKeepsTheAuthoredLayerOnALowAltitudeHull(){
        //gamma flies below the bullet layer, so its modules need no correction
        Unit attacker = UnitTypes.gamma.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        enemyBuilding(58, 48);

        assertTrue(attacker.type.lowAltitude);
        assertTrue(attacker.type.flyingLayer < Layer.bullet);

        assertNotNull(attacker.mountTurret((Turret)Blocks.duo));
        WeaponMount mount = firstShot(attacker, 900);
        assertNotNull(mount, "the mounted module must fire at the enemy structure");
        assertEquals(0f, mount.bullet.layerOffset, 0.0001f,
            "a hull already below the bullet layer must not move its shots");
    }
}
