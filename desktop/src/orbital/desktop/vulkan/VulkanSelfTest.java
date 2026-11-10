package orbital.desktop.vulkan;

import arc.util.*;

import orbital.desktop.vulkan.gl.*;

/**
 * Offscreen Vulkan self-check, runnable from the shipped build with {@code -vulkan-check}.
 *
 * <p>It exercises the complete native core end to end - device creation, SPIR-V compilation of a
 * minimal shader pair, pipeline creation, a color clear, an opaque draw and a blended draw - and
 * reports the sampled pixels. That makes the result verifiable without a development environment:
 * the report either lists the exact RGBA values read back from the device or states precisely which
 * step failed and why.</p>
 *
 * <p>Nothing here touches the game window: OpenGL remains the renderer of the running game
 * ({@link VulkanBackend}), this is a diagnostic of the Vulkan port only.</p>
 */
public final class VulkanSelfTest{
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

    private static final int GL_TRIANGLES = 4;

    private VulkanSelfTest(){
    }

    /** Runs the check and returns a multi-line report, never throwing. */
    public static String report(int size){
        StringBuilder out = new StringBuilder();
        try{
            run(size, out);
        }catch(Throwable error){
            out.append("  self-check aborted: ").append(error.getClass().getSimpleName())
                .append(": ").append(error.getMessage());
        }
        return out.toString();
    }

    private static void run(int size, StringBuilder out) throws Exception{
        out.append("[Vulkan] coreReady=").append(VulkanBackend.coreReady())
            .append(" rendererReady=").append(VulkanBackend.rendererReady()).append('\n');

        if(!VulkanLibrary.loaded()){
            out.append("  native library unavailable: ").append(VulkanLibrary.failureReason()).append('\n');
            return;
        }

        out.append("  driver: ").append(VulkanSupport.isAvailable()
            ? VulkanSupport.deviceInfo() : "no Vulkan driver registered").append('\n');

        if(!VulkanDevice.create(size, size)){
            out.append("  create failed: ").append(VulkanDevice.lastError()).append('\n');
            return;
        }

        try{
            out.append("  device: ").append(VulkanDevice.deviceName()).append('\n');
            long start = System.nanoTime();

            GLSLProgram.Result translated = GLSLProgram.translate(VERTEX, FRAGMENT);
            byte[] vertex = SpirvCompiler.compile(translated.vertexSource, SpirvCompiler.Stage.VERTEX);
            byte[] fragment = SpirvCompiler.compile(translated.fragmentSource, SpirvCompiler.Stage.FRAGMENT);
            out.append("  spirv: vertex ").append(vertex.length).append(" bytes, fragment ")
                .append(fragment.length).append(" bytes\n");

            if(!VulkanDevice.pipeline(vertex, fragment, false, GL_TRIANGLES)){
                out.append("  pipeline failed: ").append(VulkanDevice.lastError()).append('\n');
                return;
            }

            VulkanDevice.clearColor(0f, 0f, 1f, 1f);
            if(!VulkanDevice.render(0, true)){
                out.append("  clear failed: ").append(VulkanDevice.lastError()).append('\n');
                return;
            }
            out.append("  clear:      ").append(sample()).append('\n');

            VulkanDevice.vertices(quad(1f, 0f, 0f, 1f));
            VulkanDevice.draw(6);
            out.append("  opaque:     ").append(sample()).append('\n');

            if(VulkanDevice.pipeline(vertex, fragment, true, GL_TRIANGLES)){
                VulkanDevice.clearColor(0f, 0f, 1f, 1f);
                VulkanDevice.vertices(quad(1f, 0f, 0f, 0.5f));
                VulkanDevice.render(6, true);
                out.append("  blended:    ").append(sample()).append('\n');
            }

            out.append("  elapsed:    ").append((System.nanoTime() - start) / 1_000_000).append(" ms\n");
            out.append("  result:     offscreen Vulkan render verified by pixel readback\n");
        }finally{
            VulkanDevice.destroy();
        }
    }

    private static String sample(){
        byte[] frame = VulkanDevice.frame();
        if(frame == null) return "readback failed: " + VulkanDevice.lastError();

        int center = (frame.length / 2 / 4) * 4;
        int corner = 0;
        return "center RGBA " + pixel(frame, center) + ", corner RGBA " + pixel(frame, corner)
            + " (" + VulkanDevice.width() + "x" + VulkanDevice.height() + ")";
    }

    private static String pixel(byte[] frame, int offset){
        return (frame[offset] & 0xff) + "," + (frame[offset + 1] & 0xff) + ","
            + (frame[offset + 2] & 0xff) + "," + (frame[offset + 3] & 0xff);
    }

    /** Full-screen quad: two triangles covering clip space, one color per vertex. */
    private static float[] quad(float r, float g, float b, float a){
        float[] xy = {-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f};
        float[] data = new float[xy.length / 2 * VulkanDevice.floatsPerVertex];
        for(int i = 0; i < xy.length / 2; i++){
            int offset = i * VulkanDevice.floatsPerVertex;
            data[offset] = xy[i * 2];
            data[offset + 1] = xy[i * 2 + 1];
            data[offset + 2] = r;
            data[offset + 3] = g;
            data[offset + 4] = b;
            data[offset + 5] = a;
        }
        return data;
    }
}
