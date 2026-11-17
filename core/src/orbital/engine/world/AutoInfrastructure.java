package orbital.engine.world;

import arc.Core;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.pooling.Pools;
import orbital.content.Blocks;
import orbital.entities.units.BuildPlan;
import orbital.game.Team;
import orbital.gen.Building;
import orbital.input.Placement;
import orbital.type.ItemStack;
import orbital.world.Block;
import orbital.world.Tile;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;
import orbital.world.blocks.production.Drill;
import orbital.world.blocks.production.GenericCrafter;
import orbital.world.blocks.production.Pump;
import orbital.world.blocks.power.PowerNode;
import orbital.world.blocks.storage.StorageBlock;

import static orbital.Vars.*;

/**
 * Turns one chosen factory into a complete build package: transport routes to its inputs and from
 * its outputs, using the fastest unlocked and affordable tier.
 *
 * <p>The factory plan itself is never added here. The returned plans are ordered so the routes are
 * built first; the caller appends the factory plan at the end. Everything is deterministic - source
 * choice, route points and block tiers are derived only from current world state - because the plans
 * are authoritative state shared with the build queue.</p>
 */
public final class AutoInfrastructure{
    /** Opt-in key for normal player placement; the test catalog always uses the planner. */
    public static final String key = "orbitalautoinfra";
    /** Hard caps so one click can never flood the unit queue. */
    public static final int maxPlansPerFactory = 96;
    public static final int maxRouteLength = 40;
    /** Search radius for a drill/pump that produces the input, in tiles. */
    public static final int sourceRadius = 18;

    private AutoInfrastructure(){
    }

    /** @return whether auto-infrastructure runs for ordinary player placement. */
    public static boolean normalPlacementEnabled(){
        return Core.settings != null && Core.settings.getBool(key, false);
    }

    /**
     * @return infrastructure plans for this factory, in build order. Empty when the block has no
     * resource transport to plan or when there is no team core to route to.
     */
    public static Seq<BuildPlan> plan(BuildPlan factory, Team team){
        Seq<BuildPlan> result = new Seq<>();
        if(factory == null || factory.block == null || factory.breaking || team == null) return result;

        Block block = factory.block;
        boolean itemInput = consumesItem(block);
        boolean liquidInput = consumesLiquid(block);
        boolean itemOutput = producesItem(block);
        boolean liquidOutput = producesLiquid(block);
        if(!itemInput && !liquidInput && !itemOutput && !liquidOutput) return result;

        CoreBuild core = closestCore(team, factory.x, factory.y);
        if(core == null) return result;

        Block itemBlock = fastestItem(team, core);
        Block liquidBlock = fastestLiquid(team, core);

        //inputs first: the transport exists before the factory starts consuming
        if(itemInput){
            Building source = closestSource(factory.x, factory.y, team, false);
            if(source != null){
                addRoute(result, source.tileX(), source.tileY(), factory.x, factory.y, itemBlock, Blocks.junction, team);
            }
        }
        if(liquidInput){
            Building source = closestSource(factory.x, factory.y, team, true);
            if(source != null){
                addRoute(result, source.tileX(), source.tileY(), factory.x, factory.y, liquidBlock, Blocks.ductRouter, team);
            }
        }

        //then the outputs back to a sink: the core, or the closest storage/tank
        Building itemSink = closestSink(factory.x, factory.y, team, false);
        Building liquidSink = closestSink(factory.x, factory.y, team, true);
        if(itemOutput && itemBlock != null && itemSink != null){
            addRoute(result, factory.x, factory.y, itemSink.tileX(), itemSink.tileY(),
                itemBlock, Blocks.junction, team, multiItemOutput(block) ? Blocks.overflowGate : null);
        }
        if(liquidOutput && liquidBlock != null && liquidSink != null){
            addRoute(result, factory.x, factory.y, liquidSink.tileX(), liquidSink.tileY(),
                liquidBlock, Blocks.ductRouter, team, multiLiquidOutput(block) ? Blocks.liquidRouter : null);
        }

        //a factory that consumes power needs a network; lay nodes toward the closest one
        if(block.consumesPower && !hasPowerNear(factory.x, factory.y)){
            addPower(result, factory, core, team);
        }

        if(result.size > maxPlansPerFactory){
            result.size = maxPlansPerFactory;
        }
        return result;
    }

    private static boolean consumesItem(Block block){
        for(boolean value : block.itemFilter){
            if(value) return true;
        }
        return false;
    }

    private static boolean consumesLiquid(Block block){
        for(boolean value : block.liquidFilter){
            if(value) return true;
        }
        return false;
    }

