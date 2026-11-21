package orbital.net.online;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Per-tick RNG must be reproducible for the same (seed, tick) and distinct across ticks. */
class TickRngTest{
    private static long draw(long seed0, long seed1, long tick, int draws){
        //seedFor is the public deterministic mixer; verify it is stable and tick-dependent.
        long s0 = TickRng.seedFor(seed0, tick);
        long s1 = TickRng.seedFor(seed1 ^ tick, tick + 1);
        java.util.Random r = new java.util.Random(s0 ^ s1);
        long result = 0;
        for(int i = 0; i < draws; i++) result = result * 31 + r.nextLong();
        return result;
    }

    @Test void sameSeedAndTickProduceSameSequence(){
        long a = draw(123L, 456L, 42L, 100);
        long b = draw(123L, 456L, 42L, 100);
        assertEquals(a, b);
    }

    @Test void differentTickProducesDifferentSequence(){
        long a = draw(123L, 456L, 42L, 20);
        long b = draw(123L, 456L, 43L, 20);
        assertNotEquals(a, b);
    }

    @Test void differentSeedProducesDifferentSequence(){
        long a = draw(123L, 456L, 42L, 20);
        long b = draw(999L, 456L, 42L, 20);
        assertNotEquals(a, b);
    }

    @Test void createsDistinctStateWords(){
        long s0 = TickRng.seedFor(7L, 1L);
        long s1 = TickRng.seedFor(7L, 2L);
        assertNotEquals(s0, s1, "consecutive ticks must not share a state word");
    }

    @Test void resetClearsInstallation(){
        TickRng.install(1L, 2L);
        assertTrue(TickRng.installed());
        TickRng.reset();
        assertFalse(TickRng.installed());
    }
}
