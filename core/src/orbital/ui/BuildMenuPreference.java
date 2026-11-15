package orbital.ui;

import arc.*;

/** Shared access to the construction-menu display preference. */
public final class BuildMenuPreference{
    /** Setting key for the classic, fixed construction menu. */
    public static final String oldMenuKey = "oldbuildmenu";

    private BuildMenuPreference(){
    }

    /** @return whether the classic fixed construction menu should be shown. */
    public static boolean oldMenuEnabled(){
        return Core.settings != null && Core.settings.getBool(oldMenuKey, false);
    }
}
