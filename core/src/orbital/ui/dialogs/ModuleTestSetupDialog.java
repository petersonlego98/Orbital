package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.ui.*;

import static orbital.Vars.*;

/**
 * Entry menu of the module test environment.
 *
 * <p>The test mode is no longer a sandbox that drops the player straight into the map: the
 * environment exists to validate dorsal modules, so it opens in three explicit steps - pick the
 * air hull, install the modules on it, then start the defense against waves of air units. The
 * wave rules stay disabled until the last step, so the player can take as long as needed while
 * configuring the hull.</p>
 */
public class ModuleTestSetupDialog extends BaseDialog{
    /** Number of setup steps: hull, modules, defense. */
    private static final int steps = 3;

    private final Table body = new Table();
    private int step;
    private @Nullable UnitType picked;
    private @Nullable TurretLoadoutDialog loadout;

    public ModuleTestSetupDialog(){
        super("@mode.testfeatures.setup.title");
        shouldPause = true;
        cont.top();
        cont.add(body).grow().row();
        buttons.defaults().size(190f, 56f);
        onResize(this::rebuild);
    }

    /** Resets the menu to the first step and shows it. */
    public void openSetup(){
        step = 0;
        //the starting Mono is not an air hull, so it must not count as a valid pick
        picked = TestNewFeaturesMode.selectedType;
        if(picked != null && !picked.flying) picked = null;
        rebuild();
        show();
    }

    /** @return the content width in scene units, bounded so the grid also fits phones. */
    private float contentWidth(){
        return Mathf.clamp(Core.graphics.getWidth() / Scl.scl(1f) * 0.9f, 400f, 760f);
    }

    private void rebuild(){
        if(!isShown() && step < 0) return;

        body.clearChildren();
        buttons.clearChildren();
        body.top().left().margin(10f);

        body.add(Core.bundle.format("mode.testfeatures.setup.step", step + 1, steps))
            .color(Pal.accent).left().padBottom(6f).row();

        switch(step){
            case 0 -> buildUnitStep();
            case 1 -> buildModuleStep();
            default -> buildWaveStep();
        }
    }

    /** Step 1: the air hull that will carry the modules and defend the core. */
    private void buildUnitStep(){
        body.add("@mode.testfeatures.setup.unit").style(Styles.outlineLabel).left().padBottom(4f).row();
        body.add("@mode.testfeatures.setup.unit.hint").color(Color.lightGray).left().wrap()
            .width(contentWidth() - 24f).padBottom(8f).row();
        note("@mode.testfeatures.setup.unit.try");

        int columns = Mathf.clamp((int)(contentWidth() / Scl.scl(76f)), 4, 9);
        int[] index = {0};
        body.pane(pane -> {
            pane.top().left();
            for(UnitType type : TestNewFeaturesMode.aerialTypes()){
                ImageButton button = pane.button(new TextureRegionDrawable(type.uiIcon), Styles.selecti, () -> {
                    picked = type;
                    rebuild();
                }).size(64f).pad(5f).tooltip(type.localizedName).get();
                button.resizeImage(48f);
                button.setChecked(picked == type);

                if(++index[0] % columns == 0){
                    pane.row();
                }
            }
        }).grow().row();

        buttons.button("@mode.testfeatures.setup.next", Icon.right, () -> {
            if(picked == null || !TestNewFeaturesMode.selectUnit(picked)){
                ui.showInfoToast(Core.bundle.get("mode.testfeatures.setup.unit.invalid"), 3f);
                return;
            }
            step = 1;
            rebuild();
        }).disabled(button -> picked == null);
    }

