package orbital.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.core.*;
import orbital.game.EventType.*;
import orbital.game.TestNewFeaturesMode;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.ui.*;

import static orbital.Vars.*;
import static orbital.gen.Tex.*;

public class MenuFragment{
    private Table container, submenu;
    private Button currentMenu;
    private MenuRenderer renderer;
    private Seq<MenuButton> customButtons = new Seq<>();
    public Seq<MenuButton> desktopButtons = null;

    public void build(Group parent){
        renderer = new MenuRenderer();

        Group group = new WidgetGroup();
        group.setFillParent(true);
        group.visible(() -> !ui.editor.isShown());
        parent.addChild(group);

        parent = group;

        //The key art replaces the procedural terrain as the menu backdrop. The old renderer is
        //kept as a fallback so headless/early-startup runs without the loose textures still work.
        parent.fill((x, y, w, h) -> {
            if(OrbitalTheme.available()){
                OrbitalTheme.renderBackdrop();
            }else{
                renderer.render();
            }
        });

        parent.fill(c -> {
            c.pane(Styles.noBarPane, cont -> {
                container = cont;
                cont.name = "menu container";

                if(!mobile){
                    c.left();
                    buildDesktop();
                    Events.on(ResizeEvent.class, event -> buildDesktop());
                }else{
                    buildMobile();
                    Events.on(ResizeEvent.class, event -> buildMobile());
                }
            }).with(pane -> {
                pane.setOverscroll(false, false);
            }).grow();
        });

        parent.fill(c -> c.bottom().right().button(Icon.discord, new ImageButtonStyle(){{
            up = discordBanner;
        }}, ui.discord::show).visible(() -> !ui.consolefrag.shown()).marginTop(9f).marginLeft(10f).tooltip("@discord").size(84, 45).name("discord"));

        //info icon
        if(mobile){
            //left/right gutter areas
            parent.fill((x, y, w, h) -> {
                x = 0f;
                y = 0f;
                w = Core.graphics.getWidth();
                h = Core.graphics.getHeight();
                if(Core.scene.marginLeft > 0){
                    paneRight.draw(x, y, Core.scene.marginLeft, h);
                }

                if(Core.scene.marginRight > 0){
                    paneLeft.draw(x + w - Core.scene.marginRight, y, Core.scene.marginRight, h);
                }

                if(Core.scene.marginBottom > 0){
                    Tex.paneTop.draw(Core.scene.marginLeft, 0, Core.graphics.getWidth() - Core.scene.marginRight - Core.scene.marginLeft, Core.scene.marginBottom);
                }
            });

            parent.fill(c -> {
                c.bottom().left();
                c.button(Icon.terminal, () -> ui.consolefrag.toggleMobile()).visible(() -> !ui.consolefrag.shown() && Core.settings.getBool("console")).pad(4f).size(60f).left().row();

                c.button("", new TextButtonStyle(){{
                    font = Fonts.def;
                    fontColor = Color.white;
                    up = infoBanner;
                }}, ui.about::show).size(84, 45).visible(() -> !ui.consolefrag.shown()).name("info");
            });
        }else if(becontrol.active()){
            parent.fill(c -> c.bottom().right().button("@be.check", Icon.refresh, () -> {
                ui.loadfrag.show();
                becontrol.checkUpdate(result -> {
                    ui.loadfrag.hide();
                    if(!result){
                        ui.showInfo("@be.noupdates");
                    }
                });
            }).size(200, 60).name("becheck").update(t -> {
                t.getLabel().setColor(becontrol.isUpdateAvailable() ? Tmp.c1.set(OrbitalTheme.silver).lerp(OrbitalTheme.cyan, Mathf.absin(5f, 1f)) : OrbitalTheme.silver);
            }));
        }

        //amber for development builds, cold silver for release builds - both from the key art
        String versionText = ((Version.build == -1) ? "[#ff8a24]" : "[#cfd8f2cc]") + Version.combined();
        parent.fill((x, y, w, h) -> {
            TextureRegion logo = menuLogo();
            float width = Core.graphics.getWidth(), height = Core.graphics.getHeight() - Core.scene.marginTop;
            float logow = logoWidth();
            float logoh = logoHeight();

            float fx = (int)(width / 2f);
            float fy = (int)(height - 6 - logoh) + logoh / 2 - (Core.graphics.isPortrait() ? Scl.scl(30f) : 0f);
            if(Core.settings.getBool("macnotch") ){
                fy -= Scl.scl(macNotchHeight);
            }

            Draw.color();
            Draw.rect(logo, fx, fy, logow, logoh);

            Fonts.outline.setColor(OrbitalTheme.silver);
            Fonts.outline.draw(versionText, fx, fy - logoh/2f - Scl.scl(7f), Align.center);
        }).touchable = Touchable.disabled;

        //Atribuição resumida no menu principal, logo abaixo do logo/versão.
        //
        //Em telas móveis o painel é omitido: o menu tem pouco espaço vertical e painel e
        //botões acabavam se sobrepondo conforme a escala de UI cresce. A atribuição
        //continua disponível no botão de informação (canto inferior esquerdo), que abre o
        //diálogo "Sobre" com o mesmo texto e os links dos repositórios.
        if(!mobile){
            parent.fill(t -> {
                t.top();
                t.touchable = Touchable.childrenOnly;
                float logoh = logoHeight();
                float topOffset = logoh + Scl.scl(Core.graphics.isPortrait() ? 58f : 38f);
                float maxWidth = Math.min(Core.graphics.getWidth() - Scl.scl(24f), Scl.scl(620f));

                t.table(OrbitalTheme.panel(), banner -> {
                    banner.margin(8f);

                    //Labels are created and sized up front so their wrapped height is known before the table lays out the rows.
                    Label title = new Label(Core.bundle.get("menu.attribution.title"), Styles.outlineLabel);
                    title.setFontScale(0.9f);
                    title.setWrap(true);
                    title.setAlignment(Align.center);
                    title.setColor(OrbitalTheme.silver);
                    title.setWidth(maxWidth);

                    Label disclaimer = new Label(Core.bundle.get("menu.attribution.disclaimer"), Styles.outlineLabel);
                    disclaimer.setFontScale(0.75f);
                    disclaimer.setWrap(true);
                    disclaimer.setAlignment(Align.center);
                    disclaimer.setColor(OrbitalTheme.ice);
                    disclaimer.setWidth(maxWidth);

                    banner.add(title).width(maxWidth).center().row();
                    banner.add(disclaimer).width(maxWidth).center().padTop(7f).row();
                    banner.button(Core.bundle.get("menu.attribution.link"), Icon.link, Styles.cleart, () -> {
                        Core.app.openURI(Links.originalRepo);
                    }).fontScale(0.75f).height(30f).padTop(6f);
                }).width(maxWidth).padTop(topOffset).row();

                //Absorbing row: it takes the space left below the banner, so the banner stays
                //anchored right under the logo instead of drifting to the middle of the screen
                //as the UI scale grows.
                t.add().growY();
            });
        }
    }

