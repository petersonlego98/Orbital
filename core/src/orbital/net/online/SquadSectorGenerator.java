package orbital.net.online;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.noise.*;
import orbital.content.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.world.*;
import orbital.world.blocks.environment.*;

import static orbital.Vars.*;

/**
 * Procedural 4-player sector parameterized by a seed.
 *
 * <p>Derived from {@code TestNewFeaturesMode.generateFourPlayerSector}, but every noise field and
 * deposit uses the squad seed instead of a global constant. Two squads with different seeds get
 * visibly different terrain, deposits and lake shapes, while each still has one clean quadrant per
 * player on a fixed team. Generated in memory only — no campaign save keys are touched.</p>
 */
public final class SquadSectorGenerator{
    public static final int size = 256;

    /** Team order is fixed: squad slot 0..3 maps to these teams. */
    public static Team[] teams(){
        return new Team[]{Team.sharded, Team.crux, Team.malis, Team.green};
    }

    /** @return core cell of each slot (2x2 quadrants of 128). */
    public static Point2[] coreCells(){
        int quarter = size / 2;
        Point2[] result = new Point2[4];
        for(int i = 0; i < 4; i++){
            int cx = (i % 2 == 0) ? quarter / 2 : quarter + quarter / 2;
            int cy = (i < 2) ? quarter / 2 : quarter + quarter / 2;
            result[i] = new Point2(cx, cy);
        }
        return result;
    }

    /** Generates the whole sector into {@code tiles} for the given squad seed. */
    public static void generate(Tiles tiles, long seed){
        Rand rand = new Rand((int)seed);

        paintBiomes(tiles, seed);
        paintLakes(tiles, seed);

        Point2[] cells = coreCells();
        Team[] teams = teams();
        Block[] ores = {Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium, Blocks.oreThorium};

        for(int i = 0; i < 4; i++){
            int cx = cells[i].x, cy = cells[i].y;

            //clean buildable clearing around the core
            for(int x = cx - 30; x <= cx + 30; x++){
                for(int y = cy - 30; y <= cy + 30; y++){
                    if(!tiles.in(x, y) || !Mathf.within(x, y, cx, cy, 28f)) continue;
                    Tile tile = tiles.getn(x, y);
                    tile.setBlock(Blocks.air);
                    tile.setOverlay(Blocks.air);
                    if(tile.floor().isLiquid) tile.setFloor(Blocks.sand.asFloor());
                }
            }

            //starter deposits, offset by the seed so layouts differ between squads
            deposit(tiles, rand, seed, ores[0], cx - 20, cy + 10, 11);
            deposit(tiles, rand, seed, ores[1], cx + 21, cy + 9, 11);
            deposit(tiles, rand, seed, ores[2], cx + 19, cy - 19, 10);
            deposit(tiles, rand, seed, ores[3], cx - 19, cy - 18, 9);
            deposit(tiles, rand, seed, ores[4], cx, cy - 27, 9);

            //small water pocket near each core
            blob(tiles, seed + 300 + i, cx + 24, cy - 26, 7, Blocks.water.asFloor());

            tiles.getn(cx, cy).setBlock(Blocks.coreShard, teams[i]);
        }

        //wave entry markers on edges
        int center = size / 2, edge = 3;
        tiles.getn(center, edge).setOverlay(Blocks.spawn);
        tiles.getn(center, size - edge - 1).setOverlay(Blocks.spawn);
        tiles.getn(edge, center).setOverlay(Blocks.spawn);
        tiles.getn(size - edge - 1, center).setOverlay(Blocks.spawn);

        orbital.engine.world.SectorResources.invalidate();
        Log.info("[squad-sector] generated @x@ sector seed=@ (4 cores)", size, size, seed);
    }

    private static void paintBiomes(Tiles tiles, long seed){
        int s = (int)seed;
        for(Tile tile : tiles){
            double heat = Simplex.noise2d(s, 4, 0.55, 1.0 / 180.0, tile.x, tile.y);
            double moisture = Simplex.noise2d(s + 1, 4, 0.55, 1.0 / 210.0, tile.x, tile.y);
            double detail = Simplex.noise2d(s + 2, 4, 0.5, 1.0 / 34.0, tile.x, tile.y);
            double rocks = Simplex.noise2d(s + 3, 3, 0.5, 1.0 / 70.0, tile.x, tile.y);

            Floor floor;
            if(rocks > 0.38) floor = (Floor)Blocks.stone;
            else if(heat > 0.32) floor = (Floor)Blocks.sand;
            else if(heat < -0.32) floor = (Floor)Blocks.ice;
            else if(moisture > 0.28) floor = (Floor)Blocks.grass;
            else if(moisture < -0.25) floor = (Floor)Blocks.darksand;
            else floor = detail > 0 ? (Floor)Blocks.grass : (Floor)Blocks.dirt;

            tile.setFloor(floor);
            tile.setOverlay(Blocks.air);
            tile.setBlock(Blocks.air);
        }
    }

    private static void paintLakes(Tiles tiles, long seed){
        for(int i = 0; i < 6; i++){
            int cx = (int)(Mathf.randomSeed(seed + i * 7, size));
            int cy = (int)(Mathf.randomSeed(seed + i * 7 + 1, size));
            int radius = 10 + (int)Mathf.randomSeed(seed + i * 7 + 2, 18);
            blob(tiles, seed + 100 + i, cx, cy, radius, Blocks.water.asFloor());
        }
    }

    private static void blob(Tiles tiles, long seed, int cx, int cy, int radius, Floor floor){
        for(int x = cx - radius - 4; x <= cx + radius + 4; x++){
            for(int y = cy - radius - 4; y <= cy + radius + 4; y++){
                if(!tiles.in(x, y)) continue;
                float d = Mathf.dst(x, y, cx, cy);
                float wobble = 1f + 0.22f * (float)Simplex.noise2d((int)seed, 2, 0.5, 1f / 9f, x, y);
                if(d <= radius * wobble) tiles.getn(x, y).setFloor(floor);
            }
        }
    }

    private static void deposit(Tiles tiles, Rand rand, long seed, Block ore, int cx, int cy, int radius){
        int cap = radius + 5;
        for(int x = cx - cap; x <= cx + cap; x++){
            for(int y = cy - cap; y <= cy + cap; y++){
                if(!tiles.in(x, y)) continue;
                float d = Mathf.dst(x, y, cx, cy);
                float wobble = 1f + 0.26f * (float)Simplex.noise2d((int)(seed + ore.id * 17 + cx + cy), 2, 0.5, 1f / 7f, x, y);
                if(d > radius * wobble) continue;
                Tile tile = tiles.getn(x, y);
                if(tile.block() != Blocks.air || tile.floor().isLiquid) continue;
                if(rand.chance(0.94f - 0.5f * (d / (radius * wobble)))) tile.setOverlay(ore);
            }
        }
    }
}
