package orbital.engine.world;

import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import orbital.type.Item;
import orbital.type.Liquid;
import orbital.world.Tile;
import orbital.world.Tiles;

import static orbital.Vars.world;

/**
 * What the builder unit knows about the natural resources of the current sector.
 *
 * <p>A single map scan builds an index of every ore tile by {@link Item} and every liquid tile by
 * {@link Liquid}. The planet drill uses this to report which resources a sector actually has.</p>
 *
 * <p>The snapshot is cached for the current {@link Tiles} object. Loading a new map replaces that
 * object, so the next {@link #get()} rescans automatically.</p>
 */
public final class SectorResources{
    private static Tiles cachedTiles;
    private static SectorResources cached;

    private final ObjectMap<Item, Seq<Tile>> itemOres = new ObjectMap<>();
    private final ObjectMap<Liquid, Seq<Tile>> liquidSources = new ObjectMap<>();
    private final ObjectSet<Item> presentItems = new ObjectSet<>();
    private final ObjectSet<Liquid> presentLiquids = new ObjectSet<>();

    private SectorResources(){
        for(int y = 0; y < world.height(); y++){
            for(int x = 0; x < world.width(); x++){
                Tile tile = world.tile(x, y);
                if(tile == null) continue;

                Item drop = tile.drop();
                if(drop != null){
                    presentItems.add(drop);
                    Seq<Tile> list = itemOres.get(drop);
                    if(list == null){
                        list = new Seq<>();
                        itemOres.put(drop, list);
                    }
                    list.add(tile);
                }

                Liquid liquid = tile.floor().liquidDrop;
                if(liquid != null){
                    presentLiquids.add(liquid);
                    Seq<Tile> list = liquidSources.get(liquid);
                    if(list == null){
                        list = new Seq<>();
                        liquidSources.put(liquid, list);
                    }
                    list.add(tile);
                }
            }
        }
    }

    /** @return the cached snapshot, rescanned when the map changes. */
    public static SectorResources get(){
        if(cached == null || cachedTiles != world.tiles){
            cached = new SectorResources();
            cachedTiles = world.tiles;
        }
        return cached;
    }

    /** Forces a rescan on the next {@link #get()}, e.g. after test terrain is edited in place. */
    public static void invalidate(){
        cached = null;
        cachedTiles = null;
    }

    public boolean has(Item item){
        return item != null && presentItems.contains(item);
    }

    public boolean has(Liquid liquid){
        return liquid != null && presentLiquids.contains(liquid);
    }

    /** @return ore tiles of this item in the sector, or an empty sequence. */
    public Seq<Tile> ores(Item item){
        Seq<Tile> tiles = item == null ? null : itemOres.get(item);
        return tiles == null ? new Seq<>() : tiles;
    }

    /** @return liquid tiles of this kind in the sector, or an empty sequence. */
    public Seq<Tile> liquids(Liquid liquid){
        Seq<Tile> tiles = liquid == null ? null : liquidSources.get(liquid);
        return tiles == null ? new Seq<>() : tiles;
    }

    /** Human-readable list of ore types that actually exist on this map, for decision logs. */
    public String summary(){
        if(presentItems.isEmpty()) return "no ores";
        StringBuilder out = new StringBuilder();
        for(Item item : presentItems){
            if(out.length() > 0) out.append(", ");
            out.append(item.name);
        }
        return out.toString();
    }
}