    private static boolean producesItem(Block block){
        return block instanceof GenericCrafter crafter
            && ((crafter.outputItem != null && crafter.outputItem.amount > 0)
                || (crafter.outputItems != null && crafter.outputItems.length > 0));
    }

    private static boolean producesLiquid(Block block){
        return block instanceof GenericCrafter crafter
            && ((crafter.outputLiquid != null && crafter.outputLiquid.amount > 0)
                || (crafter.outputLiquids != null && crafter.outputLiquids.length > 0));
    }

    private static CoreBuild closestCore(Team team, int x, int y){
        CoreBuild best = null;
        float bestDistance = Float.MAX_VALUE;
        for(CoreBuild core : state.teams.cores(team)){
            float distance = distanceSq(core.tileX() - x, core.tileY() - y);
            if(distance < bestDistance){
                bestDistance = distance;
                best = core;
            }
        }
        return best;
    }

    /** Nearest building that produces an input of this kind; the core is the safe fallback. */
    private static Building closestSource(int x, int y, Team team, boolean liquid){
        Building best = null;
        float bestDistance = Float.MAX_VALUE;
        int minX = Math.max(0, x - sourceRadius), maxX = Math.min(world.width() - 1, x + sourceRadius);
        int minY = Math.max(0, y - sourceRadius), maxY = Math.min(world.height() - 1, y + sourceRadius);

        for(int ty = minY; ty <= maxY; ty++){
            for(int tx = minX; tx <= maxX; tx++){
                Tile tile = world.tile(tx, ty);
                if(tile == null || tile.build == null) continue;

                boolean matches;
                if(liquid){
                    matches = tile.build.block instanceof Pump;
                }else{
                    matches = tile.build.block instanceof Drill;
                }
                if(!matches) continue;

                float distance = distanceSq(tx - x, ty - y);
                if(distance < bestDistance){
                    bestDistance = distance;
                    best = tile.build;
                }
            }
        }

        if(best != null) return best;

        CoreBuild core = closestCore(team, x, y);
        return core == null ? null : core;
    }

    private static void addRoute(Seq<BuildPlan> plans, int sx, int sy, int tx, int ty,
                                 Block transport, Block junction, Team team){
        addRoute(plans, sx, sy, tx, ty, transport, junction, team, null);
    }

    private static void addRoute(Seq<BuildPlan> plans, int sx, int sy, int tx, int ty,
                                 Block transport, Block junction, Team team, Block firstTile){
        if(transport == null || plans.size >= maxPlansPerFactory) return;

        Seq<Point2> points = Placement.pathfindLine(true, sx, sy, tx, ty);
        try{
            int step = Math.max(1, points.size / maxRouteLength);
            for(int i = 0; i < points.size - 1; i += step){
                Point2 from = points.get(i);
                Point2 to = points.get(Math.min(i + step, points.size - 1));

                //endpoints are existing buildings; only the tiles in between get transport
                if((from.x == sx && from.y == sy) || (to.x == tx && to.y == ty)) continue;

                Tile tile = world.tile(from.x, from.y);
                if(tile == null || tile.block() != Blocks.air) continue;

                int rotation = Tile.relativeTo(from.x, from.y, to.x, to.y);

                //a corner needs a junction, otherwise the line stops at the turn
                Block block = transport;
                if(firstTile != null && i <= step){
                    //the tile leaving a multi-output factory sorts/overflows the mixed stream
                    block = firstTile;
                }else if(i + step < points.size - 1){
                    Point2 next = points.get(Math.min(i + step * 2, points.size - 1));
                    int nextRotation = Tile.relativeTo(to.x, to.y, next.x, next.y);
                    if(nextRotation != rotation && junction != null){
                        block = junction;
                    }
                }

                BuildPlan plan = new BuildPlan(from.x, from.y, rotation, block);
                if(!plan.placeable(team)) continue;

                boolean duplicate = false;
                for(BuildPlan existing : plans){
                    if(existing.samePos(plan)){
                        duplicate = true;
                        break;
                    }
                }
                if(!duplicate) plans.add(plan);
                if(plans.size >= maxPlansPerFactory) break;
            }
        }finally{
            Pools.freeAll(points);
        }
    }

    private static boolean multiItemOutput(Block block){
        return block instanceof GenericCrafter crafter
            && crafter.outputItems != null && crafter.outputItems.length > 1;
    }

    private static boolean multiLiquidOutput(Block block){
        return block instanceof GenericCrafter crafter
            && crafter.outputLiquids != null && crafter.outputLiquids.length > 1;
    }

