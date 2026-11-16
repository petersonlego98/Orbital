package orbital.game;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.Nullable;
import arc.util.noise.*;
import orbital.content.*;
import orbital.entities.*;
import orbital.entities.units.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.maps.*;
import orbital.type.*;
import orbital.type.unit.ErekirUnitType;
import orbital.ui.dialogs.ModuleTestSetupDialog;
import orbital.world.*;
import orbital.world.blocks.environment.*;

import static orbital.Vars.*;

/** Isolated, fixed-map environment used to validate aerial modules against waves of air units. */
public final class TestNewFeaturesMode{
    public static final String rulesName = "test-new-features";
    /** The map editor's maximum size, used so the showcase spans the largest sector the game allows. */
    public static final int mapSize = 800;
    /** Fixed seed for the showcase terrain, so every visit shows the same planet. */
    private static final int showcaseSeed = 20261113;
    /** Delay written to the wave clock, in ticks. Waves are manual, so this is only a safety gap. */
    public static final float waveSpacing = 45f * 60f;
    /** Difficulty used to build the base air-only wave progression. */
    public static final float waveDifficulty = 0.55f;

    private static Map testMap;
    private static boolean initializeMono;
    private static boolean listenerInstalled;
    private static @Nullable ModuleTestSetupDialog setupDialog;

    /** Air unit chosen in the setup menu, or null before a choice is made. */
    public static @Nullable UnitType selectedType;
    /** True while the setup menu is still open; waves stay disabled until it closes. */
    public static boolean setupOpen;
    /** True once the air-wave defense has started. */
    public static boolean wavesStarted;

    /** Team that receives structures placed with the enemy-building tool. */
    public static Team enemyTeam = Team.crux;
    /** Block armed by the enemy-building tool; null means nothing is selected. */
    public static @Nullable Block enemyBlock;
    /** When true, the enemy-building tool removes whatever is under the cursor instead of placing. */
    public static boolean enemyRemoving;
    /** Block armed by the player-building tool; null means nothing is selected. */
    public static @Nullable Block playerBlock;

    private TestNewFeaturesMode(){
    }

    public static boolean active(){
        return state != null && state.rules != null && rulesName.equals(state.rules.modeName);
    }

    /**
     * @return whether the build menu offers this block in the test mode. The test map is a
     * showcase of both planets, so every visible, placeable block is available regardless of
     * research unlocks or the environment bits of the current planet.
     */
    public static boolean blockAvailable(Block block){
        //no environmentBuildable() here: the showcase map is on Qatia, while the blocks belong
        //to Serpulo/Erekir tech trees. isOnPlanet(qatia) is false for both, which would empty
        //the whole catalog. Build.validPlaceIgnoreUnits has the matching test-mode bypass.
        return block != null && block.isVisible() && block.placeablePlayer;
    }

    /** Starts a fresh, non-persistent test session on the built-in flat map. */
    public static void start(){
        if(testMap == null){
            testMap = new Map(StringMap.of(
                "name", Core.bundle.get("mode.testfeatures.map", "Teste Novos Recursos"),
                "author", "Orbital"
            ));
            testMap.width = mapSize;
            testMap.height = mapSize;
            world.addMapLoader(testMap, () -> {
                state.map = testMap;
                world.loadGenerator(mapSize, mapSize, tiles -> {
                    tiles.fill();
                    generateShowcase(tiles);

                    //Air waves need somewhere to enter. Four edge markers keep the whole border
                    //in use instead of piling every wave on one side of the map.
                    int center = mapSize / 2;
                    int edge = 3;
                    tiles.getn(center, edge).setOverlay(Blocks.spawn);
                    tiles.getn(center, mapSize - edge - 1).setOverlay(Blocks.spawn);
                    tiles.getn(edge, center).setOverlay(Blocks.spawn);
                    tiles.getn(mapSize - edge - 1, center).setOverlay(Blocks.spawn);

                    tiles.getn(center, center).setBlock(Blocks.coreShard, Team.sharded);
                });
            });
        }

        if(!listenerInstalled){
            Events.run(Trigger.newGame, TestNewFeaturesMode::spawnInitialMono);
            listenerInstalled = true;
        }

        Rules rules = Gamemode.testFeatures.apply(new Rules());
        rules.planet = Planets.qatia;
        rules.waves = false;
        rules.waveTimer = false;
        rules.loadout = resourceLoadout();
        //unit creation is free, but the normal unit cap still applies
        rules.disableUnitCap = false;

        selectedType = null;
        setupOpen = true;
        wavesStarted = false;
        playerBlock = null;
        enemyBlock = null;
        enemyRemoving = false;
        //a stress battle from a previous session must never leak into a fresh one
        TestEnginePhase.reset();

        //the tutorial belongs to this round, not to the save: replay the four hints on every
        //fresh visit so a player testing a different combination sees the explanations again
        if(ui != null && ui.hints != null) ui.hints.resetModuleHints();

        //every test session starts with engine evidence in the log: the parallel kernels against
        //their sequential reference, and how the engine scales on this machine
        arc.util.Log.info(orbital.engine.validation.EngineValidation.run().summary());

        initializeMono = true;
        //createSave = false: this is a disposable test session, it must never touch the save list
        control.playMap(testMap, rules, false, false);
    }

