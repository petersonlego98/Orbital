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
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.ImageButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.*;
import orbital.annotations.Annotations.*;
import orbital.content.*;
import orbital.core.GameState.*;
import orbital.core.*;
import orbital.ctype.*;
import orbital.entities.abilities.*;
import orbital.entities.units.*;
import orbital.game.EventType.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.input.*;
import orbital.net.Packets.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.ui.dialogs.*;
import orbital.world.*;
import orbital.world.blocks.environment.*;
import orbital.world.meta.*;

import static orbital.Vars.*;
import static orbital.gen.Tex.*;

public class HudFragment{
    private static final float dsize = 65f, pauseHeight = 36f;

    public PlacementFragment blockfrag = new PlacementFragment();
    public CoreItemsDisplay coreItems = new CoreItemsDisplay();
    public boolean shown = true;

    private ImageButton flip;

    private String hudText = "";
    private boolean showHudText;

    private Table lastUnlockTable;
    private Table lastUnlockLayout;
    private long lastToast;
    private float pauseDisableDur;

    private Seq<Block> blocksOut = new Seq<>();
    private Table hudLabel;
    private float coreAttackTime;

    /** last unit shown in the loadout bar, used to avoid rebuilding it every frame. */
    private Unit turretBarUnit;
    /** identity signature of the turret mounts currently drawn in the loadout bar. */
    private long turretBarSignature = Long.MIN_VALUE;
    /** turret add/remove menu for the selected unit. */
    private @Nullable TurretLoadoutDialog turretDialog;
    /** block picker of the enemy-structure tool of the test mode. */
    private @Nullable EnemyBuildingDialog enemyDialog;
    /** last armed block/team/mode of the enemy-structure tool, used to rebuild its panel only on change. */
    private long enemySignature = Long.MIN_VALUE;
    /** reused plan for the ghost preview of the armed enemy structure. */
    private final BuildPlan enemyPlan = new BuildPlan();
    /** the ghost preview never searches for neighboring plans, so an empty list is enough. */
    private final Seq<BuildPlan> emptyPlans = new Seq<>();

    private static ObjectSet<String> favoriteBlocks = new ObjectSet<>();
    private static String lastFavorited = null;

    private void addBlockSelection(Table cont){
        Table blockSelection = new Table();
        var pane = new ScrollPane(blockSelection, Styles.smallPane);
        pane.setFadeScrollBars(false);
        Planet[] last = {state.rules.planet};
        pane.update(() -> {
            if(pane.hasScroll()){
                Element result = Core.scene.getHoverElement();
                if(result == null || !result.isDescendantOf(pane)){
                    Core.scene.setScrollFocus(null);
                }
            }

            if(state.rules.planet != last[0]){
                last[0] = state.rules.planet;
                rebuildBlockSelection(blockSelection, "");
            }
        });

        Table[] configTable = {null};
        Block[] lastBlock = {null};

        cont.table(search -> {
            search.image(Icon.zoom).padRight(8);
            search.field("", text -> rebuildBlockSelection(blockSelection, text)).growX()
            .name("editor/search").maxTextLength(maxNameLength).get().setMessageText("@players.search");
        }).growX().pad(-2).padLeft(6f);
        cont.row();
        cont.collapser(t -> {
            configTable[0] = t;
        }, () -> control.input.block != null && control.input.block.editorConfigurable).with(c -> c.setEnforceMinSize(true)).update(col -> {

            if(lastBlock[0] != control.input.block){
                configTable[0].clear();
                if(control.input.block != null){
                    control.input.block.buildEditorConfig(configTable[0]);
                    col.invalidateHierarchy();
                }
                lastBlock[0] = control.input.block;
            }
        }).growX().row();
        cont.add(pane).expandY().top().left();

        rebuildBlockSelection(blockSelection, "");
    }

    private static void loadFavorites(){
        favoriteBlocks = Core.settings.getJson("editor-block-favorites", ObjectSet.class, String.class, ObjectSet::new);
    }

    private static void saveFavorites(){
        Core.settings.putJson("editor-block-favorites", String.class, favoriteBlocks);
    }

    private static boolean isFavorite(Block block){
        return favoriteBlocks.contains(block.name);
    }

    private static void toggleFavorite(Block block){
        boolean isFav = favoriteBlocks.contains(block.name);
        if(isFav){
            favoriteBlocks.remove(block.name);
            Sounds.uiFavorite.play(0.35f, 0.67f, 0f);
        }else{
            favoriteBlocks.add(block.name);
            lastFavorited = block.name;
            Sounds.uiFavorite.play(0.6f, 2.0f, 0f);
        }
        saveFavorites();
    }

