package orbital.desktop.vulkan.gl;

import arc.graphics.GL20;
import orbital.desktop.vulkan.VulkanDevice;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end render test for the GL20-to-Vulkan bridge.
 *
 * <p>It drives the public {@link VulkanGL20} surface exactly like Arc does - compile/link, buffer
 * upload, vertex attribute pointers, element buffer, texture upload, uniform write, indexed draw -
 * and then reads the real offscreen frame back from the device. The visible result is verified
 * per corner: the shader multiplies a 2x2 texture by a {@code vec4} uniform, so a wrong UBO
 * offset, UV orientation, index buffer or vertex layout changes the sampled pixels.</p>
 *
 * <p>Without a Vulkan driver (for example no hardware ICD and no software ICD) the test logs the
 * reason and returns instead of failing: OpenGL is still the shipping backend.</p>
 */
class VulkanGL20RenderTest{
    private static final int SIZE = 64;

    private static final String VERTEX = """
        attribute vec2 a_position;
        attribute vec2 a_uv;
        varying vec2 v_uv;
        void main(){
            v_uv = a_uv;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT = """
        uniform vec4 u_tint;
        uniform sampler2D u_texture;
        varying vec2 v_uv;
        void main(){
            gl_FragColor = texture2D(u_texture, v_uv) * u_tint;
        }
        """;

    @Test
    void rendersTexturedIndexedQuadThroughGl20AndReadsBackPixels(){
        VulkanDevice.destroy();
        if(!VulkanDevice.create(SIZE, SIZE)){
            System.out.println("[VulkanGL20] render path not exercised: " + VulkanDevice.lastError());
            return;
        }

        try{
            VulkanGL20 gl = new VulkanGL20();

            int program = link(gl);

            // 2x2 RGBA texture: red, green / blue, white, uploaded through glTexImage2D.
            int texture = uploadCornerTexture(gl);
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);

            // Interleaved vertex buffer: position (2 floats) + uv (2 floats), stride 16 bytes.
            int vertexBuffer = gl.glGenBuffer();
            gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vertexBuffer);
            gl.glBufferData(GL20.GL_ARRAY_BUFFER, quadVertices().remaining() * Float.BYTES,
                quadVertices(), GL20.GL_STATIC_DRAW);

            gl.glEnableVertexAttribArray(0);
            gl.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 0);
            gl.glEnableVertexAttribArray(1);
            gl.glVertexAttribPointer(1, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

            // Element buffer: two triangles, uploaded and drawn through the offset overload.
            int indexBuffer = gl.glGenBuffer();
            gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            IntBuffer indices = IntBuffer.wrap(new int[]{0, 1, 2, 2, 1, 3});
            gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indices.remaining() * Integer.BYTES,
                indices, GL20.GL_STATIC_DRAW);

            gl.glUseProgram(program);
            int tint = gl.glGetUniformLocation(program, "u_tint");
            assertTrue(tint > 0, "u_tint must resolve to a uniform location");
            gl.glUniform4f(tint, 1f, 1f, 1f, 1f);

            gl.glClearColor(0f, 0f, 0f, 1f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            gl.glDrawElements(GL20.GL_TRIANGLES, 6, GL20.GL_UNSIGNED_INT, 0);

            byte[] frame = VulkanDevice.frame();
            assertNotNull(frame, "readback failed: " + VulkanDevice.lastError());
            assertEquals(SIZE * SIZE * 4, frame.length);

            // With a white tint the sampled texture must show each 2x2 texel in its corner.
            assertPixel(frame, 2, 2, 255, 0, 0, 255, "top-left");
            assertPixel(frame, SIZE - 3, 2, 0, 255, 0, 255, "top-right");
            assertPixel(frame, 2, SIZE - 3, 0, 0, 255, 255, "bottom-left");
            assertPixel(frame, SIZE - 3, SIZE - 3, 255, 255, 255, 255, "bottom-right");

            // A tint that zeroes red proves the UBO is really consumed: red becomes black.
            gl.glUniform4f(tint, 0f, 1f, 1f, 1f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            gl.glDrawElements(GL20.GL_TRIANGLES, 6, GL20.GL_UNSIGNED_INT, 0);
            frame = VulkanDevice.frame();
            assertNotNull(frame, "readback failed: " + VulkanDevice.lastError());
            assertPixel(frame, 2, 2, 0, 0, 0, 255, "tinted top-left");
            assertPixel(frame, SIZE - 3, 2, 0, 255, 0, 255, "tinted top-right");

            System.out.println("[VulkanGL20] textured indexed quad rendered on '" + VulkanDevice.deviceName() + "'");
        }finally{
            VulkanDevice.destroy();
        }
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
        gl.glBindAttribLocation(program, 1, "a_uv");
        gl.glLinkProgram(program);

        IntBuffer status = IntBuffer.allocate(1);
        gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
        assertEquals(GL20.GL_TRUE, status.get(0), "shader pair failed to link through GLSLProgram/SpirvCompiler");
        return program;
    }

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
     * Full-screen quad. Readback row 0 is the top of the framebuffer, so the top vertices carry
     * UV (0,0)/(1,0) and the bottom vertices UV (0,1)/(1,1).
     */
    private static FloatBuffer quadVertices(){
        float[] data = {
            // x, y, u, v
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f,
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f
        };
        return FloatBuffer.wrap(data);
    }

    private static void assertPixel(byte[] frame, int x, int y, int r, int g, int b, int a, String what){
        int index = (y * SIZE + x) * 4;
        int actualR = frame[index] & 0xff;
        int actualG = frame[index + 1] & 0xff;
        int actualB = frame[index + 2] & 0xff;
        int actualA = frame[index + 3] & 0xff;
        String at = what + " @" + x + "," + y + " expected (" + r + "," + g + "," + b + "," + a
            + ") but was (" + actualR + "," + actualG + "," + actualB + "," + actualA + ")";
        assertClose(r, actualR, at);
        assertClose(g, actualG, at);
        assertClose(b, actualB, at);
        assertClose(a, actualA, at);
    }

    private static void assertClose(int expected, int actual, String message){
        if(Math.abs(expected - actual) > 2){
            fail(message);
        }
    }
}
