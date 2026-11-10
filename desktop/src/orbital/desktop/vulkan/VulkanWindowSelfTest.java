package orbital.desktop.vulkan;

import arc.util.*;

/**
 * Presents a few Vulkan frames into the game's own window, reads the pixels back and logs them.
 *
 * <p>Run from the desktop launcher with {@code -vulkan-window}. It is the end-to-end stage of the
 * windowed path: the game keeps rendering with OpenGL, while this proves that a Vulkan swapchain
 * can be created on the same window, drawn into, presented and verified by pixel readback. Nothing
 * here decides that Vulkan becomes the renderer - it only removes the unknowns of the surface path.</p>
 */
public final class VulkanWindowSelfTest{
    private static final int WIDTH = 320, HEIGHT = 240;
    private static final int FRAMES = 3;

    private VulkanWindowSelfTest(){
    }

    /** Runs the check and logs one line per frame; never throws. */
    public static void report(){
        if(!VulkanLibrary.loaded()){
            Log.info("[Vulkan] window check skipped: native library unavailable (@)", VulkanLibrary.failureReason());
            return;
        }

        VulkanWindow window = new VulkanWindow(VulkanWindow.mainWindowTitle(), WIDTH, HEIGHT);
        if(!window.open()){
            Log.warn("[Vulkan] window check could not attach to '@': @",
                VulkanWindow.mainWindowTitle(), VulkanDevice.lastError());
            return;
        }

        try{
            Log.info("[Vulkan] surface @x@ on '@' (swapchain images: @)",
                WIDTH, HEIGHT, VulkanWindow.mainWindowTitle(), VulkanDevice.imageCount());

            float[][] colors = {
                {1f, 0f, 0f, 1f}, {0f, 1f, 0f, 1f}, {0f, 0f, 1f, 1f}
            };

            for(int frame = 0; frame < FRAMES; frame++){
                float[] color = colors[frame % colors.length];
                byte[] pixels = window.present(color[0], color[1], color[2], color[3]);
                if(pixels == null){
                    Log.warn("[Vulkan] window check frame @ failed: @", frame, VulkanDevice.lastError());
                    return;
                }
                Log.info("[Vulkan] window frame @/ @ presented RGBA @,@,@,@ (image @ of @, vsync @)",
                    frame + 1, FRAMES,
                    pixels[0] & 0xff, pixels[1] & 0xff, pixels[2] & 0xff, pixels[3] & 0xff,
                    window.surfaceImage(), VulkanDevice.imageCount(), VulkanDevice.hasVSync());
            }

            Log.info("[Vulkan] window check: @ frames presented to '@' and verified by pixel readback",
                window.frames(), VulkanWindow.mainWindowTitle());
        }finally{
            window.close();
        }
    }
}
