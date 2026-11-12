import arc.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.abilities.*;
import orbital.entities.bullet.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.ui.dialogs.TurretLoadoutDialog;
import orbital.ui.fragments.HudFragment;
import orbital.world.Block;
import orbital.world.blocks.defense.*;
import orbital.world.blocks.defense.turrets.*;
import orbital.world.blocks.distribution.MassDriver;
import orbital.world.blocks.units.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static orbital.Vars.*;

/**
 * Regression tests for the dorsal support module loadout.
 *
 * <p>Covers the placement rules chosen for this feature (one module per category, manual
 * anchors per hull, no overlap, no turret budget impact), the native-ability conflict rule
 * and the gameplay effects driven by each installed module.</p>
 */
public class SupportLoadoutTest{

    /**
     * Resolved lazily: UnitTypes.* is populated by launchApplication(), so a static
     * field initialized at class-load time would still hold null references.
     */
    private static UnitType[] air(){
        return new UnitType[]{
            UnitTypes.mono, UnitTypes.flare, UnitTypes.poly, UnitTypes.mega, UnitTypes.horizon,
            UnitTypes.zenith, UnitTypes.antumbra, UnitTypes.eclipse, UnitTypes.quad, UnitTypes.oct,
            UnitTypes.avert, UnitTypes.obviate, UnitTypes.quell, UnitTypes.disrupt, UnitTypes.alpha,
            UnitTypes.beta, UnitTypes.gamma, UnitTypes.evoke, UnitTypes.incite, UnitTypes.emanate,
            UnitTypes.monoDuo, UnitTypes.flareDuo
        };
    }

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @Test
    void categoriesAreIdentifiedByBlockName(){
        assertEquals(SupportModuleCategory.repair, SupportModuleCategory.of(Blocks.mender));
        assertEquals(SupportModuleCategory.repair, SupportModuleCategory.of(Blocks.mendProjector));
        assertEquals(SupportModuleCategory.overdrive, SupportModuleCategory.of(Blocks.overdriveProjector));
        assertEquals(SupportModuleCategory.overdrive, SupportModuleCategory.of(Blocks.overdriveDome));
        assertEquals(SupportModuleCategory.force, SupportModuleCategory.of(Blocks.forceProjector));
        assertEquals(SupportModuleCategory.tractor, SupportModuleCategory.of(Blocks.parallax));
        assertEquals(SupportModuleCategory.pointDefense, SupportModuleCategory.of(Blocks.segment));
        assertEquals(SupportModuleCategory.unitRepair, SupportModuleCategory.of(Blocks.repairPoint));
        assertEquals(SupportModuleCategory.unitRepair, SupportModuleCategory.of(Blocks.repairTurret));
        assertEquals(SupportModuleCategory.unitRepair, SupportModuleCategory.of(Blocks.unitRepairTower));
        assertEquals(SupportModuleCategory.liquid, SupportModuleCategory.of(Blocks.wave));
        assertEquals(SupportModuleCategory.liquid, SupportModuleCategory.of(Blocks.tsunami));
        assertEquals(SupportModuleCategory.transport, SupportModuleCategory.of(Blocks.massDriver));
        //mender/mendProjector share MendProjector, and both overdrive blocks share OverdriveProjector,
        //so class identity cannot be used to tell categories apart.
        assertNull(SupportModuleCategory.of(Blocks.duo));
        assertNull(SupportModuleCategory.of(null));
    }

    @Test
    void everyTurretSlotAndAnchorFitsItsHull(){
        for(UnitType type : air()){
            for(TurretSlot slot : type.turretSlots){
                assertTrue(DorsalGeometry.fitsHull(type, slot.offsetX, slot.offsetY,
                    DorsalGeometry.footprint(slot.maxSize)),
                    type.name + " slot " + slot.maxSize + " @" + slot.offsetX + "," + slot.offsetY + " is outside the hull");
            }
            for(SupportAnchor anchor : type.supportAnchors){
                assertTrue(DorsalGeometry.fitsHull(type, anchor.offsetX, anchor.offsetY,
                    DorsalGeometry.footprint(anchor.maxSize)),
                    type.name + " " + anchor.category + " anchor is outside the hull");
            }
        }
    }

    /** @return the square side, in world units, that a module of {@code size} actually occupies on this hull. */
    private static float drawnFootprint(UnitType type, int size){
        //headless runs have no atlas: the module sprite is size*pxTile pixels per side, exactly
        //the fallback SupportMount.regionPx() and TurretWeapon.regionPx() use.
        return DorsalGeometry.visualFootprint(type, size, size * TurretSlot.pxTile);
    }

    @Test
    void supportAnchorsNeverOverlapEachOther(){
        for(UnitType type : air()){
            for(int i = 0; i < type.supportAnchors.size; i++){
                SupportAnchor a = type.supportAnchors.get(i);
                for(int j = i + 1; j < type.supportAnchors.size; j++){
                    SupportAnchor b = type.supportAnchors.get(j);
                    //tower-module categories deliberately reuse the calibrated offsets of the
                    //original three anchors: they are alternatives for the same physical position,
                    //and the runtime overlap rule only ever installs one of them at a time
                    if(a.offsetX == b.offsetX && a.offsetY == b.offsetY) continue;
                    assertFalse(DorsalGeometry.overlaps(
                        a.offsetX, a.offsetY, drawnFootprint(type, a.maxSize),
                        b.offsetX, b.offsetY, drawnFootprint(type, b.maxSize)),
                        type.name + " " + a.category + " overlaps " + b.category);
                }
            }
        }
    }

    /** The tower-module anchors must sit exactly on the phase-6 calibrated positions. */
    @Test
    void towerAnchorsReuseTheKnownPositions(){
        UnitType[] hulls = {
            UnitTypes.zenith, UnitTypes.obviate, UnitTypes.quad, UnitTypes.antumbra,
            UnitTypes.disrupt, UnitTypes.eclipse, UnitTypes.oct
        };

        for(UnitType type : hulls){
            java.util.Set<String> known = new java.util.HashSet<>();
            for(SupportAnchor anchor : type.supportAnchors){
                if(anchor.category == SupportModuleCategory.repair
                    || anchor.category == SupportModuleCategory.overdrive
                    || anchor.category == SupportModuleCategory.force){
                    known.add(anchor.offsetX + "," + anchor.offsetY);
                }
            }
            assertFalse(known.isEmpty(), type.name + " has no known support position");

            for(SupportAnchor anchor : type.supportAnchors){
                if(anchor.category == SupportModuleCategory.repair
                    || anchor.category == SupportModuleCategory.overdrive
                    || anchor.category == SupportModuleCategory.force) continue;
                assertTrue(known.contains(anchor.offsetX + "," + anchor.offsetY),
                    type.name + " " + anchor.category + " must reuse a known position");
            }
        }
    }

