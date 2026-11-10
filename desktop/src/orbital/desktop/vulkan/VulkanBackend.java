package orbital.desktop.vulkan;

import arc.util.*;

import orbital.core.RendererPreference;

/**
 * Desktop graphics backend selection.
 *
 * <p>Stage 3 of the Arc Vulkan port adds a working Vulkan 1.0 rendering core
 * ({@link VulkanDevice}), which renders offscreen and supports pixel readback. Arc's GL20/GL30
 * contract on top of it is still missing, so Vulkan cannot be selected as the active renderer yet:
 * OpenGL remains the default and the fallback, and every Vulkan request - the {@link #OPTION}
 * launch flag or the {@link RendererPreference#key} setting from Settings > Advanced Graphics -
 * resolves to OpenGL with a log line explaining why.</p>
 */
public final class VulkanBackend{
    public static final String OPTION = "-vulkan";
    public static final String CHECK_OPTION = "-vulkan-check";
    public static final String WINDOW_CHECK_OPTION = "-vulkan-window";
    public static final String APP_OPTION = "-vulkan-app";
    public static final String APP_PIXELS_OPTION = "-vulkan-app-pixels";

    public enum Type{
        VULKAN, OPENGL
    }

    private VulkanBackend(){
    }

    /** True when {@code -vulkan} is present in the launch arguments. */
    public static boolean requested(String[] args){
        for(String arg : args){
            if(OPTION.equals(arg)) return true;
        }
        return false;
    }

    /**
     * True once a launcher entry point can construct the Arc Vulkan renderer. This is the single
     * switch of the port: the native core, the GL20/GL30 layer over it and their tests are all in
     * place, and {@code VulkanApplication} exists, but the preference path must NOT construct it
     * yet - when exercised on a Vulkan-capable machine (2026-11-10) the port rendered broken
     * output (colored rectangles) in real gameplay. It stays reachable only through the explicit
     * {@code -vulkan-app} flag until the rendering defect is fixed on such a machine.
     */
    public static boolean rendererWired(){
        return false;
    }

    /** True only when the Arc Vulkan renderer is both wired and usable on this machine. */
    public static boolean rendererReady(){
        return rendererWired() && coreReady();
    }

    /** True when the native Vulkan rendering core is present and usable, independently of the Arc port. */
    public static boolean coreReady(){
        return VulkanDevice.available() && VulkanSupport.isAvailable();
    }

    /** True when {@code -vulkan-check} is present: runs the offscreen self-check, see {@link VulkanSelfTest}. */
    public static boolean checkRequested(String[] args){
        for(String arg : args){
            if(CHECK_OPTION.equals(arg)) return true;
        }
        return false;
    }

    /**
     * True when {@code -vulkan-window} is present: runs the windowed self-check, which attaches a
     * swapchain to the game window and verifies the presented pixels. See
     * {@link VulkanWindowSelfTest}.
     */
    public static boolean windowCheckRequested(String[] args){
        for(String arg : args){
            if(WINDOW_CHECK_OPTION.equals(arg)) return true;
        }
        return false;
    }

    /**
     * True when {@code -vulkan-app} is present: runs the whole game on the Vulkan application
     * backend, where {@code Core.gl20} is {@link orbital.desktop.vulkan.gl.VulkanGL20}. This is the
     * furthest stage of the port; it is not reachable from the settings preference, because the
     * renderer is not complete.
     */
    public static boolean appRequested(String[] args){
        for(String arg : args){
            if(APP_OPTION.equals(arg)) return true;
        }
        return false;
    }

    /**
     * True when {@code -vulkan-app-pixels} is present, alongside {@link #APP_OPTION}: reads the
     * presented frame back every few seconds and logs its colour histogram. Diagnostic only - the
     * readback costs a full frame and would mask a performance problem.
     */
    public static boolean pixelReportRequested(String[] args){
        for(String arg : args){
            if(APP_PIXELS_OPTION.equals(arg)) return true;
        }
        return false;
    }

    /**
     * Chooses a backend before any GL-dependent Arc resources are created. Every Vulkan failure
     * or missing port stage intentionally returns OPENGL.
     *
     * @param args           launch arguments; {@link #OPTION} forces the request
     * @param vulkanSelected whether the persisted preference asks for Vulkan, see
     *                       {@link VulkanPreferences#vulkanRequested()}
     */
    public static Type select(String[] args, boolean vulkanSelected){
        if(!requested(args) && !vulkanSelected) return Type.OPENGL;

        if(!VulkanSupport.isAvailable()){
            Log.warn("[Vulkan] No usable Vulkan device detected; falling back to OpenGL.");
            return Type.OPENGL;
        }

        if(!rendererReady()){
            Log.warn("[Vulkan] @ requested, but the Arc GL20/GL30 port over the Vulkan core is incomplete; falling back to OpenGL.", VulkanSupport.deviceInfo());
            return Type.OPENGL;
        }

        return Type.VULKAN;
    }

    /**
     * Chooses a backend using the persisted preference, which is read from the settings file because
     * {@code Core.settings} does not exist yet at this point.
     */
    public static Type select(String[] args){
        return select(args, VulkanPreferences.vulkanRequested());
    }
}
