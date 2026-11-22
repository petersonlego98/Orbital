package orbital.maps.generators;

import arc.math.*;
import arc.util.*;
import arc.util.noise.*;
import orbital.content.*;
import orbital.game.*;
import orbital.type.*;
import orbital.world.*;

import static orbital.Vars.*;

/**
 * Procedural sector generator for Quatia: a green rainforest world crossed by
 * rivers, with lava caverns in the highlands and cryofluid streams on the snowy
 * peaks. Every sector is generated from its own seed, so all 272 sectors are
 * distinct and playable. Fluid density is deliberately moderate to keep the
 * shader cost low on integrated GPUs.
 */
public class QuatiaSectorGenerator implements WorldGenerator{
    /** All Quatia sectors share the same size, independent of the old campaign maps. */
    public static final int size = 160;

    private final int seed;
    private final long squadSeed;
    private Tiles tiles;
    private int width, height;
    private Rand rand;

    public QuatiaSectorGenerator(int seed){
        this(seed, -1);
    }

    public QuatiaSectorGenerator(Sector sector){
        this(sector.id, sector.squadSeed);
    }

    private QuatiaSectorGenerator(int seed, long squadSeed){
        this.seed = seed;
        this.squadSeed = squadSeed;
    }

    @Override
    public void generate(Tiles tiles, WorldParams params){
        this.tiles = tiles;
        this.width = tiles.width;
        this.height = tiles.height;
        this.rand = new Rand(seed + (int)params.seedOffset);

        //Tiles starts as a null array; create one Tile per cell before painting floors.
        tiles.fill();

        rainforest();
        highlands();
        rivers();
        lavaCaverns();
        cryoStreams();
        shorelines();
        ores();

        if(squadSeed >= 0){
            squadLayout();
        }else{
            starterCore();
        }
    }

    /**
     * Four-quadrant layout for a squad sector: one core, clearing and set of deposits
     * per player. The biome stays Quatia's rainforest; only the ownership changes.
     */
    private void squadLayout(){
        int quarter = size / 2;
        Team[] teams = orbital.net.online.SquadSectorGenerator.teams();
        Block[] deposits = {Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium, Blocks.oreThorium};

        for(int i = 0; i < 4; i++){
            int cx = (i % 2 == 0) ? quarter / 2 : quarter + quarter / 2;
            int cy = (i < 2) ? quarter / 2 : quarter + quarter / 2;

            //Clean buildable clearing around the core.
            for(int x = cx - 20; x <= cx + 20; x++){
                for(int y = cy - 20; y <= cy + 20; y++){
                    if(!tiles.in(x, y) || !Mathf.within(x, y, cx, cy, 18f)) continue;
                    Tile tile = tiles.getn(x, y);
                    tile.setBlock(Blocks.air);
                    tile.setOverlay(Blocks.air);
                    if(tile.floor().isLiquid) tile.setFloor(Blocks.grass.asFloor());
                }
            }

            //Starter deposits, offset by the squad seed so layouts differ between groups.
            for(int d = 0; d < deposits.length; d++){
                int ox = cx + (d % 2 == 0 ? -12 : 12);
                int oy = cy + (d < 2 ? 8 : -9);
                deposit(cx, cy, ox, oy, deposits[d]);
            }

            tiles.getn(cx, cy).setBlock(Blocks.coreShard, teams[i]);
        }

        //Wave entry markers on the map edges.
        int edge = 3, center = size / 2;
        tiles.getn(center, edge).setOverlay(Blocks.spawn);
        tiles.getn(center, size - edge - 1).setOverlay(Blocks.spawn);
        tiles.getn(edge, center).setOverlay(Blocks.spawn);
        tiles.getn(size - edge - 1, center).setOverlay(Blocks.spawn);
    }

    private void deposit(int cx, int cy, int x, int y, Block ore){
        for(int dx = -6; dx <= 6; dx++){
            for(int dy = -6; dy <= 6; dy++){
                if(!tiles.in(x + dx, y + dy) || !Mathf.within(dx, dy, 0f, 0f, 6f)) continue;
                Tile tile = tiles.getn(x + dx, y + dy);
                if(tile.block() != Blocks.air || tile.floor().isLiquid) continue;
                if(rand.chance(0.9f)) tile.setOverlay(ore);
            }
        }
    }

    /** Green Amazon-like floor covering the whole sector. */
    private void rainforest(){
        for(Tile tile : tiles){
            float n = Simplex.noise2d(seed + 11, 3, 0.55, 1f / 42f, tile.x, tile.y);
            tile.setFloor(n > 0.22f ? Blocks.moss.asFloor() : (n > -0.28f ? Blocks.grass.asFloor() : Blocks.dirt.asFloor()));
        }
    }

