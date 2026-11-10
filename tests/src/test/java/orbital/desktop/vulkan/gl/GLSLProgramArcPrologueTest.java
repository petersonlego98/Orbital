package orbital.desktop.vulkan.gl;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the shader pairs this bridge actually receives from Arc.
 *
 * <p>{@code arc.graphics.gl.Shader#preprocess} produces one of two shapes:</p>
 *
 * <ul>
 *   <li><b>GL2 path</b> ({@code Core.gl30 == null}): a {@code #ifdef GL_ES / precision ... /
 *       #else / #define lowp ... / #endif} block in front of the untouched GLSL ES 1.00 source,
 *       which still uses {@code attribute}/{@code varying}/{@code gl_FragColor}.</li>
 *   <li><b>GL3 path</b>: the same block plus a {@code #version 300 es} line, with
 *       {@code attribute} rewritten to {@code in} and {@code varying} to {@code out}/{@code in}.</li>
 * </ul>
 *
 * <p>Both must end up as a linker-compatible GLSL 450 pair. The GL2 shape is the one the game uses
 * today, because {@code VulkanApplication} leaves {@code Core.gl30} null; the GL3 shape is pinned
 * so the bridge keeps working the day that changes.</p>
 */
class GLSLProgramArcPrologueTest{
    private static final String GL2_PROLOGUE =
        "#ifdef GL_ES\n"
            + "precision highp float;\n"
            + "precision mediump int;\n"
            + "#else\n"
            + "#define lowp  \n"
            + "#define mediump \n"
            + "#define highp \n"
            + "#endif\n";

    /** The shorter variant Arc emits when the version line is added separately. */
    private static final String GL2_SHORT_PROLOGUE =
        "#ifndef GL_ES\n"
            + "#define lowp  \n"
            + "#define mediump \n"
            + "#define highp \n"
            + "#endif\n";

    private static final String GL2_BODY_VERTEX =
        "attribute vec4 a_position;\n"
            + "attribute vec4 a_color;\n"
            + "attribute vec2 a_texCoord0;\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "void main(){\n"
            + "    v_color = a_color;\n"
            + "    v_color.rgb *= v_color.a;\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = a_position;\n"
            + "}\n";

    private static final String GL2_BODY_FRAGMENT =
        "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "void main(){\n"
            + "    gl_FragColor = v_color * texture2D(u_texture, v_texCoords);\n"
            + "}\n";

    /** Arc's GL3 output for the identical source. */
    private static final String GL3_BODY_VERTEX =
        "in vec4 a_position;\n"
            + "in vec4 a_color;\n"
            + "in vec2 a_texCoord0;\n"
            + "out vec4 v_color;\n"
            + "out vec2 v_texCoords;\n"
            + "void main(){\n"
            + "    v_color = a_color;\n"
            + "    v_color.rgb *= v_color.a;\n"
            + "    v_texCoords = a_texCoord0;\n"
            + "    gl_Position = a_position;\n"
            + "}\n";

    private static final String GL3_BODY_FRAGMENT =
        "in vec4 v_color;\n"
            + "in vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "out vec4 fragColor;\n"
            + "void main(){\n"
            + "    fragColor = v_color * texture(u_texture, v_texCoords);\n"
            + "}\n";

    @Test
    void gl2ArcPairCompilesToSpirv(){
        assertCompiles(
            GL2_PROLOGUE + GL2_BODY_VERTEX,
            GL2_PROLOGUE + GL2_BODY_FRAGMENT,
            "GL2 full prologue");
    }

    @Test
    void gl2ShortProloguePairCompilesToSpirv(){
        assertCompiles(
            GL2_SHORT_PROLOGUE + GL2_BODY_VERTEX,
            GL2_SHORT_PROLOGUE + GL2_BODY_FRAGMENT,
            "GL2 short prologue");
    }

    @Test
    void gl3ArcPairCompilesToSpirv(){
        assertCompiles(
            "#version 300 es\n" + GL2_PROLOGUE + GL3_BODY_VERTEX,
            "#version 300 es\n" + GL2_PROLOGUE + GL3_BODY_FRAGMENT,
            "GL3 Arc rewrite");
    }

    @Test
    void noConditionalOrVersionLeaksIntoTheOutput(){
        GLSLProgram.Result result = GLSLProgram.translate(
            GL2_PROLOGUE + GL2_BODY_VERTEX, GL2_PROLOGUE + GL2_BODY_FRAGMENT);

        for(String source : new String[]{result.vertexSource, result.fragmentSource}){
            assertFalse(source.contains("#endif"), "dangling #endif must not survive:\n" + source);
            assertFalse(source.contains("#ifdef"), "Arc's #ifdef block must be gone:\n" + source);
            assertFalse(source.contains("#version 300"), "Arc's version line must be replaced:\n" + source);
            assertFalse(source.contains("#define lowp"), "empty precision macros must be dropped:\n" + source);
            assertFalse(source.contains("precision"), "precision qualifiers do not exist in 450:\n" + source);
            assertTrue(source.startsWith("#version 450\n"), "GLSL 450 is required:\n" + source);
            assertEquals(1, source.split("#version", -1).length - 1, "exactly one #version line:\n" + source);
        }
    }

    @Test
    void definesWithASizeSurvivesButEmptyDefinesDoNot(){
        String source = "#define LIGHTS 4\n#define lowp \nvarying vec4 v_color;\n"
            + "void main(){ v_color = vec4(1.0); gl_Position = vec4(0.0); }\n";
        GLSLProgram.Result result = GLSLProgram.translate(source, source);

        assertTrue(result.vertexSource.contains("#define LIGHTS 4"),
            "a #define with a value is needed for uniform array sizes:\n" + result.vertexSource);
        assertFalse(result.vertexSource.contains("lowp"),
            "an empty precision macro must be dropped:\n" + result.vertexSource);
    }

    @Test
    void aConditionalBlockThatDefinesSomethingUsefulIsKept(){
        // Same shape as Arc's wrapper, but the macro carries a definition, so dropping the block
        // would change the shader's meaning.
        String fragment = GL2_PROLOGUE.replace("#define lowp", "#define lowp") + ""
            + "#ifdef GL_ES\n"
            + "#define LOWP lowp\n"
            + "precision mediump float;\n"
            + "#else\n"
            + "#define LOWP\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "void main(){ gl_FragColor = v_color; }\n";

        GLSLProgram.Result result = GLSLProgram.translate(GL2_PROLOGUE + GL2_BODY_VERTEX, fragment);

        assertTrue(result.fragmentSource.contains("#ifdef GL_ES"),
            "a block that gives a macro a definition must survive:\n" + result.fragmentSource);
        assertTrue(result.fragmentSource.contains("#define LOWP lowp"),
            "the macro definition must survive:\n" + result.fragmentSource);
        assertFalse(result.fragmentSource.contains("precision"),
            "precision qualifiers still do not exist in 450:\n" + result.fragmentSource);
        assertFalse(result.fragmentSource.contains("#define lowp "),
            "Arc's own empty precision macros are still dropped:\n" + result.fragmentSource);
    }

    private static void assertCompiles(String vertex, String fragment, String what){
        try{
            byte[] vertexSpirv = SpirvCompiler.compile(vertexOf(vertex), SpirvCompiler.Stage.VERTEX);
            assertNotNull(vertexSpirv, what + ": vertex SPIR-V must be produced");
            assertNotNull(SpirvCompiler.compile(fragmentOf(fragment), SpirvCompiler.Stage.FRAGMENT),
                what + ": fragment SPIR-V must be produced");
        }catch(SpirvCompiler.CompileException error){
            fail(what + ": " + error.getMessage());
        }
    }

    private static String vertexOf(String vertex){
        return GLSLProgram.translate(vertex, GL2_PROLOGUE + GL2_BODY_FRAGMENT).vertexSource;
    }

    private static String fragmentOf(String fragment){
        return GLSLProgram.translate(GL2_PROLOGUE + GL2_BODY_VERTEX, fragment).fragmentSource;
    }
}
