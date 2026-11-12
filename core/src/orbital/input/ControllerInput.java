package orbital.input;

import arc.Core;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.util.Nullable;
import arc.util.Tmp;

/**
 * Reads a physical game controller (an Android gamepad, or a controller on the desktop backend).
 *
 * <p>Arc names the same physical button differently depending on the mapping the backend uses
 * (Xbox-style {@code controllerA} vs. PlayStation-style {@code buttonA}), and only some
 * backends report the analog sticks as axes. To keep the game working with any of them, this
 * helper reads <b>every</b> candidate key and returns zero when nothing is pressed, which is
 * the normal state when no controller is connected. No state is kept here: every method is a
 * pure read of {@link Core#input}.</p>
 *
 * <p>The whole helper obeys the {@code gamepad} setting of the controls menu: with it off, no
 * button, stick or d-pad reaches the game, which is what a player wants when a controller is
 * plugged in for something else.</p>
 */
public final class ControllerInput{
    private ControllerInput(){}

    /** Settings key of the controls menu that turns every read below on and off. */
    public static final String enabledKey = "gamepad";

    /** @return whether the player left the controller enabled in the controls menu. */
    public static boolean enabled(){
        return Core.settings.getBool(enabledKey, true);
    }

    /** Every face/trigger key that means "fire", across Arc's controller mappings. */
    private static final KeyCode[] fireKeys = {
        KeyCode.controllerA, KeyCode.buttonA,
        KeyCode.controllerRTrigger, KeyCode.buttonR2,
        KeyCode.controllerRBumper, KeyCode.buttonR1,
    };

    /** @return whether any fire key is held. */
    public static boolean firing(){
        if(!enabled()) return false;
        //the rebindable binding comes first, so the controls menu entry is the primary key
        if(Core.input.keyDown(Binding.controllerFire)) return true;

        for(KeyCode key : fireKeys){
            if(Core.input.keyDown(key)) return true;
        }
        return false;
    }

    /** @return the horizontal movement axis: left stick, then the d-pad as a fallback. */
    public static float moveX(){
        if(!enabled()) return 0f;

        float value = Core.input.axis(KeyCode.controllerLStickXAxis);
        if(Core.input.keyDown(KeyCode.controllerdPadLeft) || Core.input.keyDown(KeyCode.dpadLeft)) value -= 1f;
        if(Core.input.keyDown(KeyCode.controllerdPadRight) || Core.input.keyDown(KeyCode.dpadRight)) value += 1f;
        return value;
    }

    /** @return the vertical movement axis: left stick, then the d-pad as a fallback. */
    public static float moveY(){
        if(!enabled()) return 0f;

        float value = Core.input.axis(KeyCode.controllerLStickYAxis);
        if(Core.input.keyDown(KeyCode.controllerdPadDown) || Core.input.keyDown(KeyCode.dpadDown)) value -= 1f;
        if(Core.input.keyDown(KeyCode.controllerdPadUp) || Core.input.keyDown(KeyCode.dpadUp)) value += 1f;
        return value;
    }

    /** @return whether the stick or the d-pad is deflected at all. */
    public static boolean moving(){
        return moveX() != 0f || moveY() != 0f;
    }

    /** @return the horizontal aim axis of the right stick; 0 when the backend reports none. */
    public static float aimX(){
        return Core.input.axis(KeyCode.controllerRStickXAxis);
    }

    /** @return the vertical aim axis of the right stick; 0 when the backend reports none. */
    public static float aimY(){
        return Core.input.axis(KeyCode.controllerRStickYAxis);
    }

    /**
     * Writes the movement vector of the left stick/d-pad into {@code out}, clamped to unit
     * length. The out-parameter form avoids allocating a vector per frame.
     */
    public static void movement(Vec2 out){
        out.set(moveX(), moveY()).limit(1f);
    }

    /**
     * @return the aim vector of the right stick, or {@code null} when it sits in its dead
     * zone. The vector is a shared scratch one ({@link Tmp#v1}): read it and copy it, or use
     * it before the next {@code Tmp} read in the same frame.
     */
    public static @Nullable Vec2 aim(){
        float x = aimX(), y = aimY();
        //a dead zone, so a loose stick does not slowly spin a manual turret
        if(x * x + y * y < 0.15f * 0.15f) return null;
        return Tmp.v1.set(x, y).limit(1f);
    }
}
