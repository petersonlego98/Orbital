package orbital.engine.simd;

/**
 * What the machine can actually execute.
 *
 * <p>The levels are deliberate and honest: {@code 0} scalar, {@code 1} the SSE2 baseline every
 * x86-64 CPU has, {@code 2} AVX2. The native layer used to require SSE4.2 while only using SSE1
 * intrinsics; that check rejected capable chips, so detection now reports the real requirement.
 * The class name {@link Sse4Backend} is kept from the architecture document even though its native
 * path is SSE2 - see that class for the explanation.</p>
 */
public record SimdCapabilities(int backendLevel, boolean nativeLoaded, String library, String note){
    public static final int SCALAR = 0;
    public static final int SSE2 = 1;
    public static final int AVX2 = 2;

    public SimdCapabilities{
        if(backendLevel < SCALAR || backendLevel > AVX2){
            throw new IllegalArgumentException("unsupported SIMD level " + backendLevel);
        }
        library = library == null ? "" : library;
        note = note == null ? "" : note;
    }

    public static SimdCapabilities scalar(String reason){
        return new SimdCapabilities(SCALAR, false, "", reason);
    }

    public boolean sse2(){
        return nativeLoaded && backendLevel >= SSE2;
    }

    public boolean avx2(){
        return nativeLoaded && backendLevel >= AVX2;
    }

    public boolean scalarOnly(){
        return backendLevel == SCALAR;
    }

    public String describe(){
        if(!nativeLoaded) return "scalar (native library unavailable)";
        return switch(backendLevel){
            case AVX2 -> "AVX2 (native)";
            case SSE2 -> "SSE2 (native)";
            default -> "scalar (native)";
        };
    }
}
