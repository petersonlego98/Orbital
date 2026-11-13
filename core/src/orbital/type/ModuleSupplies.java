package orbital.type;

import arc.struct.*;
import arc.util.Nullable;
import orbital.Vars;
import orbital.ctype.UnlockableContent;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.*;
import orbital.world.consumers.*;

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
 *   <li><b>Coolant:</b> the same buffer, for a support block that takes a liquid input - the
 *       {@code force-projector} and the {@code repair-turret} coolant, or the
 *       {@code unit-repair-tower}'s required ozone; the block sizes it from its own per-tick
 *       use.</li>
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
     * @return every liquid a module takes from the hull's liquid pool: its ammo liquids when
     * it is a liquid turret, plus any coolant or required liquid of the block behind it.
     *
     * <p>This is the pool-side counterpart of {@link #liquids(Block)}, which stays ammo-only.
     * It is the same rule for a turret module and a support module: whatever liquid the block
     * itself can hold in its buffer (coolant tank, required reagent) belongs to the hull pool,
     * so its liquids are offered in the picker.</p>
     */
    public static Seq<Liquid> supportLiquids(Block block){
        Seq<Liquid> result = liquids(block);
        ConsumeLiquidBase consumer = liquidConsumer(block);
        if(consumer == null || Vars.content == null) return result;

        for(Liquid liquid : Vars.content.liquids()){
            if(consumer.consumes(liquid) && !result.contains(liquid, true)) result.add(liquid);
        }
        return result;
    }

    /**
     * @return the liquid consumer of a support block, or null when it takes no liquid. The
     * first consumer is enough: a block has at most one liquid input or coolant in this
     * system, and a liquid turret's ammo filter is its only one.
     */
    public static @Nullable ConsumeLiquidBase liquidConsumer(Block block){
        return block == null ? null : block.findConsumer(c -> c instanceof ConsumeLiquidBase);
    }

    /** @return the coolant consumer of a block, or null when the block takes no coolant. */
    public static @Nullable ConsumeCoolant coolant(Block block){
        return block == null ? null : block.findConsumer(c -> c instanceof ConsumeCoolant);
    }

    /**
     * @return true when this support block draws this liquid from the hull's pool: as ammo for
     * a liquid turret, or as coolant/a required input for a support block such as the
     * {@code force-projector} and the {@code repair-turret}.
     */
    public static boolean acceptsSupportLiquid(Block block, @Nullable Liquid liquid){
        if(liquid == null) return false;
        if(accepts(block, liquid)) return true;

        ConsumeLiquidBase consumer = liquidConsumer(block);
        return consumer != null && consumer.consumes(liquid);
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
     * @return how much of the liquid pool this block represents, in liquid units: a liquid
     * turret's own {@link Block#liquidCapacity}, or the buffer of a support block that takes
     * coolant (the block sizes it from its coolant use). Blocks that take no liquid add
     * nothing.
     */
    public static float liquidCapacity(Block block){
        if(block instanceof LiquidTurret liquid){
            return liquid.liquidCapacity > 0f ? liquid.liquidCapacity : Math.max(1, liquid.maxAmmo);
        }

        ConsumeLiquidBase consumer = liquidConsumer(block);
        if(consumer != null) return block.liquidCapacity > 0f ? block.liquidCapacity : 1f;
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
        return ammoPerShot((Block)turret);
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
     * @return liquid this block drains per tick, derived from its own liquid ammo: a liquid
     * turret sprays one liquid unit per shot, and {@code ReloadTurret.reload} ticks pass
     * between shots, so the rate is {@code 1 / reload}. A coolant block drains its own
     * coolant amount; blocks that take no liquid drain nothing.
     */
    public static float liquidUsage(Block block){
        if(block instanceof LiquidTurret liquid) return 1f / Math.max(1f, liquid.reload);

        ConsumeLiquidBase consumer = liquidConsumer(block);
        return consumer != null ? consumer.amount : 0f;
    }

    /** @return liquid this turret drains per tick. */
    public static float liquidUsage(Turret turret){
        return liquidUsage((Block)turret);
    }

    /**
     * @return ammo a shot costs, in the block's own units ({@link Turret#ammoPerShot}). Zero
     * for turrets with no item ammo - those run on energy or on liquid and never draw items.
     */
    public static float ammoPerShot(Block block){
        if(block instanceof ItemTurret item) return Math.max(1, item.ammoPerShot);
        return 0f;
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
