package orbital.net.online;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Input commands must round-trip and the buffer must dedupe/keep-last correctly. */
class InputCommandTest{
    @Test void roundTrip(){
        InputCommand original = new InputCommand(123L, 45.5f, -12.25f,
            0.75f, -1f, true, false, true);
        InputCommand decoded = assertDoesNotThrow(() -> InputCommand.decode(original.encode()));
        assertEquals(original, decoded);
    }

    @Test void flagsRoundTrip(){
        for(boolean shoot : new boolean[]{true, false}){
            for(boolean boost : new boolean[]{true, false}){
                for(boolean build : new boolean[]{true, false}){
                    InputCommand c = new InputCommand(1, 0, 0, 0, 0, shoot, boost, build);
                    InputCommand d = assertDoesNotThrow(() -> InputCommand.decode(c.encode()));
                    assertEquals(shoot, d.shooting());
                    assertEquals(boost, d.boosting());
                    assertEquals(build, d.building());
                }
            }
        }
    }

    @Test void bufferUsesLatestForTickAndFallsBack(){
        InputBuffer buffer = new InputBuffer();
        InputCommand first = new InputCommand(5, 1, 1, 0, 0, false, false, false);
        InputCommand latest = new InputCommand(5, 9, 9, 0, 0, true, false, false);
        buffer.accept(1, first);
        buffer.accept(1, latest);
        assertEquals(latest, buffer.consume(1, 5), "same tick must use the latest command");

        //No command for tick 6: reuse the most recent intention.
        assertEquals(latest, buffer.consume(1, 6));
        assertNull(buffer.consume(99, 7), "unknown player has no input");
    }

    @Test void discardBeforeBoundsMemory(){
        InputBuffer buffer = new InputBuffer();
        buffer.accept(1, new InputCommand(1, 0, 0, 0, 0, false, false, false));
        buffer.accept(1, new InputCommand(2, 0, 0, 0, 0, false, false, false));
        buffer.discardBefore(2);
        assertEquals(2, buffer.consume(1, 2).tick());
    }
}
