package orbital.content;

import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.Turret;

/**
 * Initial dorsal configuration for the 20 air units and their demonstration variants.
 *
 * <p>Multipliers and measured areas come from the tower-module design report.
 * Turret slots and support anchors are explicit hull-local placements in world units.
 * Support anchors are separate from the turret budget and enforce one visual module
 * per category: repair, overdrive and force field. The tower-module categories
 * (tractor, point defense, unit repair, liquid, transport) deliberately reuse the
 * calibrated offsets of those three known anchors instead of adding new coordinates:
 * they are alternative modules for the same physical positions, so the runtime
 * overlap rule installs at most one module per position at a time.
 *
 * <p>Module <b>visual size</b> is not configured here anymore. {@link DorsalVisual} derives it
 * from each hull's measured sprite area ({@code dorsalAreaOverride}) and its number of dorsal
 * positions, applying the same rule to removable turrets and support modules. The hull can
 * still be tuned with {@code UnitType.dorsalVisualTuning}, and a single support anchor with
 * {@code SupportAnchor.scaled(float)}. None of that affects {@code maxSize}, offsets,
 * footprints, overlap checks, gameplay ranges or the compatibility list.</p>
 *
 * <p><b>Phase 6 - positions.</b> Offsets are hull-local and were recalibrated against each
 * hull's real sprite: the bounding-box corners of most capitals are transparent, so a position
 * derived only from the measured area can sit off the drawn hull. Every position below keeps
 * its direction relative to the hull centre and stays inside the dorsal silhouette by
 * {@link DorsalGeometry#fitsHull}; module-vs-module separation now uses the <b>drawn</b>
 * footprint ({@link DorsalGeometry#visualFootprint}), which is what the player actually sees,
 * while the silhouette test and the dorsal budget keep using the full block footprint.</p>
 */
public class TurretLoadouts{

