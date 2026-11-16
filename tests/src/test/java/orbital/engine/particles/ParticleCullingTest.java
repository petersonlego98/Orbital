package orbital.engine.particles;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Tests viewport culling and the compacted vertex order, sequential and parallel. */
class ParticleCullingTest{

    @Test
    void onlyVisibleParticlesAreWrittenInOriginalOrder(){
        int count = 3000; //above the parallel threshold
        float[] data = particles(count, 111L);
        float[] vertices = new float[count * ParticleVertexBuilder.vertexStride];
        int[] offsets = new int[count];

        int length = ParticleVertexBuilder.build(data, count, vertices,
            0f, 0f, 200f, 200f, 0f, offsets);

        float[] expected = new float[vertices.length];
        int visible = 0;
        for(int i = 0; i < count; i++){
            int source = i * ParticleVertexBuilder.particleStride;
            float x = data[source + 2], y = data[source + 3];
            float size = Math.max(data[source + 6], data[source + 7]);
            if(x + size < -100f || x - size > 100f || y + size < -100f || y - size > 100f) continue;
            ParticleVertexBuilder.writeVertex(data, expected, i, visible * ParticleVertexBuilder.vertexStride);
            visible++;
        }

        assertEquals(visible * ParticleVertexBuilder.vertexStride, length);
        assertArrayEquals(expected, vertices, 0f);

        int written = 0;
        for(int i = 0; i < count; i++){
            if(offsets[i] >= 0) written++;
        }
        assertEquals(visible, written, "the offset pass must mark exactly the visible particles");
    }

    @Test
    void paddingExpandsTheViewport(){
        float[] data = new float[ParticleVertexBuilder.particleStride];
        data[0] = 0f;
        data[1] = 1f;
        data[2] = 105f; //outside a 200-wide viewport, inside with padding
        data[3] = 0f;
        data[6] = 1f;
        data[7] = 1f;
        data[8] = 0.5f;

        int[] offsets = new int[1];
        float[] vertices = new float[4];

        int without = ParticleVertexBuilder.build(data, 1, vertices, 0f, 0f, 200f, 200f, 0f, offsets);
        int with = ParticleVertexBuilder.build(data, 1, vertices, 0f, 0f, 200f, 200f, 10f, offsets);

        assertEquals(0, without);
        assertEquals(ParticleVertexBuilder.vertexStride, with);
        assertEquals(105f, vertices[0], 0f);
    }

    @Test
    void noCullingOverloadStillWritesEveryParticle(){
        float[] data = particles(64, 9L);
        float[] vertices = new float[64 * ParticleVertexBuilder.vertexStride];

        int length = ParticleVertexBuilder.build(data, 64, vertices);

        assertEquals(vertices.length, length);
        for(int i = 0; i < 64; i++){
            assertEquals(data[i * ParticleVertexBuilder.particleStride + 2],
                vertices[i * ParticleVertexBuilder.vertexStride], 0f);
        }
    }

    private static float[] particles(int count, long seed){
        Random random = new Random(seed);
        float[] data = new float[count * ParticleVertexBuilder.particleStride];
        for(int i = 0; i < count; i++){
            int offset = i * ParticleVertexBuilder.particleStride;
            data[offset] = random.nextFloat() * 0.5f;
            data[offset + 1] = 1f;
            data[offset + 2] = (random.nextFloat() - 0.5f) * 400f;
            data[offset + 3] = (random.nextFloat() - 0.5f) * 400f;
            data[offset + 4] = 0f;
            data[offset + 5] = 0f;
            data[offset + 6] = 1f;
            data[offset + 7] = 1f;
            data[offset + 8] = random.nextFloat();
        }
        return data;
    }
}