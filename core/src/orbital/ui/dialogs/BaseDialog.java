package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.util.*;
import orbital.core.GameState.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.graphics.*;

import static orbital.Vars.*;

public class BaseDialog extends Dialog{
    protected boolean wasPaused;
    /** If true, this dialog will pause the game while open. */
    protected boolean shouldPause;
    protected Image titleImage;

    public BaseDialog(String title, DialogStyle style){
        super(title, style);
        setFillParent(true);
        //this dialog is the whole stage: centring it is not only meaningless, it is harmful. While
        //the Android system bars show, the stage carries insets, and centring a full-screen dialog
        //against those insets shifted the menu up by half the inset on every frame - the title and
        //the first rows ended up off the top of the screen.
        setCentered(false);
        this.title.setAlignment(Align.center);
        titleTable.row();
        titleImage = titleTable.image(Tex.whiteui, Pal.accent).growX().height(3f).pad(4f).get();

        hidden(() -> {
            if(shouldPause && state.isGame() && !net.active() && !wasPaused){
                state.set(State.playing);
            }
            Sounds.uiBack.play();
        });

        shown(() -> {
            if(shouldPause && state.isGame() && !net.active()){
                wasPaused = state.is(State.paused);
                state.set(State.paused);
            }
        });
    }

    public BaseDialog(String title){
        this(title, Core.scene.getStyle(DialogStyle.class));
    }

    public void setTitleColor(Color color){
        titleImage.setColor(color);
        title.setColor(color);
    }

    /**
     * Puts the dialog at the origin of the stage.
     *
     * <p>The stock implementation centres the dialog between the stage margins, which is what
     * every caller wants for a floating window. This dialog fills the stage, so its place is the
     * origin - and on Android the margins are non-zero while the system bars are up, so the stock
     * centring quietly moved the whole menu off the top of the screen.</p>
     */
    @Override
    public void centerWindow(){
        setPosition(0f, 0f);
    }

    /** Places the buttons as an overlay on top of the content. Used when the content can be scrolled through.*/
    protected void makeButtonOverlay(){
        clearChildren();
        add(titleTable).growX().row();
        stack(cont, buttons).grow();
        buttons.bottom();
    }

    protected void onResize(Runnable run){
        Events.on(ResizeEvent.class, event -> {
            //ignore resize events while the Android text input dialog is shown - this does lead to buggy layout when rotated, but it's better than clearing the text. I don't know of a better solution to the problem
            if(isShown() && Core.scene.getDialog() == this && !Core.input.isShowingTextInput()){
                run.run();
                updateScrollFocus();
            }
        });
    }

    public void addCloseListener(){
       closeOnBack();
    }

    public void addCloseButton(float width){
        buttons.defaults().size(width, 64f);
        buttons.button("@back", Icon.left, this::hide).size(width, 64f);

        addCloseListener();
    }

    @Override
    public void addCloseButton(){
        addCloseButton(210f);
    }
}
