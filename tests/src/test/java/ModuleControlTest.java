import arc.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.abilities.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static orbital.Vars.*;

/**
 * Tests for the pilot-facing module control: picking the module that fires alone, its
 * auto/manual aiming mode, and the numbering the bottom bar and the number keys share.
 *
 * <p>Picking is what the number keys and the bottom bar cells both do: while a weapon module is
 * picked, it is the <b>only</b> one that fires - every other weapon module stays mounted and
 * tracks the aim, it just never shoots. Picking the same module again releases the selection and
 * every module fires together. Support modules have nothing to fire, so the same gesture switches
 * their effect on and off.</p>
 *
 * <p>The firing assertions reuse the same driver as
 * {@link TurretModuleShootingTest}: no controller runs, so a <b>manual</b> module - which only
 * fires where a pilot aims - stays silent by construction, while an <b>automatic</b> module
 * picks its own target and shoots. That is exactly the difference under test.</p>
 */
public class ModuleControlTest{

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

    /** enemy turret block, so the target is a ground structure like the ones the test mode places */
    private void enemyBuilding(int tileX, int tileY){
        world.tile(tileX, tileY).setBlock(Blocks.duo, Team.crux, 0);
        state.teams.updateTeamStats();
    }

    /**
     * Runs the unit exactly like the generated Unit.update() does: the controller first - it arms
     * every controllable mount, exactly as the player's aim() does frame by frame - and then the
     * hull silences the mounts the pilot did not pick before the weapons run. A fresh target is
     * placed each call, so a long run never runs out of something to shoot at.
     */
    private int[] runAndCountShots(Unit unit, int frames){
        enemyBuilding(52, 48);
        int[] before = shotsPerMount(unit);

        for(int i = 0; i < frames; i++){
            Time.update();
            if(unit.controller() != null) unit.controller().updateUnit();
            for(int k = 0; k < unit.mounts.length; k++){
                WeaponMount mount = unit.mounts[k];
                //the controller arms every controllable mount: without it a module that was
                //silenced and then released would stay frozen at the shoot=false the silence wrote
                if(mount.weapon.controllable){
                    mount.rotate = true;
                    mount.shoot = true;
                }
                //the hull silences a switched-off module before the weapon runs
                unit.applyDisarmed(mount);
                mount.weapon.update(unit, mount);
            }
            Groups.bullet.update();
        }

        int[] after = shotsPerMount(unit);
        int[] shots = new int[after.length];
        for(int k = 0; k < after.length; k++){
            shots[k] = after[k] - before[k];
        }
        return shots;
    }

    /** @return the shots each mount has fired so far, zero for every non-turret mount. */
    private static int[] shotsPerMount(Unit unit){
        int[] shots = new int[unit.mounts.length];
        for(int k = 0; k < unit.mounts.length; k++){
            if(unit.mounts[k].weapon instanceof TurretWeapon) shots[k] = unit.mounts[k].totalShots;
        }
        return shots;
    }

    /** @return true when two turret weapons are the same module kind (same turret block). */
    private static boolean sameKind(Weapon a, Weapon b){
        if(!(a instanceof TurretWeapon ta) || !(b instanceof TurretWeapon tb)) return false;
        return ta.turret == tb.turret;
    }

    /** @return the index in the mount array of the first weapon mount of the group at this bar position. */
    private static int moduleMount(Unit unit, int index){
        int group = 0;
        for(int k = 0; k < unit.mounts.length; k++){
            if(!unit.mounts[k].weapon.isTurretMount()) continue;
            //first mount of a group: no earlier mount shares its kind
            boolean first = true;
            for(int j = 0; j < k; j++){
                if(unit.mounts[j].weapon.isTurretMount() && sameKind(unit.mounts[j].weapon, unit.mounts[k].weapon)){
                    first = false;
                    break;
                }
            }
            if(first && group++ == index) return k;
        }
        return -1;
    }

