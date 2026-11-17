package orbital.engine.world;

import arc.struct.Seq;
import orbital.content.Blocks;
import orbital.entities.units.BuildPlan;
import orbital.game.Team;
import orbital.gen.Building;
import orbital.type.Item;
import orbital.type.Liquid;
import orbital.world.Block;
import orbital.world.Build;
import orbital.world.Tile;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;

import static orbital.Vars.*;

/**
 * Autonomous builder: picks a site and plans the whole production chain for a factory.
 *
 * <p>The player only chooses the factory type. The unit behaves like a player who only builds: it
 * finds a free site near the core where the consumed ore/liquid is reachable, then enqueues drills,
 * unloaders, aligned transport, pumps, power and finally the factory. Every route starts and ends
 * on a tile adjacent to the factory with its rotation pointing the right way, so the chain is
 * connected end to end instead of merely drawn nearby.</p>
 */
public final class FactoryChainPlanner{
    /** Maximum radius from the core where a site may be chosen. */
    public static final int siteRadius = 24;
    /** Radius used to look for ore/liquid tiles that feed the factory. */
    public static final int sourceRadius = 40;
    /** Penalty for a site that cannot reach one of the required sources. */
    public static final float missingSourcePenalty = 400f;

    private FactoryChainPlanner(){
    }

    /** @return all plans for the chain, in build order; empty when no site exists. */
    public static Seq<BuildPlan> build(Block factory, int rotation, Team team){
        Seq<BuildPlan> plans = new Seq<>();
        if(factory == null || team == null) return plans;

        CoreBuild core = AutoInfrastructure.closestCore(team, 0, 0);
        if(core == null) return plans;

        Tile site = chooseSite(factory, team);
        if(site == null) return plans;

        int haul = Math.abs(site.x - core.tileX()) + Math.abs(site.y - core.tileY());
        Block itemTransport = TransportTier.fastestItem(core, haul);
        Block liquidTransport = TransportTier.fastestLiquid(core);

        //inputs: ore -> drill -> unloader -> aligned belt -> factory
        for(Item item : content.items()){
            if(!factory.consumesItem(item)) continue;
            Tile ore = nearestSource(site, item, null);
            if(ore == null) continue;

            Block drill = fastestDrill(core);
            if(!placeable(drill, team, ore.x, ore.y)) continue;
            plans.add(new BuildPlan(ore.x, ore.y, 0, drill));

            Tile unload = stepTowards(ore, site, factory.size, team);
            if(unload != null && placeable(Blocks.unloader, team, unload.x, unload.y)){
                plans.add(new BuildPlan(unload.x, unload.y, 0, Blocks.unloader));
            }

            Tile feed = unload == null ? ore : unload;
            Tile connector = addConnector(plans, factory, site, feed.x, feed.y,
                itemTransport, true, team);
            if(connector != null && unload != null){
                AutoInfrastructure.addRoute(plans, connector.x, connector.y,
                    unload.x, unload.y, itemTransport, Blocks.junction, team, null);
            }
        }

        //liquids: pump on the liquid tile -> aligned conduit -> factory
        for(Liquid liquid : content.liquids()){
            if(!factory.consumesLiquid(liquid)) continue;
            Tile liquidTile = nearestSource(site, null, liquid);
            if(liquidTile == null) continue;

            Block pump = fastestPump(core);
            if(!placeable(pump, team, liquidTile.x, liquidTile.y)) continue;
            plans.add(new BuildPlan(liquidTile.x, liquidTile.y, 0, pump));

            Tile connector = addConnector(plans, factory, site, liquidTile.x, liquidTile.y,
                liquidTransport, true, team);
            if(connector != null){
                AutoInfrastructure.addRoute(plans, connector.x, connector.y,
                    liquidTile.x, liquidTile.y, liquidTransport, Blocks.ductRouter, team, null);
            }
        }

        //the factory itself, after its inputs so it starts supplied
        plans.add(new BuildPlan(site.x, site.y, rotation, factory));

        //output: factory -> aligned belt -> closest sink
        Building sink = factory.acceptsItems
            ? AutoInfrastructure.closestSink(site.x, site.y, team, false) : null;
        if(sink != null){
            Tile connector = addConnector(plans, factory, site, sink.tileX(), sink.tileY(),
                itemTransport, false, team);
            if(connector != null){
                AutoInfrastructure.addRoute(plans, connector.x, connector.y,
                    sink.tileX(), sink.tileY(), itemTransport, Blocks.junction, team, null);
            }
        }

        //power: real generator first, core as fallback
        if(factory.consumesPower && !AutoInfrastructure.hasPowerNear(site.x, site.y)){
            Building source = AutoInfrastructure.closestPowerSource(site.x, site.y, team);
            if(source != null){
                AutoInfrastructure.addPower(plans, new BuildPlan(site.x, site.y, rotation, factory),
                    source, team);
            }
        }

        return plans;
    }