    /** Rocky mountains; the tallest peaks become snow fields. */
    private void highlands(){
        for(Tile tile : tiles){
            float m = Simplex.noise2d(seed + 23, 3, 0.6, 1f / 30f, tile.x, tile.y);
            if(m > 0.46f){
                tile.setFloor((m > 0.68f ? Blocks.snow.asFloor() : Blocks.stone.asFloor()));
            }else if(m > 0.38f && rand.chance(0.35f)){
                tile.setFloor(Blocks.basalt.asFloor());
            }
        }
    }

    /** Thin water rivers following ridged noise; moderate coverage. */
    private void rivers(){
        for(Tile tile : tiles){
            if(tile.floor().isLiquid || tile.floor() == Blocks.snow || tile.floor() == Blocks.ice) continue;
            float r = 1f - Math.abs(Simplex.noise2d(seed + 37, 2, 0.6, 1f / 36f, tile.x, tile.y));
            if(r > 0.90f){
                tile.setFloor(r > 0.965f ? Blocks.deepwater.asFloor() : Blocks.water.asFloor());
            }
        }
    }

    /** Lava pockets inside the rocky highlands (slag is this fork's lava block). */
    private void lavaCaverns(){
        for(Tile tile : tiles){
            if(tile.floor() != Blocks.stone && tile.floor() != Blocks.basalt) continue;
            float c = 1f - Math.abs(Simplex.noise2d(seed + 53, 2, 0.7, 1f / 16f, tile.x, tile.y));
            if(c > 0.925f){
                tile.setFloor(rand.chance(0.75f) ? Blocks.slag.asFloor() : Blocks.hotrock.asFloor());
            }
        }
    }

    /** Cryofluid streams running down the snowy peaks. */
    private void cryoStreams(){
        for(Tile tile : tiles){
            if(tile.floor() != Blocks.snow && tile.floor() != Blocks.ice) continue;
            float c = 1f - Math.abs(Simplex.noise2d(seed + 71, 2, 0.7, 1f / 20f, tile.x, tile.y));
            if(c > 0.93f){
                tile.setFloor(Blocks.cryofluid.asFloor());
            }
        }
    }

    /** Soft sand banks where water meets land. */
    private void shorelines(){
        for(Tile tile : tiles){
            if(tile.floor().isLiquid) continue;
            boolean nearWater = false;
            for(int dx = -1; dx <= 1 && !nearWater; dx++){
                for(int dy = -1; dy <= 1; dy++){
                    if(tiles.in(tile.x + dx, tile.y + dy) && tiles.getn(tile.x + dx, tile.y + dy).floor().isLiquid){
                        nearWater = true;
                        break;
                    }
                }
            }
            if(nearWater) tile.setFloor(Blocks.sand.asFloor());
        }
    }

    /** Scattered deposits so every sector can bootstrap a base. */
    private void ores(){
        Block[] deposits = {Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium, Blocks.oreThorium};
        float[] scales = {30f, 34f, 38f, 44f, 52f};
        for(int i = 0; i < deposits.length; i++){
            for(Tile tile : tiles){
                if(tile.floor().isLiquid || tile.floor() == Blocks.snow) continue;
                float n = Simplex.noise2d(seed + 100 + i * 17, 2, 0.7, 1f / scales[i], tile.x, tile.y);
                if(n > 0.62f && rand.chance(0.75f)){
                    tile.setOverlay(deposits[i]);
                }
            }
        }
    }

    /** One clean clearing with a starting core, matching the old campaign rule. */
    private void starterCore(){
        int cx = width / 2, cy = height / 2;
        for(int x = cx - 14; x <= cx + 14; x++){
            for(int y = cy - 14; y <= cy + 14; y++){
                if(tiles.in(x, y) && Mathf.within(x, y, cx, cy, 14f)){
                    Tile tile = tiles.getn(x, y);
                    tile.setFloor(Blocks.grass.asFloor());
                    tile.setOverlay(Blocks.air);
                }
            }
        }

        Schematics.placeLoadout(Loadouts.basicShard, cx, cy);

        Tile core = world.tile(cx, cy);
        if(core == null || core.build == null){
            throw new IllegalStateException("Quatia sector did not place a starting core.");
        }
        core.build.items.add(Items.copper, 300);
        core.build.items.add(Items.lead, 200);
        core.build.items.add(Items.coal, 100);
    }
}
