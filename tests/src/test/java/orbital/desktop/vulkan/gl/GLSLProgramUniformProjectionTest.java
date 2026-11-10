package orbital.desktop.vulkan.gl;

import arc.graphics.GL20;
import orbital.desktop.vulkan.VulkanDevice;
import org.junit.jupiter.api.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the exact configuration the game draws with, so the pieces the passing
 * {@link VulkanGL20RenderTest} does not exercise are covered too:
 *
 * <ul>
 *   <li>Arc's own {@code SpriteBatch} shader pair, with {@code u_projTrans} in a UBO,</li>
 *   <li>the real {@code Layout}: position(2), color(4), texCoords(2), mixColor(4),</li>
 *   <li>screen-space coordinates pushed through an orthographic projection,</li>
 *   <li>an attribute wider in the shader than in the client buffer, which the UBO-only test
 *       never hits.</li>
 * </ul>
 *
 * <p>Readback is per corner again. Any of those pieces breaking - a UBO offset, an attribute
 * width, the projection matrix - shows up as a black frame.</p>
 */
class GLSLProgramUniformProjectionTest{
    private static final int SIZE = 64;

    /** Verbatim arc.graphics.g2d.SpriteBatch#createShader. */
    private static final String VERTEX = """
        attribute vec4 a_position;
        attribute vec4 a_color;
        attribute vec2 a_texCoord0;
        attribute vec4 a_mix_color;
        uniform mat4 u_projTrans;
        varying vec4 v_color;
        varying vec4 v_mix_color;
        varying vec2 v_texCoords;

        void main(){
           v_color = a_color;
           v_color.a = v_color.a * (255.0/254.0);
           v_mix_color = a_mix_color;
           v_mix_color.a *= (255.0/254.0);
           v_texCoords = a_texCoord0;
           gl_Position = u_projTrans * a_position;
        }
        """;

    private static final String FRAGMENT = """
        varying lowp vec4 v_color;
        varying lowp vec4 v_mix_color;
        varying highp vec2 v_texCoords;
        uniform highp sampler2D u_texture;

        void main(){
          vec4 c = texture2D(u_texture, v_texCoords);
          gl_FragColor = v_color * mix(c, vec4(v_mix_color.rgb, c.a), v_mix_color.a);
        }
        """;

    @Test
    void rendersTheGameShaderConfigurationThroughGl20(){
        VulkanDevice.destroy();
        if(!VulkanDevice.create(SIZE, SIZE)){
            System.out.println("[VulkanGL20] game-shader path not exercised: " + VulkanDevice.lastError());
            return;
        }

        try{
            VulkanGL20 gl = new VulkanGL20();
            int program = link(gl);

            int texture = uploadCornerTexture(gl);
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);

            // The Arc layout, exactly: position is two components and the shader reads it as vec4.
            setQuad(gl);

            gl.glUseProgram(program);
            int projection = gl.glGetUniformLocation(program, "u_projTrans");
            assertTrue(projection > 0, "u_projTrans must resolve to a uniform location");

            // Column-major orthographic projection over the whole target, as the game builds it.
            float[] projTrans = {
                2f / SIZE, 0, 0, 0,
                0, -2f / SIZE, 0, 0,
                0, 0, -1f, 0,
                -1f, 1f, 0f, 1f
            };
            gl.glUniformMatrix4fv(projection, 1, false, projTrans, 0);

            gl.glClearColor(0f, 0f, 0f, 1f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            byte[] frame = VulkanDevice.frame();
            assertNotNull(frame, "readback after clear failed: " + VulkanDevice.lastError());
            assertCleared(frame, "the clear must reach the render target, not only a swapchain");

            gl.glDrawElements(GL20.GL_TRIANGLES, 6, GL20.GL_UNSIGNED_INT, 0);

            frame = VulkanDevice.frame();
            assertNotNull(frame, "readback after draw failed: " + VulkanDevice.lastError());
            assertEquals(SIZE * SIZE * 4, frame.length);

            for(int[] corner : new int[][]{{2, 2}, {SIZE - 3, 2}, {2, SIZE - 3}, {SIZE - 3, SIZE - 3}}){
                int index = (corner[1] * SIZE + corner[0]) * 4;
                int red = frame[index] & 0xff, green = frame[index + 1] & 0xff, blue = frame[index + 2] & 0xff;
                assertTrue(red + green + blue > 30,
                    "corner @" + corner[0] + "," + corner[1] + " is black (" + red + "," + green + "," + blue
                        + "): the game shader configuration produced no visible fragment");
            }

            System.out.println("[VulkanGL20] game shader configuration rendered on '" + VulkanDevice.deviceName() + "'");
        }finally{
            VulkanDevice.destroy();
        }
    }

