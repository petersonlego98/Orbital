package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.ctype.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.world.*;

import java.util.*;

import static orbital.Vars.*;

/**
 * Shared catalogue for the free units and structures available in the feature-test mode.
 *
 * <p>Both catalogues live in the same modal dialog, separated by tabs. This keeps the large
 * grids away from the touch controls in the HUD and avoids the unit and construction menus
 * covering each other on tablets.</p>
 */
public class TestFeaturesCatalogDialog extends BaseDialog{
    private enum Tab{
        units,
        buildings
    }

    private final Table list = new Table();
    private final ScrollPane pane;
    private Tab tab = Tab.units;
    /** Whether the structures tab arms the player's building tool or the enemy one. */
    private boolean armPlayerTool;
    private String search = "";

    public TestFeaturesCatalogDialog(){
        super("@mode.testfeatures.catalog.title");
        //the game keeps running while a structure is being armed; pausing here keeps the tap
        //target from moving under the cursor while the player browses the catalogue
        shouldPause = true;
        cont.top();

        cont.table(t -> {
            t.defaults().growX().height(mobile ? 46f : 52f).pad(2f);
            t.button("@mode.testfeatures.catalog.units", Icon.units, Styles.flatTogglet, () -> selectTab(Tab.units))
                .update(button -> button.setChecked(tab == Tab.units));
            t.button("@mode.testfeatures.catalog.buildings", Icon.distribution, Styles.flatTogglet, () -> selectTab(Tab.buildings))
                .update(button -> button.setChecked(tab == Tab.buildings));
        }).growX().row();

        cont.table(t -> {
            t.image(Icon.zoom).padRight(8f);
            t.field("", this::rebuild).growX().maxTextLength(maxNameLength).get().setMessageText("@players.search");
        }).growX().pad(-2f).padLeft(6f).row();

        pane = new ScrollPane(list, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.update(() -> {
            if(pane.hasScroll()){
                Element result = Core.scene.getHoverElement();
                if(result == null || !result.isDescendantOf(pane)){
                    Core.scene.setScrollFocus(null);
                }
            }
        });
        cont.add(pane).grow();

        buttons.button("@back", Icon.left, this::hide).size(210f, 64f);
        addCloseListener();
        onResize(this::rebuild);
    }

    /** Opens the catalogue on the units tab. */
    public void showUnits(){
        show(Tab.units);
    }

    /** Opens the catalogue on the structures tab. */
    public void showBuildings(){
        showBuildings(false);
    }

    /**
     * Opens the catalogue on the structures tab, choosing which tool a picked structure arms:
     * the player's team (queued through the normal build pipeline) or the enemy team (instant).
     */
    public void showBuildings(boolean armPlayerTool){
        this.armPlayerTool = armPlayerTool;
        show(Tab.buildings);
    }

    private void show(Tab selected){
        tab = selected;
        rebuild();
        show();
        //the pane width is only known once it is laid out, so the grid is rebuilt with the real
        //width on the next frame - this keeps the columns aligned to the pane edges
        Core.app.post(() -> {
            if(isShown()) rebuild();
        });
    }

    private void selectTab(Tab selected){
        if(tab == selected) return;
        tab = selected;
        rebuild();
    }

    private void rebuild(String searchText){
        search = normalize(searchText);
        rebuild();
    }

    private void rebuild(){
        list.clear();
        list.top().left().margin(10f);
        list.defaults().left().pad(2f);

        float cellSize = mobile ? 92f : 84f;
        float scale = Math.max(0.01f, Scl.scl(1f));
        float paneWidth = measuredPaneWidth() / scale;
        float scrollWidth = scrollBarWidth() / scale;
        float[] layout = catalogGridLayout(paneWidth, cellSize, scrollWidth);
        int columns = (int)layout[0];
        float cell = layout[1];

        int found = 0;
        if(tab == Tab.units){
            found = buildUnitGrid(columns, cell);
        }else{
            found = buildBuildingGrid(columns, cell);
        }

        if(found == 0){
            list.add("@none.found").padLeft(54f).padTop(10f);
        }
    }

    private int buildUnitGrid(int columns, float cell){
        int found = 0;
        for(UnitType type : TestNewFeaturesMode.aerialTypes()){
            if(!matches(type.localizedName, type.name)) continue;

            ImageButton button = addEntry(type, unitIcon(type), cell, () -> {
                if(!TestNewFeaturesMode.spawn(type)){
                    ui.showInfoToast(Core.bundle.get("mode.testfeatures.limit"), 3f);
                }
            });
            button.update(() -> button.setChecked(false));

            found++;
            if(found % columns == 0) list.row();
        }
        return found;
    }

    private int buildBuildingGrid(int columns, float cell){
        int found = 0;
        Seq<Block> blocks = TestNewFeaturesMode.enemyBlocks();

        //the catalogue is grouped the same way the build menu is: one header per category, and the
        //name-sorted blocks of that category below it
        for(Category category : Category.all){
            Seq<Block> categoryBlocks = blocks.select(block -> block.category == category && matches(block.localizedName, block.name));
            if(categoryBlocks.isEmpty()) continue;

            list.add(Core.bundle.get("category." + category.name() + ".name", category.name()))
                .style(Styles.outlineLabel).left().padTop(8f).padBottom(2f).colspan(columns).row();

            int index = 0;
            for(Block block : categoryBlocks){
                ImageButton button = addEntry(block, blockIcon(block), cell, () -> {
                    if(armPlayerTool){
                        TestNewFeaturesMode.armPlayerBlock(block);
                    }else{
                        TestNewFeaturesMode.armEnemyBlock(block);
                    }
                    hide();
                });
                button.update(() -> button.setChecked(armPlayerTool
                    ? TestNewFeaturesMode.lastFactory == block
                    : TestNewFeaturesMode.enemyBlock == block));

                index++;
                found++;
                if(index % columns == 0) list.row();
            }

            //do not let the next category header share the row with the last block
            if(index % columns != 0) list.row();
        }
        return found;
    }

    /**
     * Adds one catalogue entry: the icon that spawns/arms the content, plus a small overlay
     * button that opens the standard content-info dialog (name, description and stats).
     *
     * <p>The info action is an overlay instead of a second cell so it does not reduce the icon
     * size, and it stays separate from the activate click.</p>
     */
    private ImageButton addEntry(UnlockableContent content, Drawable icon, float cell, Runnable activate){
        float inner = Math.max(1f, cell - 4f);

        ImageButton button = new ImageButton(Tex.whiteui, Styles.clearNoneTogglei);
        button.getStyle().imageUp = icon;
        button.resizeImage(inner * 0.78f);
        button.clicked(activate);

        Table overlay = new Table();
        overlay.touchable = Touchable.childrenOnly;
        overlay.top().right();
        overlay.button(Icon.info, Styles.clearNonei, () -> ui.content.show(content))
            .size(Math.max(26f, inner * 0.28f)).pad(2f)
            .tooltip("@mode.testfeatures.catalog.info");

        list.add(new arc.scene.ui.layout.Stack(button, overlay)).size(inner).tooltip(content.localizedName);
        return button;
    }

    private static Drawable unitIcon(UnitType type){
        if(type.uiIcon != null && type.uiIcon.found()) return new TextureRegionDrawable(type.uiIcon);
        if(type.fullIcon != null && type.fullIcon.found()) return new TextureRegionDrawable(type.fullIcon);
        return Icon.units;
    }

    private static Drawable blockIcon(Block block){
        if(block.uiIcon != null && block.uiIcon.found()) return new TextureRegionDrawable(block.uiIcon);
        if(block.fullIcon != null && block.fullIcon.found()) return new TextureRegionDrawable(block.fullIcon);
        return Icon.distribution;
    }

    private boolean matches(String localizedName, String internalName){
        if(search.isEmpty()) return true;
        return localizedName.toLowerCase(Locale.ROOT).contains(search)
            || internalName.toLowerCase(Locale.ROOT).contains(search);
    }

    private static String normalize(String value){
        return value == null ? "" : value.trim().replaceAll(" +", " ").toLowerCase(Locale.ROOT);
    }

    /** @return the measured width of the catalogue pane, in raw pixels. */
    private float measuredPaneWidth(){
        float width = pane.getWidth();
        if(width <= 1f) width = list.getWidth();
        if(width <= 1f) width = Core.graphics.getWidth() - Scl.scl(80f);
        return Math.max(1f, width);
    }

    /**
     * Works out a responsive grid that always leaves room for the vertical scrollbar.
     *
     * <p>Cells grow up to {@code maxCell} to use the available width, so the catalogue does not
     * leave a large empty strip to the right of the icons on wide screens.</p>
     *
     * @param width available pane width, in ui units
     * @param preferred preferred minimum size of one cell, in ui units
     * @param scroll width reserved for the vertical scrollbar, in ui units
     * @return the number of columns and the size of one cell, in ui units
     */
    public static float[] catalogGridLayout(float width, float preferred, float scroll){
        float available = Math.max(1f, width - Math.max(0f, scroll));
        float requested = preferred > 1f ? preferred : 84f;
        int columns = Mathf.clamp((int)Math.floor(available / requested), 1, 10);
        float cell = Math.min(Math.max(available / columns, requested), maxCell);
        return new float[]{columns, cell};
    }

    /** Largest cell the catalogue grid may grow to, in ui units. */
    public static final float maxCell = 104f;

    /** @return the width the vertical scrollbar of a pane takes, in raw pixels. */
    private static float scrollBarWidth(){
        try{
            ScrollPane.ScrollPaneStyle style = Core.scene.getStyle(ScrollPane.ScrollPaneStyle.class);
            if(style != null){
                float width = 0f;
                if(style.vScroll != null) width = Math.max(width, style.vScroll.getMinWidth());
                if(style.vScrollKnob != null) width = Math.max(width, style.vScrollKnob.getMinWidth());
                //the small pane draws a transparent track over the content, so its own minimum
                //width can be zero. Reserve a real strip anyway so the last icon stays clear.
                return Math.max(width, 24f);
            }
        }catch(Throwable ignored){
        }
        return 24f;
    }
}
