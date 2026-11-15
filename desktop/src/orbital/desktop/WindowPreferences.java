package orbital.desktop;

import arc.*;
import arc.util.*;
import orbital.core.*;

/** Reads the saved window resolution before the SDL window is created. */
public final class WindowPreferences{
    private WindowPreferences(){
    }

    /**
     * Loads the saved resolution. Null means automatic sizing or no valid stored value.
     */
    public static @Nullable WindowResolution load(){
        try{
            String value = DesktopSettings.load(DesktopSettings.dataDirectory())
                .getString(WindowPreference.key, WindowPreference.automatic);
            return WindowResolution.parse(value);
        }catch(Throwable error){
            Log.debug("[Windows] Could not read the stored window resolution: @", error.toString());
            return null;
        }
    }
}
