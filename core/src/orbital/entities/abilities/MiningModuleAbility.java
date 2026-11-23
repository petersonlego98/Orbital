package orbital.entities.abilities;

import orbital.world.Block;
import orbital.world.blocks.production.*;

/**
 * Dorsal mining drill.
 *
 * <p>Every player hull already mines the hardest ore ({@code applyUniversalMining}), so the
 * drill's tier is not a gate here - it is the measure of how good the module is. A mounted drill
 * multiplies the carrier's mining rate and extends its mining range; the values come from the
 * real block's {@code tier}, exactly the way other support modules copy their block's stats.</p>
 *
 * <p>One module per hull: the category is exclusive, so the bonuses are applied directly by
 * {@code MinerComp} through the live support mounts rather than by a per-tick field.</p>
 */
public class MiningModuleAbility extends Ability{
    /** Multiplier applied to the unit's mining rate; 1 when no drill is mounted. */
    public final float speedMultiplier;
    /** Extra world units added to the unit's mining range. */
    public final float rangeBonus;
    /** Tier of the mounted drill block. */
    public final int tier;

    public MiningModuleAbility(Block block){
        int blockTier = 1;
        if(block instanceof Drill drill){
            blockTier = drill.tier;
        }else if(block instanceof BeamDrill beam){
            blockTier = beam.tier;
        }
        this.tier = Math.max(1, blockTier);
        //tier 2 (mechanical) gives 1.6x speed and +16 range; tier 7 (eruption) gives 3.1x/+56
        this.speedMultiplier = 1f + this.tier * 0.3f;
        this.rangeBonus = this.tier * 8f;
    }
}