    public static void load(){
        //light hulls - a single 1x1 slot; the silhouette has no room for a second footprint
        setup(UnitTypes.mono, 1.00f, 2304,
            turrets(t(1, 0f, 0f)), supports());
        setup(UnitTypes.flare, 1.00f, 2304,
            turrets(t(1, 0f, 0f)), supports());
        setup(UnitTypes.poly, 1.00f, 3192,
            turrets(t(1, 0f, 0f)), supports());
        setup(UnitTypes.alpha, 1.00f, 2304,
            turrets(t(1, 0f, 0f)), supports());
        setup(UnitTypes.beta, 1.00f, 3024,
            turrets(t(1, 0f, 0f)), supports());
        //gamma has a twin fuselage: a centre slot falls in the gap between the two hulls,
        //so each 1x1 slot sits over its own fuselage
        setup(UnitTypes.gamma, 1.00f, 4624,
            turrets(t(1, -2.56f, 0.27f), t(1, 2.90f, 0.34f)), supports());
        setup(UnitTypes.evoke, 1.00f, 3024,
            turrets(t(1, 0f, 0f)), supports());
        //incite is a twin-fuselage hull too; the centre falls in the gap between the hulls
        setup(UnitTypes.incite, 1.00f, 4752,
            turrets(t(1, -2.69f, 1.96f), t(1, 2.91f, 2.38f)), supports());
        //Visual proportions are no longer tuned here: DorsalVisual derives them from each
        //hull's measured sprite area and its number of dorsal positions. The same rule sizes
        //both removable turrets and support modules.
        setup(UnitTypes.emanate, 1.00f, 6888,
            turrets(t(1, -5.5f, -5.5f), t(1, -5.5f, 5.5f)),
            supports(s(SupportModuleCategory.repair, 1, 5.5f, 0f)));
        setup(UnitTypes.horizon, 1.00f, 5184,
            turrets(t(1, -3.25f, 0f)),
            supports(s(SupportModuleCategory.repair, 1, 4f, 0f)));

        //medium hulls - a 2x2 turret nearly fills these silhouettes; anchors only where
        //a fully separate footprint still fits alongside at least one turret slot
        setup(UnitTypes.avert, 0.90f, 9216,
            turrets(t(2, -3.5f, 0f)), supports());
        setup(UnitTypes.mega, 1.00f, 10000,
            turrets(t(2, -4f, 0f)), supports());
        setup(UnitTypes.zenith, 0.90f, 12544,
            turrets(t(2, -2.14f, -2.14f), t(1, 9f, 9f)),
            supports(
                s(SupportModuleCategory.repair, 1, 9f, -9f),
                s(SupportModuleCategory.unitRepair, 1, 9f, -9f)
            ));

        //heavy hulls
        setup(UnitTypes.obviate, 0.95f, 18200,
            turrets(t(2, -8f, -8f), t(2, 8f, 8f)),
            supports(
                s(SupportModuleCategory.repair, 1, 8f, -8f),
                s(SupportModuleCategory.pointDefense, 2, 8f, -8f)
            ));
        setup(UnitTypes.quad, 0.85f, 48400,
            turrets(t(4, -11f, 11f), t(2, 3.02f, -4.11f)),
            supports(
                s(SupportModuleCategory.repair, 2, 15.19f, -0.8f),
                s(SupportModuleCategory.overdrive, 3, -13.06f, -13.06f),
                s(SupportModuleCategory.force, 3, 11.46f, 12.5f),
                s(SupportModuleCategory.unitRepair, 2, 15.19f, -0.8f),
                s(SupportModuleCategory.tractor, 2, -13.06f, -13.06f),
                s(SupportModuleCategory.liquid, 3, 11.46f, 12.5f),
                s(SupportModuleCategory.transport, 3, -13.06f, -13.06f),
                s(SupportModuleCategory.pointDefense, 2, 15.19f, -0.8f)
            ));
        setup(UnitTypes.quell, 0.80f, 26244,
            turrets(t(3, -7.5f, -7.5f), t(1, 8.5f, 8.5f)),
            supports(
                s(SupportModuleCategory.repair, 2, 9.62f, -8.18f),
                s(SupportModuleCategory.overdrive, 2, -8.5f, 10f)
            ));

        //capital hulls
        setup(UnitTypes.antumbra, 0.70f, 51840,
            turrets(t(4, -11.5f, 11.5f), t(2, 0f, -19f), t(2, 16.5f, -17.5f)),
            supports(
                s(SupportModuleCategory.repair, 2, 16.5f, -4f),
                s(SupportModuleCategory.overdrive, 3, -15f, -15f),
                s(SupportModuleCategory.force, 3, 13f, 14f),
                s(SupportModuleCategory.liquid, 3, -15f, -15f),
                s(SupportModuleCategory.transport, 3, 13f, 14f),
                s(SupportModuleCategory.unitRepair, 2, 16.5f, -4f),
                s(SupportModuleCategory.tractor, 2, -15f, -15f),
                s(SupportModuleCategory.pointDefense, 2, 13f, 14f)
            ));
        setup(UnitTypes.disrupt, 0.75f, 59049,
            turrets(t(4, -13.5f, -8f), t(2, 16f, -8f)),
            supports(
                s(SupportModuleCategory.repair, 2, 0.31f, -20.38f),
                s(SupportModuleCategory.overdrive, 3, 12f, 17f),
                s(SupportModuleCategory.force, 3, -16f, 16f),
                s(SupportModuleCategory.liquid, 3, 12f, 17f),
                s(SupportModuleCategory.transport, 3, -16f, 16f),
                s(SupportModuleCategory.unitRepair, 2, 0.31f, -20.38f),
                s(SupportModuleCategory.tractor, 2, 12f, 17f),
                s(SupportModuleCategory.pointDefense, 2, -16f, 16f)
            ));
        setup(UnitTypes.eclipse, 0.50f, 102720,
            turrets(t(5, -19.5f, -10f), t(5, 19.5f, -10f), t(2, -13.79f, 19.79f)),
            supports(
                s(SupportModuleCategory.repair, 2, -19.5f, -27f),
                s(SupportModuleCategory.overdrive, 3, 2.16f, -26.64f),
                s(SupportModuleCategory.force, 3, 16.99f, 16.99f),
                s(SupportModuleCategory.liquid, 3, -19.5f, -27f),
                s(SupportModuleCategory.transport, 3, 2.16f, -26.64f),
                s(SupportModuleCategory.unitRepair, 2, 16.99f, 16.99f),
                s(SupportModuleCategory.tractor, 2, -19.5f, -27f),
                s(SupportModuleCategory.pointDefense, 2, 2.16f, -26.64f)
            ));
        setup(UnitTypes.oct, 0.85f, 160000,
            turrets(
                t(5, -29.5f, -10f), t(4, 26.25f, 0f), t(4, 0f, 33.5f),
                t(2, -14.73f, 14.73f), t(2, 23.92f, -23.92f)
            ),
            supports(
                s(SupportModuleCategory.repair, 2, -21.07f, -27.14f),
                s(SupportModuleCategory.overdrive, 3, 9.45f, -33.08f),
                s(SupportModuleCategory.force, 3, 10.75f, 8.6f),
                s(SupportModuleCategory.liquid, 3, -21.07f, -27.14f),
                s(SupportModuleCategory.transport, 3, 9.45f, -33.08f),
                s(SupportModuleCategory.unitRepair, 2, 10.75f, 8.6f),
                s(SupportModuleCategory.tractor, 2, -21.07f, -27.14f),
                s(SupportModuleCategory.pointDefense, 2, 9.45f, -33.08f)
            ));

        //demonstration variants: same hulls, with one 1x1 dorsal slot.
        setup(UnitTypes.monoDuo, 1.00f, 2304,
            turrets(t(1, 0f, 0f)), supports());
        setup(UnitTypes.flareDuo, 1.00f, 2304,
            turrets(t(1, 0f, 0f)), supports());
    }

