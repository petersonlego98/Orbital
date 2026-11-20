import arc.util.Time;
import orbital.content.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.io.*;
import orbital.type.*;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies per-building levels, their stat curves, upgrades and save persistence. */
public class BuildingLevelSystemTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(32, 32);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
    }

    @Test
    void levelStartsAtOneAndClampsToTheAllowedRange(){
        Building duo = place(Blocks.duo, 10, 10);

        assertEquals(1, duo.level);

        duo.setLevel(1000);
        assertEquals(BuildingLevels.maxLevel, duo.level);

        duo.setLevel(-5);
        assertEquals(1, duo.level);
    }

    @Test
    void levelScalesHealthAndKeepsTheCurrentHealthRatio(){
        Building duo = place(Blocks.duo, 10, 10);
        float baseMax = duo.maxHealth;

        duo.setLevel(100);
        assertEquals(baseMax * BuildingLevels.healthMultiplier(100), duo.maxHealth, 0.5f);

        duo.health = duo.maxHealth / 2f;
        duo.setLevel(50);
        assertEquals(duo.maxHealth / 2f, duo.health, 1f);
        assertTrue(duo.health <= duo.maxHealth);
    }

    @Test
    void levelImprovesProgressSpeedAndCapacity(){
        Time.setDeltaProvider(() -> 1f);
        Building duo = place(Blocks.duo, 10, 10);
        duo.efficiency = 1f;

        float baseProgress = duo.getProgressIncrease(60f);
        duo.setLevel(100);
        assertTrue(duo.getProgressIncrease(60f) > baseProgress * 2.5f,
            "level 100 must produce noticeably faster than level 1");

        Building vault = place(Blocks.vault, 12, 12);
        int baseCapacity = vault.getMaximumAccepted(Items.copper);
        vault.setLevel(100);
        assertTrue(vault.getMaximumAccepted(Items.copper) > baseCapacity);
    }

    @Test
    void upgradeSpendsCoreResourcesAndStopsAtMaxLevel(){
        Building core = place(Blocks.coreShard, 5, 5);
        assertNotNull(Team.sharded.core());
        core.items.add(Items.copper, 100000);
        core.items.add(Items.lead, 100000);

        Building duo = place(Blocks.duo, 10, 10);
        assertTrue(BuildingLevels.canUpgrade(duo, Team.sharded));

        int copperBefore = core.items.get(Items.copper);
        assertTrue(BuildingLevels.upgrade(duo, Team.sharded));
        //resources are spent immediately and the timed evolution begins
        assertTrue(core.items.get(Items.copper) < copperBefore, "the upgrade must spend copper from the core");
        assertTrue(duo.evolving, "a timed evolution must start");
        assertEquals(2, duo.evolveTarget);
        assertEquals(1, duo.level, "the level only changes when the evolution finishes");
        assertFalse(BuildingLevels.canUpgrade(duo, Team.sharded), "cannot stack upgrades while evolving");

        //advance time until the evolution completes
        Time.setDeltaProvider(() -> 1f);
        for(int i = 0; i < 1000 && duo.evolving; i++) duo.update();
        assertFalse(duo.evolving);
        assertEquals(2, duo.level, "the new level applies when the timer completes");

        duo.setLevel(BuildingLevels.maxLevel);
        assertFalse(BuildingLevels.canUpgrade(duo, Team.sharded));
        assertFalse(BuildingLevels.upgrade(duo, Team.sharded));
    }

    @Test
    void levelAndScaledHealthSurviveSaveLoad(){
        Building duo = place(Blocks.duo, 10, 10);
        duo.setLevel(42);
        duo.health = duo.maxHealth * 0.5f;

        float expectedMax = duo.maxHealth;
        float expectedHealth = duo.health;

        SaveIO.write(saveDirectory.child("building-level.msav"));
        logic.reset();
        SaveIO.load(saveDirectory.child("building-level.msav"));

        Building loaded = world.tile(10, 10).build;
        assertNotNull(loaded, "the leveled building must exist after loading");
        assertEquals(42, loaded.level);
        assertEquals(expectedMax, loaded.maxHealth, 0.5f);
        assertEquals(expectedHealth, loaded.health, 0.5f);
    }

    private static Building place(Block block, int x, int y){
        Tile tile = world.tile(x, y);
        tile.setBlock(block, Team.sharded);
        return tile.build;
    }
}
