package orbital.desktop.vulkan;

/** Static holder for the launcher flags, so {@code main} can pass them to the instance. */
public final class VulkanChecks{
    /** True when {@code -vulkan-window} was present on the command line. */
    public static boolean windowCheck;

    private VulkanChecks(){
    }
}
