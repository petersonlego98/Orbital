package orbital.type;

import arc.graphics.Color;
import arc.util.Nullable;
import orbital.ctype.UnlockableContent;
import orbital.graphics.Pal;
import orbital.world.consumers.ConsumeLiquidBase;

/**
 * A hull's module supply, as three pools: <b>item ammo</b>, <b>liquid ammo</b> and
 * <b>energy</b>.
 *
 * <p>Each bar sums what every installed module of that kind brings: an item turret adds its
 * own ammo buffer to the item pool, a liquid turret or a liquid support module adds its liquid
 * capacity to the liquid pool, and every power consumer - turret or projector - adds its
 * drain to the energy pool. The capacities are physical buffers, so they are the sum of every
 * mounted module's own buffer regardless of which resource the pool is currently fed with;
 * the chosen resource decides what the pool carries, not how much it holds.</p>
 *
 * <p>Runtime-only. Nothing here is consumed by the game yet: the pools are stored, derived
 * from the installed blocks and read by the loadout menu's bars. Drain on fire, resupply
 * from a core and every other economy concern is a later step.</p>
 */
public final class ModuleSupply{
    /** The item feeding the item-ammo pool, or null while no item-ammo module is installed. */
    public @Nullable Item item;
    /** The liquid feeding the liquid-ammo pool, or null while no liquid module is installed. */
    public @Nullable Liquid liquid;

    /** Item ammo carried, in the item's own ammo units. */
    public float ammo;
    /** Item ammo the hull can carry: the sum of every item-ammo module's buffer. */
    public float ammoCapacity;

    /** Liquid carried, in liquid units. */
    public float liquidAmount;
    /** Liquid the hull can carry: the sum of every liquid module's capacity. */
    public float liquidCapacity;

    /** Energy carried. */
    public float energy;
    /** Energy the hull can carry: every module's power usage, scaled by {@link #energyTicks}. */
    public float energyCapacity;

    /**
     * Ticks of power drain the hull carries. The engine's own power consumer is a rate, not a
     * buffer, so the equivalent buffer has to be defined somewhere; five seconds of drain is
     * the number this pool uses for every module, applied consistently, so the bar stays
     * readable no matter which projector is mounted.
     */
    public static final float energyTicks = 5f * 60f;

    /**
     * Ticks a pool takes to refill from empty while its modules are not drawing on it. There
     * is no supply chain yet - nothing carries items, liquid or power to a dorsal module - so
     * the pools recharge slowly and reach an equilibrium while their modules run. When the
     * real chain exists (resupply from a core, an ammo drop, a power line), this is the number
     * that gets replaced.
     */
    public static final float refillTicks = 600f;

    /** @return true when any installed module feeds the item-ammo pool. */
    public boolean usesAmmo(){
        return ammoCapacity > 0f;
    }

    /** @return true when any installed module feeds the liquid pool. */
    public boolean usesLiquid(){
        return liquidCapacity > 0f;
    }

    /** @return true when any installed module feeds the energy pool. */
    public boolean usesEnergy(){
        return energyCapacity > 0f;
    }

    /**
     * Picks the resource of one ammo pool. An item feeds the item pool, a liquid feeds the
     * liquid pool, and each choice leaves the other pool's resource untouched - the two pools
     * are separate, so a hull may carry an item turret and a liquid turret at once.
     */
    public void setResource(@Nullable UnlockableContent resource){
        if(resource instanceof Item item) this.item = item;
        else if(resource instanceof Liquid liquid) this.liquid = liquid;
    }

    /**
     * @return the colour of the item-ammo bar: the chosen item's own colour, or the neutral
     * grey while nothing feeds that pool.
     */
    public Color ammoColor(){
        return item != null ? item.color : Pal.gray;
    }

    /**
     * @return the colour of the liquid bar: the chosen liquid's bar colour (the one the game
     * already draws in every other liquid bar), or the neutral grey while nothing feeds it.
     */
    public Color liquidColor(){
        if(liquid == null) return Pal.gray;
        return liquid.barColor != null ? liquid.barColor : liquid.color;
    }

    /** @return the energy bar colour: the power colour the rest of the game uses. */
    public Color energyColor(){
        return Pal.power;
    }

    /** @return 0-1 fill of the item-ammo bar. */
    public float ammoFraction(){
        return ammoCapacity <= 0f ? 0f : Math.min(1f, Math.max(0f, ammo / ammoCapacity));
    }

    /** @return 0-1 fill of the liquid bar. */
    public float liquidFraction(){
        return liquidCapacity <= 0f ? 0f : Math.min(1f, Math.max(0f, liquidAmount / liquidCapacity));
    }

    /** @return 0-1 fill of the energy bar. */
    public float energyFraction(){
        return energyCapacity <= 0f ? 0f : Math.min(1f, Math.max(0f, energy / energyCapacity));
    }

    /**
     * Spends one tick of a liquid consumer out of this pool and reports how much of it was
     * satisfied.
     *
     * <p>It is the module-side equivalent of the block's own consumer: the liquid must be one
     * the block accepts, and the cost is the block's per-tick amount. A coolant booster uses
     * the returned fraction to scale its bonus, exactly like the block scales its efficiency
     * by the liquid it could draw this tick.</p>
     *
     * @return 0 when the pool has none of the liquid, 1 when the tick was fully paid, and a
     * fraction when the pool ran dry partway through the tick.
     */
    public float useLiquid(@Nullable ConsumeLiquidBase consumer, float delta){
        if(consumer == null || liquid == null || liquidAmount <= 0f || delta <= 0f) return 0f;
        if(!consumer.consumes(liquid)) return 0f;

        float needed = consumer.amount * delta;
        if(needed <= 0.0001f) return 1f;

        float used = Math.min(needed, liquidAmount);
        liquidAmount -= used;
        return used / needed;
    }

    /**
     * Keeps the carried amounts inside their pools. A capacity that grew tops the pool up -
     * nothing drains it yet, so a mounted module brings its own share and the bar reads full
     * right away - and a capacity that shrank drops the amount with it.
     */
    public void clamp(){
        ammoCapacity = Math.max(0f, ammoCapacity);
        liquidCapacity = Math.max(0f, liquidCapacity);
        energyCapacity = Math.max(0f, energyCapacity);

        ammo = Math.min(Math.max(0f, ammo), ammoCapacity);
        liquidAmount = Math.min(Math.max(0f, liquidAmount), liquidCapacity);
        energy = Math.min(Math.max(0f, energy), energyCapacity);

        //a new module tops its pool up, so the bar is full after every mount
        if(ammo < ammoCapacity) ammo = ammoCapacity;
        if(liquidAmount < liquidCapacity) liquidAmount = liquidCapacity;
        if(energy < energyCapacity) energy = energyCapacity;
    }
}