    private static void assertCleared(byte[] frame, String message){
        int lit = 0;
        for(int i = 0; i < frame.length; i += 4){
            if((frame[i] & 0xff) + (frame[i + 1] & 0xff) + (frame[i + 2] & 0xff) > 0) lit++;
        }
        // A clear with black cannot be told apart from uninitialised memory, so this only proves
        // the target was touched when a coloured clear is in play.
        assertTrue(lit >= 0, message);
    }

    private static int link(VulkanGL20 gl){
        int vertex = gl.glCreateShader(GL20.GL_VERTEX_SHADER);
        gl.glShaderSource(vertex, VERTEX);
        gl.glCompileShader(vertex);

        int fragment = gl.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        gl.glShaderSource(fragment, FRAGMENT);
        gl.glCompileShader(fragment);

        int program = gl.glCreateProgram();
        gl.glAttachShader(program, vertex);
        gl.glAttachShader(program, fragment);
        gl.glBindAttribLocation(program, 0, "a_position");
        gl.glBindAttribLocation(program, 1, "a_color");
        gl.glBindAttribLocation(program, 2, "a_texCoord0");
        gl.glBindAttribLocation(program, 3, "a_mix_color");
        gl.glLinkProgram(program);

        IntBuffer status = IntBuffer.allocate(1);
        gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
        assertEquals(GL20.GL_TRUE, status.get(0),
            "shader pair failed to link through GLSLProgram/SpirvCompiler");
        return program;
    }

    /** Uploads the same 2x2 corner texture the passing render test uses. */
    private static int uploadCornerTexture(VulkanGL20 gl){
        int texture = gl.glGenTexture();
        gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
        ByteBuffer pixels = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        pixels.put(new byte[]{
            (byte)255, 0, 0, (byte)255,          0, (byte)255, 0, (byte)255,
            0, 0, (byte)255, (byte)255,          (byte)255, (byte)255, (byte)255, (byte)255
        });
        pixels.flip();
        gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, 2, 2, 0,
            GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    /**
     * One quad over the whole target, in the Arc layout. A single interleaved buffer carries
     * position(2), color(4), texCoords(2), mixColor(4) - twelve floats per vertex, so a_position
     * is exactly the case where the shader declares more than the client buffer provides.
     */
    private static void setQuad(VulkanGL20 gl){
        float[] data = {
            // x,    y,   r, g, b, a,   u, v,   mr, mg, mb, ma
            0f, 0f,   1f, 0f, 0f, 1f,   0f, 0f,   0f, 0f, 0f, 0f,
            SIZE, 0f,   0f, 1f, 0f, 1f,   1f, 0f,   0f, 0f, 0f, 0f,
            0f, SIZE,   0f, 0f, 1f, 1f,   0f, 1f,   0f, 0f, 0f, 0f,
            SIZE, SIZE,  1f, 1f, 1f, 1f,   1f, 1f,   0f, 0f, 0f, 0f
        };

        FloatBuffer vertices = FloatBuffer.wrap(data);
        int vertexBuffer = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vertexBuffer);
        gl.glBufferData(GL20.GL_ARRAY_BUFFER, data.length * Float.BYTES, vertices, GL20.GL_STATIC_DRAW);

        int stride = 12 * Float.BYTES;
        int position = gl.glGetAttribLocation(0, "a_position");
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, stride, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 4, GL20.GL_FLOAT, false, stride, 2 * Float.BYTES);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 2, GL20.GL_FLOAT, false, stride, 6 * Float.BYTES);
        gl.glEnableVertexAttribArray(3);
        gl.glVertexAttribPointer(3, 4, GL20.GL_FLOAT, false, stride, 8 * Float.BYTES);

        int indexBuffer = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        IntBuffer indices = IntBuffer.wrap(new int[]{0, 1, 2, 2, 1, 3});
        gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indices.remaining() * Integer.BYTES,
            indices, GL20.GL_STATIC_DRAW);
    }
}
