package orbital.diagnostics;

import arc.*;
import arc.util.*;
import orbital.core.*;

/**
 * Opt-in periodic dump of every {@link PerfCounter} to the log, used to measure engine changes on
 * slow hardware. Enable with {@code -Dorbital.cpuprofile=1} (or pass a number of seconds between
 * samples, e.g. {@code -Dorbital.cpuprofile=3}).
 *
 * <p>The listener is deliberately registered as an {@link arc.ApplicationListener} instead of an
 * engine event: it must also run while a sector is loading or the player is in the menu, where the
 * gameplay update events do not fire.</p>
 */
public final class CpuProfiler{
    private static final int defaultIntervalSeconds = 2;

    private CpuProfiler(){
    }

    /** Starts logging if the {@code orbital.cpuprofile} system property is set. */
    public static void maybeStart(){
        String prop = System.getProperty("orbital.cpuprofile");
        if(prop == null) return;

        int seconds = defaultIntervalSeconds;
        try{
            int parsed = Integer.parseInt(prop);
            if(parsed > 0) seconds = parsed;
        }catch(NumberFormatException ignored){
        }

        final int intervalFrames = Math.max(1, seconds * 60);
        final int[] frame = {0};
        Core.app.addListener(new arc.ApplicationListener(){
            @Override public void update(){
                if(++frame[0] % intervalFrames != 0) return;

                StringBuilder line = new StringBuilder("[cpuprofile] frame=")
                    .append(Core.graphics.getFrameId()).append(" fps=")
                    .append(Core.graphics.getFramesPerSecond());
                for(PerfCounter counter : PerfCounter.all){
                    line.append(' ').append(counter.name()).append('=')
                        .append(String.format("%.2f", counter.valueMs()));
                }
                Log.info(line.toString());
            }
        });
        Log.info("[cpuprofile] enabled, sampling every ~@s", seconds);
    }
}
