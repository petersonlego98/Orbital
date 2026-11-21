package orbital.net.online;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player input queue for authoritative re-simulation.
 *
 * <p>The network thread may deliver several inputs for a player before the server simulates that
 * tick. They are queued by tick and consumed when the simulation reaches it. Missing ticks reuse
 * the last command (a dropped UDP input must not freeze a unit), and multiple commands for the same
 * tick keep only the latest — input is an intention, not an event that must be applied twice.</p>
 */
public final class InputBuffer{
    private final Map<Integer, Map<Long, InputCommand>> byPlayer = new HashMap<>();
    private final Map<Integer, InputCommand> last = new HashMap<>();

    /** Accepts a command from a player. Thread-safe against the simulation consumer. */
    public synchronized void accept(int playerId, InputCommand command){
        byPlayer.computeIfAbsent(playerId, k -> new HashMap<>()).put(command.tick(), command);
        last.put(playerId, command);
    }

    /**
     * Consumes the command for {@code playerId} at {@code tick}. If none arrived, returns the most
     * recent command so the player keeps their last intention across a lost packet.
     */
    public synchronized InputCommand consume(int playerId, long tick){
        Map<Long, InputCommand> player = byPlayer.get(playerId);
        InputCommand command = player == null ? null : player.remove(tick);
        if(command != null) last.put(playerId, command);
        InputCommand result = command != null ? command : last.get(playerId);
        if(player != null && player.isEmpty()) byPlayer.remove(playerId);
        return result;
    }

    /** Drops queued commands older than {@code tick} to bound memory. */
    public synchronized void discardBefore(long tick){
        for(Map<Long, InputCommand> player : byPlayer.values()) player.keySet().removeIf(t -> t < tick);
        byPlayer.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public synchronized void clearPlayer(int playerId){
        byPlayer.remove(playerId);
        last.remove(playerId);
    }

    public synchronized void clear(){
        byPlayer.clear();
        last.clear();
    }
}
