import arc.struct.Seq;
import orbital.content.*;
import orbital.entities.units.GroupedWeaponMount;
import orbital.entities.units.WeaponMount;
import orbital.game.Team;
import orbital.gen.Unit;
import orbital.type.Weapon;
import orbital.world.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contrato dos modulos de arma combinados por tipo de tiro, com niveis T ativaveis. */
class CombinedWeaponModuleTest{
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
    void catalogGroupsWeaponsByShotType(){
        Seq<String> keys = new Seq<>();
        for(WeaponModules.Group group : WeaponModules.groups()){
            assertNotNull(group.name);
            assertTrue(group.tierCount() > 0, group.key + " has no tier");
            keys.add(group.key);
        }
        Seq<String> unique = new Seq<>();
        for(String key : keys) if(!unique.contains(key)) unique.add(key);
        assertEquals(unique.size, keys.size, "one module per shot type");
    }

    @Test
    void oneSlotAndFixedCostForTheWholeGroup(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WeaponModules.Group group = WeaponModules.groups().find(g -> g.key.equals("laser"));
        assertNotNull(group, "laser group exists");

        float before = unit.loadoutCost();
        int slotsBefore = unit.mountedTurrets();
        GroupedWeaponMount mounted = unit.mountGroup(group);
        assertNotNull(mounted, "quad has a free dorsal slot");
        assertEquals(1, unit.groupedMounts.length);
        assertEquals(group.tierCount(), countTierWeapons(unit));
        assertEquals(before + group.cost, unit.loadoutCost(), 0.001f, "the group costs one slot");
        assertEquals(slotsBefore + 1, unit.mountedTurrets(), "tier copies are one module");
        assertEquals(1, unit.moduleGroups().size, "the HUD shows a single cell");

        assertTrue(unit.removeGroup(mounted));
        assertEquals(0, unit.groupedMounts.length);
        assertEquals(before, unit.loadoutCost(), 0.001f);
    }

    @Test
    void inactiveTierNeverFires(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WeaponModules.Group group = WeaponModules.groups().find(g -> g.tierCount() >= 2);
        GroupedWeaponMount mounted = unit.mountGroup(group);
        assertNotNull(mounted);

        int activeTier = -1;
        for(int i = 0; i < 5; i++){
            if(mounted.tiers[i] != null){ activeTier = i; break; }
        }
        mounted.active[activeTier] = false;
        WeaponMount tierMount = mounted.tiers[activeTier];
        tierMount.shoot = true;
        unit.applyDisarmed(tierMount);
        assertFalse(tierMount.shoot, "a switched-off tier is silenced");
    }

    @Test
    void legacyModuleKeepsItsTier(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        WeaponModules.Definition legacy = WeaponModules.all().find(d -> d.tier == 2);
        assertNotNull(legacy);
        Weapon weapon = unit.mountWeaponModule(legacy);
        assertNotNull(weapon);
        assertEquals(1, unit.groupedMounts.length, "legacy mount becomes a combined module");
        GroupedWeaponMount combined = unit.groupedMounts[0];
        assertTrue(combined.active[legacy.tier - 1], "the original tier stays active");
        assertEquals(1, combined.activeCount());
    }

    private static int countTierWeapons(Unit unit){
        int count = 0;
        for(WeaponMount mount : unit.mounts){
            if(mount.weapon.groupKey != null) count++;
        }
        return count;
    }
}
