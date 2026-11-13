import orbital.ui.fragments.HintsFragment;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance criteria for the part-test tutorial: every explanation the wizard and the hints
 * show must exist in the shipped bundles, and the player-facing copy must stay free of the
 * implementation vocabulary (hull, dorsal, loadout, pools...).
 *
 * <p>The properties files are read directly, like in {@code ControlsMenuTest}: the headless
 * test application does not always build a runtime bundle, and the shipped text is what needs
 * verifying.</p>
 */
public class ModuleTutorialTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    private static final String[] files = {
        "bundles/bundle.properties", "bundles/bundle_pt_BR.properties"
    };

    private static final String[] keys = {
        //the new "how to try it" blocks of the three setup steps
        "mode.testfeatures.setup.unit.try",
        "mode.testfeatures.setup.modules.kinds",
        "mode.testfeatures.setup.modules.try",
        "mode.testfeatures.setup.waves.tools",
        "mode.testfeatures.setup.waves.after",
        //contextual hints, with the touch variants where the gesture differs
        "hint.moduleParts", "hint.moduleParts.mobile",
        "hint.moduleSupplies",
        "hint.moduleControls", "hint.moduleControls.mobile",
        "hint.moduleWaves"
    };

    @Test
    void everyTutorialTextExistsInBothBundles() throws Exception{
        for(String file : files){
            String content = java.nio.file.Files.readString(java.nio.file.Path.of(file));
            assertFalse(content.isEmpty(), "bundle must be readable: " + file);

            for(String key : keys){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }

    /** text() looks up "hint." + enum name, so a renamed constant silently shows ???hint.x???. */
    @Test
    void everyModuleHintNameHasItsBundleKey() throws Exception{
        for(String file : files){
            String content = java.nio.file.Files.readString(java.nio.file.Path.of(file));

            for(HintsFragment.ModuleHint hint : HintsFragment.ModuleHint.values()){
                String key = "hint." + hint.name();
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file + " (enum constant must match the bundle key)");
            }
        }
    }

    /** The point of the tutorial is that anyone can read it: no developer vocabulary. */
    @Test
    void theWalkthroughUsesPlainLanguage() throws Exception{
        String[] portugueseJargon = {
            "fuselagem", "dorsal", "módulo", "orçamento", "loadout", "pool",
            "validação", "unidades aéreas", "instale"
        };
        String[] englishJargon = {
            "hull", "dorsal", "module", "loadout", "pool", "validation", "air units"
        };

        String pt = java.nio.file.Files.readString(java.nio.file.Path.of(files[1]));
        String en = java.nio.file.Files.readString(java.nio.file.Path.of(files[0]));

        checkJargon(pt, portugueseJargon);
        checkJargon(en, englishJargon);
    }

    /** Only the text after " = " counts: the keys themselves are developer-facing identifiers. */
    private static void checkJargon(String bundle, String[] jargon){
        for(String line : bundle.split("\n")){
            int eq = line.indexOf(" = ");
            if(eq < 0 || !isTutorialKey(line.substring(0, eq))) continue;

            String value = line.substring(eq + 3).toLowerCase();
            for(String word : jargon){
                assertFalse(value.contains(word), "technical term '" + word + "' in: " + line);
            }
        }
    }

    private static boolean isTutorialKey(String key){
        return key.startsWith("mode.testfeatures.setup.")
            || key.startsWith("mode.testfeatures.modules")
            || key.equals("mode.testfeatures.name")
            || key.equals("mode.testfeatures.description")
            || key.startsWith("hint.module");
    }

    /** A fresh round must be able to show all four hints, even on a brand new save. */
    @Test
    void moduleHintsAreImmediateAndSessionScoped(){
        HintsFragment.ModuleHint[] hints = HintsFragment.ModuleHint.values();
        assertEquals(4, hints.length, "parts, supplies, controls, waves");

        for(HintsFragment.ModuleHint hint : hints){
            assertTrue(hint.immediate(), hint + " must bypass the generic playtime threshold");
            assertTrue(hint.valid(), hint + " must be valid on both platforms");
            assertFalse(hint.finished(), hint + " must stay in the pool for the next round");
            assertTrue(hint.order() < 0, hint + " must precede the generic hints");
        }
    }
}
