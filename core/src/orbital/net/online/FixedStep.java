package orbital.net.online;

/**
 * Deterministic fixed-step clock.
 *
 * <p>The legacy loop advances {@code state.tick} by {@code delta * 60f}, where delta varies with
 * frame rate and stalls. Two machines therefore never run the same tick sequence, which is why the
 * old code warns that syncing the RNG seed "is far from a guarantee". An authoritative server must
 * instead advance in whole, equal steps: a fixed step of 1/60 second, accumulated from real time and
 * drained in integer increments. Given the same elapsed time, every run produces exactly the same
 * sequence of ticks.</p>
 *
 * <p>The accumulator is capped to prevent a long stall from producing an unbounded burst of catch-up
 * steps. The cap is expressed in whole ticks, so it too is deterministic.</p>
 */
public final class FixedStep{
    /** Default: 60 ticks per second, matching the legacy {@code delta * 60f} scale. */
    public static final float DEFAULT_HZ = 60f;
    /** Never simulate more than this many ticks in one update (a ~0.5s catch-up burst). */
    public static final int DEFAULT_MAX_CATCHUP = 30;

    private final long stepNanos;
    private final int maxCatchup;
    private long accumulator;

    public FixedStep(){
        this(DEFAULT_HZ, DEFAULT_MAX_CATCHUP);
    }

    public FixedStep(float hz, int maxCatchup){
        if(hz <= 0 || !Float.isFinite(hz)) throw new IllegalArgumentException("hz must be positive");
        //Exact integer nanoseconds: using a float here loses the fractional part of 1e9/60 and
        //silently drops one tick over a second, which breaks determinism.
        this.stepNanos = Math.max(1L, Math.round(1_000_000_000.0 / hz));
        this.maxCatchup = Math.max(1, maxCatchup);
    }

    /**
     * Adds real elapsed nanoseconds and returns how many whole fixed ticks are due.
     * Any remainder is kept for the next call.
     */
    public int advance(long elapsedNanos){
        if(elapsedNanos <= 0) return 0;
        accumulator += elapsedNanos;
        long ticks = accumulator / stepNanos;
        if(ticks <= 0) return 0;
        if(ticks > maxCatchup){
            ticks = maxCatchup;
            accumulator = 0; //drop the backlog instead of carrying an unbounded debt
        }else{
            accumulator -= ticks * stepNanos;
        }
        return (int)ticks;
    }

    /** Converts a tick count back to the logical time value the game uses ({@code delta * 60}). */
    public static float toLogicalTick(long ticks, float hz){
        return ticks * (DEFAULT_HZ / hz);
    }

    public long pendingNanos(){
        return accumulator;
    }

    /** @return the exact duration of one fixed tick, in nanoseconds. */
    public long stepNanos(){
        return stepNanos;
    }

    public void reset(){
        accumulator = 0;
    }
}
