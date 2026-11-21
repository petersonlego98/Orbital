package orbital.net.online;

import orbital.gen.*;

import static orbital.Vars.*;

/**
 * Client side: samples the local player's intentions and sends one {@link InputCommand} per tick.
 *
 * <p>Only intentions cross the wire — never the resulting position. The server re-simulates and
 * publishes the authoritative result, so a modified client cannot teleport. When the local player
 * has no unit, a neutral command is sent so the server still advances the tick uniformly.</p>
 */
public final class InputSender{
    private long lastTick = -1;

    /** Samples and sends the current input if the tick advanced. */
    public void send(){
        if(!net.client() || player == null) return;
        long tick = (long)state.tick;
        if(tick <= lastTick) return;
        lastTick = tick;

        Unit unit = player.unit();
        float aimX = player.mouseX, aimY = player.mouseY;
        //Movement intention from the unit's current velocity direction; the server clamps it.
        float mx = unit == null ? 0 : unit.vel().x;
        float my = unit == null ? 0 : unit.vel().y;

        InputCommand command = new InputCommand(tick, aimX, aimY, mx, my,
            player.shooting, player.boosting, player.isBuilder());
        Call.clientBinaryPacketUnreliable(InputReplicator.CHANNEL, command.encode());
    }

    public void reset(){
        lastTick = -1;
    }
}
