package orbital.desktop;

import arc.*;
import arc.files.*;
import orbital.core.*;
import orbital.io.*;

import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the read-only settings snapshot used before the SDL window is created. */
class DesktopSettingsTest{
    private static final Fi directory = new Fi("../../tests/build/test_desktop_settings");
    private static final String dataDirectoryProperty = "orbital.data.dir";
    private String previousDataDirectory;

    @BeforeEach
    void freshSettings(){
        directory.deleteDirectory();
        directory.mkdirs();

        previousDataDirectory = System.getProperty(dataDirectoryProperty);
        System.setProperty(dataDirectoryProperty, directory.file().getAbsolutePath());
    }

    @AfterEach
    void restoreSettings(){
        if(previousDataDirectory == null){
            System.clearProperty(dataDirectoryProperty);
        }else{
            System.setProperty(dataDirectoryProperty, previousDataDirectory);
        }
    }

    @Test
    void loadsStoredValuesWithoutCreatingBackups() throws Exception{
        save("windowresolution", "2560x1440");
        Path settingsFile = directory.child("settings.bin").file().toPath();
        byte[] before = java.nio.file.Files.readAllBytes(settingsFile);

        Settings settings = assertDoesNotThrow(() -> DesktopSettings.load(directory));

        assertEquals("2560x1440", settings.getString("windowresolution"));
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(settingsFile), "reading the settings must not rewrite the file");
        assertFalse(directory.child("settings_backup.bin").exists(), "reading the settings must not refresh the backup copy");
    }

    @Test
    void missingSettingsProduceDefaults(){
        Settings settings = assertDoesNotThrow(() -> DesktopSettings.load(directory));

        assertEquals(WindowPreference.automatic, settings.getString(WindowPreference.key, WindowPreference.automatic));
        assertFalse(directory.child("settings.bin").exists());
    }

    @Test
    void dataDirectoryHonoursTheLaunchOverride(){
        assertEquals(directory.file().getAbsolutePath(), DesktopSettings.dataDirectory().file().getAbsolutePath());
    }

    @Test
    void loadsTheStoredResolutionBeforeTheWindowExists(){
        save(WindowPreference.key, "1920x1080");

        assertEquals(new WindowResolution(1920, 1080), WindowPreferences.load());
    }

    @Test
    void automaticOrInvalidStoredResolutionsAreIgnored(){
        save(WindowPreference.key, WindowPreference.automatic);
        assertNull(WindowPreferences.load());

        save(WindowPreference.key, "invalid");
        assertNull(WindowPreferences.load());
    }

    @Test
    void missingSettingsMeanAutomaticSizing(){
        assertNull(WindowPreferences.load());
    }

    private static void save(String key, String value){
        Settings settings = new Settings();
        settings.setJson(JsonIO.json);
        settings.setAppName("Orbital");
        settings.setDataDirectory(directory);
        settings.load();
        settings.put(key, value);
        settings.forceSave();
    }

}
