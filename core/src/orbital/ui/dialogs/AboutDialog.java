package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.ui.*;
import orbital.ui.Links.*;

import static orbital.Vars.*;

public class AboutDialog extends BaseDialog{
    Seq<String> contributors = new Seq<>();
    static ObjectSet<String> bannedItems = ObjectSet.with("google-play", "itch.io", "dev-builds", "f-droid");

    public AboutDialog(){
        super("@about.button");

        shown(() -> {
            contributors = Seq.with(Core.files.internal("contributors").readString("UTF-8").split("\n"));
            Core.app.post(this::setup);
        });

        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();
        buttons.clear();

        float h = Core.graphics.isPortrait() ? 90f : 80f;
        float w = Core.graphics.isPortrait() ? 400f : 650f;

        Table in = new Table();
        in.margin(12f);
        ScrollPane pane = new ScrollPane(in);
        pane.setFadeScrollBars(false);

        //Seção 1: Sobre o jogo
        section(in, "about.section.game", w);
        body(in, "about.game.text", w);

        //Seção 2: História e Origem do Projeto
        section(in, "about.section.history", w);
        body(in, "about.history.text", w);

        //Seção 3: Créditos & Colaboradores
        section(in, "about.section.credits", w);
        body(in, Core.bundle.format("about.credits.original", Links.originalRepo), w, 6f);
        body(in, Core.bundle.format("about.credits.fork", Links.forkRepo), w, 12f);

        //Seção 4: Licença GPLv3
        section(in, "about.section.license", w);
        body(in, "about.license.text", w);

        //Seção 5: Bibliotecas e licenças de terceiros
        section(in, "about.section.libraries", w);
        body(in, "about.libraries.text", w);

        //Seção 6: Links de Repositórios e Licença
        section(in, "about.section.links", w);

        Table linksTable = new Table();
        linksTable.left();
        addLink(linksTable, "about.links.original", Icon.github, Links.originalRepo);
        addLink(linksTable, "about.links.fork", Icon.github, Links.forkRepo);
        addLink(linksTable, "about.links.license", Icon.book, Links.licenseUrl);
        in.add(linksTable).growX().padBottom(6f).row();
        in.add(Core.bundle.get("about.links.files")).width(w).wrap().color(Color.lightGray).left().padBottom(16f).row();

        //Seção 7: Links da Comunidade
        section(in, "about.section.community", w);

        for(LinkEntry link : Links.getLinks()){
            if((ios || steam) && bannedItems.contains(link.name)){
                continue;
            }

            Table table = new Table(Styles.grayPanel);
            table.margin(0);
            table.table(img -> {
                img.image().height(h - 5).width(40f).color(link.color);
                img.row();
                img.image().height(5).width(40f).color(link.color.cpy().mul(0.6f, 0.6f, 0.8f, 1f));
            }).expandY();

            table.table(i -> {
                i.background(Styles.grayPanel);
                i.image(link.icon);
            }).size(h - 5, h);

            table.table(inset -> {
                inset.add("[accent]" + link.title).growX().left();
                inset.row();
                inset.labelWrap(link.description).width(w - 100f - h).color(Color.lightGray).growX();
            }).padLeft(8);

            table.button(Icon.link, Styles.clearNonei, () -> {
                if(link.name.equals("wiki")) Events.fire(Trigger.openWiki);

                if(!Core.app.openURI(link.link)){
                    ui.showErrorMessage("@linkfail");
                    Core.app.setClipboardText(link.link);
                }
            }).size(h - 5, h);

            in.add(table).size(w, h).padTop(5).row();
        }

        shown(() -> Time.run(1f, () -> Core.scene.setScrollFocus(pane)));

        cont.add(pane).growX();

        addCloseButton();

        buttons.button("@credits", this::showCredits).size(200f, 64f);
    }

    private void section(Table table, String key, float width){
        table.add("[accent]" + Core.bundle.get(key) + "[]").style(Styles.outlineLabel).width(width).left().padBottom(4f).row();
    }

    private void body(Table table, String key, float width){
        body(table, Core.bundle.get(key), width, 12f);
    }

    private void body(Table table, String text, float width, float bottom){
        table.add(text).width(width).wrap().color(Color.lightGray).left().padBottom(bottom).row();
    }

    private void addLink(Table table, String key, Drawable icon, String url){
        table.button(Core.bundle.get(key), icon, Styles.flatt, () -> {
            if(!Core.app.openURI(url)){
                ui.showErrorMessage("@linkfail");
                Core.app.setClipboardText(url);
            }
        }).growX().height(44f).padBottom(4f).row();
    }

    public void showCredits(){
        BaseDialog dialog = new BaseDialog("@credits");
        dialog.addCloseButton();
        dialog.cont.add("@credits.text").fillX().wrap().get().setAlignment(Align.center);
        dialog.cont.row();
        if(!contributors.isEmpty()){
            dialog.cont.image().color(Pal.accent).fillX().height(3f).pad(3f);
            dialog.cont.row();
            dialog.cont.add("@contributors");
            dialog.cont.row();
            dialog.cont.pane(new Table(){{
                int i = 0;
                left();
                for(String c : contributors){
                    add("[lightgray]" + c).left().pad(3).padLeft(6).padRight(6);
                    if(++i % 3 == 0){
                        row();
                    }
                }
            }});
        }
        dialog.show();
    }
}
