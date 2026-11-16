package orbital.ui;

import orbital.engine.threading.OrbitalThreadConfig;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the worker-count row in Settings &gt; Advanced Graphics.
 *
 * <p>The dialog itself needs a live scene and atlas, so - like the other dialog tests - this one
 * verifies the source wiring and the bundle keys the row renders.</p>
 */
class OrbitalEngineSettingsTest{
    private static final Path bundles = Paths.get("bundles");
    private static final Path dialog = Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java");

    @Test
    void workerRowIsRegisteredInAdvancedGraphics() throws Exception{
        String source = Files.readString(dialog, StandardCharsets.UTF_8);

        assertTrue(Pattern.compile("advancedgraphics\\.sliderPref\\(OrbitalThreadConfig\\.key").matcher(source).find(),
            "the worker slider must live in the advanced graphics table");
        assertTrue(Pattern.compile("OrbitalThreadConfig\\.key,\\s*OrbitalThreadConfig\\.automatic").matcher(source).find(),
            "the worker slider must default to automatic");
        assertTrue(source.contains("OrbitalEngine.reconfigure(value)"),
            "changing the slider must apply the new worker count");
    }

    @Test
    void workerKeysExistInEveryBundledLocale() throws Exception{
        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            Path file = bundles.resolve("bundle" + locale + ".properties");
            assertTrue(Files.isRegularFile(file), "missing bundle " + file);

            String content = Files.readString(file, StandardCharsets.UTF_8);
            for(String suffix : new String[]{".name", ".description", ".auto", ".workers"}){
                String key = "setting." + OrbitalThreadConfig.key + suffix;
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }

    @Test
    void automaticDefaultIsZeroAndHasNoArtificialCoreCeiling(){
        assertEquals(0, OrbitalThreadConfig.automatic);
        assertTrue(OrbitalThreadConfig.maxSelectable() >= 32);
        assertTrue(OrbitalThreadConfig.maxWorkers >= 32);
    }
}