    /** Nearest storage/tank that can receive output; the core is always the fallback. */
    private static Building closestSink(int x, int y, Team team, boolean liquid){
        Building best = null;
        float bestDistance = Float.MAX_VALUE;
        int minX = Math.max(0, x - sourceRadius), maxX = Math.min(world.width() - 1, x + sourceRadius);
        int minY = Math.max(0, y - sourceRadius), maxY = Math.min(world.height() - 1, y + sourceRadius);

        for(int ty = minY; ty <= maxY; ty++){
            for(int tx = minX; tx <= maxX; tx++){
                Tile tile = world.tile(tx, ty);
                if(tile == null || tile.build == null || tile.build.team != team) continue;
                if(!(tile.build.block instanceof StorageBlock)) continue;

                float distance = distanceSq(tx - x, ty - y);
                if(distance < bestDistance){
                    bestDistance = distance;
                    best = tile.build;
                }
            }
        }
        if(best != null) return best;

        CoreBuild core = closestCore(team, x, y);
        return core;
    }

    private static boolean hasPowerNear(int x, int y){
        int radius = 10;
        for(int ty = Math.max(0, y - radius); ty <= Math.min(world.height() - 1, y + radius); ty++){
            for(int tx = Math.max(0, x - radius); tx <= Math.min(world.width() - 1, x + radius); tx++){
                Tile tile = world.tile(tx, ty);
                if(tile == null || tile.build == null) continue;
                if(tile.build.power != null && tile.build.power.graph != null) return true;
            }
        }
        return false;
    }

    /** Lays power nodes from the factory toward the core when no network is in range. */
    private static void addPower(Seq<BuildPlan> plans, BuildPlan factory, CoreBuild core, Team team){
        Block node = null;
        int range = 0;
        if(Blocks.powerNodeLarge != null && Blocks.powerNodeLarge.unlockedNow()
            && affordable(Blocks.powerNodeLarge, core)){
            node = Blocks.powerNodeLarge;
            range = (int)((PowerNode)Blocks.powerNodeLarge).laserRange;
        }else if(Blocks.powerNode != null && affordable(Blocks.powerNode, core)){
            node = Blocks.powerNode;
            range = (int)((PowerNode)Blocks.powerNode).laserRange;
        }
        if(node == null) return;

        int spacing = Math.max(2, range - 1);
        Seq<Point2> points = Placement.pathfindLine(true, factory.x, factory.y, core.tileX(), core.tileY());
        try{
            int placed = 0;
            for(int i = 2; i < points.size && placed < 12 && plans.size < maxPlansPerFactory; i += spacing){
                Point2 point = points.get(i);
                Tile tile = world.tile(point.x, point.y);
                if(tile == null || tile.block() != Blocks.air) continue;

                BuildPlan plan = new BuildPlan(point.x, point.y, 0, node);
                if(!plan.placeable(team)) continue;

                boolean duplicate = false;
                for(BuildPlan existing : plans){
                    if(existing.samePos(plan)){
                        duplicate = true;
                        break;
                    }
                }
                if(!duplicate){
                    plans.add(plan);
                    placed++;
                }
            }
        }finally{
            Pools.freeAll(points);
        }
    }

    /** Fastest unlocked tier the core can actually pay for, with a fallback to slower tiers. */
    private static Block fastestItem(Team team, CoreBuild core){
        Block[] candidates = {Blocks.surgeConveyor, Blocks.plastaniumConveyor,
            Blocks.titaniumConveyor, Blocks.armoredConveyor, Blocks.conveyor};
        for(Block block : candidates){
            if(block != null && block.unlockedNow() && affordable(block, core)) return block;
        }
        return Blocks.conveyor;
    }

    private static Block fastestLiquid(Team team, CoreBuild core){
        Block[] candidates = {Blocks.armoredDuct, Blocks.duct, Blocks.pulseConduit, Blocks.conduit};
        for(Block block : candidates){
            if(block != null && block.unlockedNow() && affordable(block, core)) return block;
        }
        return Blocks.conduit;
    }

    private static boolean affordable(Block block, CoreBuild core){
        if(core == null || core.items == null) return false;
        for(ItemStack stack : block.requirements){
            if(core.items.get(stack.item) < stack.amount) return false;
        }
        return true;
    }

    private static float distanceSq(int dx, int dy){
        return dx * dx + dy * dy;
    }

    /** Debug text for the test panel: what the planner would do for this factory. */
    public static String describe(BuildPlan factory, Team team){
        Seq<BuildPlan> plans = plan(factory, team);
        if(plans.isEmpty()) return "no infrastructure planned";
        Block transport = plans.first().block;
        return plans.size + " transport tiles (" + (transport == null ? "?" : transport.name) + ")";
    }
}