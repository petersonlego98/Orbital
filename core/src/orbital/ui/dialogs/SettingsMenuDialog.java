package orbital.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import orbital.content.*;
import orbital.content.TechTree.*;
import orbital.core.*;
import orbital.core.RendererPreference;
import orbital.ctype.*;
import orbital.engine.OrbitalEngine;
import orbital.engine.threading.OrbitalThreadConfig;
import orbital.engine.units.UnitMovementTick;
import orbital.engine.validation.EngineCalibration;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.input.*;
import orbital.type.*;
import orbital.ui.*;

import java.io.*;
import java.util.zip.*;

import static arc.Core.*;
import static orbital.Vars.*;

public class SettingsMenuDialog extends BaseDialog{
    public SettingsTable graphics;
    /** Desktop-only renderer settings; see {@link RendererPreference}. */
    public SettingsTable advancedgraphics;
    public SettingsTable game;
    /** construction automation: who builds the blocks the player queues. */
    public SettingsTable constructions;
    public SettingsTable sound;
    public SettingsTable dev;
    public SettingsTable main;

    private Table prefs;
    private Table menu;
    private BaseDialog dataDialog;
    private BaseDialog planetDataDialog;
    private Planet planet = Planets.qatia;
    private Seq<SettingsCategory> categories = new Seq<>();