    /** Step 2: dorsal modules are installed through the regular loadout screen. */
    private void buildModuleStep(){
        Unit unit = TestNewFeaturesMode.playerUnit();

        body.add("@mode.testfeatures.setup.modules").style(Styles.outlineLabel).left().padBottom(4f).row();
        body.add(unit == null ? Core.bundle.get("mode.testfeatures.modules.none")
            : Core.bundle.format("mode.testfeatures.setup.modules.target", unit.type.localizedName))
            .color(Color.lightGray).left().wrap().width(contentWidth() - 24f).padBottom(4f).row();
        body.add("@mode.testfeatures.setup.modules.hint").color(Color.lightGray).left().wrap()
            .width(contentWidth() - 24f).padBottom(12f).row();
        note("@mode.testfeatures.setup.modules.kinds");
        note("@mode.testfeatures.setup.modules.try");

        body.button("@mode.testfeatures.modules.open", Icon.settings, this::openLoadout)
            .size(contentWidth() - 24f, 52f).left().padBottom(10f).row();

        if(unit != null){
            body.add(Core.bundle.format("mode.testfeatures.setup.summary", unit.type.localizedName,
                unit.mountedTurrets() + (unit.wallMounts == null ? 0 : unit.wallMounts.length),
                supportCount(unit))).color(Pal.accent).left().wrap().width(contentWidth() - 24f).row();
        }

        buttons.button("@mode.testfeatures.setup.back", Icon.left, () -> {
            step = 0;
            rebuild();
        });
        buttons.button("@mode.testfeatures.setup.next", Icon.right, () -> {
            step = 2;
            rebuild();
        }).disabled(button -> TestNewFeaturesMode.playerUnit() == null);
    }

    /** Step 3: hand the hull to the defense and unlock the air waves. */
    private void buildWaveStep(){
        Unit unit = TestNewFeaturesMode.playerUnit();

        body.add("@mode.testfeatures.setup.waves").style(Styles.outlineLabel).left().padBottom(4f).row();
        body.add("@mode.testfeatures.setup.waves.hint").color(Color.lightGray).left().wrap()
            .width(contentWidth() - 24f).padBottom(8f).row();
        body.add(String.format(Core.bundle.get("mode.testfeatures.setup.waves.timer"),
            (int)(TestNewFeaturesMode.initialWaveSpacing / 60f), (int)(TestNewFeaturesMode.waveSpacing / 60f)))
            .color(Color.lightGray).left().wrap().width(contentWidth() - 24f).padBottom(12f).row();
        note("@mode.testfeatures.setup.waves.tools");
        note("@mode.testfeatures.setup.waves.after");

        if(unit != null){
            body.add(Core.bundle.format("mode.testfeatures.setup.summary", unit.type.localizedName,
                unit.mountedTurrets() + (unit.wallMounts == null ? 0 : unit.wallMounts.length),
                supportCount(unit))).color(Pal.accent).left().wrap().width(contentWidth() - 24f).row();
        }

        buttons.button("@mode.testfeatures.setup.back", Icon.left, () -> {
            step = 1;
            rebuild();
        });
        buttons.button("@mode.testfeatures.setup.start", Icon.play, () -> {
            if(TestNewFeaturesMode.beginWaves()){
                hide();
                ui.showInfoToast(Core.bundle.get("mode.testfeatures.setup.waves.started"), 4f);
            }else{
                ui.showInfoToast(Core.bundle.get("mode.testfeatures.setup.waves.invalid"), 3f);
            }
        }).disabled(button -> unit == null);
    }

    /**
     * A short "how to try it" note in plain language. The explicit width is what makes it wrap
     * inside the dialog column on phones instead of running off the right edge.
     */
    private void note(String key){
        body.add(key).color(Color.lightGray).left().wrap().width(contentWidth() - 30f)
            .padLeft(6f).padBottom(8f).row();
    }

    private void openLoadout(){
        Unit unit = TestNewFeaturesMode.playerUnit();
        if(unit == null) return;

        if(loadout == null){
            loadout = new TurretLoadoutDialog();
        }
        loadout.show(unit, this::rebuild);
    }

    private static int supportCount(Unit unit){
        int count = 0;
        if(unit.supportMounts != null){
            for(SupportMount mount : unit.supportMounts){
                if(mount != null && mount.block != null) count++;
            }
        }
        return count;
    }
}
