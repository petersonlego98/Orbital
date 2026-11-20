package orbital.game;

import arc.*;
import arc.math.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.*;

import static orbital.Vars.*;

/** Shared rules for per-building levels: bounds, stat curves and resource upgrades. */
public final class BuildingLevels{
    public static final int maxLevel = 100;

    /** Baseline seconds for a level evolution; higher levels take a little longer. */
    public static final float baseUpgradeSeconds = 3f;
    public static final float perLevelSeconds = 0.5f;

    private BuildingLevels(){
    }

    /** @return how long evolving to {@code nextLevel} takes, in seconds. */
    public static float evolutionDuration(int nextLevel){
        return baseUpgradeSeconds + Math.max(0, nextLevel - 1) * perLevelSeconds;
    }

    public static int clamp(int level){
        return Mathf.clamp(level, 1, maxLevel);
    }

    /** Health grows faster than combat output so leveled buildings survive longer fights. */
    public static float healthMultiplier(int level){
        return 1f + (clamp(level) - 1) * 0.05f;
    }

    /** Production, crafting and turret reload speed. */
    public static float speedMultiplier(int level){
        return 1f + (clamp(level) - 1) * 0.02f;
    }

    /** Damage dealt by bullets fired from the building. */
    public static float damageMultiplier(int level){
        return 1f + (clamp(level) - 1) * 0.03f;
    }

    /** Turret targeting range. */
    public static float rangeMultiplier(int level){
        return 1f + (clamp(level) - 1) * 0.01f;
    }

    /** Item and liquid capacity. */
    public static float capacityMultiplier(int level){
        return 1f + (clamp(level) - 1) * 0.01f;
    }

    /** @return the resources needed to reach {@code nextLevel}, honoring the rules' build cost. */
    public static ItemStack[] upgradeCost(Block block, int nextLevel){
        if(block == null || nextLevel <= 1 || nextLevel > maxLevel) return new ItemStack[0];

        float scale = 1f + (nextLevel - 1) * 0.35f;
        ItemStack[] cost = new ItemStack[block.requirements.length];
        for(int i = 0; i < cost.length; i++){
            ItemStack requirement = block.requirements[i];
            cost[i] = new ItemStack(requirement.item,
                Mathf.round(requirement.amount * scale * state.rules.buildCostMultiplier));
        }
        return cost;
    }

    /** @return whether the team's core can pay for the building's next level. */
    public static boolean canUpgrade(Building build, Team team){
        if(build == null || build.level >= maxLevel || team == null) return false;
        if(build.evolving) return false; //one evolution at a time
        Building core = team.core();
        return core != null && core.items.has(upgradeCost(build.block, build.level + 1), 1f);
    }

    /**
     * Pays the upgrade from the team core and starts a timed evolution. The level itself is applied
     * when the building's evolution timer completes.
     */
    public static boolean upgrade(Building build, Team team){
        if(!canUpgrade(build, team)) return false;

        ItemStack[] cost = upgradeCost(build.block, build.level + 1);
        Building core = team.core();
        for(ItemStack stack : cost){
            core.items.remove(stack.item, stack.amount);
        }
        int target = build.level + 1;
        build.startEvolution(target, evolutionDuration(target) * 60f);
        return true;
    }

    /** @return a short localized list of the next upgrade's cost, for the configuration panel. */
    public static String costString(Building build){
        if(build == null || build.level >= maxLevel) return Core.bundle.get("building.level.max");

        ItemStack[] cost = upgradeCost(build.block, build.level + 1);
        if(cost.length == 0) return Core.bundle.get("building.level.free");

        StringBuilder result = new StringBuilder();
        for(int i = 0; i < cost.length; i++){
            if(i > 0) result.append(", ");
            result.append(cost[i].amount).append(' ').append(cost[i].item.localizedName);
        }
        return result.toString();
    }
}
