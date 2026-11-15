package orbital.core;

import arc.*;
import arc.files.*;
import orbital.io.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the automatic DPI scale preference and its platform-specific provider hook. */
class DpiPreferenceTest{
    private static final Fi directory = new Fi("../../tests/build/test_dpi_preference");

    @BeforeEach
    void freshSettings(){
        directory.deleteDirectory();
        directory.mkdirs();

        Settings settings = new Settings();
        settings.setJson(JsonIO.json);
        settings.setAppName("Orbital");
        settings.setDataDirectory(directory);
        Core.settings = settings;
        DpiPreference.scaleProvider = null;
    }

    @AfterEach
    void restoreSettings(){
        DpiPreference.scaleProvider = null;
        Core.settings = null;
    }

    @Test
    void isUnavailableWithoutADesktopProvider(){
        assertFalse(DpiPreference.available());
        assertEquals(125, DpiPreference.recommendedScale(125));
    }

    @Test
    void returnsTheDetectedScale(){
        DpiPreference.scaleProvider = () -> 150;

        assertTrue(DpiPreference.available());
        assertEquals(150, DpiPreference.recommendedScale(100));
    }

    @Test
    void rejectsUnrealisticOrFailingScales(){
        DpiPreference.scaleProvider = () -> 10;
        assertEquals(100, DpiPreference.recommendedScale(100));

        DpiPreference.scaleProvider = () -> 400;
        assertEquals(125, DpiPreference.recommendedScale(125));

        DpiPreference.scaleProvider = () -> {
            throw new IllegalStateException("DPI unavailable");
        };
        assertEquals(100, DpiPreference.recommendedScale(100));
    }

    @Test
    void automaticScalingIsEnabledByDefault(){
        assertTrue(DpiPreference.automatic());
    }

    @Test
    void storesTheAutomaticScalingChoice(){
        DpiPreference.setAutomatic(false);
        assertFalse(DpiPreference.automatic());
        assertFalse(Core.settings.getBool(DpiPreference.key, true));

        DpiPreference.setAutomatic(true);
        assertTrue(DpiPreference.automatic());
        assertTrue(Core.settings.getBool(DpiPreference.key, false));
    }

    @Test
    void remembersTheManualScale(){
        assertEquals(100, DpiPreference.manualScale(100));

        DpiPreference.setManualScale(140);
        assertEquals(140, DpiPreference.manualScale(100));

        DpiPreference.setManualScale(10);
        assertEquals(140, DpiPreference.manualScale(100), "out-of-range values must not overwrite the stored scale");

        DpiPreference.setManualScale(400);
        assertEquals(140, DpiPreference.manualScale(100), "out-of-range values must not overwrite the stored scale");
    }

    @Test
    void settingChangesAreIgnoredWithoutLiveSettings(){
        Core.settings = null;

        assertFalse(DpiPreference.automatic());
        DpiPreference.setAutomatic(true);
        assertFalse(DpiPreference.automatic());
    }
}
