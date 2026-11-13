import arc.*;
import arc.math.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.abilities.*;
import orbital.entities.bullet.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.*;
import orbital.world.blocks.defense.*;
import orbital.world.blocks.defense.turrets.*;
import orbital.world.blocks.distribution.MassDriver;
import orbital.world.blocks.units.*;
import org.junit.jupiter.api.*;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional verification of every dorsal support module: each one must produce the effect
 * its block produces, in real time, through SupportMount.update(). A module that can be
 * mounted but never does anything shows up here as a failure.
 *
 * <p>Units.nearby/nearbyEnemies/closestEnemy read the per-team quadtree, which is rebuilt by
 * state.teams.updateTeamStats() - so every unit and bullet is spawned first and the tree is
 * rebuilt after, exactly like the game does between frames.</p>
 */
public class SupportModuleEffectsTest{

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

    /** a heavy hull whose anchors cover every category */
    private static Unit carrier(){
        Unit unit = UnitTypes.quad.spawn(Team.sharded, 384f, 384f);
        state.teams.updateTeamStats();
        return unit;
    }

    /** a flying ally (mono) at half health, close to the carrier */
    private static Unit damagedAlly(Unit carrier){
        Unit ally = UnitTypes.mono.spawn(Team.sharded, carrier.x + 20f, carrier.y);
        ally.set(carrier.x + 20f, carrier.y);
        ally.health = ally.maxHealth * 0.5f;
        state.teams.updateTeamStats();
        return ally;
    }

    /** a damaged allied building placed a few tiles from the carrier */
    private static Building damagedBuilding(Unit carrier){
        int tx = (int)(carrier.x / tilesize) + 3;
        int ty = (int)(carrier.y / tilesize);
        world.tile(tx, ty).setBlock(Blocks.duo, Team.sharded, 0);
        Building building = world.tile(tx, ty).build;
        building.health = building.maxHealth * 0.5f;
        state.teams.updateTeamStats();
        return building;
    }

    /** ticks one mount for `ticks` through the same path the generated update uses */
    private static void tick(SupportMount mount, Unit unit, float ticks){
        float delta = Time.delta;
        try{
            Time.delta = ticks;
            mount.update(unit);
        }finally{
            Time.delta = delta;
        }
    }

    /**
     * Reports that the hull is firing.
     *
     * <p>Kept for modules whose effect the pilot triggers. Support modules aim by themselves,
     * so this is not needed for {@code wave}/{@code tsunami} - see
     * {@link #liquidModuleFiresWithoutThePilotHoldingFire}, which proves the jet comes out
     * with the hull idle.</p>
     */
    private static void hullFires(Unit unit){
        unit.isShooting(true);
    }

    @Test
    void repairFieldHealsFlyingAlliesOnly(){
        //a dorsal repair module belongs to an air hull: it serves the wing, not the ground
        Unit unit = carrier();
        Unit flyer = damagedAlly(unit);
        Unit grounded = UnitTypes.risso.spawn(Team.sharded, unit.x - 20f, unit.y);
        grounded.health = grounded.maxHealth * 0.5f;

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.mender);
            assertNotNull(mount);

