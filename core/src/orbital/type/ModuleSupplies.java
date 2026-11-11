package orbital.type;

import arc.struct.*;
import arc.util.Nullable;
import orbital.ctype.UnlockableContent;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.*;

/**
 * Reads the supply a dorsal module demands straight from the block it came from.
 *
 * <p>Everything here is derived from parameters the block already declares, so a modded
 * turret - or a balance tweak to a vanilla one - shows up in the pool without any new number
 * being written down:</p>
 *
 * <ul>
 *   <li><b>Item ammo:</b> the turret's own {@link Turret#maxAmmo} ammo units, which is the
 *       buffer the block on the ground carries.</li>
 *   <li><b>Liquid ammo:</b> the block's own {@link Block#liquidCapacity}, the buffer a liquid
 *       turret drains its spray from.</li>
 *   <li><b>Energy:</b> the block's power usage (per tick) times
 *       {@link ModuleSupply#energyTicks}, which is the only number in this system that is not
 *       a block parameter - the engine's power consumer is a rate, not a buffer.</li>
 * </ul>
 */
public final class ModuleSupplies{
    private ModuleSupplies(){}

    /** @return true when this block feeds the unified ammo/liquid pool (item or liquid ammo). */
    public static boolean usesAmmoPool(Block block){
        return block instanceof ItemTurret || block instanceof LiquidTurret;
    }

    /** @return true when this turret feeds the unified ammo/liquid pool. */
    public static boolean usesAmmoPool(Turret turret){
        return usesAmmoPool((Block)turret);
    }

    /**
     * @return true when this block accepts this resource as ammo. Power turrets accept nothing
     * - they run on energy - so they are compatible with any loadout, which is what keeps a
     * hull's single pool consistent.
     */
    public static boolean accepts(Block block, @Nullable UnlockableContent resource){
        if(resource instanceof Item item) return block instanceof ItemTurret i && i.ammoTypes.containsKey(item);
        if(resource instanceof Liquid liquid) return block instanceof LiquidTurret l && l.ammoTypes.containsKey(liquid);
        return false;
    }

    /** @return true when this turret accepts this resource as ammo. */
    public static boolean accepts(Turret turret, @Nullable UnlockableContent resource){
        return accepts((Block)turret, resource);
    }

    /** @return every item this block accepts as ammo, in content order. */
    public static Seq<Item> items(Block block){
        Seq<Item> result = new Seq<>(Item.class);
        if(block instanceof ItemTurret itemTurret){
            for(Item item : itemTurret.ammoTypes.keys()) result.add(item);
        }
        return result;
    }

    /** @return every liquid this block accepts as ammo, in content order. */
    public static Seq<Liquid> liquids(Block block){
        Seq<Liquid> result = new Seq<>(Liquid.class);
        if(block instanceof LiquidTurret liquidTurret){
            for(Liquid liquid : liquidTurret.ammoTypes.keys()) result.add(liquid);
        }
        return result;
    }

    /** @return every item this turret accepts as ammo, in content order. */
    public static Seq<Item> items(Turret turret){
        return items((Block)turret);
    }

    /** @return every liquid this turret accepts as ammo, in content order. */
    public static Seq<Liquid> liquids(Turret turret){
        return liquids((Block)turret);
    }

    /**
     * @return how much of the item-ammo pool this block represents, in the block's own ammo
     * units ({@link Turret#maxAmmo}). Blocks without item ammo add nothing.
     */
    public static float ammoCapacity(Block block){
        if(block instanceof ItemTurret item) return Math.max(1, item.maxAmmo);
        return 0f;
    }

    /** @return how much of the item-ammo pool this turret represents. */
    public static float ammoCapacity(Turret turret){
        return ammoCapacity((Block)turret);
    }

    /**
     * @return how much of the liquid pool this block represents, in liquid units: the block's
     * own {@link Block#liquidCapacity}, which is what a liquid turret drains its spray from.
     * Blocks that are not liquid turrets add nothing.
     */
    public static float liquidCapacity(Block block){
        if(block instanceof LiquidTurret liquid){
            return liquid.liquidCapacity > 0f ? liquid.liquidCapacity : Math.max(1, liquid.maxAmmo);
        }
        return 0f;
    }

    /** @return how much of the liquid pool this turret represents. */
    public static float liquidCapacity(Turret turret){
        return liquidCapacity((Block)turret);
    }

    /**
     * @return ammo spent per shot, in the block's own units. Zero for turrets with no ammo.
     */
    public static float ammoPerShot(Turret turret){
        return usesAmmoPool(turret) ? Math.max(1, turret.ammoPerShot) : 0f;
    }

    /** @return power this block drains per tick, or 0 when it runs on no power. */
    public static float powerUsage(Block block){
        return block != null && block.consPower != null ? block.consPower.usage : 0f;
    }

    /** @return power this turret drains per tick, or 0 when it runs on no power. */
    public static float powerUsage(Turret turret){
        return powerUsage((Block)turret);
    }

    /**
     * @return energy buffer this block represents on a hull: its drain rate for
     * {@link ModuleSupply#energyTicks} ticks.
     */
    public static float energyCapacity(Block block){
        return powerUsage(block) * ModuleSupply.energyTicks;
    }

    /** @return energy buffer this turret represents on a hull. */
    public static float energyCapacity(Turret turret){
        return energyCapacity((Block)turret);
    }
}
