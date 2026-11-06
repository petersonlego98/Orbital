package orbital.ui.fragments;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import orbital.graphics.*;
import orbital.ui.*;

public class LoadingFragment{
    private Table table;
    private TextButton button;
    private Bar bar;
    private Label nameLabel;
    private @Nullable Runnable cancelListener;
    private float progValue;

    public void build(Group parent){
        parent.fill(t -> {
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

            //round emblem cut from the key art, in place of the old empty spacer
            t.add(new Element(){
                @Override public void draw(){
                    TextureRegion emblem = OrbitalTheme.mark();
                    if(emblem == null) return;
                    Draw.alpha(parentAlpha);
                    float size = Scl.scl(132f);
                    Draw.rect(emblem, x + width / 2f, y + height / 2f, size, size);
                    Draw.reset();
                }
            }).size(148f, 148f).padBottom(6f).row();

            t.add(warningBar()).growX().height(24f);
            t.row();
            nameLabel = t.add("@loading").pad(10f).style(Styles.outlineLabel).color(OrbitalTheme.cyan).get();
            t.row();

            Label tagline = new Label(Core.bundle.get("loading.orbital.tagline"), Styles.outlineLabel);
            tagline.setFontScale(0.7f);
            tagline.setColor(OrbitalTheme.cyan);
            t.add(tagline).padBottom(8f).row();

            t.add(warningBar()).growX().height(24f);
            t.row();

            nameLabel.setText("@loading");

            bar = t.add(new Bar()).pad(3).padTop(6).size(500f, 40f).visible(false).get();
            t.row();
            button = t.button("@cancel", OrbitalTheme.loadButton(), () -> {
                if(cancelListener != null){
                    cancelListener.run();
                }
            }).pad(20).size(250f, 70f).visible(false).get();
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
        cancelListener = null;
        bar.visible = false;
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
