package orbital.net.online;

import arc.math.Mathf;
import orbital.core.*;
import orbital.entities.*;
import orbital.gen.*;

import static orbital.Vars.*;

/**
 * Server-side re-simulation of a player input command.
 *
 * <p>Unlike the legacy {@code clientSnapshot} handler (which copied the client's position after a
 * loose distance check), this derives the new state purely from the authoritative input and the
 * unit's own physics. A modified client can only change its intentions, not teleport or set an
 * impossible velocity.</p>
 */
public final class InputSimulator{
    /** Clamps one movement component to a sane per-tick limit before applying. */
    private static final float MAX_MOVE = 8f;

    private InputSimulator(){
    }

    /** Applies {@code command} to the player's unit on the server. */
    public static void apply(Unit unit, InputCommand command){
        if(unit == null || !unit.isValid() || command == null) return;

        //Aim: pure intention, always accepted.
        unit.aim(command.aimX(), command.aimY());

        //Shooting: clamped to what the unit can actually do. Boost is expressed as the unit's
        //elevation target (PlayerComp derives it from the boost flag each tick).
        boolean shooting = command.shooting();
        if(unit.type.flying || !unit.type.canBoost) shooting = shooting && unit.type.canBoost;
        unit.controlWeapons(shooting, shooting);
        if(unit.type.canBoost){
            float target = command.boosting() ? 1f : 0f;
            unit.elevation = Mathf.approachDelta(unit.elevation, target,
                target > 0 ? unit.type.riseSpeed : unit.type.descentSpeed);
        }

        //Movement: ignore invalid values and clamp magnitude; never copy a raw client position.
        float mx = Mathf.clamp(command.moveX(), -MAX_MOVE, MAX_MOVE);
        float my = Mathf.clamp(command.moveY(), -MAX_MOVE, MAX_MOVE);
        if(!Float.isFinite(mx)) mx = 0;
        if(!Float.isFinite(my)) my = 0;

        float speed = unit.speed();
        if(unit.isFlying()){
            unit.trns(mx * speed, my * speed);
        }else{
            unit.move(mx * speed, my * speed);
        }

        confineToQuadrant(unit);
    }

    /** Keeps a squad player inside the 80x80-tile quadrant assigned to its team slot. */
    private static void confineToQuadrant(Unit unit){
        //Only squad-owned sectors use the four-quadrant layout.
        if(state.rules.sector == null || state.rules.sector.squadSeed < 0) return;

        Player player = unit.getPlayer();
        if(player == null || player.con == null) return;

        String squadId = SquadAssigner.squadOf(player.con.uuid);
        if(squadId == null) return;

        SquadSectorRegistry registry = OnlineHolder.squads();
        if(registry == null) return;
        int slot = registry.slot(squadId, player.con.uuid);
        if(slot < 0 || slot > 3) return;

        int half = orbital.maps.generators.QuatiaSectorGenerator.size / 2;
        int minX = (slot % 2 == 0) ? 0 : half;
        int maxX = (slot % 2 == 0) ? half : orbital.maps.generators.QuatiaSectorGenerator.size;
        int minY = (slot < 2) ? 0 : half;
        int maxY = (slot < 2) ? half : orbital.maps.generators.QuatiaSectorGenerator.size;

        //Two-tile margin so the unit never sits on the exact division line.
        float margin = World.conv(2f);
        unit.x = Mathf.clamp(unit.x, World.conv(minX) + margin, World.conv(maxX) - margin);
        unit.y = Mathf.clamp(unit.y, World.conv(minY) + margin, World.conv(maxY) - margin);
    }
}
