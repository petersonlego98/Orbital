package orbital.engine.ai;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/** Checks that the AI phase is reachable from the game and localised in every bundle. */
class AISettingsWiringTest{
    private static final Path dialog = Paths.get("../../core/src/orbital/ui/dialogs/SettingsMenuDialog.java");
    private static final Path logic = Paths.get("../../core/src/orbital/core/Logic.java");

    @Test
    void aiPhaseIsOptInAndRunsBeforeTheUnitGroupUpdate() throws Exception{
        String menu = Files.readString(dialog, StandardCharsets.UTF_8);
        assertTrue(menu.contains("advancedgraphics.checkPref(orbital.engine.ai.AITick.key, false"),
            "the AI phase must stay opt-in in advanced graphics");

        String source = Files.readString(logic, StandardCharsets.UTF_8);
        int tick = source.indexOf("AITick.tick()");
        int update = source.indexOf("Groups.unit.update();");
        assertTrue(tick >= 0, "Logic must call the AI tick");
        assertTrue(update > tick, "the AI tick must see the previous frame's aim points");
    }

    @Test
    void aiKeysExistInEveryBundledLocale() throws Exception{
        for(String locale : new String[]{"", "_pt_BR", "_pt_PT"}){
            Path file = Paths.get("bundles", "bundle" + locale + ".properties");
            String content = Files.readString(file, StandardCharsets.UTF_8);
            for(String key : new String[]{
                "setting.orbitalparallelai.name",
                "setting.orbitalparallelai.description",
                "settings.orbitalparallelai.unavailable"}){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }
}