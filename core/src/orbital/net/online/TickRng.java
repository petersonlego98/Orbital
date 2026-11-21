package orbital.net.online;

import arc.math.Mathf;
import arc.util.Log;
import orbital.logic.GlobalVars;

/**
 * Deterministic per-tick RNG.
 *
 * <p>The simulation consumes randomness through two globals: {@link GlobalVars#rand} (logic
 * processor {@code rand} instruction) and {@link Mathf#rand} (every {@code Mathf.random/range/
 * chance} call). Both are mutable and outlive a tick. With a fixed tick, the same sequence is only
 * reproduced if both are re-seeded from the same value at the start of every tick.</p>
 *
 * <p>Re-seeding the globals avoids rewriting the ~330 call sites: each tick starts from a state
 * derived from the match seed and the tick number, so the same tick always draws the same numbers
 * in the same order. Only enabled in authoritative mode; legacy play keeps its existing random
 * behaviour byte-for-byte.</p>
 */
public final class TickRng{
    /** Golden-ratio increment, used to decorrelate consecutive ticks. */
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private static long baseSeed0;
    private static long baseSeed1;
    private static long lastTick = -1;
    private static boolean installed;

    private TickRng(){
    }

    /** Captures the match seed. Must be called once, before any randomness is consumed. */
    public static void install(long seed0, long seed1){
        if(installed) return;
        baseSeed0 = seed0;
        baseSeed1 = seed1;
        installed = true;
    }

    /** Installs from the current global RNG state (call right after the world is loaded). */
    public static void installFromGlobals(){
        if(installed) return;
        install(GlobalVars.rand.seed0, GlobalVars.rand.seed1);
    }

    /** Re-seeds both global RNGs for the given tick. Safe to call once per tick. */
    public static void beginTick(long tick){
        if(!AuthoritativeMode.enabled()) return;
        if(!installed) installFromGlobals();

        long s0 = mix(baseSeed0, tick);
        long s1 = mix(baseSeed1 ^ tick, tick + 1);
        GlobalVars.rand.setState(s0, s1);
        Mathf.rand.setState(s0, s1);
        lastTick = tick;
    }

    /** @return a deterministic 64-bit value for (seed, tick), used by other subsystems as a seed. */
    public static long seedFor(long seed, long tick){
        return mix(seed, tick);
    }

    private static long mix(long seed, long tick){
        long z = seed + GAMMA * tick;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long baseSeed0(){
        return baseSeed0;
    }

    public static long baseSeed1(){
        return baseSeed1;
    }

    public static long lastTick(){
        return lastTick;
    }

    public static boolean installed(){
        return installed;
    }

    /** Clears captured state on a new match. */
    public static void reset(){
        installed = false;
        baseSeed0 = 0;
        baseSeed1 = 0;
        lastTick = -1;
    }
}
