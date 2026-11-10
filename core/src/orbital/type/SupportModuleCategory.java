package orbital.type;

import arc.util.Nullable;
import orbital.content.StatusEffects;
import orbital.entities.abilities.*;
import orbital.world.Block;

/** Categories of dorsal support modules. A unit may carry at most one block from each category. */
public enum SupportModuleCategory{
    repair,
    overdrive,
    force,
    /** pulls enemy air units with a tractor beam ({@code parallax}). */
    tractor,
    /** shoots down enemy bullets ({@code segment}). */
    pointDefense,
    /** heals allied units directly ({@code repair-point}, {@code repair-turret}, {@code unit-repair-tower}). */
    unitRepair,
    /** liquid support: extinguishes burning allies and applies the liquid's status to enemies ({@code wave}, {@code tsunami}). */
    liquid,
    /** logistics: launches items to allied buildings or units ({@code mass-driver}). */
    transport,
    /**
     * Hull armour plates: every wall and rampart ({@code Wall} blocks of the defense category).
     * They are one single category on purpose - the player may carry any number of wall
     * modules, they simply compete with the turrets for dorsal space and area.
     */
    armor;

    /** @return the category of a supported block, or null when the block is not a support module. */
    public static @Nullable SupportModuleCategory of(Block block){
        if(block == null || block.name == null) return null;

        return switch(block.name){
            case "mender", "mend-projector" -> repair;
            case "overdrive-projector", "overdrive-dome" -> overdrive;
            case "force-projector" -> force;
            case "parallax" -> tractor;
            case "segment" -> pointDefense;
            case "repair-point", "repair-turret", "unit-repair-tower" -> unitRepair;
            case "wave", "tsunami" -> liquid;
            case "mass-driver" -> transport;
            //walls and ramparts: one armour category - only the large (2x2) plates are modules,
            //see WallModuleAbility. Small (1x1) walls stay regular buildable blocks.
            case "copper-wall-large",
                 "titanium-wall-large",
                 "plastanium-wall-large",
                 "thorium-wall-large",
                 "phase-wall-large",
                 "surge-wall-large",
                 "scrap-wall-large",
                 "beryllium-wall-large",
                 "tungsten-wall-large",
                 "carbide-wall-large",
                 "reinforced-surge-wall-large",
                 "shielded-wall" -> armor;
            default -> null;
        };
    }

    /**
     * @return the category provided by a hull's built-in ability, or null when the ability
     * has no support-module equivalent.
     *
     * <p>Native abilities block installation of an equivalent module, so two implementations
     * of the same effect never fight over shared unit state (for example {@code unit.shield}).</p>
     */
    public static @Nullable SupportModuleCategory ofAbility(Ability ability){
        if(ability == null) return null;

        //heals allied units in an area
        if(ability instanceof RepairFieldAbility) return repair;

        //boost field for allied units; suppression/energy fields are different mechanics
        if(ability instanceof StatusFieldAbility status &&
            (status.effect == StatusEffects.overdrive || status.effect == StatusEffects.overclock)) return overdrive;

        //bullet-absorbing shields, including directional arcs
        if(ability instanceof ForceFieldAbility || ability instanceof ShieldArcAbility) return force;

        return null;
    }

    public static boolean isSupport(Block block){
        return of(block) != null;
    }

    /**
     * @return true when this block is a wall or rampart, i.e. a module of the single
     * {@link #armor} category.
     *
     * <p>Wall modules are not installed on support anchors: they take turret space and dorsal
     * area (see {@code WeaponsComp.mountWall}), so every anchor, exclusivity and catalogue check
     * must branch on this predicate instead of treating them as ordinary support modules.</p>
     */
    public static boolean isWallModule(Block block){
        return of(block) == armor;
    }
}
