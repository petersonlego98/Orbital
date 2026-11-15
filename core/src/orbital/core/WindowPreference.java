package orbital.core;

import arc.struct.*;
import arc.util.*;

import static arc.Core.*;

/**
 * Persisted window-resolution preference and the desktop hook used to enumerate and apply display
 * modes.
 *
 * <p>The core module cannot depend on a desktop display API, so the launcher installs a
 * {@link Provider}. The settings menu uses that provider to show only modes supported by the
 * user's computer.</p>
 */
public final class WindowPreference{
    /** Settings key holding the selected window resolution. */
    public static final String key = "windowresolution";
    /** Value used when the operating system should choose the window size. */
    public static final String automatic = "auto";

    /** Desktop display API used by the advanced graphics settings. */
    public interface Provider{
        /** Returns the compatible display modes, preferably including the current mode. */
        Seq<WindowResolution> resolutions();

        /**
         * Applies a resolution to the game window. A null resolution restores the automatic,
         * operating-system-managed size.
         */
        boolean apply(@Nullable WindowResolution resolution);
    }

    /** Provider installed by the desktop launcher; null on platforms without display enumeration. */
    public static @Nullable Provider provider;

    private WindowPreference(){
    }

    /**
     * Returns compatible resolutions, with duplicate dimensions merged using the highest refresh
     * rate and the largest resolution first.
     */
    public static Seq<WindowResolution> available(){
        Seq<WindowResolution> result = new Seq<>();
        if(provider != null){
            try{
                Seq<WindowResolution> resolutions = provider.resolutions();
                if(resolutions != null) result.addAll(resolutions);
            }catch(Throwable ignored){
                result.clear();
            }
        }

        Seq<WindowResolution> unique = new Seq<>();
        for(WindowResolution resolution : result){
            if(resolution == null) continue;

            WindowResolution previous = null;
            for(WindowResolution existing : unique){
                if(existing.width == resolution.width && existing.height == resolution.height){
                    previous = existing;
                    break;
                }
            }

            if(previous == null){
                unique.add(resolution);
            }else if(resolution.refreshRate > previous.refreshRate){
                unique.remove(previous);
                unique.add(resolution);
            }
        }

        unique.sort((first, second) -> {
            long firstArea = (long)first.width * first.height;
            long secondArea = (long)second.width * second.height;
            int comparison = Long.compare(secondArea, firstArea);
            if(comparison != 0) return comparison;
            comparison = Integer.compare(second.width, first.width);
            if(comparison != 0) return comparison;
            return Integer.compare(second.height, first.height);
        });
        return unique;
    }

    /** Stored value, or {@link #automatic} when no valid value has been selected. */
    public static String value(){
        if(settings == null) return automatic;
        String value = settings.getString(key, automatic);
        if(automatic.equalsIgnoreCase(value)) return automatic;
        return WindowResolution.parse(value) == null ? automatic : value;
    }

    /** Parsed stored resolution, or null when automatic mode is selected. */
    public static @Nullable WindowResolution selected(){
        return WindowResolution.parse(value());
    }

    /**
     * Applies a resolution through the desktop provider. A null resolution selects automatic
     * sizing.
     */
    public static boolean apply(@Nullable WindowResolution resolution){
        if(provider == null) return false;

        try{
            return provider.apply(resolution);
        }catch(Throwable ignored){
            return false;
        }
    }
}