    /** The wordmark cut from the key art, falling back to the atlas logo when the texture is missing. */
    private TextureRegion menuLogo(){
        TextureRegion themed = OrbitalTheme.wordmark();
        return themed != null ? themed : Core.atlas.find("logo");
    }

    /** Logo width in screen units, capped so the mark leaves room for the version and attribution on short windows. */
    private float logoWidth(){
        TextureRegion logo = menuLogo();
        float width = Math.min(logo.width * Scl.scl(1) * logo.scale, Core.graphics.getWidth() - Scl.scl(20));
        //the wordmark is a wide banner, so it needs a taller cap than the old compact logo
        float maxHeight = (Core.graphics.getHeight() - Core.scene.marginTop) * (OrbitalTheme.wordmark() != null ? 0.17f : 0.13f);
        if(maxHeight > 0f){
            width = Math.min(width, maxHeight * logo.width / logo.height);
        }
        return width;
    }

    private float logoHeight(){
        TextureRegion logo = menuLogo();
        return logoWidth() * logo.height / logo.width;
    }

    private void buildMobile(){
        container.clear();
        container.name = "buttons";
        //The pane's viewport is measured in scene units, and Scl.scl(1) is how many pixels one
        //unit takes. Sizing the content with the raw pixel size made it grow relative to the
        //viewport as UI scaling increases, which pushed the second row of buttons off-screen.
        container.setSize(Core.graphics.getWidth() / Scl.scl(1f), Core.graphics.getHeight() / Scl.scl(1f));

        float size = 120f;
        container.defaults().size(size).pad(5).padTop(4f);

        MobileButton
            play = new MobileButton(Icon.play, "@starmap", () -> checkPlay(ui.planet::show)),
            expedition = new MobileButton(Icon.terrain, "Expedição", () -> checkPlay(ui.expedition::show)),
            testFeatures = new MobileButton(Icon.terrain, "Novidades", () -> checkPlay(TestNewFeaturesMode::start)),
            custom = new MobileButton(Icon.rightOpenOut, "@customgame", () -> checkPlay(ui.custom::show)),
            maps = new MobileButton(Icon.download, "@loadgame", () -> checkPlay(ui.load::show)),
            join = new MobileButton(Icon.add, "@joingame", () -> checkPlay(ui.join::show)),
            editor = new MobileButton(Icon.terrain, "@editor", () -> checkPlay(ui.maps::show)),
            tools = new MobileButton(Icon.settings, "@settings", ui.settings::show),
            mods = new MobileButton(Icon.book, "@mods", ui.mods::show),
            exit = new MobileButton(Icon.exit, "@quit", () -> Core.app.exit()),
            about = new MobileButton(Icon.info, "@about.button", ui.about::show);

        Seq<MobileButton> customs = customButtons.map(b -> new MobileButton(b.icon, b.text, b.runnable == null ? () -> {} : b.runnable));

        if(!Core.graphics.isPortrait()){
            container.marginTop(60f);
            container.add(play);
            container.add(join);
            container.add(custom);
            container.add(maps);
            container.add(expedition);
            container.add(testFeatures);
            // add odd custom buttons
            for(int i = 1; i < customs.size; i += 2){
                container.add(customs.get(i));
            }
            container.row();

            container.add(editor);
            container.add(tools);
            container.add(mods);
            // add even custom buttons (before the exit button)
            for(int i = 0; i < customs.size; i += 2){
                container.add(customs.get(i));
            }
            container.add(ios ? about : exit);
        }else{
            container.marginTop(0f);
            container.add(play);
            container.add(maps);
            container.row();
            container.add(expedition);
            container.row();
            container.add(testFeatures);
            container.row();
            container.add(custom);
            container.add(join);
            container.row();
            container.add(editor);
            container.add(tools);
            container.row();
            container.add(mods);
            // add custom buttons
            for(int i = 0; i < customs.size; i++){
                container.add(customs.get(i));
                if(i % 2 == 0) container.row();
            }
            container.add(ios ? about : exit);
        }
    }

