package orbital.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.scene.*;
import arc.scene.style.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.core.*;
import orbital.ctype.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.mod.*;
import orbital.mod.Mods.*;
import orbital.ui.*;

import java.util.*;
import java.util.regex.*;

import static orbital.Vars.*;

public class ModsDialog extends BaseDialog{
    private static final Pattern modVersionPattern = Pattern.compile("\\[[vb](\\d+)(?:\\.(\\d+))?\\]");

    protected BaseDialog currentContent;
    protected BaseDialog modDetails;

    protected float scroll = 0f;

    public ModsDialog(){
        super("@mods");
        addCloseButton();

        if(mods.list().contains(LoadedMod::failed)){
            buttons.button("@mods.restore", Icon.power, () -> {
                mods.list().each(LoadedMod::failed, m -> mods.setEnabled(m, true));
                setup();
            });
        }else{ //no room for both buttons on mobile
            buttons.button("@mods.guide", Icon.link, () -> Core.app.openURI(modGuideURL)).size(210, 64f);
        }

        if(!mobile){
            buttons.button("@mods.openfolder", Icon.link, () -> Core.app.openFolder(modDirectory.absolutePath()));
        }

        shown(this::setup);
        onResize(this::setup);

        Events.on(ResizeEvent.class, event -> {
            if(currentContent != null){
                currentContent.hide();
                currentContent = null;
            }
        });

        hidden(() -> {
            if(mods.requiresReload()){
                mods.reload();
            }
        });
    }

    void setup(){
        float h = 110f;
        float w = Math.min(Core.graphics.getWidth() / Scl.scl(1.05f) - Scl.scl(28f), 520f);

        cont.clear();
        cont.defaults().width(Math.min(Core.graphics.getWidth() / Scl.scl(1.05f), 556f)).pad(4);
        cont.add("@mod.reloadrequired").visible(mods::requiresReload).center().get().setAlignment(Align.center);
        cont.row();

        cont.table(buttons -> {
            buttons.left().defaults().growX().height(60f).uniformX();

            TextButtonStyle style = Styles.flatBordert;
            float margin = 12f;

            buttons.button("@mod.import", Icon.add, style, () -> {
                BaseDialog dialog = new BaseDialog("@mod.import");

                TextButtonStyle bstyle = Styles.flatt;

                dialog.cont.table(Tex.button, t -> {
                    t.defaults().size(300f, 70f);
                    t.margin(12f);

                    t.button("@mod.import.file", Icon.file, bstyle, () -> {
                        dialog.hide();

                        FileChooser.open("zip", "jar").submitMulti(files -> {
                            for(var file : files){
                                try{
                                    mods.importMod(file);
                                }catch(Exception e){
                                    ui.showException(e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("writable dex") ? "@error.moddex" : "", e);
                                    Log.err(e);
                                }
                            }

                            setup();
                        });
                    }).margin(12f);

                    t.row();

                });
                dialog.addCloseButton();

                dialog.show();

            }).margin(margin);
        }).width(w);

        cont.row();

        if(!mods.list().isEmpty()){
            boolean[] anyDisabled = {false};
            Table[] pane = {null};

            Cons<String> rebuild = query -> {
                var cont = pane[0];
                cont.clear();
                boolean any = false;
                for(LoadedMod mod : mods.list()){
                    if(Strings.matches(query, mod.meta.displayName)){
                        any = true;
                        if(!mod.enabled() && !anyDisabled[0] && mods.list().size > 0){
                            anyDisabled[0] = true;
                            cont.row();
                            cont.image().growX().height(4f).pad(6f).color(Pal.gray).row();
                        }

                        cont.button(t -> {
                            t.top().left();
                            t.margin(12f);

                            String stateDetails = getStateDetails(mod);
                            if(stateDetails != null){
                                t.addListener(new Tooltip(f -> f.background(Styles.black8).margin(4f).add(stateDetails).growX().width(400f).wrap()));
                            }

                            t.defaults().left().top();
                            t.table(title1 -> {
                                title1.left();

                                title1.add(new BorderImage()).size(h - 8f).padTop(-8f).padLeft(-8f).padRight(8f).with(b -> {
                                    if(mod.iconTexture != null){
                                        b.setDrawable(new TextureRegion(mod.iconTexture));
                                    }else{
                                        b.setDrawable(Tex.nomap);
                                        //if the texture gets loaded with a delay, update it
                                        b.update(() -> {
                                            if(mod.iconTexture != null){
                                                b.setDrawable(new TextureRegion(mod.iconTexture));
                                                //stop updating
                                                b.update(null);
                                            }
                                        });
                                    }
                                    b.border(Pal.accent);
                                });

                                title1.table(text -> {
                                    boolean hideDisabled = !mod.isSupported() || mod.hasUnmetDependencies() || mod.hasContentErrors();
                                    String shortDesc = mod.meta.shortDescription();

                                    text.add("[accent]" + Strings.stripColors(mod.meta.displayName) + "\n"
                                        + (mod.enabled() || hideDisabled ? (shortDesc.length() > 0 ? "[lightgray]" + shortDesc : "") : Core.bundle.get(mod.failed() ? "mod.failed" : "mod.disabled")))
                                    .ellipsis(true).top().width(300f).growX().left();

                                    text.row();

                                    String state = getStateText(mod);
                                    if(state != null){
                                        text.labelWrap(state).growX().row();
                                    }
                                }).top().growX();

                                title1.add().growX();
                            }).growX().growY().left();

                            t.table(right -> {
                                right.right();
                                right.button(mod.enabled() ? Icon.downOpen : Icon.upOpen, Styles.clearNonei, () -> {
                                    mods.setEnabled(mod, !mod.enabled());
                                    setup();
                                }).size(50f).disabled(!mod.isSupported());

                                right.button(mod.hasSteamID() ? Icon.link : Icon.trash, Styles.clearNonei, () -> {
                                    if(!mod.hasSteamID()){
                                        ui.showConfirm("@confirm", "@mod.remove.confirm", () -> {
                                            mods.removeMod(mod);
                                            setup();
                                        });
                                    }else{
                                        platform.viewListing(mod);
                                    }
                                }).size(50f);

                                if(steam && !mod.hasSteamID()){
                                    right.row();
                                    right.button(Icon.export, Styles.clearNonei, () -> {
                                        platform.publish(mod);
                                    }).size(50f);
                                }
                            }).growX().right().padRight(-8f).padTop(-8f);
                        }, Styles.grayt, () -> showMod(mod)).size(w, h).growX().pad(4f).padTop(8f).row();
                    }
                }

                if(!any){
                    cont.add("@none.found").color(Color.lightGray).pad(4);
                }
            };

            if(!mobile || Core.graphics.isPortrait()){
                cont.table(search -> {
                    search.image(Icon.zoom).padRight(8f);
                    search.field("", rebuild).growX();
                }).fillX().padBottom(4);
            }

            cont.row();
            cont.pane(table1 -> {
                pane[0] = table1.margin(10f).top();
                rebuild.get("");
            }).scrollX(false).update(s -> scroll = s.getScrollY()).get().setScrollYForce(scroll);

            cont.row();
        }else{
            cont.table(Styles.black6, t -> t.add("@mods.none")).height(80f);
        }

        cont.row();
    }

