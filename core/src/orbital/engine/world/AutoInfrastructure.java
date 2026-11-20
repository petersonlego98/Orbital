package orbital.engine.world;

import arc.Core;
import arc.math.geom.Point2;
import orbital.world.blocks.distribution.ItemBridge;
import orbital.world.blocks.distribution.MassDriver;
import arc.struct.Seq;
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

        Block itemBlock = TransportTier.fastestItem(core, 0);
        Block liquidBlock = TransportTier.fastestLiquid(core);

        //Power nodes are queued first: a long transport route can otherwise consume the whole
        //maxPlansPerFactory budget and leave a power-hungry factory permanently unsupplied.
        //Building the network first is also the correct construction order.
        //a factory that consumes power needs a network; lay nodes toward a real generator/battery
        if(block.hasPower && block.consPower != null && block.consPower.usage > 0f
            && !hasPowerNear(factory.x, factory.y, block, team)){
            Building source = closestPowerSource(factory.x, factory.y, team);
            if(source != null){
                addPower(result, factory, source, team);
            }
        }

        //inputs first: the transport exists before the factory starts consuming
        if(itemInput){
            Building source = closestSource(factory.x, factory.y, team, false);
            if(source != null){
                int haul = Math.abs(factory.x - source.tileX()) + Math.abs(factory.y - source.tileY());
                Block routeBlock = TransportTier.fastestItem(core, haul);
                addRoute(result, source.tileX(), source.tileY(), factory.x, factory.y,
                    routeBlock, Blocks.junction, team);
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
            int haul = Math.abs(factory.x - itemSink.tileX()) + Math.abs(factory.y - itemSink.tileY());
            Block outputBlock = TransportTier.fastestItem(core, haul);
            addRoute(result, factory.x, factory.y, itemSink.tileX(), itemSink.tileY(),
                outputBlock, Blocks.junction, team, multiItemOutput(block) ? Blocks.overflowGate : null);
        }
        if(liquidOutput && liquidBlock != null && liquidSink != null){
            addRoute(result, factory.x, factory.y, liquidSink.tileX(), liquidSink.tileY(),
                liquidBlock, Blocks.ductRouter, team, multiLiquidOutput(block) ? Blocks.liquidRouter : null);
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

    public static CoreBuild closestCore(Team team, int x, int y){
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

    public static void addRoute(Seq<BuildPlan> plans, int sx, int sy, int tx, int ty,
                                 Block transport, Block junction, Team team){
        addRoute(plans, sx, sy, tx, ty, transport, junction, team, null);
    }

    public static void addRoute(Seq<BuildPlan> plans, int sx, int sy, int tx, int ty,
                                 Block transport, Block junction, Team team, Block firstTile){
        addRoute(plans, sx, sy, tx, ty, transport, junction, team, firstTile, null);
    }

    /**
     * @param avoid an extra building footprint that is not in {@code plans} yet (the factory is
     *              appended only after its input routes, so it must be protected explicitly).
     */
    public static void addRoute(Seq<BuildPlan> plans, int sx, int sy, int tx, int ty,
                                 Block transport, Block junction, Team team, Block firstTile,
                                 BuildPlan avoid){
        if(transport == null || plans.size >= maxPlansPerFactory) return;

        int firstAdded = plans.size;

        Seq<Point2> points = Placement.pathfindLine(true, sx, sy, tx, ty);
        try{
            if(transport instanceof MassDriver){
                //A mass driver is a paired long-range link, never a per-tile belt. The old
                //fallback below placed one driver on every path tile when endpoints were too
                //close or either endpoint could not be placed, producing adjacent dead drivers.
                Point2 a = points.size >= 6 ? points.get(2) : null;
                Point2 b = points.size >= 6 ? points.get(points.size - 3) : null;
                boolean farEnough = a != null && b != null
                    && Math.abs(a.x - b.x) + Math.abs(a.y - b.y) >= 8;
                Tile ta = farEnough ? world.tile(a.x, a.y) : null;
                Tile tb = farEnough ? world.tile(b.x, b.y) : null;
                if(farEnough && ta != null && tb != null
                    && ta.block() == Blocks.air && tb.block() == Blocks.air){
                    BuildPlan first = new BuildPlan(a.x, a.y, 0, transport);
                    BuildPlan second = new BuildPlan(b.x, b.y, 0, transport);
                    if(first.placeable(team) && second.placeable(team)){
                        first.config = new Point2(b.x - a.x, b.y - a.y);
                        second.config = new Point2(a.x - b.x, a.y - b.y);
                        plans.add(first);
                        plans.add(second);
                        return;
                    }
                }
                //If a valid, separated pair cannot be placed, use the fastest continuous belt.
                transport = TransportTier.fastestItem(closestCore(team, sx, sy), 0);
            }

            int step = Math.max(1, points.size / maxRouteLength);
            for(int i = 0; i < points.size - 1; i += step){
                Point2 from = points.get(i);
                Point2 to = points.get(Math.min(i + step, points.size - 1));

                //The start is an existing building. The last "from" tile must still be built:
                //it is the transport tile immediately before the destination.
                if(from.x == sx && from.y == sy) continue;

                Tile tile = world.tile(from.x, from.y);
                if(tile == null || tile.block() != Blocks.air) continue;
                //multi-tile buildings live in the same queue as belts. Checking only exact
                //positions lets a route overwrite a drill's second tile or a crafter's edge.
                if(overlapsPlannedFootprint(plans, from.x, from.y)
                    || overlapsFootprint(avoid, from.x, from.y)) continue;

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
            //Placement.pathfindLine owns this sequence. It frees the Point2 instances at the start
            //of its next call. Freeing here double-frees them: Arc's Pools.freeAll(Seq) does not
            //clear the sequence, so the next pathfindLine frees the same instances again and hands
            //out aliased points. See the audit in ConstructionAuditTest.
        }

        linkGaps(plans, firstAdded, transport, team, avoid);
    }

    /**
     * Where the pathfinder had to step around an occupied tile, the two neighbours on either side
     * are too far apart for a plain belt. Connect them with a bridge instead of leaving a hole.
     */
    private static void linkGaps(Seq<BuildPlan> plans, int firstAdded, Block transport, Team team,
                                  BuildPlan avoid){
        CoreBuild core = closestCore(team, 0, 0);
        Block bridge = transport != null && transport.isDuct
            ? TransportTier.fastestDuctBridge(core)
            : TransportTier.fastestBridge(core);
        if(bridge == null) return;

        int range = bridge instanceof ItemBridge itemBridge ? itemBridge.range : 4;
        for(int i = firstAdded + 1; i < plans.size; i++){
            BuildPlan before = plans.get(i - 1);
            BuildPlan after = plans.get(i);
            if(before.block == null || after.block == null) continue;

            int dx = Math.abs(before.x - after.x);
            int dy = Math.abs(before.y - after.y);
            if(dx + dy <= 1 || dx > range || dy > range) continue;
            if(before.block instanceof ItemBridge || after.block instanceof ItemBridge) continue;

            //bridges only link straight tiles; add the corner of an L so the jump is orthogonal
            if(dx > 0 && dy > 0){
                int ix = after.x, iy = before.y;
                Tile corner = world.tile(ix, iy);
                if(corner == null || corner.block() != Blocks.air) continue;

                boolean occupied = false;
                for(BuildPlan existing : plans){
                    if(existing.x == ix && existing.y == iy){
                        occupied = true;
                        break;
                    }
                }
                if(occupied) continue;
                if(overlapsFootprint(avoid, ix, iy)) continue;

                BuildPlan middle = new BuildPlan(ix, iy,
                    Tile.relativeTo(ix, iy, after.x, after.y), transport);
                if(overlapsPlannedFootprint(plans, ix, iy)) continue;
                if(!middle.placeable(team)) continue;

                plans.insert(i, middle);
                after = middle;
                dx = Math.abs(before.x - after.x);
                dy = Math.abs(before.y - after.y);
            }
            if(dx + dy <= 1 || (dx != 0 && dy != 0)) continue;

            if(bridge instanceof ItemBridge itemBridge
               && !itemBridge.positionsValid(before.x, before.y, after.x, after.y)) continue;

            before.block = bridge;
            after.block = bridge;
            //the receiving end stores the relative link back to the sender
            after.config = new Point2(before.x - after.x, before.y - after.y);
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
    public static Building closestSink(int x, int y, Team team, boolean liquid){
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

    /** Nearest friendly generator/battery with a live power graph; the core is the fallback. */
    public static Building closestPowerSource(int x, int y, Team team){
        Building best = null;
        float bestDistance = Float.MAX_VALUE;
        int radius = 48;
        int minX = Math.max(0, x - radius), maxX = Math.min(world.width() - 1, x + radius);
        int minY = Math.max(0, y - radius), maxY = Math.min(world.height() - 1, y + radius);

        for(int ty = minY; ty <= maxY; ty++){
            for(int tx = minX; tx <= maxX; tx++){
                Tile tile = world.tile(tx, ty);
                if(tile == null || tile.build == null || tile.build.team != team) continue;
                if(!tile.build.block.outputsPower) continue;
                if(tile.build.power == null || tile.build.power.graph == null) continue;

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

    public static boolean hasPowerNear(int x, int y){
        return hasPowerNear(x, y, Blocks.air, null);
    }

    /** Checks for an adjacent power graph that actually has a same-team producer. */
    public static boolean hasPowerNear(int x, int y, Block target, Team team){
        int size = target == null ? 1 : Math.max(1, target.size);
        int middle = (size - 1) / 2;
        int minX = x - middle, minY = y - middle;
        int maxX = minX + size - 1, maxY = minY + size - 1;
        for(int ty = Math.max(0, minY - 1); ty <= Math.min(world.height() - 1, maxY + 1); ty++){
            for(int tx = Math.max(0, minX - 1); tx <= Math.min(world.width() - 1, maxX + 1); tx++){
                if(tx >= minX && tx <= maxX && ty >= minY && ty <= maxY) continue;
                Tile tile = world.tile(tx, ty);
                if(tile == null || tile.build == null || (team != null && tile.build.team != team)
                    || tile.build.power == null || tile.build.power.graph == null) continue;
                for(Building producer : tile.build.power.graph.producers){
                    if(producer != null && producer.block.outputsPower
                        && (team == null || producer.team == team)) return true;
                }
            }
        }
        return false;
    }

    /** Lays power nodes from the factory toward the core when no network is in range. */
    public static void addPower(Seq<BuildPlan> plans, BuildPlan factory, Building source, Team team){
        CoreBuild core = closestCore(team, factory.x, factory.y);
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
        Seq<Point2> points = Placement.pathfindLine(true, factory.x, factory.y,
            source.tileX(), source.tileY());
        //The transport route usually runs along this same line. A node on the exact path tile
        //would collide with the already-planned belts, so probe a small perpendicular fan.
        int[][] offsets = {{0, 0}, {0, 2}, {0, -2}, {2, 0}, {-2, 0},
            {0, 3}, {0, -3}, {3, 0}, {-3, 0}, {2, 2}, {-2, -2}};
        try{
            int placed = 0;
            for(int i = 2; i < points.size && placed < 12 && plans.size < maxPlansPerFactory; i += spacing){
                Point2 point = points.get(i);
                for(int[] offset : offsets){
                    int x = point.x + offset[0], y = point.y + offset[1];
                    Tile tile = world.tile(x, y);
                    if(tile == null || tile.block() != Blocks.air) continue;
                    if(overlapsPlannedFootprint(plans, x, y)) continue;

                    BuildPlan plan = new BuildPlan(x, y, 0, node);
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
                    break;
                }
            }
        }finally{
            //The points belong to Placement.pathfindLine; see the note in addRoute.
        }
    }

    private static boolean overlapsPlannedFootprint(Seq<BuildPlan> plans, int x, int y){
        for(BuildPlan plan : plans){
            if(plan.block == null) continue;
            int size = Math.max(1, plan.block.size);
            int minX = plan.x - (size - 1) / 2, minY = plan.y - (size - 1) / 2;
            if(x >= minX && x < minX + size && y >= minY && y < minY + size) return true;
        }
        return false;
    }

    private static boolean overlapsFootprint(BuildPlan plan, int x, int y){
        if(plan == null || plan.block == null) return false;
        int size = Math.max(1, plan.block.size);
        int minX = plan.x - (size - 1) / 2, minY = plan.y - (size - 1) / 2;
        return x >= minX && x < minX + size && y >= minY && y < minY + size;
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
