package orbital.net.online;

import arc.util.*;
import orbital.game.*;
import orbital.gen.Player;

import static orbital.Vars.*;

/**
 * Assigns each connecting player to a squad of up to 4 and to the matching team slot.
 *
 * <p>Called on the server at connect time. The squad -> sector seed mapping is persisted by
 * {@link SquadSectorRegistry}, so reconnecting with the same uuid returns the same squad and the
 * same sector. A different fourth player starts a new squad with a different seed.</p>
 */
public final class SquadAssigner{
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

        Log.info("[squad] @ -> @ slot @ (seed @)", uuid, squad.id, slot, squad.seed);
        return teams[slot];
    }
}