    /**
     * @return how many weapon groups the bar numbers: one per kind of dorsal weapon, then fixed
     * hull weapons. The numbering the number keys use is the same.
     */
    private static int weaponModuleCount(Unit unit){
        int count = 0;
        for(int k = 0; k < unit.mounts.length; k++){
            if(!unit.mounts[k].weapon.isTurretMount()) continue;
            boolean first = true;
            for(int j = 0; j < k; j++){
                if(unit.mounts[j].weapon.isTurretMount() && sameKind(unit.mounts[j].weapon, unit.mounts[k].weapon)){
                    first = false;
                    break;
                }
            }
            if(first) count++;
        }
        return count;
    }

    /** @return the bar position of the first of the last {@code count} weapon groups. */
    private static int lastModules(Unit unit, int count){
        return weaponModuleCount(unit) - count;
    }

    /** Fills the pools the way a resupply would, so a long run never starves the modules. */
    private static void refillSupply(Unit unit){
        ModuleSupply supply = unit.moduleSupply();
        supply.ammo = supply.ammoCapacity;
        supply.liquidAmount = supply.liquidCapacity;
    }

    /** @return a gamma with one dorsal Duo and an enemy in range. */
    private Unit hullWithModule(){
        Unit unit = UnitTypes.gamma.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        enemyBuilding(52, 48);
        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "gamma must accept the module");
        return unit;
    }

    /** @return a quad with a dorsal Duo and a dorsal Lancer: two separately-addressable groups. */
    private Unit hullWithTwoModules(){
        Unit unit = UnitTypes.quad.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        enemyBuilding(52, 48);
        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "quad must accept the dorsal duo");
        assertNotNull(unit.mountTurret((Turret)Blocks.lancer), "quad must accept the dorsal lancer");
        return unit;
    }

    @Test
    void moduleNumberingCountsWeaponsBeforeSupportModules(){
        //quad carries anchors of several categories, so the bar has both kinds of module
        Unit unit = UnitTypes.quad.spawn(Team.sharded, 48 * 8f, 48 * 8f);

        int groups = weaponModuleCount(unit);

        assertNotNull(unit.mountSupport(Blocks.mendProjector), "quad must accept the repair projector");
        assertNotNull(unit.mountTurret((Turret)Blocks.duo), "quad must accept the dorsal duo");

        //weapon groups first (quad's own + the new duo), then the support module
        assertEquals(groups + 2, unit.moduleCount());
        assertEquals(Blocks.duo.localizedName, unit.moduleName(groups));
        assertEquals(Blocks.mendProjector.localizedName, unit.moduleName(groups + 1));
    }

    private static @Nullable String weaponName(Unit unit, int index){
        return unit.moduleName(index);
    }

    @Test
    void aSingleModuleFiresPickedOrReleased(){
        Unit unit = hullWithModule();
        int index = unit.moduleCount() - 1;
        TurretWeapon duo = (TurretWeapon)unit.mounts[moduleMount(unit, index)].weapon;
        int dorsal = moduleMount(unit, index);

        assertTrue(unit.moduleActive(index), "a fresh module starts armed");
        assertTrue(runAndCountShots(unit, 30)[dorsal] > 0, "the module must fire while armed");

        //with one weapon module the pick is a no-op it can see: it is the only one that could
        //fire either way, so it fires picked and released alike
        assertTrue(unit.selectModule(index), "picking it succeeds");
        assertFalse(unit.weaponDisarmed(duo), "the picked module is the one that fires");
        refillSupply(unit);
        assertTrue(runAndCountShots(unit, 200)[dorsal] > 0, "the module fires while picked");

        assertFalse(unit.selectModule(index), "picking it again releases the selection");
        assertTrue(unit.moduleActive(index), "releasing lets every module fire again");
        refillSupply(unit);
        assertTrue(runAndCountShots(unit, 200)[dorsal] > 0, "the module fires again");
    }

    @Test
    void aPickedModuleIsTheOnlyOneThatFires(){
        Unit unit = hullWithTwoModules();
        //the two dorsal Duos sit last in the numbering, after the hull's own fixed weapon
        int base = lastModules(unit, 2);
        int first = moduleMount(unit, base), second = moduleMount(unit, base + 1);

        //nothing picked: every weapon module shoots
        assertNull(unit.selectedModule());
        int[] both = runAndCountShots(unit, 200);
        assertTrue(both[first] > 0 && both[second] > 0, "a hull starts with every module firing");

        assertTrue(unit.selectModule(base), "picking the first dorsal module succeeds");
        assertTrue(unit.moduleSelected(base));
        assertFalse(unit.moduleSelected(base + 1));
        assertTrue(unit.moduleActive(base));
        assertFalse(unit.moduleActive(base + 1), "an unpicked weapon module stays mounted but silent");

        refillSupply(unit);
        int[] shots = runAndCountShots(unit, 200);
        assertTrue(shots[first] > 0, "the picked module keeps firing");
        assertEquals(0, shots[second], "no other module fires while one is picked");
    }

    @Test
    void aPickedModuleStaysMountedAndKeepsTracking(){
        Unit unit = hullWithTwoModules();
        int base = lastModules(unit, 2);
        int first = moduleMount(unit, base), second = moduleMount(unit, base + 1);
        Weapon duo = unit.mounts[second].weapon;
        int mounts = unit.mounts.length;

        assertTrue(unit.selectModule(base));
        assertEquals(mounts, unit.mounts.length, "silencing a module never uninstalls it");
        assertTrue(unit.mounts[second].weapon == duo, "the slot holds the same module");
        assertTrue(unit.weaponDisarmed(duo));

        //the pilot aims somewhere else for a while: the silenced module keeps tracking the aim
        //while the pick keeps shooting, and is still pointing at the aim afterwards
        float rotation = unit.mounts[second].rotation;
        for(int i = 0; i < 60; i++){
            Time.update();
            unit.aim(48 * 8f + 400f, 48 * 8f, true);
            unit.mounts[first].rotate = unit.mounts[second].rotate = true;
            unit.mounts[first].shoot = true;
            unit.applyDisarmed(unit.mounts[second]);
            unit.mounts[first].weapon.update(unit, unit.mounts[first]);
            unit.mounts[second].weapon.update(unit, unit.mounts[second]);
        }
        assertTrue(unit.mounts[second].rotation != rotation, "a silenced module keeps tracking the aim");
        assertFalse(unit.mounts[second].shoot, "it is only the firing that stops");

        int[] shots = runAndCountShots(unit, 200);
        assertEquals(0, shots[second], "and it never fires while it is silenced");
        assertTrue(shots[first] > 0);
    }

    @Test
    void pickingAnotherModuleMovesTheFireControl(){
        Unit unit = hullWithTwoModules();
        int base = lastModules(unit, 2);
        int first = moduleMount(unit, base), second = moduleMount(unit, base + 1);

        assertTrue(unit.selectModule(base));
        assertTrue(unit.selectModule(base + 1), "a second pick moves the selection");

        assertFalse(unit.moduleSelected(base));
        assertTrue(unit.moduleSelected(base + 1));
        assertTrue(unit.selectedModule() == unit.mounts[second].weapon, "the selection follows the pick");

        refillSupply(unit);
        int[] shots = runAndCountShots(unit, 200);
        assertTrue(shots[second] > 0, "the new pick fires");
        assertEquals(0, shots[first], "the dropped pick goes quiet again");
    }

    @Test
    void pickingTheSameModuleAgainLetsEveryModuleFire(){
        Unit unit = hullWithTwoModules();
        int base = lastModules(unit, 2);
        int first = moduleMount(unit, base), second = moduleMount(unit, base + 1);

        assertTrue(unit.selectModule(base));
        assertFalse(unit.selectModule(base), "the same module again releases the selection");
        assertNull(unit.selectedModule(), "no module is picked any more");

        refillSupply(unit);
        int[] shots = runAndCountShots(unit, 200);
        assertTrue(shots[first] > 0 && shots[second] > 0, "every weapon module fires together again");
    }

    @Test
    void removingThePickedModuleLetsTheRestFireAgain(){
        Unit unit = hullWithTwoModules();
        int base = lastModules(unit, 2);
        Weapon picked = unit.mounts[moduleMount(unit, base)].weapon;

        assertTrue(unit.selectModule(base));
        assertTrue(unit.weaponDisarmed(unit.mounts[moduleMount(unit, base + 1)].weapon),
            "the rest of the loadout is silenced");

        assertTrue(unit.removeWeapon(picked));
        assertNull(unit.selectedModule(), "the picked module cannot outlive its weapon");
        assertFalse(unit.weaponDisarmed(unit.mounts[moduleMount(unit, base)].weapon),
            "a hull that lost its pick must not silence itself for a module that is gone");

        int[] shots = runAndCountShots(unit, 200);
        assertTrue(shots[moduleMount(unit, base)] > 0, "the remaining module fires");
    }

    @Test
    void aSupportModuleIsSwitchedNotPicked(){
        Unit unit = hullWithTwoModules();
        assertNotNull(unit.mountSupport(Blocks.mendProjector));
        //support modules are numbered after every weapon module
        int support = weaponModuleCount(unit);

        assertFalse(unit.moduleFires(support), "a support module has nothing to fire");
        assertTrue(unit.moduleFires(support - 1), "a weapon module fires");

        //the same gesture switches the effect, and it never becomes the firing selection: a
        //support module mounts with its effect running, so the first touch switches it off
        assertTrue(unit.moduleActive(support), "a support module starts with its effect running");
        assertFalse(unit.selectModule(support), "touching it switches the effect off");
        assertNull(unit.selectedModule(), "a support module is never the module that fires alone");

        assertTrue(unit.selectModule(support), "touching it again switches the effect on");
        assertTrue(unit.moduleActive(support));

        //and a weapon pick leaves whatever a support module is doing alone
        unit.selectModule(0);
        assertTrue(unit.moduleActive(support), "the weapon selection never touches a support effect");
        assertTrue(unit.moduleSelected(0));
    }

    @Test
    void removingAModuleDropsItsPilotSelection(){
        Unit unit = hullWithModule();
        TurretWeapon duo = (TurretWeapon)unit.mounts[unit.mounts.length - 1].weapon;
        int index = unit.moduleCount() - 1;

        assertTrue(unit.selectModule(index));
        unit.setModuleAuto(index, true);

        assertTrue(unit.removeWeapon(duo));
        assertNull(unit.selectedModule(), "a removed module cannot stay picked");
        assertFalse(unit.weaponAimsSelf(duo), "a removed weapon cannot stay in auto-aim");
        assertFalse(unit.weaponDisarmed(duo), "and it cannot stay disarmed");
    }

    @Test
    void aManualModuleWaitsForThePilotAndAutoAimsOnItsOwn(){
        Unit unit = hullWithModule();
        WeaponMount mount = unit.mounts[unit.mounts.length - 1];
        int index = unit.moduleCount() - 1;

        //no pilot and no controller runs here: nothing aims for the module, which is the
        //manual state - a manual module only fires where something aims it
        assertFalse(unit.moduleAuto(index), "modules start in manual mode");
        for(int i = 0; i < 120; i++){
            Time.update();
            unit.applyDisarmed(mount);
            mount.weapon.update(unit, mount);
        }
        assertEquals(0, mount.totalShots, "a manual module only fires where the pilot aims");
        assertNull(mount.target, "a manual module never searches for a target of its own");

        assertTrue(unit.setModuleAuto(index, true));
        assertTrue(unit.moduleAuto(index));
        for(int i = 0; i < 120; i++){
            Time.update();
            unit.applyDisarmed(mount);
            mount.weapon.update(unit, mount);
        }
        assertTrue(mount.totalShots > 0, "an automatic module picks its own target");
        assertNotNull(mount.target, "an automatic module keeps the enemy it found");
    }

    @Test
    void anAutomaticModuleKeepsItsTargetWhileThePilotAimsElsewhere(){
        Unit unit = hullWithModule();
        int index = unit.moduleCount() - 1;

        //the player's aim() runs every frame in real play: it must not throw away the target
        //an automatic module picked
        unit.setModuleAuto(index, true);
        for(int i = 0; i < 60; i++){
            Time.update();
            unit.aim(48 * 8f + 400f, 48 * 8f, true);
            unit.mounts[unit.mounts.length - 1].weapon.update(unit, unit.mounts[unit.mounts.length - 1]);
        }

        WeaponMount mount = unit.mounts[unit.mounts.length - 1];
        assertNotNull(mount.target, "the automatic module keeps the target it found");
    }

    @Test
    void thePilotSelectionBelongsToTheUnit(){
        Unit first = hullWithModule();
        first.setModuleAuto(first.moduleCount() - 1, true);
        first.selectModule(first.moduleCount() - 1);

        Unit second = UnitTypes.gamma.spawn(Team.sharded, 60 * 8f, 48 * 8f);
        second.mountTurret((Turret)Blocks.duo);
        assertNull(second.selectedModule(),
            "a fresh hull starts with every module firing, whatever the previous hull chose");
        assertFalse(second.weaponAimsSelf(second.mounts[second.mounts.length - 1].weapon),
            "a fresh hull starts manual, whatever the previous hull chose");
    }

    @Test
    void supportModulesAreAutomaticExceptTheLiquidJets(){
        Unit unit = UnitTypes.quad.spawn(Team.sharded, 48 * 8f, 48 * 8f);

        SupportMount repair = unit.mountSupport(Blocks.mendProjector);
        assertNotNull(repair, "quad must accept the repair projector");
        assertTrue(repair.autoTarget, "a support module runs its effect on its own");

        //one module per category: the liquid anchor is a different position of the same hull
        SupportMount liquid = unit.mountSupport(Blocks.wave);
        assertNotNull(liquid, "quad must accept the wave module");
        assertFalse(liquid.autoTarget, "wave and tsunami are piloted manually, like a weapon");

        //toggling the aim mode is a loadout action; it never changes the on/off state
        SupportMount wave = null;
        for(SupportMount mount : unit.supportMounts){
            if(mount.block == Blocks.wave) wave = mount;
        }
        assertNotNull(wave);
        assertTrue(unit.setModuleAuto(unit.moduleCount() - 1, true));
        assertTrue(wave.autoTarget, "the pilot may still hand the jet back to the module");
        assertTrue(wave.active, "changing the aim mode never switches the module off");
    }

    @Test
    void theLiquidJetWaitsForTheHullToFire(){
        Unit unit = UnitTypes.quad.spawn(Team.sharded, 48 * 8f, 48 * 8f);
        Unit enemy = UnitTypes.mono.spawn(Team.crux, unit.x + 60f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount, "quad must accept the wave module");
            assertTrue(mount.ability instanceof LiquidSupportAbility);
            assertFalse(mount.autoTarget, "wave and tsunami are piloted, not automatic");

            LiquidSupportAbility jet = (LiquidSupportAbility)mount.ability;
            float liquid = unit.moduleSupply().liquidAmount;

            //no fire control: the jet waits, and the hull pool is untouched
            for(int i = 0; i < 20; i++){
                Time.delta = 4f;
                jet.update(unit);
                Groups.bullet.update();
            }
            assertTrue(Groups.bullet.size() == 0, "the jet may not spray while the hull is not firing");
            assertEquals(liquid, unit.moduleSupply().liquidAmount, "no spray, no liquid spent");

            //the hull fires: the jet goes out and pays its liquid
            unit.isShooting(true);
            for(int i = 0; i < 20; i++){
                Time.delta = 4f;
                jet.update(unit);
                Groups.bullet.update();
            }
            assertTrue(Groups.bullet.size() > 0, "the jet must spray while the hull fires");
            assertTrue(unit.moduleSupply().liquidAmount < liquid, "the spray pays out of the hull pool");

            //handing the module back switches the effect too, not only the loadout row
            assertTrue(unit.setModuleAuto(unit.moduleCount() - 1, true));
            assertTrue(mount.autoTarget && jet.autoTarget,
                "the effect must follow the mode the pilot chose");
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void indexOutOfRangeIsRefused(){
        Unit unit = hullWithModule();
        assertFalse(unit.moduleActive(-1));
        assertFalse(unit.moduleActive(unit.moduleCount()));
        assertFalse(unit.moduleFires(unit.moduleCount()));
        assertFalse(unit.moduleSelected(unit.moduleCount()));
        assertFalse(unit.moduleFires(-1));
        assertFalse(unit.moduleSelected(-1));
        assertFalse(unit.selectModule(unit.moduleCount()));
        assertFalse(unit.selectModule(-1));
        assertFalse(unit.toggleModule(unit.moduleCount()));
        assertFalse(unit.setModuleAuto(unit.moduleCount(), true));
        assertNull(unit.moduleName(unit.moduleCount()));
    }
}
