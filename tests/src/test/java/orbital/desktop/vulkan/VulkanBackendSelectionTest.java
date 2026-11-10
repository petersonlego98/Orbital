package orbital.desktop.vulkan;

import arc.*;
import arc.files.*;
import orbital.core.*;
import orbital.core.RendererPreference;
import orbital.io.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how the desktop launcher turns the stored renderer preference into a backend choice.
 *
 * <p>The preference is written through a throwaway {@link Settings} and read back through
 * {@link VulkanPreferences#load(Fi)} - the same path {@code DesktopLauncher#main} uses before
 * {@code Core.settings} exists. Backend selection is asserted by hand: the port rendered broken
 * output when wired on a Vulkan-capable machine (2026-11-10), so every request must resolve to
 * OpenGL regardless of the drivers installed.</p>
 */
class VulkanBackendSelectionTest{
    //tests run with core/assets as the working directory, so the scratch data stays outside it
    private static final Fi directory = new Fi("../../tests/build/test_vulkan_preferences");
    private static final String[] noArgs = new String[0];

    @BeforeEach
    void freshDirectory(){
        VulkanPreferences.reset();
        directory.deleteDirectory();
        directory.mkdirs();
    }

    @AfterEach
    void clearCache(){
        VulkanPreferences.reset();
    }

    @Test
    void openGlIsChosenWhenNothingRequestsVulkan(){
        assertEquals(VulkanBackend.Type.OPENGL, VulkanBackend.select(noArgs, false));
    }

    @Test
    void aStoredVulkanPreferenceDoesNotSwitchTheRendererYet(){
        assertEquals(VulkanBackend.Type.OPENGL, VulkanBackend.select(noArgs, true));
    }

    @Test
    void theLaunchFlagDoesNotSwitchTheRendererYet(){
        assertEquals(VulkanBackend.Type.OPENGL, VulkanBackend.select(new String[]{VulkanBackend.OPTION}, false));
        assertEquals(VulkanBackend.Type.OPENGL, VulkanBackend.select(new String[]{VulkanBackend.OPTION}, true));
    }

    @Test
    void theRendererStaysReportedAsNotReady(){
        assertFalse(VulkanBackend.rendererWired());
        assertFalse(VulkanBackend.rendererReady());
    }

    @Test
    void launchArgumentsAreRecognized(){
        assertFalse(VulkanBackend.requested(noArgs));
        assertTrue(VulkanBackend.requested(new String[]{"-width", "800", VulkanBackend.OPTION}));

        assertFalse(VulkanBackend.checkRequested(noArgs));
        assertTrue(VulkanBackend.checkRequested(new String[]{VulkanBackend.CHECK_OPTION}));
        assertFalse(VulkanBackend.checkRequested(new String[]{VulkanBackend.OPTION}));
    }

    @Test
    void anEmptySettingsDirectoryMeansOpenGl(){
        assertEquals(RendererPreference.opengl, VulkanPreferences.load(directory));
        assertFalse(VulkanPreferences.vulkanRequested());
    }

    @Test
    void aStoredVulkanPreferenceIsReadBack(){
        write(RendererPreference.vulkan);

        assertEquals(RendererPreference.vulkan, VulkanPreferences.load(directory));
        assertTrue(VulkanPreferences.vulkanRequested());
    }

    @Test
    void aStoredOpenGlPreferenceIsReadBack(){
        write(RendererPreference.opengl);

        assertEquals(RendererPreference.opengl, VulkanPreferences.load(directory));
        assertFalse(VulkanPreferences.vulkanRequested());
    }

    @Test
    void anUnreadableSettingsFileMeansOpenGl(){
        directory.child("settings.bin").writeString("this is not a settings file");

        assertEquals(RendererPreference.opengl, VulkanPreferences.load(directory));
        assertFalse(VulkanPreferences.vulkanRequested());
    }

    @Test
    void aSettingsFileWithoutTheKeyMeansOpenGl(){
        write("uiscale", 100);

        assertEquals(RendererPreference.opengl, VulkanPreferences.load(directory));
        assertFalse(VulkanPreferences.vulkanRequested());
    }

    @Test
    void theSettingsFileIsOnlyRead(){
        write(RendererPreference.vulkan);
        byte[] before = directory.child("settings.bin").readBytes();

        VulkanPreferences.load(directory);

        assertArrayEquals(before, directory.child("settings.bin").readBytes(), "reading the preference must not rewrite the settings file");
        assertFalse(directory.child("settings_backup.bin").exists(), "reading the preference must not refresh the settings backup");
    }

    private void write(String value){
        write(RendererPreference.key, value);
    }

    private void write(String key, Object value){
        Settings settings = new Settings();
        settings.setJson(JsonIO.json);
        settings.setAppName("Orbital");
        settings.setDataDirectory(directory);
        settings.load();
        settings.put(key, value);
        settings.forceSave();
    }
}