    @Test
    void everySupportAnchorHasACompatibleTurretSlot(){
        for(UnitType type : air()){
            for(SupportAnchor anchor : type.supportAnchors){
                boolean found = false;
                for(TurretSlot slot : type.turretSlots){
                    if(!DorsalGeometry.overlaps(
                        anchor.offsetX, anchor.offsetY, drawnFootprint(type, anchor.maxSize),
                        slot.offsetX, slot.offsetY, drawnFootprint(type, slot.maxSize))){
                        found = true;
                        break;
                    }
                }
                assertTrue(found, type.name + " has no turret slot free of its " + anchor.category + " anchor");
            }
        }
    }

    /**
     * Phase 6: every dorsal position was recalibrated onto the hull's real sprite, and the
     * module-vs-module separation now uses the drawn size. Two modules must therefore never
     * overlap once drawn, on any of the 22 hulls.
     */
    @Test
    void noTwoDorsalModulesOverlapOnceDrawn(){
        for(UnitType type : air()){
            java.util.List<float[]> points = new java.util.ArrayList<>();
            java.util.List<Integer> sizes = new java.util.ArrayList<>();
            java.util.List<String> names = new java.util.ArrayList<>();
            java.util.List<Boolean> isAnchor = new java.util.ArrayList<>();

            for(TurretSlot slot : type.turretSlots){
                points.add(new float[]{slot.offsetX, slot.offsetY});
                sizes.add(slot.maxSize);
                names.add("slot " + slot.maxSize);
                isAnchor.add(false);
            }
            for(SupportAnchor anchor : type.supportAnchors){
                points.add(new float[]{anchor.offsetX, anchor.offsetY});
                sizes.add(anchor.maxSize);
                names.add(anchor.category.name());
                isAnchor.add(true);
            }

            for(int i = 0; i < points.size(); i++){
                for(int j = i + 1; j < points.size(); j++){
                    //two anchors sharing one calibrated offset are alternative modules for the
                    //same physical position, never two sprites drawn at once
                    if(isAnchor.get(i) && isAnchor.get(j)
                        && points.get(i)[0] == points.get(j)[0]
                        && points.get(i)[1] == points.get(j)[1]) continue;
                    assertFalse(DorsalGeometry.overlaps(
                        points.get(i)[0], points.get(i)[1], drawnFootprint(type, sizes.get(i)),
                        points.get(j)[0], points.get(j)[1], drawnFootprint(type, sizes.get(j))),
                        type.name + ": " + names.get(i) + " overlaps " + names.get(j) + " once drawn");
                }
            }
        }
    }

    @Test
    void oneSupportModulePerCategory(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertNotNull(unit.mountSupport(Blocks.mender));
        assertTrue(unit.hasSupportCategory(SupportModuleCategory.repair));

        //same category, different block: still rejected even though the 2x2 anchor accepts it
        assertFalse(unit.canMountSupport(Blocks.mendProjector), "a second repair block must be rejected");
        assertNull(unit.mountSupport(Blocks.mendProjector));
        assertEquals(1, unit.supportMounts.length);
    }

    @Test
    void removingSupportFreesItsCategory(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        SupportMount mount = unit.mountSupport(Blocks.mender);
        assertNotNull(mount);
        assertFalse(unit.canMountSupport(Blocks.mendProjector));

        assertTrue(unit.removeSupportMount(mount));
        assertTrue(unit.canMountSupport(Blocks.mendProjector));
        assertNotNull(unit.mountSupport(Blocks.mendProjector));
        assertEquals(1, unit.supportMounts.length);
    }

    @Test
    void distinctCategoriesCoexistWithoutOverlap(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertNotNull(unit.mountSupport(Blocks.mender));
        assertNotNull(unit.mountSupport(Blocks.overdriveDome));
        assertNotNull(unit.mountSupport(Blocks.forceProjector));
        assertEquals(3, unit.supportMounts.length);

        for(int i = 0; i < unit.supportMounts.length; i++){
            SupportMount a = unit.supportMounts[i];
            for(int j = i + 1; j < unit.supportMounts.length; j++){
                SupportMount b = unit.supportMounts[j];
                //phase 6: separation uses the drawn footprint, which is what the player sees
                assertFalse(DorsalGeometry.overlaps(
                    a.x, a.y, a.visualWidth(), b.x, b.y, b.visualWidth()),
                    a.category + " overlaps " + b.category);
            }
        }
    }

    @Test
    void hiddenModuleOnlySupportBlocksStayInTheCatalogue(){
        //wave, tsunami, parallax, repair-point and repair-turret are hidden from construction
        //menus by Blocks.hideModuleOnlyTurrets, but the loadout catalogue must still offer
        //them: their only existence is as dorsal modules (same rule as the turret catalogue).
        Block[] blocks = {
            Blocks.parallax, Blocks.segment, Blocks.repairPoint, Blocks.repairTurret,
            Blocks.unitRepairTower, Blocks.wave, Blocks.tsunami, Blocks.massDriver
        };
        UnitType[] capitals = {
            UnitTypes.quad, UnitTypes.antumbra, UnitTypes.disrupt, UnitTypes.eclipse, UnitTypes.oct
        };

        for(Block block : blocks){
            assertNotNull(SupportModuleCategory.of(block), block.name + " must belong to a support category");
            for(UnitType type : capitals){
                assertTrue(TurretLoadoutDialog.supportCatalogEligible(block, type),
                    type.name + " must list " + block.name + " in the support catalogue");
                assertTrue(type.create(Team.sharded).canMountSupport(block),
                    type.name + " must be able to mount " + block.name);
            }
        }

        //they stay hidden as buildable blocks and non-support blocks stay out of the catalogue
        assertTrue(Blocks.wave.isHidden() && Blocks.wave.moduleOnly);
        assertTrue(Blocks.tsunami.isHidden() && Blocks.tsunami.moduleOnly);
        assertTrue(Blocks.repairTurret.isHidden() && Blocks.repairTurret.moduleOnly);
        assertFalse(TurretLoadoutDialog.supportCatalogEligible(Blocks.duo, UnitTypes.quad));

        //zenith's 1x1 unitRepair anchor offers the point, not the 2x2 repair turret
        assertTrue(TurretLoadoutDialog.supportCatalogEligible(Blocks.repairPoint, UnitTypes.zenith));
        assertFalse(TurretLoadoutDialog.supportCatalogEligible(Blocks.repairTurret, UnitTypes.zenith));
    }