    //region showcase planet

    /**
     * Builds the "new resources" showcase: the largest sector allowed by the game, carrying every
     * natural floor, ore, wall, liquid body, tree and decoration from both Serpulo and Erekir.
     */
    private static void generateShowcase(Tiles tiles){
        Rand rand = new Rand(showcaseSeed);

        paintBiomes(tiles);
        placeLiquidBodies(tiles, rand);
        paintAlienPatches(tiles, rand);
        placeOreFields(tiles, rand);
        placeWallOutcrops(tiles, rand);
        scatterWallOres(tiles, rand);
        decorate(tiles, rand);
        placeVents(tiles, rand);
        clearProtectedAreas(tiles);
    }

    /** Paints the base terrain by blending heat, moisture, rock and Erekir-exotic noise fields. */
    private static void paintBiomes(Tiles tiles){
        for(Tile tile : tiles){
            double heat = Simplex.noise2d(showcaseSeed, 4, 0.55, 1.0 / 180.0, tile.x, tile.y);
            double moisture = Simplex.noise2d(showcaseSeed + 1, 4, 0.55, 1.0 / 210.0, tile.x, tile.y);
            double detail = Simplex.noise2d(showcaseSeed + 2, 4, 0.5, 1.0 / 34.0, tile.x, tile.y);
            double rocks = Simplex.noise2d(showcaseSeed + 3, 3, 0.5, 1.0 / 70.0, tile.x, tile.y);
            double exotic = Simplex.noise2d(showcaseSeed + 4, 3, 0.5, 1.0 / 55.0, tile.x, tile.y);

            Floor floor;
            if(rocks > 0.38){
                floor = rockyFloor(detail);
            }else if(heat > 0.32){
                floor = volcanicFloor(detail);
            }else if(heat < -0.32){
                floor = frozenFloor(detail);
            }else if(moisture > 0.28){
                floor = lushFloor(detail);
            }else if(moisture < -0.25){
                floor = aridFloor(detail);
            }else{
                floor = temperateFloor(detail);
            }

            //the eastern third carries Erekir's crystal, carbon, ferric, red and arkyic biomes
            if(tile.x > tiles.width * 0.70f && exotic > -0.15){
                floor = erekirFloor(detail, exotic);
            }

            tile.setFloor(floor);
        }
    }

    private static Floor temperateFloor(double d){
        if(d > 0.5) return Blocks.grass.asFloor();
        if(d > 0.1) return Blocks.dirt.asFloor();
        if(d < -0.5) return Blocks.stone.asFloor();
        return Blocks.grass.asFloor();
    }

    private static Floor lushFloor(double d){
        if(d > 0.52) return Blocks.grass.asFloor();
        if(d > 0.12) return Blocks.moss.asFloor();
        if(d < -0.62) return Blocks.mud.asFloor();
        if(d < -0.42) return Blocks.sporeMoss.asFloor();
        return Blocks.dirt.asFloor();
    }

    private static Floor aridFloor(double d){
        if(d > 0.52) return Blocks.darksand.asFloor();
        if(d > 0.12) return Blocks.sand.asFloor();
        if(d < -0.6) return Blocks.yellowStonePlates.asFloor();
        if(d < -0.4) return Blocks.yellowStone.asFloor();
        if(d < -0.1) return Blocks.salt.asFloor();
        return Blocks.dirt.asFloor();
    }

    private static Floor frozenFloor(double d){
        if(d > 0.45) return Blocks.snow.asFloor();
        if(d > -0.1) return Blocks.iceSnow.asFloor();
        if(d < -0.55) return Blocks.redIce.asFloor();
        if(d < -0.25) return Blocks.ice.asFloor();
        return Blocks.snow.asFloor();
    }

    private static Floor volcanicFloor(double d){
        if(d > 0.55) return Blocks.magmarock.asFloor();
        if(d > 0.18) return Blocks.hotrock.asFloor();
        if(d < -0.45) return Blocks.charr.asFloor();
        if(d < -0.1) return Blocks.craters.asFloor();
        return Blocks.basalt.asFloor();
    }

    private static Floor rockyFloor(double d){
        if(d > 0.55) return Blocks.regolith.asFloor();
        if(d > 0.25) return Blocks.dacite.asFloor();
        if(d < -0.55) return Blocks.shale.asFloor();
        if(d < -0.35) return Blocks.roughRhyolite.asFloor();
        if(d < -0.18) return Blocks.rhyolite.asFloor();
        if(d < -0.05) return Blocks.rhyoliteCrater.asFloor();
        return Blocks.stone.asFloor();
    }

    private static Floor erekirFloor(double d, double e){
        if(e > 0.55) return Blocks.crystallineStone.asFloor();
        if(e > 0.34) return Blocks.crystalFloor.asFloor();
        if(e > 0.14) return Blocks.beryllicStone.asFloor();
        if(e > -0.02) return Blocks.carbonStone.asFloor();
        if(e > -0.2) return Blocks.ferricStone.asFloor();
        if(e > -0.34) return Blocks.ferricCraters.asFloor();
        if(e > -0.46) return Blocks.redIce.asFloor();
        if(e > -0.58) return Blocks.redStone.asFloor();
        if(e > -0.7) return Blocks.denseRedStone.asFloor();
        if(d > 0.35) return Blocks.yellowStonePlates.asFloor();
        return Blocks.arkyicStone.asFloor();
    }