    /**
     * Picks a free site near the core where the consumed ore and liquid can be reached. Smaller
     * rings win, then distance to core and to the required sources.
     */
    public static Tile chooseSite(Block factory, Team team){
        CoreBuild core = AutoInfrastructure.closestCore(team, 0, 0);
        if(core == null) return null;

        Tile best = null;
        float bestScore = Float.MAX_VALUE;
        for(int radius = 3; radius <= siteRadius; radius += 2){
            for(int y = core.tileY() - radius; y <= core.tileY() + radius; y++){
                for(int x = core.tileX() - radius; x <= core.tileX() + radius; x++){
                    if(Math.max(Math.abs(x - core.tileX()), Math.abs(y - core.tileY())) != radius) continue;

                    Tile tile = world.tile(x, y);
                    if(tile == null || tile.block() != Blocks.air) continue;
                    //ore and liquid floors are reserved for drills/pumps, never for the factory
                    if(tile.drop() != null || tile.floor().liquidDrop != null) continue;
                    if(!placeable(factory, team, x, y)) continue;

                    //best effort: a missing source is expensive, but never a reason to build
                    //nothing. The unit must always end up with a factory and a usable site.
                    float score = distance(x - core.tileX(), y - core.tileY());
                    for(Item item : content.items()){
                        if(!factory.consumesItem(item)) continue;
                        float d = nearestSourceDistance(x, y, item, null);
                        score += d < 0 ? missingSourcePenalty : d * 2f;
                    }
                    for(Liquid liquid : content.liquids()){
                        if(!factory.consumesLiquid(liquid)) continue;
                        float d = nearestSourceDistance(x, y, null, liquid);
                        score += d < 0 ? missingSourcePenalty : d * 2f;
                    }

                    if(score < bestScore){
                        bestScore = score;
                        best = tile;
                    }
                }
            }
        }
        return best;
    }

    private static Tile nearestSource(Tile site, Item item, Liquid liquid){
        Tile best = null;
        float bestDistance = Float.MAX_VALUE;
        int minX = Math.max(0, site.x - sourceRadius), maxX = Math.min(world.width() - 1, site.x + sourceRadius);
        int minY = Math.max(0, site.y - sourceRadius), maxY = Math.min(world.height() - 1, site.y + sourceRadius);

        for(int y = minY; y <= maxY; y++){
            for(int x = minX; x <= maxX; x++){
                Tile tile = world.tile(x, y);
                if(tile == null || tile.block() != Blocks.air) continue;

                boolean matches = item != null ? tile.drop() == item : tile.floor().liquidDrop == liquid;
                if(!matches) continue;

                float distance = distance(x - site.x, y - site.y);
                if(distance < bestDistance){
                    bestDistance = distance;
                    best = tile;
                }
            }
        }
        return best;
    }

    private static float nearestSourceDistance(int x, int y, Item item, Liquid liquid){
        Tile source = nearestSource(world.tile(x, y), item, liquid);
        return source == null ? -1f : distance(source.x - x, source.y - y);
    }

