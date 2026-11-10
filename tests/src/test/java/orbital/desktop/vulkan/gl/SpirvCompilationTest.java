package orbital.desktop.vulkan.gl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Compiles the translated GLSL 450 with shaderc targeting Vulkan 1.0. */
class SpirvCompilationTest{
    private static final String SIMPLE_VERTEX = "void main(){ gl_Position = vec4(0.0); }";
    private static final String SIMPLE_FRAGMENT = "void main(){}";

    @Test
    void nativeCompilerIsAvailable(){
        assertTrue(SpirvCompiler.available(),
            "shaderc native library could not be loaded: " + SpirvCompiler.unavailableCause());
    }

    @Test
    void compilesMinimalVulkanShaderPair() throws Exception{
        GLSLProgram.Result result = GLSLProgram.translate("""
            attribute vec4 a_position;
            varying vec2 v_uv;
            uniform mat4 u_proj;
            void main(){
                v_uv = a_position.xy;
                gl_Position = u_proj * a_position;
            }
            """, """
            varying vec2 v_uv;
            uniform sampler2D u_texture;
            void main(){
                gl_FragColor = texture2D(u_texture, v_uv);
            }
            """);

        assertTrue(SpirvCompiler.compile(result.vertexSource, SpirvCompiler.Stage.VERTEX).length > 0);
        assertTrue(SpirvCompiler.compile(result.fragmentSource, SpirvCompiler.Stage.FRAGMENT).length > 0);
    }

    @Test
    void compilesEveryProjectShaderPair() throws Exception{
        assertTrue(SpirvCompiler.available(),
            "shaderc native library could not be loaded: " + SpirvCompiler.unavailableCause());

        Path dir = Path.of("shaders");
        List<Path> files;
        try(Stream<Path> stream = Files.list(dir)){
            files = stream.filter(p -> p.toString().endsWith(".vert") || p.toString().endsWith(".frag")).sorted().toList();
        }

        Map<String, Path> vertexByBase = new HashMap<>();
        for(Path path : files){
            String name = path.getFileName().toString();
            if(name.endsWith(".vert")){
                vertexByBase.put(name.substring(0, name.length() - 5), path);
            }
        }

        List<String> failures = new ArrayList<>();
        int compiled = 0;
        for(Path path : files){
            String name = path.getFileName().toString();
            boolean vertex = name.endsWith(".vert");
            String source = Files.readString(path);
            String companion;
            if(vertex){
                companion = SIMPLE_FRAGMENT;
            }else{
                Path paired = vertexByBase.get(name.substring(0, name.length() - 5));
                companion = paired != null && Files.exists(paired)
                    ? Files.readString(paired) : Files.readString(dir.resolve("screenspace.vert"));
            }

            GLSLProgram.Result result = vertex
                ? GLSLProgram.translate(source, companion)
                : GLSLProgram.translate(companion, source);

            compiled += check(path.getFileName() + " [vertex]", result.vertexSource, SpirvCompiler.Stage.VERTEX, failures);
            compiled += check(path.getFileName() + " [fragment]", result.fragmentSource, SpirvCompiler.Stage.FRAGMENT, failures);
        }

        assertTrue(failures.isEmpty(),
            "SPIR-V compilation failures (" + failures.size() + "):\n" + String.join("\n", failures));
        assertEquals(files.size() * 2, compiled,
            "both stages must compile for every shader file");
        System.out.println("[SPIR-V] " + files.size() + " shader files -> " + compiled + " compiled modules (Vulkan 1.0)");
    }

    private static int check(String label, String source, SpirvCompiler.Stage stage, List<String> failures){
        try{
            byte[] spirv = SpirvCompiler.compile(source, stage);
            if(spirv.length == 0){
                failures.add(label + ": empty SPIR-V");
                return 0;
            }
            return 1;
        }catch(SpirvCompiler.CompileException e){
            failures.add(label + ": " + e.getMessage() + "\n" + indent(e.log) + "\n--- source ---\n" + numbered(source));
            return 0;
        }
    }

    private static String indent(String text){
        if(text == null || text.isBlank()) return "  (no compiler log)";
        return "  " + text.strip().replace("\n", "\n  ");
    }

    private static String numbered(String source){
        String[] lines = source.split("\\r?\\n", -1);
        StringBuilder out = new StringBuilder();
        for(int i = 0; i < lines.length; i++){
            out.append(String.format("%4d | %s%n", i + 1, lines[i]));
        }
        return out.toString();
    }
}
