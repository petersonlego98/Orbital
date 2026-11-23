package orbital.android;

import android.content.*;
import android.util.DisplayMetrics;
import arc.util.*;
import orbital.core.*;

/**
 * Android half of the automatic DPI scaling shared with the Windows launcher.
 *
 * <p>Reads the display density and installs the shared scale provider. The actual calculation
 * lives in {@link DpiPreference} so it can be tested without the Android runtime.</p>
 */
public final class AndroidDpiSupport{
    private AndroidDpiSupport(){
    }

    /**
     * Detects the display density and installs the shared scale provider. Must run before the
     * client is created: {@link orbital.Vars} reads the recommendation while loading settings.
     *
     * @param tabletAddition the same value passed to {@code Scl.setAddition} by the launcher
     */
    public static void install(Context context, boolean tabletAddition){
        try{
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int dpi = metrics != null ? metrics.densityDpi : 0;
            float density = dpi > 0 ? dpi / 160f : (metrics != null ? metrics.density : 0f);
            if(density <= 0f){
                Log.warn("[Android] no usable display density reported; keeping fixed UI scale");
                return;
            }

            final float detectedDensity = density;
            final float addition = tabletAddition ? 0.5f : 0f;
            DpiPreference.scaleProvider = () -> DpiPreference.recommendedAndroidScale(detectedDensity, addition);

            Log.info("[Android] display DPI @, automatic UI scale @%", dpi,
                DpiPreference.recommendedAndroidScale(detectedDensity, addition));
        }catch(Throwable e){
            Log.warn("[Android] could not read display DPI, keeping fixed UI scale: @", e.getMessage());
        }
    }
}
