package orbital.desktop.vulkan.gl;

import arc.graphics.GL20;
import org.junit.jupiter.api.Test;

import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure CPU tests for the GL vertex-attribute translation. These run without any Vulkan driver,
 * so they cover the layout/packing code even when the offscreen render test has to skip.
 */
class VulkanGL20VertexPackingTest{
    @Test
    void packsInterleavedStrideWithSourceOffsets(){
        VulkanGL20 gl = new VulkanGL20();

        int buffer = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buffer);
        FloatBuffer vertices = FloatBuffer.wrap(new float[]{
            -1f, 1f, 0f, 0f,
             1f, 1f, 1f, 0f,
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f
        });
        gl.glBufferData(GL20.GL_ARRAY_BUFFER, vertices.capacity() * Float.BYTES, vertices, GL20.GL_STATIC_DRAW);

        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        int[] layout = gl.layoutForTest();
        assertNotNull(layout);
        assertEquals(4 * Float.BYTES, layout[0], "pipeline stride");
        assertArrayEquals(new int[]{0, 2, 0, 1, 2, 8}, new int[]{layout[1], layout[2], layout[3], layout[4], layout[5], layout[6]});

        float[] packed = gl.packForTest(0, 4);
        assertNotNull(packed);
        assertEquals(16, packed.length);
        for(int vertex = 0; vertex < 4; vertex++){
            int base = vertex * 4;
            assertEquals(vertices.get(base), packed[base], 0.0001f);
            assertEquals(vertices.get(base + 1), packed[base + 1], 0.0001f);
            assertEquals(vertices.get(base + 2), packed[base + 2], 0.0001f);
            assertEquals(vertices.get(base + 3), packed[base + 3], 0.0001f);
        }
    }

    @Test
    void packsSeparateTightBuffersIntoOneInterleavedLayout(){
        VulkanGL20 gl = new VulkanGL20();

        int positions = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, positions);
        FloatBuffer positionData = FloatBuffer.wrap(new float[]{-1f, -1f, 1f, -1f, 0f, 1f});
        gl.glBufferData(GL20.GL_ARRAY_BUFFER, positionData.capacity() * Float.BYTES, positionData, GL20.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, 0, 0);

        int colors = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, colors);
        FloatBuffer colorData = FloatBuffer.wrap(new float[]{
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 1f
        });
        gl.glBufferData(GL20.GL_ARRAY_BUFFER, colorData.capacity() * Float.BYTES, colorData, GL20.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 4, GL20.GL_FLOAT, false, 0, 0);

        int[] layout = gl.layoutForTest();
        assertNotNull(layout);
        assertEquals((2 + 4) * Float.BYTES, layout[0], "packed pipeline stride");
        assertArrayEquals(new int[]{0, 2, 0, 1, 4, 2 * Float.BYTES},
            new int[]{layout[1], layout[2], layout[3], layout[4], layout[5], layout[6]});

        float[] packed = gl.packForTest(0, 3);
        assertNotNull(packed);
        assertEquals(18, packed.length);
        assertArrayEquals(new float[]{-1f, -1f, 1f, 0f, 0f, 1f}, slice(packed, 0));
        assertArrayEquals(new float[]{1f, -1f, 0f, 1f, 0f, 1f}, slice(packed, 6));
        assertArrayEquals(new float[]{0f, 1f, 0f, 0f, 1f, 1f}, slice(packed, 12));
    }

    private static float[] slice(float[] values, int offset){
        float[] out = new float[6];
        System.arraycopy(values, offset, out, 0, out.length);
        return out;
    }
}