            tick(mount, unit, ((MendProjector)Blocks.mender).reload + 1f);
            assertTrue(flyer.health > flyer.maxHealth * 0.5f, "the flying ally must be healed");
            assertEquals(grounded.maxHealth * 0.5f, grounded.health,
                "a grounded ally must not be healed by the air repair field");
        }finally{
            grounded.remove();
            flyer.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void repairBeamHealsFlyingAlliesOnly(){
        Unit unit = carrier();
        Unit flyer = damagedAlly(unit);
        Unit grounded = UnitTypes.risso.spawn(Team.sharded, unit.x - 20f, unit.y);
        grounded.health = grounded.maxHealth * 0.5f;

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.repairPoint);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairBeamAbility);

            for(int i = 0; i < 60; i++) tick(mount, unit, 1f);
            assertTrue(flyer.health > flyer.maxHealth * 0.5f, "the flying ally must be healed");
            assertEquals(grounded.maxHealth * 0.5f, grounded.health,
                "the repair beam must not spend itself on a grounded ally");
        }finally{
            grounded.remove();
            flyer.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void repairBeamModuleHealsDamagedBuildings(){
        Unit unit = carrier();
        Building building = null;

        try{
            building = damagedBuilding(unit);
            SupportMount mount = unit.mountSupport(Blocks.repairPoint);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairBeamAbility);

            for(int i = 0; i < 60; i++) tick(mount, unit, 1f);
            assertTrue(building.health > building.maxHealth * 0.5f,
                "the repair beam must also patch a damaged allied building");
        }finally{
            if(building != null) building.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void repairFieldModuleHealsDamagedBuildings(){
        Unit unit = carrier();
        Building building = null;

        try{
            building = damagedBuilding(unit);
            SupportMount mount = unit.mountSupport(Blocks.mender);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairFieldAbility);

            tick(mount, unit, ((MendProjector)Blocks.mender).reload + 1f);
            assertTrue(building.health > building.maxHealth * 0.5f,
                "the repair field must also patch a damaged allied building");
        }finally{
            if(building != null) building.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void repairTowerModuleHealsDamagedBuildings(){
        Unit unit = carrier();
        Building building = null;

        try{
            building = damagedBuilding(unit);
            SupportMount mount = unit.mountSupport(Blocks.unitRepairTower);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairFieldAbility);

            for(int i = 0; i < 60; i++) tick(mount, unit, 1f);
            assertTrue(building.health > building.maxHealth * 0.5f,
                "the repair tower must also patch a damaged allied building");
        }finally{
            if(building != null) building.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void repairFieldHealsADamagedAirAlly(){
        Unit unit = carrier();
        Unit ally = damagedAlly(unit);

        try{
            SupportMount mount = unit.mountSupport(Blocks.mender);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairFieldAbility, "mender must build a repair field");
            tick(mount, unit, ((MendProjector)Blocks.mender).reload + 1f);
            assertTrue(ally.health > ally.maxHealth * 0.5f, "mender must heal a damaged airborne ally");
        }finally{
            ally.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void repairProjectorHealsADamagedAirAlly(){
        Unit unit = carrier();
        Unit ally = damagedAlly(unit);

        try{
            SupportMount mount = unit.mountSupport(Blocks.mendProjector);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairFieldAbility);
            tick(mount, unit, ((MendProjector)Blocks.mendProjector).reload + 1f);
            assertTrue(ally.health > ally.maxHealth * 0.5f, "the projector must heal an airborne ally");
        }finally{
            ally.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void tractorModulePullsAnEnemyAirUnit(){
        Unit unit = carrier();
        Unit enemy = UnitTypes.mono.spawn(Team.crux, unit.x + 120f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.parallax);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof TractorAbility);

            TractorBeamTurret parallax = (TractorBeamTurret)Blocks.parallax;
            float before = enemy.dst(unit);
            //the real loop: the ability writes the impulse, but only the units' own velocity
            //update (Groups.unit.update) turns that impulse into moved distance
            for(int i = 0; i < 40; i++){
                Time.delta = 1f;
                Groups.unit.update();
                state.teams.updateTeamStats();
            }
            assertTrue(enemy.dst(unit) < before,
                "the tractor beam must pull the enemy closer; force=" + parallax.force);
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void pointDefenseModuleShootsDownAnEnemyBullet(){
        Unit unit = carrier();
        Unit shooter = UnitTypes.flare.spawn(Team.crux, unit.x, unit.y + 200f);
        Bullet bullet = null;

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.segment);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof PointDefenseAbility);

            PointDefenseTurret segment = (PointDefenseTurret)Blocks.segment;
            //a weak bullet: the module's own damage is enough to break it in one shot
            bullet = new BasicBulletType(2f, 1f){{
                width = 8f;
                height = 8f;
                lifetime = 60f * 60f;
                speed = 0f;
            }}.create(shooter, Team.crux, unit.x + 20f, unit.y, 0f);
            assertNotNull(bullet);
            assertTrue(bullet.isAdded());

            tick(mount, unit, segment.reload + 1f);
            assertFalse(bullet.isAdded(), "the point defence must remove an enemy bullet");
        }finally{
            if(bullet != null) bullet.remove();
            shooter.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.bullet.update();
            Groups.unit.update();
        }
    }

    @Test
    void repairBeamModuleHealsTheClosestAirAlly(){
        Unit unit = carrier();
        Unit ally = damagedAlly(unit);

        try{
            SupportMount mount = unit.mountSupport(Blocks.repairPoint);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairBeamAbility);

            for(int i = 0; i < 60; i++) tick(mount, unit, 1f);
            assertTrue(ally.health > ally.maxHealth * 0.5f, "the repair beam must heal an airborne ally");
        }finally{
            ally.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void unitRepairTowerHealsAirAllies(){
        Unit unit = carrier();
        Unit ally = damagedAlly(unit);

        try{
            SupportMount mount = unit.mountSupport(Blocks.unitRepairTower);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairFieldAbility);

            RepairTower tower = (RepairTower)Blocks.unitRepairTower;
            for(int i = 0; i < 60; i++) tick(mount, unit, 1f);
            assertTrue(ally.health > ally.maxHealth * 0.5f,
                "the repair tower must heal an airborne ally; healAmount=" + tower.healAmount);
        }finally{
            ally.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void liquidModuleShootsAJetAtEnemyAirUnits(){
        Unit unit = carrier();
        Unit enemy = UnitTypes.mono.spawn(Team.crux, unit.x + 60f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof LiquidSupportAbility);

            LiquidTurret wave = (LiquidTurret)Blocks.wave;
            int before = Groups.bullet.size();
            hullFires(unit);
            tick(mount, unit, wave.reload + 1f);

            assertTrue(Groups.bullet.size() > before,
                "the liquid module must spray a jet at an enemy in range");
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.bullet.update();
            Groups.unit.update();
        }
    }

    @Test
    void liquidJetWaitsForThePilotWhileTheSupportHalfRuns(){
        //regression: wave/tsunami were once gated wholesale, so the whole module - jet and
        //support half alike - returned while the hull was not firing. Now only the jet
        //waits for the pilot: extinguishing a burning ally and hindering enemies inside the
        //field run on the module's own cadence, and the jet goes out when the hull fires.
        Unit unit = carrier();
        Unit enemy = UnitTypes.mono.spawn(Team.crux, unit.x + 60f, unit.y);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, unit.x + 20f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);
            //the liquid jets are the piloted exception among the support modules
            assertFalse(mount.autoTarget, "wave and tsunami are piloted, not automatic");

            LiquidTurret wave = (LiquidTurret)Blocks.wave;
            ally.apply(StatusEffects.burning, 60f);
            unit.isShooting(false);
            int before = Groups.bullet.size();

            tick(mount, unit, wave.reload + 1f);

            assertEquals(before, Groups.bullet.size(),
                "the jet may not spray while the hull is not firing");
            assertTrue(ally.getDuration(StatusEffects.burning) <= 0f,
                "the support half must run while the hull idles");

            //the hull fires: the jet goes out
            unit.isShooting(true);
            before = Groups.bullet.size();
            tick(mount, unit, wave.reload + 1f);
            assertTrue(Groups.bullet.size() > before,
                "the jet must spray while the hull fires");
        }finally{
            ally.remove();
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.bullet.update();
            Groups.unit.update();
        }
    }

    @Test
    void liquidModuleSupportsAlliesWhileTheHullIdles(){
        //the extinguishing half must never wait for the fire control: a burning ally stays
        //burning unless the module puts its fire out, hull firing or not. The opposite
        //transition subtracts wet's duration x0.5 from the burn time, so a short burn (60)
        //is fully converted by wave's wet (120) - see liquidModuleExtinguishesABurningAlly.
        Unit unit = carrier();
        Unit ally = UnitTypes.mono.spawn(Team.sharded, unit.x + 20f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);

            ally.apply(StatusEffects.burning, 60f);
            unit.isShooting(false);
            float burningBefore = ally.getDuration(StatusEffects.burning);
            assertTrue(burningBefore > 0f, "the ally must start burning for this test to mean anything");

            tick(mount, unit, ((LiquidTurret)Blocks.wave).reload + 1f);

            assertTrue(ally.getDuration(StatusEffects.burning) <= 0f,
                "the liquid module must extinguish a burning ally while the hull idles");
        }finally{
            ally.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void supportModulesDefaultToAutoTargetLikeTheirBlocks(){
        //every category starts on auto: the module reproduces a block that acts on its own,
        //and manual aim is opt-in from the loadout dialog. The liquid jets are the one
        //exception - wave and tsunami are piloted weapons, so their trigger is the pilot's
        //fire control and their aim is the hull's own (see LiquidSupportAbility).
        //One block per category per hull (the one-per-category rule), so each check runs on
        //its own carrier.
        Block[] blocks = {
            Blocks.mender,            //repair
            Blocks.overdriveProjector, //overdrive
            Blocks.forceProjector,    //force
            Blocks.parallax,          //tractor
            Blocks.segment,           //pointDefense
            Blocks.repairPoint,       //unitRepair
            Blocks.wave,              //liquid
            Blocks.massDriver,        //transport
            Blocks.shieldedWall        //armour (wall plate)
        };

        for(Block block : blocks){
            Unit unit = carrier();
            try{
                SupportMount mount = unit.mountSupport(block);
                if(mount == null) continue; //wall plates mount through mountWall, not anchors
                //the liquid jets are the piloted exception: wave and tsunami fire when the
                //player holds the fire control, and aim where the hull aims
                boolean piloted = block == Blocks.wave || block == Blocks.tsunami;
                assertEquals(!piloted, mount.autoTarget,
                    block.name + (piloted ? " is piloted by the player" : " must aim by itself; a dorsal module acts like its block"));
            }finally{
                if(unit.supportMounts != null){
                    for(SupportMount mount : unit.supportMounts){
                        if(mount != null) unit.removeSupportMount(mount);
                    }
                }
                unit.remove();
                state.teams.updateTeamStats();
                Groups.unit.update();
            }
        }
    }

    @Test
    void turretModulesAimLikeTheirBlock(){
        //regression: the shots now have to work like the block - the turret turns toward the
        //target and the shot leaves the module's own muzzle, not the carrier's centre. A
        //module can only do that if the ability is told where on the hull it is docked.
        Unit unit = UnitTypes.oct.spawn(Team.sharded, 384f, 384f);
        Unit enemy = UnitTypes.mono.spawn(Team.crux, 384f + 60f, 384f);

        try{
            state.teams.updateTeamStats();
            //the jet is piloted: it only turns and sprays while the hull fires, so this test
            //hands that module back to its own targeting - the loadout dialog does the same
            //for a player - and drives the tracking with the hull firing
            //hull rotation 90 => hull angle 0, so the module's dock is exactly its anchor
            unit.rotation = 90f;
            unit.set(384f, 384f);

            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);
            unit.setModuleAuto(unit.moduleCount() - 1, true);
            unit.isShooting(true);
            AimingModule aim = (AimingModule)mount.ability;

            float dockX = aim.moduleX(unit), dockY = aim.moduleY(unit);
            //the enemy straight to the right of the module's dock
            enemy.set(dockX + 50f, dockY);
            state.teams.updateTeamStats();

            float rotationBefore = aim.rotation();
            int created = 0, prev = Groups.bullet.size();
            float nearest = Float.MAX_VALUE;

            for(int i = 0; i < 60; i++){
                Time.delta = 1f;
                unit.update();
                state.teams.updateTeamStats();
                Groups.unit.update();
                Groups.bullet.update();
                if(Groups.bullet.size() > prev) created += Groups.bullet.size() - prev;
                prev = Groups.bullet.size();
                for(var bullet : Groups.bullet){
                    float dx = Mathf.dst(bullet.x, bullet.y, dockX, dockY);
                    if(dx < nearest) nearest = dx;
                }
            }

            //the turret has turned: it started off-axis and now faces its target
            assertNotEquals(0f, aim.rotation(), "the module's turret must be able to rotate");
            float want = Angles.angle(dockX, dockY, enemy.x, enemy.y);
            float got = ModuleDock.worldAngle(unit, aim.rotation());
            float err = Math.min(Math.abs(want - got), Math.abs(Math.abs(want - got) - 360f));
            assertTrue(err < 30f,
                "the turret must track its target; aim error was " + err + " degrees");

            //the jet leaves the module's muzzle, not the hull: every shot starts at the dock
            assertTrue(created > 0, "the module must spray");
            assertTrue(nearest < 20f,
                "the shot must leave from the module's own dock; closest was " + nearest);
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void repairBeamAimsAtWhatItHeals(){
        //the repair beam is pointed at the damaged target it heals - the carrier itself here,
        //since that is the only damaged unit in the field - and the beam follows the target
        Unit unit = UnitTypes.oct.spawn(Team.sharded, 384f, 384f);

        try{
            state.teams.updateTeamStats();
            unit.rotation = 90f;
            unit.set(384f, 384f);
            unit.health = unit.maxHealth * 0.5f;

            SupportMount mount = unit.mountSupport(Blocks.repairPoint);
            assertNotNull(mount);
            AimingModule aim = (AimingModule)mount.ability;
            float dockX = aim.moduleX(unit), dockY = aim.moduleY(unit);

            for(int i = 0; i < 60; i++) tick(mount, unit, 1f);

            assertTrue(unit.health > unit.maxHealth * 0.5f, "the repair beam must heal");
            float want = Angles.angle(dockX, dockY, unit.x, unit.y);
            float got = ModuleDock.worldAngle(unit, aim.rotation());
            float err = Math.min(Math.abs(want - got), Math.abs(Math.abs(want - got) - 360f));
            assertTrue(err < 5f, "the repair turret must aim at the unit it heals; error was " + err);
        }finally{
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void liquidModuleSpendsItsLiquidOnEachSpray(){
        Unit unit = carrier();
        Unit enemy = UnitTypes.mono.spawn(Team.crux, unit.x + 60f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);

            LiquidTurret wave = (LiquidTurret)Blocks.wave;
            float before = unit.moduleSupply().liquidAmount;
            hullFires(unit);
            for(int i = 0; i < 5; i++){
                tick(mount, unit, wave.reload + 1f);
                Groups.bullet.update();
            }
            assertTrue(unit.moduleSupply().liquidAmount < before,
                "the spray must take liquid out of the hull pool");
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.bullet.update();
            Groups.unit.update();
        }
    }

    @Test
    void liquidTankFallsWhileSprayingAndRefillsWhileIdle(){
        //regression: the module draws its liquid tank the way the block does, at the pool's
        //current level, so the player sees the tank drop every time a jet goes out and refill
        //while the module rests. The pool capacity is the block's own liquidCapacity, so the
        //level reads exactly like the block's tank.
        Unit unit = UnitTypes.oct.spawn(Team.sharded, 384f, 384f);
        Unit enemy = UnitTypes.mono.spawn(Team.crux, 384f + 60f, 384f);

        try{
            state.teams.updateTeamStats();
            unit.isShooting(false);
            unit.set(384f, 384f);
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);
            //the jets start piloted, so hand this module back to its own targeting: the tank
            //check needs it to spray with the hull idle, then rest when the target is gone
            unit.setModuleAuto(unit.moduleCount() - 1, true);

            var supply = unit.moduleSupply();
            supply.setResource(Liquids.water);
            float capacity = supply.liquidCapacity;
            float full = supply.liquidAmount;
            assertEquals(1f, full / capacity, 0.0001f, "a fresh module starts with a full tank");

            float lowest = full;
            for(int i = 0; i < 200; i++){
                Time.delta = 1f;
                unit.update();
                state.teams.updateTeamStats();
                Groups.unit.update();
                Groups.bullet.update();
                lowest = Math.min(lowest, supply.liquidAmount);
            }
            assertTrue(lowest < full, "the tank must drop while the jet goes out");

            //with no target the module rests, and the pool recharges on its own
            enemy.remove();
            state.teams.updateTeamStats();
            float empty = supply.liquidAmount;
            for(int i = 0; i < 700; i++){
                Time.delta = 1f;
                unit.update();
                state.teams.updateTeamStats();
            }
            assertTrue(supply.liquidAmount > empty,
                "the tank must refill while the module rests");
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void liquidModuleKeepsSprayingForALongRun(){
        //regression: wave and tsunami drain energy in the block, and when that energy was
        //actually consumed the pool emptied in seconds and the module went dark for good -
        //the spray stopped after ~300 ticks. Energy must never gate a module, so the jet has
        //to keep coming out after a long run.
        Unit unit = carrier();
        Unit enemy = UnitTypes.mono.spawn(Team.crux, unit.x + 60f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);

            LiquidTurret wave = (LiquidTurret)Blocks.wave;
            hullFires(unit);
            for(int i = 0; i < 800; i++){
                tick(mount, unit, wave.reload + 1f);
                Groups.bullet.update();
                if(i == 400) state.teams.updateTeamStats();
            }

            assertTrue(Groups.bullet.size() > 0,
                "the liquid module must still be spraying after a long run");
            //the energy pool, when the block has a power consumer, is never emptied either
            assertTrue(unit.moduleSupply().energyCapacity == 0f || unit.moduleSupply().energy > 0f,
                "energy is a reading, not a bill: the pool must not be emptied");
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.bullet.update();
            Groups.unit.update();
        }
    }

    @Test
    void liquidModuleSlowsEnemyAirUnits(){
        Unit unit = carrier();
        Unit enemy = UnitTypes.mono.spawn(Team.crux, unit.x + 20f, unit.y);

        try{
            state.teams.updateTeamStats();
            SupportMount mount = unit.mountSupport(Blocks.wave);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof LiquidSupportAbility);

            LiquidTurret wave = (LiquidTurret)Blocks.wave;
            hullFires(unit);
            tick(mount, unit, wave.reload + 1f);
            assertTrue(enemy.getDuration(StatusEffects.wet) > 0f,
                "the liquid module must apply the liquid status to an enemy in range");
        }finally{
            enemy.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void massDriverModuleDeliversItems(){
        Unit unit = carrier();
        Building target = null;

        try{
            //a container a few tiles away: the hull's own stores feed the module, which hands
            //them over exactly like the block hands items to a linked target
            world.tile((int)unit.x / 8 + 3, (int)unit.y / 8).setBlock(Blocks.vault, Team.sharded, 0);
            state.teams.updateTeamStats();
            target = world.tile((int)unit.x / 8 + 3, (int)unit.y / 8).build;
            assertNotNull(target);

            SupportMount mount = unit.mountSupport(Blocks.massDriver);
            assertNotNull(mount);

            //the hull's stores feed the module; set after mounting, because mounting recomputes
            unit.moduleSupply().item = Items.copper;
            unit.moduleSupply().ammo = 60f;
            assertTrue(mount.ability instanceof MassDriverAbility);
            tick(mount, unit, ((MassDriver)Blocks.massDriver).reload + 1f);

            assertTrue(target.items.total() > 0, "the mass driver must deliver the hull's stores");
            assertTrue(unit.moduleSupply().ammo < 60f, "the delivery must spend the pool");
        }finally{
            if(target != null) target.remove();
            unit.remove();
            Groups.unit.update();
        }
    }

    @Test
    void overdriveModuleBoostsAirAllies(){
        Unit unit = carrier();
        Unit ally = damagedAlly(unit);

        try{
            SupportMount mount = unit.mountSupport(Blocks.overdriveProjector);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof StatusFieldAbility);

            tick(mount, unit, ((OverdriveProjector)Blocks.overdriveProjector).reload + 1f);
            assertTrue(ally.getDuration(SupportEffects.projectorBoost) > 0f
                || ally.getDuration(StatusEffects.overclock) > 0f,
                "the overdrive projector must boost a nearby airborne ally");
        }finally{
            ally.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }

    @Test
    void forceModuleShieldsTheCarrier(){
        Unit unit = carrier();

        try{
            SupportMount mount = unit.mountSupport(Blocks.forceProjector);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof ForceFieldAbility);
            tick(mount, unit, 1f);

            ForceProjector force = (ForceProjector)Blocks.forceProjector;
            assertTrue(unit.shield > 0f, "the force module must give the carrier a shield; block shield=" + force.shieldHealth);
        }finally{
            unit.remove();
            Groups.unit.update();
        }
    }

    @Test
    void forceModuleFeedsOnCoolantAndRegeneratesFaster(){
        Unit unit = carrier();

        try{
            SupportMount mount = unit.mountSupport(Blocks.forceProjector);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof ForceFieldAbility);
            ForceFieldAbility field = (ForceFieldAbility)mount.ability;

            ModuleSupply supply = unit.moduleSupply();
            assertNotNull(supply.liquid, "the force projector must feed the liquid pool with coolant");
            assertEquals(ModuleSupplies.liquidCapacity(Blocks.forceProjector), supply.liquidCapacity, 0.001f,
                "the projector's own liquid buffer must size the bar");
            assertTrue(ModuleSupplies.acceptsSupportLiquid(Blocks.forceProjector, Liquids.water),
                "water must be accepted as coolant");

            supply.setResource(Liquids.water);
            unit.refreshModuleSupply();
            supply.liquidAmount = supply.liquidCapacity;

            unit.shield = field.scaledMax(unit) * 0.5f;
            float shieldBefore = unit.shield, liquidBefore = supply.liquidAmount;

            tick(mount, unit, 1f);

            assertTrue(supply.liquidAmount < liquidBefore,
                "rebuilding the shield must spend coolant from the hull pool");
            float expected = field.regen * field.coolantMultiplier
                * (1f + (Liquids.water.heatCapacity - 0.4f) * 0.9f);
            assertEquals(expected, unit.shield - shieldBefore, 0.02f,
                "the block's coolant multiplier must speed the shield up");
        }finally{
            unit.remove();
            Groups.unit.update();
        }
    }

    @Test
    void repairTurretModuleFeedsOnCoolantAndHealsFaster(){
        Unit unit = carrier();
        Unit ally = damagedAlly(unit);

        try{
            SupportMount mount = unit.mountSupport(Blocks.repairTurret);
            assertNotNull(mount);
            assertTrue(mount.ability instanceof RepairBeamAbility);
            RepairBeamAbility beam = (RepairBeamAbility)mount.ability;

            ModuleSupply supply = unit.moduleSupply();
            assertNotNull(supply.liquid, "the repair turret must feed the liquid pool with coolant");
            assertEquals(ModuleSupplies.liquidCapacity(Blocks.repairTurret), supply.liquidCapacity, 0.001f,
                "the repair turret's own liquid buffer must size the bar");
            assertTrue(ModuleSupplies.acceptsSupportLiquid(Blocks.repairTurret, Liquids.water),
                "water must be accepted as coolant");

            supply.setResource(Liquids.water);
            unit.refreshModuleSupply();
            supply.liquidAmount = supply.liquidCapacity;

            float healthBefore = ally.health, liquidBefore = supply.liquidAmount;

            tick(mount, unit, 1f);

            assertTrue(supply.liquidAmount < liquidBefore,
                "healing must spend coolant from the hull pool");
            float expected = beam.repairSpeed * (1f + Liquids.water.heatCapacity * beam.coolantMultiplier);
            assertEquals(expected, ally.health - healthBefore, 0.02f,
                "the block's coolant multiplier must boost the repair speed");
        }finally{
            ally.remove();
            unit.remove();
            state.teams.updateTeamStats();
            Groups.unit.update();
        }
    }
}
