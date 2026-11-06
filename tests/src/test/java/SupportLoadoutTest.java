import arc.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.abilities.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.ui.fragments.HudFragment;
import orbital.world.Block;
import orbital.world.blocks.defense.*;
import orbital.world.blocks.defense.turrets.*;
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

    @Test
    void supportAnchorsNeverOverlapEachOther(){
        for(UnitType type : air()){
            for(int i = 0; i < type.supportAnchors.size; i++){
                SupportAnchor a = type.supportAnchors.get(i);
                for(int j = i + 1; j < type.supportAnchors.size; j++){
                    SupportAnchor b = type.supportAnchors.get(j);
                    assertFalse(DorsalGeometry.overlaps(
                        a.offsetX, a.offsetY, DorsalGeometry.footprint(a.maxSize),
                        b.offsetX, b.offsetY, DorsalGeometry.footprint(b.maxSize)),
                        type.name + " " + a.category + " overlaps " + b.category);
                }
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
                        anchor.offsetX, anchor.offsetY, DorsalGeometry.footprint(anchor.maxSize),
                        slot.offsetX, slot.offsetY, DorsalGeometry.footprint(slot.maxSize))){
                        found = true;
                        break;
                    }
                }
                assertTrue(found, type.name + " has no turret slot free of its " + anchor.category + " anchor");
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
                assertFalse(DorsalGeometry.overlaps(
                    a.x, a.y, a.footprint(), b.x, b.y, b.footprint()),
                    a.category + " overlaps " + b.category);
            }
        }
    }

    @Test
    void capitalHullsCarryAllThreeCategories(){
        UnitType[] capitals = {
            UnitTypes.quad, UnitTypes.antumbra, UnitTypes.disrupt, UnitTypes.eclipse
        };

        for(UnitType type : capitals){
            assertEquals(3, type.supportAnchors.size, type.name);
            Unit unit = type.create(Team.sharded);
            assertNotNull(unit.mountSupport(Blocks.mender), type.name + " repair");
            assertNotNull(unit.mountSupport(Blocks.overdriveProjector), type.name + " overdrive");
            assertNotNull(unit.mountSupport(Blocks.forceProjector), type.name + " force");
            assertEquals(3, unit.supportMounts.length, type.name);
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

        //oct has physical anchors for all three categories, so a rejection here is caused by
        //the native conflict rule, not by missing space
        assertEquals(3, UnitTypes.oct.supportAnchors.size);
        Unit oct = UnitTypes.oct.create(Team.sharded);
        assertFalse(oct.canMountSupport(Blocks.mender));
        assertFalse(oct.canMountSupport(Blocks.mendProjector));
        assertFalse(oct.canMountSupport(Blocks.forceProjector));
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
        //frozen snapshot from the functional phase, in air() order. Phase 3 changes drawing only:
        //not one hull may gain or lose a category.
        String[] expected = {
            "mono:", "flare:", "poly:", "mega:", "horizon:repair", "zenith:repair",
            "antumbra:repair|overdrive|force", "eclipse:repair|overdrive|force",
            "quad:repair|overdrive|force", "oct:overdrive", "avert:", "obviate:repair",
            "quell:repair|overdrive", "disrupt:repair|overdrive|force", "alpha:", "beta:",
            "gamma:", "evoke:", "incite:", "emanate:repair", "mono-duo:", "flare-duo:"
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
            assertEquals(3, quad.supportAnchors.size);
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
        try{
            float base = DorsalGeometry.supportVisualScale(UnitTypes.quad, anchor, 64f);
            anchor.scaled(0.30f);
            float overridden = DorsalGeometry.supportVisualScale(UnitTypes.quad, anchor, 64f);

            assertTrue(overridden > 0f && overridden < base,
                "a per-anchor override must be able to shrink one module further");
            //placement rules are completely independent of the override
            assertEquals(2, anchor.maxSize);
            assertTrue(anchor.accepts(Blocks.mendProjector));
            assertEquals(15f, anchor.offsetX, 0.001f);
            assertEquals(-15f, anchor.offsetY, 0.001f);
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
