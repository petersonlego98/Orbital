package orbital.engine.world;

import orbital.content.Blocks;
import orbital.type.ItemStack;
import orbital.world.Block;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranked transport methods for the auto-infrastructure planner.
 *
 * <p>The ranking is the throughput declared by the blocks themselves (items or liquid units per
 * second), so "use the fastest method" is a data decision, not a hard-coded preference for one
 * block. Every method still has to be unlocked and payable by the core; the first one that is
 * both is chosen. Distance is not part of the ranking - the owner asked for the fastest available
 * method, not the cheapest route.</p>
 */
public final class TransportTier{
    public record Method(Block block, float unitsPerSecond, boolean liquid){
        public boolean unlockedAndAffordable(CoreBuild core){
            if(block == null || !block.unlockedNow() || core == null || core.items == null) return false;
            for(ItemStack stack : block.requirements){
                if(core.items.get(stack.item) < stack.amount) return false;
            }
            return true;
        }
    }

    /** Items per second: stack conveyors move 10 items every 4/60 s = 40/s. */
    public static final List<Method> ITEMS = List.of(
        new Method(Blocks.surgeConveyor, 40f, false),
        new Method(Blocks.plastaniumConveyor, 40f, false),
        new Method(Blocks.phaseConveyor, 30f, false),
        new Method(Blocks.titaniumConveyor, 10f, false),
        new Method(Blocks.armoredConveyor, 9.6f, false),
        new Method(Blocks.itemBridge, 0.81f, false),
        new Method(Blocks.conveyor, 5f, false)
    );

    /** A mass driver beats any belt over a long haul, but needs room at both ends. */
    public static final List<Method> LONG_HAUL = List.of(
        new Method(Blocks.massDriver, 36f, false));
    /** Minimum point-to-point distance where a 3x3 mass driver is worth planning. */
    public static final int longHaulTiles = 12;

    /** Liquid units per second, from the pressure/flow values authored in Blocks. */
    public static final List<Method> LIQUIDS = List.of(
        new Method(Blocks.armoredDuct, 20f, true),
        new Method(Blocks.duct, 16f, true),
        new Method(Blocks.pulseConduit, 12f, true),
        new Method(Blocks.conduit, 9f, true)
    );

    private TransportTier(){
    }

    /** @return the fastest item method the core can pay for, never null. */
    public static Block fastestItem(CoreBuild core, int distance){
        if(distance >= longHaulTiles){
            Block longHaul = pick(LONG_HAUL, core, null);
            if(longHaul != null) return longHaul;
        }
        return pick(ITEMS, core, Blocks.conveyor);
    }

    /** @return the fastest liquid method the core can pay for, never null. */
    public static Block fastestLiquid(CoreBuild core){
        return pick(LIQUIDS, core, Blocks.conduit);
    }

    /** Bridges are gap closers, not throughput on the main line; prefer the long/fast one. */
    public static Block fastestBridge(CoreBuild core){
        return pick(List.of(
            new Method(Blocks.phaseConveyor, 30f, false),
            new Method(Blocks.itemBridge, 0.81f, false)), core, Blocks.itemBridge);
    }

    public static Block fastestDuctBridge(CoreBuild core){
        return pick(List.of(new Method(Blocks.ductBridge, 5f, false)), core, Blocks.ductBridge);
    }

    /** Sorted view for diagnostics; includes whether each method is currently usable. */
    public static List<String> describe(CoreBuild core){
        List<String> lines = new ArrayList<>();
        for(Method method : ITEMS){
            lines.add(method.block.name + " " + method.unitsPerSecond + "/s"
                + (method.unlockedAndAffordable(core) ? " [ready]" : " [locked/poor]"));
        }
        return lines;
    }

    private static Block pick(List<Method> methods, CoreBuild core, Block fallback){
        List<Method> sorted = new ArrayList<>(methods);
        sorted.sort((a, b) -> Float.compare(b.unitsPerSecond(), a.unitsPerSecond()));
        for(Method method : sorted){
            if(method.unlockedAndAffordable(core)) return method.block;
        }
        return fallback;
    }
}