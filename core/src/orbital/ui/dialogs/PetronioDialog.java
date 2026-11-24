package orbital.ui.dialogs;

import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import orbital.graphics.*;
import orbital.ui.*;

import static orbital.Vars.*;

/** In-game codex entry for Petrônio, the ore that replaces every other mineral. */
public class PetronioDialog extends BaseDialog{
    /** Palette recreated from the reference art supplied for the mineral. */
    public static final Color[] palette = {
        Color.valueOf("ffc52e"), Color.valueOf("fff1a8"), Color.valueOf("b87922"),
        Color.valueOf("67dccb"), Color.valueOf("b58aff")
    };

    public PetronioDialog(){
        super("@petronio.title");
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();
        buttons.clear();

        Table content = new Table();
        content.top().left().margin(12f);

        content.add("@petronio.title").style(Styles.outlineLabel).color(OrbitalTheme.gold).left().row();
        content.add("@petronio.subtitle").color(OrbitalTheme.cyan).left().padBottom(10f).row();
        content.add("@petronio.text").wrap().width(520f).left().row();

        content.add("@petronio.palette").color(Pal.accent).left().padTop(12f).padBottom(6f).row();
        Table colors = new Table();
        colors.left();
        for(Color color : palette){
            colors.image().color(color).size(28f).padRight(6f);
        }
        content.add(colors).left().row();

        content.add("@petronio.facts").wrap().width(520f).left().padTop(12f).row();

        ScrollPane pane = new ScrollPane(content);
        pane.setFadeScrollBars(false);
        cont.add(pane).grow();
        addCloseButton();
    }
}
