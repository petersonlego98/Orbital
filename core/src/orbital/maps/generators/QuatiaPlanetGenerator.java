package orbital.maps.generators;

import arc.math.*;
import orbital.content.*;
import orbital.ctype.*;
import orbital.game.*;
import orbital.type.*;
import orbital.world.*;

import static orbital.Vars.*;

/**
 * Generates Orbital's first production colony: one peaceful map with nearby copper, lead, coal
 * and sand deposits. The existing Mindustry buildings provide the initial mining and crafting loop.
 */
public class QuatiaPlanetGenerator extends BlankPlanetGenerator{
    private static final int mapSize = 160;
    private static final int depositRadius = 9;

    @Override
    public int getSectorSize(Sector sector){
        // The first colony is a single gridless sector; do not use the planet-scale default size.
        return mapSize;
    }

    @Override
    protected void generate(){
        int centerX = width / 2;
        int centerY = height / 2;

        for(Tile tile : tiles){
            tile.setFloor(Blocks.grass.asFloor());
        }

        placeOre(Blocks.oreCopper, centerX - 28, centerY + 10);
        placeOre(Blocks.oreLead, centerX + 28, centerY + 10);
        placeOre(Blocks.oreCoal, centerX + 24, centerY - 26);
        placeSand(centerX - 25, centerY - 25);

        unlockStarterContent();
        Schematics.placeLoadout(Loadouts.basicShard, centerX, centerY);

        var core = world.tile(centerX, centerY).build;
        if(core == null){
            throw new IllegalStateException("Quatia's starter loadout did not place a core.");
        }

        //Enough material to establish the first drills and power chain; local deposits remain the
        //source for continued production.
        core.items.add(Items.copper, 350);
        core.items.add(Items.lead, 250);
        core.items.add(Items.coal, 100);
    }

    private void unlockStarterContent(){
        for(UnlockableContent content : new UnlockableContent[]{
            Blocks.conveyor,
            Blocks.router,
            Blocks.mechanicalDrill,
            Blocks.combustionGenerator,
            Blocks.powerNode,
            Blocks.siliconSmelter,
            Items.lead,
            Items.coal,
            Items.silicon
        }){
            content.quietUnlock();
        }
    }

    private void placeOre(Block ore, int centerX, int centerY){
        float radiusSquared = depositRadius * depositRadius;
        for(int x = centerX - depositRadius; x <= centerX + depositRadius; x++){
            for(int y = centerY - depositRadius; y <= centerY + depositRadius; y++){
                int dx = x - centerX, dy = y - centerY;
                float distanceSquared = dx * dx + dy * dy;
                if(distanceSquared <= radiusSquared){
                    float density = 0.82f + 0.17f * (1f - distanceSquared / radiusSquared);
                    if(rand.chance(density)){
                        tiles.getn(x, y).setOverlay(ore);
                    }
                }
            }
        }
    }

    private void placeSand(int centerX, int centerY){
        int radius = depositRadius + 2;
        int radiusSquared = radius * radius;
        for(int x = centerX - radius; x <= centerX + radius; x++){
            for(int y = centerY - radius; y <= centerY + radius; y++){
                int dx = x - centerX, dy = y - centerY;
                if(dx * dx + dy * dy <= radiusSquared && rand.chance(0.9f)){
                    tiles.getn(x, y).setFloor(Blocks.sand.asFloor());
                }
            }
        }
    }
}
