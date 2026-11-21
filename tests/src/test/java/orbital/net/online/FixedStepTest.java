package orbital.net.online;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Fixed-step clock must be deterministic and independent of how time is sliced. */
class FixedStepTest{
    @Test void exactTicks(){
        FixedStep step = new FixedStep(60f, 30);
        long oneTick = step.stepNanos();
        assertEquals(1, step.advance(oneTick));
        assertEquals(0, step.advance(oneTick - 1));
        assertEquals(1, step.advance(1));
    }

    @Test void sameTotalProducesSameTicksRegardlessOfSlicing(){
        //One big advance vs many small ones must yield the same number of ticks.
        //50 Hz divides 1s exactly (20ms/tick), avoiding rounding ambiguity in the assertion.
        FixedStep whole = new FixedStep(50f, 1000);
        int totalWhole = whole.advance(1_000_000_000L);

        FixedStep sliced = new FixedStep(50f, 1000);
        int totalSliced = 0;
        for(int i = 0; i < 100; i++) totalSliced += sliced.advance(10_000_000L);

        assertEquals(totalWhole, totalSliced);
        assertEquals(50, totalWhole);
    }

    @Test void catchupIsCapped(){
        FixedStep step = new FixedStep(60f, 5);
        assertEquals(5, step.advance(10_000_000_000L)); //10 seconds would be 600 ticks
        assertEquals(0, step.pendingNanos());
        assertEquals(1, step.advance(step.stepNanos()));
    }

    @Test void zeroAndNegativeNeverTick(){
        FixedStep step = new FixedStep();
        assertEquals(0, step.advance(0));
        assertEquals(0, step.advance(-1));
    }

    @Test void logicalConversion(){
        assertEquals(60f, FixedStep.toLogicalTick(60, 60f), 0.0001f);
        assertEquals(60f, FixedStep.toLogicalTick(30, 30f), 0.0001f);
    }
}
