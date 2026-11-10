package orbital.desktop.vulkan;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Framebuffer objects and depth/stencil on the Vulkan core.
 *
 * <p>Creation, binding, sampling and readback are exercised through the native device. Without a
 * Vulkan driver the device cannot be created, so the tests assert the documented graceful failure
 * instead of skipping blindly: a framebuffer without a device must not crash and must say why.</p>
 */
class VulkanFramebufferTest{
    private static final int SIZE = 64;

    @BeforeEach
    void destroyLeftovers(){
        VulkanDevice.destroy();
    }

    @AfterEach
    void cleanup(){
        VulkanDevice.destroy();
    }

    @Test
    void withoutADeviceNothingCrashesAndEveryCallExplainsItself(){
        assertFalse(VulkanDevice.created());
        assertNull(VulkanDevice.frame());

        assertEquals(0, VulkanDevice.createFramebuffer(SIZE, SIZE, false, false));
        //failureReason() is the reason of the rejected Java call; lastError() also reports a stale
        //native message from an earlier failed create(), which is why it is not asserted here.
        assertEquals("device is not created", VulkanDevice.failureReason());

        assertFalse(VulkanDevice.bindFramebuffer(1));
        assertFalse(VulkanDevice.deleteFramebuffer(1));
        assertEquals(0, VulkanDevice.framebufferStatus(1));
        assertFalse(VulkanDevice.sampleFramebuffer(1));
        assertFalse(VulkanDevice.clearDepthStencil(1f, 0));
        assertFalse(VulkanDevice.finish());

        //Dimensions of an unbound device are zero, not a stale value from a previous run.
        assertEquals(0, VulkanDevice.boundWidth());
        assertEquals(0, VulkanDevice.boundHeight());
    }

    @Test
    void invalidSizesAreRejected(){
        assumeDevice();

        assertEquals(0, VulkanDevice.createFramebuffer(0, SIZE, false, false));
        assertEquals(0, VulkanDevice.createFramebuffer(SIZE, -1, false, false));
        assertTrue(VulkanDevice.lastError().contains("invalid framebuffer size"));

        //A rejected call leaves no half-created framebuffer behind.
        assertFalse(VulkanDevice.bindFramebuffer(1));
    }

    @Test
    void aFramebufferCoversTheFullLifecycle(){
        assumeDevice();

        int framebuffer = VulkanDevice.createFramebuffer(SIZE, SIZE, true, true);
        if(framebuffer == 0){
            //Depth formats are optional: a device without one still yields colour-only targets.
            assertFalse(VulkanDevice.lastError().isEmpty());
            return;
        }
        assertTrue(VulkanDevice.bindFramebuffer(framebuffer));
        assertEquals(SIZE, VulkanDevice.boundWidth());
        assertEquals(SIZE, VulkanDevice.boundHeight());
        assertEquals(1, VulkanDevice.framebufferStatus(framebuffer), "the device reports an incomplete framebuffer: " + VulkanDevice.lastError());

        //Clearing is the first thing a render pass does, so it must work before any draw.
        assertTrue(VulkanDevice.clearColor(0f, 1f, 0f, 1f));
        assertTrue(VulkanDevice.clearDepthStencil(0.5f, 3));
        assertTrue(VulkanDevice.render(0, true));

        byte[] pixels = new byte[SIZE * SIZE * VulkanDevice.bytesPerPixel];
        assertTrue(VulkanDevice.pixels(pixels),
            "readback from the framebuffer failed: " + VulkanDevice.lastError());
        assertEquals(0, pixels[0] & 0xff, "cleared red channel must be zero");
        assertEquals(255, pixels[1] & 0xff, "cleared green channel must be the requested one");

        //Sampling needs rendered contents, which this target now has.
        assertTrue(VulkanDevice.sampleFramebuffer(framebuffer),
            "sampling a rendered framebuffer failed: " + VulkanDevice.lastError());

        assertTrue(VulkanDevice.deleteFramebuffer(framebuffer));
        assertEquals(0, VulkanDevice.framebufferStatus(framebuffer), "a deleted framebuffer is unknown");
    }

    @Test
    void samplingBeforeRenderingIsRejected(){
        assumeDevice();

        int framebuffer = VulkanDevice.createFramebuffer(SIZE, SIZE, false, false);
        assumeTrue(framebuffer != 0, "no framebuffer could be created: " + VulkanDevice.lastError());

        assertTrue(VulkanDevice.bindFramebuffer(framebuffer));
        assertFalse(VulkanDevice.sampleFramebuffer(framebuffer),
            "sampling undefined contents must be refused, not returned as garbage");
        assertTrue(VulkanDevice.lastError().contains("no rendered contents"));

        assertTrue(VulkanDevice.sampleFramebuffer(0), "the uploaded texture is always sampleable");
    }

    @Test
    void bindingTheDefaultTargetRestoresItsSize(){
        assumeDevice();

        int framebuffer = VulkanDevice.createFramebuffer(SIZE, SIZE, false, false);
        assumeTrue(framebuffer != 0, "no framebuffer could be created: " + VulkanDevice.lastError());

        assertTrue(VulkanDevice.bindFramebuffer(framebuffer));
        assertTrue(VulkanDevice.bindFramebuffer(0));
        assertEquals(VulkanDevice.width(), VulkanDevice.boundWidth());
        assertEquals(VulkanDevice.height(), VulkanDevice.boundHeight());
    }

    private void assumeDevice(){
        if(!VulkanDevice.create(128, 128)){
            VulkanDevice.destroy();
            assumeTrue(false, "no Vulkan device: " + VulkanDevice.lastError());
        }
    }

    private void assumeTrue(boolean condition, String reason){
        org.junit.jupiter.api.Assumptions.assumeTrue(condition, reason);
    }
}