    /**
     * Re-applies the default removable dorsal turrets (Duo) to the base hulls.
     *
     * <p>Must be called AFTER all content - including mod overrides and JSON patches -
     * has been loaded, so mods that replace a unit's {@code weapons} list cannot erase
     * the removable module. Idempotent: a unit that already carries this turret is left
     * untouched, so it is safe to invoke on every content (re)initialization.</p>
     */
    public static void applyRemovableTurrets(Block duo){
        if(duo == null) return;

        addRemovableDuo(UnitTypes.mono, duo);
        addRemovableDuo(UnitTypes.flare, duo);
        addRemovableDuo(UnitTypes.monoDuo, duo);
        addRemovableDuo(UnitTypes.flareDuo, duo);

        assignStaticTurretAnchors(UnitTypes.mono);
        assignStaticTurretAnchors(UnitTypes.flare);
        assignStaticTurretAnchors(UnitTypes.monoDuo);
        assignStaticTurretAnchors(UnitTypes.flareDuo);
    }

    private static void addRemovableDuo(UnitType type, Block duo){
        if(type == null) return;
        boolean has = type.weapons.contains(w -> w instanceof TurretWeapon tw && tw.turret == duo);
        if(!has){
            type.weapons.add(TurretWeapon.of(duo));
        }
    }

    /** Assigns content-defined removable turrets to their first compatible, non-overlapping slot. */
    private static void assignStaticTurretAnchors(UnitType type){
        if(type == null) return;

        for(Weapon weapon : type.weapons){
            if(!(weapon instanceof TurretWeapon turretWeapon) || !turretWeapon.removable || turretWeapon.slotIndex >= 0) continue;

            float footprint = DorsalGeometry.footprint(turretWeapon.turretSize);
            for(int i = 0; i < type.turretSlots.size; i++){
                TurretSlot slot = type.turretSlots.get(i);
                if(!slot.accepts(turretWeapon.turret)) continue;
                if(!DorsalGeometry.fitsHull(type, slot.offsetX, slot.offsetY, footprint)) continue;
                if(overlapsAssignedTurrets(type, weapon, slot.offsetX, slot.offsetY, footprint)) continue;

                turretWeapon.x = slot.offsetX;
                turretWeapon.y = slot.offsetY;
                turretWeapon.slotIndex = i;
                break;
            }
        }
    }

    private static boolean overlapsAssignedTurrets(UnitType type, Weapon candidate, float x, float y, float footprint){
        for(Weapon weapon : type.weapons){
            if(weapon == candidate || !(weapon instanceof TurretWeapon turretWeapon) || turretWeapon.slotIndex < 0) continue;
            if(DorsalGeometry.overlaps(x, y, footprint,
                turretWeapon.x, turretWeapon.y, DorsalGeometry.footprint(turretWeapon.turretSize))) return true;
        }
        return false;
    }

    private static void setup(UnitType type, float multiplier, float area, TurretSlot[] slots, SupportAnchor[] anchors){
        if(type == null) return;

        type.loadoutBudgetMultiplier = multiplier;
        type.dorsalAreaOverride = area;
        //DorsalVisual reads the measured area and the slot/anchor counts below to derive the
        //drawing scale. Nothing here changes geometry, budget or compatibility.
        type.turretSlots.clear();
        type.supportAnchors.clear();

        for(TurretSlot slot : slots) type.turretSlots.add(slot);
        for(SupportAnchor anchor : anchors) type.supportAnchors.add(anchor);
    }

    private static TurretSlot t(int maxSize, float offsetX, float offsetY){
        return new TurretSlot(maxSize, offsetX, offsetY);
    }

    private static TurretSlot[] turrets(TurretSlot... slots){
        return slots;
    }

    private static SupportAnchor s(SupportModuleCategory category, int maxSize, float offsetX, float offsetY){
        return new SupportAnchor(category, maxSize, offsetX, offsetY);
    }

    private static SupportAnchor[] supports(SupportAnchor... anchors){
        return anchors;
    }
}
