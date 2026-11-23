package orbital.game;

import arc.graphics.*;
import orbital.graphics.*;

/**
 * Visual and semantic category of a module left on the ground in the 8x8 battle royale.
 *
 * <p>There is one kind per dorsal support category plus a kind for every removable weapon
 * (turret blocks and native unit weapons mounted as modules). The colour is used for the
 * glowing border of the dropped module and the type label is a bundle key shown above it.</p>
 */
public enum ModuleDropKind{
    repair(Pal.moduleRepair, "support.category.repair"),
    unitRepair(Pal.moduleUnitRepair, "support.category.unitRepair"),
    overdrive(Pal.moduleOverload, "support.category.overdrive"),
    weapon(Pal.moduleWeapon, "module.drop.type.weapon"),
    force(Pal.moduleForce, "support.category.force"),
    tractor(Pal.moduleTractor, "support.category.tractor"),
    pointDefense(Pal.modulePointDefense, "support.category.pointDefense"),
    liquid(Pal.moduleLiquid, "support.category.liquid"),
    transport(Pal.moduleTransport, "support.category.transport"),
    armor(Pal.moduleArmor, "support.category.armor");

    public final Color color;
    public final String typeKey;

    ModuleDropKind(Color color, String typeKey){
        this.color = color;
        this.typeKey = typeKey;
    }

    /** @return the kind that owns a support block, or null when the block is a weapon. */
    public static ModuleDropKind of(orbital.type.SupportModuleCategory category){
        if(category == null) return null;
        return switch(category){
            case repair -> repair;
            case unitRepair -> unitRepair;
            case overdrive -> overdrive;
            case force -> force;
            case tractor -> tractor;
            case pointDefense -> pointDefense;
            case liquid -> liquid;
            case transport -> transport;
            case armor -> armor;
        };
    }
}
