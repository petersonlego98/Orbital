package orbital.core;

import arc.func.*;
import arc.util.*;

import static arc.Core.*;

/**
 * Shared preference used to keep the interface at a consistent physical size on high-DPI displays.
 *
 * <p>The desktop and Android launchers each install a scale provider before the client is created.
 * The core then uses that value as the default UI scale, while still allowing the user to override
 * it with the regular UI-scale slider.</p>
 */
public final class DpiPreference{
    /** Setting that controls whether the detected DPI scale is applied automatically. */
    public static final String key = "autodpi";
    /** Last scale chosen manually, restored when automatic scaling is turned off. */
    public static final String manualScaleKey = "uiscale.manual";
    /** DPI used by Windows for a 100% interface scale. */
    public static final int baselineDpi = 96;
    /** Density (dpi / 160) of the Android device where the old fixed 125% default fit well. */
    public static final float androidReferenceDensity = 420f / 160f;
    /** Arc's quantised density factor times that 125% default on the reference device. */
    public static final float androidReferenceScale = 2f * 1.25f;
    /** Range accepted by the UI-scale slider. */
    public static final int minScale = 25, maxScale = 300;

    /**
     * Supplies the recommended UI scale as a percentage. It is installed by the desktop or Android
     * launcher; null means that no DPI-aware scale is available.
     */
    public static @Nullable Intp scaleProvider;

    private DpiPreference(){
    }

    /** True when a desktop-specific DPI scale provider is available. */
    public static boolean available(){
        return scaleProvider != null;
    }

    /**
     * Returns the provider's recommended UI scale, or {@code fallback} when no provider is
     * installed or the provider fails.
     */
    public static int recommendedScale(int fallback){
        if(scaleProvider == null) return fallback;

        try{
            int scale = scaleProvider.get();
            if(scale < minScale || scale > maxScale) return fallback;
            return scale;
        }catch(Throwable ignored){
            return fallback;
        }
    }

    /** Stores whether automatic DPI scaling should be used on the next launch. */
    public static void setAutomatic(boolean automatic){
        if(settings != null) settings.put(key, automatic);
    }

    /** Whether automatic DPI scaling is enabled. */
    public static boolean automatic(){
        return settings != null && settings.getBool(key, true);
    }

    /** Last UI scale chosen by the user, or {@code fallback} when none was stored. */
    public static int manualScale(int fallback){
        int scale = settings == null ? fallback : settings.getInt(manualScaleKey, fallback);
        return scale < minScale || scale > maxScale ? fallback : scale;
    }

    /**
     * Android UI-scale recommendation. Arc already multiplies the mobile scene by a density factor
     * quantised to 0.5 steps (and clamped at 1). This returns the product that makes the final
     * scale track the real density, so buttons keep the same physical size on every display.
     */
    public static int recommendedAndroidScale(float density, float addition){
        float arcBase = Math.max(1f, (float)Math.round((density / 1.5f + addition) / 0.5d) * 0.5f);
        float target = density * androidReferenceScale / androidReferenceDensity;
        int percent = Math.round(target / arcBase * 100f);
        return Math.max(100, Math.min(200, percent));
    }

    /** Remembers a manual UI scale so disabling automatic DPI can restore it. */
    public static void setManualScale(int scale){
        if(settings != null && scale >= minScale && scale <= maxScale){
            settings.put(manualScaleKey, scale);
        }
    }
}
