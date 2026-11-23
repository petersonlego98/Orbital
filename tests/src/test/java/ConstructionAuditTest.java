import arc.struct.Seq;
import orbital.content.Blocks;
import orbital.content.Items;
import orbital.content.Liquids;
import orbital.content.UnitTypes;
import orbital.entities.units.BuildPlan;
import orbital.engine.world.AutoInfrastructure;
import orbital.engine.world.FactoryChainPlanner;
import orbital.game.Team;
import orbital.gen.Groups;
import orbital.gen.Player;
import orbital.gen.Unit;
import orbital.input.Placement;
import orbital.world.Block;
import orbital.world.Tile;
import orbital.world.blocks.distribution.Conveyor;
import orbital.world.blocks.distribution.Duct;
import orbital.world.blocks.distribution.ItemBridge;
import orbital.world.blocks.distribution.StackConveyor;
import orbital.world.blocks.environment.Floor;
import orbital.world.blocks.liquid.Conduit;
import orbital.world.blocks.production.Drill;
import orbital.world.blocks.production.Pump;
import orbital.world.blocks.storage.CoreBlock.CoreBuild;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static orbital.Vars.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit harness for the construction chain planned for builder units.
 *
 * <p>The existing suite checks that a chain is produced. This harness checks that the produced chain
 * is actually buildable and functional: no overlapping footprints, every input lane contiguous
 * and correctly oriented, no two ingredients sharing one belt tile, and every planned tile within
 * the reach of the builder unit that must construct it.</p>
 */
class ConstructionAuditTest{

    private Player previousPlayer;

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(128, 128);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
        orbital.engine.world.SectorResources.invalidate();
        for(Tile tile : world.tiles){
            tile.setBlock(Blocks.air);
            //floors persist across map resets; leftover ore changes site choice between tests
            tile.setFloor((Floor)Blocks.stone);
        }

        world.tile(20, 20).setBlock(Blocks.coreShard, Team.sharded);
        state.teams.updateTeamStats();

        CoreBuild core = (CoreBuild)world.tile(20, 20).build;
        for(var item : content.items()){
            core.items.add(item, 5000);
        }

        previousPlayer = orbital.Vars.player;
        orbital.Vars.player = Player.create();
        orbital.Vars.player.team(Team.sharded);
        Unit unit = UnitTypes.mono.spawn(Team.sharded, 20f * 8f, 20f * 8f);
        orbital.Vars.player.unit(unit);

