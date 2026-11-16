package orbital.engine.combat;

import orbital.engine.memory.ProjectileData;

/**
 * The bullet velocity-integration kernel, shared by the worker phase and the validation harness.
 *
 * <p>The vanilla {@code BulletComp.update()} moves a bullet by {@code vel * delta} and scales the
 * velocity by {@code max(1 - type.drag * delta, 0)}. Unlike units, bullets do not zero an axis that
 * did not move, and they keep the "do not move on the frame they spawn" rule, which is handled by
 * the capture filter in {@link ProjectileTick}, not by this kernel.</p>
 */
public final class ProjectileKernel{
    private ProjectileKernel(){
    }

    /** Integrates one projectile row in place, in the exact order the vanilla comp uses. */
    public static void integrate(ProjectileData bullets, float[] drag, int index, float delta){
        float vx = bullets.velocityX[index], vy = bullets.velocityY[index];

        bullets.x[index] += vx * delta;
        bullets.y[index] += vy * delta;

        float scale = Math.max(1f - drag[index] * delta, 0f);
        bullets.velocityX[index] = vx * scale;
        bullets.velocityY[index] = vy * scale;
    }
}