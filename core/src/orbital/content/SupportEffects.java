package orbital.content;

import orbital.graphics.*;
import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.*;

/**
 * Status effects used by dorsal overdrive support modules.
 *
 * <p>The two overdrive blocks boost buildings by {@code speedBoost} (1.5x for the projector,
 * 2.5x for the dome). Units are boosted through the status system instead, so these two
 * hidden effects reproduce the block's real multiplier while keeping the effect timed and
 * cancellable. They are created after {@link Blocks#load()} so the initial values are read
 * from the blocks themselves instead of being duplicated here. Because mods may patch
 * {@code speedBoost} afterwards, {@link orbital.entities.units.SupportMount} refreshes the
 * multiplier again when the module is installed.</p>
 *
 * <p>They are appended to the end of the status content list, so existing status IDs and
 * save/sync compatibility are unchanged.</p>
 */
public class SupportEffects{
    public static StatusEffect projectorBoost, domeBoost;

    /** Called from ContentLoader.createBaseContent(), right after Blocks.load(). */
    public static void load(){
        projectorBoost = boost("support-projector-boost", ((OverdriveProjector)Blocks.overdriveProjector).speedBoost);
        domeBoost = boost("support-dome-boost", ((OverdriveProjector)Blocks.overdriveDome).speedBoost);
    }

    /** @return the boost effect matching an overdrive support block, or null. */
    public static StatusEffect forBlock(Block block){
        if(block == null) return null;
        if(block == Blocks.overdriveProjector) return projectorBoost;
        if(block == Blocks.overdriveDome) return domeBoost;
        return null;
    }

    private static StatusEffect boost(String name, float speed){
        return new StatusEffect(name){{
            //internal effects: no database entry, no generated icons, no periodic particle effects
            show = false;
            hideDatabase = true;
            generateIcons = false;
            color = Pal.accent;
            speedMultiplier = speed;
            effect = Fx.none;
            effectChance = 0f;
            permanent = false;
        }};
    }
}
