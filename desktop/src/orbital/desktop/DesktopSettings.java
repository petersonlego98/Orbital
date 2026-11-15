package orbital.desktop;

import arc.*;
import arc.files.*;
import arc.util.*;
import orbital.core.*;
import orbital.io.*;

import java.io.*;

import static orbital.Vars.*;

/** Reads desktop settings before the live {@link arc.Settings} instance has been loaded. */
public final class DesktopSettings{
    private DesktopSettings(){
    }

    /** Directory used by {@link orbital.Vars#loadSettings()}, resolved without {@code Core.files}. */
    public static Fi dataDirectory(){
        String override = System.getProperty("orbital.data.dir", OS.env("ORBITAL_DATA_DIR"));
        if(override != null) return new Fi(override);

        // Steam keeps settings next to the executable; SdlFi maps a relative path onto the working
        // directory, which is what Core.files.local("saves/") resolves to as well.
        return Version.isSteam ? new Fi("saves") : new Fi(OS.getAppDataDirectoryString(appName));
    }

    /**
     * Loads a throwaway settings object from a directory. The live settings are not modified and
     * no backup copy is written.
     */
    public static Settings load(Fi directory) throws IOException{
        Settings settings = new Settings();
        settings.setJson(JsonIO.json);
        settings.setAppName(appName);
        settings.setDataDirectory(directory);

        Fi file = directory.child("settings.bin");
        if(file.exists()) settings.loadValues(file);
        return settings;
    }
}
