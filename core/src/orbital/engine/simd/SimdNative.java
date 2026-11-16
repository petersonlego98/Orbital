package orbital.engine.simd;

import arc.util.*;
import orbital.engine.simd.SimdCapabilities;

import java.util.*;

/**
 * Loads and calls the packaged native SIMD library.
 *
 * <p>The DLL is extracted once per build into a deterministic name carrying a SHA-256 digest: a
 * new build lands in a new file, an unchanged one is reused, and Windows is never asked to delete
 * a mapped library. Earlier builds used a random name and leaked a copy per launch; those decimal
 * names are still cleaned up best-effort.</p>
 *
 * <p>The library itself picks AVX2, then SSE2, then scalar based on CPUID. Java only asks which
 * level won; the same translation unit therefore cannot disagree with itself about detection.</p>
 */
public final class SimdNative{
    private static final String resource = "/native/windows-x86_64/orbital_particlesimd.dll";
    private static final boolean archiveSupported;

    private static final boolean loaded;
    private static final int backendLevel;
    private static final String libraryPath;

    static{
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        archiveSupported = OS.isWindows && (arch.equals("amd64") || arch.equals("x86_64"));

        String path = archiveSupported ? extractAndLoad() : null;
        int level = 0;
        boolean available = path != null;
        if(available){
            try{
                level = backendLevelNative();
                if(level < 0 || level > SimdCapabilities.AVX2) available = false;
            }catch(LinkageError | RuntimeException unsupported){
                available = false;
            }
        }

        loaded = available;
        backendLevel = available ? level : SimdCapabilities.SCALAR;
        libraryPath = available ? path : "";
    }

    private SimdNative(){
    }

    public static boolean loaded(){
        return loaded;
    }

    public static int backendLevel(){
        return backendLevel;
    }

    public static String libraryPath(){
        return libraryPath;
    }

    public static SimdCapabilities capabilities(){
        if(!loaded){
            String reason = archiveSupported
                ? "native library unavailable; using the Java scalar kernel"
                : "native SIMD ships for Windows x86_64 only; using the Java scalar kernel";
            return SimdCapabilities.scalar(reason);
        }
        return new SimdCapabilities(backendLevel, true, libraryPath, "native particle kernel loaded");
    }

    /** @return the new particle count, or a negative value when the library is unavailable. */
    public static int updateParticles(float[] data, int count, float delta){
        if(!loaded) return -1;
        return updateParticlesNative(data, count, delta);
    }

    /**
     * Integrates a whole SoA slice in one native call.
     *
     * @return the number of rows processed, or a negative value when the library cannot run the
     * data (length mismatch, unloaded library).
     */
    public static int updateUnits(float[] x, float[] y, float[] vx, float[] vy, float[] drag,
                                  int offset, int count, float delta, boolean zeroUnmoved){
        if(!loaded) return -1;
        return updateUnitsNative(x, y, vx, vy, drag, offset, count, delta, zeroUnmoved);
    }

    /** @return the native address of a direct buffer, or 0 when the library is unavailable. */
    public static long directAddress(java.nio.ByteBuffer buffer){
        if(!loaded || buffer == null || !buffer.isDirect()) return 0L;
        return directAddressNative(buffer);
    }

    /** Integrates one disjoint slice of a shared direct SoA buffer. */
    public static int updateUnitsDirect(long ax, long ay, long avx, long avy, long adrag,
                                        int offset, int count, float delta, boolean zeroUnmoved){
        if(!loaded) return -1;
        return updateUnitsDirectNative(ax, ay, avx, avy, adrag, offset, count, delta, zeroUnmoved);
    }

    private static String extractAndLoad(){
        try(var input = SimdNative.class.getResourceAsStream(resource)){
            if(input == null) return null;

            byte[] bytes = input.readAllBytes();
            java.nio.file.Path library = java.nio.file.Path.of(
                System.getProperty("java.io.tmpdir"), "orbital-simd-" + digest(bytes) + ".dll");
            deleteLegacyCopies(library);

            if(!java.nio.file.Files.isRegularFile(library)
                || !Arrays.equals(java.nio.file.Files.readAllBytes(library), bytes)){
                //stage next to the target and rename: the final name never holds a half-written file
                java.nio.file.Path staging = java.nio.file.Files.createTempFile("orbital-simd-", ".part");
                try{
                    java.nio.file.Files.write(staging, bytes);
                    java.nio.file.Files.move(staging, library,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }catch(Exception copy){
                    java.nio.file.Files.deleteIfExists(staging);
                    throw copy;
                }
            }

            System.load(library.toAbsolutePath().toString());
            return library.toAbsolutePath().toString();
        }catch(Exception | LinkageError ignored){
            return null;
        }
    }

    /**
     * Best-effort removal of copies earlier builds left behind. Only decimal names match; the
     * hashed names of this scheme are reused and skipped. A copy still mapped by another instance
     * cannot be deleted on Windows, and that failure is swallowed on purpose.
     */
    private static void deleteLegacyCopies(java.nio.file.Path keep){
        if(keep.getParent() == null) return;
        try(var files = java.nio.file.Files.newDirectoryStream(
            keep.getParent(), "orbital-*simd-*.dll")){
            for(java.nio.file.Path candidate : files){
                if(candidate.equals(keep)) continue;

                String name = candidate.getFileName().toString();
                // this build never uses the old prefix, so every particlesimd copy is disposable;
                // with the current prefix only the decimal random names of the previous scheme are
                boolean oldPrefix = name.startsWith("orbital-particlesimd-");
                boolean legacyDecimal = name.matches("orbital-(particlesimd|simd)-\\d{1,20}\\.dll");
                if(!oldPrefix && !legacyDecimal) continue;

                try{
                    java.nio.file.Files.deleteIfExists(candidate);
                }catch(Exception stillMapped){
                }
            }
        }catch(Exception ignoredListing){
        }
    }

    /** @return the first 16 hex digits of the SHA-256, used to name the extracted DLL per build. */
    private static String digest(byte[] bytes){
        try{
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(16);
            for(int i = 0; i < 8; i++){
                hex.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                hex.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return hex.toString();
        }catch(java.security.NoSuchAlgorithmException fallback){
            //SHA-256 is mandatory in every JRE; keep the method total anyway
            return Integer.toHexString(Arrays.hashCode(bytes));
        }
    }

    private static native int updateParticlesNative(float[] data, int count, float delta);

    private static native int updateUnitsNative(float[] x, float[] y, float[] vx, float[] vy,
                                                float[] drag, int offset, int count, float delta,
                                                boolean zeroUnmoved);

    private static native int backendLevelNative();

    private static native long directAddressNative(java.nio.ByteBuffer buffer);

    private static native int updateUnitsDirectNative(long ax, long ay, long avx, long avy,
                                                      long adrag, int offset, int count,
                                                      float delta, boolean zeroUnmoved);
}