    /** Scatters every liquid pool the game knows: water, tainted water, oil, slag, cryofluid and arkycite. */
    private static void placeLiquidBodies(Tiles tiles, Rand rand){
        //Serpulo water, in shallow, deep and spore-tainted forms
        for(int i = 0; i < 14; i++) placeLake(tiles, rand, Blocks.deepwater.asFloor(), Blocks.water.asFloor(), rand.random(20, 46));
        for(int i = 0; i < 6; i++) placeLake(tiles, rand, Blocks.water.asFloor(), Blocks.sandWater.asFloor(), rand.random(12, 28));
        for(int i = 0; i < 5; i++) placeLake(tiles, rand, Blocks.deepTaintedWater.asFloor(), Blocks.taintedWater.asFloor(), rand.random(14, 32));
        for(int i = 0; i < 4; i++) placeLake(tiles, rand, Blocks.darksandWater.asFloor(), Blocks.darksandTaintedWater.asFloor(), rand.random(10, 24));

        //fluids from both planets: oil, slag, cryofluid and arkycite
        for(int i = 0; i < 4; i++) placeLake(tiles, rand, Blocks.tar.asFloor(), Blocks.tar.asFloor(), rand.random(8, 20));
        for(int i = 0; i < 3; i++) placeLake(tiles, rand, Blocks.slag.asFloor(), Blocks.slag.asFloor(), rand.random(7, 16));
        for(int i = 0; i < 4; i++) placeLake(tiles, rand, Blocks.cryofluid.asFloor(), Blocks.cryofluid.asFloor(), rand.random(7, 18));
        for(int i = 0; i < 4; i++) placeLake(tiles, rand, Blocks.arkyciteFloor.asFloor(), Blocks.arkyciteFloor.asFloor(), rand.random(9, 22));
    }

    private static void placeLake(Tiles tiles, Rand rand, Floor center, Floor edge, int radius){
        for(int attempt = 0; attempt < 30; attempt++){
            int cx = rand.random(0, tiles.width - 1), cy = rand.random(0, tiles.height - 1);
            if(isProtected(cx, cy, radius + 8f)) continue;

            boolean painted = false;
            int cap = radius + 6;
            for(int x = cx - cap; x <= cx + cap; x++){
                for(int y = cy - cap; y <= cy + cap; y++){
                    if(!tiles.in(x, y)) continue;

                    float dx = x - cx, dy = y - cy;
                    float dist = Mathf.sqrt(dx * dx + dy * dy);
                    float wobble = 1f + 0.24f * (float)Simplex.noise2d(showcaseSeed + 7, 2, 0.5, 1f / 9f, x, y);
                    if(dist > radius * wobble) continue;

                    Tile tile = tiles.getn(x, y);
                    if(tile.floor().isLiquid || isProtected(x, y, 0f)) continue;

                    tile.setFloor(dist < radius * wobble * 0.62f ? center : edge);
                    painted = true;
                }
            }
            if(painted) return;
        }
    }

    /** Adds the alien moss carpets that host redweed and purbush. */
    private static void paintAlienPatches(Tiles tiles, Rand rand){
        for(int i = 0; i < 8; i++){
            int cx = rand.random(0, tiles.width - 1), cy = rand.random(0, tiles.height - 1);
            int radius = rand.random(8, 18);
            if(isProtected(cx, cy, radius)) continue;
            paintFloorBlob(tiles, cx, cy, radius, Blocks.redmat.asFloor(), Blocks.redmat.asFloor(), showcaseSeed + 40 + i);
        }

        for(int i = 0; i < 6; i++){
            int cx = rand.random(0, tiles.width - 1), cy = rand.random(0, tiles.height - 1);
            int radius = rand.random(8, 18);
            if(isProtected(cx, cy, radius)) continue;
            paintFloorBlob(tiles, cx, cy, radius, Blocks.bluemat.asFloor(), Blocks.bluemat.asFloor(), showcaseSeed + 80 + i);
        }
    }

    private static void paintFloorBlob(Tiles tiles, int cx, int cy, int radius, Floor center, Floor edge, int seed){
        int cap = radius + 5;
        for(int x = cx - cap; x <= cx + cap; x++){
            for(int y = cy - cap; y <= cy + cap; y++){
                if(!tiles.in(x, y)) continue;

                float dx = x - cx, dy = y - cy;
                float dist = Mathf.sqrt(dx * dx + dy * dy);
                float wobble = 1f + 0.22f * (float)Simplex.noise2d(seed, 2, 0.5, 1f / 8f, x, y);
                if(dist > radius * wobble) continue;

                Tile tile = tiles.getn(x, y);
                if(tile.floor().isLiquid || isProtected(x, y, 0f)) continue;

                tile.setFloor(dist < radius * wobble * 0.6f ? center : edge);
            }
        }
    }

