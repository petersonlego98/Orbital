package orbital.desktop.vulkan.gl;

import arc.backend.sdl.jni.SDL;
import arc.graphics.GL20;
import orbital.desktop.vulkan.VulkanDevice;
import org.junit.jupiter.api.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders the <b>game's</b> shader configuration into a <b>windowed</b> device.
 *
 * <p>Two other suites cover half of this each: {@link GLSLProgramUniformProjectionTest} runs
 * Arc's {@code SpriteBatch} pair with a {@code u_projTrans} UBO on the offscreen path, and
 * {@link VulkanGL20RenderTest} runs a simple pair on no path in particular. This one runs the
 * game's pair on the windowed path, which is what ships - the swapchain exists, the render target
 * is built after it, and the frame leaves through a blit.</p>
 *
 * <p>Readback covers all four corners. A black frame here means the windowed path loses fragments
 * that the offscreen path keeps, which is exactly the failure the game shows.</p>
 */
class VulkanGL20WindowedGameShaderTest{
    private static final int SIZE = 64;
    private static final String TITLE = "OrbitalVulkanGameShaderTest";

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
    void rendersTheGameShaderIntoAWindowedDevice(){
        VulkanDevice.destroy();
        if(SDL.SDL_Init(SDL.SDL_INIT_VIDEO) != 0){
            System.out.println("[VulkanGL20] windowed game-shader path not exercised: SDL video unavailable");
            return;
        }

        long window = 0;
        try{
            window = SDL.SDL_CreateWindow(TITLE, SIZE, SIZE, 4);
            if(window == 0){
                System.out.println("[VulkanGL20] windowed game-shader path not exercised: " + SDL.SDL_GetError());
                return;
            }

            long handle = VulkanDevice.findWindow(TITLE);
            long instance = VulkanDevice.moduleHandle();
            if(handle == 0 || instance == 0){
                System.out.println("[VulkanGL20] windowed game-shader path not exercised: no window handle");
                return;
            }
            if(!VulkanDevice.createWindowed(SIZE, SIZE, handle, instance)){
                System.out.println("[VulkanGL20] windowed game-shader path not exercised: " + VulkanDevice.lastError());
                return;
            }
            assertTrue(VulkanDevice.isWindowed(), "the device must be windowed");

            // Draw the simple pair first: if this one keeps its pixels on the very same device,
            // the loss is specific to what the game's shader asks for, not to the windowed path.
            VulkanGL20 probe = new VulkanGL20();
            drawSimpleQuad(probe, SIZE);
            byte[] simple = VulkanDevice.frame();
            assertNotNull(simple, "readback after the simple draw failed: " + VulkanDevice.lastError());
            for(int[] corner : new int[][]{{2, 2}, {SIZE - 3, SIZE - 3}}){
                int index = (corner[1] * SIZE + corner[0]) * 4;
                int sum = (simple[index] & 0xff) + (simple[index + 1] & 0xff) + (simple[index + 2] & 0xff);
                assertTrue(sum > 30, "simple quad corner @" + corner[0] + "," + corner[1]
                    + " is black on the windowed device: the render path itself lost fragments");
            }

            VulkanGL20 gl = new VulkanGL20();
            int program = link(gl);

            gl.glBindTexture(GL20.GL_TEXTURE_2D, uploadCornerTexture(gl));
            setQuad(gl);

            gl.glUseProgram(program);
            int projection = gl.glGetUniformLocation(program, "u_projTrans");
            assertTrue(projection > 0, "u_projTrans must resolve to a uniform location");
            // The quad is in screen space, so it needs the same orthographic projection the game
            // applies; an identity matrix would leave every vertex outside the clip volume.
            gl.glUniformMatrix4fv(projection, 1, false, orthographic(), 0);

            gl.glClearColor(0f, 0f, 0f, 1f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

            byte[] frame = VulkanDevice.frame();
            if(frame == null){
                fail("readback on the windowed device failed: " + VulkanDevice.lastError());
            }
            assertEquals(SIZE * SIZE * 4, frame.length);

            gl.glDrawElements(GL20.GL_TRIANGLES, 6, GL20.GL_UNSIGNED_INT, 0);

            frame = VulkanDevice.frame();
            if(frame == null){
                fail("readback after the draw failed: " + VulkanDevice.lastError());
            }

            // The present blits this very target into the swapchain. Reading it back afterwards
            // separates the two halves of the windowed path: the render writes the pixels, and
            // only the blit could take them away.
            assertTrue(VulkanDevice.presentFrame(), "present failed: " + VulkanDevice.lastError());

            byte[] after = VulkanDevice.frame();
            assertNotNull(after, "readback after the present failed: " + VulkanDevice.lastError());
            assertArrayEquals(frame, after, "the present changed the rendered target: the blit"
                + " rewrote the pixels the render pass produced");

            for(int[] corner : new int[][]{{2, 2}, {SIZE - 3, 2}, {2, SIZE - 3}, {SIZE - 3, SIZE - 3}}){
                int index = (corner[1] * SIZE + corner[0]) * 4;
                int sum = (frame[index] & 0xff) + (frame[index + 1] & 0xff) + (frame[index + 2] & 0xff);
                assertTrue(sum > 30, "corner @" + corner[0] + "," + corner[1] + " is black ("
                    + (frame[index] & 0xff) + "," + (frame[index + 1] & 0xff) + ","
                    + (frame[index + 2] & 0xff) + "): the windowed device kept no fragments");
            }

            System.out.println("[VulkanGL20] game shader into a windowed device on '"
                + VulkanDevice.deviceName() + "'");
        }finally{
            VulkanDevice.destroy();
            if(window != 0) SDL.SDL_DestroyWindow(window);
            SDL.SDL_Quit();
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
        gl.glBindAttribLocation(program, 1, "a_color");
        gl.glBindAttribLocation(program, 2, "a_texCoord0");
        gl.glBindAttribLocation(program, 3, "a_mix_color");
        gl.glLinkProgram(program);

        IntBuffer status = IntBuffer.allocate(1);
        gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
        assertEquals(GL20.GL_TRUE, status.get(0), "shader pair failed to link");
        return program;
    }

    /** A plain full-screen quad with a white tint and no uniform, as a control case. */
    private static void drawSimpleQuad(VulkanGL20 gl, int size){
        String vertex = """
            attribute vec2 a_position;
            attribute vec2 a_uv;
            varying vec2 v_uv;
            void main(){
                v_uv = a_uv;
                gl_Position = vec4(a_position, 0.0, 1.0);
            }
            """;
        String fragment = """
            uniform vec4 u_tint;
            uniform sampler2D u_texture;
            varying vec2 v_uv;
            void main(){
                gl_FragColor = texture2D(u_texture, v_uv) * u_tint;
            }
            """;

        int v = gl.glCreateShader(GL20.GL_VERTEX_SHADER);
        gl.glShaderSource(v, vertex);
        gl.glCompileShader(v);
        int f = gl.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        gl.glShaderSource(f, fragment);
        gl.glCompileShader(f);
        int program = gl.glCreateProgram();
        gl.glAttachShader(program, v);
        gl.glAttachShader(program, f);
        gl.glBindAttribLocation(program, 0, "a_position");
        gl.glBindAttribLocation(program, 1, "a_uv");
        gl.glLinkProgram(program);

        gl.glBindTexture(GL20.GL_TEXTURE_2D, uploadCornerTexture(gl));

        FloatBuffer vertices = FloatBuffer.wrap(new float[]{
            -1f, 1f, 0f, 0f,   1f, 1f, 1f, 0f,   -1f, -1f, 0f, 1f,   1f, -1f, 1f, 1f
        });
        int vb = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vb);
        gl.glBufferData(GL20.GL_ARRAY_BUFFER, 16 * Float.BYTES, vertices, GL20.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 2, GL20.GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        int ib = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, ib);
        IntBuffer indices = IntBuffer.wrap(new int[]{0, 1, 2, 2, 1, 3});
        gl.glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, indices.remaining() * Integer.BYTES,
            indices, GL20.GL_STATIC_DRAW);

        gl.glUseProgram(program);
        int tint = gl.glGetUniformLocation(program, "u_tint");
        gl.glUniform4f(tint, 1f, 1f, 1f, 1f);

        gl.glClearColor(0f, 0f, 0f, 1f);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        gl.glDrawElements(GL20.GL_TRIANGLES, 6, GL20.GL_UNSIGNED_INT, 0);
    }

