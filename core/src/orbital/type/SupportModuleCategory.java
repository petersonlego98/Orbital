package orbital.type;

import arc.util.Nullable;
import orbital.content.StatusEffects;
import orbital.entities.abilities.*;
import orbital.world.Block;

/** Categories of dorsal support modules. A unit may carry at most one block from each category. */
public enum SupportModuleCategory{
    repair,
    overdrive,
    force;

    /** @return the category of a supported block, or null when the block is not a support module. */
    public static @Nullable SupportModuleCategory of(Block block){
        if(block == null || block.name == null) return null;

        return switch(block.name){
            case "mender", "mend-projector" -> repair;
            case "overdrive-projector", "overdrive-dome" -> overdrive;
            case "force-projector" -> force;
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
}
