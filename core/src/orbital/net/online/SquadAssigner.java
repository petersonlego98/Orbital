package orbital.net.online;

import arc.struct.*;
import arc.util.*;
import orbital.content.*;
import orbital.game.*;
import orbital.gen.Player;
import orbital.type.*;

import static orbital.Vars.*;

/**
 * Assigns each connecting player to a squad of up to 4 and to the matching team slot.
 *
 * <p>Called on the server at connect time. The squad -> sector mapping is persisted by
 * {@link SquadSectorRegistry}, so reconnecting with the same uuid returns the same squad, the same
 * planet sector and the same quadrant. A different fourth player starts a new squad with its own
 * sector.</p>
 */
public final class SquadAssigner{
    /** uuid -> squad id, used by the authoritative input simulator to confine quadrants. */
    private static final ObjectMap<String, String> playerSquads = new ObjectMap<>();

    private SquadAssigner(){
    }

    /** @return the team assigned to this player, or null when squads are not active. */
    public static Team assign(Player player){
        SquadSectorRegistry registry = OnlineHolder.squads();
        if(registry == null || player == null || player.con == null) return null;

        String uuid = player.con.uuid;
        if(uuid == null || uuid.equals("AAAAAAAA")) return null; //handshake not complete yet

        SquadSectorRegistry.Squad squad = registry.assign(uuid);
        int slot = registry.slot(squad.id, uuid);
        Team[] teams = SquadSectorGenerator.teams();
        if(slot < 0 || slot >= teams.length) slot = 0;

        playerSquads.put(uuid, squad.id);
        bindSector(registry, squad);

        Log.info("[squad] @ -> @ slot @ (sector @, seed @)", uuid, squad.id, slot, squad.sectorId, squad.seed);
        return teams[slot];
    }

    /** Marks the squad's planet sector so its generator produces the four-player layout. */
    private static void bindSector(SquadSectorRegistry registry, SquadSectorRegistry.Squad squad){
        if(Planets.qatia == null || squad.sectorId < 0 || squad.sectorId >= Planets.qatia.sectors.size) return;
        Sector sector = Planets.qatia.sectors.get(squad.sectorId);
        sector.squadSeed = squad.seed;
    }

    /** @return the squad id for a player uuid, or null when squads are inactive. */
    public static @Nullable String squadOf(String uuid){
        return uuid == null ? null : playerSquads.get(uuid);
    }
}
