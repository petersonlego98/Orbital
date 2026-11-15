package orbital.desktop;

import arc.*;
import arc.util.*;
import org.lwjgl.system.windows.*;
import orbital.core.*;

import java.awt.*;
import java.awt.geom.*;

/**
 * Windows DPI integration.
 *
 * <p>The game window is made per-monitor aware before SDL creates it. The detected scale is then
 * used as the default UI scale, keeping controls at approximately the same physical size on
 * high-resolution/high-DPI displays.</p>
 */
public final class WindowsDpiSupport{
    private static boolean installed;

    private WindowsDpiSupport(){
    }

    /**
     * Enables per-monitor DPI awareness on the launcher thread. This must run before SDL creates
     * the window; otherwise Windows may bitmap-stretch the first frame.
     */
    public static void install(){
        if(installed || !OS.isWindows) return;
        installed = true;

        try{
            long previous = User32.SetThreadDpiAwarenessContext(
                User32.DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
            long current = User32.GetThreadDpiAwarenessContext();
            int awareness = User32.GetAwarenessFromDpiAwarenessContext(current);
            Log.info("[Windows] DPI awareness enabled (context @, previous @, awareness @).",
                Long.toHexString(current), Long.toHexString(previous), awareness);
        }catch(Throwable error){
            // Older Windows versions may not expose the per-monitor-v2 API. The game remains
            // usable, but SDL's system-DPI fallback is used instead.
            Log.warn("[Windows] Per-monitor DPI awareness could not be enabled: @", error.toString());
        }
    }

    /**
     * Returns the recommended UI scale as a percentage of the normal 96-DPI size.
     */
    public static int recommendedUiScale(){
        int scale = 100;

        // When SDL exposes a high-DPI drawable, the ratio between its physical and logical size is
        // the most accurate value for the monitor that currently owns the window.
        if(Core.graphics != null && Core.graphics.getWidth() > 0){
            int logicalWidth = Core.graphics.getWidth();
            int physicalWidth = Core.graphics.getBackBufferWidth();
            if(physicalWidth > 0 && logicalWidth > 0){
                int drawableScale = Math.round(physicalWidth * 100f / logicalWidth);
                if(drawableScale >= 75 && drawableScale <= 300) scale = drawableScale;
            }
        }

        // If the backend does not expose a high-DPI drawable, AWT still reports the Windows
        // display transform (1.5 at 150%, for example).
        if(scale == 100){
            try{
                GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
                GraphicsDevice device = environment.getDefaultScreenDevice();
                GraphicsConfiguration configuration = device.getDefaultConfiguration();
                AffineTransform transform = configuration.getDefaultTransform();
                int awtScale = (int)Math.round(transform.getScaleX() * 100f);
                if(awtScale >= 75 && awtScale <= 300) scale = awtScale;
            }catch(Throwable ignored){
                // AWT is optional in a minimal desktop runtime.
            }
        }

        // Last-resort fallback for Windows versions that expose neither a high-DPI drawable nor
        // a useful AWT transform.
        if(scale == 100){
            try{
                int dpi = User32.GetDpiForSystem();
                if(dpi > 0){
                    int nativeScale = Math.round(dpi * 100f / DpiPreference.baselineDpi);
                    if(nativeScale >= 75 && nativeScale <= 300) scale = nativeScale;
                }
            }catch(Throwable ignored){
                // Keep the 100% default.
            }
        }

        return Math.max(75, Math.min(300, scale));
    }
}
