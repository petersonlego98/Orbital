package orbital.net.online;

import arc.struct.*;
import orbital.game.*;
import orbital.gen.Player;

/**
 * Assigns one of the eight Battle Royale teams to each connected player.
 *
 * <p>Unlike the 4-player squad used in the campaign, a battle royale match has exactly eight
 * human slots and no AI fallback: a ninth player is rejected. Assignment is persistent for the
 * lifetime of the match, so reconnecting with the same uuid returns the same team.</p>
 */
public final class BattleRoyaleTeamAssigner{
    private static final ObjectSet<Team> used = new ObjectSet<>();
    private static final ObjectMap<String, Team> playerTeams = new ObjectMap<>();

    private BattleRoyaleTeamAssigner(){
    }

    /** @return a free team, the player's existing team, or null when all eight slots are taken. */
    public static Team assign(Player player){
        if(player == null || player.con == null || player.con.uuid == null) return null;
        String uuid = player.con.uuid;

        Team existing = playerTeams.get(uuid);
        if(existing != null) return existing;

        for(Team team : BattleRoyaleMode.teams()){
            if(used.contains(team)) continue;
            used.add(team);
            playerTeams.put(uuid, team);
            return team;
        }
        return null;
    }

    /** Clears all assignments for a new match. */
    public static void reset(){
        used.clear();
        playerTeams.clear();
    }

    /** @return how many of the eight slots are occupied. */
    public static int count(){
        return used.size;
    }

    public static Team teamOf(String uuid){
        return uuid == null ? null : playerTeams.get(uuid);
    }
}
