package orbital.desktop.vulkan;

import arc.backend.sdl.*;
import arc.backend.sdl.jni.*;
import arc.util.*;

import java.util.*;

/**
 * Presents Vulkan frames into a window the game already owns.
 *
 * <p>This is the first stage of a Vulkan-backed application path. It does not replace the OpenGL
 * renderer - the game keeps rendering through OpenGL - it proves the pieces a real Vulkan
 * application needs, in order:</p>
 *
 * <ol>
 *   <li>finding the Win32 handle of an existing SDL window ({@code nativeFindWindow});</li>
 *   <li>creating a swapchain on it ({@link VulkanDevice#createWindowed});</li>
 *   <li>drawing, presenting and reading back what was presented.</li>
 * </ol>
 *
 * <p>Nothing here is wired into the launcher yet: it exists so the surface path can be exercised
 * end to end before any decision about replacing OpenGL is made.</p>
 */
public class VulkanWindow{
    private final String title;
    private int width, height;
    private boolean created;
    private long handle;
    private int frames;
    /** Swapchain image of the last presented frame. */
    private int surfaceImage = -1;

    public VulkanWindow(String title, int width, int height){
        this.title = title;
        this.width = width;
        this.height = height;
    }

    /** Attaches a swapchain to the game window with this title. */
    public boolean open(){
        close();

        handle = VulkanDevice.findWindow(title);
        if(handle == 0L){
            Log.warn("[Vulkan] no window titled '@' is open", title);
            return false;
        }

        long hinstance = VulkanDevice.moduleHandle();
        if(hinstance == 0L){
            Log.warn("[Vulkan] could not resolve this process HINSTANCE");
            return false;
        }

        if(!VulkanDevice.createWindowed(width, height, handle, hinstance)){
            Log.warn("[Vulkan] windowed device failed: @", VulkanDevice.lastError());
            return false;
        }

        created = true;
        Log.info("[Vulkan] surface @x@ on '@' (swapchain images: @)",
            width, height, title, VulkanDevice.imageCount());
        return true;
    }

    /** Blits the rendered frame into the swapchain and presents it; false on failure. */
    public boolean presentFrame(){
        return created && VulkanDevice.presentFrame();
    }

    /**
     * Presents the frame the renderer drew and returns the pixels that were presented, or null on
     * failure. The rendered target is blitted into the swapchain, which is the Vulkan equivalent
     * of a buffer swap.
     */
    public byte[] present(float r, float g, float b, float a){
        if(!created) return null;

        //The swapchain follows the window, so the drawable size can differ from what was asked for.
        int w = VulkanDevice.windowWidth(), h = VulkanDevice.windowHeight();
        if(w != width || h != height){
            Log.info("[Vulkan] surface presented at @x@ (requested @x@)", w, h, width, height);
            width = w;
            height = h;
        }

        if(!VulkanDevice.presentFrame()){
            Log.warn("[Vulkan] present failed: @", VulkanDevice.lastError());
            return null;
        }

        // Readback from the rendered target: the same pixels were just blitted to the swapchain,
        // and the target is the only image the readback path knows how to reach.
        byte[] rgba = new byte[w * h * VulkanDevice.bytesPerPixel];
        if(!VulkanDevice.pixels(rgba)){
            Log.warn("[Vulkan] presented, but could not read the target back: @", VulkanDevice.lastError());
            return null;
        }

        frames++;
        surfaceImage = VulkanDevice.imageIndex();
        return rgba;
    }

    public boolean setVSync(boolean enabled){
        return created && VulkanDevice.setVSync(enabled);
    }

    public void close(){
        if(created){
            VulkanDevice.destroy();
            created = false;
        }
        handle = 0L;
    }

    public boolean isOpen(){
        return created;
    }

    public int frames(){
        return frames;
    }

    public int surfaceImage(){
        return surfaceImage;
    }

    public int width(){
        return width;
    }

    public int height(){
        return height;
    }

    /** Title lookup for the main game window, as created by the SDL backend. */
    public static String mainWindowTitle(){
        return "Orbital";
    }
}
