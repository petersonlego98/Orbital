package orbital.desktop.vulkan.gl;

import arc.graphics.GL20;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VulkanGlPortTest{
    private static final String SIMPLE_VERTEX = "void main(){ gl_Position = vec4(0.0); }";
    private static final String SIMPLE_FRAGMENT = "void main(){}";

    @Test
    void translatesDefaultShaderPair() throws IOException{
        GLSLProgram.Result result = GLSLProgram.translate(
            Files.readString(Path.of("shaders/default.vert")),
            Files.readString(Path.of("shaders/default.frag")));

        assertTrue(result.vertexSource.startsWith("#version 450\n"));
        assertTrue(result.fragmentSource.startsWith("#version 450\n"));

        for(String source : List.of(result.vertexSource, result.fragmentSource)){
            assertFalse(source.matches("(?s).*\\b(attribute|varying)\\b.*"), "legacy declaration remains");
            assertFalse(source.contains("texture2D("), "texture2D remains");
            assertFalse(source.contains("gl_FragColor"), "gl_FragColor remains");
        }

        assertTrue(result.vertexSource.contains("layout(location = 0) in vec4 a_position;"));
        assertTrue(result.vertexSource.contains("layout(location = 0) out vec4 v_color;"));
        assertTrue(result.fragmentSource.contains("layout(location = 2) in vec4 v_mix_color;"));
        assertTrue(result.fragmentSource.contains("layout(location = 0) out vec4 orbital_fragColor;"));
        assertTrue(result.fragmentSource.contains("orbital_fragColor = "));
        assertTrue(result.fragmentSource.contains("texture(u_texture, v_texCoords)"));
        assertTrue(result.fragmentSource.contains("layout(set = 0, binding = 1) uniform sampler2D u_texture;"));

        assertEquals(0, result.uniformLayout.offsetOf("u_projTrans"));
        assertEquals(64, result.uniformLayout.offsetOf("u_viewportInverse"));
        assertEquals(80, result.uniformLayout.size());
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("framebuffer origin")));
    }

    @Test
    void varyingLocationsAreSharedBetweenStages() throws IOException{
        GLSLProgram.Result result = GLSLProgram.translate(
            Files.readString(Path.of("shaders/default.vert")),
            Files.readString(Path.of("shaders/default.frag")));

        assertEquals(0, result.varyingLocations.get("v_color"));
        assertEquals(1, result.varyingLocations.get("v_texCoords"));
        assertEquals(2, result.varyingLocations.get("v_mix_color"));
        assertTrue(result.warnings.stream().anyMatch(w -> w.contains("fragment varying has no vertex output")));

        assertTrue(result.vertexSource.contains("layout(location = 1) out vec2 v_texCoords;"));
        assertTrue(result.fragmentSource.contains("layout(location = 1) in vec2 v_texCoords;"));
    }

    @Test
    void uniformBlockIsIdenticalInBothStages() throws IOException{
        GLSLProgram.Result result = GLSLProgram.translate(
            Files.readString(Path.of("shaders/default.vert")),
            Files.readString(Path.of("shaders/default.frag")));

        assertEquals(uboBlock(result.vertexSource), uboBlock(result.fragmentSource));
    }

    @Test
    void translatesEveryProjectShader() throws IOException{
        List<Path> shaders;
        try(Stream<Path> stream = Files.list(Path.of("shaders"))){
            shaders = stream.filter(p -> p.toString().endsWith(".vert") || p.toString().endsWith(".frag")).sorted().toList();
        }
        assertTrue(shaders.size() >= 30, "expected the real shader set, got " + shaders.size());

        for(Path shader : shaders){
            String source = Files.readString(shader);
            boolean isVertex = shader.toString().endsWith(".vert");
            GLSLProgram.Result result = isVertex
                ? GLSLProgram.translate(source, SIMPLE_FRAGMENT)
                : GLSLProgram.translate(SIMPLE_VERTEX, source);

            for(String translated : List.of(result.vertexSource, result.fragmentSource)){
                assertTrue(translated.startsWith("#version 450\n"), shader + " missing version");
                assertFalse(translated.matches("(?s).*\\b(attribute|varying)\\b.*"), shader + " legacy declaration remains");
                assertFalse(translated.contains("texture2D("), shader + " texture2D remains");
                assertFalse(translated.contains("gl_FragColor"), shader + " gl_FragColor remains");
                assertFalse(translated.contains("precision "), shader + " precision qualifier remains");
            }
            assertFalse(result.warnings.stream().anyMatch(w -> w.contains("unsupported uniform type")), shader + " unsupported uniform");
            assertFalse(result.warnings.stream().anyMatch(w -> w.contains("unparsable")), shader + " unparsable array");
        }
    }

    @Test
    void resolvesMacroArraySizesWithStd140Layout() throws IOException{
        GLSLProgram.Result result = GLSLProgram.translate(SIMPLE_VERTEX,
            Files.readString(Path.of("shaders/shockwave.frag")));

        assertEquals(0, result.uniformLayout.offsetOf("u_resolution"));
        assertEquals(8, result.uniformLayout.offsetOf("u_campos"));
        assertEquals(16, result.uniformLayout.offsetOf("u_shockwaves"));
        assertEquals(1024, result.uniformLayout.member("u_shockwaves").size);
        assertEquals(64, result.uniformLayout.member("u_shockwaves").arrayLength);
        assertEquals(1040, result.uniformLayout.offsetOf("u_shockwave_count"));
        assertEquals(1056, result.uniformLayout.size());
        assertTrue(result.fragmentSource.contains("vec4 u_shockwaves[64];"));
        assertTrue(result.vertexSource.contains("vec4 u_shockwaves[64];"));
        assertTrue(result.fragmentSource.contains("#define MAX_SHOCKWAVES 64"));
    }

    @Test
    void uniformBufferWritesAtComputedOffsets() throws IOException{
        GLSLProgram.Result result = GLSLProgram.translate(
            Files.readString(Path.of("shaders/default.vert")), SIMPLE_FRAGMENT);
        ByteBuffer buffer = result.uniformLayout.createBuffer();

        result.uniformLayout.putVec2(buffer, "u_viewportInverse", 0.5f, 0.25f);
        assertEquals(0.5f, buffer.getFloat(64));
        assertEquals(0.25f, buffer.getFloat(68));
    }

    @Test
    void stripsPrecisionAndPreservesConditionalPreprocessor(){
        String fragment = """
            #ifdef GL_ES
            #define LOWP lowp
            precision mediump float;
            #else
            #define LOWP
            #endif
            varying lowp vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            void main(){
                gl_FragColor = v_color * texture2D(u_texture, v_texCoords);
            }
            """;

        GLSLProgram.Result result = GLSLProgram.translate(SIMPLE_VERTEX, fragment);

        assertFalse(result.fragmentSource.contains("precision "));
        assertFalse(result.fragmentSource.contains("gl_FragColor"));
        assertFalse(result.fragmentSource.contains("texture2D("));
        assertTrue(result.fragmentSource.contains("#ifdef GL_ES"));
        assertTrue(result.fragmentSource.contains("texture(u_texture, v_texCoords)"));
        assertTrue(result.fragmentSource.contains("layout(location = 0) in vec4 v_color;"));
    }

    @Test
    void pipelineNeutralCapabilitiesDoNotInvalidatePipelines(){
        VulkanGLState state = new VulkanGLState();
        VulkanPipelineState before = state.snapshot();
        state.enable(GL20.GL_DITHER);
        state.disable(GL20.GL_DITHER);
        VulkanPipelineState after = state.snapshot();
        assertEquals(before, after);
        assertEquals(before.hashCode(), after.hashCode());
    }

    @Test
    void pipelineStateTracksDepthAndColorMask(){
        VulkanGLState state = new VulkanGLState();
        state.enable(GL20.GL_DEPTH_TEST);
        state.depthMask(false);
        state.colorMask(false, true, false, true);
        VulkanPipelineState snapshot = state.snapshot();
        assertTrue(snapshot.depthTest);
        assertFalse(snapshot.depthWrite);
        assertFalse(snapshot.colorMaskR);
        assertTrue(snapshot.colorMaskG);
        assertFalse(snapshot.colorMaskB);
        assertTrue(snapshot.colorMaskA);
    }

    private static String uboBlock(String source){
        int start = source.indexOf("uniform OrbitalUniforms");
        int end = source.indexOf("};", start);
        return source.substring(start, end + 2);
    }
}
