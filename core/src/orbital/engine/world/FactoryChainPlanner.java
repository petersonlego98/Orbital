package orbital.engine.world;

import arc.struct.Seq;
import arc.util.Nullable;
import orbital.content.Blocks;
import orbital.entities.units.BuildPlan;
import orbital.game.Team;
import orbital.game.Teams.BlockPlan;
import orbital.gen.Building;
import orbital.gen.Groups;
import orbital.gen.Unit;
import orbital.type.Item;
import orbital.type.Liquid;
import orbital.world.Block;
import orbital.world.Build;
import orbital.world.Tile;
import orbital.world.blocks.production.Drill;
import orbital.world.blocks.production.GenericCrafter;
import orbital.world.blocks.production.HeatCrafter;
import orbital.world.blocks.production.Pump;
import orbital.world.blocks.heat.HeatProducer;
import orbital.world.blocks.power.SolarGenerator;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;

import java.util.HashMap;
import java.util.Map;

import static orbital.Vars.*;

/**
 * Autonomous builder: picks a site and plans the whole production chain for a factory.
 *
 * <p>The player only chooses the factory type. The unit behaves like a player who only builds: it
 * finds a free site near the core where the consumed ore/liquid is reachable, then enqueues exact
 * input sources, aligned transport, power, the factory and its output route. It consults
 * {@link SectorResources} (what minerals/liquids actually exist in the sector) to decide whether to
 * mine an ingredient locally or pull it from the core stock, and picks a drill of sufficient tier
 * for each ore. Missing ingredients are never silently substituted with a different ore or liquid.</p>
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

    /**
     * Builds the selected catalog structure automatically. Production crafters receive their
     * complete supply chain; all other buildings receive a legal site and required power.
     */
    public static Seq<BuildPlan> build(Block factory, int rotation, Team team){
        Seq<BuildPlan> plans = new Seq<>();
        if(factory == null || team == null) return plans;

        CoreBuild core = AutoInfrastructure.closestCore(team, 0, 0);
        if(core == null) return plans;

        //Only production crafters need the full source/transport chain. Other catalog entries
        //(turrets, drills, pumps, walls, power blocks, unit factories, logistics, etc.) are
        //ordinary structures: find a legal site and enqueue the selected building itself.
        if(!(factory instanceof GenericCrafter)){
            BuildPlan structure = buildStandalone(factory, rotation, team, core);
            if(structure != null) plans.add(structure);
            if(structure != null && needsPower(factory)
                && !ensureFactoryPower(plans, structure, core, team)){
                arc.util.Log.warn("[auto-build] cancelled @: could not provide a live power source",
                    factory.name);
                plans.clear();
            }
            arc.util.Log.info("[auto-build] @ plans queued for structure @",
                plans.size, factory.name);
            return plans;
        }

        //The unit's knowledge of the sector: one map scan indexes every ore and liquid by type.
        //It decides whether each ingredient is mined locally or taken from the core stock.
        SectorResources sector = SectorResources.get();
        arc.util.Log.info("[auto-build] sector ores: @", sector.summary());

        Tile site = chooseSite(factory, team, sector);
        if(site == null) return plans;

        //The factory is appended only after its inputs so it starts supplied. Keep the plan object
        //available from the start: input routes must not cross its future footprint.
        BuildPlan factoryPlan = new BuildPlan(site.x, site.y, rotation, factory);

        //This planner starts at nearby drills and storage endpoints. A mass driver is not a
        //single-tile connector and must only be selected by a dedicated paired-endpoint route.
        //Use the fastest affordable continuous belt for these local factory connections.
        Block itemTransport = TransportTier.fastestItem(core, 0);
        Block liquidTransport = TransportTier.fastestLiquid(core);
        boolean missingInput = false;
        //One belt tile can carry only one ingredient stream. Remember which ingredient owns each
        //connector so a second item/liquid cannot silently merge into the same lane.
        Map<String, String> connectorOwner = new HashMap<>();

        //Inputs: for every required ingredient the unit checks what the sector actually offers.
        //If the ore is present and a drill of sufficient tier can mine it, it mines locally;
        //otherwise it falls back to the core stock through an item-configured unloader.
        for(Item item : content.items()){
            if(!consumes(factory, item)) continue;
            Block drill = bestDrill(item, core);
            Tile ore = drill == null ? null
                : sector.nearest(item, site.x, site.y, drill, factory, team);
            int sourceX, sourceY;
            Block sourceBlock;
            if(ore != null){
                plans.add(new BuildPlan(ore.x, ore.y, 0, drill));
                sourceBlock = drill;
                if(needsPower(drill)
                    && !AutoInfrastructure.hasPowerNear(ore.x, ore.y, drill, team)){
                    Building power = AutoInfrastructure.closestPowerSource(ore.x, ore.y, team);
                    if(power != null){
                        AutoInfrastructure.addPower(plans, new BuildPlan(ore.x, ore.y, 0, drill), power, team);
                    }
                }
                sourceX = ore.x;
                sourceY = ore.y;
                arc.util.Log.info("[auto-build] @ ore found in sector; drilling at @,@ with @",
                    item.name, ore.x, ore.y, drill.name);
            }else if(core.items != null && core.items.get(item) > 0
                && footprintGap(core.tileX(), core.tileY(), core.block.size,
                    site.x, site.y, factory.size) >= 3){
                //The sector cannot supply this ore (absent, unreachable, or no drill capable of
                //mining it). A stocked core is a valid fallback; mining a different ore is not.
                Tile unloader = addCoreUnloader(plans, core, item, site, team);
                if(unloader == null){
                    arc.util.Log.warn("[auto-build] cannot unload @ from the core for @",
                        item.name, factory.name);
                    missingInput = true;
                    continue;
                }
                sourceX = unloader.x;
                sourceY = unloader.y;
                sourceBlock = Blocks.unloader;
                arc.util.Log.warn("[auto-build] no usable @ ore in sector; using core stock through an unloader",
                    item.name);
            }else{
                arc.util.Log.warn("[auto-build] no source for @ required by @", item.name, factory.name);
                missingInput = true;
                continue;
            }

            Tile connector = addConnector(plans, factory, site, sourceX, sourceY,
                itemTransport, true, team, connectorOwner, "item:" + item.name);
            if(connector == null){
                arc.util.Log.warn("[auto-build] no free input connector for @", factory.name);
                missingInput = true;
            }else if(!addSupplyRoute(plans, sourceX, sourceY, sourceBlock,
                connector, itemTransport, Blocks.junction, team, factoryPlan)){
                arc.util.Log.warn("[auto-build] no clear item route into @", factory.name);
                missingInput = true;
            }
        }

        //liquids: pump on the liquid tile -> aligned conduit -> factory
        for(Liquid liquid : content.liquids()){
            if(!consumes(factory, liquid)) continue;
            Block pump = fastestPump(core);
            Tile liquidTile = sector.nearest(liquid, site.x, site.y, pump, factory, team);
            if(liquidTile == null){
                arc.util.Log.warn("[auto-build] no liquid @ source in sector for @",
                    liquid.localizedName, factory.name);
                missingInput = true;
                continue;
            }

            plans.add(new BuildPlan(liquidTile.x, liquidTile.y, 0, pump));
            if(needsPower(pump)
                && !AutoInfrastructure.hasPowerNear(liquidTile.x, liquidTile.y, pump, team)){
                Building power = AutoInfrastructure.closestPowerSource(liquidTile.x, liquidTile.y, team);
                if(power != null){
                    AutoInfrastructure.addPower(plans,
                        new BuildPlan(liquidTile.x, liquidTile.y, 0, pump), power, team);
                }
            }

            Tile connector = addConnector(plans, factory, site, liquidTile.x, liquidTile.y,
                liquidTransport, true, team, connectorOwner, "liquid:" + liquid.name);
            if(connector == null){
                arc.util.Log.warn("[auto-build] no free liquid connector for @", factory.name);
                missingInput = true;
            }else if(!addSupplyRoute(plans, liquidTile.x, liquidTile.y, pump,
                connector, liquidTransport, Blocks.ductRouter, team, factoryPlan)){
                arc.util.Log.warn("[auto-build] no clear liquid route into @", factory.name);
                missingInput = true;
            }
        }

        //Do not leave a factory on the map that can never run. A player would choose a different
        //site/source or wait for stock; silently substituting another ore/liquid is not valid.
        if(missingInput){
            arc.util.Log.warn("[auto-build] cancelled @: at least one required input cannot be connected",
                factory.name);
            plans.clear();
            return plans;
        }

        //the factory itself, after its inputs so it starts supplied
        plans.add(factoryPlan);

        //Heat crafters (e.g. the carbide crucible) refuse to run without a heater touching their
        //footprint. Reserve a free side before transport/power can consume the adjacent tiles.
        int heaters = 0;
        if(factory instanceof HeatCrafter heat && heat.heatRequirement > 0f){
            heaters = placeHeat(plans, factoryPlan, heat, core, team);
            arc.util.Log.info("[auto-build] heat for @: @ source(s) for @ units required",
                factory.name, heaters, heat.heatRequirement);
            if(heaters == 0){
                arc.util.Log.warn("[auto-build] cancelled @: no free side for a heat source",
                    factory.name);
                plans.clear();
                return plans;
            }
        }

        //output: factory -> aligned belt -> closest sink
        boolean itemOutput = producesItems(factory);
        Building sink = itemOutput ? AutoInfrastructure.closestSink(site.x, site.y, team, false) : null;
        arc.util.Log.info("[auto-build] output @ produces items: @, sink: @",
            factory.name, itemOutput, sink == null ? "none" : sink.block.name);
        if(sink != null){
            Tile connector = addConnector(plans, factory, site, sink.tileX(), sink.tileY(),
                itemTransport, false, team, new HashMap<>(), "output");
            if(connector != null){
                AutoInfrastructure.addRoute(plans, connector.x, connector.y,
                    sink.tileX(), sink.tileY(), itemTransport, Blocks.junction, team, null);
            }
        }

        if(needsPower(factory) && !ensureFactoryPower(plans, factoryPlan, core, team)){
            arc.util.Log.warn("[auto-build] cancelled @: could not provide a live power source",
                factory.name);
            plans.clear();
            return plans;
        }

        if(needsPower(factory)){
            int powerSources = 0;
            for(BuildPlan plan : plans){
                if(plan.block != null && plan.block.outputsPower) powerSources++;
            }
            arc.util.Log.info("[auto-build] power for @: @ planned generators",
                factory.name, powerSources);
        }

        arc.util.Log.info("[auto-build] chain for @ at @,@: @ plans",
            factory.name, site.x, site.y, plans.size);
        return plans;
    }

    /** Finds a legal site for a non-crafter structure, including drills and pumps on resources. */
    private static @Nullable BuildPlan buildStandalone(Block block, int rotation, Team team,
                                                       CoreBuild core){
        for(int radius = 2; radius <= Math.max(world.width(), world.height()); radius++){
            BuildPlan best = null;
            float bestDistance = Float.MAX_VALUE;
            int minX = Math.max(0, core.tileX() - radius);
            int maxX = Math.min(world.width() - 1, core.tileX() + radius);
            int minY = Math.max(0, core.tileY() - radius);
            int maxY = Math.min(world.height() - 1, core.tileY() + radius);
            for(int y = minY; y <= maxY; y++){
                for(int x = minX; x <= maxX; x++){
                    if(Math.max(Math.abs(x - core.tileX()), Math.abs(y - core.tileY())) != radius) continue;
                    Tile tile = world.tile(x, y);
                    if(tile == null || tile.block() != Blocks.air
                        || !canPlaceAnyRotation(block, team, x, y, rotation)
                        || !hasRequiredResource(block, tile)
                        || overlapsQueuedPlans(block, x, y, team)) continue;
                    float distance = distance(x - core.tileX(), y - core.tileY());
                    if(distance < bestDistance){
                        int placeRotation = validRotation(block, team, x, y, rotation);
                        best = new BuildPlan(x, y, placeRotation, block);
                        bestDistance = distance;
                    }
                }
            }
            if(best != null) return best;
        }
        arc.util.Log.warn("[auto-build] no legal site found for @", block.name);
        return null;
    }

    /** Avoids giving two structures in the unit/team queues the same footprint. */
    private static boolean overlapsQueuedPlans(Block candidate, int x, int y, Team team){
        for(Unit unit : Groups.unit){
            if(unit.team() != team || !unit.canBuild()) continue;
            for(BuildPlan pending : unit.plans){
                if(pending == null || pending.breaking || pending.block == null) continue;
                if(footprintGap(x, y, candidate.size, pending.x, pending.y,
                    pending.block.size) == 0) return true;
            }
        }

        var data = team.data();
        if(data != null){
            for(BlockPlan pending : data.plans){
                if(pending.block == null) continue;
                if(footprintGap(x, y, candidate.size, pending.x, pending.y,
                    pending.block.size) == 0) return true;
            }
        }
        return false;
    }

    /** Drills need a mined ore under them; pumps need a liquid body under them. */
    private static boolean hasRequiredResource(Block block, Tile tile){
        if(block instanceof Drill) return tile.drop() != null;
        if(block instanceof Pump) return tile.floor().liquidDrop != null;
        return true;
    }

    private static boolean canPlaceAnyRotation(Block block, Team team, int x, int y, int preferred){
        return validRotation(block, team, x, y, preferred) >= 0;
    }

    private static int validRotation(Block block, Team team, int x, int y, int preferred){
        for(int offset = 0; offset < 4; offset++){
            int rotation = (preferred + offset) & 3;
            if(Build.validPlaceIgnoreUnits(block, team, x, y, rotation, false, false)){
                return rotation;
            }
        }
        return -1;
    }

    /**
     * Picks a free site near the core where the consumed ore and liquid can be reached. Smaller
     * rings win, then distance to core and to the required sources.
     */
    public static Tile chooseSite(Block factory, Team team){
        return chooseSite(factory, team, SectorResources.get());
    }

    /** Site scoring driven by the sector inventory rather than repeated whole-map scans. */
    public static Tile chooseSite(Block factory, Team team, SectorResources sector){
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
                    if(overlapsQueuedPlans(factory, x, y, team)) continue;
                    //ore and liquid floors are reserved for drills/pumps, never for the factory
                    if(tile.drop() != null || tile.floor().liquidDrop != null) continue;
                    if(!placeable(factory, team, x, y)) continue;

                    //Prefer a site with a legal, exact source. Core stock is considered separately
                    //because it needs room for both an unloader and its transport line.
                    float score = distance(x - core.tileX(), y - core.tileY());
                    for(Item item : content.items()){
                        if(!consumes(factory, item)) continue;
                        Block drill = bestDrill(item, core);
                        float d = drill == null ? -1f
                            : sector.nearestDistance(item, x, y, drill, factory, team);
                        if(d < 0 && core.items != null && core.items.get(item) > 0
                            && footprintGap(core.tileX(), core.tileY(), core.block.size,
                                x, y, factory.size) >= 3){
                            d = distance(core.tileX() - x, core.tileY() - y);
                        }
                        score += d < 0 ? missingSourcePenalty : d * 2f;
                    }
                    for(Liquid liquid : content.liquids()){
                        if(!consumes(factory, liquid)) continue;
                        Block pump = fastestPump(core);
                        float d = sector.nearestDistance(liquid, x, y, pump, factory, team);
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

    /** Manhattan gap between two placed footprints; 2 leaves one tile for a connector. */
    static int footprintGap(int ax, int ay, int asize, int bx, int by, int bsize){
        int aminX = ax - (asize - 1) / 2, aminY = ay - (asize - 1) / 2;
        int bminX = bx - (bsize - 1) / 2, bminY = by - (bsize - 1) / 2;
        int dx = Math.max(0, Math.max(aminX - (bminX + bsize - 1),
            bminX - (aminX + asize - 1)));
        int dy = Math.max(0, Math.max(aminY - (bminY + bsize - 1),
            bminY - (aminY + asize - 1)));
        return dx + dy;
    }

    private static float footprintCenter(int anchor, int size){
        return anchor - (size - 1) / 2 + (size - 1) / 2f;
    }

    /** Adds a belt/conduit adjacent to the factory with a rotation pointing in or out. */
    private static Tile addConnector(Seq<BuildPlan> plans, Block factory, Tile site,
                                     int otherX, int otherY, Block transport,
                                     boolean input, Team team,
                                     Map<String, String> owner, String ingredient){
        int size = Math.max(1, factory.size);
        int middle = (size - 1) / 2;
        int minX = site.x - middle, minY = site.y - middle;
        int maxX = minX + size - 1, maxY = minY + size - 1;
        int[][] candidates = {
            {maxX + 1, minY + middle},
            {minX + middle, maxY + 1},
            {minX - 1, minY + middle},
            {minX + middle, minY - 1}
        };

        Tile best = null;
        int bestX = 0, bestY = 0;
        int bestRotation = 0;
        float bestDistance = Float.MAX_VALUE;
        for(int[] candidate : candidates){
            int x = candidate[0], y = candidate[1];
            Tile tile = world.tile(x, y);
            if(tile == null || tile.block() != Blocks.air || !placeable(transport, team, x, y)) continue;
            int rotation = Tile.relativeTo((float)x, (float)y,
                footprintCenter(site.x, size), footprintCenter(site.y, size));
            if(rotation < 0) rotation = 0;
            if(!input) rotation = (rotation + 2) % 4;

            String key = x + ":" + y;
            String previousOwner = owner.get(key);
            boolean occupiedByPlan = false;
            for(BuildPlan existing : plans){
                if(existing.x == x && existing.y == y){
                    //Only the same ingredient may reuse its own connector. A different stream
                    //merging into this tile would deliver the wrong item to the crafter.
                    if(ingredient.equals(previousOwner)
                        && existing.block == transport && existing.rotation == rotation){
                        return tile;
                    }
                    occupiedByPlan = true;
                    break;
                }
            }
            if(occupiedByPlan) continue;
            owner.put(key, ingredient);
            if(overlapsPlans(plans, x, y)) continue;

            float distance = distance(x - otherX, y - otherY);
            if(distance < bestDistance){
                best = tile;
                bestX = x;
                bestY = y;
                bestRotation = rotation;
                bestDistance = distance;
            }
        }
        if(best == null) return null;
        plans.add(new BuildPlan(bestX, bestY, bestRotation, transport));
        return best;
    }

    /**
     * Starts a feed route outside the full source footprint. Starting a path at the anchor of a
     * 2x2 drill used to let the pathfinder place belts on the drill's other footprint tiles,
     * because the drill is only a plan and not yet present in the world.
     */
    private static boolean addSupplyRoute(Seq<BuildPlan> plans, int sx, int sy, Block sourceBlock,
                                          Tile connector, Block transport, Block junction,
                                          Team team, BuildPlan avoid){
        int size = Math.max(1, sourceBlock == null ? 1 : sourceBlock.size);
        int middle = (size - 1) / 2;
        int minX = sx - middle, minY = sy - middle;
        int maxX = minX + size - 1, maxY = minY + size - 1;
        int cx = minX + middle, cy = minY + middle;
        int dx = connector.x - cx, dy = connector.y - cy;
        int[][] sides = Math.abs(dx) >= Math.abs(dy)
            ? (dx >= 0
                ? new int[][]{{1, 0}, {0, 1}, {0, -1}, {-1, 0}}
                : new int[][]{{-1, 0}, {0, 1}, {0, -1}, {1, 0}})
            : (dy >= 0
                ? new int[][]{{0, 1}, {1, 0}, {-1, 0}, {0, -1}}
                : new int[][]{{0, -1}, {1, 0}, {-1, 0}, {0, 1}});

        for(int[] side : sides){
            int x = side[0] > 0 ? maxX + 1 : (side[0] < 0 ? minX - 1 : cx);
            int y = side[1] > 0 ? maxY + 1 : (side[1] < 0 ? minY - 1 : cy);

            BuildPlan existing = null;
            for(BuildPlan plan : plans){
                if(plan.x == x && plan.y == y){
                    existing = plan;
                    break;
                }
            }
            if(existing != null){
                if(x == connector.x && y == connector.y && existing.block == transport) return true;
                continue;
            }
            if(overlapsPlans(plans, x, y)) continue;

            Tile tile = world.tile(x, y);
            if(tile == null || tile.block() != Blocks.air) continue;
            int rotation = Tile.relativeTo((float)x, (float)y,
                (float)connector.x, (float)connector.y);
            if(rotation < 0) rotation = 0;
            if(!placeable(transport, team, x, y)) continue;

            plans.add(new BuildPlan(x, y, rotation, transport));
            AutoInfrastructure.addRoute(plans, x, y, connector.x, connector.y,
                transport, junction, team, null, avoid);
            return true;
        }
        return false;
    }

    /** Places an item-configured unloader directly beside the core for core-stock feeds. */
    private static Tile addCoreUnloader(Seq<BuildPlan> plans, CoreBuild core, Item item,
                                       Tile site, Team team){
        int size = Math.max(1, core.block.size);
        int middle = (size - 1) / 2;
        int minX = core.tileX() - middle, minY = core.tileY() - middle;
        int maxX = minX + size - 1, maxY = minY + size - 1;
        int[][] candidates = {
            {maxX + 1, minY + middle},
            {minX + middle, maxY + 1},
            {minX - 1, minY + middle},
            {minX + middle, minY - 1}
        };

        Tile best = null;
        float bestDistance = Float.MAX_VALUE;
        for(int[] candidate : candidates){
            int x = candidate[0], y = candidate[1];
            Tile tile = world.tile(x, y);
            if(tile == null || tile.block() != Blocks.air
                || !placeable(Blocks.unloader, team, x, y)) continue;
            if(overlapsPlans(plans, x, y)) continue;
            float distance = distance(x - site.x, y - site.y);
            if(distance < bestDistance){
                best = tile;
                bestDistance = distance;
            }
        }
        if(best == null) return null;
        plans.add(new BuildPlan(best.x, best.y, 0, Blocks.unloader, item));
        return best;
    }

    /**
     * Places heat sources on free sides of a heat-consuming crafter, each facing the factory so
     * the heat is transferred. The sandbox heat source (1000 units, no inputs) is preferred; real
     * heaters are used outside the sandbox.
     *
     * @return the number of heat sources placed.
     */
    private static int placeHeat(Seq<BuildPlan> plans, BuildPlan factory, HeatCrafter crafter,
                                 CoreBuild core, Team team){
        Block heater = heatSourceBlock(core);
        if(heater == null) return 0;

        float output = heater instanceof HeatProducer producer
            ? producer.heatOutput : 10f;
        int needed = Math.max(1, (int)Math.ceil(crafter.heatRequirement / Math.max(1f, output)));

        int size = Math.max(1, factory.block.size);
        int middle = (size - 1) / 2;
        int minX = factory.x - middle, minY = factory.y - middle;
        int maxX = minX + size - 1, maxY = minY + size - 1;

        //Scan the whole ring one tile outside the factory footprint: any orthogonally adjacent
        //tile can host a heater, not just the four mid-sides which the input belts may occupy.
        int placed = 0;
        for(int y = minY - 1; y <= maxY + 1; y++){
            for(int x = minX - 1; x <= maxX + 1; x++){
                //corner tiles only touch the factory diagonally and cannot transfer heat
                boolean insideX = x >= minX && x <= maxX;
                boolean insideY = y >= minY && y <= maxY;
                if(insideX == insideY) continue;
                if(x < 0 || y < 0 || x >= world.width() || y >= world.height()) continue;
                if(overlapsPlans(plans, x, y)) continue;

                //the heater must face the factory: rotation points toward its center
                int rotation = Tile.relativeTo((float)x, (float)y,
                    minX + (size - 1) / 2f, minY + (size - 1) / 2f);
                if(rotation < 0) continue;

                Tile tile = world.tile(x, y);
                if(tile != null && tile.block() != Blocks.air) continue;
                if(!Build.validPlaceIgnoreUnits(heater, team, x, y, rotation, false, false)) continue;

                plans.add(new BuildPlan(x, y, rotation, heater));
                placed++;
                if(placed >= needed) return placed;
            }
        }
        return placed;
    }

    /** Sandbox heat source first (free, single tile, always on), then real heaters by output. */
    private static Block heatSourceBlock(CoreBuild core){
        if(Blocks.heatSource != null) return Blocks.heatSource;

        Block[] candidates = {Blocks.phaseHeater, Blocks.slagHeater, Blocks.electricHeater};
        Block best = null;
        float bestOutput = 0f;
        for(Block block : candidates){
            if(!(block instanceof HeatProducer producer) || !producer.unlockedNow()) continue;
            if(producer.heatOutput > bestOutput && core.items != null
                && canAffordCopies(producer, core, 1)){
                best = block;
                bestOutput = producer.heatOutput;
            }
        }
        return best;
    }

    /** Connects to a real existing generator or creates a connected solar farm beside the factory. */
    private static boolean ensureFactoryPower(Seq<BuildPlan> plans, BuildPlan factoryPlan,
                                              CoreBuild core, Team team){
        Building source = AutoInfrastructure.closestPowerSource(factoryPlan.x, factoryPlan.y, team);
        if(source != null && source.block.outputsPower){
            if(!AutoInfrastructure.hasPowerNear(factoryPlan.x, factoryPlan.y,
                factoryPlan.block, team)){
                int before = plans.size;
                AutoInfrastructure.addPower(plans, factoryPlan, source, team);
                if(plans.size == before
                    && !AutoInfrastructure.hasPowerNear(factoryPlan.x, factoryPlan.y,
                        factoryPlan.block, team)){
                    return false;
                }
            }
            return true;
        }

        float light = orbital.world.meta.Attribute.light.env()
            + (state.rules.lighting ? 1f - state.rules.ambientLight.a : 1f);
        float multiplier = state.rules.solarMultiplier * Math.max(0f, light);
        if(multiplier <= 0f || core.items == null) return false;

        float usage = factoryPlan.block.consPower == null ? 0f : factoryPlan.block.consPower.usage;
        // Large panels avoid the old 48-small-panel cap for power-hungry blocks such as the
        // phase weaver. In the test mode all resources are stocked, including phase fabric;
        // outside it, affordability/unlocks naturally fall back to ordinary solar panels.
        Block[] candidates = {Blocks.largeSolarPanel, Blocks.solarPanel};
        for(Block panel : candidates){
            if(!(panel instanceof SolarGenerator generator) || !panel.unlockedNow()) continue;
            float output = generator.powerProduction * multiplier;
            if(output <= 0f) continue;
            int required = Math.max(1, (int)Math.ceil(usage * 1.25f / output));
            if(required > 256 || !canAffordCopies(panel, core, required)) continue;
            if(placeConnectedSolarFarm(plans, factoryPlan, panel, required, team)) return true;
        }
        return false;
    }

    /**
     * Adds solar buildings one at a time, always touching the factory or the already planned
     * solar network. This handles multiblock panels as well as the 1x1 fallback and guarantees
     * the generated plans form one power graph instead of a visually nearby scatter.
     */
    private static boolean placeConnectedSolarFarm(Seq<BuildPlan> plans, BuildPlan factoryPlan,
                                                   Block panel, int required, Team team){
        int built = 0;
        int radius = 14;
        while(built < required){
            int bestX = Integer.MIN_VALUE, bestY = Integer.MIN_VALUE;
            int bestDistance = Integer.MAX_VALUE;
            for(int y = factoryPlan.y - radius; y <= factoryPlan.y + radius; y++){
                for(int x = factoryPlan.x - radius; x <= factoryPlan.x + radius; x++){
                    int distance = Math.abs(x - factoryPlan.x) + Math.abs(y - factoryPlan.y);
                    if(distance >= bestDistance || !touchesPlannedPower(plans, panel, x, y)) continue;
                    if(overlapsPlannedFootprint(plans, panel, x, y)) continue;
                    if(!Build.validPlaceIgnoreUnits(panel, team, x, y, 0, false, false)) continue;
                    bestX = x;
                    bestY = y;
                    bestDistance = distance;
                }
            }
            if(bestX == Integer.MIN_VALUE) return false;
            plans.add(new BuildPlan(bestX, bestY, 0, panel));
            built++;
        }
        return true;
    }

    private static boolean touchesPlannedPower(Seq<BuildPlan> plans, Block panel, int x, int y){
        for(BuildPlan plan : plans){
            if(plan.block == null || (!plan.block.hasPower && !plan.block.outputsPower)) continue;
            if(footprintGap(x, y, panel.size, plan.x, plan.y, plan.block.size) == 1) return true;
        }
        return false;
    }

    private static boolean overlapsPlannedFootprint(Seq<BuildPlan> plans, Block candidate,
                                                    int x, int y){
        int candidateMiddle = (candidate.size - 1) / 2;
        int candidateMinX = x - candidateMiddle, candidateMinY = y - candidateMiddle;
        int candidateMaxX = candidateMinX + candidate.size - 1;
        int candidateMaxY = candidateMinY + candidate.size - 1;
        for(BuildPlan plan : plans){
            if(plan.block == null) continue;
            int size = Math.max(1, plan.block.size);
            int minX = plan.x - (size - 1) / 2, minY = plan.y - (size - 1) / 2;
            int maxX = minX + size - 1, maxY = minY + size - 1;
            if(candidateMinX <= maxX && candidateMaxX >= minX
                && candidateMinY <= maxY && candidateMaxY >= minY) return true;
        }
        return false;
    }

    /** Whether a single-tile placement lies inside any planned building's footprint. */
    private static boolean overlapsPlans(Seq<BuildPlan> plans, int x, int y){
        for(BuildPlan plan : plans){
            if(plan.block == null) continue;
            int size = Math.max(1, plan.block.size);
            int minX = plan.x - (size - 1) / 2, minY = plan.y - (size - 1) / 2;
            if(x >= minX && x < minX + size && y >= minY && y < minY + size) return true;
        }
        return false;
    }

    private static boolean canAffordCopies(Block block, CoreBuild core, int copies){
        for(var stack : block.requirements){
            if(core.items.get(stack.item) < (long)stack.amount * copies) return false;
        }
        return true;
    }

    private static boolean needsPower(Block block){
        return block != null && block.hasPower && block.consPower != null
            && block.consPower.usage > 0f;
    }

    static boolean placeable(Block block, Team team, int x, int y){
        return block != null && Build.validPlaceIgnoreUnits(block, team, x, y, 0, false, false);
    }

    private static Block fastestDrill(CoreBuild core){
        Block[] candidates = hasRealPowerSource(core)
            ? new Block[]{Blocks.laserDrill, Blocks.pneumaticDrill, Blocks.mechanicalDrill}
            : new Block[]{Blocks.pneumaticDrill, Blocks.mechanicalDrill};
        return firstPayable(candidates, core, Blocks.mechanicalDrill);
    }

    /**
     * Fastest affordable drill that can actually mine {@code item}. An ore harder than the drill's
     * tier would leave a dead drill on the map, so the tier check filters candidates first and the
     * core-stock fallback handles items no available drill can mine (e.g. tungsten/tier 5 ore).
     */
    private static Block bestDrill(Item item, CoreBuild core){
        Block[] candidates = hasRealPowerSource(core)
            ? new Block[]{Blocks.laserDrill, Blocks.pneumaticDrill, Blocks.mechanicalDrill}
            : new Block[]{Blocks.pneumaticDrill, Blocks.mechanicalDrill};
        for(Block block : candidates){
            if(!(block instanceof Drill drill)) continue;
            if(drill.tier < item.hardness) continue;
            if(!affordable(block, core)) continue;
            return block;
        }
        return null;
    }

    private static boolean affordable(Block block, CoreBuild core){
        if(block == null || core == null || core.items == null) return false;
        for(var stack : block.requirements){
            if(core.items.get(stack.item) < stack.amount) return false;
        }
        return true;
    }

    private static Block fastestPump(CoreBuild core){
        Block[] candidates = hasRealPowerSource(core)
            ? new Block[]{Blocks.impulsePump, Blocks.rotaryPump, Blocks.mechanicalPump}
            : new Block[]{Blocks.mechanicalPump};
        return firstPayable(candidates, core, Blocks.mechanicalPump);
    }

    private static boolean hasRealPowerSource(CoreBuild core){
        Building source = AutoInfrastructure.closestPowerSource(
            core.tileX(), core.tileY(), core.team);
        return source != null && source.block.outputsPower;
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

    /** Modded blocks can have short filter arrays, so never index them blindly. */
    private static boolean consumes(Block block, Item item){
        return item != null && item.id < block.itemFilter.length && block.itemFilter[item.id];
    }

    private static boolean consumes(Block block, Liquid liquid){
        return liquid != null && liquid.id < block.liquidFilter.length && block.liquidFilter[liquid.id];
    }

    private static boolean producesItems(Block block){
        return block instanceof GenericCrafter crafter
            && ((crafter.outputItem != null && crafter.outputItem.amount > 0)
                || (crafter.outputItems != null && crafter.outputItems.length > 0));
    }

    static float distance(int dx, int dy){
        return dx * dx + dy * dy;
    }
}
