import arc.Core;
import arc.util.Time;
import orbital.content.Bullets;
import orbital.engine.OrbitalEngine;
import orbital.engine.combat.ProjectileTick;
import orbital.game.Team;
import orbital.gen.Bullet;
import orbital.gen.Groups;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** End-to-end tests for the parallel bullet movement phase against real bullets. */
class ProjectileTickIntegrationTest{

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

        Groups.bullet.clear();
        Time.delta = 1f;
        Bullets.placeholder.drag = 0.1f;
        Core.settings.put(ProjectileTick.key, true);
        ProjectileTick.overrideThreshold(1);
    }

    @AfterEach
    void cleanup(){
        ProjectileTick.overrideThreshold(-1);
        Core.settings.put(ProjectileTick.key, false);
        Groups.bullet.clear();
        OrbitalEngine.shutdownIfStarted();
    }

    private static Bullet bullet(){
        //start once, then make it eligible: vanilla leaves a just-spawned bullet at the muzzle
        Bullet bullet = Bullets.placeholder.create(null, Team.sharded, 100f, 100f, 0f);
        assertNotNull(bullet);
        bullet.justSpawned(false);
        bullet.vel().set(10f, 0f);
        return bullet;
    }

    @Test
    void aBulletMovesExactlyOncePerTick(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine cannot run the engine pool");
        Bullet bullet = bullet();

        ProjectileTick.tick();

        assertTrue(ProjectileTick.lastEngaged);
        assertEquals(1, ProjectileTick.lastEngagedBullets);
        assertEquals(1, ProjectileTick.lastEligibleBullets);

        Groups.bullet.update();

        //one integration: a second one would land on 109 (10 * 0.9)
        assertEquals(110f, bullet.x(), 0.001f, "the bullet moved twice");
        assertEquals(100f, bullet.y(), 0.001f);
        assertEquals(9f, bullet.vel().x, 0.001f, "drag must be applied once");
    }

    @Test
    void justSpawnedBulletsAreLeftToTheVanillaMuzzleRule(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine cannot run the engine pool");
        Bullet fresh = Bullets.placeholder.create(null, Team.sharded, 200f, 100f, 0f);
        assertNotNull(fresh);
        fresh.vel().set(10f, 0f);

        ProjectileTick.tick();

        assertFalse(ProjectileTick.lastEngaged, "a just-spawned bullet must not be captured");
        assertEquals(0, ProjectileTick.lastEligibleBullets);

        Groups.bullet.update();

        assertEquals(200f, fresh.x(), 0.001f, "the muzzle rule must still hold");
        assertFalse(fresh.justSpawned(), "the vanilla update must clear the flag");
    }

    @Test
    void withThePreferenceOffNothingIsCaptured(){
        Core.settings.put(ProjectileTick.key, false);
        Bullet bullet = bullet();

        ProjectileTick.tick();

        assertFalse(ProjectileTick.lastEngaged);
        assertEquals(0, ProjectileTick.lastEngagedBullets);

        Groups.bullet.update();

        assertEquals(110f, bullet.x(), 0.001f, "the vanilla path must still move the bullet once");
    }

    @Test
    void aStaleFlagNeverFreezesABullet(){
        assumeTrue(OrbitalEngine.parallelCapable(), "this machine cannot run the engine pool");
        Bullet bullet = bullet();

        //engine flags the bullet but the group update never runs
        ProjectileTick.tick();
        assertTrue(ProjectileTick.lastEngaged);
        assertEquals(110f, bullet.x(), 0.001f);

        //the next tick does not engage but must clear the pending flag
        Core.settings.put(ProjectileTick.key, false);
        ProjectileTick.tick();
        assertFalse(ProjectileTick.lastEngaged);

        Groups.bullet.update();

        assertEquals(119f, bullet.x(), 0.001f, "a stale flag swallowed a frame of movement");
    }

    @Test
    void bulletsBelowTheThresholdAreNotCaptured(){
        ProjectileTick.overrideThreshold(64);
        bullet();

        ProjectileTick.tick();

        assertFalse(ProjectileTick.lastEngaged);
        assertEquals(1, ProjectileTick.lastEligibleBullets);
    }
}