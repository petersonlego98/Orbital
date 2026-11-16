package orbital.engine.particles;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ParticleVertexBuilderTest{

    @Test
    void buildsOneVertexSlotPerParticle(){
        float[] data = particles(10);
        float[] vertices = new float[10 * ParticleVertexBuilder.vertexStride];

        int length = ParticleVertexBuilder.build(data, 10, vertices);

        assertEquals(40, length);
        for(int i = 0; i < 10; i++){
            int source = i * ParticleVertexBuilder.particleStride;
            int target = i * ParticleVertexBuilder.vertexStride;
            assertEquals(data[source + 2], vertices[target]);
            assertEquals(data[source + 3], vertices[target + 1]);
            assertEquals(data[source + 8], vertices[target + 3]);
            float sizeFrom = data[source + 6];
            float sizeTo = data[source + 7];
            float size = vertices[target + 2];
            assertTrue(size >= Math.min(sizeFrom, sizeTo) && size <= Math.max(sizeFrom, sizeTo),
                "the interpolated size must stay between the two sizes");
        }
    }

    @Test
    void emptyBatchProducesNothing(){
        float[] vertices = new float[4];
        assertEquals(0, ParticleVertexBuilder.build(new float[0], 0, vertices));
        assertEquals(0, vertices[0]);
    }

    @Test
    void largeBatchesUseTheSameValuesAsTheSequentialPath(){
        float[] data = particles(5_000);
        float[] parallel = new float[5_000 * ParticleVertexBuilder.vertexStride];
        float[] sequential = new float[5_000 * ParticleVertexBuilder.vertexStride];

        ParticleVertexBuilder.build(data, 5_000, parallel);
        for(int i = 0; i < 5_000; i++){
            writeSequential(data, sequential, i);
        }

        assertArrayEquals(sequential, parallel, 0f);
    }

    private static void writeSequential(float[] data, float[] vertices, int particle){
        int source = particle * ParticleVertexBuilder.particleStride;
        int target = particle * ParticleVertexBuilder.vertexStride;
        float age = data[source];
        float lifetime = data[source + 1];
        float sizeFrom = data[source + 6];
        float sizeTo = data[source + 7];
        float progress = Math.max(0f, Math.min(1f, age / lifetime));
        vertices[target] = data[source + 2];
        vertices[target + 1] = data[source + 3];
        vertices[target + 2] = sizeFrom + (sizeTo - sizeFrom) * progress;
        vertices[target + 3] = data[source + 8];
    }

    private static float[] particles(int count){
        Random random = new Random(4242L + count);
        float[] data = new float[count * ParticleVertexBuilder.particleStride];
        for(int i = 0; i < count; i++){
            int offset = i * ParticleVertexBuilder.particleStride;
            data[offset] = random.nextFloat();
            data[offset + 1] = 1f + random.nextFloat();
            data[offset + 2] = random.nextFloat() * 100f;
            data[offset + 3] = random.nextFloat() * 100f;
            data[offset + 4] = 0f;
            data[offset + 5] = 0f;
            data[offset + 6] = random.nextFloat() * 4f;
            data[offset + 7] = random.nextFloat() * 4f;
            data[offset + 8] = random.nextFloat();
        }
        return data;
    }
}