    /** Places an organic cluster of every ore block from both planets. */
    private static void placeOreFields(Tiles tiles, Rand rand){
        Block[] ores = {
            Blocks.oreCopper, Blocks.oreLead, Blocks.oreScrap, Blocks.oreCoal,
            Blocks.oreTitanium, Blocks.oreThorium, Blocks.oreCrystalThorium,
            Blocks.oreBeryllium, Blocks.oreTungsten
        };

        for(int i = 0; i < ores.length; i++){
            for(int cluster = 0; cluster < 6; cluster++){
                int cx = rand.random(0, tiles.width - 1), cy = rand.random(0, tiles.height - 1);
                int radius = rand.random(8, 22);
                if(isProtected(cx, cy, radius)) continue;
                paintOreBlob(tiles, rand, cx, cy, radius, ores[i], showcaseSeed + 200 + i * 31 + cluster);
            }
        }
    }

    private static void paintOreBlob(Tiles tiles, Rand rand, int cx, int cy, int radius, Block ore, int seed){
        int cap = radius + 5;
        for(int x = cx - cap; x <= cx + cap; x++){
            for(int y = cy - cap; y <= cy + cap; y++){
                if(!tiles.in(x, y)) continue;

                float dx = x - cx, dy = y - cy;
                float dist = Mathf.sqrt(dx * dx + dy * dy);
                float wobble = 1f + 0.26f * (float)Simplex.noise2d(seed, 2, 0.5, 1f / 7f, x, y);
                if(dist > radius * wobble) continue;

                Tile tile = tiles.getn(x, y);
                if(tile.block() != Blocks.air || tile.floor().isLiquid || !tile.floor().hasSurface() || isProtected(x, y, 0f)) continue;

                //ores are denser at the core of the deposit and fade towards its edge
                float density = 0.94f - 0.5f * (dist / (radius * wobble));
                if(rand.chance(density)) tile.setOverlay(ore);
            }
        }
    }

    /** Grows rocky ridges using each floor's matching wall, so every natural wall appears. */
    private static void placeWallOutcrops(Tiles tiles, Rand rand){
        for(Tile tile : tiles){
            if(tile.block() != Blocks.air || tile.floor().isLiquid || isProtected(tile.x, tile.y, 0f)) continue;

            Block wall = wallFor(tile.floor());
            if(wall == null) continue;

            double cluster = Simplex.noise2d(showcaseSeed + 5, 3, 0.5, 1f / 30f, tile.x, tile.y);
            if(cluster > 0.54 && rand.chance(0.9f)){
                if(wall == Blocks.carbonWall && rand.chance(0.2f)) wall = Blocks.graphiticWall;
                tile.setBlock(wall);
            }
        }
    }

    private static @Nullable Block wallFor(Floor floor){
        if(floor == Blocks.stone || floor == Blocks.craters || floor == Blocks.charr) return Blocks.stoneWall;
        if(floor == Blocks.dirt || floor == Blocks.grass) return Blocks.dirtWall;
        if(floor == Blocks.moss || floor == Blocks.sporeMoss) return Blocks.sporeWall;
        if(floor == Blocks.snow || floor == Blocks.ice || floor == Blocks.iceSnow) return Blocks.iceWall;
        if(floor == Blocks.sand || floor == Blocks.darksand) return Blocks.duneWall;
        if(floor == Blocks.dacite) return Blocks.daciteWall;
        if(floor == Blocks.regolith) return Blocks.regolithWall;
        if(floor == Blocks.yellowStone || floor == Blocks.yellowStonePlates) return Blocks.yellowStoneWall;
        if(floor == Blocks.rhyolite || floor == Blocks.roughRhyolite || floor == Blocks.rhyoliteCrater) return Blocks.rhyoliteWall;
        if(floor == Blocks.carbonStone) return Blocks.carbonWall;
        if(floor == Blocks.ferricStone || floor == Blocks.ferricCraters) return Blocks.ferricStoneWall;
        if(floor == Blocks.beryllicStone) return Blocks.beryllicStoneWall;
        if(floor == Blocks.arkyicStone) return Blocks.arkyicWall;
        if(floor == Blocks.crystallineStone || floor == Blocks.crystalFloor) return Blocks.crystallineStoneWall;
        if(floor == Blocks.redIce) return Blocks.redIceWall;
        if(floor == Blocks.redStone || floor == Blocks.denseRedStone) return Blocks.redStoneWall;
        if(floor == Blocks.salt) return Blocks.saltWall;
        if(floor == Blocks.shale) return Blocks.shaleWall;
        if(floor == Blocks.basalt || floor == Blocks.hotrock || floor == Blocks.magmarock) return Blocks.duneWall;
        return null;
    }

    /** Seeds wall ores on exposed ridges: thorium, beryllium, graphite and tungsten. */
    private static void scatterWallOres(Tiles tiles, Rand rand){
        for(Tile tile : tiles){
            Block wall = tile.block();
            Block ore;

            if(wall == Blocks.stoneWall || wall == Blocks.daciteWall || wall == Blocks.rhyoliteWall || wall == Blocks.iceWall){
                ore = Blocks.wallOreThorium;
            }else if(wall == Blocks.beryllicStoneWall){
                ore = Blocks.wallOreBeryllium;
            }else if(wall == Blocks.carbonWall || wall == Blocks.graphiticWall){
                ore = Blocks.wallOreGraphite;
            }else if(wall == Blocks.redStoneWall || wall == Blocks.crystallineStoneWall){
                ore = Blocks.wallOreTungsten;
            }else{
                continue;
            }

            if(exposed(tiles, tile) && rand.chance(0.16f)){
                tile.setOverlay(ore);
            }
        }
    }

