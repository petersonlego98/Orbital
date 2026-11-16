package orbital.engine.particles;

import arc.math.Mathf;
import orbital.engine.OrbitalEngine;

/**
 * Builds the particle vertex buffer, in parallel when the batch is large enough.
 *
 * <p>Every particle owns a disjoint slot of four floats in the output buffer, so workers never
 * touch the same memory and no lock is needed. The buffer itself stays owned by the renderer; this
 * class only fills it and reports how many vertices are valid.</p>
 *
 * <p>Below {@link #parallelThreshold} particles the loop runs on the calling thread: handing a
 * few hundred items to the pool costs more than the arithmetic.</p>
 */
public final class ParticleVertexBuilder{
    /** Particle layout: age, lifetime, x, y, vx, vy, sizeFrom, sizeTo, color. */
    public static final int particleStride = 9;
    /** Vertex layout: x, y, size, color. */
    public static final int vertexStride = 4;
    /** Below this many particles the build stays on the calling thread. */
    public static final int parallelThreshold = 2048;

    private ParticleVertexBuilder(){
    }

    /**
     * Fills {@code vertices} with one quad-sprite slot per particle.
     *
     * @return the number of valid floats in {@code vertices}.
     */
    public static int build(float[] data, int particleCount, float[] vertices){
        if(particleCount <= 0) return 0;

        boolean parallel = OrbitalEngine.parallelForIfUseful(
            "graphics.particles.vertices", particleCount, parallelThreshold,
            index -> writeVertex(data, vertices, index));

        if(!parallel){
            for(int index = 0; index < particleCount; index++){
                writeVertex(data, vertices, index);
            }
        }

        return particleCount * vertexStride;
    }

    /**
     * Writes one particle's vertex slot. Public so the validation harness can build the sequential
     * reference without duplicating the lerp formula.
     */
    public static void writeVertex(float[] data, float[] vertices, int particle){
        int source = particle * particleStride;
        int target = particle * vertexStride;

        float color = data[source + 8]; //TODO: colorTo
        float size = Mathf.lerp(data[source + 6], data[source + 7],
            Mathf.clamp(data[source] / data[source + 1]));

        vertices[target] = data[source + 2];
        vertices[target + 1] = data[source + 3];
        vertices[target + 2] = size;
        vertices[target + 3] = color;
    }
}
