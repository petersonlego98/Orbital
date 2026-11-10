package orbital.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

import orbital.core.RendererPreference;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the Settings > Advanced Graphics area: the bundle entries the renderer row displays and
 * the dialog wiring that registers it.
 *
 * <p>The dialog can only be built with a live scene and atlas, both of which the headless test
 * harness does not have, so - like the other dialog tests here - this one verifies the source and
 * the bundle instead.</p>
 */
class AdvancedGraphicsSettingsTest{
    private static final Path bundles = Paths.get("../../core/assets/bundles");
    private static final Path dialog = Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java");

    private static Set<String> keys;

    @BeforeAll
    static void loadBundles() throws Exception{
        keys = new HashSet<>();

        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            Path file = bundles.resolve("bundle" + locale + ".properties");
            assertTrue(Files.isRegularFile(file), "missing bundle " + file);

            for(String line : Files.readAllLines(file, StandardCharsets.UTF_8)){
                int split = line.indexOf('=');
                if(split > 0) keys.add(line.substring(0, split).trim());
            }
        }
    }

    @Test
    void theAreaExistsInEveryBundle(){
        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            assertTrue(keys.contains("settings.advancedgraphics"), "settings.advancedgraphics is missing from bundle" + locale);
        }
    }

    @Test
    void everyRendererRowEntryExistsInEveryBundle(){
        List<String> required = List.of(
        "setting." + RendererPreference.key + ".name",
        "setting." + RendererPreference.key + ".description",
        "setting." + RendererPreference.key + ".opengl",
        "setting." + RendererPreference.key + ".vulkan",
        "setting." + RendererPreference.key + ".available",
        "setting." + RendererPreference.key + ".unavailable",
        "settings." + RendererPreference.key + ".restart"
        );

        for(String key : required){
            assertTrue(keys.contains(key), "missing bundle entry: " + key);
        }
    }

    //the label of each button is "setting.<key>.<value>", so the stored values must match the
    //bundle suffixes exactly or the row shows raw keys
    @Test
    void bothOptionLabelsResolveFromTheStoredValues(){
        for(String value : new String[]{RendererPreference.opengl, RendererPreference.vulkan}){
            String key = "setting." + RendererPreference.key + "." + value;
            assertTrue(keys.contains(key), "the option value '" + value + "' has no label at '" + key + "'");
        }
    }

    //The row label is the short renderer name; the long warnings live in the change dialog, not in
    //the table - a wide label stretches the row and paints its text over the controls beside it.
    @Test
    void theRowLabelStaysShort(){
        String name = value("setting." + RendererPreference.key + ".name");

        assertFalse(name.isEmpty(), "the row label must not be empty");
        assertTrue(name.length() <= 40,
            "the row label must stay short enough for the table cell, but was " + name.length()
                + " characters: '" + name + "'");
    }

    @Test
    void theChangeDialogCarriesTheLongWarnings(){
        // Same reason as above: these strings are only shown in a scrollable info dialog.
        for(String key : new String[]{
            "setting." + RendererPreference.key + ".available",
            "setting." + RendererPreference.key + ".unavailable"
        }){
            assertTrue(value(key).length() > nameLength(),
                "the warning at '" + key + "' should explain what experimental means");
        }
    }

    private static int nameLength(){
        return value("setting." + RendererPreference.key + ".name").length();
    }

    @Test
    void theDialogRegistersTheRowForDesktopOnly(){
        String source = read(dialog);

        assertTrue(source.contains("advancedgraphics.rebuild()"), "the new table has to be rebuilt on resize");
        assertTrue(source.contains("@settings.advancedgraphics"), "the menu must offer the new area");
        assertTrue(source.contains("advancedgraphics.pref(new SettingsTable.OptionsSetting("), "the area must register the renderer row");
        assertTrue(source.contains("RendererPreference.key"), "the row must use the shared preference key");

        //Vulkan is desktop-only, so the row sits behind a platform guard with no other block in it
        assertTrue(Pattern.compile("if\\(!mobile\\)\\{[^{}]*?advancedgraphics\\.pref", Pattern.DOTALL)
        .matcher(source).find(), "the renderer row must be registered for desktop only");
    }

    private static String value(String key){
        return read(bundles.resolve("bundle.properties")).lines()
        .map(line -> line.split("=", 2))
        .filter(parts -> parts.length == 2 && parts[0].trim().equals(key))
        .map(parts -> parts[1].trim())
        .findFirst().orElseThrow(() -> new AssertionError("missing bundle entry: " + key));
    }

    private static String read(Path file){
        try{
            assertTrue(Files.isRegularFile(file), "missing file " + file);
            return Files.readString(file, StandardCharsets.UTF_8);
        }catch(Exception e){
            throw new AssertionError("cannot read " + file, e);
        }
    }
}