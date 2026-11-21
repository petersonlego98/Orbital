package orbital.net.online;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The switch defaults to off so existing multiplayer is untouched. */
class AuthoritativeModeTest{
    @Test void defaultsAreSafe(){
        assertFalse(AuthoritativeMode.enabled(), "authoritative mode must be opt-in");
        assertEquals(FixedStep.DEFAULT_HZ, AuthoritativeMode.hz(), 0.0001f);
        assertNotNull(AuthoritativeMode.clock());
    }

    @Test void resetClearsClock(){
        AuthoritativeMode.clock().advance(1_000_000_000L);
        AuthoritativeMode.reset();
        assertEquals(0, AuthoritativeMode.clock().pendingNanos());
    }
}
