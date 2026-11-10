package orbital.desktop.vulkan;

import arc.util.*;

/** Desktop Vulkan capability probe. Stage 1 of the Arc Vulkan backend port. */
public final class VulkanSupport{
    private static final boolean available;
    private static final String deviceInfo;

    static{
        String device = null;
        boolean loaded = false;

        if(VulkanLibrary.loaded()){
            try{
                device = nativeProbe();
            }catch(Throwable error){
                // A loaded library without a working probe is still a missing capability.
                Log.debug("[Vulkan] Device probe failed: @", error.toString());
                device = null;
            }
            loaded = device != null;
        }

        available = loaded;
        deviceInfo = device;
    }

    private VulkanSupport(){
    }

    /** Returns true when a Vulkan 1.0+ device with a graphics queue is available. */
    public static boolean isAvailable(){
        return available;
    }

    /** Device name and Vulkan version, or null when unavailable. */
    public static String deviceInfo(){
        return deviceInfo;
    }

    /** Why the probe failed, or null when it succeeded. Only used for diagnostics. */
    public static String unavailableReason(){
        return available ? null : VulkanLibrary.failureReason();
    }

    private static native String nativeProbe();
}
