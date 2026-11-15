package orbital.core;

import arc.*;
import arc.files.*;
import arc.struct.*;
import orbital.io.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the persisted window-resolution preference and its desktop provider hook. */
class WindowPreferenceTest{
    private static final Fi directory = new Fi("../../tests/build/test_window_preference");

    @BeforeEach
    void freshSettings(){
        directory.deleteDirectory();
        directory.mkdirs();

        Settings settings = new Settings();
        settings.setJson(JsonIO.json);
        settings.setAppName("Orbital");
        settings.setDataDirectory(directory);
        Core.settings = settings;
        WindowPreference.provider = null;
    }

    @AfterEach
    void restoreSettings(){
        WindowPreference.provider = null;
        Core.settings = null;
    }

    @Test
    void defaultsToAutomaticSize(){
        assertEquals(WindowPreference.automatic, WindowPreference.value());
        assertNull(WindowPreference.selected());
    }

    @Test
    void readsAStoredResolution(){
        Core.settings.put(WindowPreference.key, "2560x1440");

        assertEquals("2560x1440", WindowPreference.value());
        assertEquals(new WindowResolution(2560, 1440), WindowPreference.selected());
    }

    @Test
    void invalidStoredValuesFallBackToAutomaticSize(){
        for(String value : new String[]{"", "auto", "automatic", "not-a-resolution", "0x1080"}){
            Core.settings.put(WindowPreference.key, value);
            assertEquals(WindowPreference.automatic, WindowPreference.value(), value);
            assertNull(WindowPreference.selected(), value);
        }
    }

    @Test
    void mergesDuplicateModesAndKeepsTheHighestRefreshRate(){
        WindowPreference.provider = provider(
            new WindowResolution(1920, 1080, 60),
            new WindowResolution(2560, 1440, 60),
            new WindowResolution(1920, 1080, 144),
            new WindowResolution(2560, 1440)
        );

        Seq<WindowResolution> available = WindowPreference.available();

        assertEquals(2, available.size);
        assertEquals(new WindowResolution(2560, 1440, 60), available.get(0));
        assertEquals(new WindowResolution(1920, 1080, 144), available.get(1));
    }

    @Test
    void ordersTheLargestModeFirst(){
        WindowPreference.provider = provider(
            new WindowResolution(1280, 720),
            new WindowResolution(1920, 1080),
            new WindowResolution(1600, 1200),
            new WindowResolution(1920, 1200)
        );

        Seq<WindowResolution> available = WindowPreference.available();

        assertEquals(Seq.with(
            new WindowResolution(1920, 1200),
            new WindowResolution(1920, 1080),
            new WindowResolution(1600, 1200),
            new WindowResolution(1280, 720)
        ), available);
    }

    @Test
    void ignoresNullModesAndProviderFailures(){
        WindowPreference.provider = provider(new WindowResolution(1920, 1080), null);
        assertEquals(Seq.with(new WindowResolution(1920, 1080)), WindowPreference.available());

        WindowPreference.provider = new WindowPreference.Provider(){
            @Override
            public Seq<WindowResolution> resolutions(){
                throw new IllegalStateException("display unavailable");
            }

            @Override
            public boolean apply(WindowResolution resolution){
                return true;
            }
        };
        assertTrue(WindowPreference.available().isEmpty());
    }

    @Test
    void appliesThroughTheDesktopProvider(){
        boolean[] called = {false};
        WindowResolution[] applied = {new WindowResolution(1, 1)};
        WindowPreference.provider = new WindowPreference.Provider(){
            @Override
            public Seq<WindowResolution> resolutions(){
                return new Seq<>();
            }

            @Override
            public boolean apply(WindowResolution resolution){
                called[0] = true;
                applied[0] = resolution;
                return true;
            }
        };

        WindowResolution resolution = new WindowResolution(1920, 1080);
        assertTrue(WindowPreference.apply(resolution));
        assertTrue(called[0]);
        assertEquals(resolution, applied[0]);

        assertTrue(WindowPreference.apply(null));
        assertNull(applied[0], "a null resolution must reset the provider to automatic sizing");
    }

    @Test
    void reportsFailureWhenNoProviderCanApply(){
        assertFalse(WindowPreference.apply(new WindowResolution(1920, 1080)));

        WindowPreference.provider = new WindowPreference.Provider(){
            @Override
            public Seq<WindowResolution> resolutions(){
                return new Seq<>();
            }

            @Override
            public boolean apply(WindowResolution resolution){
                throw new IllegalStateException("window not ready");
            }
        };
        assertFalse(WindowPreference.apply(new WindowResolution(1920, 1080)));
    }

    private static WindowPreference.Provider provider(WindowResolution... resolutions){
        return new WindowPreference.Provider(){
            @Override
            public Seq<WindowResolution> resolutions(){
                return Seq.with(resolutions);
            }

            @Override
            public boolean apply(WindowResolution resolution){
                return true;
            }
        };
    }
}