    private @Nullable String getStateText(LoadedMod item){
        if(item.isOutdated()){
            return "@mod.incompatiblemod";
        }else if(item.isBlacklisted()){
            return "@mod.blacklisted";
        }else if(!item.isSupported()){
            return "@mod.incompatiblegame";
        }else if(item.state == ModState.circularDependencies){
            return "@mod.circulardependencies";
        }else if(item.state == ModState.incompleteDependencies){
            return "@mod.incompletedependencies";
        }else if(item.hasUnmetDependencies()){
            return "@mod.unmetdependencies";
        }else if(item.hasContentErrors()){
            return "@mod.erroredcontent";
        }else if(item.meta.hidden){
            return "@mod.multiplayer.compatible";
        }
        return null;
    }

    private @Nullable String getStateDetails(LoadedMod item){
        if(item.isOutdated()){
            return "@mod.incompatiblemod.details";
        }else if(item.isBlacklisted()){
            return "@mod.blacklisted.details";
        }else if(!item.isSupported()){
            return Core.bundle.format("mod.requiresversion.details", item.meta.minGameVersion);
        }else if(item.state == ModState.circularDependencies){
            return "@mod.circulardependencies.details";
        }else if(item.state == ModState.incompleteDependencies){
            return Core.bundle.format("mod.incompletedependencies.details", item.missingDependencies.toString(", "));
        }else if(item.hasUnmetDependencies()){
            return Core.bundle.format("mod.missingdependencies.details", item.missingDependencies.toString(", "));
        }else if(item.hasContentErrors()){
            return "@mod.erroredcontent.details";
        }
        return null;
    }

