package orbital.desktop.vulkan.gl;

import arc.graphics.GL20;
import org.junit.jupiter.api.Test;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class VulkanGL20Test{
    private static final String VERTEX = """
        attribute vec2 a_position;
        attribute vec4 a_color;
        varying vec4 v_color;
        void main(){
            v_color = a_color;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT = """
        varying vec4 v_color;
        void main(){
            gl_FragColor = v_color;
        }
        """;

    @Test
    void tracksClearColorAndStateTransitions(){
        VulkanGL20 gl = new VulkanGL20();
        gl.glClearColor(0.2f, 0.4f, 0.6f, 1.0f);
        gl.glEnable(GL20.GL_BLEND);
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);

        VulkanPipelineState snapshot = gl.getState().snapshot();
        assertTrue(snapshot.blendEnabled);
        assertFalse(snapshot.depthWrite);
        assertEquals(GL20.GL_SRC_ALPHA, snapshot.blendSrcRGB);
        assertEquals(GL20.GL_ONE_MINUS_SRC_ALPHA, snapshot.blendDstRGB);
    }

    @Test
    void compilesAndLinksProgramViaGlslAndSpirv(){
        VulkanGL20 gl = new VulkanGL20();

        int vert = gl.glCreateShader(GL20.GL_VERTEX_SHADER);
        gl.glShaderSource(vert, VERTEX);
        gl.glCompileShader(vert);

        IntBuffer status = IntBuffer.allocate(1);
        gl.glGetShaderiv(vert, GL20.GL_COMPILE_STATUS, status);
        assertEquals(GL20.GL_TRUE, status.get(0));

        int frag = gl.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        gl.glShaderSource(frag, FRAGMENT);
        gl.glCompileShader(frag);

        int prog = gl.glCreateProgram();
        gl.glAttachShader(prog, vert);
        gl.glAttachShader(prog, frag);
        gl.glLinkProgram(prog);

        gl.glGetProgramiv(prog, GL20.GL_LINK_STATUS, status);
        assertEquals(GL20.GL_TRUE, status.get(0), "Program linking should succeed via SpirvCompiler");

        gl.glUseProgram(prog);
        int loc = gl.glGetUniformLocation(prog, "u_custom");
        assertTrue(loc > 0, "Uniform locations should be assigned positive IDs");
    }

    @Test
    void maintainsBufferAndTextureHandles(){
        VulkanGL20 gl = new VulkanGL20();
        int buf1 = gl.glGenBuffer();
        int buf2 = gl.glGenBuffer();
        assertNotEquals(buf1, buf2);

        int tex1 = gl.glGenTexture();
        int tex2 = gl.glGenTexture();
        assertNotEquals(tex1, tex2);

        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, buf1);
        gl.glBindTexture(GL20.GL_TEXTURE_2D, tex1);
    }

    @Test
    void honorsExplicitAttributeLocationsFromBindAttribLocation(){
        GLSLProgram.Result result = GLSLProgram.translate(VERTEX, FRAGMENT, java.util.Map.of(
            "a_position", 2,
            "a_color", 5));

        assertEquals(2, result.attributeLocations.get("a_position"));
        assertEquals(5, result.attributeLocations.get("a_color"));
        assertTrue(result.vertexSource.contains("layout(location = 2) in vec2 a_position;"));
        assertTrue(result.vertexSource.contains("layout(location = 5) in vec4 a_color;"));
    }
}
