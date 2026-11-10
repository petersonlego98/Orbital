package orbital.core;

import arc.*;
import arc.files.*;
import orbital.io.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the persisted renderer preference that backs Settings > Advanced Graphics.
 *
 * <p>{@code Core.settings} is replaced with a throwaway instance on a temporary directory, so the
 * real user settings are never touched.</p>
 */
class RendererPreferenceTest{
    //tests run with core/assets as the working directory, so the scratch data stays outside it
    private static final Fi directory = new Fi("../../tests/build/test_renderer_preference");
    private static final String file = "settings.bin";

    @BeforeEach
    void freshSettings(){
        directory.deleteDirectory();
        directory.mkdirs();

        Settings settings = new Settings();
        settings.setJson(JsonIO.json);
        settings.setAppName("Orbital");
        settings.setDataDirectory(directory);
        Core.settings = settings;
    }

    @AfterEach
    void restoreSettings(){
        Core.settings = null;
        RendererPreference.vulkanProbe = null;
    }

    @Test
    void defaultsToOpenGl(){
        assertEquals(RendererPreference.opengl, RendererPreference.get());
        assertFalse(RendererPreference.vulkanRequested());
    }

    @Test
    void storesAndReadsBackTheRequestedBackend(){
        RendererPreference.set(RendererPreference.vulkan);
        assertEquals(RendererPreference.vulkan, RendererPreference.get());
        assertTrue(RendererPreference.vulkanRequested());

        RendererPreference.set(RendererPreference.opengl);
        assertEquals(RendererPreference.opengl, RendererPreference.get());
        assertFalse(RendererPreference.vulkanRequested());
    }

    @Test
    void survivesASettingsRoundTrip(){
        Core.settings.load();
        RendererPreference.set(RendererPreference.vulkan);
        Core.settings.forceSave();

        assertTrue(directory.child(file).exists());

        Settings reloaded = new Settings();
        reloaded.setJson(JsonIO.json);
        reloaded.setAppName("Orbital");
        reloaded.setDataDirectory(directory);
        reloaded.load();

        assertEquals(RendererPreference.vulkan, RendererPreference.normalize(reloaded.getString(RendererPreference.key)));
    }

    @Test
    void unexpectedValuesFallBackToOpenGl(){
        assertFallback(null);
        assertFallback("");
        assertFallback("   ");
        assertFallback("vulcan");
        assertFallback("metal");
        assertFallback("direct3d");
    }

    @Test
    void vulkanIsAcceptedInEveryLetterCase(){
        for(String value : new String[]{"vulkan", "Vulkan", "VULKAN", " vulKan "}){
            assertEquals(RendererPreference.vulkan, RendererPreference.normalize(value), value);
        }
    }

    @Test
    void capabilityProbeIsOptional(){
        assertFalse(RendererPreference.vulkanUsable(), "no probe installed means unavailable");

        RendererPreference.vulkanProbe = () -> false;
        assertFalse(RendererPreference.vulkanUsable());

        RendererPreference.vulkanProbe = () -> true;
        assertTrue(RendererPreference.vulkanUsable());
    }

    private void assertFallback(String value){
        assertEquals(RendererPreference.opengl, RendererPreference.normalize(value), "'" + value + "' must not select a backend");
    }
}
