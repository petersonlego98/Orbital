package orbital.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The module menu (Unit turrets) is an equipment screen, not a plain list: the category bar has
 * to stay above the catalogue and the "show everything" action has to remain the big way back
 * from a filtered view. The dialog needs a running scene, so the wiring is checked in its source.
 */
class TurretLoadoutFilterTest{
    private static final Path dialog = Paths.get("../../core/src/orbital/ui/dialogs/TurretLoadoutDialog.java");
    private static final Path bundles = Paths.get("../../core/assets/bundles");

    @Test
    void offersOneButtonPerCategoryPlusTheShowEverythingAction(){
        String source = read(dialog);

        assertTrue(source.contains("\"@turret.loadout.filter.all\"") && source.contains("Icon.list"),
            "the big button must return to the catalogue that shows everything");
        assertTrue(source.contains("\"@turret.loadout.filter.weapons\"") && source.contains("Icon.turret"),
            "the weapons category needs its own icon and label");
        assertTrue(source.contains("\"@turret.loadout.filter.support\"") && source.contains("Icon.defense"),
            "the support category needs its own icon and label");

        assertTrue(source.contains("setFilter(CatalogFilter.all)"), "the big button must clear the filter");
        assertTrue(source.contains("setFilter(CatalogFilter.weapons)"), "the weapons button must filter the catalogue");
        assertTrue(source.contains("setFilter(CatalogFilter.support)"), "the support button must filter the catalogue");
    }

    @Test
    void eachCategoryOnlyBuildsItsOwnCatalogueSections(){
        String source = read(dialog);

        assertTrue(Pattern.compile("if\\(filter != CatalogFilter\\.support\\)\\{").matcher(source).find(),
            "the weapons sections must disappear while support is selected");
        assertTrue(Pattern.compile("if\\(filter != CatalogFilter\\.weapons\\)\\{").matcher(source).find(),
            "the support sections must disappear while weapons is selected");
        assertTrue(source.contains("filter = CatalogFilter.all;"),
            "the menu must reopen showing everything instead of keeping the previous filter");
    }

    @Test
    void everyFilterLabelExistsInTheBundles(){
        List<String> required = List.of(
            "turret.loadout.categories",
            "turret.loadout.filter.all",
            "turret.loadout.filter.weapons",
            "turret.loadout.filter.support"
        );

        for(String locale : new String[]{"", "_pt_BR"}){
            Set<String> keys = keysFor(locale);
            for(String key : required){
                assertTrue(keys.contains(key), "missing bundle entry in bundle" + locale + ": " + key);
            }
        }
    }

    private static Set<String> keysFor(String locale){
        Set<String> keys = new HashSet<>();
        Path file = bundles.resolve("bundle" + locale + ".properties");
        assertTrue(Files.isRegularFile(file), "missing bundle " + file);

        try{
            for(String line : Files.readAllLines(file, StandardCharsets.UTF_8)){
                int split = line.indexOf('=');
                if(split > 0) keys.add(line.substring(0, split).trim());
            }
        }catch(Exception e){
            throw new AssertionError("cannot read " + file, e);
        }
        return keys;
    }

    private static String read(Path path){
        try{
            return Files.readString(path, StandardCharsets.UTF_8);
        }catch(Exception e){
            throw new AssertionError("cannot read " + path, e);
        }
    }
}
