import arc.*;
import orbital.ui.*;
import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the opt-in preference for the classic fixed construction menu. */
public class BuildMenuPreferenceTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @AfterEach
    void clearPreference(){
        Core.settings.remove(BuildMenuPreference.oldMenuKey);
    }

    @Test
    void classicMenuIsDisabledByDefault(){
        Core.settings.remove(BuildMenuPreference.oldMenuKey);

        assertFalse(BuildMenuPreference.oldMenuEnabled(),
            "the classic fixed construction menu must be disabled by default");
    }

    @Test
    void classicMenuCanBeEnabled(){
        Core.settings.put(BuildMenuPreference.oldMenuKey, true);
        assertTrue(BuildMenuPreference.oldMenuEnabled());

        Core.settings.put(BuildMenuPreference.oldMenuKey, false);
        assertFalse(BuildMenuPreference.oldMenuEnabled());
    }

    @Test
    void preferenceTextExistsInBothBundles() throws Exception{
        String[] files = {"bundles/bundle.properties", "bundles/bundle_pt_BR.properties"};
        String[] keys = {"setting.oldbuildmenu.name", "setting.oldbuildmenu.description"};

        for(String file : files){
            String content = java.nio.file.Files.readString(Path.of(file));
            for(String key : keys){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }
}