    @Test
    void capitalHullsCarryAllThreeCategories(){
        UnitType[] capitals = {
            UnitTypes.quad, UnitTypes.antumbra, UnitTypes.disrupt, UnitTypes.eclipse
        };
        Block[] towerBlocks = {
            Blocks.parallax, Blocks.segment, Blocks.repairPoint, Blocks.repairTurret,
            Blocks.unitRepairTower, Blocks.wave, Blocks.tsunami, Blocks.massDriver
        };

        for(UnitType type : capitals){
            assertEquals(8, type.supportAnchors.size, type.name);

            //every category is individually installable on a fresh hull
            for(Block block : towerBlocks){
                assertNotNull(type.create(Team.sharded).mountSupport(block),
                    type.name + " must accept " + block.name + " on a fresh hull");
            }

            //but the five new categories reuse the three known positions, so only one
            //module per physical position can be installed at a time
            Unit unit = type.create(Team.sharded);
            assertNotNull(unit.mountSupport(Blocks.mender), type.name + " repair");
            assertNotNull(unit.mountSupport(Blocks.overdriveProjector), type.name + " overdrive");
            assertNotNull(unit.mountSupport(Blocks.forceProjector), type.name + " force");
            assertEquals(3, unit.supportMounts.length, type.name);
            assertFalse(unit.canMountSupport(Blocks.parallax),
                type.name + " tractor shares a position that is already occupied");
            assertNull(unit.mountSupport(Blocks.parallax));
        }
    }

    @Test
    void supportMountsDoNotConsumeTurretBudget(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        int slots = unit.type.turretSlots.size;
        float cost = unit.loadoutCost();
        int turrets = unit.mountedTurrets();

        unit.mountSupport(Blocks.mender);
        unit.mountSupport(Blocks.overdriveProjector);
        unit.mountSupport(Blocks.forceProjector);

        assertEquals(slots, unit.type.turretSlots.size);
        assertEquals(cost, unit.loadoutCost(), 0.001f);
        assertEquals(turrets, unit.mountedTurrets());
        assertEquals(cost, unit.type.loadoutCost(), 0.001f);
    }

    @Test
    void unsupportedBlocksAreRejected(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertFalse(unit.canMountSupport(Blocks.duo));
        assertNull(unit.mountSupport(Blocks.duo));
        assertNull(unit.mountSupport(null));
        assertEquals(0, unit.supportMounts.length);
    }

    //region functional effects

    @Test
    void nativeAbilitiesAreFixedAndBlockEquivalentModules(){
        //poly heals natively; oct has both a native shield and a native repair field
        assertTrue(UnitTypes.poly.hasNativeSupport(SupportModuleCategory.repair));
        assertTrue(UnitTypes.oct.hasNativeSupport(SupportModuleCategory.force));
        assertTrue(UnitTypes.oct.hasNativeSupport(SupportModuleCategory.repair));
        assertFalse(UnitTypes.oct.hasNativeSupport(SupportModuleCategory.overdrive));

        //oct has physical anchors for every category, so a rejection here is caused by
        //the native conflict rule, not by missing space
        assertEquals(8, UnitTypes.oct.supportAnchors.size);
        Unit oct = UnitTypes.oct.create(Team.sharded);
        assertFalse(oct.canMountSupport(Blocks.mender));
        assertFalse(oct.canMountSupport(Blocks.mendProjector));
        assertFalse(oct.canMountSupport(Blocks.forceProjector));
        //the new categories have no native conflict on oct and must still mount
        assertTrue(oct.canMountSupport(Blocks.parallax));
        assertTrue(oct.canMountSupport(Blocks.segment));
        assertTrue(oct.canMountSupport(Blocks.repairPoint));
        assertTrue(oct.canMountSupport(Blocks.wave));
        assertTrue(oct.canMountSupport(Blocks.massDriver));
        assertNotNull(oct.mountSupport(Blocks.overdriveProjector), "the free category must still mount");
        assertEquals(1, oct.supportMounts.length);
    }

    @Test
    void supportAbilitiesUseTheRealBlockParameters(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        MendProjector mender = (MendProjector)Blocks.mender;
        OverdriveProjector dome = (OverdriveProjector)Blocks.overdriveDome;
        ForceProjector projector = (ForceProjector)Blocks.forceProjector;

        SupportMount repair = unit.mountSupport(Blocks.mender);
        assertNotNull(repair);
        assertTrue(repair.ability instanceof RepairFieldAbility);
        RepairFieldAbility heal = (RepairFieldAbility)repair.ability;
        assertEquals(0f, heal.amount, 0.001f);
        assertEquals(mender.reload, heal.reload, 0.001f);
        assertEquals(mender.range, heal.range, 0.001f);
        assertEquals(mender.healPercent, heal.healPercent, 0.001f);

        SupportMount boost = unit.mountSupport(Blocks.overdriveDome);
        assertNotNull(boost);
        assertTrue(boost.ability instanceof StatusFieldAbility);
        StatusFieldAbility status = (StatusFieldAbility)boost.ability;
        assertEquals(dome.reload, status.reload, 0.001f);
        assertEquals(dome.reload + 1f, status.duration, 0.001f);
        assertEquals(dome.range, status.range, 0.001f);
        assertNotNull(status.effect);
        assertEquals(dome.speedBoost, status.effect.speedMultiplier, 0.001f);

        SupportMount shieldMount = unit.mountSupport(Blocks.forceProjector);
        assertNotNull(shieldMount);
        assertTrue(shieldMount.ability instanceof ForceFieldAbility);
        ForceFieldAbility shield = (ForceFieldAbility)shieldMount.ability;
        assertEquals(projector.radius, shield.radius, 0.001f);
        assertEquals(projector.shieldHealth, shield.max, 0.001f);
        assertEquals(projector.cooldownNormal, shield.regen, 0.001f);
        assertEquals(projector.sides, shield.sides);
        assertEquals(projector.shieldRotation, shield.rotation, 0.001f);
    }

