package orbital.input;

import arc.Core;
import arc.math.geom.Vec2;
import arc.struct.IntSeq;
import orbital.ai.UnitCommand;
import orbital.ai.UnitStance;
import orbital.gen.*;

import static orbital.Vars.*;

/**
 * "Fixed camera" control mode for the test/play loop.
 *
 * <p>When the player enables it from the top-bar toggle, the camera stops following the controlled
 * unit and is instead panned by dragging. Clicking an allied unit selects it and offers a Move
 * button; pressing it arms a destination pick, and the next map click sends that unit to the spot
 * through the normal authoritative command pipeline.</p>
 *
 * <p>All mutable state is local and transient (per client, per session) except the persisted on/off
 * preference, so multiplayer authority is untouched: the actual order goes through
 * {@link InputHandler#commandUnits}, exactly like a command-mode order.</p>
 */
public final class FixedCameraControl{
    /** Persisted preference key for the top-bar toggle. */
    public static final String key = "fixed-camera";

    /** The unit clicked while the mode was active, waiting for a Move order. */
    private static Unit selected;
    /** True while waiting for the map click that sets the move destination. */
    private static boolean awaitingDestination;

    private FixedCameraControl(){
    }

    public static boolean enabled(){
        return Core.settings != null && Core.settings.getBool(key, false);
    }

    public static void toggle(){
        Core.settings.put(key, !enabled());
        clear();
    }

    /** @return the unit currently selected for a move order, or null. */
    public static Unit selected(){
        if(selected != null && (!selected.isValid() || selected.team != player.team())) selected = null;
        return selected;
    }

    public static void select(Unit unit){
        if(unit != null && unit.team == player.team() && unit.isCommandable() && unit.isValid()){
            selected = unit;
            awaitingDestination = false;
        }else{
            clear();
        }
    }

    public static void clear(){
        selected = null;
        awaitingDestination = false;
    }

    public static boolean awaitingDestination(){
        return awaitingDestination;
    }

    /** Arms the destination pick for the currently selected unit. */
    public static void armMove(){
        Unit unit = selected();
        if(unit != null && unit.isCommandable()) awaitingDestination = true;
    }

    public static void cancelMove(){
        awaitingDestination = false;
    }

    /**
     * Sends the selected unit to {@code pos}. Returns true when an order was actually issued.
     */
    public static boolean moveTo(Vec2 pos){
        Unit unit = selected();
        if(unit == null || !awaitingDestination || pos == null) return false;
        awaitingDestination = false;

        IntSeq ids = IntSeq.with(unit.id);
        //Clear "hold position" and make sure the unit switches to a move command, then order it
        //to the chosen spot through the same authoritative pipeline command mode uses.
        InputHandler.setUnitStanceChunked(ids, UnitStance.holdPosition, false);
        InputHandler.setUnitCommandChunked(ids, UnitCommand.moveCommand);
        Call.commandUnits(player, new int[]{unit.id}, null, null, pos, false, true);
        ui.showInfoToast(Core.bundle.get("fixedcamera.sent"), 2f);
        selected = null;
        return true;
    }
}
