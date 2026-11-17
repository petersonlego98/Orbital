package orbital.engine.world;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that both build entry points and the preference are wired. */
class AutoInfrastructureWiringTest{

    @Test
    void catalogQueuesInfrastructureBeforeTheFactory() throws Exception{
        String source = Files.readString(
            Paths.get("../../core/src/orbital/game/TestNewFeaturesMode.java"),
            StandardCharsets.UTF_8);

        int plan = source.indexOf("AutoInfrastructure.plan(plan, player.team())");
        int factory = source.indexOf("unit.addBuild(plan, true)");
        assertTrue(plan >= 0, "the catalog must plan the infrastructure");
        assertTrue(factory > plan, "transport must be queued before the factory plan");
    }

    @Test
    void normalPlacementChecksTheOptInKey() throws Exception{
        String input = Files.readString(
            Paths.get("../../core/src/orbital/input/InputHandler.java"),
            StandardCharsets.UTF_8);
        assertTrue(input.contains("AutoInfrastructure.normalPlacementEnabled()"),
            "ordinary placement must honor the preference");

        String menu = Files.readString(
            Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java"),
            StandardCharsets.UTF_8);
        assertTrue(menu.contains("constructions.checkPref(orbital.engine.world.AutoInfrastructure.key, false"),
            "the preference must be opt-in in Constructions");
    }

    @Test
    void keysExistInEveryBundledLocale() throws Exception{
        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            Path file = Paths.get("bundles", "bundle" + locale + ".properties");
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for(String key : new String[]{
                "setting.orbitalautoinfra.name", "setting.orbitalautoinfra.description"}){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }
}