    private void buildDesktop(){
        container.clear();
        //same as buildMobile: keep the content in units so it tracks the pane's viewport
        container.setSize(Core.graphics.getWidth() / Scl.scl(1f), Core.graphics.getHeight() / Scl.scl(1f));

        float width = 230f;
        Drawable background = OrbitalTheme.panel();

        container.left();
        container.add().width(Core.graphics.getWidth()/10f);
        container.table(background, t -> {
            t.defaults().width(width).height(70f);
            t.name = "buttons";

            if(desktopButtons == null){
                desktopButtons = Seq.with(
                    new MenuButton("@play", Icon.play,
                        new MenuButton("@starmap", Icon.planet, () -> checkPlay(ui.planet::show)),
                        new MenuButton("Expedição asteroidária", Icon.terrain, () -> checkPlay(ui.expedition::show)),
                        new MenuButton("@mode.testfeatures.name", Icon.terrain, () -> checkPlay(TestNewFeaturesMode::start)),
                        new MenuButton("@joingame", Icon.add, () -> checkPlay(ui.join::show)),
                        new MenuButton("@customgame", Icon.terrain, () -> checkPlay(ui.custom::show)),
                        new MenuButton("@loadgame", Icon.download, () -> checkPlay(ui.load::show))
                    ),
                    new MenuButton("@database.button", Icon.menu,
                        new MenuButton("@schematics", Icon.paste, ui.schematics::show),
                        new MenuButton("@database", Icon.book, ui.database::show),
                        new MenuButton("@about.button", Icon.info, ui.about::show)
                    ),
                    new MenuButton("@editor", Icon.terrain, () -> checkPlay(ui.maps::show)), steam ? new MenuButton("@workshop", Icon.steam, platform::openWorkshop) : null,
                    new MenuButton("@mods", Icon.book, ui.mods::show),
                    new MenuButton("@settings", Icon.settings, ui.settings::show)
                );
            }

            buttons(t, desktopButtons.toArray(MenuButton.class));
            buttons(t, customButtons.toArray(MenuButton.class));
            buttons(t, new MenuButton("@quit", Icon.exit, Core.app::exit));
        }).width(width).growY();

        container.table(background, t -> {
            submenu = t;
            t.name = "submenu";
            t.color.a = 0f;
            t.top();
            t.defaults().width(width).height(70f);
            t.visible(() -> !t.getChildren().isEmpty());

        }).width(width).growY();
    }