    /** Adds a belt/conduit adjacent to the factory with a rotation pointing in or out. */
    private static Tile addConnector(Seq<BuildPlan> plans, Block factory, Tile site,
                                     int otherX, int otherY, Block transport,
                                     boolean input, Team team){
        int dx = otherX - site.x, dy = otherY - site.y;
        int cx = site.x, cy = site.y;
        int size = Math.max(1, factory.size);
        int edge = size - 1;

        if(Math.abs(dx) >= Math.abs(dy)){
            cx = dx >= 0 ? site.x + edge + 1 : site.x - 1;
            cy = site.y + edge / 2;
        }else{
            cx = site.x + edge / 2;
            cy = dy >= 0 ? site.y + edge + 1 : site.y - 1;
        }

        Tile connector = world.tile(cx, cy);
        if(connector == null || connector.block() != Blocks.air) return null;
        if(!placeable(transport, team, cx, cy)) return null;

        //the byte overload only handles directly adjacent tiles; the float one works at any range
        int rotation = Tile.relativeTo((float)cx, (float)cy,
            site.x + edge / 2f, site.y + edge / 2f);
        if(rotation < 0) rotation = 0;
        if(!input) rotation = (rotation + 2) % 4;

        BuildPlan plan = new BuildPlan(cx, cy, rotation, transport);
        for(BuildPlan existing : plans){
            if(existing.samePos(plan)) return connector;
        }
        plans.add(plan);
        return connector;
    }

    /** First free tile from a source toward the factory, used for the unloader. */
    private static Tile stepTowards(Tile from, Tile site, int size, Team team){
        int dx = Integer.signum(site.x - from.x), dy = Integer.signum(site.y - from.y);

        //prefer a spot facing the factory but one tile away from its footprint, so the belt
        //between the unloader and the factory has room to exist
        for(int step = 2; step >= 1; step--){
            for(int[] axis : new int[][]{{dx, 0}, {0, dy}, {-dx, 0}, {0, dy != 0 ? -dy : 1}}){
                if(axis[0] == 0 && axis[1] == 0) continue;
                Tile tile = world.tile(from.x + axis[0] * step, from.y + axis[1] * step);
                if(!unloaderSpot(tile, site, size, team)) continue;
                if(footprintDistance(tile, site, size) >= 2 || step == 1) return tile;
            }
        }
        return null;
    }

    private static int footprintDistance(Tile tile, Tile site, int size){
        int dx = Math.max(0, Math.max(site.x - tile.x, tile.x - (site.x + size - 1)));
        int dy = Math.max(0, Math.max(site.y - tile.y, tile.y - (site.y + size - 1)));
        return dx + dy;
    }

    private static boolean unloaderSpot(Tile tile, Tile site, int size, Team team){
        if(tile == null || tile.block() != Blocks.air) return false;
        //never put the unloader under the factory footprint
        if(tile.x >= site.x && tile.x < site.x + size
            && tile.y >= site.y && tile.y < site.y + size) return false;
        return placeable(Blocks.unloader, team, tile.x, tile.y);
    }

    private static boolean placeable(Block block, Team team, int x, int y){
        return block != null && Build.validPlaceIgnoreUnits(block, team, x, y, 0, false, false);
    }

    private static Block fastestDrill(CoreBuild core){
        Block[] candidates = {Blocks.laserDrill, Blocks.pneumaticDrill, Blocks.mechanicalDrill};
        return firstPayable(candidates, core, Blocks.mechanicalDrill);
    }

    private static Block fastestPump(CoreBuild core){
        Block[] candidates = {Blocks.impulsePump, Blocks.rotaryPump, Blocks.mechanicalPump};
        return firstPayable(candidates, core, Blocks.mechanicalPump);
    }

    private static Block firstPayable(Block[] candidates, CoreBuild core, Block fallback){
        for(Block block : candidates){
            if(block == null) continue;
            boolean payable = core.items != null;
            if(payable){
                for(var stack : block.requirements){
                    if(core.items.get(stack.item) < stack.amount){
                        payable = false;
                        break;
                    }
                }
            }
            if(payable) return block;
        }
        return fallback;
    }

    private static float distance(int dx, int dy){
        return dx * dx + dy * dy;
    }
}