    private static float[] identity(){
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    /** Column-major orthographic projection over the whole target, matching the game's. */
    private static float[] orthographic(){
        return new float[]{
            2f / SIZE, 0, 0, 0,
            0, -2f / SIZE, 0, 0,
            0, 0, -1f, 0,
            -1f, 1f, 0f, 1f
        };
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

    /** The Arc layout: position(2), color(4), texCoords(2), mixColor(4); position is widened. */
    private static void setQuad(VulkanGL20 gl){
        float[] data = {
            0f, 0f,     1f, 0f, 0f, 1f,   0f, 0f,   0f, 0f, 0f, 0f,
            SIZE, 0f,   0f, 1f, 0f, 1f,   1f, 0f,   0f, 0f, 0f, 0f,
            0f, SIZE,   0f, 0f, 1f, 1f,   0f, 1f,   0f, 0f, 0f, 0f,
            SIZE, SIZE, 1f, 1f, 1f, 1f,   1f, 1f,   0f, 0f, 0f, 0f
        };

        int vertexBuffer = gl.glGenBuffer();
        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vertexBuffer);
        gl.glBufferData(GL20.GL_ARRAY_BUFFER, data.length * Float.BYTES, FloatBuffer.wrap(data),
            GL20.GL_STATIC_DRAW);

        int stride = 12 * Float.BYTES;
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
