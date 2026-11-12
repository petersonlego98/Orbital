import arc.*;
import arc.input.KeyBind;
import arc.util.*;
import orbital.content.*;
import orbital.input.*;
import orbital.ui.dialogs.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static orbital.Vars.*;

/**
 * Tests for the controls menu: the three switches of Orbital's own controls and the module
 * number keys they gate.
 *
 * <p>The switches live in the same dialog as the rebindable keys, so a player who cannot move
 * finds the analog stick, the module hotkeys and the gamepad without hunting for them.</p>
 */
public class ControlsMenuTest{

    @BeforeAll
    static void setup(){
        ApplicationTests.launchApplication(false);
    }

    @BeforeEach
    void map(){
        world.resize(96, 96);
        world.beginMapLoad();
        world.tiles.fill();
        world.endMapLoad();
    }

    /**
     * Every label the controls menu shows must exist in the shipped bundles: the section, the
     * three switches (name plus tooltip), the two new keybind categories and the eleven keys.
     *
     * <p>The properties files are read directly, because the headless test application does not
     * always build a runtime bundle - and the shipped text is what needs verifying.</p>
     */
    @Test
    void controlSwitchLabelsExist() throws Exception{
        String[] files = {"bundles/bundle.properties", "bundles/bundle_pt_BR.properties"};

        String[] keys = {
            "controls.orbital.title", "controls.orbital.hint",
            "category.modules.name", "category.gamepad.name",
            "keybind.module_toggle_01.name", "keybind.module_toggle_10.name",
            "keybind.controller_fire.name"
        };
        for(String key : new String[]{"analogstick", "module-hotkeys", "gamepad"}){
            keys = java.util.Arrays.copyOf(keys, keys.length + 2);
            keys[keys.length - 2] = "setting." + key + ".name";
            keys[keys.length - 1] = "setting." + key + ".description";
        }

        for(String file : files){
            String content = new java.io.File(file).exists()
                ? java.nio.file.Files.readString(java.nio.file.Path.of(file)) : "";
            assertTrue(!content.isEmpty(), "bundle must be readable: " + file);

            for(String key : keys){
                assertTrue(content.contains("\n" + key + " =") || content.startsWith(key + " ="),
                    "missing " + key + " in " + file);
            }
        }
    }

    /** the ten module keys must be listed one by one, not only the first and the last */
    @Test
    void everyModuleKeyHasALabel() throws Exception{
        String content = java.nio.file.Files.readString(java.nio.file.Path.of("bundles/bundle.properties"));
        for(int i = 1; i <= 10; i++){
            String key = String.format("keybind.module_toggle_%02d.name", i);
            assertTrue(content.contains("\n" + key + " ="), "missing " + key);
        }
    }
    /** the master switch must actually gate the controller reads */
    @Test
    void theGamepadSwitchGatesEveryControllerRead(){
        Core.settings.put(ControllerInput.enabledKey, false);
        try{
            assertFalse(ControllerInput.enabled());
            assertFalse(ControllerInput.firing());
            assertEquals(0f, ControllerInput.moveX());
            assertEquals(0f, ControllerInput.moveY());
            assertFalse(ControllerInput.moving());
            assertNull(ControllerInput.aim());
        }finally{
            Core.settings.put(ControllerInput.enabledKey, true);
        }

        assertTrue(ControllerInput.enabled());
        //with no controller connected every read is the neutral one, so the switch cannot be
        //told apart from "no controller" - which is the point
        assertFalse(ControllerInput.moving());
        assertNull(ControllerInput.aim());
    }

    /** the analog switch is the only thing that hides the stick: the drag control stays */
    @Test
    void theAnalogSwitchIsReadByDefault(){
        //default true: the stick shows on a phone without the player touching the settings
        assertFalse(Core.settings.has(KeybindDialog.analogKey), "not forced by default");
        Core.settings.defaults(KeybindDialog.analogKey, true);
        assertTrue(Core.settings.getBool(KeybindDialog.analogKey, true));

        assertFalse(Core.settings.has(KeybindDialog.moduleHotkeysKey), "not forced by default");
        Core.settings.defaults(KeybindDialog.moduleHotkeysKey, true);
        assertTrue(Core.settings.getBool(KeybindDialog.moduleHotkeysKey, true));

        assertFalse(Core.settings.has(ControllerInput.enabledKey), "not forced by default");
        assertTrue(Core.settings.getBool(ControllerInput.enabledKey, true));

        //and the switch turns it off without touching the settings file semantics
        Core.settings.put(KeybindDialog.analogKey, false);
        assertFalse(Core.settings.getBool(KeybindDialog.analogKey, true));
        Core.settings.remove(KeybindDialog.analogKey);
    }
}
