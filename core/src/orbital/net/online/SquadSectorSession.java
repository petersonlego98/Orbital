package orbital.net.online;

import arc.files.*;
import arc.util.*;
import orbital.content.*;
import orbital.io.*;
import orbital.type.*;

import static orbital.Vars.*;

/**
 * Loads (or generates) the planet sector assigned to a squad.
 *
 * <p>Each squad owns one of Quatia's 272 sectors and that sector is generated with four
 * quadrants, one per player. The resulting world is persisted to a per-group save file so a
 * reload never regenerates a different layout.</p>
 */
public final class SquadSectorSession{
    private SquadSectorSession(){
    }

    /** @return the per-group save file (under the saves directory). */
    public static Fi sectorFile(String squadId){
        return saveDirectory.child("squad-" + squadId + ".msav");
    }

    /** Generates the squad's planet sector and writes it to its save file. */
    public static void generateAndSave(String squadId){
        SquadSectorRegistry registry = OnlineHolder.squads();
        if(registry == null) throw new IllegalStateException("squad registry not installed");
        SquadSectorRegistry.Squad squad = registry.byId(squadId);
        if(squad == null) throw new IllegalArgumentException("unknown squad: " + squadId);
        if(Planets.qatia == null) throw new IllegalStateException("Quatia not loaded");

        int sectorId = registry.sectorIdFor(squadId);
        if(sectorId < 0 || sectorId >= Planets.qatia.sectors.size){
            throw new IllegalStateException("squad " + squadId + " has no planet sector");
        }

        Sector sector = Planets.qatia.sectors.get(sectorId);
        sector.squadSeed = squad.seed;

        logic.reset();
        world.loadSector(sector);

        SaveIO.save(sectorFile(squadId));
        Log.info("[squad] sector @ saved to @", sectorId, sectorFile(squadId));
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