    private void showMod(LoadedMod mod){
        modDetails = new BaseDialog(mod.meta.displayName);

        modDetails.addCloseButton();

        if(!mobile){
            modDetails.buttons.button("@mods.openfolder", Icon.link, () -> Core.app.openFolder(mod.file.absolutePath()));
        }

        if(mod.getRepo() != null){
            modDetails.buttons.button("@mods.github.open", Icon.link, () -> Core.app.openURI("https://github.com/" + mod.getRepo()));
        }

        modDetails.cont.pane(desc -> {
            desc.center();
            desc.defaults().padTop(10).left();

            desc.add("@editor.name").padRight(10).color(Color.gray).padTop(0);
            desc.row();
            desc.add(mod.meta.displayName).growX().wrap().padTop(2);
            desc.row();
            if(mod.meta.author != null){
                desc.add("@editor.author").padRight(10).color(Color.gray);
                desc.row();
                desc.add(mod.meta.author).growX().wrap().padTop(2);
                desc.row();
            }
            if(mod.meta.version != null){
                desc.add("@mod.version").padRight(10).color(Color.gray).top();
                desc.row();
                desc.add(mod.meta.version).growX().wrap().padTop(2);
                desc.row();
            }
            if(mod.meta.description != null){
                desc.add("@editor.description").padRight(10).color(Color.gray).top();
                desc.row();
                desc.add(mod.meta.description).growX().wrap(true).padTop(2);
                desc.row();
            }

            String state = getStateDetails(mod);

            if(state != null){
                desc.add("@mod.disabled").padTop(13f).padBottom(-6f).row();
                desc.add(state).growX().wrap().row();
            }

        }).width(Math.min(Core.graphics.getWidth() / Scl.scl(1f), 600f));

        Seq<UnlockableContent> all = Seq.with(content.getContentMap()).<Content>flatten().select(c -> c.minfo.mod == mod && c instanceof UnlockableContent u && !u.isHidden()).as();
        if(all.any()){
            modDetails.cont.row();
            modDetails.cont.button("@mods.viewcontent", Icon.book, () -> {
                BaseDialog d = new BaseDialog(mod.meta.displayName);
                d.cont.pane(cs -> {
                    int i = 0;
                    for(UnlockableContent c : all){
                        cs.button(new TextureRegionDrawable(c.uiIcon), Styles.flati, iconMed, () -> {
                            ui.content.show(c);
                        }).size(50f).with(im -> {
                            var click = im.getClickListener();
                            im.update(() -> im.getImage().color.lerp(!click.isOver() ? Color.lightGray : Color.white, 0.4f * Time.delta));

                        }).tooltip(c.localizedName);

                        if(++i % (int)Math.min(Core.graphics.getWidth() / Scl.scl(110), 14) == 0) cs.row();
                    }
                }).grow();
                d.addCloseButton();
                d.show();
                currentContent = d;
            }).size(300, 50).pad(4);
        }

        modDetails.show();
    }

    /** @return {major, minor} parsed from a leading/trailing "[v{major}]" or "[v{major}.{minor}]" tag, or null if none is found at either end (or the numbers are unparseable). */
    public static @Nullable int[] parseVersionTag(String str){
        if(str == null) return null;

        Matcher m = modVersionPattern.matcher(str);
        while(m.find()){
            if(m.start() == 0 || m.end() == str.length()){
                int major = Strings.parseInt(m.group(1));
                //any major version below 15 is likely a major-version tag like [v7] and should be ignored
                if(major == Integer.MIN_VALUE || major < 15) return null;
                int minor = m.group(2) != null ? Strings.parseInt(m.group(2)) : 0;
                if(minor == Integer.MIN_VALUE) return null;
                return new int[]{major, minor};
            }
        }
        return null;
    }

    public static @Nullable int[] parseVersion(String str){
        if(str == null || str.isEmpty()) return null;

        int dot = str.indexOf('.');
        String majorStr = dot == -1 ? str : str.substring(0, dot);
        String minorStr = dot == -1 ? null : str.substring(dot + 1);

        if(majorStr.isEmpty() || (dot != -1 && (minorStr.isEmpty() || str.indexOf('.', dot + 1) != -1))) return null;

        int major = Strings.parseInt(majorStr);
        if(major == Integer.MIN_VALUE) return null;
        int minor = minorStr != null ? Strings.parseInt(minorStr) : 0;
        if(minor == Integer.MIN_VALUE) return null;

        return new int[]{major, minor};
    }

    /** @return whether the specified release name and its tags match the current game build (e.g. "Bingus Mod [v160]" should only match build 160) */
    public static boolean matchesGameVersion(String releaseTitle){
        return matchesGameVersion(parseVersionTag(releaseTitle));
    }

    public static boolean matchesGameVersion(@Nullable int[] tag){
        //version not specified, matches anything.
        if(tag == null) return true;
        //must match exactly; custom builds (-1) do not match.
        //for revisions, either match if the revision is unspecified (v160 -> matches ALL 160.x builds), OR, if it is specified, match the exact version (the mod author must have had a good reason for it)
        return Version.build == tag[0] && (tag[1] == 0 || Version.revision == tag[1]);
    }
}
