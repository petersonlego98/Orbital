package orbital.ui.fragments;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import orbital.graphics.*;
import orbital.ui.*;

import static orbital.Vars.mobile;

public class LoadingFragment{
    private Table table;
    private TextButton button;
    private Bar bar;
    private Label nameLabel;
    private @Nullable Cell<Bar> barCell;
    private @Nullable Cell<TextButton> buttonCell;
    private float contentWidth = 420f;
    private @Nullable Runnable cancelListener;
    private float progValue;

    public void build(Group parent){
        parent.fill(t -> {
            t.align(Align.center);
            //rect must fill screen completely.
            t.rect((x, y, w, h) -> {
                if(OrbitalTheme.available()){
                    OrbitalTheme.renderLoadingBackdrop();
                    //the theme renderer resets the batch color, so reproduce the table's fade-out
                    //by covering the key art with deep space as the loading screen disappears
                    Draw.color(OrbitalTheme.space.r, OrbitalTheme.space.g, OrbitalTheme.space.b, 1f - t.color.a);
                    Fill.crect(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
                }else{
                    Draw.alpha(t.color.a);
                    Styles.black8.draw(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
                }
            });
            t.visible = false;
            t.touchable = Touchable.enabled;

            //Everything lives in ONE bounded column. Full-width rows let long localized strings
            //escape their cells and paint over the emblem, the tagline and the progress bar.
            contentWidth = Math.min(420f, Core.graphics.getWidth() / Scl.scl(1f) * 0.86f);
            Table content = new Table();
            t.add(content).center();

            //round emblem cut from the key art, in place of the old empty spacer
            content.add(new Element(){
                @Override public void draw(){
                    TextureRegion emblem = OrbitalTheme.mark();
                    if(emblem == null) return;
                    Draw.alpha(parentAlpha);
                    //Element coordinates are already scene units: applying Scl.scl here doubled
                    //the scale on Android and made the emblem overflow its own cell
                    float size = 112f;
                    Draw.rect(emblem, x + width / 2f, y + height / 2f, size, size);
                    Draw.reset();
                }
            }).size(112f, 112f).padBottom(8f).center().row();

            content.add(warningBar()).growX().height(16f).row();

            Cell<Label> nameCell = content.add("@loading").width(contentWidth).center();
            nameLabel = nameCell.get();
            nameLabel.setAlignment(Align.center);
            nameLabel.setWrap(true);
            nameLabel.setFontScale(mobile ? 0.75f : 0.9f);
            nameCell.padTop(8f).padBottom(4f).row();

            Label tagline = new Label(Core.bundle.get("loading.orbital.tagline"), Styles.outlineLabel);
            tagline.setAlignment(Align.center);
            tagline.setWrap(true);
            tagline.setFontScale(mobile ? 0.62f : 0.7f);
            tagline.setColor(OrbitalTheme.cyan);
            content.add(tagline).width(contentWidth).center().padBottom(10f).row();

            content.add(warningBar()).growX().height(16f).row();

            //hidden rows still reserve their size in scene2d, which pushed the visible block up;
            //collapse the cells until the progress/cancel controls are actually needed
            bar = new Bar();
            bar.visible = false;
            barCell = content.add(bar).size(0f, 0f);
            barCell.row();
            buttonCell = content.button("@cancel", OrbitalTheme.loadButton(), () -> {
                if(cancelListener != null){
                    cancelListener.run();
                }
            }).size(0f, 0f);
            button = buttonCell.get();
            button.visible = false;
            button.keyDown(key -> {
                if(cancelListener != null && (key == KeyCode.back || key == KeyCode.escape)){
                    cancelListener.run();
                }
            });
            table = t;
        });
    }

    /** Warning stripes in the key art's electric cyan instead of the global gold accent. */
    private static WarningBar warningBar(){
        WarningBar bar = new WarningBar();
        bar.setColor(OrbitalTheme.cyan);
        return bar;
    }

    public void toFront(){
        table.toFront();
    }

    public void setProgress(Floatp progress){
        bar.reset(0f);
        bar.visible = true;
        if(barCell != null){
            barCell.size(contentWidth, 40f).pad(3f).padTop(6f);
            if(table != null) table.invalidateHierarchy();
        }
        bar.set(() -> ((int)(progress.get() * 100) + "%"), progress, OrbitalTheme.cyan);
    }

    public void snapProgress(){
        bar.snap();
    }

    public void setProgress(float progress){
        progValue = progress;
        if(!bar.visible){
            setProgress(() -> progValue);
        }
    }

    public void showProgressBar(){
        if(!bar.visible){
            setProgress(() -> progValue);
        }
    }

    public boolean showingProgress(){
        return bar.visible;
    }


    public void setButton(Runnable listener){
        button.visible = true;
        if(buttonCell != null){
            buttonCell.size(Math.min(250f, contentWidth), 70f).pad(20f);
            if(table != null) table.invalidateHierarchy();
        }
        button.requestKeyboard();
        cancelListener = listener;
    }

    public void setText(String text){
        nameLabel.setText(text);
    }

    public void setText(String text, Color color){
        nameLabel.setText(text);
        nameLabel.setColor(color);
    }

    public void show(){
        show("@loading");
    }

    public void show(String text){
        button.visible = false;
        if(buttonCell != null) buttonCell.size(0f, 0f).pad(0f);
        cancelListener = null;
        bar.visible = false;
        if(barCell != null) barCell.size(0f, 0f).pad(0f);
        table.clearActions();
        table.touchable = Touchable.enabled;
        nameLabel.setColor(OrbitalTheme.cyan);
        nameLabel.setText(text);
        table.visible = true;
        table.color.a = 1f;
        table.toFront();
    }

    public boolean shown(){
        return table.visible;
    }

    public void hide(){
        table.clearActions();
        table.toFront();
        button.visible = false;
        table.touchable = Touchable.disabled;
        table.actions(Actions.fadeOut(0.5f), Actions.visible(false));

        if(Core.scene.getKeyboardFocus() == button){
            Core.scene.setKeyboardFocus(null);
        }
    }

}
