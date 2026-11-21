package orbital.net.online;

/**
 * Global switch for the authoritative online mode.
 *
 * <p>While disabled (the default), the game runs the unchanged legacy variable-step loop and the
 * legacy ArcNet snapshots. While enabled, the tick advances in fixed steps and the server is the
 * only authority: clients send commands and receive events with an authoritative seq/tick.</p>
 *
 * <p>The switch is deliberately a system property, not a settings entry: it must be chosen before
 * the world starts, and a server and its clients must agree on it before joining.</p>
 */
public final class AuthoritativeMode{
    private static final boolean enabled = System.getProperty("orbital.authoritative") != null;
    private static final float hz = parseFloat(System.getProperty("orbital.tickrate"), FixedStep.DEFAULT_HZ);
    private static final FixedStep clock = new FixedStep(hz, FixedStep.DEFAULT_MAX_CATCHUP);

    private AuthoritativeMode(){
    }

    /** @return whether authoritative fixed-step mode is active for this process. */
    public static boolean enabled(){
        return enabled;
    }

    /** @return the configured tick rate (default 60). */
    public static float hz(){
        return hz;
    }

    /** @return the shared fixed-step clock. */
    public static FixedStep clock(){
        return clock;
    }

    /** Resets the accumulator on a new game/session. */
    public static void reset(){
        clock.reset();
    }

    private static float parseFloat(String value, float fallback){
        if(value == null) return fallback;
        try{
            float parsed = Float.parseFloat(value);
            return parsed > 0 && Float.isFinite(parsed) ? parsed : fallback;
        }catch(NumberFormatException invalid){
            return fallback;
        }
    }
}
