package orbital.desktop.vulkan.gl;

import org.lwjgl.util.shaderc.Shaderc;

import java.nio.ByteBuffer;

/**
 * Compiles Vulkan-compatible GLSL 450 into SPIR-V using shaderc.
 *
 * <p>The native shaderc library is optional: {@link #available()} reports whether it could be
 * loaded, and the caller must fall back rather than assume success. Compilation targets Vulkan 1.0
 * explicitly, which makes glslang enforce Vulkan rules (explicit locations, explicit bindings,
 * no default-block uniforms) instead of silently accepting OpenGL-style shaders.</p>
 */
public final class SpirvCompiler{
    public enum Stage{
        VERTEX, FRAGMENT
    }

    public static final class CompileException extends Exception{
        public final String log;

        CompileException(String message, String log){
            super(message);
            this.log = log;
        }
    }

    private static final class Native{
        static final Throwable failure;
        static final boolean ready;

        static{
            Throwable error = null;
            boolean ok = false;
            try{
                long compiler = Shaderc.shaderc_compiler_initialize();
                if(compiler != 0){
                    Shaderc.shaderc_compiler_release(compiler);
                    ok = true;
                }
            }catch(Throwable t){
                error = t;
            }
            failure = error;
            ready = ok;
        }
    }

    private SpirvCompiler(){
    }

    public static boolean available(){
        return Native.ready;
    }

    /** Failure reason when the native compiler is unavailable, or null. */
    public static Throwable unavailableCause(){
        return Native.failure;
    }

    public static byte[] compile(String source, Stage stage) throws CompileException{
        if(!available()){
            throw new CompileException("shaderc native library unavailable",
                String.valueOf(unavailableCause()));
        }

        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        long result = 0;
        try{
            Shaderc.shaderc_compile_options_set_source_language(options, Shaderc.shaderc_source_language_glsl);
            Shaderc.shaderc_compile_options_set_target_env(options,
                Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_0);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, false);

            int kind = stage == Stage.VERTEX
                ? Shaderc.shaderc_glsl_vertex_shader
                : Shaderc.shaderc_glsl_fragment_shader;

            result = Shaderc.shaderc_compile_into_spv(compiler, source, kind,
                stage == Stage.VERTEX ? "shader.vert" : "shader.frag", "main", options);
            if(result == 0){
                throw new CompileException("shaderc returned no result", "");
            }

            String log = Shaderc.shaderc_result_get_error_message(result);
            int status = Shaderc.shaderc_result_get_compilation_status(result);
            if(status != Shaderc.shaderc_compilation_status_success){
                //the shaderc message is the only description of what is wrong with the translated
                //shader, and a silent "status 2" is useless in a bug report
                String report = "[SPIR-V] " + stage + " shader failed (status " + status + "):\n"
                    + log + "\n--- source ---\n" + source;
                System.out.println(report);
                System.err.println(report);
                throw new CompileException("shaderc status " + status + ": " + log, log);
            }

            ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
            if(bytes == null || bytes.remaining() == 0 || bytes.remaining() % 4 != 0){
                throw new CompileException("invalid SPIR-V output", log);
            }
            byte[] spirv = new byte[bytes.remaining()];
            bytes.get(spirv);

            // SPIR-V magic number: 0x07230203, little-endian.
            int magic = (spirv[0] & 0xff) | ((spirv[1] & 0xff) << 8)
                | ((spirv[2] & 0xff) << 16) | ((spirv[3] & 0xff) << 24);
            if(magic != 0x07230203){
                throw new CompileException("missing SPIR-V magic number", log);
            }
            return spirv;
        }finally{
            if(result != 0) Shaderc.shaderc_result_release(result);
            if(options != 0) Shaderc.shaderc_compile_options_release(options);
            if(compiler != 0) Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
