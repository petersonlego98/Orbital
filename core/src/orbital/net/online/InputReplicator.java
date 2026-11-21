package orbital.net.online;

import arc.util.Log;
import orbital.gen.*;

import java.io.IOException;

import static orbital.Vars.*;

/**
 * Server-side input authority.
 *
 * <p>Receives compact {@link InputCommand}s from clients, buffers them by tick, and applies them to
 * each player's unit during the authoritative simulation step. The client's raw position is never
 * trusted — the server derives it from the command and its own physics. That closes the cheat hole
 * of the legacy {@code clientSnapshot} and makes the tick reproducible.</p>
 */
public final class InputReplicator{
    /** Binary channel used for input commands (client -> server). */
    public static final String CHANNEL = "orbital.input";

    private final InputBuffer buffer = new InputBuffer();
    private long lastAppliedTick = -1;

    /** Installs the binary receive handler on the server. Call once at startup. */
    public void install(){
        netServer.addBinaryPacketHandler(CHANNEL, this::receive);
        Log.info("[online] input channel installed");
    }

    private void receive(Player player, byte[] data){
        if(player == null) return;
        try{
            buffer.accept(player.id, InputCommand.decode(data));
        }catch(IOException e){
            Log.err("[online] bad input command from @: @", player.id, e.getMessage());
        }
    }

    /**
     * Applies buffered inputs for the current tick before the world simulation. Called from the
     * authoritative step in {@code Logic}, right after {@code TickRng.beginTick}.
     */
    public void applyTick(long tick){
        if(tick <= lastAppliedTick) return;
        lastAppliedTick = tick;
        buffer.discardBefore(tick);

        for(Player player : Groups.player){
            if(player.con == null || !player.con.isConnected()) continue;
            InputCommand command = buffer.consume(player.id, tick);
            if(command == null) continue;
            InputSimulator.apply(player.unit(), command);

            //Mirror the intended aim/flags onto the player so the rest of the sim sees them.
            player.mouseX = command.aimX();
            player.mouseY = command.aimY();
            player.shooting = command.shooting();
            player.boosting = command.boosting();
        }
    }

    public InputBuffer buffer(){
        return buffer;
    }
}