    @Test
    void projectorAndDomeUseTheirOwnBoostStrength(){
        assertNotSame(SupportEffects.projectorBoost, SupportEffects.domeBoost);
        assertSame(SupportEffects.forBlock(Blocks.overdriveProjector), SupportEffects.projectorBoost);
        assertSame(SupportEffects.forBlock(Blocks.overdriveDome), SupportEffects.domeBoost);
        assertNull(SupportEffects.forBlock(Blocks.mender));
        assertNull(SupportEffects.forBlock(null));

        //each mount refreshes its own effect from the block's current value; a mod JSON patch
        //is allowed to change speedBoost after base content was created
        Unit projectorUnit = UnitTypes.quad.create(Team.sharded);
        SupportMount projectorMount = projectorUnit.mountSupport(Blocks.overdriveProjector);
        assertNotNull(projectorMount);
        assertEquals(((OverdriveProjector)Blocks.overdriveProjector).speedBoost,
            ((StatusFieldAbility)projectorMount.ability).effect.speedMultiplier, 0.001f);

        Unit domeUnit = UnitTypes.quad.create(Team.sharded);
        SupportMount domeMount = domeUnit.mountSupport(Blocks.overdriveDome);
        assertNotNull(domeMount);
        assertEquals(((OverdriveProjector)Blocks.overdriveDome).speedBoost,
            ((StatusFieldAbility)domeMount.ability).effect.speedMultiplier, 0.001f);
    }

    @Test
    void forceModuleCreatesAndReleasesItsShield(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        assertEquals(0f, unit.shield, 0.001f);

        SupportMount mount = unit.mountSupport(Blocks.forceProjector);
        assertNotNull(mount);
        assertTrue(unit.shield > 0f, "mounting must initialize the shield pool");
        assertEquals(((ForceFieldAbility)mount.ability).scaledMax(unit), unit.shield, 0.001f);

        assertTrue(unit.removeSupportMount(mount));
        assertEquals(0f, unit.shield, 0.001f, "removing must release the shared shield field");
    }

    @Test
    void supportUpdateAndDrawDelegateToTheAbility(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        SupportMount mount = unit.mountSupport(Blocks.mender);
        assertNotNull(mount);

        int[] calls = {0, 0};
        mount.ability = new Ability(){
            @Override public void update(Unit u){ calls[0]++; }
            @Override public void draw(Unit u){ calls[1]++; }
        };

        mount.update(unit);
        mount.draw(unit);
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
    }

