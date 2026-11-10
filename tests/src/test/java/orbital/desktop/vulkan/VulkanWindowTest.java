package orbital.desktop.vulkan;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Windowed Vulkan presentation into a real SDL window.
 *
 * <p>This is the item-3 milestone: find the game window, create a swapchain on it, draw, present
 * and read the presented pixels back. The readback is the only honest evidence of what reached the
 * screen, so that is what is asserted.</p>
 *
 * <p>Without a Vulkan driver the whole path is skipped, exactly like the offscreen tests.</p>
 */
class VulkanWindowTest{
    private static final int WIDTH = 320, HEIGHT = 240;

    private static VulkanWindow window;

    @BeforeAll
    static void openWindow(){
        VulkanDevice.destroy();
        if(!VulkanDevice.available()){
            return;
        }
        window = new VulkanWindow("Orbital", WIDTH, HEIGHT);
        assumeOpened(window.open());
    }

    @AfterAll
    static void closeWindow(){
        if(window != null) window.close();
        VulkanDevice.destroy();
    }

    @Test
    void theWindowIsFoundAndTheSwapchainIsCreated(){
        assertNotNull(window, "the test is skipped when no Vulkan driver exists");
        assertTrue(window.isOpen());
        assertTrue(VulkanDevice.isWindowed());
        assertTrue(VulkanDevice.imageCount() >= 1, "a swapchain must hold at least one image");
        assertEquals(WIDTH, VulkanDevice.windowWidth());
        assertEquals(HEIGHT, VulkanDevice.windowHeight());
    }

    @Test
    void presentedFramesCanBeReadBack(){
        assumeWindow();

        byte[] green = window.present(0f, 1f, 0f, 1f);
        assertNotNull(green, "present failed: " + VulkanDevice.lastError());
        assertEquals(WIDTH * HEIGHT * VulkanDevice.bytesPerPixel, green.length);
        assertPixel(green, WIDTH / 2, HEIGHT / 2, 0, 255, 0, 255, "cleared green");
        assertPixel(green, 2, 2, 0, 255, 0, 255, "corner is cleared too");

        byte[] red = window.present(1f, 0f, 0f, 1f);
        assertNotNull(red, "present failed: " + VulkanDevice.lastError());
        assertPixel(red, WIDTH / 2, HEIGHT / 2, 255, 0, 0, 255, "cleared red");
        assertTrue(window.frames() >= 2, "frames are counted");
    }

    @Test
    void aSecondFrameReusesTheSameSurface(){
        assumeWindow();

        int images = VulkanDevice.imageCount();
        for(int i = 0; i < 3; i++){
            assertNotNull(window.present(0f, 0f, 1f, 1f), "frame " + i + " failed: " + VulkanDevice.lastError());
        }
        assertEquals(images, VulkanDevice.imageCount(), "repeated frames must not rebuild the swapchain");
        assertEquals(3, window.frames());
    }

    private void assumeWindow(){
        org.junit.jupiter.api.Assumptions.assumeTrue(window != null && window.isOpen(),
            "no Vulkan device or no SDL window available");
    }

    private static void assumeOpened(boolean opened){
        org.junit.jupiter.api.Assumptions.assumeTrue(opened, "windowed device could not be created");
    }

    private static void assertPixel(byte[] frame, int x, int y, int r, int g, int b, int a, String what){
        int index = (y * WIDTH + x) * 4;
        String at = what + " @" + x + "," + y + " expected (" + r + "," + g + "," + b + "," + a
            + ") but was (" + (frame[index] & 0xff) + "," + (frame[index + 1] & 0xff) + ","
            + (frame[index + 2] & 0xff) + "," + (frame[index + 3] & 0xff) + ")";
        assertEquals(r, frame[index] & 0xff, at);
        assertEquals(g, frame[index + 1] & 0xff, at);
        assertEquals(b, frame[index + 2] & 0xff, at);
        assertEquals(a, frame[index + 3] & 0xff, at);
    }
}
