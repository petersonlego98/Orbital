package orbital.desktop.vulkan;

import java.io.*;

import arc.*;
import arc.files.*;
import arc.util.*;
import orbital.core.*;
import orbital.core.RendererPreference;
import orbital.io.*;

import static orbital.Vars.appName;

/**
 * Reads {@link RendererPreference} before {@code Core.settings} exists.
 *
 * <p>The graphics backend has to be known before the window is created - {@code SdlApplication}'s
 * constructor creates the OpenGL context - so at launcher time {@code Core.settings} is still null
 * and the value has to come from the settings file itself. This class only ever reads: it builds a
 * throwaway {@link Settings}, points it at the directory {@link orbital.Vars#loadSettings()} would
 * use, and asks for the one key it needs.</p>
 */
public final class VulkanPreferences{
    private static @Nullable String cached;

    private VulkanPreferences(){
    }

    /** Resolves the preference once and caches it; called before {@link VulkanBackend#select}. */
    public static String load(){
        return load(dataDirectory());
    }

    /** Resolves the preference for one settings directory and caches it. */
    public static String load(Fi directory){
        cached = read(directory);
        return cached;
    }

    /** Cached preference, read on first use when {@link #load()} was never called. */
    public static String value(){
        if(cached == null) cached = read(dataDirectory());
        return cached;
    }

    /** Drops the cached value so the next read goes to disk again. */
    public static void reset(){
        cached = null;
    }

    /** True when the stored preference asks for Vulkan. */
    public static boolean vulkanRequested(){
        return RendererPreference.vulkan.equalsIgnoreCase(value());
    }

    /**
     * Reads one key from a settings directory without touching the live settings.
     *
     * @return the normalized preference, or {@link RendererPreference#opengl} when the file is
     * missing, unreadable or holds something unexpected
     */
    public static String read(Fi directory){
        Settings settings = new Settings();
        settings.setJson(JsonIO.json);
        settings.setAppName(appName);
        settings.setDataDirectory(directory);

        Fi file = directory.child("settings.bin");
        if(!file.exists()) return RendererPreference.opengl;

        try{
            //loadValues(Fi) instead of load(): it is read-only, while load() refreshes the backup copy.
            settings.loadValues(file);
        }catch(Throwable error){
            Log.debug("[Vulkan] Could not read the stored renderer preference: @", error.toString());
            return RendererPreference.opengl;
        }

        return RendererPreference.normalize(settings.getString(RendererPreference.key, RendererPreference.opengl));
    }

    /** The data directory {@link orbital.Vars#loadSettings()} uses, resolved without {@code Core.files}. */
    private static Fi dataDirectory(){
        String override = System.getProperty("orbital.data.dir", OS.env("ORBITAL_DATA_DIR"));
        if(override != null) return new Fi(override);

        //Steam keeps settings next to the executable; SdlFi maps a relative path onto the working
        //directory, which is what Core.files.local("saves/") resolves to as well.
        return Version.isSteam ? new Fi("saves") : new Fi(OS.getAppDataDirectoryString(appName));
    }
}