    private void rebuildBlockSelection(Table blockSelection, String searchText){
        blockSelection.clear();

        blocksOut.clear();
        // Favorites first
        for(Block block : Vars.content.blocks()){
            if(isFavorite(block)){
                blocksOut.add(block);
            }
        }

        for(Block block : Vars.content.blocks()){
            if(!isFavorite(block)){
                blocksOut.add(block);
            }
        }
        blocksOut.sort((b1, b2) -> {
            // These two block should stay at the top
            if(b1 == Blocks.removeOre || b1 == Blocks.removeWall) return -1;
            if(b2 == Blocks.removeOre || b2 == Blocks.removeWall) return 1;
            int fav = Boolean.compare(isFavorite(b2), isFavorite(b1));
            if(fav != 0) return fav;
            int synth = Boolean.compare(b1.synthetic(), b2.synthetic());
            if(synth != 0) return synth;
            int ore = Boolean.compare(b1 instanceof OverlayFloor && b1 != Blocks.removeOre, b2 instanceof OverlayFloor && b2 != Blocks.removeOre);
            if(ore != 0) return ore;
            return Integer.compare(b1.id, b2.id);
        });

        int i = 0;

        for(Block block : blocksOut){
            TextureRegion region = block.uiIcon;

            if(!Core.atlas.isFound(region)
            || (!block.inEditor && !(block instanceof RemoveWall) && !(block instanceof RemoveOre))
            || !block.isOnPlanet(state.rules.planet)
            || block.buildVisibility == BuildVisibility.debugOnly
            || (!searchText.isEmpty() && !(block == Blocks.removeOre || block == Blocks.removeWall) && !block.localizedName.toLowerCase().contains(searchText.trim().replaceAll(" +", " ").toLowerCase()))
            ) continue;

            ImageButton button = new ImageButton(Tex.whiteui, Styles.clearNoneTogglei);
            button.getStyle().imageUp = new TextureRegionDrawable(region);
            button.clicked(() -> control.input.block = block);
            // Pc input for favorites
            button.clicked(KeyCode.mouseRight, () -> {
                if(block == Blocks.removeOre || block == Blocks.removeWall) return;
                toggleFavorite(block);
                rebuildBlockSelection(blockSelection, searchText);
                control.input.block = block;
            });
            // Mobile double-click for favorites
            button.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if(mobile && getTapCount() == 2){
                        if(block == Blocks.removeOre || block == Blocks.removeWall) return;
                        toggleFavorite(block);
                        rebuildBlockSelection(blockSelection, searchText);
                        control.input.block = block;
                    }
                }
            });
            button.resizeImage(8 * 4f);
            button.update(() -> button.setChecked(control.input.block == block));

            Stack stack = new Stack();
            stack.add(button);
            if(isFavorite(block)){
                Image favIcon = new Image(Icon.star);
                favIcon.setColor(Color.gold);
                favIcon.setOrigin(Align.center);

                if(block.name.equals(lastFavorited)){
                    favIcon.color.a = 0f;
                    favIcon.setScale(0f);
                    favIcon.addAction(Actions.parallel(
                        Actions.scaleTo(0.7f, 0.7f, 0.2f),
                        Actions.alpha(1f, 0.2f),
                        Actions.run(() -> lastFavorited = null)
                    ));
                }else{
                    favIcon.setScale(0.7f);
                }

                Table overlay = new Table().align(Align.topRight);
                overlay.touchable = Touchable.disabled;
                overlay.add(favIcon).size(2f).pad(12f);

                stack.add(overlay);
            }
            blockSelection.add(stack).size(48f).tooltip(block.localizedName);

            if(++i % 6 == 0){
                blockSelection.row();
            }
        }

        if(i == 0){
            blockSelection.add("@none.found").padLeft(54f).padTop(10f);
        }
    }

    public void build(Group parent){
        loadFavorites();
        //warn about guardian/boss waves
        Events.on(WaveEvent.class, e -> {
            int max = 10;
            int winWave = state.rules.winWave > 0 ? state.rules.winWave : Integer.MAX_VALUE;
            outer:
            for(int i = state.wave - 1; i <= Math.min(state.wave + max, winWave - 2); i++){
                for(SpawnGroup group : state.rules.spawns){
                    if(group.effect == StatusEffects.boss && group.getSpawned(i) > 0){
                        int diff = (i + 2) - state.wave;

                        //increments at which to warn about incoming guardian
                        if(diff == 1 || diff == 2 || diff == 5 || diff == 10){
                            showToast(Icon.warning, group.type.emoji() + " " + Core.bundle.format("wave.guardianwarn" + (diff == 1 ? ".one" : ""), diff));
                        }

                        break outer;
                    }
                }
            }
        });

        Events.on(SectorCaptureEvent.class, e -> {
            if(e.sector.isBeingPlayed()){
                ui.announce("@sector.capture.current", 5f);
            }else{
                showToast(Core.bundle.format("sector.capture", e.sector.name()));
            }
        });

        Events.on(SectorLoseEvent.class, e -> {
            showToast(Icon.warning, Core.bundle.format("sector.lost", e.sector.name()));
        });

        Events.on(SectorInvasionEvent.class, e -> {
            showToast(Icon.warning, Core.bundle.format("sector.attacked", e.sector.name()));
        });

        Events.on(ResetEvent.class, e -> {
            coreItems.resetUsed();
            coreItems.clear();
            if(hudLabel != null){
                hudLabel.color.a = 0f;
            }
            turretBarUnit = null;
            turretBarSignature = Long.MIN_VALUE;
            showHudText = false;
        });

        //paused table
        parent.fill(t -> {
            float sidePad = dsize * 5 + 4f;
            t.name = "paused";
            t.top().visible(() -> state.isPaused() && shown() && !netServer.isWaitingForPlayers() && !(mobile && Core.graphics.isPortrait())).touchable = Touchable.disabled;
            t.table(Styles.black6, top -> {
                top.label(() -> state.gameOver && state.isCampaign() ? "@sector.curlost" : "@paused")
                .style(Styles.outlineLabel).pad(8f);
                top.spacerX(() -> sidePad + Core.scene.marginLeft - Core.scene.marginRight);
            }).height(pauseHeight).growX()
            .padLeft(sidePad);
        });

        //pause disabled table
        parent.fill(t -> {
            t.name = "pause-disabled";
            t.top().visible(() -> pauseDisableDur > 0f && shown() && !mobile && !netServer.isWaitingForPlayers() && !state.isPaused() && !(state.gameOver && state.isCampaign())).touchable = Touchable.disabled;
            t.update(() -> {
                t.color.a = t.color.a > 0f && pauseDisableDur > 0f ? t.color.a - Time.delta / pauseDisableDur : 1f;
                if(t.color.a <= 0f){
                    pauseDisableDur = 0f;
                }
            });
            t.table(Styles.black6, top -> top.label(() -> "@pause.disabled").style(Styles.outlineLabel).pad(8f)).height(pauseHeight).growX();
        });

        //left/right gutter areas
        parent.fill((x, y, w, h) -> {
            x = 0f;
            y = 0f;
            w = Core.graphics.getWidth();
            h = Core.graphics.getHeight();
            Draw.color();
            if(Core.scene.marginLeft > 0){
                paneRight.draw(x, y, Core.scene.marginLeft, h);
            }

            if(Core.scene.marginRight > 0){
                paneLeft.draw(x + w - Core.scene.marginRight, y, Core.scene.marginRight, h);
            }
        });

        //"waiting for players"
        parent.fill(t -> {
            t.name = "waiting";
            t.visible(() -> netServer.isWaitingForPlayers() && state.isPaused() && shown()).touchable = Touchable.disabled;
            t.table(Styles.black6, top -> top.add("@waiting.players").style(Styles.outlineLabel).pad(18f));
        });

        //minimap + position
        parent.fill(t -> {
            t.name = "minimap/position";
            t.visible(() -> Core.settings.getBool("minimap") && shown());
            //minimap
            t.add(new Minimap()).name("minimap");
            t.row();
            //position
            t.label(() ->
                (Core.settings.getBool("position") ? player.tileX() + "," + player.tileY() + "\n" : "") +
                (Core.settings.getBool("mouseposition") ? "[lightgray]" + World.toTile(Core.input.mouseWorldX()) + "," + World.toTile(Core.input.mouseWorldY()) : ""))
            .visible(() -> Core.settings.getBool("position") || Core.settings.getBool("mouseposition"))
            .touchable(Touchable.disabled)
            .style(Styles.outlineLabel)
            .name("position");
            t.top().right();
        });

        ui.hints.build(parent);

        //menu at top left
        parent.fill(cont -> {
            cont.name = "overlaymarker";
            cont.top().left();

            if(mobile){
                //for better inset visuals
                cont.rect((x, y, w, h) -> {
                    if(Core.scene.marginTop > 0){
                        Draw.color();
                        Tex.paneRight.draw(x, y, w, Core.scene.marginTop);
                    }
                }).fillX().row();

                //paused in portrait mode
                cont.label(() -> state.gameOver && state.isCampaign() ? "@sector.curlost" : "@paused")
                .style(Styles.outlineLabel)
                .labelAlign(Align.center)
                .visible(() -> state.isPaused() && shown && !netServer.isWaitingForPlayers() && Core.graphics.isPortrait())
                .touchable(Touchable.disabled).height(20f).padTop(-40f).fillX();

                cont.row();

                cont.table(select -> {
                    select.name = "mobile buttons";
                    select.left();
                    select.defaults().size(dsize).left();
                    select.background(Styles.black6);

                    ImageButtonStyle style = Styles.clearNonei;

                    select.button(Icon.menu, style, ui.paused::show).name("menu");
                    flip = select.button(Icon.upOpen, style, () -> {
                        if(Core.settings.getBool("console")){
                            ui.consolefrag.toggleMobile();
                        }else{
                            toggleMenus();
                        }
                    }).update(i -> {
                        if(Core.settings.getBool("console")){
                            i.getStyle().imageUp = Icon.terminal;
                            shown = true; //force shown when console is enabled, because there is no other way to show/hide
                        }else{
                            i.getStyle().imageUp = shown ? Icon.downOpen : Icon.upOpen;
                        }
                    }).get();
                    flip.name = "flip";

                    select.button(Icon.paste, style, ui.schematics::show)
                    .name("schematics");

                    select.button(Icon.pause, style, () -> {
                        if(net.active()){
                            ui.listfrag.toggle();
                        }else if(!state.rules.pauseDisabled){
                            state.set(state.isPaused() ? State.playing : State.paused);
                        }
                    }).name("pause").update(i -> {
                        if(net.active()){
                            i.setDisabled(false);
                            i.getStyle().imageUp = Icon.players;
                        }else{
                            i.setDisabled(state.rules.pauseDisabled || (state.isCampaign() && state.afterGameOver));
                            i.getStyle().imageUp = state.isPaused() ? Icon.play : Icon.pause;
                        }
                    });

                    select.button(Icon.chat, style,() -> {
                        if(net.active() && mobile){
                            if(ui.chatfrag.shown()){
                                ui.chatfrag.hide();
                            }else{
                                ui.chatfrag.toggle();
                            }
                        }else if(state.isCampaign()){
                            ui.research.show();
                        }else{
                            ui.database.show();
                        }
                    }).name("chat").update(i -> {
                        if(net.active() && mobile){
                            i.getStyle().imageUp = Icon.chat;
                        }else if(state.isCampaign()){
                            i.getStyle().imageUp = Icon.tree;
                        }else{
                            i.getStyle().imageUp = Icon.book;
                        }
                    });

                    select.image().color(Pal.gray).width(4f).fillY();
                    //there is no back button on iOS, so the menu has to be shown at all times.
                }).visible(() -> OS.isIos || !control.input.logicHideHud);

                cont.row();
                cont.image().height(4f).color(Pal.gray).fillX();
                cont.row();
            }

            cont.update(() -> {
                if(Core.input.keyTap(Binding.toggleMenus) && !ui.chatfrag.shown() && !Core.scene.hasDialog() && !Core.scene.hasField()){
                    Core.settings.getBoolOnce("ui-hidden", () -> {
                        ui.announce(Core.bundle.format("showui",  Binding.toggleMenus.value.key.toString(), 11));
                    });
                    toggleMenus();
                }

                if(Core.input.keyTap(Binding.skipWave) && canSkipWave() && !Core.scene.hasDialog() && !Core.scene.hasField()){
                    if(net.client() && player.admin){
                        Call.adminRequest(player, AdminAction.wave, null);
                    }else{
                        logic.skipWave();
                    }
                }
            });

            Table wavesMain, editorMain;

            cont.stack(wavesMain = new Table(), editorMain = new Table(), new Element(){
                //this may seem insane, but adding an empty element of a specific height to this stack fixes layout issues on mobile.

                {
                    visible = false;
                    touchable = Touchable.disabled;
                }

                @Override
                public float getPrefHeight(){
                    return Scl.scl(123f);
                }
            }).name("waves/editor");

            wavesMain.visible(() -> shown() && !state.isEditor());
            wavesMain.top().left().name = "waves";

            wavesMain.table(s -> {
                //wave info button with text
                s.add(makeStatusTable()).grow().name("status");

                var rightStyle = new ImageButtonStyle(){{
                    over = buttonRightOver;
                    down = buttonRightDown;
                    up = buttonRight;
                    disabled = buttonRightDisabled;
                    imageDisabledColor = Color.clear;
                    imageUpColor = Color.white;
                }};

                //table with button to skip wave
                s.button(Icon.play, rightStyle, 30f, () -> {
                    if(net.client() && player.admin){
                        Call.adminRequest(player, AdminAction.wave, null);
                    }else{
                        logic.skipWave();
                    }
                }).growY().fillX().right().width(40f).disabled(b -> !canSkipWave()).name("skip").get().toBack();
            }).width(dsize * 5 + 4f).name("statustable");

            wavesMain.row();

            addInfoTable(wavesMain.table().width(dsize * 5f + 4f).left().get());

            wavesMain.row();
            addExpeditionTable(wavesMain.table().width(dsize * 5f + 4f).left().get());

            editorMain.name = "editor";
            editorMain.table(Tex.buttonEdge4, t -> {
                t.name = "teams";

                t.top().table(teams -> {
                    teams.left();
                    for(Team team : Team.baseTeams){
                        ImageButton button = teams.button(Tex.whiteui, Styles.clearNoneTogglei, 33f, () -> Call.setPlayerTeamEditor(player, team))
                        .size(45f).margin(6f).get();
                        button.getImageCell().grow();
                        button.getStyle().imageUpColor = team.color;
                        button.update(() -> button.setChecked(player.team() == team));
                    }

                    teams.button(Icon.downOpen, Styles.emptyi, () -> Core.settings.put("editor-blocks-shown", !Core.settings.getBool("editor-blocks-shown")))
                    .size(45f).update(m -> m.getStyle().imageUp = (Core.settings.getBool("editor-blocks-shown") ? Icon.upOpen : Icon.downOpen));
                }).top().left().row();

                t.collapser(this::addBlockSelection, () -> Core.settings.getBool("editor-blocks-shown"));

            }).width(dsize * 5 + 4f).top();
            if(mobile){
                editorMain.row().spacerY(() -> {
                    if(control.input instanceof MobileInput mob && Core.settings.getBool("editor-blocks-shown")){
                        if(Core.graphics.isPortrait()) return Core.graphics.getHeight() / 2f / Scl.scl(1f);
                        if(mob.hasSchematic()) return 156f;
                        if(mob.showCancel()) return 50f;
                    }
                    return 0f;
                });
            }

            editorMain.row().add().growY();
            editorMain.visible(() -> shown() && state.isEditor());

            //fps display
            cont.table(info -> {
                info.name = "fps/ping";
                info.touchable = Touchable.disabled;
                info.top().left().margin(4).visible(() -> Core.settings.getBool("fps") && shown());
                IntFormat fps = new IntFormat("fps");
                IntFormat ping = new IntFormat("ping");
                IntFormat tps = new IntFormat("tps");
                IntFormat mem = new IntFormat("memory");
                IntFormat memnative = new IntFormat("memory2");

                info.label(() -> fps.get(Core.graphics.getFramesPerSecond())).left().style(Styles.outlineLabel).name("fps");
                info.row();

                if(android){
                    info.label(() -> memnative.get((int)(Core.app.getJavaHeap() / 1024 / 1024), (int)(Core.app.getNativeHeap() / 1024 / 1024))).left().style(Styles.outlineLabel).name("memory2");
                }else{
                    info.label(() -> mem.get((int)(Core.app.getJavaHeap() / 1024 / 1024))).left().style(Styles.outlineLabel).name("memory");
                }
                info.row();

                info.label(() -> ping.get(netClient.getPing())).visible(net::client).left().style(Styles.outlineLabel).name("ping").row();
                info.label(() -> tps.get(state.serverTps == -1 ? 60 : state.serverTps)).visible(net::client).left().style(Styles.outlineLabel).name("tps").row();

            }).top().left();
        });

        //core info
        parent.fill(t -> {
            t.top();

            if(Core.settings.getBool("macnotch") ){
                t.margin(macNotchHeight);
            }

            t.visible(this::shown);

            t.name = "coreinfo";

            t.collapser(v -> v.add().height(pauseHeight), () -> !netServer.isWaitingForPlayers() && (state.isPaused() || pauseDisableDur > 0f)).row();

            t.table(c -> {
                //core items
                c.top().collapser(coreItems, () -> Core.settings.getBool("coreitems") && !mobile && shown()).fillX().row();

                float notifDuration = 240f;

                Events.run(Trigger.teamCoreDamage, () -> coreAttackTime = notifDuration);
                Events.on(ResetEvent.class, e -> {
                    coreAttackTime = 0f;
                });

                //'core is under attack' table
                c.collapser(top -> top.background(Styles.black6).add("@coreattack").pad(8)
                .with(co -> {
                    co.tapped(() -> {
                        if(control.lastDamagedCore != null){
                            control.input.panCamera(Tmp.v1.set(control.lastDamagedCore));
                        }
                    });
                    co.addListener(new HandCursorListener());
                })
                .update(label -> label.color.set(Color.orange).lerp(Color.scarlet, Mathf.absin(Time.time, 2f, 1f))), true,
                () -> {
                    if(state.isMenu() || !player.team().data().hasCore()){
                        coreAttackTime = 0f;
                        return false;
                    }
                    if(!shown() || state.isPaused()) return false;

                    return (coreAttackTime -= Time.delta) > 0;
                })
                .touchable(Touchable.disabled)
                .fillX().row();
            }).row();

            var bossb = new StringBuilder();
            var bossText = Core.bundle.get("guardian");
            int maxBosses = 6;

            t.table(v -> v.margin(10f)
            .add(new Bar(() -> {
                bossb.setLength(0);
                for(int i = 0; i < Math.min(state.teams.bosses.size, maxBosses); i++){
                    bossb.append(state.teams.bosses.get(i).type.emoji());
                }
                if(state.teams.bosses.size > maxBosses){
                    bossb.append("[accent]+[]");
                }
                bossb.append(" ");
                bossb.append(bossText);
                return bossb;
            }, () -> Pal.health, () -> {
                if(state.boss() == null) return 0f;
                float max = 0f, val = 0f;
                for(var boss : state.teams.bosses){
                    max += boss.maxHealth;
                    val += boss.health;
                }
                return max == 0f ? 0f : val / max;
            }).blink(Color.white).outline(new Color(0, 0, 0, 0.6f), 7f)).grow())
            .fillX().width(320f).height(60f).name("boss").visible(() -> state.rules.waves && state.boss() != null && !(mobile && Core.graphics.isPortrait())).padTop(7).row();

            t.table(Styles.black3, p -> p.margin(4).label(() -> hudText).style(Styles.outlineLabel)).touchable(Touchable.disabled).with(p -> hudLabel = p)
                .with(p -> p.visible(() -> (p.color.a = Mathf.lerpDelta(p.color.a, Mathf.num(showHudText), 0.2f)) >= 0.001f));
        });

        //spawner warning
        parent.fill(t -> {
            t.name = "nearpoint";
            t.touchable = Touchable.disabled;
            t.table(Styles.black6, c -> c.add("@nearpoint")
            .update(l -> l.setColor(Tmp.c1.set(Color.white).lerp(Color.scarlet, Mathf.absin(Time.time, 10f, 1f))))
            .labelAlign(Align.center, Align.center))
            .margin(6).update(u -> u.color.a = Mathf.lerpDelta(u.color.a, Mathf.num(spawner.playerNear()), 0.1f)).get().color.a = 0f;
        });

        //'saving' indicator
        parent.fill(t -> {
            t.name = "saving";
            t.bottom().visible(() -> control.saves.isSaving());
            t.add("@saving").style(Styles.outlineLabel);
        });

        //TODO DEBUG: rate table
        if(false)
            parent.fill(t -> {
                t.bottom().left();
                t.table(Styles.black6, c -> {
                    Bits used = new Bits(content.items().size);

                    Runnable rebuild = () -> {
                        c.clearChildren();

                        for(Item item : content.items()){
                            if(state.rules.sector != null && state.rules.sector.info.getExport(item) >= 1){
                                c.image(item.uiIcon);
                                c.label(() -> (int)state.rules.sector.info.getExport(item) + " /s").color(Color.lightGray);
                                c.row();
                            }
                        }
                    };

                    c.update(() -> {
                        boolean wrong = false;
                        for(Item item : content.items()){
                            boolean has = state.rules.sector != null && state.rules.sector.info.getExport(item) >= 1;
                            if(used.get(item.id) != has){
                                used.set(item.id, has);
                                wrong = true;
                            }
                        }
                        if(wrong){
                            rebuild.run();
                        }
                    });
                }).visible(() -> state.isCampaign() && content.items().contains(i -> state.rules.sector != null && state.rules.sector.info.getExport(i) > 0));
            });

        //dorsal loadout of the selected unit
        parent.fill(t -> {
            t.name = "turret-loadout";
            t.bottom().left();
            t.touchable = Touchable.childrenOnly;
            t.visible(() -> shown() && !state.isMenu() && !state.isEditor() && selectedHasTurretBar());
            t.table(Styles.black6, bar -> {
                bar.margin(3f);
                Table content = new Table();
                bar.add(content);
                content.update(() -> {
                    Unit unit = selectedUnit();
                    long signature = turretSignature(unit);
                    if(unit != turretBarUnit || signature != turretBarSignature){
                        turretBarUnit = unit;
                        turretBarSignature = signature;
                        rebuildTurretBar(content, unit);
                    }
                });
            }).padLeft(mobile ? 175f : 0f).padBottom(mobile ? 12f : 8f);
        });

        //analog stick (touch controls only): the hull answers it directly, so dragging the
        //map with another finger and the stick never fight over the same finger. The switch
        //lives in the controls menu; with it off, the original drag control is the only one.
        parent.fill(t -> {
            t.name = "analogstick";
            t.bottom().right();
            t.visible(() -> mobile && shown() && !state.isMenu() && !state.isEditor() && !player.dead()
                && Core.settings.getBool(KeybindDialog.analogKey, true));
            t.touchable = Touchable.childrenOnly;
            t.add(buildAnalogStick()).size(Scl.scl(120f)).pad(Scl.scl(18f));
        });

        buildTestFeaturesPanel(parent);
        blockfrag.build(parent);
    }

    private void buildTestFeaturesPanel(Group parent){
        parent.fill(t -> {
            t.bottom().right();
            t.visible(() -> shown() && state.isGame() && TestNewFeaturesMode.active());
            t.table(Styles.black6, panel -> {
                panel.margin(8f).top().left();
                panel.add("@mode.testfeatures.units").style(Styles.outlineLabel).left().padBottom(5f).row();
                panel.pane(scroll -> {
                    scroll.left().top();
                    //columns follow the pane width, so phones show more than four units per row
                    float width = Core.graphics.getWidth() * (mobile ? 0.22f : 0.28f);
                    int columns = Mathf.clamp((int)(width / Scl.scl(46f)), 4, 7);
                    int index = 0;
                    float btnSize = mobile ? 36f : 44f;
                    float imgSize = mobile ? 26f : 32f;
                    for(UnitType type : TestNewFeaturesMode.aerialTypes()){
                        var btn = scroll.button(new TextureRegionDrawable(type.uiIcon), Styles.selecti, () -> {
                            if(!TestNewFeaturesMode.spawn(type)){
                                ui.showInfoToast(Core.bundle.get("mode.testfeatures.limit", "Unit cap reached or no valid spawn location."), 3f);
                            }
                        }).size(btnSize).tooltip(type.localizedName).pad(2f).get();
                        btn.resizeImage(imgSize);
                        if(++index % columns == 0) scroll.row();
                    }
                }).size(mobile ? 188f : 248f, mobile ? 138f : 184f).top().left().row();
                panel.button("@mode.testfeatures.setup.button", Icon.settings, TestNewFeaturesMode::openSetup)
                    .width(mobile ? 188f : 248f).height(34f).padTop(4f).left()
                    .visible(() -> !TestNewFeaturesMode.wavesStarted).row();
                panel.add("@mode.testfeatures.modules").color(Pal.accent).left().padTop(6f).row();
                panel.label(() -> {
                    Unit unit = selectedUnit();
                    return unit == null ? Core.bundle.get("mode.testfeatures.modules.none")
                        : Core.bundle.format("mode.testfeatures.modules.target", unit.type.localizedName);
                }).color(Color.lightGray).width(mobile ? 188f : 248f).wrap().left().row();
                panel.button("@mode.testfeatures.modules.open", () -> openTurretDialog(selectedUnit()))
                    .width(mobile ? 188f : 248f).height(34f).padTop(4f).left()
                    .disabled(b -> {
                        Unit unit = selectedUnit();
                        return unit == null || unit.type.turretSlots.isEmpty();
                    });
                buildEnemyBuildings(panel);
            }).pad(4f);
        });

        buildEnemyBuildPlacer(parent);
    }

    /**
     * Arm / disarm section of the test-mode panel: enemy team, block to drop, remove mode.
     *
     * <p>It is rebuilt only when the armed block, team or mode changes (same trick as the turret
     * bar), so the labels and toggles stay in sync without flickering every frame.</p>
     */
    private void buildEnemyBuildings(Table panel){
        float width = mobile ? 188f : 248f;

        Table section = new Table();
        section.left();
        panel.add(section).width(width).left().row();
        section.update(() -> {
            long signature = enemyToolSignature();
            if(signature != enemySignature){
                enemySignature = signature;
                rebuildEnemyBuildings(section, width);
            }
        });
    }

    private long enemyToolSignature(){
        //block ids, team ids, the remove flag and the rotation are the only things that change the section
        long block = TestNewFeaturesMode.enemyBlock == null ? -1 : TestNewFeaturesMode.enemyBlock.id;
        return block * 31L + (TestNewFeaturesMode.enemyRemoving ? 7 : 0) + (TestNewFeaturesMode.enemyTeam == null ? 0 : TestNewFeaturesMode.enemyTeam.id) + control.input.rotation * 3L;
    }

    private void rebuildEnemyBuildings(Table section, float width){
        section.clearChildren();
        section.left();

        section.add("@mode.testfeatures.buildings").color(Pal.accent).left().padTop(6f).padBottom(4f).row();

        //team row: one toggle per team that can hold enemy structures
        section.table(teams -> {
            teams.left();
            teams.defaults().growX().padRight(2f).padBottom(2f).height(24f);
            for(Team team : TestNewFeaturesMode.enemyTeams()){
                teams.button(team.localized(), Styles.flatToggleMenut, () -> TestNewFeaturesMode.enemyTeam = team)
                    .update(b -> b.setChecked(TestNewFeaturesMode.enemyTeam == team)).get();
            }
        }).width(width).left().row();

        section.label(() -> {
            Block block = TestNewFeaturesMode.enemyBlock;
            StringBuilder text = new StringBuilder(Core.bundle.format("mode.testfeatures.buildings.team", TestNewFeaturesMode.enemyTeam.localized()));
            text.append('\n').append(TestNewFeaturesMode.enemyRemoving
                ? Core.bundle.get("mode.testfeatures.buildings.removing")
                : block == null
                ? Core.bundle.get("mode.testfeatures.buildings.hint")
                : Core.bundle.format("mode.testfeatures.buildings.tap", block.localizedName) + "\n[lightgray]" +
                Core.bundle.format("mode.testfeatures.buildings.rotate", control.input.rotation));
            return text.toString();
        }).color(Color.lightGray).width(width).wrap().left().padBottom(4f).row();

        //block button: arms a block, and disarms it (same as cancel) when clicked again
        section.button(b -> {
            b.left();
            b.image().size(mobile ? 24f : 28f).padRight(6f).update(i -> {
                Block block = TestNewFeaturesMode.enemyBlock;
                i.setDrawable(block == null ? Icon.add : new TextureRegionDrawable(block.uiIcon));
                i.setScaling(Scaling.fit);
            });
            b.label(() -> TestNewFeaturesMode.enemyBlock == null
                ? Core.bundle.get("mode.testfeatures.buildings.select")
                : TestNewFeaturesMode.enemyBlock.localizedName).growX().wrap();
        }, Styles.defaultt, () -> {
            if(TestNewFeaturesMode.enemyBlock == null){
                openEnemyBuildingDialog();
            }else{
                TestNewFeaturesMode.enemyBlock = null;
            }
        }).growX().height(mobile ? 36f : 40f).left().padBottom(2f).row();

        section.button("@mode.testfeatures.buildings.remove", Icon.cancel, Styles.cleart, () -> {
            TestNewFeaturesMode.enemyRemoving = !TestNewFeaturesMode.enemyRemoving;
            if(TestNewFeaturesMode.enemyRemoving) TestNewFeaturesMode.enemyBlock = null;
        }).checked(b -> TestNewFeaturesMode.enemyRemoving).growX().left().padBottom(2f).row();

        section.button(b -> b.add(Core.bundle.format("mode.testfeatures.buildings.clear", TestNewFeaturesMode.enemyTeam.localized())).left(),
            Styles.cleart, this::clearEnemyStructures).growX().left();
    }

    /**
     * Invisible full-screen click target for the enemy-structure tool.
     *
     * <p>It lives at the back of the HUD group, so every real HUD widget still receives its own
     * taps. It is only touchable while the tool is armed: a tap on empty space then drops (or
     * removes) a structure for the enemy team instead of selecting units or aiming.</p>
     */
    private void buildEnemyBuildPlacer(Group parent){
        Table placer = new Table();
        placer.name = "enemybuildplacer";
        placer.touchable = Touchable.disabled;
        placer.update(() -> placer.touchable = TestNewFeaturesMode.enemyToolArmed() ? Touchable.enabled : Touchable.disabled);
        placer.clicked(this::placeEnemyAtCursor);
        //right click disarms the tool: it also blocks aiming, which is how the normal build menu
        //is dismissed, so the player can always get back to shooting in one click
        placer.addListener(new ClickListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(button != KeyCode.mouseRight) return false;

                TestNewFeaturesMode.enemyBlock = null;
                TestNewFeaturesMode.enemyRemoving = false;
                event.stop();
                return true;
            }
        });

        parent.addChild(placer);
        placer.setFillParent(true);
        placer.toBack();

        //the plan preview uses the same layer the regular build plans are drawn on
        Events.run(Trigger.drawOver, () -> Draw.draw(Layer.plans, this::drawEnemyPlan));
    }

    /** Drops (or removes) the enemy structure under the cursor. */
    private void placeEnemyAtCursor(){
        if(!state.isGame() || !TestNewFeaturesMode.active()) return;

        int x = World.toTile(Core.input.mouseWorldX()), y = World.toTile(Core.input.mouseWorldY());

        if(TestNewFeaturesMode.enemyRemoving){
            Block previous = world.tile(x, y) == null ? null : world.tile(x, y).block();
            if(TestNewFeaturesMode.removeEnemyBuilding(x, y) && previous != null){
                previous.breakSound.at(x * tilesize, y * tilesize);
            }
            return;
        }

        Block block = TestNewFeaturesMode.enemyBlock;
        if(block == null) return;

        if(TestNewFeaturesMode.placeEnemyBuilding(block, TestNewFeaturesMode.enemyTeam, x, y, control.input.rotation, block.nextConfig())){
            block.placeSound.at(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        }
    }

    /** Removes every structure of the armed team from the test map. */
    private void clearEnemyStructures(){
        int removed = TestNewFeaturesMode.clearEnemyBuildings(TestNewFeaturesMode.enemyTeam);
        ui.showInfoToast(Core.bundle.format(removed > 0 ? "mode.testfeatures.buildings.cleared" : "mode.testfeatures.buildings.none",
            TestNewFeaturesMode.enemyTeam.localized(), removed), 3f);
    }

    private void openEnemyBuildingDialog(){
        if(enemyDialog == null){
            enemyDialog = new EnemyBuildingDialog();
        }
        enemyDialog.show(() -> {});
    }

    /** Draws the ghost of the armed enemy structure at the cursor, like a regular build plan. */
    private void drawEnemyPlan(){
        if(!state.isGame() || !TestNewFeaturesMode.enemyToolArmed()) return;

        Tile tile = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if(tile == null) return;

        Block block = TestNewFeaturesMode.enemyBlock;
        int x = tile.x, y = tile.y;
        if(block != null && block.isMultiblock()){
            //preview the tile the structure would really land on: multiblocks clamp to their center
            x = Mathf.clamp(x, (block.size - 1) / 2, world.width() - block.size / 2 - 1);
            y = Mathf.clamp(y, (block.size - 1) / 2, world.height() - block.size / 2 - 1);
        }

        Lines.stroke(2f, TestNewFeaturesMode.enemyTeam.color);
        Lines.rect(x * tilesize - tilesize / 2f, y * tilesize - tilesize / 2f, block == null ? tilesize : block.size * tilesize, block == null ? tilesize : block.size * tilesize);
        Draw.reset();

        if(block == null || block.uiIcon == null) return;

        enemyPlan.set(x, y, control.input.rotation, block);
        enemyPlan.config = block.nextConfig();
        enemyPlan.animScale = 1f;
        block.drawPlan(enemyPlan, emptyPlans, true);
        Draw.reset();
    }

    /** @return the first selected unit, or the unit currently controlled by the player. */
    private @Nullable Unit selectedUnit(){
        if(!control.input.selectedUnits.isEmpty()) return control.input.selectedUnits.first();
        if(player != null && !player.dead() && player.unit() != null) return player.unit();
        return null;
    }

    /** @return true if the selected unit has a dorsal bar worth showing. */
    private boolean selectedHasTurretBar(){
        Unit unit = selectedUnit();
        if(unit == null) return false;
        if(!unit.type.turretSlots.isEmpty()) return true;
        if(unit.supportMounts != null && unit.supportMounts.length > 0) return true;

        for(WeaponMount mount : unit.mounts){
            if(mount.weapon.isTurretMount()) return true;
        }
        return false;
    }

    /** Identity signature used to rebuild the bar only when the turret/support mount list changes. */
    private static long turretSignature(@Nullable Unit unit){
        if(unit == null) return Long.MIN_VALUE;

        long signature = System.identityHashCode(unit);
        for(WeaponMount mount : unit.mounts){
            if(mount.weapon.isTurretMount()){
                signature = signature * 31L + System.identityHashCode(mount.weapon);
            }
        }
        //support cells are rebuilt when a module is installed or removed; toggling active
        //does not need a rebuild because each cell updates its own badge/border every frame.
        if(unit.supportMounts != null){
            for(SupportMount mount : unit.supportMounts){
                if(mount != null) signature = signature * 31L + System.identityHashCode(mount);
            }
        }
        //wall plates sit in turret slots, so installing or removing one reshapes the bar too
        if(unit.wallMounts != null){
            for(WallMount mount : unit.wallMounts){
                if(mount != null) signature = signature * 31L + System.identityHashCode(mount);
            }
        }
        return signature;
    }

    /** Forces a rebuild of the bottom bar on the next frame (a mount was added or removed). */
    private void refreshTurretBar(){
        turretBarSignature = Long.MIN_VALUE;
    }

    private void openTurretDialog(Unit unit){
        if(unit == null) return;
        if(turretDialog == null){
            turretDialog = new TurretLoadoutDialog();
        }
        turretDialog.show(unit, this::refreshTurretBar);
    }

    private static String turretName(Weapon weapon){
        if(weapon instanceof TurretWeapon turretWeapon){
            return turretWeapon.turret.localizedName;
        }
        if(weapon.moduleDisplayName != null && !weapon.moduleDisplayName.isEmpty()){
            return weapon.moduleDisplayName;
        }
        return Core.bundle.get("weapon." + weapon.name + ".name", weapon.name);
    }

    private static float reloadFraction(WeaponMount mount){
        if(mount.weapon.reload <= 0f) return 1f;
        return Mathf.clamp(1f - mount.reload / mount.weapon.reload);
    }

    /**
     * @return the analog stick of the touch controls, wired to the mobile input handler.
     * Created once per HUD build; the handler side only reads the vector it writes.
     */
    private Table buildAnalogStick(){
        if(!(control.input instanceof MobileInput mobile)){
            //no mobile handler (desktop, headless): the stick would have nowhere to write
            return new Table();
        }

        AnalogStick stick = new AnalogStick(mobile.analogVector, mobile::syncAnalog);
        stick.name = "analogstick";
        return stick;
    }

    /** Rebuilds the bottom bar: supply bars on top, one cell per module below. */
    private void rebuildTurretBar(Table table, @Nullable Unit unit){
        table.clearChildren();
        table.left();

        if(unit == null) return;

        //Cells are as large on phones as on desktop: the dorsal bar is a touch surface, and the
        //small icons that fit a 44 unit cell are hard to hit when the game is in the player's hands.
        float cellSize = mobile ? 56f : 54f;
        float cellPad = mobile ? 2f : 2f;

        //Two tables, on purpose. A cell in a table's first column sets that column's width for
        //every row of that table, so the wide supply bar sitting in the same table as the
        //modules left the gap seen between the first module and the second: the first column
        //became as wide as the bar. Stacking the bars in their own table keeps the modules
        //evenly spaced and lets the bars be as wide as they need to be.
        Table bars = new Table();
        bars.left();
        Table modules = new Table();
        modules.left();

        //the bars are read, never clicked, so they take the room of five module cells; the width
        //is set on the cell itself, exactly as a label's is, so the bars growX to fill it
        float supplyWidth = 5f * (cellSize + cellPad * 2f);
        //the extra bottom padding is the air between the bars and the modules below
        bars.add(supplyCell(unit)).width(supplyWidth).left().padBottom(cellPad + 8f).row();

        //module numbering: the same order the number keys use, so key 1 is the first cell
        int index = 0;

        for(WeaponMount mount : unit.mounts){
            if(mount.weapon.isTurretMount()){
                int moduleIndex = index++;
                modules.add(turretCell(unit, mount, moduleIndex)).size(cellSize).pad(cellPad).tooltip(turretTooltip(unit, moduleIndex, mount.weapon));
            }
        }

        //support modules sit immediately beside the turrets. The badge on top of each icon
        //shows whether its effect is running; clicking toggles it without uninstalling.
        if(unit.supportMounts != null){
            for(SupportMount mount : unit.supportMounts){
                if(mount == null || mount.block == null) continue;
                int moduleIndex = index++;
                modules.add(supportCell(unit, mount, moduleIndex)).size(cellSize).pad(cellPad).tooltip(supportTooltip(unit, moduleIndex, mount));
            }
        }

        //wall plates occupy turret slots and always defend while installed - no badge, no
        //click; the icon shows the plate's own colour
        if(unit.wallMounts != null){
            for(WallMount mount : unit.wallMounts){
                if(mount == null || mount.block == null) continue;
                modules.add(wallCell(unit, mount)).size(cellSize).pad(cellPad).tooltip(wallTooltip(mount));
            }
        }

        //a slot is only free when no turret and no plate sits in it
        if(unit.mountedTurrets() + unit.wallMounts.length < unit.type.turretSlots.size){
            modules.add(actionCell(unit, Icon.add, Core.bundle.get("turret.loadout.add")))
                .size(cellSize).pad(cellPad).tooltip(Core.bundle.get("turret.loadout.add"));
        }

        modules.add(actionCell(unit, Icon.hammer, Core.bundle.get("turret.loadout.title")))
            .size(cellSize).pad(cellPad).tooltip(Core.bundle.get("turret.loadout.title"));

        //bars above, modules below, both left-aligned in the same column
        table.add(bars).left().row();
        table.add(modules).left().row();
    }

    /**
     * Acts on one module of the bottom bar, with a toast naming it: this is the touch
     * equivalent of the module number keys. A weapon module becomes the only one to fire; a
     * support module is switched on or off, keeping its effect installed. The loadout dialog
     * keeps the mount itself untouched.
     */
    private void selectModule(Unit unit, int index){
        if(unit == null) return;
        if(control.input != null) control.input.selectModule(unit, index);
    }

    private Table actionCell(Unit unit, Drawable icon, String tooltip){
        Table cell = new Table();
        cell.background(Styles.black3);
        cell.button(icon, Styles.clearNonei, () -> openTurretDialog(unit))
            .size(44f).tooltip(tooltip);
        return cell;
    }

    /** height of each of the three stacked supply bars: tall enough for the outline font */
    private static final float barHeight = mobile ? 14f : 18f;

    /**
     * The hull's supply cell in the dorsal bar: three stacked bars - ammo, liquid and energy -
     * each labelled with the pool it shows, coloured by that pool's chosen resource (the neutral
     * grey when nothing feeds it) and carrying its numbers in a label of its own beside it.
     *
     * <p>The pool name is the bar's own label, so the bars are tall enough for the outline font
     * that Bar draws; the amount cannot go in there as well - "Municao: Explosivo 80 / 40" needs
     * more width than the dorsal bar should take - so it lives in a small label to the right. The
     * full reading stays in the tooltip. Values are read live from the pools, which the loadout
     * dialog rebuilds on every mount, removal and resource change.
     */
    private Table supplyCell(Unit unit){
        ModuleSupply supply = unit.moduleSupply();

        Table cell = new Table();
        cell.background(Styles.black3);

        //one row per pool, with air between the bars and a larger gap under the last one, so
        //the cell reads as three separate meters and not as a single dark block
        poolRow(cell, poolName("ammo"), supply::ammoColor, supply::ammoFraction,
            () -> hudFraction(supply.ammo, supply.ammoCapacity),
            Core.bundle.format("module.supply.ammo", hudAmmoName(supply.item), (int)supply.ammo, (int)supply.ammoCapacity), 5f);

        poolRow(cell, poolName("liquid"), supply::liquidColor, supply::liquidFraction,
            () -> hudFraction(supply.liquidAmount, supply.liquidCapacity),
            Core.bundle.format("module.supply.liquid", hudAmmoName(supply.liquid), (int)supply.liquidAmount, (int)supply.liquidCapacity), 3f);

        poolRow(cell, poolName("energy"), supply::energyColor, supply::energyFraction,
            () -> hudFraction(supply.energy, supply.energyCapacity),
            Core.bundle.format("module.supply.energy", (int)supply.energy, (int)supply.energyCapacity), 3f);

        return cell;
    }

    /**
     * One row of the supply cell: the labelled bar on the left and its numbers on the right.
     *
     * @param name localized pool name drawn inside the bar
     * @param top padding of the row, which separates it from the one above
     * @param tip tooltip text with the resource and the full reading of the pool
     */
    private void poolRow(Table cell, Prov<CharSequence> name, Prov<Color> color, Floatp fraction,
        Prov<CharSequence> value, String tip, float top){
        //the tooltip carries the resource and the full reading: the bar has room only for the
        //pool name, and the numbers beside it only for the amount
        cell.add(new Bar(name, color, fraction))
            .growX().height(barHeight).padTop(top).padLeft(6f).tooltip(tip);
        cell.add(hudValue(value, color)).left().padLeft(4f).padRight(6f).padTop(top).row();
    }

    /** @return the localized short name of a pool, the one written on its bar. */
    private static Prov<CharSequence> poolName(String pool){
        return () -> Core.bundle.get("module.supply." + pool + ".short");
    }

    /**
     * @return the small label that carries one bar's numbers. Its colour is the pool's own, so
     * the reading belongs to the bar beside it.
     */
    private static Label hudValue(Prov<CharSequence> text, Prov<Color> color){
        Label label = new Label(text);
        label.setFontScale(0.75f);
        label.update(() -> label.setColor(color.get()));
        return label;
    }

    /** @return the short label of a HUD pool bar: only the numbers, never the resource name. */
    private static String hudFraction(float value, float capacity){
        return (int)value + "/" + (int)capacity;
    }

    /** @return the localized name of an ammo resource, or the "none" placeholder. */
    private static String hudAmmoName(@Nullable UnlockableContent resource){
        return resource == null ? Core.bundle.get("module.supply.none") : resource.localizedName;
    }

    /** Tooltip of a weapon cell: name, module kind, firing state and the number key. */
    private String turretTooltip(Unit unit, int index, Weapon weapon){
        boolean removable = weapon.removable;
        String state = removable ? Core.bundle.get("turret.hud.removable") : Core.bundle.get("turret.hud.native");
        //the pilot's pick is the state worth reading: every other module stays silent
        String power = Core.bundle.get(moduleSelected(unit, index) ? "module.hud.selected"
            : (moduleActive(unit, index) ? "support.hud.active" : "module.hud.silent"));
        return turretName(weapon)
            + "\n[lightgray]" + state + " · " + power
            + "\n" + Core.bundle.format("module.hint.select", moduleKeyName(index));
    }

    private static boolean moduleActive(Unit unit, int index){
        return unit != null && unit.moduleActive(index);
    }

    private static boolean moduleSelected(Unit unit, int index){
        return unit != null && unit.moduleSelected(index);
    }

    /** @return the label of the number key that switches this module: 1..9, then 0. */
    private static String moduleKeyName(int index){
        return String.valueOf((index + 1) % 10);
    }

    /**
     * @return the small number badge drawn on a module cell: the key (or the fingertip target)
     * that switches the module on and off.
     */
    private static void moduleBadge(Stack stack, int index){
        Table badge = new Table();
        badge.top().left();
        badge.touchable = Touchable.disabled;
        Label label = badge.label(() -> moduleKeyName(index)).style(Styles.outlineLabel)
            .fontScale(mobile ? 0.7f : 0.85f).get();
        label.setColor(Pal.accent);
        badge.pack();
        stack.add(badge);
    }

    /**
     * A weapon cell of the bottom bar: icon with the module number, reload and heat bars, and
     * the pilot's selection. Clicking picks the module: from then on it is the only one that
     * fires, every other module of the hull stays mounted and silent. Clicking the same cell
     * again lets every module fire together.
     */
    private Table turretCell(Unit unit, WeaponMount mount, int index){
        Weapon weapon = mount.weapon;
        boolean removable = weapon.removable;
        float baseThickness = removable ? 3f : 2f;
        float iconSize = 44f;
        TextureRegion region = weapon.region != null && weapon.region.found() ? weapon.region : Core.atlas.find(weapon.name);
        if(region == null || !region.found()){
            region = unit.type.uiIcon;
        }

        Table cell = new Table();
        cell.background(Styles.black3);

        Stack stack = new Stack();
        BorderImage icon = new BorderImage(region, baseThickness);
        icon.setScaling(Scaling.fit);
        icon.border(removable ? Pal.accent : Pal.gray);
        stack.add(icon);

        moduleBadge(stack, index);

        //the star is what a pilot reads at a glance: this module fires while the rest waits
        Table mark = new Table();
        mark.bottom().right();
        mark.touchable = Touchable.disabled;
        mark.visible(() -> moduleSelected(unit, index));
        mark.image(Icon.star).size(13f).pad(1f).get().setColor(Pal.accent);
        stack.add(mark);

        if(!removable){
            Table lock = new Table();
            lock.top().right();
            lock.image(Icon.lockSmall).size(13f).pad(1f).get().setColor(Pal.gray);
            stack.add(lock);
        }

        cell.add(stack).size(iconSize).pad(2f).row();

        Bar reload = new Bar(() -> "", () -> removable ? Pal.accent : Pal.gray, () -> reloadFraction(mount));
        reload.blink(Color.white);
        cell.add(reload).height(4f).growX().pad(0f, 2f, 1f, 2f).row();

        Bar heat = new Bar(() -> "", () -> Pal.health, () -> Mathf.clamp(mount.heat));
        cell.add(heat).height(2f).growX().pad(0f, 2f, 2f, 2f).row();

        cell.clicked(() -> selectModule(unit, index));
        cell.update(() -> {
            boolean selected = unit.moduleSelected(index);
            boolean on = unit.moduleActive(index);
            //the picked module wears the accent border at full thickness, and the rest of the
            //loadout fades: armed but unpicked keeps its own border, a silenced module greys out
            icon.border(selected || (on && removable) ? Pal.accent : Pal.gray);
            icon.thickness = selected ? 4f : baseThickness;
            icon.setColor(on ? Color.white : Color.gray);
            cell.background(on ? Styles.black3 : Styles.black6);
        });
        return cell;
    }

    /** @return the bundle key for a support module's current state. Public so headless tests can verify it. */
    public static String supportStateKey(boolean active){
        return active ? "support.hud.active" : "support.hud.inactive";
    }

    /** @return the tooltip of a support module cell: name, category, current state and hint. */
    private String supportTooltip(Unit unit, int index, SupportMount mount){
        String aim = Core.bundle.get(mount.autoTarget ? "module.aim.auto" : "module.aim.manual");
        return mount.block.localizedName
            + "\n[lightgray]" + Core.bundle.get("support.category." + mount.category.name())
            + " · " + Core.bundle.get(supportStateKey(mount.active))
            + " · " + aim
            + "\n" + Core.bundle.format("module.hint.key", moduleKeyName(index));
    }

    /**
     * A support module cell in the bottom dorsal bar. It mirrors {@link #turretCell} but shows
     * an ATIVO/INATIVO badge on top of the icon and toggles the effect on click. The module is
     * never removed here, so its anchor, category and footprint stay reserved.
     */
    private Table supportCell(Unit unit, SupportMount mount, int index){
        return moduleCell(unit, mount.block, () -> mount.active, () -> selectModule(unit, index),
            () -> supportTooltip(unit, index, mount), index);
    }

    /** @return the tooltip of a wall plate cell: name, category and the always-on rule. */
    private static String wallTooltip(WallMount mount){
        return mount.block.localizedName
            + "\n[lightgray]" + Core.bundle.get("support.category.armor")
            + "\n" + Core.bundle.get("wall.hud.tip");
    }

    /**
     * A wall plate cell in the bottom dorsal bar. Plates have no on/off toggle: a plate defends
     * while it is mounted, so there is no badge and no click. The icon stays untinted - the
     * plate's colour shows on the hull itself, not on the HUD.
     */
    private Table wallCell(Unit unit, WallMount mount){
        float iconSize = 44f;
        TextureRegion region = mount.block.uiIcon;
        if(region == null || !region.found()) region = unit.type.uiIcon;

        Table cell = new Table();
        cell.background(Styles.black3);

        BorderImage icon = new BorderImage(region, 3f);
        icon.setScaling(Scaling.fit);
        cell.add(icon).size(iconSize).pad(2f).row();

        //a plate is always defending while installed
        Bar status = new Bar(() -> "", () -> Pal.heal, () -> 1f);
        cell.add(status).height(4f).growX().pad(0f, 2f, 2f, 2f).row();

        cell.update(() -> {
            icon.setColor(Color.white);
            icon.border(Pal.accent);
            icon.thickness = 3f;
        });
        return cell;
    }

    /** Shared body of every module cell: icon, live state badge, click toggle. */
    /**
     * Shared body of every module cell that reports a running effect: icon, live state badge,
     * the number of the key that acts on it, and the click action. This shape belongs to the
     * <b>support</b> modules - the ones that have nothing to fire - so the click switches their
     * effect on and off, exactly as the number key does.
     *
     * @param index position in the bar, used for the number badge; negative leaves it out.
     */
    private Table moduleCell(Unit unit, Block block, Boolp active, Runnable toggle, Prov<String> tooltip, int index){
        float iconSize = 44f;
        TextureRegion region = block.uiIcon;
        if(region == null || !region.found()) region = unit.type.uiIcon;

        Table cell = new Table();
        cell.background(Styles.black3);

        Stack stack = new Stack();
        BorderImage icon = new BorderImage(region, 3f);
        icon.setScaling(Scaling.fit);
        stack.add(icon);

        //the number of the key (or the fingertip target) that switches the module off
        if(index >= 0){
            Table number = new Table();
            number.top().left();
            number.touchable = Touchable.disabled;
            Label label = number.label(() -> moduleKeyName(index)).style(Styles.outlineLabel)
                .fontScale(mobile ? 0.7f : 0.85f).get();
            label.setColor(Pal.accent);
            stack.add(number);
        }

        //the badge sits on top of the icon; a text supplier keeps it live after every click
        Table overlay = new Table();
        overlay.top();
        //overlay.top() centers horizontally and pins vertically to the top; do not call
        //center() on the cell, as it would override the vertical alignment.
        Label state = overlay.label(() -> Core.bundle.get(supportStateKey(active.get())))
            .style(Styles.outlineLabel).fontScale(mobile ? 0.55f : 0.65f).get();
        stack.add(overlay);

        cell.add(stack).size(iconSize).pad(2f).row();

        Bar status = new Bar(() -> "", () -> active.get() ? Pal.heal : Pal.remove,
            () -> active.get() ? 1f : 0f);
        cell.add(status).height(4f).growX().pad(0f, 2f, 2f, 2f).row();

        cell.clicked(toggle);
        cell.update(() -> {
            boolean on = active.get();
            state.setColor(on ? Pal.heal : Pal.remove);
            icon.border(on ? Pal.accent : Pal.gray);
            icon.thickness = on ? 3f : 2f;
            icon.setColor(on ? Color.white : Color.gray);
            cell.background(on ? Styles.black3 : Styles.black6);
        });
        return cell;
    }

    @Remote(targets = Loc.both, forward = true, called = Loc.both)
    public static void setPlayerTeamEditor(Player player, Team team){
        if(state.isEditor() && player != null){
            player.team(team);
        }
    }

    public void setHudText(String text){
        showHudText = true;
        hudText = text;
    }

    public void toggleHudText(boolean shown){
        showHudText = shown;
    }

    private void scheduleToast(Runnable run){
        long duration = (int)(3.5 * 1000);
        long since = Time.timeSinceMillis(lastToast);
        if(since > duration){
            lastToast = Time.millis();
            run.run();
        }else{
            Time.runTask((duration - since) / 1000f * 60f, run);
            lastToast += duration;
        }
    }

    public boolean hasToast(){
        return Time.timeSinceMillis(lastToast) < 3.5f * 1000f;
    }

    public void showToast(String text){
        showToast(Icon.ok, text);
    }

    public void showToast(Drawable icon, String text){
        showToast(icon, -1, text);
    }

    public void showToast(Drawable icon, float size, String text){
        if(state.isMenu()) return;

        scheduleToast(() -> {
            Sounds.uiNotify.play();

            Table table = new Table(Tex.button);
            table.update(() -> {
                if(state.isMenu() || !ui.hudfrag.shown()){
                    table.remove();
                }
            });
            table.margin(12);
            var cell = table.image(icon).pad(3);
            if(size > 0) cell.size(size);
            table.add(text).wrap().width(280f).get().setAlignment(Align.center, Align.center);
            table.pack();

            //create container table which will align and move
            Table container = Core.scene.table();
            container.top().add(table);
            container.setTranslation(0, table.getPrefHeight());
            container.actions(Actions.translateBy(0, -table.getPrefHeight(), 1f, Interp.fade), Actions.delay(2.5f),
            //nesting actions() calls is necessary so the right prefHeight() is used
            Actions.run(() -> container.actions(Actions.translateBy(0, table.getPrefHeight(), 1f, Interp.fade), Actions.remove())));
        });
    }

    public void showPauseDisabled(){
        pauseDisableDur = 60f;
    }

    /** Show unlock notification for a new recipe. */
    public void showUnlock(UnlockableContent content){
        //some content may not have icons... yet
        //also don't play in the tutorial to prevent confusion
        if(state.isMenu()) return;

        Sounds.uiNotify.play();

        //if there's currently no unlock notification...
        if(lastUnlockTable == null){
            scheduleToast(() -> {
                Table table = new Table(Tex.button);
                table.update(() -> {
                    if(state.isMenu()){
                        table.remove();
                        lastUnlockLayout = null;
                        lastUnlockTable = null;
                    }
                });
                table.margin(12);

                Table in = new Table();

                //create texture stack for displaying
                Image image = new Image(content.uiIcon);
                image.setScaling(Scaling.fit);

                in.add(image).size(8 * 6).pad(2);

                //add to table
                table.add(in).padRight(8);
                table.add("@unlocked");
                table.pack();

                //create container table which will align and move
                Table container = Core.scene.table();
                container.top().add(table);
                container.setTranslation(0, table.getPrefHeight());
                container.actions(Actions.translateBy(0, -table.getPrefHeight(), 1f, Interp.fade), Actions.delay(2.5f),
                //nesting actions() calls is necessary so the right prefHeight() is used
                Actions.run(() -> container.actions(Actions.translateBy(0, table.getPrefHeight(), 1f, Interp.fade), Actions.run(() -> {
                    lastUnlockTable = null;
                    lastUnlockLayout = null;
                }), Actions.remove())));

                lastUnlockTable = container;
                lastUnlockLayout = in;
            });
        }else{
            //max column size
            int col = 3;
            //max amount of elements minus extra 'plus'
            int cap = col * col - 1;

            //get old elements
            Seq<Element> elements = new Seq<>(lastUnlockLayout.getChildren());
            int esize = elements.size;

            //...if it's already reached the cap, ignore everything
            if(esize > cap) return;

            //get size of each element
            float size = 48f / Math.min(elements.size + 1, col);

            lastUnlockLayout.clearChildren();
            lastUnlockLayout.defaults().size(size).pad(2);

            for(int i = 0; i < esize; i++){
                lastUnlockLayout.add(elements.get(i));

                if(i % col == col - 1){
                    lastUnlockLayout.row();
                }
            }

            //if there's space, add it
            if(esize < cap){

                Image image = new Image(content.uiIcon);
                image.setScaling(Scaling.fit);

                lastUnlockLayout.add(image);
            }else{ //else, add a specific icon to denote no more space
                lastUnlockLayout.image(Icon.add);
            }

            lastUnlockLayout.pack();
        }
    }

    private void toggleMenus(){
        shown = !shown;
    }

    private Table makeStatusTable(){
        Table table = new Table(Tex.wavepane);

        StringBuilder ibuild = new StringBuilder();

        IntFormat
        wavef = new IntFormat("wave"),
        wavefc = new IntFormat("wave.cap"),
        enemyf = new IntFormat("wave.enemy"),
        enemiesf = new IntFormat("wave.enemies"),
        enemycf = new IntFormat("wave.enemycore"),
        enemycsf = new IntFormat("wave.enemycores"),

        waitingf = new IntFormat("wave.waiting", i -> {
            ibuild.setLength(0);
            int m = i/60;
            int s = i % 60;
            if(m > 0){
                ibuild.append(m);
                ibuild.append(":");
                if(s < 10){
                    ibuild.append("0");
                }
            }
            ibuild.append(s);
            return ibuild.toString();
        });

        table.touchable = Touchable.enabled;

        StringBuilder builder = new StringBuilder();

        table.name = "waves";

        table.marginTop(0).marginBottom(4).marginLeft(4);

        class SideBar extends Element{
            public Floatp amount;
            public boolean flip, drawBack;
            public Boolp flash;

            float last, blink, value;

            public SideBar(Floatp amount, Boolp flash, boolean flip, boolean drawBack, Color color){
                this.amount = amount;
                this.flip = flip;
                this.flash = flash;
                this.drawBack = drawBack;

                setColor(color);
            }

            public SideBar(Floatp amount, Boolp flash, boolean flip){
                this(amount, flash, flip, true, Pal.health);
            }

            @Override
            public void draw(){
                float next = amount.get();

                if(Float.isNaN(next) || Float.isInfinite(next)) next = 1f;

                if(next < last && flash.get()){
                    blink = 1f;
                }

                blink = Mathf.lerpDelta(blink, 0f, 0.2f);
                value = Mathf.lerpDelta(value, next, 0.15f);
                last = next;

                if(Float.isNaN(value) || Float.isInfinite(value)) value = 1f;

                if(drawBack) drawInner(Pal.darkishGray, 1f);
                drawInner(Tmp.c1.set(color).lerp(Color.white, blink), value);
            }

            void drawInner(Color color, float fract){
                if(fract < 0) return;

                fract = Mathf.clamp(fract);
                if(flip){
                    x += width;
                    width = -width;
                }

                float stroke = width * 0.35f;
                float bh = height/2f;
                Draw.color(color, parentAlpha);

                float f1 = Math.min(fract * 2f, 1f), f2 = (fract - 0.5f) * 2f;

                float bo = -(1f - f1) * (width - stroke);

                Fill.quad(
                x, y,
                x + stroke, y,
                x + width + bo, y + bh * f1,
                x + width - stroke + bo, y + bh * f1
                );

                if(f2 > 0){
                    float bx = x + (width - stroke) * (1f - f2);
                    Fill.quad(
                    x + width, y + bh,
                    x + width - stroke, y + bh,
                    bx, y + height * fract,
                    bx + stroke, y + height * fract
                    );
                }

                Draw.reset();

                if(flip){
                    width = -width;
                    x -= width;
                }
            }
        }

        table.stack(
        new Element(){
            @Override
            public void draw(){
                Draw.color(Pal.darkerGray, parentAlpha);
                Fill.poly(x + width/2f, y + height/2f, 6, height / Mathf.sqrt3);
                Draw.reset();
                Drawf.shadow(x + width/2f, y + height/2f, height * 1.13f, parentAlpha);
            }
        },
        new Table(t -> {
            float bw = 40f;
            float pad = -20;
            t.margin(0);
            t.clicked(() -> {
                if(!player.dead() && mobile){
                    Call.unitClear(player);
                    control.input.recentRespawnTimer = 1f;
                    control.input.controlledType = null;
                    control.input.droppingItem = false;
                }
            });

            float[] shieldFrac = {0};
            Boolp playerHasPayloads = () -> player.unit() instanceof Payloadc pay && !pay.payloads().isEmpty();
            Floatp playerPayloadCapacityUsed = () -> player.unit() instanceof Payloadc pay ? pay.payloadUsed() / player.unit().type().payloadCapacity : 0f;

            t.stack(
            //health
            new SideBar(() -> player.dead() ? 0f : player.unit().healthf(), () -> true, true),
            //shields
            new SideBar(() -> player.dead() ? 0 : shieldFrac[0], () -> true, true, false, Pal.accent){{
                visible(() -> {
                    if(player.dead()) return false;
                    drawBack = !playerHasPayloads.get();

                    var ab = Structs.find(player.unit().abilities, a -> a instanceof ForceFieldAbility || a instanceof ShieldArcAbility);
                    if(ab instanceof ForceFieldAbility ff){
                        shieldFrac[0] = player.unit().shield / ff.max;
                        return ff.max > 0;
                    }else if(ab instanceof ShieldArcAbility sa){
                        shieldFrac[0] = sa.data / sa.max;
                        return sa.max > 0;
                    }else{
                        return false;
                    }
                });
            }}).width(bw).growY().padRight(pad);

            t.image(() -> player.icon()).scaling(Scaling.bounded).grow().maxWidth(54f);

            t.add(new SideBar(
            () ->
                player.dead() ? 0f :
                player.displayAmmo() ? player.unit().ammof() :
                playerHasPayloads.get() ? playerPayloadCapacityUsed.get() :
                player.unit().healthf(),
            () -> !(player.displayAmmo() || playerHasPayloads.get()), false)).width(bw).growY().padLeft(pad).update(b -> {

                b.color.set(player.displayAmmo() ? Pal.ammo : playerHasPayloads.get() ? Pal.items : Pal.health);
            });

            t.getChildren().get(1).toFront();
        })).size(120f, 80).padRight(4);

        Cell[] lcell = {null};
        boolean[] couldSkip = {true};

        lcell[0] = table.labelWrap(() -> {

            //update padding depend on whether the button to the right is there
            boolean can = canSkipWave();
            if(can != couldSkip[0]){
                if(canSkipWave()){
                    lcell[0].padRight(8f);
                }else{
                    lcell[0].padRight(-42f);
                }
                table.invalidateHierarchy();
                couldSkip[0] = can;
            }

            builder.setLength(0);

            //mission overrides everything
            if(state.rules.mission != null && state.rules.mission.length() > 0){
                builder.append(state.rules.mission);
                return builder;
            }

            //objectives override mission?
            if(state.rules.objectives.any()){
                boolean first = true;
                for(var obj : state.rules.objectives){
                    if(!obj.qualified() || obj.hidden) continue;

                    String text = obj.text();
                    if(text != null && !text.isEmpty()){
                        if(!first) builder.append("\n\n[white]");
                        builder.append(UI.formatIcons(text));

                        first = false;
                    }
                }

                //TODO: display standard status when empty objective?
                if(builder.length() > 0){
                    return builder;
                }
            }

            if(!player.team().activateUnitFactories()){
                builder.append("[lightgray]").append(Core.bundle.format("rules.unitfactoryactivation.objective", "[accent]" + UI.formatTime((float)Math.max(state.rules.unitActivationDelay(player.team()) - state.tick, 0f)))).append("[white]\n");
            }

            if(!state.rules.waves && state.rules.attackMode){
                int sum = Math.max(state.teams.present.sum(t -> t.team != player.team() ? t.cores.size : 0), 1);
                builder.append(sum > 1 ? enemycsf.get(sum) : enemycf.get(sum));
                return builder;
            }

            //do not show status after game over
            if(state.afterGameOver && state.isCampaign()){
                return builder;
            }

            if(!state.rules.waves && state.isCampaign()){
                builder.append("[lightgray]").append(Core.bundle.get("sector.curcapture"));
            }

            if(!state.rules.waves){
                return builder;
            }

            if(state.rules.winWave > 1 && state.rules.winWave >= state.wave){
                builder.append(wavefc.get(state.wave, state.rules.winWave));
            }else{
                builder.append(wavef.get(state.wave));
            }
            builder.append("\n");

            if(state.enemies > 0){
                if(state.enemies == 1){
                    builder.append(enemyf.get(state.enemies));
                }else{
                    builder.append(enemiesf.get(state.enemies));
                }
                builder.append("\n");
            }

            if(state.rules.waveTimer){
                builder.append((logic.isWaitingWave() ? Core.bundle.get("wave.waveInProgress") : (waitingf.get((int)(state.wavetime/60)))));
            }else if(state.enemies == 0){
                builder.append(Core.bundle.get("waiting"));
            }

            return builder;
        }).growX().pad(8f);

        table.row();

        //TODO nobody reads details anyway.
        /*
        table.clicked(() -> {
            if(state.rules.objectives.any()){
                StringBuilder text = new StringBuilder();

                boolean first = true;
                for(var obj : state.rules.objectives){
                    if(!obj.qualified()) continue;

                    String details = obj.details();
                    if(details != null){
                        if(!first) text.append('\n');
                        text.append(details);

                        first = false;
                    }
                }

                //TODO this, as said before, could be much better.
                ui.showInfo(text.toString());
            }
        });*/

        return table;
    }

    private void addInfoTable(Table table){
        table.name = "infotable";
        table.left();

        var count = new float[]{-1};
        table.table().update(t -> {
            if(player.unit() instanceof Payloadc payload){
                if(count[0] != payload.payloadUsed()){
                    t.clear();
                    payload.contentInfo(t, 8 * 2, 275f);
                    count[0] = payload.payloadUsed();
                }
            }else{
                count[0] = -1;
                t.clear();
            }
        }).growX().visible(() -> {
            boolean result = player.unit() instanceof Payloadc p && p.payloadUsed() > 0;
            if(!result) count[0] = -1f;
            return result;
        }).colspan(2);
        table.row();

        Bits statuses = new Bits();

        table.table().update(t -> {
            t.left();
            Bits applied = player.dead() ? null : player.unit().statusBits();
            if(!statuses.equals(applied)){
                t.clear();

                if(applied != null){
                    for(StatusEffect effect : content.statusEffects()){
                        if(applied.get(effect.id) && effect.uiIcon.found()){
                            t.image(effect.uiIcon).scaling(Scaling.fit).size(iconMed).get()
                            .addListener(new Tooltip(l -> l.label(() ->
                                player.dead() ? "" : effect.localizedName + " [lightgray]" + (player.unit().getDuration(effect) >= Float.MAX_VALUE ? "∞" : UI.formatTime(player.unit().getDuration(effect)))).style(Styles.outlineLabel)));
                        }
                    }

                    statuses.set(applied);
                }else{
                    statuses.clear();
                }
            }
        }).left();
    }

    /** Orbital: remaining time of the asteroid expedition, shown only while inside Gier. */
    private void addExpeditionTable(Table table){
        table.name = "expedition";
        table.left();

        table.table(Styles.black6, t -> {
            t.image(Icon.terrain).padLeft(8f).size(18f);

            Label time = t.label(() -> "Expedição " + ExpeditionClock.formattedRemaining()).style(Styles.outlineLabel).pad(8f).get();
            time.update(() -> time.setColor(ExpeditionClock.lowTime() ? Color.scarlet : Color.white));

            t.button(Icon.download, Styles.clearNonei, () -> {
                ItemSeq collected = ExpeditionClock.collectCargo();
                if(collected.total > 0){
                    ui.showInfoToast("Carga recolhida: " + GlobalInventory.format(collected), 5f);
                }else{
                    ui.showInfoToast("Nenhuma carga no núcleo.", 3f);
                }
            }).size(30f).padRight(6f).tooltip("Recolher carga do núcleo para o inventário global")
                .disabled(b -> !ExpeditionClock.hasCargo());
        }).padTop(4f);

        table.visible(ExpeditionClock::inExpedition);
    }

    private boolean canSkipWave(){
        return state.rules.waves && state.rules.waveSending && ((net.server() || player.admin) || !net.active()) && state.enemies == 0 && !spawner.isSpawning();
    }

    public boolean shown() {
        return shown && !control.input.logicHideHud;
    }

}
