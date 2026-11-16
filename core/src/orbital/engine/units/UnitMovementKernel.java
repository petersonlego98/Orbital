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
        integrate(units.x, units.y, units.velocityX, units.velocityY, drag, index, delta, true);
    }

    /** Raw-array form shared with the SIMD fallback, so the formula has one definition. */
    public static void integrate(float[] x, float[] y, float[] vx, float[] vy, float[] drag,
                                 int index, float delta, boolean zeroUnmoved){
        float px = x[index], py = y[index];
        float sx = vx[index], sy = vy[index];

        float nx = px + sx * delta, ny = py + sy * delta;
        if(zeroUnmoved && Mathf.equal(px, nx)) sx = 0f;
        if(zeroUnmoved && Mathf.equal(py, ny)) sy = 0f;

        float scale = Math.max(1f - drag[index] * delta, 0f);
        x[index] = nx;
        y[index] = ny;
        vx[index] = sx * scale;
        vy[index] = sy * scale;
    }
}