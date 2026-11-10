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
 * Narrows the windowed failure down: same device, same Arc layout, same three varyings - but no
 * uniform block.
 *
 * <p>The game's pair keeps its pixels on the offscreen path and loses them on the windowed one,
 * while a plain pair keeps them on both. The game's pair differs from the plain one in three ways:
 * four attributes instead of two, three varyings instead of one, and a {@code mat4} uniform in a
 * UBO. This test keeps the first two and drops the third, so whatever it reports points at one of
 * them.</p>
 */
class VulkanGL20WindowedAttribVaryingsTest{
    private static final int SIZE = 64;
    private static final String TITLE = "OrbitalVulkanAttribVaryingsTest";

    /** The game's varying set and its Arc layout, with the projection written as a constant. */
    private static final String VERTEX = """
        attribute vec4 a_position;
        attribute vec4 a_color;
        attribute vec2 a_texCoord0;
        attribute vec4 a_mix_color;
        varying vec4 v_color;
        varying vec4 v_mix_color;
        varying vec2 v_texCoords;

        void main(){
           v_color = a_color;
           v_mix_color = a_mix_color;
           v_texCoords = a_texCoord0;
           gl_Position = vec4(a_position.xy, 0.0, 1.0);
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
    void keepsPixelsWithFourAttributesThreeVaryingsAndNoUniform(){
        VulkanDevice.destroy();
        if(SDL.SDL_Init(SDL.SDL_INIT_VIDEO) != 0){
            System.out.println("[VulkanGL20] attribute/varying probe not exercised: SDL video unavailable");
            return;
        }

        long window = 0;
        try{
            window = SDL.SDL_CreateWindow(TITLE, SIZE, SIZE, 4);
            if(window == 0){
                System.out.println("[VulkanGL20] attribute/varying probe not exercised: " + SDL.SDL_GetError());
                return;
            }

            long handle = VulkanDevice.findWindow(TITLE);
            long instance = VulkanDevice.moduleHandle();
            if(handle == 0 || instance == 0
                    || !VulkanDevice.createWindowed(SIZE, SIZE, handle, instance)){
                System.out.println("[VulkanGL20] attribute/varying probe not exercised: "
                    + VulkanDevice.lastError());
                return;
            }

            VulkanGL20 gl = new VulkanGL20();
            int program = link(gl);

            gl.glBindTexture(GL20.GL_TEXTURE_2D, uploadCornerTexture(gl));
            setQuad(gl);
            gl.glUseProgram(program);

            // The game's batch enables alpha blending; the passing tests here do not, so the
            // windowed path has never been checked with a blending pipeline.
            gl.glEnable(GL20.GL_BLEND);
            gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            gl.glClearColor(0f, 0f, 0f, 1f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            gl.glDrawElements(GL20.GL_TRIANGLES, 6, GL20.GL_UNSIGNED_INT, 0);

            byte[] frame = VulkanDevice.frame();
            assertNotNull(frame, "readback failed: " + VulkanDevice.lastError());

            int lit = 0;
            for(int i = 0; i < frame.length; i += 4){
                if((frame[i] & 0xff) + (frame[i + 1] & 0xff) + (frame[i + 2] & 0xff) > 30) lit++;
            }

            System.out.println("[VulkanGL20] windowed, four attributes and three varyings, no UBO: "
                + lit + " lit pixels of " + (SIZE * SIZE));
            assertTrue(lit > 100, "only " + lit + " pixels survived: the windowed path dropped"
                + " fragments that the attribute and varying counts alone explain");
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

    private static void setQuad(VulkanGL20 gl){
        float[] data = {
            -1f, 1f,     1f, 0f, 0f, 1f,   0f, 0f,   0f, 0f, 0f, 0f,
            1f, 1f,      0f, 1f, 0f, 1f,   1f, 0f,   0f, 0f, 0f, 0f,
            -1f, -1f,    0f, 0f, 1f, 1f,   0f, 1f,   0f, 0f, 0f, 0f,
            1f, -1f,     1f, 1f, 1f, 1f,   1f, 1f,   0f, 0f, 0f, 0f
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