    private static boolean exposed(Tiles tiles, Tile tile){
        for(int i = 0; i < 4; i++){
            Tile other = tiles.get(tile.x + Geometry.d4[i].x, tile.y + Geometry.d4[i].y);
            if(other != null && other.block() == Blocks.air){
                return true;
            }
        }
        return false;
    }

    /** Scatters every tree, boulder, crystal, seaweed and vent over its matching floor. */
    private static void decorate(Tiles tiles, Rand rand){
        for(Tile tile : tiles){
            if(tile.block() != Blocks.air || tile.overlay() != Blocks.air || isProtected(tile.x, tile.y, 0f)) continue;

            Floor floor = tile.floor();

            //sea plants grow on the shallow liquids
            if(floor.isLiquid){
                if((floor == Blocks.sandWater || floor == Blocks.darksandWater) && rand.chance(0.03f)){
                    tile.setBlock(Blocks.yellowCoral);
                }
                continue;
            }

            if(!floor.hasSurface()) continue;

            double forest = Simplex.noise2d(showcaseSeed + 8, 3, 0.5, 1f / 45f, tile.x, tile.y);
            Block prop = null;

            if(floor == Blocks.grass || floor == Blocks.dirt){
                if(forest > 0.12f && rand.chance(0.055f)){
                    prop = rand.chance(0.7f) ? Blocks.pine : (rand.chance(0.55f) ? Blocks.whiteTree : Blocks.whiteTreeDead);
                }else if(rand.chance(0.015f)){
                    prop = Blocks.shrubs;
                }
            }else if(floor == Blocks.moss || floor == Blocks.sporeMoss){
                if(forest > 0.05f && rand.chance(0.065f)){
                    prop = Blocks.sporePine;
                }else if(rand.chance(0.02f)){
                    prop = Blocks.sporeCluster;
                }
            }else if(floor == Blocks.snow || floor == Blocks.ice || floor == Blocks.iceSnow){
                if(forest > 0.08f && rand.chance(0.05f)){
                    prop = rand.chance(0.6f) ? Blocks.snowPine : (rand.chance(0.5f) ? Blocks.whiteTree : Blocks.whiteTreeDead);
                }
            }else if(floor == Blocks.redStone || floor == Blocks.denseRedStone){
                if(forest > 0.2f && rand.chance(0.035f)) prop = Blocks.redDiamondWall;
            }else if(floor == Blocks.crystallineStone || floor == Blocks.crystalFloor){
                if(rand.chance(0.035f)){
                    int pick = rand.nextInt(4);
                    prop = pick == 0 ? Blocks.crystalCluster : pick == 1 ? Blocks.vibrantCrystalCluster : pick == 2 ? Blocks.crystalBlocks : Blocks.crystalOrbs;
                }
            }else if(floor == Blocks.redmat){
                if(rand.chance(0.05f)) prop = Blocks.redweed;
            }else if(floor == Blocks.bluemat){
                if(rand.chance(0.05f)) prop = Blocks.purbush;
            }else{
                Block boulder = boulderFor(floor);
                if(boulder != null && rand.chance(0.012f)) prop = boulder;
            }

            if(prop != null) tile.setBlock(prop);
        }
    }

    private static @Nullable Block boulderFor(Floor floor){
        if(floor == Blocks.stone || floor == Blocks.craters || floor == Blocks.charr || floor == Blocks.regolith) return Blocks.boulder;
        if(floor == Blocks.dacite) return Blocks.daciteBoulder;
        if(floor == Blocks.rhyolite || floor == Blocks.roughRhyolite || floor == Blocks.rhyoliteCrater) return Blocks.rhyoliteBoulder;
        if(floor == Blocks.sand || floor == Blocks.darksand) return Blocks.sandBoulder;
        if(floor == Blocks.snow || floor == Blocks.ice || floor == Blocks.iceSnow || floor == Blocks.salt) return Blocks.snowBoulder;
        if(floor == Blocks.basalt || floor == Blocks.hotrock || floor == Blocks.magmarock) return Blocks.basaltBoulder;
        if(floor == Blocks.shale) return Blocks.shaleBoulder;
        if(floor == Blocks.yellowStone || floor == Blocks.yellowStonePlates) return Blocks.yellowStoneBoulder;
        if(floor == Blocks.carbonStone) return Blocks.carbonBoulder;
        if(floor == Blocks.ferricStone || floor == Blocks.ferricCraters) return Blocks.ferricBoulder;
        if(floor == Blocks.beryllicStone) return Blocks.beryllicBoulder;
        if(floor == Blocks.arkyicStone) return Blocks.arkyicBoulder;
        if(floor == Blocks.crystallineStone || floor == Blocks.crystalFloor) return Blocks.crystallineBoulder;
        if(floor == Blocks.redIce) return Blocks.redIceBoulder;
        if(floor == Blocks.redStone || floor == Blocks.denseRedStone) return Blocks.redStoneBoulder;
        if(floor == Blocks.dirt || floor == Blocks.grass) return Blocks.boulder;
        return null;
    }