    public SettingsMenuDialog(){
        super(bundle.get("settings", "Settings"));
        addCloseButton();

        cont.add(main = new SettingsTable());
        shouldPause = true;

        shown(() -> {
            back();
            rebuildMenu();
        });

        int[] lastRebuildSize = {Core.graphics.getWidth(), Core.graphics.getHeight()};
        onResize(() -> {
            if(lastRebuildSize[0] != Core.graphics.getWidth() || lastRebuildSize[1] != Core.graphics.getHeight()){
                graphics.rebuild();
                advancedgraphics.rebuild();
                sound.rebuild();
                game.rebuild();
                constructions.rebuild();
                dev.rebuild();
                updateScrollFocus();
                lastRebuildSize[0] = Core.graphics.getWidth();
                lastRebuildSize[1] = Core.graphics.getHeight();
            }
        });

        cont.clearChildren();
        cont.remove();
        buttons.remove();

        menu = new Table(Tex.button);

        game = new SettingsTable();
        constructions = new SettingsTable();
        graphics = new SettingsTable();
        advancedgraphics = new SettingsTable();
        sound = new SettingsTable();
        dev = new SettingsTable();

        prefs = new Table();
        prefs.top();
        prefs.margin(14f);

        rebuildMenu();

        prefs.clearChildren();
        prefs.add(menu);

        planetDataDialog = new BaseDialog("@settings.data");
        planetDataDialog.addCloseButton();

        planetDataDialog.cont.table(Tex.button, t -> {
            t.defaults().size(280f, 60f).left();
            TextButtonStyle style = Styles.flatt;

            t.button(bundle.format("settings.planetselect", "[#" + planet.iconColor + "]" + planet.localizedName), Icon.planet, style, () -> {
                BaseDialog dialog = new BaseDialog("");
                dialog.cont.pane(p -> {
                    p.background(Tex.button);
                    int i = 0;

                    for(var plan : content.planets()){
                        if(plan.generator == null || plan.sectors.size == 0 || !plan.accessible) continue;

                        p.button(plan.localizedName, Styles.flatTogglet, () -> {
                            planet = plan;
                            dialog.hide();
                        }).size(110f, 45f).checked(planet == plan);

                        if(++i % 4 == 0){
                            p.row();
                        }
                    }
                });
                dialog.setFillParent(false);
                dialog.addCloseButton();
                dialog.show();
            }).marginLeft(4).get().getLabel().setText(() -> bundle.format("settings.planetselect", "[#" + planet.iconColor + "]" + planet.localizedName));

            t.row();

            t.button("@settings.clearplanetresearch", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", bundle.format("settings.clearplanetresearch.confirm", planet.localizedName), () -> {
                    universe.clearLoadoutInfo();
                    for(TechNode node : TechTree.all){
                        if(node.rootNode == planet.techTree){
                            node.reset();
                        }
                    }
                    content.each(c -> {
                        if(c instanceof UnlockableContent u && u.databaseTabs.contains(planet)){
                            u.clearUnlock();
                        }
                    });
                    settings.remove("unlocks");
                });
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearplanetcampaignsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", bundle.format("settings.clearplanetcampaignsaves.confirm", planet.localizedName), () -> {
                    planet.clearStats();
                    boolean any = false;
                    for(var sec : planet.sectors){
                        sec.clearInfo();
                        if(sec.save != null){
                            any = true;
                            sec.save.delete();
                            sec.save = null;
                        }
                    }
                    if(any){
                        planet.reloadMeshAsync();
                    }

                    for(var slot : control.saves.getSaveSlots().copy()){
                        if(slot.isSector() && slot.getSector().planet == planet){
                            slot.delete();
                        }
                    }
                });
            }).marginLeft(4);

            t.row();
        });

        dataDialog = new BaseDialog("@settings.data");
        dataDialog.addCloseButton();

        dataDialog.cont.table(Tex.button, t -> {
            t.defaults().size(280f, 60f).left();
            TextButtonStyle style = Styles.flatt;

            t.button("@settings.cleardata", Icon.trash, style, () -> ui.showConfirm("@confirm", "@settings.clearall.confirm", () -> {
                ObjectMap<String, Object> map = new ObjectMap<>();
                for(String value : Core.settings.keys()){
                    if(value.contains("usid") || value.contains("uuid")){
                        map.put(value, Core.settings.get(value, null));
                    }
                }
                Core.settings.clear();
                Core.settings.putAll(map);

                for(Fi file : dataDirectory.list()){
                    file.deleteDirectory();
                }

                Core.app.exit();
            })).marginLeft(4);

            t.row();

            t.button("@settings.clearplanetdata", Icon.trash, style, () -> planetDataDialog.show()).marginLeft(4).row();

            t.button("@settings.clearsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearsaves.confirm", () -> {
                    control.saves.deleteAll();
                });
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearresearch", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearresearch.confirm", () -> {
                    universe.clearLoadoutInfo();
                    for(TechNode node : TechTree.all){
                        node.reset();
                    }
                    content.each(c -> {
                        if(c instanceof UnlockableContent u){
                            u.clearUnlock();
                        }
                    });
                    settings.remove("unlocks");
                });
            }).marginLeft(4);

            t.row();

            t.button("@settings.clearcampaignsaves", Icon.trash, style, () -> {
                ui.showConfirm("@confirm", "@settings.clearcampaignsaves.confirm", () -> {
                    for(var planet : content.planets()){
                        planet.clearStats();
                        boolean any = false;
                        for(var sec : planet.sectors){
                            sec.clearInfo();
                            if(sec.save != null){
                                any = true;
                                sec.save.delete();
                                sec.save = null;
                            }
                        }
                        if(any){
                            planet.reloadMeshAsync();
                        }
                    }

                    for(var slot : control.saves.getSaveSlots().copy()){
                        if(slot.isSector()){
                            slot.delete();
                        }
                    }
                });
            }).marginLeft(4);

            t.row();

            t.button("@data.export", Icon.upload, style, () -> {
                FileChooser.export("orbital-data-export", "zip", this::exportData);
            }).marginLeft(4);

            t.row();

            t.button("@data.import", Icon.download, style, () -> ui.showConfirm("@confirm", "@data.import.confirm", () -> FileChooser.open("zip").submit(file -> {
                try{
                    importData(file);
                    mapPreviewDirectory.deleteDirectory();
                    control.saves.resetSave();
                    state = new GameState();
                    Core.app.exit();
                }catch(IllegalArgumentException e){
                    ui.showErrorMessage("@data.invalid");
                }catch(Exception e){
                    Log.err(e);
                    if(e.getMessage() == null || !e.getMessage().contains("too short")){
                        ui.showException(e);
                    }else{
                        ui.showErrorMessage("@data.invalid");
                    }
                }
            }))).marginLeft(4);

            if(!mobile){
                t.row();
                t.button("@data.openfolder", Icon.folder, style, () -> Core.app.openFolder(Core.settings.getDataDirectory().absolutePath())).marginLeft(4);
            }

            t.row();

            t.button("@crash.export", Icon.upload, style, () -> {
                if(settings.getDataDirectory().child("crashes").list().length == 0 && !settings.getDataDirectory().child("last_log.txt").exists()){
                    ui.showInfo("@crash.none");
                }else{
                    FileChooser.export("logs", "txt", file -> file.writeString(getLogs()));
                }
            }).marginLeft(4);
        });

        row();
        pane(prefs).grow().top();
        row();
        add(buttons).fillX();

        addSettings();
    }

    String getLogs(){
        Fi log = settings.getDataDirectory().child("last_log.txt");

        StringBuilder out = new StringBuilder();
        for(Fi fi : settings.getDataDirectory().child("crashes").list()){
            out.append(fi.name()).append("\n\n").append(fi.readString()).append("\n");
        }

        if(log.exists()){
            out.append("\nlast log:\n").append(log.readString());
        }

        return out.toString();
    }

    /** Adds a custom settings category, with the icon being the specified region. */
    public void addCategory(String name, @Nullable String region, Cons<SettingsTable> builder){
        categories.add(new SettingsCategory(name, region == null ? null : new TextureRegionDrawable(atlas.find(region)), builder));
    }

    /** Adds a custom settings category, for use in mods. The specified consumer should add all relevant mod settings to the table. */
    public void addCategory(String name, @Nullable Drawable icon, Cons<SettingsTable> builder){
        categories.add(new SettingsCategory(name, icon, builder));
    }

    /** Adds a custom settings category, for use in mods. The specified consumer should add all relevant mod settings to the table. */
    public void addCategory(String name, Cons<SettingsTable> builder){
        addCategory(name, (Drawable)null, builder);
    }

    public Seq<SettingsCategory> getCategories(){
        return categories;
    }

    /** Shared handler for the automatic DPI switch on desktop and Android. */
    private void onDpiToggle(boolean automatic){
        int fallback = android ? 125 : 100;
        if(automatic){
            //The current value is the manual scale the user had before enabling automatic mode;
            //keep it for when the option is turned off again.
            DpiPreference.setManualScale(settings.getInt("uiscale", fallback));
        }else{
            //The persisted uiscale currently holds the detected DPI value. Restore the manual
            //choice so turning this option off has a visible effect after restart.
            settings.put("uiscale", DpiPreference.manualScale(fallback));
        }
        ui.showInfo(bundle.get("settings.autodpi.restart"));
    }

    void rebuildMenu(){
        menu.clearChildren();

        TextButtonStyle style = Styles.flatt;

        float marg = 8f, isize = iconMed;

        menu.defaults().size(300f, 60f);
        menu.button("@settings.game", Icon.settings, style, isize, () -> visible(0)).marginLeft(marg).row();
        menu.button("@settings.constructions", Icon.hammer, style, isize, () -> visible(1)).marginLeft(marg).row();
        menu.button("@settings.graphics", Icon.image, style, isize, () -> visible(2)).marginLeft(marg).row();
        if(!mobile){
            menu.button("@settings.advancedgraphics", Icon.terminal, style, isize, () -> visible(3)).marginLeft(marg).row();
        }
        menu.button("@settings.sound", Icon.volumeUp, style, isize, () -> visible(4)).marginLeft(marg).row();
        menu.button("@settings.language", Icon.chat, style, isize, ui.language::show).marginLeft(marg).row();
        //the controls menu is now relevant on the phone too: the analog stick, the module
        //number keys and the gamepad switches live there, and a Bluetooth keyboard or a
        //controller is the only thing missing to rebind
        menu.button("@settings.controls", Icon.move, style, isize, ui.controls::show).marginLeft(marg).row();

        menu.button("@settings.data", Icon.save, style, isize, () -> dataDialog.show()).marginLeft(marg).row();
        menu.button("@settings.dev", Icon.fileCode, style, isize, () -> visible(5)).marginLeft(marg).row();
        //ui.about is created after this dialog, so the reference must be resolved lazily.
        menu.button("@about.button", Icon.info, style, isize, () -> ui.about.show()).marginLeft(marg).row();

        int i = 6;
        for(var cat : categories){
            int index = i;
            if(cat.icon == null){
                menu.button(cat.name, style, () -> visible(index)).marginLeft(marg).row();
            }else{
                menu.button(cat.name, cat.icon, style, isize, () -> visible(index)).with(b -> ((Image)b.getChildren().get(1)).setScaling(Scaling.fit)).marginLeft(marg).row();
            }
            i++;
        }
    }

    void addSettings(){
        //construction automation: default is the builder units doing the work
        constructions.checkPref("autobuild", true);
        //the classic fixed build menu is opt-in; the command tablet is the default interface
        constructions.checkPref(BuildMenuPreference.oldMenuKey, false);
        //opt-in: placing a factory also queues the transport routes it needs
        constructions.checkPref(orbital.engine.world.AutoInfrastructure.key, false);

        sound.checkPref("alwaysmusic", false);
        sound.sliderPref("musicvol", 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("sfxvol", 100, 0, 100, 1, i -> i + "%");
        sound.sliderPref("ambientvol", 100, 0, 100, 1, i -> i + "%");

        game.sliderPref("saveinterval", 60, 10, 5 * 120, 10, i -> Core.bundle.format("setting.seconds", i));

        if(mobile){
            game.checkPref("autotarget", true);
            game.checkPref("keyboard", false, val -> {
                control.setInput(val ? new DesktopInput() : new MobileInput());
                input.setUseKeyboard(val);
            });
            if(Core.settings.getBool("keyboard")){
                control.setInput(new DesktopInput());
                input.setUseKeyboard(true);
            }

        }
        //the issue with touchscreen support on desktop is that:
        //1) I can't test it
        //2) the SDL backend doesn't support multitouch
        /*else{
            game.checkPref("touchscreen", false, val -> control.setInput(!val ? new DesktopInput() : new MobileInput()));
            if(Core.settings.getBool("touchscreen")){
                control.setInput(new MobileInput());
            }
        }*/

        game.checkPref("communityservers", true, val -> {
            defaultServers.clear();
            if(val){
                JoinDialog.fetchServers();
            }
        });

        game.checkPref("savecreate", true);
        game.checkPref("blockreplace", true);
        game.checkPref("conveyorpathfinding", true);
        game.checkPref("hints", true);

        if(!mobile){
            game.checkPref("backgroundpause", true);
            game.checkPref("buildautopause", false);
            game.checkPref("distinctcontrolgroups", true);
        }

        game.checkPref("doubletapmine", false);
        game.checkPref("commandmodehold", true);

        if(steam){
            game.sliderPref("playerlimit", 16, 2, 32, i -> {
                platform.updateLobby();
                return i + "";
            });
        }

        graphics.sliderPref("uiEdgePadding", 0, 0, 100, s -> s + "px", s -> {
            if(ui != null){
                ui.updateMargins();
                Core.scene.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
            }
        });

        int[] lastUiScale = {settings.getInt("uiscale", android ? 125 : 100)};

        graphics.sliderPref("uiscale", android ? 125 : 100, 25, 300, 5, s -> {
            //if the user changed their UI scale, but then put it back, don't consider it 'changed'
            boolean modified = s != lastUiScale[0];
            Core.settings.put("uiscalechanged", modified);
            // A manual UI scale takes precedence over the automatically detected DPI.
            if(modified && DpiPreference.available()){
                DpiPreference.setAutomatic(false);
                DpiPreference.setManualScale(s);
            }
            return s + "%";
        });

        //Advanced Graphics does not exist on touch devices, so the DPI switch lives next to the
        //UI-scale slider there. Both platforms share the same preference and restart notice.
        if(mobile && DpiPreference.available()){
            graphics.checkPref(DpiPreference.key, true, this::onDpiToggle);
        }

        graphics.sliderPref("screenshake", 4, 0, 8, i -> (i / 4f) + "x");

        graphics.sliderPref("bloomintensity", 6, 0, 16, i -> (int)(i / 4f * 100f) + "%");
        graphics.sliderPref("bloomblur", 2, 1, 16, i -> i + "x");

        graphics.sliderPref("fpscap", 120, 10, 245, 5, s -> {
            if(ios){
                Core.graphics.setPreferredFPS(s > 240 ? 0 : s);
            }
            return (s > 240 ? Core.bundle.get("setting.fpscap.none") : Core.bundle.format("setting.fpscap.text", s));
        });

        if(ios){
            int value = Core.settings.getInt("fpscap", 120);
            Core.graphics.setPreferredFPS(value > 240 ? 0 : value);
        }

        graphics.sliderPref("chatopacity", 100, 0, 100, 5, s -> s + "%");
        graphics.sliderPref("lasersopacity", 100, 0, 100, 5, s -> {
            if(ui.settings != null){
                Core.settings.put("preferredlaseropacity", s);
            }
            return s + "%";
        });

        graphics.sliderPref("unitlaseropacity", 100, 0, 100, 5, s -> s + "%");
        graphics.sliderPref("bridgeopacity", 100, 0, 100, 5, s -> s + "%");

        graphics.sliderPref("maxmagnificationmultiplierpercent", 100, 100, 200, 25, s -> {
            if(ui.settings != null){
                Core.settings.put("maxzoomingamemultiplier", (float)s / 100.0f);
            }
            return s + "%";
        });

        graphics.sliderPref("minmagnificationmultiplierpercent", 100, 100, 300, 25, s -> {
            if(ui.settings != null){
                Core.settings.put("minzoomingamemultiplier", (float)s / 100.0f);
            }
            return s + "%";
        });

        if(!mobile){
            graphics.checkPref("vsync", true, b -> Core.graphics.setVSync(b));
            graphics.checkPref("fullscreen", false, b -> Core.graphics.setFullscreen(b));

            Core.graphics.setVSync(Core.settings.getBool("vsync"));

            if(Core.settings.getBool("fullscreen")){
                Core.app.post(() -> Core.graphics.setFullscreen(true));
            }
        }else if(!ios){
            graphics.checkPref("landscape", false, b -> {
                if(b){
                    platform.beginForceLandscape();
                }else{
                    platform.endForceLandscape();
                }
            });

            if(Core.settings.getBool("landscape")){
                platform.beginForceLandscape();
            }
        }

        graphics.checkPref("effects", true);
        graphics.checkPref("atmosphere", true);
        graphics.checkPref("drawlight", true);
        graphics.checkPref("destroyedblocks", true);
        graphics.checkPref("blockstatus", false);
        graphics.checkPref("playerchat", true);
        if(!mobile){
            graphics.checkPref("coreitems", true);
        }
        graphics.checkPref("minimap", !mobile);
        graphics.checkPref("smoothcamera", true);
        if(!mobile){
            graphics.checkPref("detach-camera", false);
        }
        graphics.checkPref("position", false);
        if(!mobile){
            graphics.checkPref("mouseposition", false);
        }
        //FPS and ping are shown out of the box: they are the first thing a player needs when the
        //frame rate drops, and the option stays available to turn them off
        graphics.checkPref("fps", true);
        graphics.checkPref("playerindicators", true);
        graphics.checkPref("showpings", true);
        graphics.checkPref("showotherbuildplans", true);
        graphics.checkPref("indicators", true);
        graphics.checkPref("showweather", true);
        graphics.checkPref("animatedwater", true);

        graphics.checkPref("bloom", true, val -> renderer.toggleBloom(val));

        graphics.checkPref("pixelate", false, val -> {
            if(val){
                Events.fire(Trigger.enablePixelation);
            }
        });

        //iOS (and possibly Android) devices do not support linear filtering well, so disable it
        graphics.checkPref("linear", !mobile, b -> {
            for(Texture tex : Core.atlas.getTextures()){
                TextureFilter filter = b ? TextureFilter.linear : TextureFilter.nearest;
                tex.setFilter(filter, filter);
            }
        });

        if(Core.settings.getBool("linear")){
            for(Texture tex : Core.atlas.getTextures()){
                TextureFilter filter = TextureFilter.linear;
                tex.setFilter(filter, filter);
            }
        }

        graphics.checkPref("skipcoreanimation", false);
        graphics.checkPref("hidedisplays", false);
        graphics.checkPref("logiclocalization", true);

        if(OS.isMac){
            graphics.checkPref("macnotch", false);
        }

        if(!mobile){
            Core.settings.put("swapdiagonal", false);
        }

        //Desktop-only display options. The resolution provider enumerates only modes supported by
        //the user's computer, while the DPI option keeps the interface at a consistent physical
        //size on high-resolution Windows displays.
        if(!mobile && WindowPreference.provider != null){
            Seq<WindowResolution> resolutions = WindowPreference.available();
            WindowResolution selected = WindowPreference.selected();
            if(selected != null && !resolutions.contains(selected)){
                //The selected mode is no longer supported (for example, the user changed monitors).
                //Fall back to automatic sizing instead of keeping a stale value.
                Core.settings.put(WindowPreference.key, WindowPreference.automatic);
            }

            Seq<String> resolutionValues = Seq.with(WindowPreference.automatic);
            Seq<String> resolutionLabels = Seq.with(bundle.get("setting.windowresolution.auto"));
            // Window resolutions are logical coordinates, so compare against the logical size
            // rather than the (possibly larger) physical drawable of a high-DPI display.
            int currentWidth = Core.graphics.getWidth();
            int currentHeight = Core.graphics.getHeight();
            for(WindowResolution resolution : resolutions){
                resolutionValues.add(resolution.id());
                String label = resolution.refreshRate > 0
                    ? bundle.format("setting.windowresolution.refresh", resolution.width, resolution.height, resolution.refreshRate)
                    : bundle.format("setting.windowresolution.option", resolution.width, resolution.height);
                if(resolution.width == currentWidth && resolution.height == currentHeight){
                    label += " " + bundle.get("setting.windowresolution.current");
                }
                resolutionLabels.add(label);
            }

            advancedgraphics.pref(new SettingsTable.DropdownSetting(WindowPreference.key,
                WindowPreference.automatic, resolutionValues.toArray(String.class),
                resolutionLabels.toArray(String.class), value -> {
                    if(!WindowPreference.apply(WindowResolution.parse(value))){
                        ui.showInfo(bundle.get("settings.windowresolution.failed"));
                    }
                }));
        }

        if(!mobile && DpiPreference.available()){
            advancedgraphics.checkPref(DpiPreference.key, true, this::onDpiToggle);
        }

        //renderer choice is desktop-only: Vulkan has no mobile backend and the preference has to be
        //read by the launcher before the window exists, so the row registers nothing elsewhere.
        if(!mobile){
            advancedgraphics.pref(new SettingsTable.OptionsSetting(RendererPreference.key, RendererPreference.opengl,
            Seq.with(RendererPreference.opengl, RendererPreference.vulkan).toArray(String.class), value -> {
                if(RendererPreference.vulkan.equals(value)){
                    // The row label stays short, so the long warnings live here instead: a wide
                    // row stretches the table and paints its text over the controls.
                    String note = bundle.get(RendererPreference.vulkanUsable()
                        ? "setting.renderer.available" : "setting.renderer.unavailable");
                    ui.showInfo(bundle.get("settings.renderer.restart") + "\n\n" + note);
                }
            }, () -> bundle.get("setting.renderer.name")));

            // Worker count for the Orbital engine. 0 means automatic and keeps one logical
            // processor for the game thread. The slider is built by SettingsTable, which fires
            // its callback once while constructing the row; only an actual change drains and
            // rebuilds an already running pool, and a pool that has not started yet simply reads
            // the stored value when it does.
            advancedgraphics.sliderPref(OrbitalThreadConfig.key, OrbitalThreadConfig.automatic,
                0, OrbitalThreadConfig.maxSelectable(), 1,
                value -> value <= 0
                    ? bundle.format("setting.orbitalworkers.auto", OrbitalThreadConfig.automaticWorkers())
                    : bundle.format("setting.orbitalworkers.workers", value),
                value -> {
                    int active = OrbitalEngine.activeWorkerCount();
                    if(active > 0 && active != OrbitalThreadConfig.resolve(value)){
                        OrbitalEngine.reconfigure(value);
                    }
                });

            //parallel unit movement is opt-in: when on, the Orbital engine integrates unit
            //velocity for the tick and VelComp skips its own integration for flagged units
            advancedgraphics.checkPref(UnitMovementTick.key, false, value -> {
                if(value && !OrbitalEngine.parallelCapable()){
                    ui.showInfo(bundle.get("settings.orbitalparallelmovement.unavailable"));
                }
            });

            //parallel bullet movement is opt-in too: collisions, lifetime and bullet behaviour stay
            //on the game thread, only the velocity integration moves to the engine workers
            advancedgraphics.checkPref(orbital.engine.combat.ProjectileTick.key, false, value -> {
                if(value && !OrbitalEngine.parallelCapable()){
                    ui.showInfo(bundle.get("settings.orbitalparallelbullets.unavailable"));
                }
            });

            //AI steering decisions run on the workers, but are not applied to entities yet: the
            //vanilla controllers stay authoritative, so this is a measurement tool for now
            advancedgraphics.checkPref(orbital.engine.ai.AITick.key, false, value -> {
                if(value && !OrbitalEngine.parallelCapable()){
                    ui.showInfo(bundle.get("settings.orbitalparallelai.unavailable"));
                }
            });

            //thresholds are measured per machine; turning this off restores the documented fixed
            //values, and changing it drops the cached measurements
            advancedgraphics.checkPref(EngineCalibration.key, true, value -> EngineCalibration.reset());
        }

        dev.checkPref("console", false, val -> {
            if(val == false && ui.consolefrag != null){
                ui.consolefrag.close();
            }
        });
        dev.checkPref("drawhitboxes", false);
        dev.checkPref("showperformance", false);

        if(!ios){
            dev.checkPref("modcrashdisable", true);
        }
    }

    public void exportData(Fi file) throws IOException{
        Seq<Fi> files = new Seq<>();
        files.add(Core.settings.getSettingsFile());
        files.addAll(customMapDirectory.list());
        files.addAll(saveDirectory.list());
        files.addAll(modDirectory.list());
        files.addAll(schematicDirectory.list());
        files.addAll(assetCacheDirectory.list()); //important for saves
        String base = Core.settings.getDataDirectory().path();

        //add directories
        for(Fi other : files.copy()){
            Fi parent = other.parent();
            while(!files.contains(parent) && !parent.equals(settings.getDataDirectory())){
                files.add(parent);
            }
        }

        try(OutputStream fos = file.write(false, 2048); ZipOutputStream zos = new ZipOutputStream(fos)){
            for(Fi add : files){
                String path = add.path().substring(base.length());
                if(add.isDirectory()) path += "/";
                //fix trailing / in path
                path = path.startsWith("/") ? path.substring(1) : path;
                zos.putNextEntry(new ZipEntry(path));
                if(!add.isDirectory()){
                    try(var stream = add.read()){
                        Streams.copy(stream, zos);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    public void importData(Fi file){
        Fi dest = Core.files.local("zipdata.zip");
        file.copyTo(dest);
        Fi zipped = new ZipFi(dest);

        Fi base = Core.settings.getDataDirectory();
        if(!zipped.child("settings.bin").exists()){
            throw new IllegalArgumentException("Not valid save data.");
        }

        //delete old saves so they don't interfere
        saveDirectory.deleteDirectory();

        //clear old assets cache
        assetCacheDirectory.deleteDirectory();

        //purge existing tmp data, keep everything else
        tmpDirectory.deleteDirectory();

        zipped.walk(f -> f.copyTo(base.child(f.path())));
        dest.delete();

        //clear old data
        settings.clear();
        //load data so it's saved on exit
        settings.load();
    }

    private void back(){
        rebuildMenu();
        prefs.clearChildren();
        prefs.add(menu);
    }

    private void visible(int index){
        prefs.clearChildren();

        Seq<Table> tables = new Seq<>();
        tables.addAll(game, constructions, graphics, advancedgraphics, sound, dev);
        for(var custom : categories){
            tables.add(custom.table);
        }

        prefs.add(tables.get(index));
    }

    @Override
    public void addCloseButton(){
        buttons.button("@back", Icon.left, () -> {
            if(prefs.getChildren().first() != menu){
                back();
            }else{
                hide();
            }
        }).size(210f, 64f);

        keyDown(key -> {
            if(key == KeyCode.escape || key == KeyCode.back){
                if(prefs.getChildren().first() != menu){
                    back();
                }else{
                    hide();
                }
            }
        });
    }

    public interface StringProcessor{
        String get(int i);
    }

    public static class SettingsCategory{
        public String name;
        public @Nullable Drawable icon;
        public Cons<SettingsTable> builder;
        public SettingsTable table;

        public SettingsCategory(String name, Drawable icon, Cons<SettingsTable> builder){
            this.name = name;
            this.icon = icon;
            this.builder = builder;

            table = new SettingsTable();
            builder.get(table);
        }
    }

    public static class SettingsTable extends Table{
        protected Seq<Setting> list = new Seq<>();

        public SettingsTable(){
            left();
        }

        public Seq<Setting> getSettings(){
            return list;
        }

        public void pref(Setting setting){
            list.add(setting);
            rebuild();
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, StringProcessor s){
            return sliderPref(name, def, min, max, 1, s);
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, int step, StringProcessor s){
            return sliderPref(name, def, min, max, step, s, null);
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, StringProcessor s, Intc changed){
            return sliderPref(name, def, min, max, 1, s, changed);
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, int step, StringProcessor s, Intc changed){
            SliderSetting res;
            list.add(res = new SliderSetting(name, def, min, max, step, s, changed));
            settings.defaults(name, def);
            rebuild();
            return res;
        }

        public void checkPref(String name, boolean def){
            list.add(new CheckSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void checkPref(String name, boolean def, Boolc changed){
            list.add(new CheckSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        public void textPref(String name, String def){
            list.add(new TextSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void textPref(String name, String def, Cons<String> changed){
            list.add(new TextSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        public void areaTextPref(String name, String def){
            list.add(new AreaTextSetting(name, def, null));
            settings.defaults(name, def);
            rebuild();
        }

        public void areaTextPref(String name, String def, Cons<String> changed){
            list.add(new AreaTextSetting(name, def, changed));
            settings.defaults(name, def);
            rebuild();
        }

        /**
         * Preference that stores one value out of a fixed set of strings, shown as a row of
         * mutually exclusive buttons. The label of each option is read from
         * {@code setting.<name>.<value>}, and {@code status}, when given, is rendered below the
         * row and re-evaluated every frame.
         */
        public static class OptionsSetting extends Setting{
            String def;
            Seq<String> values;
            @Nullable Cons<String> changed;
            @Nullable Prov<String> status;

            public OptionsSetting(String name, String def, String[] values, @Nullable Cons<String> changed){
                this(name, def, values, changed, null);
            }

            public OptionsSetting(String name, String def, String[] values, @Nullable Cons<String> changed, @Nullable Prov<String> status){
                super(name);
                this.def = def;
                this.values = Seq.with(values);
                this.changed = changed;
                this.status = status;
                settings.defaults(name, def);
            }

            @Override
            public void add(SettingsTable table){
                Table content = table.table().left().growX().get();
                content.add(title, Styles.outlineLabel).left();
                addDesc(content);
                table.row();

                Table options = table.table().left().get();
                ButtonGroup<TextButton> group = new ButtonGroup<>();
                for(String value : values){
                    TextButton button = new TextButton(bundle.get("setting." + name + "." + value), Styles.flatTogglet);
                    button.clicked(() -> {
                        boolean modified = !value.equals(settings.getString(name, def));
                        settings.put(name, value);
                        if(changed != null && modified){
                            changed.get(value);
                        }
                    });
                    options.add(button).group(group).update(b -> b.setChecked(value.equals(settings.getString(name, def)))).size(180f, 45f);
                }
                addDesc(table.add(options).left().padTop(3f).get());
                table.row();

                if(status != null){
                    //the stored value is a request: Vulkan only renders when a complete backend for
                    //it exists, so the row also says what the current selection will actually do.
                    Label current = new Label(status.get(), Styles.outlineLabel);
                    current.setWrap(true);
                    table.add(current).left().growX().padTop(6f).update(label -> label.setText(status.get()));
                    table.row();
                }
            }
        }

        /**
         * Preference backed by a dropdown-style button. The stored value and its visible label
         * are separate, so dynamic values such as display resolutions do not need one bundle key
         * per option.
         */
        public static class DropdownSetting extends Setting{
            String def;
            Seq<String> values;
            Seq<String> labels;
            @Nullable Cons<String> changed;

            public DropdownSetting(String name, String def, String[] values, String[] labels, @Nullable Cons<String> changed){
                super(name);
                this.def = def;
                this.values = Seq.with(values);
                this.labels = Seq.with(labels);
                this.changed = changed;
                settings.defaults(name, def);
            }

            @Override
            public void add(SettingsTable table){
                Table content = table.table().left().growX().get();
                content.add(title, Styles.outlineLabel).left();
                addDesc(content);
                table.row();

                TextButton button = new TextButton(label(currentValue()), Styles.flatt);
                button.clicked(() -> showOptions(button));
                addDesc(table.add(button).left().width(300f).height(45f).padTop(3f).get());
                table.row();
            }

            private void showOptions(TextButton button){
                BaseDialog dialog = new BaseDialog(title);
                dialog.cont.pane(p -> {
                    p.defaults().size(300f, 45f).left();
                    for(int i = 0; i < values.size; i++){
                        String value = values.get(i);
                        p.button(labels.get(i), Styles.flatTogglet, () -> {
                            select(value);
                            button.setText(label(value));
                            dialog.hide();
                        }).checked(value.equals(currentValue())).row();
                    }
                });
                dialog.addCloseButton();
                dialog.show();
            }

            private void select(String value){
                boolean modified = !value.equals(currentValue());
                settings.put(name, value);
                if(changed != null && modified) changed.get(value);
            }

            private String currentValue(){
                return settings.getString(name, def);
            }

            private String label(String value){
                int index = values.indexOf(value);
                return index < 0 ? values.isEmpty() ? value : labels.first() : labels.get(index);
            }
        }

        public void rebuild(){
            clearChildren();

            for(Setting setting : list){
                setting.add(this);
            }

            button(bundle.get("settings.reset", "Reset to Defaults"), () -> {
                for(Setting setting : list){
                    if(setting.name == null || setting.title == null) continue;
                    settings.remove(setting.name);
                }
                rebuild();
            }).margin(14).width(240f).pad(6);
        }

        public abstract static class Setting{
            public String name;
            public String title;
            public @Nullable String description;

            public Setting(String name){
                this.name = name;
                String winkey = "setting." + name + ".name.windows";
                title = OS.isWindows && bundle.has(winkey) ? bundle.get(winkey) : bundle.get("setting." + name + ".name", name);
                description = bundle.getOrNull("setting." + name + ".description");
            }

            public abstract void add(SettingsTable table);

            public void addDesc(Element elem){
                ui.addDescTooltip(elem, description);
            }
        }

        public static class CheckSetting extends Setting{
            boolean def;
            Boolc changed;

            public CheckSetting(String name, boolean def, Boolc changed){
                super(name);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(SettingsTable table){
                Table box = Elems.check(title, () -> settings.getBool(name), value -> {
                    settings.put(name, value);
                    if(changed != null){
                        changed.get(value);
                    }
                });

                addDesc(table.add(box).minWidth(Math.min(500f, Core.graphics.getWidth() / 1.2f / Scl.scl(1f))).fillX().height(45f).left().padTop(7f).get());
                table.row();
            }
        }

        public static class SliderSetting extends Setting{
            int def, min, max, step;
            StringProcessor sp;
            Intc changed;

            public SliderSetting(String name, int def, int min, int max, int step, StringProcessor s, Intc changed){
                super(name);
                this.def = def;
                this.min = min;
                this.max = max;
                this.step = step;
                this.sp = s;
                this.changed = changed;
            }

            @Override
            public void add(SettingsTable table){
                Slider slider = new Slider(min, max, step, false);

                slider.setValue(settings.getInt(name));

                Label value = new Label("", Styles.outlineLabel);
                Table content = new Table();
                //Title and value used to share one row: a long translated title wrapped onto two
                //lines and the value was painted on top of the second line. Give the title its own
                //row and the value its own right-aligned row below it.
                content.add(title, Styles.outlineLabel).left().growX().wrap().row();
                content.add(value).right().padLeft(10f);
                content.margin(3f, 33f, 3f, 33f);
                content.touchable = Touchable.disabled;

                slider.changed(() -> {
                    settings.put(name, (int)slider.getValue());
                    value.setText(sp.get((int)slider.getValue()));
                    if(changed != null) changed.get((int)slider.getValue());
                });

                slider.change();

                //A stack draws both children over the same rect, so the value label and the slider
                //knob fought for the same pixels. Give each its own row: title/value above, the
                //slider track below. The label table stays non-touchable so dragging the slider
                //still works through it.
                Table row = new Table();
                float rowWidth = Math.min(Core.graphics.getWidth() / 1.2f / Scl.scl(1f), 500f);
                row.add(content).growX().row();
                row.add(slider).growX().padTop(2f);
                addDesc(table.add(row).width(rowWidth).left().padTop(4f).get());
                table.row();
            }
        }

        public static class TextSetting extends Setting{
            String def;
            Cons<String> changed;

            public TextSetting(String name, String def, Cons<String> changed){
                super(name);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(SettingsTable table){
                TextField field = new TextField();

                field.update(() -> field.setText(settings.getString(name)));

                field.changed(() -> {
                    settings.put(name, field.getText());
                    if(changed != null){
                        changed.get(field.getText());
                    }
                });

                Table prefTable = table.table().left().padTop(3f).get();
                prefTable.add(field);
                prefTable.label(() -> title);
                addDesc(prefTable);
                table.row();
            }
        }

        public static class AreaTextSetting extends TextSetting{
            public AreaTextSetting(String name, String def, Cons<String> changed){
                super(name, def, changed);
            }

            @Override
            public void add(SettingsTable table){
                TextArea area = new TextArea("");
                area.setPrefRows(5);

                area.update(() -> {
                    area.setText(settings.getString(name));
                    area.setWidth(table.getWidth());
                });

                area.changed(() -> {
                    settings.put(name, area.getText());
                    if(changed != null){
                        changed.get(area.getText());
                    }
                });

                addDesc(table.label(() -> title).left().padTop(3f).get());
                table.row().add(area).left();
                table.row();
            }
        }
    }
}
