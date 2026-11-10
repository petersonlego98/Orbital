package orbital.desktop.vulkan;

import orbital.desktop.vulkan.gl.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bridge test for the native Vulkan device binding.
 *
 * <p>Without a Vulkan driver (no ICD registered) the device is expected to fail fast with a
 * descriptive error and to keep rejecting calls afterwards instead of crashing. With a driver - a
 * software ICD such as SwiftShader or lavapipe is enough - the whole offscreen path is exercised:
 * pipeline creation, a clear, an opaque draw and a blended draw, all verified through pixel
 * readback.</p>
 */
class VulkanDeviceBridgeTest{
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

    @Test
    void nativeLibraryIsPackaged(){
        assertTrue(VulkanLibrary.loaded(), "orbital_vulkan.dll could not be loaded: " + VulkanLibrary.failureReason());
        assertTrue(VulkanDevice.available());
    }

    @Test
    void callsBeforeCreationAreRejectedWithoutThrowing(){
        VulkanDevice.destroy(); // deterministic starting state, whatever ran before
        assertFalse(VulkanDevice.created());

        assertFalse(VulkanDevice.clearColor(0f, 0f, 0f, 1f));
        assertFalse(VulkanDevice.vertices(quad(1f, 0f, 0f, 1f)));
        assertFalse(VulkanDevice.uniforms(new byte[4]));
        assertFalse(VulkanDevice.pipeline(new byte[4], new byte[4], false, GL_TRIANGLES));
        assertFalse(VulkanDevice.render(6, true));
        assertFalse(VulkanDevice.draw(6));
        assertFalse(VulkanDevice.clearAndPresent(0f, 0f, 0f, 1f));
        assertFalse(VulkanDevice.setVSync(true));
        assertFalse(VulkanDevice.resizeWindow(100, 100));
        assertFalse(VulkanDevice.uploadTexture(2, 2, new byte[16]));
        assertFalse(VulkanDevice.pixels(new byte[64 * 64 * 4]));
        assertNull(VulkanDevice.frame());
        assertEquals("device is not created", VulkanDevice.failureReason());
    }

    @Test
    void windowedCreationValidatesHandlesAndDimensions(){
        VulkanDevice.destroy();
        assertFalse(VulkanDevice.createWindowed(0, 100, 1L, 1L));
        assertEquals("invalid size 0x100", VulkanDevice.failureReason());

        assertFalse(VulkanDevice.createWindowed(100, 100, 0L, 1L));
        assertEquals("HWND and HINSTANCE must be non-zero native handles", VulkanDevice.failureReason());

        assertFalse(VulkanDevice.createWindowed(100, 100, 1L, 0L));
        assertEquals("HWND and HINSTANCE must be non-zero native handles", VulkanDevice.failureReason());
    }

    @Test
    void deviceReportsWhyVulkanIsUnusable(){
        VulkanDevice.destroy();
        if(!VulkanDevice.create(64, 64)){
            String error = VulkanDevice.lastError();
            assertNotNull(error, "a failed create() must report a reason");
            assertFalse(error.isBlank(), "a failed create() must report a reason");
            assertFalse(error.equals("device is not created"),
                "create() must reach the native layer, not fail on the Java guard");
            System.out.println("[Vulkan] no usable device on this host: " + error);
            return;
        }

        try{
            assertTrue(VulkanDevice.created());
            assertEquals(64, VulkanDevice.width());
            assertEquals(64, VulkanDevice.height());
        }finally{
            VulkanDevice.destroy();
        }
    }

    @Test
    void rendersQuadAndReadsBackPixelsWhenADriverExists() throws Exception{
        VulkanDevice.destroy();
        if(!VulkanDevice.create(128, 128)){
            System.out.println("[Vulkan] render path not exercised: " + VulkanDevice.lastError());
            return;
        }

        try{
            byte[] vertex = SpirvCompiler.compile(GLSLProgram.translate(VERTEX, FRAGMENT).vertexSource, SpirvCompiler.Stage.VERTEX);
            byte[] fragment = SpirvCompiler.compile(GLSLProgram.translate(VERTEX, FRAGMENT).fragmentSource, SpirvCompiler.Stage.FRAGMENT);

            assertTrue(VulkanDevice.pipeline(vertex, fragment, false, GL_TRIANGLES),
                "pipeline creation failed: " + VulkanDevice.lastError());

            // Clear only: every pixel keeps the clear color, which validates clear + readback.
            VulkanDevice.clearColor(0f, 0f, 1f, 1f);
            assertTrue(VulkanDevice.render(0, true), VulkanDevice.lastError());
            assertUniformColor(0, 0, 255, 255, "clear");

            // Opaque quad covering the whole target.
            assertTrue(VulkanDevice.vertices(quad(1f, 0f, 0f, 1f)));
            assertTrue(VulkanDevice.draw(6), VulkanDevice.lastError());
            assertUniformColor(255, 0, 0, 255, "opaque quad");

            // Blended quad with 50% alpha over the blue frame: src*0.5 + dst*0.5.
            assertTrue(VulkanDevice.pipeline(vertex, fragment, true, GL_TRIANGLES),
                "blended pipeline creation failed: " + VulkanDevice.lastError());
            VulkanDevice.clearColor(0f, 0f, 1f, 1f);
            assertTrue(VulkanDevice.vertices(quad(1f, 0f, 0f, 0.5f)));
            assertTrue(VulkanDevice.render(6, true), VulkanDevice.lastError());
            assertUniformColor(128, 0, 128, 255, "blended quad");
        }finally{
            VulkanDevice.destroy();
        }
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

    private static void assertUniformColor(int r, int g, int b, int a, String what){
        byte[] frame = VulkanDevice.frame();
        assertNotNull(frame, what + ": readback failed: " + VulkanDevice.lastError());
        for(int i = 0; i < frame.length; i += 4){
            String at = what + " pixel " + (i / 4) + " (RGBA "
                + (frame[i] & 0xff) + "," + (frame[i + 1] & 0xff) + "," + (frame[i + 2] & 0xff) + "," + (frame[i + 3] & 0xff) + ")";
            assertClose(r, frame[i] & 0xff, at);
            assertClose(g, frame[i + 1] & 0xff, at);
            assertClose(b, frame[i + 2] & 0xff, at);
            assertClose(a, frame[i + 3] & 0xff, at);
        }
        System.out.println("[Vulkan] " + what + ": " + VulkanDevice.width() + "x" + VulkanDevice.height()
            + " sampled @" + (frame[0] & 0xff) + "," + (frame[1] & 0xff) + "," + (frame[2] & 0xff) + "," + (frame[3] & 0xff));
    }

    /** Blending on normalized 8-bit targets is implementation-rounded, so allow a small delta. */
    private static void assertClose(int expected, int actual, String message){
        int tolerance = 2;
        if(Math.abs(expected - actual) > tolerance){
            fail(message + " expected ~" + expected + " but was " + actual);
        }
    }
}