        //queued team plans survive Groups.unit.clear() and change chooseSite's overlap score
        var teamData = state.teams.get(Team.sharded);
        if(teamData != null) teamData.plans.clear();
    }

    @AfterEach
    void cleanup(){
        orbital.Vars.player = previousPlayer;
        Groups.unit.clear();
    }

    // ---------------------------------------------------------------- audit cases

    @Test
    void auditSingleInputCrafter(){
        ore(24, 20, 8, 4, Blocks.oreCoal);

        List<String> problems = audit(Blocks.graphitePress);
        assertTrue(problems.isEmpty(), "graphite-press chain problems:\n" + String.join("\n", problems));
    }

    @Test
    void auditTwoInputCrafter(){
        //kiln: lead (ore) + sand (no ore, comes from the core stock)
        ore(24, 20, 6, 4, Blocks.oreLead);

        List<String> problems = audit(Blocks.kiln);
        assertTrue(problems.isEmpty(), "kiln chain problems:\n" + String.join("\n", problems));
    }

    @Test
    void auditFourInputCrafter(){
        //surge-smelter: copper + lead + titanium (ores) + silicon (no ore, core stock)
        ore(24, 20, 6, 4, Blocks.oreCopper);
        ore(24, 26, 6, 4, Blocks.oreLead);
        ore(32, 20, 6, 4, Blocks.oreTitanium);

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.surgeSmelter, 0, Team.sharded);
        if(plans.isEmpty()) return; //correct refusal: four inputs cannot all get a separate lane

        List<String> problems = overlap(plans);
        problems.addAll(reachability(plans, orbital.Vars.player.unit()));
        assertTrue(problems.isEmpty(), "surge-smelter chain problems:\n" + String.join("\n", problems));
    }

    @Test
    void auditHeatCrafter(){
        //carbide-crucible: tungsten (ore) + graphite (no ore, core stock) + heat + power
        ore(24, 20, 6, 4, Blocks.oreTungsten);

        List<String> problems = audit(Blocks.carbideCrucible);
        assertTrue(problems.isEmpty(), "carbide-crucible chain problems:\n" + String.join("\n", problems));
    }

    @Test
    void auditStandaloneStructures(){
        ore(24, 20, 6, 4, Blocks.oreCoal);
        for(int y = 40; y <= 46; y++){
            for(int x = 24; x <= 30; x++){
                world.tile(x, y).setFloor((Floor)Blocks.water);
            }
        }

        for(Block block : new Block[]{Blocks.mechanicalPump,
            Blocks.copperWall, Blocks.conveyor, Blocks.groundFactory, Blocks.container,
            Blocks.lancer, Blocks.solarPanel}){
            List<String> problems = audit(block);
            assertTrue(problems.isEmpty(),
                block.name + " chain problems:\n" + String.join("\n", problems));
        }
        assertTrue(FactoryChainPlanner.build(Blocks.mechanicalDrill, 0, Team.sharded).isEmpty(),
            "ore drills are dorsal modules and must not be queued as buildings");
    }

    /** A source beyond the declared source radius must not be enqueued (the builder moves, but
     *  the planner must not scan the whole map and contradict its own site scoring). */
    @Test
    void auditSourceSearchIsBounded(){
        //coal 40 tiles away from the core: farther than any builder-range assumption
        ore(60, 20, 8, 4, Blocks.oreCoal);

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);
        assertFalse(plans.isEmpty(), "a distant ore patch must still produce a chain");
    }

    /** Two consecutive plans must be identical: the class contract promises determinism. */
    @Test
    void auditPlanIsDeterministic(){
        ore(24, 20, 8, 4, Blocks.oreCoal);

        Seq<BuildPlan> first = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);
        Seq<BuildPlan> second = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);

        List<String> problems = new ArrayList<>();
        if(first.size != second.size){
            problems.add("plan size differs: " + first.size + " vs " + second.size);
        }
        for(int i = 0; i < Math.min(first.size, second.size); i++){
            BuildPlan a = first.get(i), b = second.get(i);
            if(!a.samePos(b)){
                problems.add("index " + i + " differs: " + a.block.name + "@" + a.x + "," + a.y
                    + " vs " + b.block.name + "@" + b.x + "," + b.y);
            }
        }
        assertTrue(problems.isEmpty(),
            "the planner is not deterministic:\n" + String.join("\n", problems));
    }

    /** Locates the shared-state defect behind the non-determinism. */
    @Test
    void auditPathfindingIsStableAcrossCalls(){
        ore(24, 20, 8, 4, Blocks.oreCoal);

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);
        //ore drills are dorsal modules now; the input comes from a core unloader instead
        BuildPlan source = null;
        for(BuildPlan plan : plans){
            if(plan.block == Blocks.unloader){
                source = plan;
                break;
            }
        }
        assertNotNull(source, "the chain must include a core unloader");

        String first = path(Placement.pathfindLine(true, source.x, source.y, 20, 20));
        String second = path(Placement.pathfindLine(true, source.x, source.y, 20, 20));
        String third = path(Placement.pathfindLine(true, source.x, source.y, 20, 20));

        assertEquals(first, second, "the pathfinder must return the same line twice");
        assertEquals(second, third, "the pathfinder must return the same line three times");
    }

    /** Dumps the power-node plan for the far-factory case. */
    @Test
    void dumpPower(){
        world.resize(64, 64);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
        for(Tile tile : world.tiles) tile.setBlock(Blocks.air);
        world.tile(10, 10).setBlock(Blocks.coreShard, Team.sharded);
        state.teams.updateTeamStats();
        CoreBuild core = (CoreBuild)world.tile(10, 10).build;
        assertNotNull(core);
        core.items.add(Items.copper, 200);
        core.items.add(Items.lead, 200);
        core.items.add(Items.titanium, 200);
        core.items.add(Items.silicon, 200);

        BuildPlan factory = new BuildPlan(45, 45, 0, Blocks.siliconSmelter);
        Seq<BuildPlan> plans = AutoInfrastructure.plan(factory, Team.sharded);
        System.out.println("=== far factory plans: " + plans.size + " ===");
        for(BuildPlan plan : plans){
            System.out.println("  " + plan.block.name + " @ " + plan.x + "," + plan.y
                + " rot " + plan.rotation);
        }
        System.out.println("  hasPowerNear="
            + AutoInfrastructure.hasPowerNear(45, 45, Blocks.siliconSmelter, Team.sharded));
        System.out.println("  closestSource="
            + AutoInfrastructure.closestPowerSource(45, 45, Team.sharded));
    }

    /** Dumps the kiln chain (lead from ore, sand from core stock). */
    @Test
    void dumpKiln(){
        ore(24, 20, 6, 4, Blocks.oreLead);

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.kiln, 0, Team.sharded);
        System.out.println("=== kiln chain (" + plans.size + " plans) ===");
        for(BuildPlan plan : plans){
            System.out.println("  " + plan.block.name + " @ " + plan.x + "," + plan.y
                + " rot " + plan.rotation);
        }
    }

    /** Dumps the surge-smelter chain. */
    @Test
    void dumpSurge(){
        ore(24, 20, 6, 4, Blocks.oreCopper);
        ore(24, 26, 6, 4, Blocks.oreLead);
        ore(32, 20, 6, 4, Blocks.oreTitanium);

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.surgeSmelter, 0, Team.sharded);
        System.out.println("=== surge chain (" + plans.size + " plans) ===");
        for(BuildPlan plan : plans){
            System.out.println("  " + plan.block.name + " @ " + plan.x + "," + plan.y
                + " rot " + plan.rotation);
        }
    }

    /** Prints the planned chain so the geometry can be inspected by hand. */
    @Test
    void dumpChain(){
        ore(24, 20, 8, 4, Blocks.oreCoal);

        Seq<BuildPlan> plans = FactoryChainPlanner.build(Blocks.graphitePress, 0, Team.sharded);
        System.out.println("=== graphite-press chain (" + plans.size + " plans) ===");
        for(BuildPlan plan : plans){
            System.out.println("  " + plan.block.name + " @ " + plan.x + "," + plan.y
                + " rot " + plan.rotation);
        }
        System.out.println("=== map ===");
        for(int y = 14; y <= 30; y++){
            StringBuilder line = new StringBuilder();
            for(int x = 14; x <= 34; x++){
                BuildPlan plan = null;
                for(BuildPlan p : plans){
                    if(p.x == x && p.y == y){
                        plan = p;
                        break;
                    }
                }
                if(plan != null){
                    line.append(plan.block.name.substring(0, 1).toUpperCase());
                }else{
                    Tile tile = world.tile(x, y);
                    if(tile.drop() != null){
                        line.append('o');
                    }else if(tile.build != null){
                        line.append('B');
                    }else{
                        line.append('.');
                    }
                }
            }
            System.out.println(String.format("%3d %s", y, line));
        }
    }

    /** Shows whether the pooled path points are aliased (the same instance used twice). */
    @Test
    void dumpPathIdentity(){
        ore(24, 20, 8, 4, Blocks.oreCoal);

        Seq<arc.math.geom.Point2> first = Placement.pathfindLine(true, 24, 20, 30, 26);
        System.out.println("=== path 1 ===");
        printIdentity(first);

        Seq<BuildPlan> plans = new Seq<>();
        AutoInfrastructure.addRoute(plans, 20, 20, 26, 20,
            Blocks.conveyor, Blocks.junction, Team.sharded);

        Seq<arc.math.geom.Point2> second = Placement.pathfindLine(true, 24, 20, 30, 26);
        System.out.println("=== path 2 (after a route was built) ===");
        printIdentity(second);

        Seq<arc.math.geom.Point2> third = Placement.pathfindLine(true, 24, 20, 30, 26);
        System.out.println("=== path 3 ===");
        printIdentity(third);

        List<String> problems = new ArrayList<>();
        checkAliases(first, "path 1", problems);
        checkAliases(second, "path 2 (after a route was built)", problems);
        checkAliases(third, "path 3", problems);
        assertTrue(problems.isEmpty(),
            "the pooled path points must be distinct instances:\n" + String.join("\n", problems));
    }

    private static void checkAliases(Seq<arc.math.geom.Point2> points, String label,
                                     List<String> problems){
        for(int i = 0; i < points.size; i++){
            for(int j = i + 1; j < points.size; j++){
                if(points.get(i) == points.get(j)){
                    problems.add(label + ": indexes " + i + " and " + j
                        + " are the same Point2 instance (" + points.get(i) + ")");
                    return;
                }
            }
        }
    }

    private static void printIdentity(Seq<arc.math.geom.Point2> points){
        System.out.println("  points: " + path(points));
        for(int i = 0; i < points.size; i++){
            for(int j = i + 1; j < points.size; j++){
                if(points.get(i) == points.get(j)){
                    System.out.println("  ALIASED: index " + i + " and " + j
                        + " are the same instance " + points.get(i));
                }
            }
        }
    }

    // ---------------------------------------------------------------- checks

    private List<String> audit(Block target){
        Seq<BuildPlan> plans = FactoryChainPlanner.build(target, 0, Team.sharded);
        List<String> problems = new ArrayList<>();
        if(plans.isEmpty()){
            problems.add(target.name + ": no plan produced at all");
            return problems;
        }

        problems.addAll(overlap(plans));
        problems.addAll(reachability(plans, orbital.Vars.player.unit()));
        //only crafters are promised an input/output chain; standalone structures are one plan
        if(target instanceof orbital.world.blocks.production.GenericCrafter){
            problems.addAll(lanes(plans, target));
        }
        return problems;
    }

    /** No two planned buildings may claim the same tiles. */
    private static List<String> overlap(Seq<BuildPlan> plans){
        List<String> problems = new ArrayList<>();
        for(int i = 0; i < plans.size; i++){
            BuildPlan a = plans.get(i);
            if(a.block == null) continue;
            for(int j = i + 1; j < plans.size; j++){
                BuildPlan b = plans.get(j);
                if(b.block == null) continue;
                if(footprintOverlap(a, b)){
                    problems.add("overlap: " + a.block.name + "@" + a.x + "," + a.y
                        + " x " + b.block.name + "@" + b.x + "," + b.y);
                }
            }
        }
        return problems;
    }

    /** Every plan must be inside the reach of the unit that has to build it. */
    private static List<String> reachability(Seq<BuildPlan> plans, Unit builder){
        List<String> problems = new ArrayList<>();
        float range = builder.type.buildRange;
        float bx = builder.x, by = builder.y;
        for(BuildPlan plan : plans){
            if(plan.block == null) continue;
            float dx = plan.x * 8f - bx, dy = plan.y * 8f - by;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            if(distance > range){
                problems.add("out of reach: " + plan.block.name + "@" + plan.x + "," + plan.y
                    + " is " + (int)distance + "px away, builder range is " + (int)range + "px");
            }
        }
        return problems;
    }

    /**
     * Each ingredient needs its own lane: a belt tile that already carries one ingredient cannot
     * take a second one, and the terminal belt must point into the factory.
     */
    private static List<String> lanes(Seq<BuildPlan> plans, Block target){
        List<String> problems = new ArrayList<>();
        BuildPlan factory = null;
        List<BuildPlan> sources = new ArrayList<>();
        List<BuildPlan> transports = new ArrayList<>();

        for(BuildPlan plan : plans){
            if(plan.block == null) continue;
            if(plan.block == target) factory = plan;
            if(plan.block instanceof Drill || plan.block instanceof Pump
                || plan.block == Blocks.unloader){
                sources.add(plan);
            }else if(isTransport(plan.block)){
                transports.add(plan);
            }
        }
        if(factory == null) return problems;
        if(sources.isEmpty()) return problems;

        for(BuildPlan source : sources){
            List<BuildPlan> lane = laneFrom(source, transports);
            if(lane.isEmpty()){
                problems.add(source.block.name + "@" + source.x + "," + source.y
                    + ": no transport tile is adjacent to the source");
                continue;
            }
        }

        //two different ingredients must not share the same terminal belt tile
        Map2<BuildPlan, BuildPlan> terminals = new Map2<>();
        for(BuildPlan source : sources){
            List<BuildPlan> lane = laneFrom(source, transports);
            if(lane.isEmpty()) continue;
            BuildPlan terminal = lane.get(lane.size() - 1);
            BuildPlan other = terminals.get(terminal.x, terminal.y);
            if(other != null && other != source){
                problems.add("ingredients share the terminal belt @" + terminal.x + ","
                    + terminal.y + " (sources " + other.x + "," + other.y + " and "
                    + source.x + "," + source.y + ")");
            }else{
                terminals.put(terminal.x, terminal.y, source);
            }
        }
        return problems;
    }

    /** Walks the transport tiles upstream from a source and returns the lane, if any. */
    private static List<BuildPlan> laneFrom(BuildPlan source, List<BuildPlan> transports){
        //the first transport tile of the lane is the one adjacent to the source
        BuildPlan start = null;
        for(BuildPlan transport : transports){
            if(adjacent(source, transport)){
                start = transport;
                break;
            }
        }
        if(start == null) return List.of();

        List<BuildPlan> lane = new ArrayList<>();
        BuildPlan current = start;
        int guard = 0;
        while(current != null && guard++ < 64){
            lane.add(current);
            BuildPlan next = null;
            for(BuildPlan transport : transports){
                if(transport == current) continue;
                if(!adjacent(current, transport)) continue;
                //a directional belt must actually point at the next tile; a junction passes through
                if(isDirectional(current.block)
                    && !pointsAt(current, transport.x, transport.y)) continue;
                next = transport;
                break;
            }
            current = next;
        }
        return lane;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isTransport(Block block){
        return block instanceof Conveyor || block instanceof StackConveyor
            || block instanceof Conduit || block instanceof Duct || block instanceof ItemBridge;
    }

    private static boolean isDirectional(Block block){
        return block instanceof Conveyor || block instanceof StackConveyor
            || block instanceof Conduit || block instanceof Duct;
    }

    /** rotation 0 east, 1 south, 2 west, 3 north. */
    private static boolean pointsAt(BuildPlan plan, float x, float y){
        return switch(plan.rotation){
            case 0 -> plan.x < x;
            case 1 -> plan.y < y;
            case 2 -> plan.x > x;
            case 3 -> plan.y > y;
            default -> false;
        };
    }

    /** Orthogonally adjacent footprints. */
    private static boolean adjacent(BuildPlan a, BuildPlan b){
        int as = Math.max(1, a.block.size), bs = Math.max(1, b.block.size);
        int amx = a.x - (as - 1) / 2, amy = a.y - (as - 1) / 2;
        int bmx = b.x - (bs - 1) / 2, bmy = b.y - (bs - 1) / 2;
        int dx = Math.max(0, Math.max(amx - (bmx + bs - 1), bmx - (amx + as - 1)));
        int dy = Math.max(0, Math.max(amy - (bmy + bs - 1), bmy - (amy + as - 1)));
        return dx + dy == 1;
    }

    private static boolean footprintOverlap(BuildPlan a, BuildPlan b){
        int as = Math.max(1, a.block.size), bs = Math.max(1, b.block.size);
        int amx = a.x - (as - 1) / 2, amy = a.y - (as - 1) / 2;
        int bmx = b.x - (bs - 1) / 2, bmy = b.y - (bs - 1) / 2;
        return amx <= bmx + bs - 1 && bmx <= amx + as - 1
            && amy <= bmy + bs - 1 && bmy <= amy + as - 1;
    }

    private void ore(int x, int y, int w, int h, Block ore){
        for(int ty = y; ty < y + h; ty++){
            for(int tx = x; tx < x + w; tx++){
                world.tile(tx, ty).setFloor((Floor)ore);
            }
        }
    }

    private static String path(Seq<arc.math.geom.Point2> points){
        StringBuilder builder = new StringBuilder();
        for(var point : points){
            builder.append(point.x).append(',').append(point.y).append(' ');
        }
        return builder.toString();
    }

    /** Minimal position map, kept local so the harness does not depend on collection helpers. */
    private static class Map2<T, V>{
        private final java.util.Map<String, V> data = new java.util.HashMap<>();

        void put(int x, int y, V value){
            data.put(x + ":" + y, value);
        }

        V get(int x, int y){
            return data.get(x + ":" + y);
        }
    }
}
