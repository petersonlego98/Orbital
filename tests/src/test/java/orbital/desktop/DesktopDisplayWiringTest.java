package orbital.desktop;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the startup wiring that has to happen before SDL creates the window. The launcher cannot
 * be instantiated in the headless test harness, so this verifies the relevant source order.
 */
class DesktopDisplayWiringTest{
    private static final Path launcher = Paths.get("../../desktop/src/orbital/desktop/DesktopLauncher.java");

    @Test
    void installsDpiAwarenessAndProvidersBeforeCreatingTheWindow(){
        String source = read();

        int dpiAwareness = source.indexOf("WindowsDpiSupport.install()");
        int dpiProvider = source.indexOf("DpiPreference.scaleProvider = WindowsDpiSupport::recommendedUiScale");
        int displayProvider = source.indexOf("WindowPreference.provider = new DesktopDisplayProvider()");
        int window = source.indexOf("new SdlApplication(");

        assertTrue(dpiAwareness >= 0, "the launcher must install Windows DPI awareness");
        assertTrue(dpiProvider > dpiAwareness, "the DPI provider must be installed after DPI awareness");
        assertTrue(displayProvider >= 0, "the launcher must install the desktop display provider");
        assertTrue(window > dpiProvider, "DPI awareness must be enabled before SDL creates the window");
        assertTrue(window > displayProvider, "the display provider must be ready before the window is created");
    }

    @Test
    void usesTheStoredResolutionAndLogicalCoordinates(){
        String source = read();

        assertTrue(source.contains("WindowResolution storedResolution = WindowPreferences.load()"),
            "the stored resolution must be read before the window exists");
        assertTrue(source.contains("maximized = preferredResolution == null"),
            "a stored resolution must not start maximized");
        assertTrue(source.contains("width = preferredResolution == null ? 900 : preferredResolution.width"),
            "the stored width must reach the SDL window");
        assertTrue(source.contains("height = preferredResolution == null ? 700 : preferredResolution.height"),
            "the stored height must reach the SDL window");
        assertTrue(source.contains("hdpiMode = HdpiMode.logical"),
            "UI coordinates must stay logical while the drawable can be high-DPI");
    }

    /**
     * Command-line width/height switches are applied while building the config, after the persisted
     * size, so they keep taking precedence.
     */
    @Test
    void commandLineSizeStillOverridesTheStoredResolution(){
        String source = read();

        int storedWidth = source.indexOf("width = preferredResolution == null ? 900 : preferredResolution.width");
        int widthSwitch = source.indexOf("case \"width\" -> width = Strings.parseInt");

        assertTrue(storedWidth >= 0);
        assertTrue(widthSwitch > storedWidth, "the -width switch must be parsed after the stored value");
    }

    private static String read(){
        try{
            return Files.readString(launcher, StandardCharsets.UTF_8);
        }catch(Exception e){
            throw new AssertionError("cannot read " + launcher, e);
        }
    }
}
