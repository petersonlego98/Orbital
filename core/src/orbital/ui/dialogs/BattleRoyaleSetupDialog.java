package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.Seq;
import arc.util.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.ui.*;

import static orbital.Vars.*;

/**
 * Entry wizard of the 8x8 battle royale. The match is loaded but paused behind it while the player
 * picks exactly one airframe, fits its dorsal modules and only then starts the fight. Victory is
 * the last team with living units; there are no cores in this mode.
 */
public class BattleRoyaleSetupDialog extends BaseDialog{
    private static final int steps = 2;

    private final Table body = new Table();
    private int step;
    private @Nullable UnitType picked;

    public BattleRoyaleSetupDialog(){
        super("@mode.battleRoyale.setup.title");
        shouldPause = true;
        cont.top();
        cont.add(body).grow().row();
        buttons.defaults().size(190f, 56f);
        onResize(this::rebuild);
    }

    /** Resets the wizard to the first step and shows it. */
    public void openSetup(){
        step = 0;
        picked = null;
        rebuild();
        show();
    }

    /** @return the content width in scene units, bounded so the grid also fits phones. */
    private float contentWidth(){
        return Mathf.clamp(Core.graphics.getWidth() / Scl.scl(1f) * 0.9f, 400f, 760f);
    }

    private void rebuild(){
        body.clearChildren();
        buttons.clearChildren();
        body.top().left().margin(10f);

        body.add(Core.bundle.format("mode.testfeatures.setup.step", step + 1, steps))
            .color(Pal.accent).left().padBottom(6f).row();

        switch(step){
            case 0 -> buildUnitStep();
            //modules are no longer fitted for free: every removable part is picked up in the field
            default -> buildBattleStep();
        }
    }

    /** Step 1: choose the single airframe that will fight in this match. */
    private void buildUnitStep(){
        body.add("@mode.battleRoyale.setup.unit").style(Styles.outlineLabel).left().padBottom(4f).row();
        body.add("@mode.battleRoyale.setup.unit.hint").color(Color.lightGray).left().wrap()
            .width(contentWidth() - 24f).padBottom(8f).row();

        //Fit every airframe on one screen. The old fixed 64px cells inside a ScrollPane needed a
        //scrollbar on smaller displays; cell size now adapts to both width and available height.
        Seq<UnitType> types = TestNewFeaturesMode.aerialTypes();
        int count = types.size;
        float availableWidth = contentWidth() - 24f;
        float availableHeight = Math.max(150f, Core.graphics.getHeight() / Scl.scl(1f) - 220f);
        float gap = 4f, cell = 30f;
        int columns = 1;
        for(float size = 64f; size >= 30f; size -= 2f){
            int candidate = Math.max(1, (int)((availableWidth + gap) / (size + gap)));
            int rows = (count + candidate - 1) / candidate;
            columns = candidate;
            if(rows * (size + gap) <= availableHeight){
                cell = size;
                break;
            }
        }
        float iconSize = cell * 0.72f;

        Table grid = new Table();
        grid.top().left();
        int[] index = {0};
        for(UnitType type : types){
            ImageButton button = grid.button(new TextureRegionDrawable(type.uiIcon), Styles.selecti, () -> {
                picked = type;
                rebuild();
            }).size(cell).pad(gap / 2f).tooltip(type.localizedName).get();
            button.resizeImage(iconSize);
            button.setChecked(picked == type);

            if(++index[0] % columns == 0){
                grid.row();
            }
        }
        body.add(grid).center().row();

        buttons.button("@mode.testfeatures.setup.next", Icon.right, () -> {
            if(picked == null || !BattleRoyaleMode.selectUnit(picked)){
                ui.showInfoToast(Core.bundle.get("mode.battleRoyale.setup.invalid"), 3f);
                return;
            }
            step = 1;
            rebuild();
        }).disabled(button -> picked == null);
    }

    /** Step 2: hand the hull to the match. Last team with living units wins. */
    private void buildBattleStep(){
        Unit unit = BattleRoyaleMode.playerUnit();

        body.add("@mode.battleRoyale.setup.battle").style(Styles.outlineLabel).left().padBottom(4f).row();
        body.add("@mode.battleRoyale.setup.battle.hint").color(Color.lightGray).left().wrap()
            .width(contentWidth() - 24f).padBottom(12f).row();

        if(unit != null){
            body.add(Core.bundle.format("mode.testfeatures.setup.summary", unit.type.localizedName,
                unit.mountedTurrets() + (unit.wallMounts == null ? 0 : unit.wallMounts.length),
                supportCount(unit))).color(Pal.accent).left().wrap().width(contentWidth() - 24f).row();
        }

        buttons.button("@mode.testfeatures.setup.back", Icon.left, () -> {
            step = 0;
            rebuild();
        });
        buttons.button("@mode.battleRoyale.setup.start", Icon.play, () -> {
            if(BattleRoyaleMode.beginBattle()){
                hide();
                ui.showInfoToast(Core.bundle.get("mode.battleRoyale.setup.started"), 4f);
            }else{
                ui.showInfoToast(Core.bundle.get("mode.battleRoyale.setup.invalid"), 3f);
            }
        }).disabled(button -> unit == null);
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
