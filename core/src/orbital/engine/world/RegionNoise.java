package orbital.engine.world;

/**
 * Deterministic, stateless value noise for region-based world generation.
 *
 * <p>Every tile is a pure function of its coordinates and the seed, so a map generator can hand
 * disjoint rectangular regions to the job system and get exactly the same terrain whatever the
 * worker count. No global random state is involved, which also makes streaming safe.</p>
 */
public final class RegionNoise{
    private RegionNoise(){
    }

    /** @return a value in {@code [0, 1)} for one tile. */
    public static float tile(int x, int y, long seed){
        long hash = seed;
        hash ^= (long)x * 0x9E3779B97F4A7C15L;
        hash ^= (long)y * 0xC2B2AE3D27D4EB4FL;
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (hash & 0x00FFFFFFL) / (float)0x01000000;
    }

    /** Fills a rectangular region of a row-major grid; row ranges may be processed in parallel. */
    public static void fill(float[] out, int width, int height,
                            int x0, int y0, int x1, int y1, long seed,
                            int firstRow, int lastRow){
        int startY = Math.max(y0, firstRow);
        int endY = Math.min(y1, lastRow);
        for(int y = startY; y < endY; y++){
            int row = y * width;
            for(int x = x0; x < x1; x++){
                out[row + x] = tile(x, y, seed);
            }
        }
    }
}