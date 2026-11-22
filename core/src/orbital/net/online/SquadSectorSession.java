package orbital.net.online;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.io.*;
import orbital.maps.*;

import static orbital.Vars.*;

/**
 * Creates and persists the sector assigned to a squad.
 *
 * <p>The sector is generated in memory from the squad's persistent seed and then written to a
 * per-group save file, so reloading it always reproduces the same map. Two squads have two different
 * seeds and therefore two different sectors. Uses {@code createSave=false} semantics: the generated
 * map is not added to the campaign save list.</p>
 */
public final class SquadSectorSession{
    private SquadSectorSession(){
    }

    /** @return the per-group save file (under the saves directory). */
    public static Fi sectorFile(String squadId){
        return saveDirectory.child("squad-" + squadId + ".msav");
    }

    /** Generates, in memory, the sector for a squad and writes it to its save file. */
    public static void generateAndSave(String squadId){
        SquadSectorRegistry registry = OnlineHolder.squads();
        if(registry == null) throw new IllegalStateException("squad registry not installed");
        SquadSectorRegistry.Squad squad = registry.byId(squadId);
        if(squad == null) throw new IllegalArgumentException("unknown squad: " + squadId);

        Map map = new Map(StringMap.of("name", "Squad " + squadId, "author", "Orbital"));
        map.width = SquadSectorGenerator.size;
        map.height = SquadSectorGenerator.size;

        world.addMapLoader(map, () -> {
            world.loadGenerator(SquadSectorGenerator.size, SquadSectorGenerator.size, tiles -> {
                tiles.fill();
                SquadSectorGenerator.generate(tiles, squad.seed);
            });
        });

        logic.reset();
        Rules rules = Gamemode.survival.apply(new Rules());
        rules.modeName = "squad-" + squadId;
        control.playMap(map, rules, false, false);

        //Persist the generated sector so a reload never regenerates a different one.
        SaveIO.save(sectorFile(squadId));
        Log.info("[squad] sector for @ saved to @", squadId, sectorFile(squadId));
    }

    /** Loads a previously generated squad sector. */
    public static void load(String squadId){
        Fi file = sectorFile(squadId);
        if(!file.exists()){
            generateAndSave(squadId);
            return;
        }
        logic.reset();
        SaveIO.load(file);
        Log.info("[squad] sector for @ loaded", squadId);
    }
}
