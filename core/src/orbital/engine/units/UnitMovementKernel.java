package orbital.engine.units;

import arc.math.Mathf;
import orbital.engine.memory.UnitData;

/**
 * The unit velocity integration kernel, shared by the worker phase and the validation harness.
 *
 * <p>This is the only place the movement math lives. {@link UnitMovementTick} runs it on the engine
 * pool, the vanilla game loop runs the same formula inside {@code VelComp.update()}, and
 * {@link orbital.engine.validation.EngineValidation} runs it sequentially and in parallel to prove
 * the two agree. Keeping one implementation is what makes "the engine is equivalent to vanilla" a
 * testable claim instead of a comment.</p>
 */
public final class UnitMovementKernel{
    private UnitMovementKernel(){
    }

    /**
     * Integrates one unit row in place, in the exact order the vanilla comp uses:
     * move by {@code vel * delta}, zero a velocity axis whose move did not change the position
     * ({@code Mathf.equal}), then apply drag.
     *
     * @param units position and velocity source and destination
     * @param drag   per-row drag, the vanilla comp's {@code drag} field copied during capture
     */
    public static void integrate(UnitData units, float[] drag, int index, float delta){
        float px = units.x[index], py = units.y[index];
        float vx = units.velocityX[index], vy = units.velocityY[index];

        float nx = px + vx * delta, ny = py + vy * delta;
        if(Mathf.equal(px, nx)) vx = 0f;
        if(Mathf.equal(py, ny)) vy = 0f;

        float scale = Math.max(1f - drag[index] * delta, 0f);
        units.x[index] = nx;
        units.y[index] = ny;
        units.velocityX[index] = vx * scale;
        units.velocityY[index] = vy * scale;
    }
}