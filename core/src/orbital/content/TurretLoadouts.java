package orbital.content;

import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.Turret;

/**
 * Initial dorsal configuration for the 20 air units.
 *
 * <p>Multipliers and measured areas come from the tower-module design report.
 * Keeping them in one place makes balance changes cheap and avoids touching
 * every unit definition.</p>
 */
public class TurretLoadouts{

    public static void load(){
        //light hulls
        setup(UnitTypes.mono, 1.00f, 2304, 1);
        setup(UnitTypes.flare, 1.00f, 2304, 1);
        setup(UnitTypes.poly, 1.00f, 3192, 1);
        setup(UnitTypes.alpha, 1.00f, 2304, 1);
        setup(UnitTypes.beta, 1.00f, 3024, 1);
        setup(UnitTypes.gamma, 1.00f, 4624, 1, 1);
        setup(UnitTypes.evoke, 1.00f, 3024, 1);
        setup(UnitTypes.incite, 1.00f, 4752, 1, 1);
        setup(UnitTypes.emanate, 1.00f, 6888, 1, 1);
        setup(UnitTypes.horizon, 1.00f, 5184, 1);

        //medium hulls
        setup(UnitTypes.avert, 0.90f, 9216, 2);
        setup(UnitTypes.mega, 1.00f, 10000, 2);
        setup(UnitTypes.zenith, 0.90f, 12544, 2, 1);

        //heavy hulls
        setup(UnitTypes.obviate, 0.95f, 18200, 2, 2);
        setup(UnitTypes.quad, 0.85f, 48400, 4, 2);
        setup(UnitTypes.quell, 0.80f, 26244, 3, 1);

        //capital hulls
        setup(UnitTypes.antumbra, 0.70f, 51840, 4, 2, 2);
        setup(UnitTypes.disrupt, 0.75f, 59049, 4, 2);
        setup(UnitTypes.eclipse, 0.50f, 102720, 5, 5, 2);
        setup(UnitTypes.oct, 0.85f, 160000, 5, 4, 4, 2, 2);

        //demonstration variants: same hulls, with one 1x1 dorsal slot.
        setup(UnitTypes.monoDuo, 1.00f, 2304, 1);
        setup(UnitTypes.flareDuo, 1.00f, 2304, 1);
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
    }

    private static void addRemovableDuo(UnitType type, Block duo){
        if(type == null) return;
        boolean has = type.weapons.contains(w -> w instanceof TurretWeapon tw && tw.turret == duo);
        if(!has){
            type.weapons.add(TurretWeapon.of(duo));
        }
    }

    private static void setup(UnitType type, float multiplier, float area, int... slotSizes){
        if(type == null) return;

        type.loadoutBudgetMultiplier = multiplier;
        type.dorsalAreaOverride = area;
        type.turretSlots.clear();

        for(int size : slotSizes){
            type.turretSlots.add(new TurretSlot(size));
        }
    }
}
