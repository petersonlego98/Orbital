package orbital.ui;

import arc.scene.style.*;
import arc.scene.ui.*;
import arc.util.*;

public class MobileButton extends ImageButton{

    public MobileButton(Drawable icon, String text, Runnable listener){
        super(icon);
        clicked(listener);
        row();
        //The label must fit inside the fixed-size button. Long names such as
        //"Teste Novos Recursos" would otherwise wrap to three lines and spill
        //past the bottom edge, so scale the text down a little.
        Label label = add(text).growX().wrap().center().fontScale(0.85f).get();
        label.setAlignment(Align.center, Align.center);
    }
}