    /** Places every steam vent on its matching floor. */
    private static void placeVents(Tiles tiles, Rand rand){
        Block[][] vents = {
            {Blocks.stone, Blocks.stoneVent},
            {Blocks.rhyolite, Blocks.rhyoliteVent},
            {Blocks.carbonStone, Blocks.carbonVent},
            {Blocks.arkyicStone, Blocks.arkyicVent},
            {Blocks.yellowStone, Blocks.yellowStoneVent},
            {Blocks.denseRedStone, Blocks.redStoneVent},
            {Blocks.crystallineStone, Blocks.crystallineVent},
            {Blocks.basalt, Blocks.basaltVent}
        };

        for(Block[] vent : vents){
            for(int placed = 0, attempt = 0; attempt < 600 && placed < 8; attempt++){
                int x = rand.random(0, tiles.width - 1), y = rand.random(0, tiles.height - 1);
                Tile tile = tiles.getn(x, y);
                if(tile.block() == Blocks.air && tile.floor() == vent[0] && !isProtected(x, y, 0f) && rand.chance(0.35f)){
                    tile.setBlock(vent[1]);
                    placed++;
                }
            }
        }
    }

    private static void clearProtectedAreas(Tiles tiles){
        int center = tiles.width / 2;
        int edge = 3;

        //room for the player's core and the initial hull
        clearArea(tiles, center, center, 12);
        //one safe landing spot under each air-wave spawn marker
        clearArea(tiles, center, edge, 6);
        clearArea(tiles, center, tiles.height - edge - 1, 6);
        clearArea(tiles, edge, center, 6);
        clearArea(tiles, tiles.width - edge - 1, center, 6);
    }

    private static void clearArea(Tiles tiles, int cx, int cy, int radius){
        int cap = radius + 2;
        for(int x = cx - cap; x <= cx + cap; x++){
            for(int y = cy - cap; y <= cy + cap; y++){
                if(!tiles.in(x, y) || !Mathf.within(x, y, cx, cy, radius + 0.5f)) continue;

                Tile tile = tiles.getn(x, y);
                tile.setBlock(Blocks.air);
                tile.setOverlay(Blocks.air);
                if(tile.floor().isLiquid) tile.setFloor(Blocks.grass.asFloor());
            }
        }
    }

    /** @return whether this position belongs to the core or one of the four air-wave spawn pads. */
    private static boolean isProtected(float x, float y, float padding){
        float centerX = world.width() / 2f, centerY = world.height() / 2f;
        float edge = 3f;
        return Mathf.within(x, y, centerX, centerY, 16f + padding)
            || Mathf.within(x, y, centerX, edge, 7f + padding)
            || Mathf.within(x, y, centerX, world.height() - edge - 1, 7f + padding)
            || Mathf.within(x, y, edge, centerY, 7f + padding)
            || Mathf.within(x, y, world.width() - edge - 1, centerY, 7f + padding);
    }

    //endregion

    private static void spawnInitialMono(){
        if(!initializeMono || !active()) return;
        initializeMono = false;

        player.team(Team.sharded);
        Unit mono = UnitTypes.mono.spawn(Team.sharded, world.unitWidth() / 2f, world.unitHeight() / 2f);
        player.unit(mono);
        player.set(mono);
        Core.camera.position.set(mono);
        selectedType = UnitTypes.mono;

        //the setup menu is the way into the environment: choose the hull, install modules,
        //then let the air waves start. Nothing is persisted, so it must be shown every start.
        Core.app.post(() -> {
            if(!active() || !setupOpen) return;
            openSetup();
        });
    }

    /** Reopens the setup menu, unless the defense has already started. */
    public static void openSetup(){
        if(!active() || wavesStarted) return;
        if(setupDialog == null) setupDialog = new ModuleTestSetupDialog();
        setupDialog.openSetup();
    }

    /** @return the resources of both planets, one stack of each visible item. */
    public static Seq<ItemStack> resourceLoadout(){
        Seq<Item> items = new Seq<>();
        for(Item item : Items.serpuloItems){
            if(!item.isHidden() && !items.contains(item)) items.add(item);
        }
        for(Item item : Items.erekirItems){
            if(!item.isHidden() && !items.contains(item)) items.add(item);
        }

        Seq<ItemStack> stacks = new Seq<>();
        for(Item item : items){
            //play() caps every stack at the core's storage capacity, so a large amount simply
            //means "fill this resource to the brim"
            stacks.add(new ItemStack(item, 9999));
        }
        return stacks;
    }

    /** @return the player unit, or null when it is missing or dead. */
    public static @Nullable Unit playerUnit(){
        return player == null || player.dead() || player.unit() == null ? null : player.unit();
    }