    private void checkPlay(Runnable run){
        if(!mods.hasContentErrors()){
            run.run();
        }else{
            ui.showInfo("@mod.noerrorplay");
        }
    }

    private void fadeInMenu(){
        submenu.clearActions();
        submenu.actions(Actions.alpha(1f, 0.15f, Interp.fade));
    }

    private void fadeOutMenu(){
        //nothing to fade out
        if(submenu.getChildren().isEmpty()){
            return;
        }

        submenu.clearActions();
        submenu.actions(Actions.alpha(1f), Actions.alpha(0f, 0.2f, Interp.fade), Actions.run(() -> submenu.clearChildren()));
    }

    private void buttons(Table t, MenuButton... buttons){
        for(MenuButton b : buttons){
            if(b == null) continue;
            Button[] out = {null};
            out[0] = t.button(b.text, b.icon, OrbitalTheme.menuButton(), () -> {
                if(currentMenu == out[0]){
                    currentMenu = null;
                    fadeOutMenu();
                }else{
                    if(b.submenu != null && b.submenu.any()){
                        currentMenu = out[0];
                        submenu.clearChildren();
                        fadeInMenu();
                        //correctly offset the button
                        submenu.add().height((Core.graphics.getHeight() - Core.scene.marginTop - Core.scene.marginBottom - out[0].getY(Align.topLeft)) / Scl.scl(1f));
                        submenu.row();
                        buttons(submenu, b.submenu.toArray());
                    }else{
                        currentMenu = null;
                        fadeOutMenu();
                        b.runnable.run();
                    }
                }
            }).marginLeft(11f).get();
            out[0].update(() -> out[0].setChecked(currentMenu == out[0]));
            t.row();
        }
    }

    /** Adds a custom button to the menu. */
    public void addButton(String text, Drawable icon, Runnable callback){
        addButton(new MenuButton(text, icon, callback));
    }

    /** Adds a custom button to the menu. */
    public void addButton(String text, Runnable callback){
        addButton(text, Styles.none, callback);
    }

    /**
     * Adds a custom button to the menu.
     * If {@link MenuButton#submenu} is null or the player is on mobile, {@link MenuButton#runnable} is invoked on click.
     * Otherwise, {@link MenuButton#submenu} is shown.
     */
    public void addButton(MenuButton button){
        customButtons.add(button);
    }

    /** Represents a menu button definition. */
    public static class MenuButton{
        public final Drawable icon;
        public final String text;
        /** Runnable ran when the button is clicked. Ignored on desktop if {@link #submenu} is not null. */
        public final Runnable runnable;
        /** Submenu shown when this button is clicked. Used instead of {@link #runnable} on desktop. */
        public final @Nullable Seq<MenuButton> submenu;

        /** Constructs a simple menu button, which behaves the same way on desktop and mobile. */
        public MenuButton(String text, Drawable icon, Runnable runnable){
            this.icon = icon;
            this.text = text;
            this.runnable = runnable;
            this.submenu = null;
        }

        /** Constructs a button that runs the runnable when clicked on mobile or shows the submenu on desktop. */
        public MenuButton(String text, Drawable icon, Runnable runnable, MenuButton... submenu){
            this.icon = icon;
            this.text = text;
            this.runnable = runnable;
            this.submenu = submenu != null ? Seq.with(submenu) : null;
        }

        /** Comstructs a desktop-only button; used internally. */
        MenuButton(String text, Drawable icon, MenuButton... submenu){
            this.icon = icon;
            this.text = text;
            this.runnable = () -> {};
            this.submenu = submenu != null ? Seq.with(submenu) : null;
        }
    }
}
