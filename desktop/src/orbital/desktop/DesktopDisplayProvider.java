package orbital.desktop;

import arc.*;
import arc.backend.sdl.*;
import arc.backend.sdl.jni.*;
import arc.struct.*;
import arc.util.*;
import orbital.core.*;

import java.awt.*;

/** Enumerates display modes and applies the selected size to the SDL window. */
public final class DesktopDisplayProvider implements WindowPreference.Provider{
    @Override
    public Seq<WindowResolution> resolutions(){
        Seq<WindowResolution> result = new Seq<>();

        try{
            GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            for(GraphicsDevice device : environment.getScreenDevices()){
                DisplayMode current = device.getDisplayMode();
                if(current != null){
                    result.add(new WindowResolution(current.getWidth(), current.getHeight(), current.getRefreshRate()));
                }

                for(DisplayMode mode : device.getDisplayModes()){
                    if(mode == null || mode.getWidth() <= 0 || mode.getHeight() <= 0) continue;
                    if(current != null && mode.getBitDepth() != current.getBitDepth()) continue;
                    result.add(new WindowResolution(mode.getWidth(), mode.getHeight(), mode.getRefreshRate()));
                }
            }
        }catch(Throwable error){
            Log.debug("[Desktop] Could not enumerate display modes: @", error.toString());
        }

        // The logical window size also covers virtual displays that AWT does not report as regular
        // screen devices. The drawable is deliberately not used: it is in physical pixels on a
        // high-DPI display, while the window size (and therefore every resolution here) is logical.
        if(Core.graphics != null && Core.graphics.getWidth() > 0 && Core.graphics.getHeight() > 0){
            result.add(new WindowResolution(Core.graphics.getWidth(), Core.graphics.getHeight()));
        }

        return result;
    }

    @Override
    public boolean apply(@Nullable WindowResolution resolution){
        if(Core.graphics == null) return false;

        // A display mode cannot be changed while SDL owns a borderless fullscreen window. The
        // selected size is a windowed size, so leave fullscreen before applying it.
        if(Core.graphics.isFullscreen()){
            Core.graphics.setFullscreen(false);
            if(Core.settings != null) Core.settings.put("fullscreen", false);
        }

        if(Core.app instanceof SdlApplication application){
            long window = application.getWindow();
            if(window != 0) SDL.SDL_RestoreWindow(window);
            if(resolution == null){
                if(window != 0) SDL.SDL_MaximizeWindow(window);
            }else{
                Core.graphics.setWindowSize(resolution.width, resolution.height);
            }
            return true;
        }

        if(resolution != null){
            Core.graphics.setWindowSize(resolution.width, resolution.height);
        }
        return resolution != null;
    }
}