    /**
     * @return how many parts are fitted on the player's ship, counting mounted turrets, armour
     * plates and support parts. Used by the tutorial hints to notice the player's first part.
     */
    public static int mountedCount(){
        Unit unit = playerUnit();
        if(unit == null) return 0;

        int count = unit.mountedTurrets() + (unit.wallMounts == null ? 0 : unit.wallMounts.length);
        if(unit.supportMounts != null){
            for(SupportMount mount : unit.supportMounts){
                if(mount != null && mount.block != null) count++;
            }
        }
        return count;
    }

    /**
     * Replaces the current player hull with the chosen air unit, keeping its position.
     * Modules installed on the previous hull are intentionally not transferred: the setup
     * menu asks for the modules only after this choice.
     */
    public static boolean selectUnit(UnitType type){
        if(!active() || type == null || !type.flying || type.isHidden()) return false;

        player.team(Team.sharded);
        Unit previous = playerUnit();
        float x = previous == null ? world.unitWidth() / 2f : previous.x;
        float y = previous == null ? world.unitHeight() / 2f : previous.y;

        if(previous != null && previous.type == type){
            selectedType = type;
            return true;
        }

        Unit unit = type.spawn(Team.sharded, x, y);
        player.unit(unit);
        player.set(unit);
        if(previous != null && previous != unit) previous.remove();
        Core.camera.position.set(unit);

        selectedType = type;
        return true;
    }

    /**
     * Starts the defense phase: fills the rules with air-only spawn groups and hands the wave
     * control to the player. Nothing arrives on its own - the play button in the HUD is the only
     * thing that calls a wave.
     */
    public static boolean beginWaves(){
        if(!active() || wavesStarted || playerUnit() == null) return false;

        configureWaveRules(state.rules);

        state.wave = 1;
        //keep the clock above zero; Logic only fires an automatic wave once it reaches zero
        state.wavetime = waveSpacing;

        selectedType = playerUnit().type;
        setupOpen = false;
        wavesStarted = true;
        return true;
    }

    /** Applies the air-only rules used by the defense, leaving the wave timer off. */
    public static void configureWaveRules(Rules rules){
        rules.spawns = airWaveGroups();
        rules.waves = true;
        //manual waves: the timer must stay off, or groups would arrive without the player asking
        rules.waveTimer = false;
        rules.waveSending = true;
        rules.waitEnemies = true;
        rules.waveSpacing = waveSpacing;
        rules.airUseSpawns = true;
        rules.hideSpawns = false;
    }

    /**
     * @return the air wave progression: the engine's air-only generator plus one late group
     * for each visible Erekir air unit, so the defense covers both planets' air fleets.
     */
    public static Seq<SpawnGroup> airWaveGroups(){
        Seq<SpawnGroup> groups = Waves.generate(waveDifficulty, new Rand(), false, true);

        Seq<UnitType> erekirAir = content.units().select(type ->
            type.flying && !type.isHidden() && type instanceof ErekirUnitType);
        erekirAir.sort((a, b) -> Float.compare(a.health, b.health));

        int index = 0;
        for(UnitType type : erekirAir){
            int tier = index++;
            groups.add(new SpawnGroup(type){{
                begin = 4 + tier * 2;
                spacing = 3;
                unitAmount = 1;
                unitScaling = 18f;
                max = 3;
                shields = tier * 40f;
                shieldScaling = 20f;
            }});
        }

        return groups;
    }

    /** Spawns an aerial unit beside the player's unit, without taking control of it. */
    public static boolean spawn(UnitType type){
        if(!active() || type == null || !type.flying || type.isHidden()) return false;
        if(!Units.canCreate(player.team(), type)) return false;

        Unit source = player.unit();
        if(source == null || source.dead) return false;

        float distance = source.hitSize / 2f + type.hitSize / 2f + tilesize * 2f;
        float angle = Mathf.random(360f);
        float x = Mathf.clamp(source.x + Angles.trnsx(angle, distance), type.hitSize, world.unitWidth() - type.hitSize);
        float y = Mathf.clamp(source.y + Angles.trnsy(angle, distance), type.hitSize, world.unitHeight() - type.hitSize);

        type.spawn(player.team(), x, y, source.rotation);
        return true;
    }

    /** @return every aerial unit in the content list, regardless of unlock state. */
    public static Seq<UnitType> aerialTypes(){
        Seq<UnitType> result = content.units().select(type -> type.flying && !type.isHidden());
        result.sort((a, b) -> a.localizedName.compareToIgnoreCase(b.localizedName));
        return result;
    }

    /**
     * @return every team that can hold enemy structures: all editor base teams except the player's
     * own and the derelicts. On a planet map these normally include the campaign enemies.
     */
    public static Seq<Team> enemyTeams(){
        Seq<Team> result = new Seq<>();
        for(Team team : Team.baseTeams){
            if(team == Team.derelict || (player != null && !player.dead() && team == player.team())) continue;
            result.add(team);
        }
        return result;
    }

