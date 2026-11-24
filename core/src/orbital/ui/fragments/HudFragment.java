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
    //keeps the compact test-tools panel expanded across frames; collapsed when the player taps its header
    private boolean testFeaturesPanelExpanded = true;
    //signature of the test-features panel content: the content is rebuilt when it changes, because
    //the panel's table is built once and keeps its children afterwards
    private long testFeaturesContentSignature = Long.MIN_VALUE;

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
    /** the framed body of the bottom module bar, kept to measure its height every frame. */
    private @Nullable Table turretBarBody;
    /** turret add/remove menu for the selected unit. */
    private @Nullable TurretLoadoutDialog turretDialog;
    /** shared units/structures catalogue of the test mode. */
    private @Nullable TestFeaturesCatalogDialog catalogDialog;
    /** test-tools panel below the command tablet, kept to reserve its vertical space. */
    private @Nullable Table testFeaturesPanel;
    /** largest classic build-menu height seen this session, in ui units. */
    private float buildMenuReservation;
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
            t.visible(() -> (Core.settings.getBool("minimap") || state.isCampaign()) && shown());

            //research and fixed-camera controls live beside the minimap, on its left
            t.table(tools -> {
                tools.top().right();
                //Battle royale keeps only the menu button; the other top tools stay hidden.
                tools.visible(() -> !BattleRoyaleMode.active());
                float iconSize = 44f;
                tools.button(Icon.tree, Styles.clearNonei, iconSize, () -> ui.research.show())
                    .name("research-pinned")
                    .tooltip("@research")
                    .padRight(6f);

                tools.button(Icon.lockOpen, Styles.clearNoneTogglei, iconSize,
                    orbital.input.FixedCameraControl::toggle)
                    .name("fixed-camera-toggle")
                    .tooltip("@fixedcamera.toggle")
                    .update(b -> b.setChecked(orbital.input.FixedCameraControl.enabled()))
                    .padRight(6f);

                tools.button(Icon.move, Styles.clearNonei, iconSize,
                    orbital.input.FixedCameraControl::armMove)
                    .name("fixed-camera-move")
                    .tooltip("@fixedcamera.move")
                    .visible(() -> orbital.input.FixedCameraControl.enabled()
                        && orbital.input.FixedCameraControl.selected() != null)
                    .update(b -> b.setColor(orbital.input.FixedCameraControl.awaitingDestination()
                        ? Pal.accent : Color.white));

                //"menu" button: returns to the main menu (saves first, like the pause menu's quit).
                //Desktop only: mobile already has one in its top-left button strip.
                tools.button("@menu", Icon.menu, () -> ui.paused.runExitSave())
                    .name("main-menu")
                    .tooltip("@menu")
                    .size(mobile ? 60f : 96f, mobile ? 36f : 44f)
                    .visible(() -> !mobile && shown());
            }).top().right().padRight(6f);

            //minimap + coordinates
            t.table(m -> {
                m.top().right();
                m.add(new Minimap()).name("minimap");
                m.row();
                m.label(() ->
                    (Core.settings.getBool("position") ? player.tileX() + "," + player.tileY() + "\n" : "") +
                    (Core.settings.getBool("mouseposition") ? "[lightgray]" + World.toTile(Core.input.mouseWorldX()) + "," + World.toTile(Core.input.mouseWorldY()) : ""))
                .visible(() -> Core.settings.getBool("position") || Core.settings.getBool("mouseposition"))
                .touchable(Touchable.disabled)
                .style(Styles.outlineLabel)
                .name("position");
            }).visible(() -> Core.settings.getBool("minimap"));

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

                cont.collapser(select -> {
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
                    flip.visible(() -> !BattleRoyaleMode.active());

                    select.button(Icon.paste, style, ui.schematics::show)
                    .name("schematics").visible(() -> !BattleRoyaleMode.active());

                    select.button(Icon.pause, style, () -> {
                        if(net.active()){
                            ui.listfrag.toggle();
                        }else if(!state.rules.pauseDisabled){
                            state.set(state.isPaused() ? State.playing : State.paused);
                        }
                    }).name("pause").visible(() -> !BattleRoyaleMode.active()).update(i -> {
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
                    }).name("chat").visible(() -> !BattleRoyaleMode.active()).update(i -> {
                        if(net.active() && mobile){
                            i.getStyle().imageUp = Icon.chat;
                        }else if(state.isCampaign()){
                            i.getStyle().imageUp = Icon.tree;
                        }else{
                            i.getStyle().imageUp = Icon.book;
                        }
                    });

                    select.image().color(Pal.gray).width(4f).fillY()
                        .visible(() -> !BattleRoyaleMode.active());
                    //there is no back button on iOS, so the menu has to be shown at all times.
                //8x8: the strip's only live button is the menu, and it moves into the status
                //box below. A collapser removes the empty strip from the layout altogether
                //(visible=false would still reserve its row), so the box reaches the corner.
                }, () -> !BattleRoyaleMode.active() && (OS.isIos || !control.input.logicHideHud));

                cont.row();
                cont.collapser(sep -> sep.image().height(4f).color(Pal.gray).fillX(),
                    () -> !BattleRoyaleMode.active());
                cont.row();
            }

            //research and fixed-camera controls were moved next to the minimap (see above)

            cont.update(() -> {
                //right click / escape cancels a pending fixed-camera move destination
                if(orbital.input.FixedCameraControl.awaitingDestination() &&
                    (Core.input.keyTap(KeyCode.escape) || Core.input.keyTap(KeyCode.mouseRight))){
                    orbital.input.FixedCameraControl.cancelMove();
                }

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

                //Table with the skip-wave button. In 8x8 it is collapsed instead of merely
                //hidden: an invisible cell still reserves its 40px, which left a gap between
                //the status box and the menu button.
                s.collapser(skip -> {
                    skip.button(Icon.play, rightStyle, 30f, () -> {
                        if(net.client() && player.admin){
                            Call.adminRequest(player, AdminAction.wave, null);
                        }else{
                            logic.skipWave();
                        }
                    }).growY().fillX().right().width(40f).disabled(b -> !canSkipWave()).name("skip").get().toBack();
                }, () -> !BattleRoyaleMode.active());

                //8x8 mobile: the menu button is inside the status box itself, right after
                //the unit icon and its health bar (see makeStatusTable). A separate cell here
                //always left a gap, so no menu button is added to this outer table.
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

                //all readouts share one row: no row() calls, so FPS, memory and (when
                //connected) ping/TPS stay side by side instead of stacking down the screen.
                info.label(() -> fps.get(Core.graphics.getFramesPerSecond())).left().style(Styles.outlineLabel).name("fps").padRight(10f);

                if(android){
                    info.label(() -> memnative.get((int)(Core.app.getJavaHeap() / 1024 / 1024), (int)(Core.app.getNativeHeap() / 1024 / 1024))).left().style(Styles.outlineLabel).name("memory2").padRight(10f);
                }else{
                    info.label(() -> mem.get((int)(Core.app.getJavaHeap() / 1024 / 1024))).left().style(Styles.outlineLabel).name("memory").padRight(10f);
                }

                info.label(() -> ping.get(netClient.getPing())).visible(net::client).left().style(Styles.outlineLabel).name("ping").padRight(10f);
                info.label(() -> tps.get(state.serverTps == -1 ? 60 : state.serverTps)).visible(net::client).left().style(Styles.outlineLabel).name("tps");

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
            //The hud is built while still in the menu, so the corner must follow the mode live:
            //battle royale puts the readout in the top-right corner, clear of both touch sticks.
            float[] lastMargin = {Float.NaN};
            t.update(() -> {
                boolean br = BattleRoyaleMode.active();
                alignCorner(t, br ? Align.topRight : Align.bottomLeft);
                //the top-right corner may hold the minimap; leave room for it before the modules
                float margin = br ? (Core.settings.getBool("minimap") ? 160f : 8f) : 0f;
                if(lastMargin[0] != margin){
                    lastMargin[0] = margin;
                    t.marginTop(margin);
                    t.marginBottom(0f);
                    t.invalidateHierarchy();
                }
            });
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
                //kept to measure the bar: the touch command buttons sit above it
                turretBarBody = bar;
            //the corner belongs to the modules: the command buttons are a column above this
            //bar, so nothing is squeezed beside the module cells
            }).padBottom(mobile ? 12f : 8f);
        });

        //analog stick (touch controls only): the hull answers it directly, so dragging the
        //map with another finger and the stick never fight over the same finger. The switch
        //lives in the controls menu; with it off, the original drag control is the only one.
        parent.fill(t -> {
            t.name = "analogstick";
            t.bottom().right();
            //Mirrored in battle royale: stick on the left, hud readout on the right.
            t.update(() -> alignCorner(t, BattleRoyaleMode.active() ? Align.bottomLeft : Align.bottomRight));
            t.visible(() -> mobile && shown() && !state.isMenu() && !state.isEditor() && !player.dead()
                && Core.settings.getBool(KeybindDialog.analogKey, true));
            t.touchable = Touchable.childrenOnly;
            //Cell sizes are scaled by Arc once. Passing Scl.scl(...) here scaled the stick a
            //second time on Android, making it reach into the test-tools panel above it.
            t.add(buildAnalogStick()).size(120f).pad(18f);
        });

        //8x8 twin-stick: the right stick aims and fires the damage weapons while held.
        parent.fill(t -> {
            t.name = "aimstick";
            t.bottom().right();
            t.visible(() -> mobile && shown() && BattleRoyaleMode.active() && !state.isMenu()
                && !state.isEditor() && !player.dead());
            t.touchable = Touchable.childrenOnly;
            t.add(buildAimStick()).size(120f).pad(18f);
        });

        buildTestFeaturesPanel(parent);
        blockfrag.build(parent);
    }

    /** Collapsible tablet-style command deck, always available in-game. */
    private final UnitCommandTablet unitCommandTablet = new UnitCommandTablet();

    private void buildTestFeaturesPanel(Group parent){
        parent.fill(t -> {
            t.bottom().right();
            //keep the right-hand stack off the screen edge: the test panel's long localized
            //labels need room, and a flush panel clips its text on Android's scaled UI
            t.marginRight(8f);
            //Battle royale is a pure squad fight: no command tablet or build palette.
            t.visible(() -> shown() && state.isGame() && !BattleRoyaleMode.active());
            t.touchable = Touchable.childrenOnly;

            boolean[] wasTest = {!TestNewFeaturesMode.active()};
            float[] lastPad = {Float.NaN};

            t.update(() -> {
                boolean test = TestNewFeaturesMode.active();
                boolean firstLayout = Float.isNaN(lastPad[0]);
                boolean modeChanged = test != wasTest[0];

                //the block menu owns the bottom-right corner in every mode: the whole stack
                //(tablet + test panel) sits above it, even with the square expanded. Leaving
                //the stack at the bottom was what made the three panels overlap.
                float currentMenuHeight = blockfrag.menuHeight();
                if(BuildMenuPreference.oldMenuEnabled()){
                    //the information box grows and shrinks while the player scrolls. Reserving
                    //the largest height keeps the stack from repeatedly jumping up and down.
                    buildMenuReservation = Math.max(buildMenuReservation, currentMenuHeight);
                }else{
                    buildMenuReservation = 0f;
                }
                float pad = 4f + buildMenuReservation + 8f;

                //only rebuild when the set of panels changes. The classic build menu can grow
                //when its information box appears; clearing the stack at that point made the
                //command tablet disappear and flash again while the player was scrolling.
                if(firstLayout || modeChanged){
                    wasTest[0] = test;
                    t.clearChildren();
                    rebuildHudStack(t);
                }

                //the expanded tablet is sized from the real space occupied by the panels below
                //it. This keeps the menus separate on both desktop and scaled Android ui.
                float scale = Math.max(0.01f, Scl.scl(1f));
                float toolsHeight = 0f;
                if(testFeaturesPanel != null){
                    toolsHeight = Math.max(testFeaturesPanel.getHeight(), testFeaturesPanel.getPrefHeight()) / scale;
                    //include the panel's own 8-unit margin on both sides
                    toolsHeight += 16f;
                }
                float sceneHeight = Core.scene.getHeight() / scale;
                //12 units above the build menu, 6 between the panels and 8 at the screen top
                float availableHeight = sceneHeight - buildMenuReservation - toolsHeight - 26f;
                unitCommandTablet.setAvailableHeight(availableHeight);

                if(firstLayout || Math.abs(pad - lastPad[0]) > 0.5f){
                    lastPad[0] = pad;
                    t.marginBottom(pad);
                    //the margin changed, so the parent hierarchy needs a new layout. Keeping
                    //the children intact makes the adjustment smooth instead of a rebuild.
                    t.invalidateHierarchy();
                }
            });
        });

        buildEnemyBuildPlacer(parent);
    }

    /** Rebuilds the right-hand hud stack: the command tablet on top, the unit panel below it. */
    private void rebuildHudStack(Table stack){
        Table list = new Table();
        list.bottom().right();

        list.add(unitCommandTablet).right().row();

        if(TestNewFeaturesMode.active()){
            testFeaturesContentSignature = Long.MIN_VALUE;
            Table panel = new Table(Styles.black6);
            panel.margin(8f).top().left();
            testFeaturesPanel = panel;

            //Android's scaled font needs more horizontal room than the old 200 units: at 200 the
            //enemy-section labels ran off the right edge of the screen
            float width = mobile ? 280f : 248f;

            //header: title + collapse/expand toggle (always visible)
            Table header = new Table();
            header.left();
            header.add("@mode.testfeatures.panel").style(Styles.outlineLabel).padBottom(5f);
            header.add().growX();
            header.button(testFeaturesPanelExpanded ? Icon.upOpen : Icon.downOpen, Styles.squareTogglei, () -> {
                testFeaturesPanelExpanded = !testFeaturesPanelExpanded;
                //the content lives in its own table that is rebuilt when this flag changes
                testFeaturesContentSignature = Long.MIN_VALUE;
            }).update(i -> i.getStyle().imageUp = testFeaturesPanelExpanded ? Icon.upOpen : Icon.downOpen)
                .size(mobile ? 48f : 40f).padLeft(6f).padTop(2f);
            panel.add(header).growX().left().padBottom(5f).row();

            //the collapsible content: rebuilt only when the expanded flag flips, so the panel
            //does not flicker every frame
            Table content = new Table();
            content.left();
            panel.add(content).growX().left().row();
            content.update(() -> {
                //drives the optional profiling battle; a no-op unless one was started by a flag
                TestEnginePhase.update();
                if(testFeaturesContentSignature != (testFeaturesPanelExpanded ? 1L : 0L)){
                    testFeaturesContentSignature = testFeaturesPanelExpanded ? 1L : 0L;
                    rebuildTestFeaturesContent(content, width);
                }
            });

            list.add(panel).right().padTop(6f);
        }else{
            testFeaturesPanel = null;
        }

        stack.add(list).bottom().right();
    }

    /**
     * Fills the collapsible part of the test-tools panel.
     *
     * <p>The large unit and structure grids are not embedded in the HUD any more. Both open the
     * same modal catalogue, so the touch controls never have to compete with a scrolling grid.
     * What remains here are actions: open a catalogue tab, configure modules and control the
     * enemy-structure tool.</p>
     */
    private void rebuildTestFeaturesContent(Table content, float width){
        content.clearChildren();
        content.left();

        if(!testFeaturesPanelExpanded) return;

        content.button("@mode.testfeatures.catalog.units", Icon.units, () -> openCatalog(true))
            .width(width).height(40f).left().row();
        content.button("@mode.testfeatures.catalog.buildings", Icon.distribution, () -> openCatalog(false, true))
            .width(width).height(40f).left()
            .update(b -> b.setChecked(TestNewFeaturesMode.lastPlayerBlock != null)).row();

        Table modules = new Table();
        modules.left();
        content.add(modules).width(width).left().row();
        modules.button("@mode.testfeatures.setup.button", Icon.settings, TestNewFeaturesMode::openSetup)
            .width(width).height(34f).padTop(4f).left()
            .visible(() -> !TestNewFeaturesMode.wavesStarted).row();
        modules.add("@mode.testfeatures.modules").color(Pal.accent).left().padTop(6f).row();
        modules.label(() -> {
            Unit unit = selectedUnit();
            return unit == null ? Core.bundle.get("mode.testfeatures.modules.none")
                : Core.bundle.format("mode.testfeatures.modules.target", unit.type.localizedName);
        }).color(Color.lightGray).width(width).wrap().left().row();
        modules.button("@mode.testfeatures.modules.open", () -> openTurretDialog(selectedUnit()))
            .width(width).height(34f).padTop(4f).left()
            .disabled(b -> {
                Unit unit = selectedUnit();
                return unit == null || unit.type.turretSlots.isEmpty();
            });

        buildEnemyBuildings(content);
    }

    /**
     * Arm / disarm section of the test-mode panel: enemy team, block to drop, remove mode.
     *
     * <p>It is rebuilt only when the armed block, team or mode changes (same trick as the turret
     * bar), so the labels and toggles stay in sync without flickering every frame.</p>
     */
    private void buildEnemyBuildings(Table panel){
        float width = mobile ? 280f : 248f;

        Table section = new Table();
        section.left();
        //own framed box: the enemy section reads as a separate "quadrado" instead of more
        //buttons glued to the module actions above it
        section.background(Tex.pane);
        section.margin(8f);
        //the section is stacked below the module actions; the large catalogues are in the
        //shared modal dialog, so no grid can drift over another panel here
        panel.add(section).width(width).left().top().padTop(8f).row();
        section.update(() -> {
            long signature = enemyToolSignature();
            if(signature != enemySignature){
                enemySignature = signature;
                rebuildEnemyBuildings(section, width);
            }
        });
        //fill right away: the update above only catches later changes, and the stored
        //signature already matches the current tool state - without this, a section rebuilt
        //while the panel reopens stayed empty until the mouse wheel changed the signature
        rebuildEnemyBuildings(section, width);
    }

    private long enemyToolSignature(){
        //block ids, team ids, the remove flag and the rotation are the only things that change the section
        long block = TestNewFeaturesMode.enemyBlock == null ? -1 : TestNewFeaturesMode.enemyBlock.id;
        return block * 31L + (TestNewFeaturesMode.enemyRemoving ? 7 : 0) + (TestNewFeaturesMode.enemyTeam == null ? 0 : TestNewFeaturesMode.enemyTeam.id) + control.input.rotation * 3L;
    }

    private void rebuildEnemyBuildings(Table section, float width){
        //the section has its own 8-unit margin on each side, so its children must be a little
        //narrower than the section itself or the last words get clipped on Android
        float inner = width - 16f;
        section.clearChildren();
        section.left();

        section.add("@mode.testfeatures.buildings").color(Pal.accent).left().padTop(6f).padBottom(4f).row();

        //team row: four toggles fit on desktop, but on a scaled Android UI their labels
        //overlap into one unreadable line - give them two rows of two there
        section.table(teams -> {
            teams.left();
            teams.defaults().growX().padRight(2f).padBottom(2f).height(24f);
            Seq<Team> teamsList = TestNewFeaturesMode.enemyTeams();
            for(int i = 0; i < teamsList.size; i++){
                Team team = teamsList.get(i);
                teams.button(team.localized(), Styles.flatToggleMenut, () -> TestNewFeaturesMode.enemyTeam = team)
                    .update(b -> b.setChecked(TestNewFeaturesMode.enemyTeam == team)).get();
                if(mobile && i % 2 == 1) teams.row();
            }
        }).width(inner).left().row();

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
        }).color(Color.lightGray).width(inner).wrap().left().padBottom(4f).row();

        //the block button always opens the shared structures tab, so an armed block can be
        //changed directly. The small cancel button disarms it without opening the catalogue.
        section.table(row -> {
            row.button(b -> {
                b.left();
                b.image().size(mobile ? 24f : 28f).padRight(6f).update(i -> {
                    Block block = TestNewFeaturesMode.enemyBlock;
                    if(block == null){
                        i.setDrawable(Icon.add);
                    }else if(block.uiIcon != null && block.uiIcon.found()){
                        i.setDrawable(new TextureRegionDrawable(block.uiIcon));
                    }else{
                        i.setDrawable(Icon.distribution);
                    }
                    i.setScaling(Scaling.fit);
                });
                b.label(() -> TestNewFeaturesMode.enemyBlock == null
                    ? Core.bundle.get("mode.testfeatures.buildings.select")
                    : TestNewFeaturesMode.enemyBlock.localizedName).growX().wrap();
            }, Styles.defaultt, () -> openCatalog(false)).growX().height(mobile ? 36f : 40f).left();
            row.button(Icon.cancel, Styles.cleari, TestNewFeaturesMode::disarmStructureTools)
                .size(mobile ? 36f : 40f).height(mobile ? 36f : 40f).left().padLeft(2f);
        }).width(inner).left().padBottom(2f).row();

        section.button("@mode.testfeatures.buildings.remove", Icon.cancel, Styles.cleart, () -> {
            TestNewFeaturesMode.enemyRemoving = !TestNewFeaturesMode.enemyRemoving;
            //the remove mode is its own tool: it cannot share the cursor with a block
            TestNewFeaturesMode.enemyBlock = null;
            TestNewFeaturesMode.playerBlock = null;
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
        placer.update(() -> placer.touchable =
            TestNewFeaturesMode.enemyToolArmed() || TestNewFeaturesMode.playerToolArmed()
            ? Touchable.enabled : Touchable.disabled);
        placer.clicked(this::placeToolAtCursor);
        //right click disarms the active tool: it also blocks aiming, which is how the normal
        //build menu is dismissed, so the player can always get back to shooting in one click
        placer.addListener(new ClickListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(button != KeyCode.mouseRight) return false;

                TestNewFeaturesMode.disarmStructureTools();
                event.stop();
                return true;
            }
        });

        parent.addChild(placer);
        placer.setFillParent(true);
        placer.toBack();

        //the plan preview uses the same layer the regular build plans are drawn on
        Events.run(Trigger.drawOver, () -> Draw.draw(Layer.plans, this::drawEnemyPlan));

        //Fixed-camera move: show a crosshair at the pending destination.
        Events.run(Trigger.drawOver, () -> {
            if(!orbital.input.FixedCameraControl.awaitingDestination()) return;
            Draw.color(Pal.accent);
            Lines.stroke(2f);
            float mx = Core.input.mouseWorldX(), my = Core.input.mouseWorldY();
            Lines.circle(mx, my, 10f);
            Lines.line(mx - 16f, my, mx + 16f, my);
            Lines.line(mx, my - 16f, mx, my + 16f);
            Draw.reset();
        });
    }

    /** Drops (or removes) the armed structure under the cursor, for the player or the enemy team. */
    private void placeToolAtCursor(){
        if(!state.isGame() || !TestNewFeaturesMode.active()) return;

        int x = World.toTile(Core.input.mouseWorldX()), y = World.toTile(Core.input.mouseWorldY());

        if(TestNewFeaturesMode.playerToolArmed()){
            Block block = TestNewFeaturesMode.playerBlock;
            if(block == null) return;
            if(TestNewFeaturesMode.queuePlayerBuilding(block, x, y, control.input.rotation, block.nextConfig())){
                block.placeSound.at(Core.input.mouseWorldX(), Core.input.mouseWorldY());
            }
            return;
        }

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

    /** Opens the shared units/structures catalogue on the requested tab. */
    private void openCatalog(boolean units){
        openCatalog(units, false);
    }

    /**
     * Opens the shared catalogue on the requested tab. On the structures tab, {@code armPlayer}
     * decides which tool a picked structure arms: the player's team or the enemy team.
     */
    private void openCatalog(boolean units, boolean armPlayer){
        if(catalogDialog == null){
            catalogDialog = new TestFeaturesCatalogDialog();
        }
        if(units){
            catalogDialog.showUnits();
        }else{
            catalogDialog.showBuildings(armPlayer);
        }
    }

    /** Draws the ghost of the armed structure at the cursor, like a regular build plan. */
    private void drawEnemyPlan(){
        if(!state.isGame()) return;

        //the player tool wins when both are somehow armed; arming one clears the other
        boolean forPlayer = TestNewFeaturesMode.playerToolArmed();
        if(!forPlayer && !TestNewFeaturesMode.enemyToolArmed()) return;

        Team team = forPlayer ? player.team() : TestNewFeaturesMode.enemyTeam;
        Block block = forPlayer ? TestNewFeaturesMode.playerBlock : TestNewFeaturesMode.enemyBlock;

        Tile tile = world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if(tile == null) return;

        int x = tile.x, y = tile.y;
        if(block != null && block.isMultiblock()){
            //preview the tile the structure would really land on: multiblocks clamp to their center
            x = Mathf.clamp(x, (block.size - 1) / 2, world.width() - block.size / 2 - 1);
            y = Mathf.clamp(y, (block.size - 1) / 2, world.height() - block.size / 2 - 1);
        }

        Lines.stroke(2f, team.color);
        Lines.rect(x * tilesize - tilesize / 2f, y * tilesize - tilesize / 2f, block == null ? tilesize : block.size * tilesize, block == null ? tilesize : block.size * tilesize);
        Draw.reset();

        if(block == null || block.uiIcon == null) return;

        enemyPlan.set(x, y, control.input.rotation, block);
        enemyPlan.config = block.nextConfig();
        enemyPlan.animScale = 1f;
        block.drawPlan(enemyPlan, emptyPlans, forPlayer
            || Build.validPlaceIgnoreUnits(block, TestNewFeaturesMode.enemyTeam, x, y, control.input.rotation, true, true));
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

    /**
     * @return the height of the bottom module bar on screen right now, or 0 when the bar is not
     * shown. The touch command buttons sit <b>above</b> the bar, so they are lifted by this
     * measured height: the corner stays with the modules, and the buttons never cover a cell.
     */
    public float moduleBarHeight(){
        if(BattleRoyaleMode.active()) return 0f;
        if(turretBarBody == null || !shown() || state.isMenu() || state.isEditor()
        || !selectedHasTurretBar()) return 0f;
        return turretBarBody.getHeight();
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

    /** Right-hand stick of the 8x8 twin-stick layout: aims and fires while held. */
    private Table buildAimStick(){
        if(!(control.input instanceof MobileInput mobile)){
            return new Table();
        }

        AnalogStick stick = new AnalogStick(mobile.aimVector, mobile::syncAim);
        stick.name = "aimstick";
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

        //module numbering: the same order the number keys use, so key 1 is the first cell.
        //One cell per group of identical modules: two Duo mounted are one module with two
        //copies, drawn once and addressed once - the selection and the aim of that cell apply
        //to every copy of it (see WeaponsComp.moduleGroups).
        int index = 0;

        for(Weapon group : unit.moduleGroups()){
            int moduleIndex = index++;
            WeaponMount mount = unit.representativeMountOf(group);
            if(mount == null) continue;
            int copies = unit.moduleCopies(group);
            modules.add(turretCell(unit, mount, moduleIndex, copies)).size(cellSize).pad(cellPad)
                .tooltip(turretTooltip(unit, moduleIndex, group, copies));
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

        //8x8: the pilot must see the whole loadout. The dorsal modules above are already
        //listed, but the unit's own guns are not dorsal modules and are left out of
        //moduleGroups(); without these cells eleven of the twenty-one aerial hulls show a
        //bar with nothing but the supply meters. They are a readout, like a wall plate:
        //no number (the number keys only address dorsal modules) and no click.
        if(BattleRoyaleMode.active()){
            //one cell per fixed mount, even when several guns share the same name: the pilot
            //must see every hardpoint, and each cell carries its own live reload bar
            for(WeaponMount mount : fixedGuns(unit)){
                modules.add(fixedGunCell(unit, mount)).size(cellSize).pad(cellPad)
                    .tooltip(turretName(mount.weapon));
            }
        }

        //Battle royale keeps the supply/module readout but hides the buttons that open the
        //module catalogue - the loadout was chosen before the fight and is fixed from now on.
        if(!BattleRoyaleMode.active()){
            //a slot is only free when no turret and no plate sits in it
            if(unit.mountedTurrets() + unit.wallMounts.length < unit.type.turretSlots.size){
                modules.add(actionCell(unit, Icon.add, Core.bundle.get("turret.loadout.add")))
                    .size(cellSize).pad(cellPad).tooltip(Core.bundle.get("turret.loadout.add"));
            }

            modules.add(actionCell(unit, Icon.hammer, Core.bundle.get("turret.loadout.title")))
                .size(cellSize).pad(cellPad).tooltip(Core.bundle.get("turret.loadout.title"));
        }

        //bars above, modules below, both left-aligned in the same column
        table.add(bars).left().row();
        table.add(modules).left().row();
    }

    /**
     * @return every fixed hardpoint of the hull, in mount order, including identical mounts.
     * These guns are not dorsal modules, so the module model leaves them out; the 8x8 HUD lists
     * them anyway so the pilot sees the whole loadout.
     */
    private static Seq<WeaponMount> fixedGuns(Unit unit){
        Seq<WeaponMount> mounts = new Seq<>(WeaponMount.class);
        for(WeaponMount mount : unit.mounts){
            if(mount == null || mount.weapon == null || mount.weapon.isTurretMount()) continue;
            mounts.add(mount);
        }
        return mounts;
    }

    /**
     * A readout cell for one fixed hardpoint: icon and its own live reload bar, no number badge
     * and no click, because the number keys and the pick-to-fire gesture only address dorsal
     * modules. Used by the 8x8 HUD so a hull without a dorsal turret still shows its weapons.
     */
    private Table fixedGunCell(Unit unit, WeaponMount mount){
        Weapon weapon = mount.weapon;
        TextureRegion region = weapon.region != null && weapon.region.found() ? weapon.region : Core.atlas.find(weapon.name);
        if(region == null || !region.found()) region = unit.type.uiIcon;

        Table cell = new Table();
        cell.background(Styles.black3);

        Stack stack = new Stack();
        BorderImage icon = new BorderImage(region, 2f);
        icon.setScaling(Scaling.fit);
        icon.border(Pal.gray);
        stack.add(icon);

        cell.add(stack).size(44f).pad(2f).row();

        Bar reload = new Bar(() -> "", () -> Pal.gray, () -> reloadFraction(mount));
        cell.add(reload).height(4f).growX().pad(0f, 2f, 2f, 2f).row();
        return cell;
    }

    /**
     * Acts on one module of the bottom bar, with a toast naming it: this is the touch
     * equivalent of the module number keys. A weapon module becomes the only one to fire; a
     * support module is switched on or off, keeping its effect installed. The loadout dialog
     * keeps the mount itself untouched.
     */
    private void selectModule(Unit unit, int index){
        if(unit == null) return;
        GroupedWeaponMount group = unit.groupAt(index);
        if(group != null){
            openGroupDialog(group);
            return;
        }
        if(control.input != null) control.input.selectModule(unit, index);
    }

    /** Chooses which tiers of a combined weapon module fire together. */
    private void openGroupDialog(GroupedWeaponMount group){
        new Dialog(group.group.name){{
            closeOnBack();
            cont.add("@weapon.module.tiers.hint").wrap().growX().row();
            for(int tier = 1; tier <= 5; tier++){
                if(group.group.tiers[tier - 1] == null) continue;
                int level = tier;
                cont.check(Core.bundle.format("weapon.module.tier", tier), group.active[tier - 1], on -> {
                    if(on || group.activeCount() > 1) group.active[level - 1] = on;
                }).left().row();
            }
            buttons.button("@back", Icon.left, this::hide).size(140f, 48f);
        }}.show();
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
    private String turretTooltip(Unit unit, int index, Weapon weapon, int copies){
        boolean removable = weapon.removable;
        String state = removable ? Core.bundle.get("turret.hud.removable") : Core.bundle.get("turret.hud.native");
        //the pilot's pick is the state worth reading: every other module stays silent
        String power = Core.bundle.get(moduleSelected(unit, index) ? "module.hud.selected"
            : (moduleActive(unit, index) ? "support.hud.active" : "module.hud.silent"));
        String name = turretName(weapon);
        //a group is one module with several copies: the pilot needs to see how many are behind
        //this cell, because every action on it (select, aim) applies to all of them
        if(copies > 1) name += "  [lightgray]" + Core.bundle.format("weapon.module.count", copies);
        return name
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
     * The red X of an equipped module: takes it off the hull and leaves it on the sector floor.
     * The touch is stopped so the cell's own click (which selects/toggles the module) never fires.
     */
    private static ImageButton dropButton(Runnable action){
        //own style instance: mutating Styles.clearNonei would turn every other clear icon red
        ImageButtonStyle style = new ImageButtonStyle();
        style.up = Styles.none;
        style.down = Styles.flatDown;
        style.over = Styles.flatOver;
        style.imageUpColor = Pal.remove;
        ImageButton button = new ImageButton(Icon.cancel, style);
        button.resizeImage(16f);
        button.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode code){
                event.stop();
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode code){
                event.stop();
                action.run();
            }
        });
        return button;
    }

    /** Overlay that pins the red X to the top-right corner of a module cell. */
    private static void addDropButton(Stack stack, Runnable action){
        Table corner = new Table();
        corner.top().right();
        corner.add(dropButton(action)).size(18f).pad(1f);
        stack.add(corner);
    }

    /**
     * A weapon cell of the bottom bar: icon with the module number, reload and heat bars, and
     * the pilot's selection. Clicking picks the module: from then on it is the only one that
     * fires, every other module of the hull stays mounted and silent. Clicking the same cell
     * again lets every module fire together.
     *
     * <p>One cell is a whole <b>group</b> of identical modules ({@link WeaponsComp#moduleGroups}),
     * not one mount: the bars shown are the representative's, and {@code copies} says how many
     * sit behind the cell - picking it arms every copy, and the aim mode of one goes to all.</p>
     */
    private Table turretCell(Unit unit, WeaponMount mount, int index, int copies){
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

        //the count of identical copies behind this cell: the number key occupies the top-left,
        //so this sits at the bottom-left, small and quiet
        if(copies > 1){
            Table copiesBadge = new Table();
            copiesBadge.bottom().left();
            copiesBadge.touchable = Touchable.disabled;
            Label label = copiesBadge.label(() -> Core.bundle.format("weapon.module.count", copies))
                .style(Styles.outlineLabel).fontScale(mobile ? 0.6f : 0.7f).get();
            label.setColor(Pal.lightTrailer);
            copiesBadge.pack();
            stack.add(copiesBadge);
        }

        if(!removable){
            Table lock = new Table();
            lock.top().right();
            lock.image(Icon.lockSmall).size(13f).pad(1f).get().setColor(Pal.gray);
            stack.add(lock);
        }else{
            Weapon toDrop = weapon;
            addDropButton(stack, () -> BattleRoyaleLoot.dropWeapon(unit, toDrop));
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
        return moduleCell(unit, mount.block, mount, () -> mount.active, () -> selectModule(unit, index),
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

        Stack stack = new Stack();
        BorderImage icon = new BorderImage(region, 3f);
        icon.setScaling(Scaling.fit);
        stack.add(icon);
        WallMount toDrop = mount;
        addDropButton(stack, () -> BattleRoyaleLoot.dropWall(unit, toDrop));
        cell.add(stack).size(iconSize).pad(2f).row();

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
    private Table moduleCell(Unit unit, Block block, SupportMount mount, Boolp active, Runnable toggle, Prov<String> tooltip, int index){
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

        SupportMount toDrop = mount;
        addDropButton(stack, () -> BattleRoyaleLoot.dropSupport(unit, toDrop));

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

    /** Keeps a full-screen fill table's corner in sync with the active game mode. */
    private static void alignCorner(Table table, int align){
        if(table.getAlign() != align){
            table.align(align);
            table.invalidateHierarchy();
        }
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
        enemyuf = new IntFormat("wave.enemyunit"),
        enemyusf = new IntFormat("wave.enemyunits"),

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
                //the 8x8 box stays visible for life/shield readout, but tapping it must not
                //detach the pilot from its hull (there is no core to respawn into)
                if(!player.dead() && mobile && !BattleRoyaleMode.active()){
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

            //8x8 mobile: the menu lives inside the status box, immediately after the unit
            //icon and its health bar, so there is no gap between them. The HUD is built once
            //at startup, before battle royale is active, so the button must always be created
            //and a collapser - not a build-time check - decides when it takes space.
            t.collapser(menu -> {
                menu.button(Icon.menu, Styles.clearNonei, 30f, ui.paused::show)
                .size(40f).padLeft(0f).name("main-menu");
            }, () -> mobile && BattleRoyaleMode.active());

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
                //Battle royale has no cores: show how many enemy units are still fighting.
                if(BattleRoyaleMode.active()){
                    int sum = 0;
                    for(Team team : BattleRoyaleMode.teams()){
                        if(team == player.team()) continue;
                        Teams.TeamData data = team.data();
                        sum += data == null || data.units == null ? 0 : data.units.size;
                    }
                    sum = Math.max(sum, 1);
                    builder.append(sum > 1 ? enemyusf.get(sum) : enemyuf.get(sum));
                    return builder;
                }

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
