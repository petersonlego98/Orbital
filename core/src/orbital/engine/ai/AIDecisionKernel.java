package orbital.engine.ai;

import arc.math.Mathf;
import orbital.engine.memory.AIData;

/**
 * One independent AI steering decision.
 *
 * <p>Given a unit's position and its current target point, the kernel writes a normalised desired
 * direction plus an arrival slowdown inside {@code arrivalRadius}. It touches only its own row, so
 * the job system may run thousands of decisions at once without synchronisation. This is the pure
 * part of an AI step: the world still owns controllers, events, weapons and construction, which is
 * why the result is committed on the game thread.</p>
 */
public final class AIDecisionKernel{
    /** Distance at which the desired speed starts dropping, in world units. */
    public static final float arrivalRadius = 32f;
    /** Positions closer together than this are treated as "already there". */
    public static final float arrivedEpsilon = 1e-4f;

    private AIDecisionKernel(){
    }

    public static void decide(AIData data, int index){
        float dx = data.targetX[index] - data.x[index];
        float dy = data.targetY[index] - data.y[index];
        float distance = Mathf.sqrt(dx * dx + dy * dy);
        data.distance[index] = distance;

        if(distance <= arrivedEpsilon){
            data.desiredX[index] = 0f;
            data.desiredY[index] = 0f;
            return;
        }

        float speed = Math.min(1f, distance / arrivalRadius);
        float scale = speed / distance;
        data.desiredX[index] = dx * scale;
        data.desiredY[index] = dy * scale;
    }
}