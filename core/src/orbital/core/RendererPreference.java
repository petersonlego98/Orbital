package orbital.core;

import arc.func.*;
import arc.util.*;

import static arc.Core.*;

/**
 * Persisted graphics backend preference.
 *
 * <p>The value lives in the {@link #key} setting and is either {@link #opengl} or {@link #vulkan},
 * as chosen under Settings > Advanced Graphics. The preference is only a request: the desktop
 * launcher resolves it into a backend <i>before</i> the GL context is created, and OpenGL stays the
 * active renderer whenever Vulkan is unavailable or the Arc renderer on top of the Vulkan core is
 * not complete enough yet.</p>
 *
 * <p>The optional {@link #vulkanProbe} hook lets the desktop launcher report hardware capability to
 * the settings UI without {@code core} depending on the desktop Vulkan module.</p>
 */
public final class RendererPreference{
    /** Settings key holding the requested backend. */
    public static final String key = "renderer";
    /** Default backend: the OpenGL renderer, available on every platform. */
    public static final String opengl = "opengl";
    /** Experimental desktop-only backend. */
    public static final String vulkan = "vulkan";

    /**
     * Reports whether a usable Vulkan device exists on this machine. Assigned by the desktop
     * launcher; null means "not probed", which the settings UI treats as unavailable.
     */
    public static @Nullable Boolp vulkanProbe;

    private RendererPreference(){
    }

    /** Requested backend, never null. Unknown values normalize to {@link #opengl}. */
    public static String get(){
        return normalize(settings == null ? null : settings.getString(key, opengl));
    }

    /** Stores a backend value; invalid values normalize to {@link #opengl}. */
    public static void set(String value){
        if(settings != null) settings.put(key, normalize(value));
    }

    /** True when Vulkan is the requested backend. */
    public static boolean vulkanRequested(){
        return vulkan.equals(get());
    }

    /** True only when a probe was installed and it found a usable Vulkan device. */
    public static boolean vulkanUsable(){
        return vulkanProbe != null && vulkanProbe.get();
    }

    /**
     * Maps any stored value onto {@link #opengl} or {@link #vulkan}. Null, empty and unknown values
     * - including leftovers from a different build - keep OpenGL, which is the safe default.
     */
    public static String normalize(String value){
        if(value != null && value.trim().equalsIgnoreCase(vulkan)) return vulkan;
        return opengl;
    }
}
