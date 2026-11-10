package orbital.desktop.vulkan;

import arc.util.*;

/**
 * Shared loader for the packaged Orbital Vulkan native library ({@code orbital_vulkan.dll}).
 *
 * <p>The library is extracted from the jar resources to a temporary file and loaded with
 * {@link System#load}. Every stage of the Vulkan port - the capability probe in
 * {@link VulkanSupport} and the offscreen rendering core in {@link VulkanDevice} - goes through
 * this class, so the DLL is extracted and mapped exactly once per process.</p>
 *
 * <p>Loading is best-effort by design: any failure is remembered in {@link #failureReason()} and
 * {@link #loaded()} returns false, which keeps OpenGL the active backend on machines without the
 * Windows x86-64 build, without a Vulkan loader, or without any Vulkan driver.</p>
 */
public final class VulkanLibrary{
    private static final String resource = "/native/windows-x86_64/orbital_vulkan.dll";

    private static final boolean loaded = load();
    private static String failure;

    private VulkanLibrary(){
    }

    /** True when the native library is mapped and its JNI entry points are resolvable. */
    public static boolean loaded(){
        return loaded;
    }

    /** Human-readable reason why the library is unavailable, or null when it is. */
    public static String failureReason(){
        return failure;
    }

    private static boolean load(){
        if(!OS.isWindows){
            failure = "Vulkan support is Windows-only in this build";
            return false;
        }

        String arch = System.getProperty("os.arch", "").toLowerCase(java.util.Locale.ROOT);
        if(!arch.equals("amd64") && !arch.equals("x86_64")){
            failure = "unsupported architecture: " + arch;
            return false;
        }

        java.nio.file.Path library = null;
        try(var input = VulkanLibrary.class.getResourceAsStream(resource)){
            if(input == null){
                failure = "orbital_vulkan.dll was not packaged with this build";
                return false;
            }

            library = java.nio.file.Files.createTempFile("orbital-vulkan-", ".dll");
            java.nio.file.Files.copy(input, library, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            library.toFile().deleteOnExit();
            System.load(library.toAbsolutePath().toString());

            // Set before any device is created: the loader picks its driver at vkCreateInstance.
            String icd = System.getProperty("orbital.vulkan.icd");
            if(icd != null && !icd.isBlank()) VulkanDevice.setIcdPath(icd);
            return true;
        }catch(Exception | LinkageError error){
            failure = describe(error);
            Log.debug("[Vulkan] Native library unavailable: @", failure);

            if(library != null){
                try{
                    java.nio.file.Files.deleteIfExists(library);
                }catch(Exception ignored){
                }
            }
            return false;
        }
    }

    private static String describe(Throwable error){
        String name = error.getClass().getSimpleName();
        return (error.getMessage() == null ? name : name + ": " + error.getMessage());
    }
}
