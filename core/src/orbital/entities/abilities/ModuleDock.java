package orbital.entities.abilities;

import arc.math.Angles;
import orbital.gen.*;

/**
 * Geometry shared by the dorsal modules that aim like the blocks they reproduce.
 *
 * <p>A module is docked at a hull-local anchor; the hull rotates, so the anchor is a point on
 * the hull and not a fixed world coordinate. Everything a turret-like module needs - where it
 * is, where it should point, what its current world angle is - goes through here, so the
 * convention lives in exactly one place.</p>
 *
 * <p>Angles follow {@code Draw.rect(region, x, y, rotation)}: degrees, with 0 pointing to the
 * right. A module's own aim is <b>relative to the hull</b>, so a module bolted nose-up still
 * tracks a target to starboard when the hull rolls.</p>
 */
public final class ModuleDock{
    private ModuleDock(){
    }

    /** @return the anchor's world X, already rotated into the hull. */
    public static float worldX(Unit unit, float anchorX, float anchorY){
        return unit.x + Angles.trnsx(unit.rotation - 90f, anchorX, anchorY);
    }

    /** @return the anchor's world Y, already rotated into the hull. */
    public static float worldY(Unit unit, float anchorX, float anchorY){
        return unit.y + Angles.trnsy(unit.rotation - 90f, anchorX, anchorY);
    }

    /** @return the hull's own rotation, in the same convention as the module aim. */
    public static float hullAngle(Unit unit){
        return unit.rotation - 90f;
    }

    /** @return the aim, relative to the hull, that points this module at (toX, toY). */
    public static float aimAt(Unit unit, float fromX, float fromY, float toX, float toY){
        return Angles.angle(fromX, fromY, toX, toY) - hullAngle(unit);
    }

    /** @return the module's current aim in world degrees. */
    public static float worldAngle(Unit unit, float relativeAim){
        return hullAngle(unit) + relativeAim;
    }

    /** @return the aim stepped toward the target at most {@code speed} degrees. */
    public static float turn(float current, float target, float speed, float delta){
        return Angles.moveToward(current, target, Math.max(speed * delta, 0f));
    }
}
