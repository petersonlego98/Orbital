package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.layout.*;
import arc.math.*;
import arc.struct.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.world.*;

import static orbital.Vars.*;

/**
 * Block picker for the enemy-structure tool of the test mode.
 *
 * <p>It mirrors the map editor palette - a search box above a scrolling grid of the blocks that
 * can be drawn, with a header per {@link Category} - but picking a block does not open the editor:
 * it arms the HUD tool and hands control back, so a single tap on the map drops an enemy structure
 * of the currently selected team.</p>
 */
public class EnemyBuildingDialog extends BaseDialog{
    private final Table list = new Table();
    private String search = "";
    private Runnable picked = () -> {};

    public EnemyBuildingDialog(){
        super("@mode.testfeatures.buildings.select");
        //the game keeps running while a structure is being armed; pausing here keeps the tap
        //target from moving under the cursor while the player browses the catalog
        shouldPause = true;
        cont.top();

        cont.table(t -> {
            t.image(Icon.zoom).padRight(8);
            t.field("", this::rebuild).growX().maxTextLength(maxNameLength).get().setMessageText("@players.search");
        }).growX().pad(-2).padLeft(6f).row();

        var pane = new ScrollPane(list, Styles.smallPane);
        pane.setFadeScrollBars(false);
        cont.add(pane).grow();

        buttons.button("@back", Icon.left, this::hide).size(210f, 64f);
        addCloseListener();
    }

    /** Opens the picker. @param picked called after a block is armed, so the caller can refresh its state. */
    public void show(Runnable picked){
        this.picked = picked;
        rebuild("");
        show();
        //the pane width is only known once it is laid out, so the grid is rebuilt with the real
        //width on the next frame - this keeps the columns aligned to the pane edges
        Core.app.post(() -> {
            if(isShown()) rebuild(search);
        });
    }

    private void rebuild(String searchText){
        search = searchText.trim().replaceAll(" +", " ").toLowerCase();
        list.clear();
        list.top().left().margin(12f);
        list.defaults().left().pad(4f);

        //columns follow the pane width instead of a fixed count, so more blocks fit per row on
        //phones and the horizontal space of the picker is not wasted
        float width = list.getWidth() > 1f ? list.getWidth() : Core.graphics.getWidth() - Scl.scl(40f);
        int columns = Mathf.clamp((int)(width / Scl.scl(58f)), 4, 8);
        int found = 0;
        Seq<Block> blocks = TestNewFeaturesMode.enemyBlocks();

        //the catalog is grouped the same way the build menu is: one header per category, and the
        //name-sorted blocks of that category below it
        for(Category category : Category.all){
            int index = 0;
            for(Block block : blocks){
                if(block.category != category) continue;
                if(block.uiIcon == null || !Core.atlas.isFound(block.uiIcon)) continue;
                if(!search.isEmpty() && !block.localizedName.toLowerCase().contains(search)) continue;

                if(index == 0){
                    list.add(Core.bundle.get("category." + category.name() + ".name", category.name()))
                        .style(Styles.outlineLabel).left().padTop(8f).padBottom(2f).row();
                }

                ImageButton button = new ImageButton(Tex.whiteui, Styles.clearNoneTogglei);
                button.getStyle().imageUp = new TextureRegionDrawable(block.uiIcon);
                button.resizeImage(8 * 4f);
                button.clicked(() -> {
                    TestNewFeaturesMode.enemyBlock = block;
                    TestNewFeaturesMode.enemyRemoving = false;
                    picked.run();
                    hide();
                });
                button.update(() -> button.setChecked(TestNewFeaturesMode.enemyBlock == block));
                list.add(button).size(50f).tooltip(block.localizedName);
                if(++index % columns == 0) list.row();
            }

            found += index;
        }

        if(found == 0){
            list.add("@none.found").padLeft(54f).padTop(10f);
        }
    }
}
