package orbital.ui;

import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that the "Show FPS &amp; Ping" toggle in Settings &gt; Graphics starts switched on.
 *
 * <p>The dialog needs a live scene and atlas, which the headless harness does not have, so the
 * preference is verified where it is declared: {@link orbital.ui.dialogs.SettingsMenuDialog}
 * registers every graphics checkbox through {@code checkPref}, and the value passed there is the
 * default {@code Arc}'s {@code Settings} reports for a player who never touched the toggle.</p>
 */
class FpsCounterPreferenceTest{
    private static final Path bundles = Paths.get("bundles");
    private static final Path dialog = Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java");

    @Test
    void fpsCounterIsEnabledByDefault() throws Exception{
        String source = Files.readString(dialog, StandardCharsets.UTF_8);

        assertTrue(Pattern.compile("checkPref\\(\"fps\",\\s*true\\)").matcher(source).find(),
            "the FPS/Ping counter must be on unless the player turns it off");
        assertFalse(Pattern.compile("checkPref\\(\"fps\",\\s*false\\)").matcher(source).find(),
            "a second registration with the old default would fight the new one");
    }

    @Test
    void fpsCounterEntriesExistInBothBundles(){
        for(String locale : new String[]{"", "_pt_BR"}){
            Path file = bundles.resolve("bundle" + locale + ".properties");
            assertTrue(Files.isRegularFile(file), "missing bundle " + file);

            String content;
            try{
                content = Files.readString(file, StandardCharsets.UTF_8);
            }catch(java.io.IOException error){
                throw new AssertionError("cannot read " + file, error);
            }

            for(String key : new String[]{"setting.fps.name", "setting.fps.description"}){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }
}