    /**
     * @return every structure that can actually be built, sorted by category and name, for the
     * shared units/structures catalogue.
     *
     * <p>{@link Block#inEditor} is deliberately not used as a filter: it controls the map editor
     * palette, not construction itself, so using it here hid valid custom structures. The real
     * build checks are used instead. Floors and cliffs are left out because this tool places
     * structures, while hidden/debug-only and module-only content cannot be built as base
     * blocks.</p>
     */
    public static Seq<Block> enemyBlocks(){
        Seq<Block> result = content.blocks().select(block ->
            block.canBeBuilt()
            && block.placeablePlayer
            && !block.moduleOnly
            && !block.isAir() && !block.isFloor() && block != Blocks.cliff
        );
        result.sort((a, b) -> {
            int category = Integer.compare(a.category.ordinal(), b.category.ordinal());
            return category != 0 ? category : a.localizedName.compareToIgnoreCase(b.localizedName);
        });
        return result;
    }

    /** @return whether the enemy-building tool is armed, either placing or removing. */
    public static boolean enemyToolArmed(){
        return active() && (enemyBlock != null || enemyRemoving);
    }

    /** @return whether the player-building tool is armed. */
    public static boolean playerToolArmed(){
        return active() && playerBlock != null;
    }

    /** Arms the enemy tool with a structure, clearing the player tool. */
    public static void armEnemyBlock(Block block){
        enemyBlock = block;
        enemyRemoving = false;
        playerBlock = null;
    }

    /** Arms the player tool with a structure, clearing the enemy tool. */
    public static void armPlayerBlock(Block block){
        playerBlock = block;
        enemyBlock = null;
        enemyRemoving = false;
    }

    /** Disarms every structure tool of the test panel. */
    public static void disarmStructureTools(){
        playerBlock = null;
        enemyBlock = null;
        enemyRemoving = false;
    }

    /**
     * Queues one structure for the player's team through the normal build pipeline, so it
     * rises at the regular construction speed instead of appearing instantly.
     *
     * @return whether the plan was queued.
     */
    public static boolean queuePlayerBuilding(Block block, int x, int y, int rotation, @Nullable Object config){
        if(!active() || block == null) return false;

        Unit unit = playerUnit();
        if(unit == null) return false;

        //multiblocks are anchored at their center tile, so pull the cursor back into the map
        if(block.isMultiblock()){
            x = Mathf.clamp(x, (block.size - 1) / 2, world.width() - block.size / 2 - 1);
            y = Mathf.clamp(y, (block.size - 1) / 2, world.height() - block.size / 2 - 1);
        }

        //same check the builder runs when the plan reaches the front of the queue, so a click
        //on an invalid spot does not leave a plan stuck on the hull forever
        if(!Build.validPlaceIgnoreUnits(block, player.team(), x, y, rotation, true, true)) return false;

        unit.addBuild(new BuildPlan(x, y, rotation, block, config), true);
        return true;
    }

    /**
     * Places one enemy structure at this tile.
     *
     * <p>Unlike the normal build path, the campaign restrictions (enemy core build radius,
     * placement overlap, darkness) are skipped: the test map only has the player's own core, and
     * the point of the tool is to put an enemy base <b>anywhere</b> it is needed to aim at, not
     * inside a protected radius around a core that does not exist.</p>
     *
     * @return whether the structure was placed.
     */
    public static boolean placeEnemyBuilding(Block block, Team team, int x, int y, int rotation, @Nullable Object config){
        if(block == null || team == null || !active()) return false;

        //multiblocks are anchored at their center tile, so pull the cursor back into the map
        if(block.isMultiblock()){
            x = Mathf.clamp(x, (block.size - 1) / 2, world.width() - block.size / 2 - 1);
            y = Mathf.clamp(y, (block.size - 1) / 2, world.height() - block.size / 2 - 1);
        }

        Tile tile = world.tile(x, y);
        if(tile == null) return false;

        tile.setBlock(block, team, rotation);

        if(tile.build != null){
            tile.build.health = block.health;
            //power links and other proximity-dependent state are resolved from here; in a local
            //session this is the same call a regular instant build ends up performing
            tile.build.placed();
            if(config != null) tile.build.configured(null, config);
        }

        block.placeEnded(tile, null, rotation, config);
        Events.fire(new BlockBuildEndEvent(tile, null, team, false, config));

        //the normal path plays the placement effect when the tile is visible to the team; do the
        //same here so placing a structure feels like the regular instant build
        if(!headless && state.isGame() && fogControl.isVisibleTile(team, tile.x, tile.y)){
            block.placeEffect.at(tile.drawx(), tile.drawy(), block.size);
        }
        return true;
    }

    /** Removes the structure at this tile, however big or whose team it is. @return whether one was removed. */
    public static boolean removeEnemyBuilding(int x, int y){
        if(!active()) return false;
        Tile tile = world.tile(x, y);
        if(tile == null || tile.build == null || tile.block() == Blocks.air) return false;
        tile.remove();
        return true;
    }

    /** Removes every structure of one team from the test map. @return how many were removed. */
    public static int clearEnemyBuildings(Team team){
        if(!active() || team == null) return 0;

        Seq<Tile> targets = new Seq<>();
        //collect first: removing a multiblock silently clears its neighbors while iterating
        for(Tile tile : world.tiles){
            if(tile.build != null && tile.isCenter() && tile.build.team == team && tile.block() != Blocks.air){
                targets.add(tile);
            }
        }
        for(Tile tile : targets){
            tile.remove();
        }
        return targets.size;
    }
}