    @Test
    void repairModuleHealsADamagedAlly(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, 10f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats(); //rebuild the per-team quadtree used by Units.nearby
            ally.health = ally.maxHealth * 0.5f;
            float before = ally.health;

            SupportMount mount = carrier.mountSupport(Blocks.mender);
            assertNotNull(mount);

            Time.delta = ((MendProjector)Blocks.mender).reload + 1f;
            mount.update(carrier);

            assertTrue(ally.health > before, "the repair field must heal a damaged allied unit");
        }finally{
            Time.delta = delta;
            ally.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    @Test
    void overdriveModuleAppliesItsBoostToNearbyAllies(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, 10f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();
            SupportMount mount = carrier.mountSupport(Blocks.overdriveDome);
            assertNotNull(mount);

            Time.delta = ((OverdriveProjector)Blocks.overdriveDome).reload + 1f;
            mount.update(carrier);

            assertTrue(ally.getDuration(SupportEffects.domeBoost) > 0f,
                "the overdrive field must apply its timed status to a nearby ally");
        }finally{
            Time.delta = delta;
            ally.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    //endregion

    //region tower modules: parallax, segment, repair-point, repair-turret, unit-repair-tower, wave, tsunami, mass-driver

    @Test
    void towerSupportModulesUseTheRealBlockParameters(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        SupportMount mount;

        TractorBeamTurret parallax = (TractorBeamTurret)Blocks.parallax;
        mount = unit.mountSupport(Blocks.parallax);
        assertNotNull(mount);
        assertTrue(mount.ability instanceof TractorAbility);
        TractorAbility tractor = (TractorAbility)mount.ability;
        assertEquals(parallax.range, tractor.range, 0.001f);
        assertEquals(parallax.force, tractor.force, 0.001f);
        assertEquals(parallax.scaledForce, tractor.scaledForce, 0.001f);
        assertEquals(parallax.damage, tractor.damage, 0.001f);
        assertTrue(unit.removeSupportMount(mount));

        PointDefenseTurret segment = (PointDefenseTurret)Blocks.segment;
        mount = unit.mountSupport(Blocks.segment);
        assertNotNull(mount);
        assertTrue(mount.ability instanceof PointDefenseAbility);
        PointDefenseAbility point = (PointDefenseAbility)mount.ability;
        assertEquals(segment.range, point.range, 0.001f);
        assertEquals(segment.bulletDamage, point.bulletDamage, 0.001f);
        assertEquals(segment.reload, point.reload, 0.001f);
        assertTrue(unit.removeSupportMount(mount));

        RepairTurret pointBlock = (RepairTurret)Blocks.repairPoint;
        mount = unit.mountSupport(Blocks.repairPoint);
        assertNotNull(mount);
        assertTrue(mount.ability instanceof RepairBeamAbility);
        RepairBeamAbility beam = (RepairBeamAbility)mount.ability;
        assertEquals(pointBlock.repairRadius, beam.range, 0.001f);
        assertEquals(pointBlock.repairSpeed, beam.repairSpeed, 0.001f);
        assertEquals(pointBlock.beamWidth, beam.beamWidth, 0.001f);
        assertTrue(unit.removeSupportMount(mount));

        RepairTurret turretBlock = (RepairTurret)Blocks.repairTurret;
        mount = unit.mountSupport(Blocks.repairTurret);
        assertNotNull(mount);
        RepairBeamAbility bigBeam = (RepairBeamAbility)mount.ability;
        assertEquals(turretBlock.repairRadius, bigBeam.range, 0.001f);
        assertEquals(turretBlock.repairSpeed, bigBeam.repairSpeed, 0.001f);
        assertEquals(turretBlock.beamWidth, bigBeam.beamWidth, 0.001f);
        assertTrue(unit.removeSupportMount(mount));

        RepairTower tower = (RepairTower)Blocks.unitRepairTower;
        mount = unit.mountSupport(Blocks.unitRepairTower);
        assertNotNull(mount);
        assertTrue(mount.ability instanceof RepairFieldAbility);
        RepairFieldAbility field = (RepairFieldAbility)mount.ability;
        assertEquals(tower.healAmount, field.amount, 0.001f);
        assertEquals(1f, field.reload, 0.001f);
        assertEquals(tower.range, field.range, 0.001f);
        assertTrue(unit.removeSupportMount(mount));

        LiquidTurret waveBlock = (LiquidTurret)Blocks.wave;
        mount = unit.mountSupport(Blocks.wave);
        assertNotNull(mount);
        assertTrue(mount.ability instanceof LiquidSupportAbility);
        LiquidSupportAbility wave = (LiquidSupportAbility)mount.ability;
        assertEquals(waveBlock.range, wave.range, 0.001f);
        assertEquals(waveBlock.reload, wave.reload, 0.001f);
        assertTrue(wave.ammoTypes.containsKey(Liquids.water), "wave sprays water by default");
        assertTrue(unit.removeSupportMount(mount));

        LiquidTurret tsunamiBlock = (LiquidTurret)Blocks.tsunami;
        mount = unit.mountSupport(Blocks.tsunami);
        assertNotNull(mount);
        LiquidSupportAbility tsunami = (LiquidSupportAbility)mount.ability;
        assertEquals(tsunamiBlock.range, tsunami.range, 0.001f);
        assertEquals(tsunamiBlock.reload, tsunami.reload, 0.001f);
        assertTrue(unit.removeSupportMount(mount));

        MassDriver driver = (MassDriver)Blocks.massDriver;
        mount = unit.mountSupport(Blocks.massDriver);
        assertNotNull(mount);
        assertTrue(mount.ability instanceof MassDriverAbility);
        MassDriverAbility logistics = (MassDriverAbility)mount.ability;
        assertEquals(driver.range, logistics.range, 0.001f);
        assertEquals(driver.reload, logistics.reload, 0.001f);
        assertEquals(driver.itemCapacity, logistics.launchMax);
    }

    @Test
    void repairBeamModuleHealsTheClosestDamagedAlly(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, 10f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();
            ally.health = ally.maxHealth * 0.5f;
            float before = ally.health;

            SupportMount mount = carrier.mountSupport(Blocks.repairPoint);
            assertNotNull(mount);

            Time.delta = 100f;
            mount.update(carrier);

            assertTrue(ally.health > before, "the repair beam must heal a damaged allied unit");
        }finally{
            Time.delta = delta;
            ally.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    @Test
    void liquidModuleExtinguishesABurningAlly(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, 10f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();
            //the opposite transition subtracts wet's duration x0.5 from the burn time,
            //so a fresh burn (60) is fully converted by wave's wet (120)
            ally.apply(StatusEffects.burning, 60f);
            assertTrue(ally.getDuration(StatusEffects.burning) > 0f);

            SupportMount mount = carrier.mountSupport(Blocks.wave);
            assertNotNull(mount);

            //the module aims by itself like its block; the pilot's fire control is not needed
            carrier.isShooting(true);
            Time.delta = ((LiquidTurret)Blocks.wave).reload + 1f;
            mount.update(carrier);

            //getDuration reads the live status list; hasEffect() misses statuses applied
            //through opposite transitions
            assertFalse(ally.getDuration(StatusEffects.burning) > 0f,
                "the liquid module must extinguish a burning allied unit");
            assertTrue(ally.getDuration(StatusEffects.wet) > 0f, "the liquid module must apply its liquid status");
        }finally{
            Time.delta = delta;
            ally.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    @Test
    void liquidModuleSlowsEnemiesInsideItsField(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit enemy = UnitTypes.flare.spawn(Team.crux, 60f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();

            SupportMount mount = carrier.mountSupport(Blocks.wave);
            assertNotNull(mount);

            //the module aims by itself like its block; the pilot's fire control is not needed
            carrier.isShooting(true);
            Time.delta = ((LiquidTurret)Blocks.wave).reload + 1f;
            mount.update(carrier);

            assertTrue(enemy.hasEffect(StatusEffects.wet), "the liquid module must hinder an enemy in range");
        }finally{
            Time.delta = delta;
            enemy.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    @Test
    void tractorModulePullsAnEnemyAirUnit(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit enemy = UnitTypes.flare.spawn(Team.crux, 100f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();

            SupportMount mount = carrier.mountSupport(Blocks.parallax);
            assertNotNull(mount);

            mount.update(carrier);

            assertTrue(enemy.vel.len() > 0f, "the tractor beam must pull the enemy towards the carrier");
        }finally{
            Time.delta = delta;
            enemy.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    @Test
    void pointDefenseModuleShootsDownABullet(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit shooter = UnitTypes.flare.spawn(Team.crux, 0f, 120f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();

            Bullet bullet = new BasicBulletType(2f, 5f).create(shooter, Team.crux, 80f, 0f, 0f);
            assertNotNull(bullet);
            assertTrue(bullet.isAdded());

            SupportMount mount = carrier.mountSupport(Blocks.segment);
            assertNotNull(mount);

            Time.delta = ((PointDefenseTurret)Blocks.segment).reload + 1f;
            mount.update(carrier);

            assertFalse(bullet.isAdded(), "the point-defense module must destroy an enemy bullet in range");
        }finally{
            Time.delta = delta;
            shooter.remove();
            carrier.remove();
            Groups.bullet.update();
            Groups.unit.update();
        }
    }

    @Test
    void massDriverModuleLaunchesTheHullStoresAndKeepsTheUnitCargo(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, 10f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();
            //the hull's stores feed the module; set after mounting, because mounting recomputes
            SupportMount mount = carrier.mountSupport(Blocks.massDriver);
            assertNotNull(mount);
            carrier.moduleSupply().item = Items.copper;
            carrier.moduleSupply().ammo = 40f;
            carrier.stack.item = Items.lead;
            carrier.stack().amount = 7;

            Time.delta = ((MassDriver)Blocks.massDriver).reload + 1f;
            mount.update(carrier);

            assertTrue(ally.hasItem(), "the mass driver must hand carried items to an allied unit");
            assertEquals(Items.copper, ally.stack().item, "the launched item comes from the pool");
            //the unit's own cargo is never touched by the module
            assertTrue(carrier.hasItem(), "the carrier keeps its own item");
            assertEquals(Items.lead, carrier.stack().item);
            assertEquals(7, carrier.stack().amount, "the carrier's amount must not decrease");

            //and the hull's pool pays for the launch
            assertTrue(carrier.moduleSupply().ammo < 40f, "the launch must spend the pool");
        }finally{
            Time.delta = delta;
            ally.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    //endregion

    //region visual scale (phases 3-4)

    /**
     * Probes each category with a fresh unit so no category conflict from an earlier probe
     * can hide a later one. This is the compatibility list the player sees in the loadout UI.
     */
    private static String compatibility(UnitType type){
        StringBuilder out = new StringBuilder();
        for(SupportModuleCategory category : SupportModuleCategory.values()){
            Block probe = switch(category){
                case repair -> Blocks.mender;
                case overdrive -> Blocks.overdriveProjector;
                case force -> Blocks.forceProjector;
                case tractor -> Blocks.parallax;
                case pointDefense -> Blocks.segment;
                case unitRepair -> Blocks.repairPoint;
                case liquid -> Blocks.wave;
                case transport -> Blocks.massDriver;
                //walls and ramparts are one armour category; the simplest plate probes it
                case armor -> Blocks.copperWall;
            };
            if(type.create(Team.sharded).canMountSupport(probe)){
                if(out.length() > 0) out.append('|');
                out.append(category.name());
            }
        }
        return out.toString();
    }

    @Test
    void visualScalePreservesEveryHullCompatibility(){
        //frozen snapshot, in air() order. The tractor/pointDefense/unitRepair/liquid/transport
        //anchors of the tower-module phase add categories to the hulls that hold their anchors;
        //the projector categories keep the functional-phase compatibility everywhere.
        String[] expected = {
            "mono:", "flare:", "poly:", "mega:", "horizon:repair", "zenith:repair|unitRepair",
            "antumbra:repair|overdrive|force|tractor|pointDefense|unitRepair|liquid|transport",
            "eclipse:repair|overdrive|force|tractor|pointDefense|unitRepair|liquid|transport",
            "quad:repair|overdrive|force|tractor|pointDefense|unitRepair|liquid|transport",
            "oct:overdrive|tractor|pointDefense|unitRepair|liquid|transport", "avert:", "obviate:repair|pointDefense",
            "quell:repair|overdrive", "disrupt:repair|overdrive|force|tractor|pointDefense|unitRepair|liquid|transport",
            "alpha:", "beta:", "gamma:", "evoke:", "incite:", "emanate:repair",
            "mono-duo:", "flare-duo:"
        };

        UnitType[] hulls = air();
        assertEquals(hulls.length, expected.length);
        for(int i = 0; i < hulls.length; i++){
            String[] parts = expected[i].split(":", -1);
            assertEquals(hulls[i].name, parts[0], "snapshot row " + i + " is out of sync with air()");
            assertEquals(parts[1], compatibility(hulls[i]), hulls[i].name + " compatibility changed");
        }
    }

    @Test
    void visualScaleIsDerivedFromMeasuredHullSizes(){
        //values measured from core/assets/sprites/sprites.aatls; the rule is anchored on them
        assertEquals(48f, DorsalVisual.hullPx(UnitTypes.mono), 0.001f);
        assertEquals(48f, DorsalVisual.hullPx(UnitTypes.flare), 0.001f);
        assertEquals(220f, DorsalVisual.hullPx(UnitTypes.quad), 0.001f);
        assertEquals(400f, DorsalVisual.hullPx(UnitTypes.oct), 0.001f);
        assertEquals(DorsalVisual.smallestHullPx, DorsalVisual.hullPx(UnitTypes.mono), 0.001f);
        assertEquals(DorsalVisual.largestHullPx, DorsalVisual.hullPx(UnitTypes.oct), 0.001f);

        //every hull uses the derived rule; no hand-tuned per-hull scale remains
        for(UnitType type : air()){
            assertEquals(1f, type.dorsalVisualTuning, 0.0001f, type.name + " must use the derived scale");
            assertTrue(DorsalVisual.hullPx(type) >= DorsalVisual.smallestHullPx - 0.01f);
            assertTrue(DorsalVisual.hullPx(type) <= DorsalVisual.largestHullPx + 0.01f);
        }

        //the target shrinks as the hull grows, so a small hull does not get a building glued to it
        assertTrue(DorsalVisual.targetFraction(UnitTypes.mono, 1) > DorsalVisual.targetFraction(UnitTypes.oct, 1));
        //a crowded 220px hull shrinks a 3x3 pod more than a 400px hull does
        assertTrue(DorsalGeometry.turretVisualScale(UnitTypes.quad, 3, 96f)
            < DorsalGeometry.turretVisualScale(UnitTypes.oct, 3, 96f));

        java.util.Set<Float> distinct = new java.util.HashSet<>();
        for(UnitType type : air()){
            if(!type.supportAnchors.isEmpty()) distinct.add(DorsalVisual.scale(type, 3, 96f));
        }
        assertTrue(distinct.size() >= 6, "expected a measured per-hull scale curve, got " + distinct);
    }

    @Test
    void everyHullAndModuleSizeStaysInsideTheProportionBand(){
        for(UnitType type : air()){
            float hull = DorsalVisual.hullPx(type);
            for(int size = 1; size <= 5; size++){
                float target = DorsalVisual.targetFraction(type, size);
                assertTrue(target >= DorsalVisual.minWidthFraction - 0.0001f
                    && target <= DorsalVisual.maxWidthFraction + 0.0001f,
                    type.name + " size " + size + " target out of band");

                float px = size * 32f;
                float scale = DorsalVisual.scale(type, size, px);
                assertTrue(scale > 0f && scale <= 1f,
                    type.name + " size " + size + " has an invalid scale");
                assertTrue(px * scale / hull <= DorsalVisual.maxWidthFraction + 0.001f,
                    type.name + " size " + size + " draws wider than the band");
            }
        }
        //degenerate inputs fall back to native size instead of dividing by zero
        assertEquals(1f, DorsalVisual.scale(null, 1, 32f), 0.0001f);
        assertEquals(1f, DorsalVisual.scale(UnitTypes.mono, 1, 0f), 0.0001f);
    }

    @Test
    void turretsAndSupportModulesShareOneRule(){
        for(UnitType type : air()){
            for(int size = 1; size <= 5; size++){
                float px = size * 32f;
                assertEquals(DorsalGeometry.turretVisualScale(type, size, px),
                    DorsalGeometry.supportVisualScale(type, null, size, px), 0.0001f,
                    type.name + " size " + size + " must use one shared rule");
            }
        }
        //the legacy 3-argument helper infers the block size from the sprite and stays consistent
        assertEquals(DorsalGeometry.supportVisualScale(UnitTypes.quad, null, 3, 96f),
            DorsalGeometry.supportVisualScale(UnitTypes.quad, null, 96f), 0.0001f);
    }

    @Test
    void turretModulesShrinkButNativeWeaponsDoNot(){
        Unit mono = UnitTypes.mono.create(Team.sharded);
        TurretWeapon duo = TurretWeapon.of(Blocks.duo);
        float scale = duo.visualScale(mono);
        assertTrue(scale > 0f && scale < 1f, "a 1x1 turret must shrink on a 48px hull");

        Weapon nativeWeapon = new Weapon("native-test");
        assertEquals(1f, nativeWeapon.visualScale(mono), 0.0001f,
            "native hull weapons must keep their original art size");

        //gameplay identity is untouched by the drawing scale
        assertEquals(Blocks.duo.size, duo.turretSize);
        assertEquals(Blocks.duo.size * TurretSlot.pxTile * (Blocks.duo.size * TurretSlot.pxTile), duo.cost(), 0.001f);
        assertEquals(0f, duo.x, 0.001f);
        assertEquals(0f, duo.y, 0.001f);
        assertEquals(-1, duo.slotIndex);
        assertNotNull(duo.bullet);
        assertTrue(duo.regionPx() > 0f);
    }

    @Test
    void runtimeMountedTurretKeepsItsSlotAndFullFootprint(){
        Unit unit = UnitTypes.gamma.create(Team.sharded);
        TurretSlot slot = unit.type.turretSlots.get(0);
        float budgetBefore = unit.loadoutCost();

        TurretWeapon mounted = unit.mountTurret((Turret)Blocks.duo);
        assertNotNull(mounted, "gamma must still accept a 1x1 turret");
        assertTrue(mounted.visualScale(unit) > 0f && mounted.visualScale(unit) <= 1f);
        assertEquals(slot.offsetX, mounted.x, 0.001f);
        assertEquals(slot.offsetY, mounted.y, 0.001f);
        assertEquals(0, mounted.slotIndex);
        assertEquals(DorsalGeometry.footprint(Blocks.duo.size), DorsalGeometry.footprint(mounted.turretSize), 0.001f);
        assertEquals(Blocks.duo.size * TurretSlot.pxTile * (Blocks.duo.size * TurretSlot.pxTile), mounted.cost(), 0.001f);
        assertTrue(unit.loadoutCost() > budgetBefore, "the turret must still consume dorsal budget");
    }

    @Test
    void perHullTuningChangesOnlyDrawing(){
        UnitType quad = UnitTypes.quad;
        float original = quad.dorsalVisualTuning;
        String before = compatibility(quad);
        try{
            float base = DorsalGeometry.turretVisualScale(quad, 3, 96f);
            quad.dorsalVisualTuning = 0.5f;
            float tuned = DorsalGeometry.turretVisualScale(quad, 3, 96f);

            assertTrue(tuned > 0f && tuned < base, "a per-hull tuning knob must be able to shrink further");
            assertEquals(before, compatibility(quad), "tuning must not change the compatibility list");
            assertEquals(8, quad.supportAnchors.size);
            assertEquals(2, quad.turretSlots.size);
        }finally{
            quad.dorsalVisualTuning = original;
        }
    }

    @Test
    void supportSpritesShrinkButKeepFullFootprintAndAnchors(){
        Block[] blocks = {
            Blocks.mender, Blocks.mendProjector, Blocks.overdriveProjector,
            Blocks.overdriveDome, Blocks.forceProjector
        };

        for(UnitType type : air()){
            if(type.supportAnchors.isEmpty()) continue;
            for(SupportAnchor anchor : type.supportAnchors){
                for(Block block : blocks){
                    if(!anchor.accepts(block)) continue;

                    Unit unit = type.create(Team.sharded);
                    SupportMount mount = unit.mountSupport(block);
                    if(mount == null) continue; //native conflict, e.g. oct's built-in force/repair

                    assertEquals(anchor.offsetX, mount.x, 0.001f, type.name + " anchor x moved");
                    assertEquals(anchor.offsetY, mount.y, 0.001f, type.name + " anchor y moved");
                    assertEquals(DorsalGeometry.footprint(block.size), mount.footprint(), 0.001f,
                        type.name + " physical footprint was scaled");
                    assertTrue(mount.visualScale > 0f && mount.visualScale <= 1f,
                        type.name + " " + block.name + " has an invalid visual scale");
                    assertTrue(mount.visualWidthFraction(type) <= DorsalGeometry.supportMaxWidthFraction + 0.001f,
                        type.name + " " + block.name + " sprite is wider than the hull band");
                }
            }
        }
    }

    @Test
    void perAnchorOverrideChangesOnlyTheDrawingScale(){
        SupportAnchor anchor = UnitTypes.quad.supportAnchors.get(0);
        float original = anchor.visualScale;
        //capture the configured placement so the assertion does not depend on hard-coded offsets
        float offsetX = anchor.offsetX, offsetY = anchor.offsetY;
        int maxSize = anchor.maxSize;
        boolean accepts = anchor.accepts(Blocks.mendProjector);
        try{
            float base = DorsalGeometry.supportVisualScale(UnitTypes.quad, anchor, 64f);
            anchor.scaled(0.30f);
            float overridden = DorsalGeometry.supportVisualScale(UnitTypes.quad, anchor, 64f);

            assertTrue(overridden > 0f && overridden < base,
                "a per-anchor override must be able to shrink one module further");
            //placement rules are completely independent of the override
            assertEquals(maxSize, anchor.maxSize);
            assertEquals(accepts, anchor.accepts(Blocks.mendProjector));
            assertEquals(offsetX, anchor.offsetX, 0.001f);
            assertEquals(offsetY, anchor.offsetY, 0.001f);
        }finally{
            anchor.visualScale = original;
        }
    }

    @Test
    void visualScaleDoesNotChangeGameplayEffects(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        SupportMount repair = unit.mountSupport(Blocks.mendProjector);
        assertNotNull(repair);
        assertTrue(repair.visualScale < 1f, "the sprite must actually be smaller on quad");

        RepairFieldAbility heal = (RepairFieldAbility)repair.ability;
        assertEquals(((MendProjector)Blocks.mendProjector).range, heal.range, 0.001f);
        assertEquals(((MendProjector)Blocks.mendProjector).healPercent, heal.healPercent, 0.001f);
        assertEquals(DorsalGeometry.footprint(Blocks.mendProjector.size), repair.footprint(), 0.001f);
        assertEquals(repair.regionPx() * repair.visualScale / DorsalGeometry.pixelsPerWorldUnit(),
            repair.visualWidth(), 0.001f);
    }

    //endregion

    //region HUD active/inactive toggle (phase 5)

    @Test
    void supportStateKeyMatchesTheHudBadge(){
        //the bottom bar reads this key for the badge drawn on top of each support icon
        assertEquals("support.hud.active", HudFragment.supportStateKey(true));
        assertEquals("support.hud.inactive", HudFragment.supportStateKey(false));
    }

    @Test
    void togglingSupportKeepsTheModuleInstalled(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        SupportMount mount = unit.mountSupport(Blocks.mender);
        assertNotNull(mount);
        assertTrue(mount.active, "a freshly installed module must be running");

        mount.setActive(unit, false);
        assertFalse(mount.active);
        assertEquals(1, unit.supportMounts.length);
        assertSame(mount, unit.supportMounts[0], "toggling must not uninstall the module");
        assertTrue(unit.hasSupportCategory(SupportModuleCategory.repair));
        assertFalse(unit.canMountSupport(Blocks.mendProjector), "the category stays occupied while off");

        mount.setActive(unit, true);
        assertTrue(mount.active);
        assertEquals(1, unit.supportMounts.length);
    }

    @Test
    void deactivatedSupportStopsUpdatingAndDrawing(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        SupportMount mount = unit.mountSupport(Blocks.mender);
        assertNotNull(mount);

        int[] calls = {0, 0};
        mount.ability = new Ability(){
            @Override public void update(Unit u){ calls[0]++; }
            @Override public void draw(Unit u){ calls[1]++; }
        };

        mount.update(unit);
        mount.draw(unit);
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);

        mount.setActive(unit, false);
        mount.update(unit);
        mount.draw(unit);
        assertEquals(1, calls[0], "an inactive module must not tick its effect");
        assertEquals(1, calls[1], "an inactive module must not draw its effect");

        //repeated clicks on an unchanged state must not create or destroy effects
        mount.setActive(unit, false);
        assertEquals(1, calls[0]);

        mount.setActive(unit, true);
        mount.update(unit);
        mount.draw(unit);
        assertEquals(2, calls[0]);
        assertEquals(2, calls[1]);
    }

    @Test
    void deactivatedRepairDoesNotHeal(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, 10f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();
            ally.health = ally.maxHealth * 0.5f;
            float before = ally.health;

            SupportMount mount = carrier.mountSupport(Blocks.mender);
            assertNotNull(mount);
            mount.setActive(carrier, false);

            Time.delta = ((MendProjector)Blocks.mender).reload + 1f;
            mount.update(carrier);

            assertEquals(before, ally.health, 0.001f, "a switched-off repair field must not heal");
        }finally{
            Time.delta = delta;
            ally.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    @Test
    void deactivatedOverdriveDoesNotApplyItsStatus(){
        Unit carrier = UnitTypes.quad.spawn(Team.sharded, 0f, 0f);
        Unit ally = UnitTypes.mono.spawn(Team.sharded, 10f, 0f);
        float delta = Time.delta;

        try{
            state.teams.updateTeamStats();
            SupportMount mount = carrier.mountSupport(Blocks.overdriveDome);
            assertNotNull(mount);
            mount.setActive(carrier, false);

            Time.delta = ((OverdriveProjector)Blocks.overdriveDome).reload + 1f;
            mount.update(carrier);

            assertEquals(0f, ally.getDuration(SupportEffects.domeBoost), 0.001f,
                "a switched-off overdrive field must not apply its status");
        }finally{
            Time.delta = delta;
            ally.remove();
            carrier.remove();
            Groups.unit.update();
        }
    }

    @Test
    void deactivatedForceReleasesShieldAndReactivationRestoresIt(){
        Unit unit = UnitTypes.quad.create(Team.sharded);
        SupportMount mount = unit.mountSupport(Blocks.forceProjector);
        assertNotNull(mount);

        float max = ((ForceFieldAbility)mount.ability).scaledMax(unit);
        assertEquals(max, unit.shield, 0.001f);

        mount.setActive(unit, false);
        assertEquals(0f, unit.shield, 0.001f, "switching off must release the shared shield pool");

        mount.setActive(unit, true);
        assertEquals(max, unit.shield, 0.001f, "switching on must reinitialize the pool");

        //removing a deactivated module is still safe and must not touch another owner's state
        mount.setActive(unit, false);
        assertTrue(unit.removeSupportMount(mount));
        assertEquals(0f, unit.shield, 0.001f);
    }

    //endregion
}
