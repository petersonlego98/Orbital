package orbital.engine.particles;

import arc.math.Mathf;
import orbital.engine.OrbitalEngine;

/**
 * Builds the particle vertex buffer, with optional viewport culling, in parallel when worth it.
 *
 * <p>Every particle owns a disjoint slot in the output buffer, so workers never touch the same
 * memory. Culling first computes a compact output index per visible particle (a single sequential
 * prefix pass over integers), then the write pass fills only those slots. The result keeps the
 * original particle order, which matters for stable alpha blending and for tests comparing the
 * parallel and sequential paths.</p>
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

    /** No culling: every particle becomes a vertex. Kept for tests and off-screen rendering. */
    public static int build(float[] data, int particleCount, float[] vertices){
        return build(data, particleCount, vertices,
            Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0f, new int[particleCount]);
    }

    /**
     * Builds vertices for particles inside the camera rectangle.
     *
     * @param offsets reusable scratch array of at least {@code particleCount} ints
     * @return the number of valid floats written to {@code vertices}
     */
    public static int build(float[] data, int particleCount, float[] vertices,
                            float cameraX, float cameraY, float cameraWidth, float cameraHeight,
                            float padding, int[] offsets){
        if(particleCount <= 0) return 0;

        boolean cull = !Float.isNaN(cameraX) && !Float.isNaN(cameraY)
            && cameraWidth > 0f && cameraHeight > 0f;
        float minX = cameraX - cameraWidth / 2f - padding;
        float maxX = cameraX + cameraWidth / 2f + padding;
        float minY = cameraY - cameraHeight / 2f - padding;
        float maxY = cameraY + cameraHeight / 2f + padding;

        //pass 1: compact visible particles, preserving their relative order
        int visible = 0;
        for(int i = 0; i < particleCount; i++){
            int source = i * particleStride;
            if(cull){
                float x = data[source + 2], y = data[source + 3];
                float size = Math.max(data[source + 6], data[source + 7]);
                if(x + size < minX || x - size > maxX || y + size < minY || y - size > maxY){
                    offsets[i] = -1;
                    continue;
                }
            }
            //the write pass needs a float offset into the vertex buffer, not a particle index
            offsets[i] = (visible++) * vertexStride;
        }

        //pass 2: disjoint writes, parallel for large batches
        boolean parallel = OrbitalEngine.parallelForIfUseful(
            "graphics.particles.vertices", particleCount, parallelThreshold,
            index -> {
                int slot = offsets[index];
                if(slot >= 0) writeVertex(data, vertices, index, slot);
            });

        if(!parallel){
            for(int i = 0; i < particleCount; i++){
                int slot = offsets[i];
                if(slot >= 0) writeVertex(data, vertices, i, slot);
            }
        }

        return visible * vertexStride;
    }

    /** Writes one particle's vertex slot. Public so the validation harness has one formula. */
    public static void writeVertex(float[] data, float[] vertices, int particle){
        writeVertex(data, vertices, particle, particle * vertexStride);
    }

    /** Writes a particle into an explicit output slot, used by compacting cull tests. */
    public static void writeVertex(float[] data, float[] vertices, int particle, int target){
        int source = particle * particleStride;

        float color = data[source + 8]; //TODO: colorTo
        float size = Mathf.lerp(data[source + 6], data[source + 7],
            Mathf.clamp(data[source] / data[source + 1]));

        vertices[target] = data[source + 2];
        vertices[target + 1] = data[source + 3];
        vertices[target + 2] = size;
        vertices[target + 3] = color;
    }
}