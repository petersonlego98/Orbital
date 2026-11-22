package orbital;

import arc.*;
import arc.assets.*;
import arc.assets.loaders.*;
import arc.audio.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import orbital.ai.*;
import orbital.audio.*;
import orbital.content.*;
import orbital.core.*;
import orbital.engine.validation.EngineValidation;
import orbital.ctype.*;
import orbital.game.EventType.*;
import orbital.game.Saves.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.io.*;
import orbital.maps.*;
import orbital.mod.*;
import orbital.net.*;
import orbital.ui.*;

import static arc.Core.*;
import static orbital.Vars.*;

public abstract class ClientLauncher extends ApplicationCore implements Platform{
    //Asset work is drained in a per-call time slice on the render thread. A larger slice means
    //fewer, longer steps: the bar advances in ~8 big steps instead of stalling across ~100 frames,
    //which finishes sooner on slow disks/iGPUs and avoids the "stuck near 100%" feel.
    private static final int loadingFPS = 8;

    private long nextFrame;
    private long beginTime;
    private long lastTargetFps = -1;
    private boolean finished = false;
    /** True for Intel iGPUs and software renderers (Mesa llvmpipe, SwiftShader). */
    private boolean weakGPU = false;
    private LoadRenderer loader;
    /** Load timeline bookkeeping, used to report which asset is stalling the bar. */
    private String lastLoadingAsset = "";
    private long loadingAssetStart, lastLoadProgressLog;

    /** Default frame cap. Android overrides this with the device's screen refresh rate. */
    protected int defaultFpsCap(){
        return 120;
    }

    @Override
    public void setup(){
        String dataDir = System.getProperty("orbital.data.dir", OS.env("ORBITAL_DATA_DIR"));
        if(dataDir != null){
            Core.settings.setDataDirectory(files.absolute(dataDir));
        }

        checkLaunch();
        loadLogger();

        loader = new LoadRenderer();
        Events.fire(new ClientCreateEvent());

        orbital.diagnostics.CpuProfiler.maybeStart();
        loadFileLogger();
        platform = this;
        maxTextureSize = Gl.getInt(Gl.maxTextureSize);
        beginTime = Time.millis();
        loadingAssetStart = beginTime;

        //debug GL information
        Log.info("[GL] Version: @", graphics.getGLVersion());
        Log.info("[GL] Max texture size: @", maxTextureSize);
        Log.info("[GL] Using @ API.", gl30 != null ? "OpenGL 3" : "OpenGL 2");

        //The live vendor string is authoritative here: the GL context already exists, so the
        //renderer is known for this very launch. IntelGpuCheck keeps a file on disk because the
        //desktop launcher has to choose the GL version *before* the window exists - reading that
        //cached value here left the Intel mitigations off for one launch, and that launch is the
        //one that uploads the 4096px atlas, merges the font atlas and turns bloom on.
        String glVendor = graphics.getGLVersion().vendorString;
        IntelGpuCheck.init(glVendor);

        boolean intelVendor = glVendor != null && glVendor.toLowerCase(java.util.Locale.ROOT).contains("intel");
        boolean isIntel = intelVendor || IntelGpuCheck.wasIntel();
        //Software renderers (Mesa llvmpipe, SwiftShader) run on the CPU: no GPU to corrupt, but a
        //low frame budget. Treat them like a weak GPU for bloom/cap defaults.
        String glString = graphics.getGLVersion().toString();
        boolean softwareGL = glString.contains("llvmpipe") || glString.contains("SwiftShader")
            || glString.contains("softpipe") || glString.contains("Mesa");
        weakGPU = isIntel || softwareGL;

        if(isIntel && !graphics.isGL30Available()) Log.warn("[GL] Intel GPU detected on previous launch. Due to memory corruption issues, OpenGL 3 support has been disabled for Intel GPUs. See issue #11041.");
        if(softwareGL) Log.info("[GL] Software renderer detected (@): CPU rasterization active", glString);
        Log.info("[GL] weak GPU profile: @ (intel: @, software: @)", weakGPU, intelVendor, softwareGL);

        if(gl30 == null && !isIntel) Log.warn("[GL] Your device or video drivers do not support OpenGL 3. This will cause performance issues.");

        //Apply graphics preferences, but only for gameplay. During loading the frame limiter and
        //vsync must stay OFF: assets.update() processes queued work in time slices, so capping the
        //loading loop to 60 fps makes the bar crawl near 100% on slow integrated GPUs. vsync and
        //the cap are switched on in applyGameplayFrameSettings() once loading finishes.
        settings.defaults("vsync", true);
        settings.defaults("fpscap", defaultFpsCap());
        //Bloom is enabled by default on every profile again. It was previously disabled for
        //weak/software GPUs to avoid a ~1s stall at the end of the load, but the visual effect is
        //restored per request; players can still turn it off under Settings > Graphics.
        settings.defaults("bloom", true);
        //Explicitly keep vsync off while loading so the frame loop can drain assets at full speed.
        graphics.setVSync(false);
        Log.info("[GL] loading without frame limit; vsync/cap apply after load (weak/software GPU: @)", weakGPU);

        if(NvGpuInfo.hasMemoryInfo()) Log.info("[GL] Total available VRAM: @", Strings.formatByteCount(NvGpuInfo.getMaxMemoryKB() * 1000L));

        if(maxTextureSize < 4096) Log.warn("[GL] Your maximum texture size is below the recommended minimum of 4096. This will cause severe performance issues.");

        Log.info("[JAVA] Version: @", OS.javaVersion);

        if(Core.app.isAndroid()) Log.info("[ANDROID] API level: @", Core.app.getVersion());
        if(Core.app.isIOS()) Log.info("[iOS] OS version: @", Core.app.getVersion());

        long ram = Runtime.getRuntime().maxMemory();

        if(!OS.isIos) Log.info("[RAM] Available: @", Strings.formatByteCount(ram));

        Time.setDeltaProvider(() -> {
            float result = Core.graphics.getDeltaTime() * 60f;
            return (Float.isNaN(result) || Float.isInfinite(result)) ? 1f : Mathf.clamp(result, 0.0001f, maxDeltaClient);
        });

        UI.loadColors();
        batch = new SpriteBatch();
        assets = new AssetManager();
        assets.setLoader(Texture.class, "." + mapExtension, new MapPreviewLoader());

        tree = new FileTree();
        assets.setLoader(Sound.class, new SoundLoader(tree){
            @Override
            public void loadAsync(AssetManager manager, String fileName, Fi file, SoundParameter parameter){

            }

            @Override
            public Sound loadSync(AssetManager manager, String fileName, Fi file, SoundParameter parameter){
                if(parameter != null && parameter.sound != null){
                    parameter.sound.loadLazy(file);

                    return parameter.sound;
                }else{
                    Sound sound = new Sound();
                    sound.loadLazy(file);
                    return sound;
                }
            }
        });
        assets.setLoader(Music.class, new MusicLoader(tree){
            @Override
            public void loadAsync(AssetManager manager, String fileName, Fi file, MusicParameter parameter){}

            @Override
            public Music loadSync(AssetManager manager, String fileName, Fi file, MusicParameter parameter){
                Music music = parameter != null && parameter.music != null ? parameter.music : new Music();

                //The track is only registered here. Decoding it now would spend ~30 CPU-seconds
                //on 25 MB of OGG while the game is still loading, freezing the loading screen and
                //stealing cores from the asset loader. MusicRegistry decodes it in the background
                //when the track is first needed.
                MusicRegistry.register(music, file);
                return music;
            }
        });

        assets.load("sprites/error.png", Texture.class);

        //ORBITAL key art for the themed menu and loading screen. These are loose textures outside
        //sprites/ on purpose: the packed atlas must stay byte-identical because
        //Fonts.mergeFontAtlas() reuses the texture page that holds the "logo" region.
        assets.load("ui/orbital-poster.png", Texture.class);
        assets.load("ui/orbital-wordmark.png", Texture.class);
        assets.load("ui/orbital-mark.png", Texture.class);

        atlas = TextureAtlas.blankAtlas();
        Vars.net = new Net(platform.getNet());
        MapPreviewLoader.setupLoaders();
        mods = new Mods();
        schematics = new Schematics();

        Fonts.loadSystemCursors();

        assets.load(new Vars());

        Fonts.loadDefaultFont();

        //Load the smaller 2048px atlas on Intel integrated GPUs (and when the driver cannot handle
        //4096px). The full atlas uploads a 4096x4096 page (~64 MB) which stalls the loading bar for
        //seconds on an HD 4600 in OpenGL 2; the fallback pages are a quarter of that each.
        boolean smallAtlas = maxTextureSize < 4096 || weakGPU;
        assets.load(new AssetDescriptor<>(smallAtlas ? "sprites/fallback/sprites.aatls" : "sprites/sprites.aatls", TextureAtlas.class)).loaded = t -> atlas = t;
        assets.loadRun("maps", Map.class, () -> maps.loadPreviews());

        Musics.load();
        Sounds.load();

        assets.loadRun("contentcreate", Content.class, () -> {
            content.createBaseContent();
            content.loadColors();
        }, () -> {
            mods.loadScripts();
            content.createModContent();
        });

        assets.load(mods);
        //Merging the UI/font atlas reads a 4096x4096 texture page back from the GPU for every
        //region and re-uploads it. On an Intel iGPU with OpenGL 2 that stalls the loading bar near
        //100% for seconds. It is only a draw-call optimization, so skip it there.
        boolean mergeAtlas = !weakGPU;
        assets.loadRun("mergeUI", PixmapPacker.class, () -> {}, () -> {
            if(mergeAtlas) Fonts.mergeFontAtlas(atlas);
        });

        add(logic = new Logic());
        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());
        add(netServer = new NetServer());
        add(netClient = new NetClient());

        assets.load(schematics);

        assets.loadRun("contentinit", ContentLoader.class, () -> content.init(), () -> {
            content.load();
            mods.loadModPatches();
        });
        assets.loadRun("baseparts", BaseRegistry.class, () -> {}, () -> bases.load());

        Core.assets.load("sprites/schematic-background.png", Texture.class).loaded = t -> t.setWrap(TextureWrap.repeat);
    }

    @Override
    public void add(ApplicationListener module){
        super.add(module);

        //autoload modules when necessary
        if(module instanceof Loadable l){
            assets.load(l);
        }
    }

    @Override
    public void resize(int width, int height){
        if(assets == null) return;

        if(!finished){
            Draw.proj().setOrtho(0, 0, width, height);
        }else{
            super.resize(width, height);
        }
    }

    @Override
    public void update(){
        PerfCounter.update.begin();

        //Never throttle the loading loop: assets.update() drains queued work in time slices, so a
        //60 fps cap here makes the progress bar crawl right before it completes on slow GPUs.
        //The limiter and vsync are enabled in applyGameplayFrameSettings() once loading finishes.
        int targetfps = !finished || ios ? 0 : Core.settings.getInt("fpscap", 120);
        //An integrated Intel GPU cannot sustain a high cap, and a cap it can never reach means the
        //limiter below never sleeps - the GPU stays pinned at 100% for nothing.
        if(!ios && weakGPU && targetfps > 60){
            targetfps = 60;
        }
        boolean changed = lastTargetFps != targetfps && lastTargetFps != -1;
        boolean limitFps = targetfps > 0 && targetfps <= 240;

        lastTargetFps = targetfps;

        if(limitFps && !changed){
            long interval = (1000 * 1000000) / targetfps;
            //Do not let debt accumulate when a frame takes longer than the interval: otherwise
            //nextFrame falls permanently behind and the sleep branch below is never taken.
            long now = Time.nanos();
            nextFrame = nextFrame + interval < now ? now + interval : nextFrame + interval;
        }else{
            nextFrame = Time.nanos();
        }

        if(!finished){
            if(loader != null){
                loader.draw();
            }
            logLoadingProgress();
            if(assets.update(1000 / loadingFPS)){
                loader.dispose();
                loader = null;
                SoundPriority.init();
                for(ApplicationListener listener : modules){
                    listener.init();
                }
                mods.eachClass(Mod::init);
                finished = true;
                applyGameplayFrameSettings();
                Events.fire(new ClientLoadEvent());
                Log.info("Total time to load: @ms", Time.timeSinceMillis(beginTime));
                clientLoaded = true;
                super.resize(graphics.getWidth(), graphics.getHeight());
                app.post(() -> app.post(() -> app.post(() -> app.post(() -> {
                    super.resize(graphics.getWidth(), graphics.getHeight());

                    //Diagnostic flag: auto-enter a mode after load so headless profiling can measure
                    //a real game instead of the menu. -Dorbital.autostart=test|battle
                    String autostart = System.getProperty("orbital.autostart");
                    if(autostart != null){
                        Log.info("[cpuprofile] auto-entering mode: @", autostart);
                        if("battle".equals(autostart)){
                            orbital.game.BattleRoyaleMode.start();
                        }else{
                            orbital.game.TestNewFeaturesMode.start();
                        }

                        //profiling knobs: lower the engagement threshold so a small stress battle
                        //still exercises the parallel kernel on a weak machine
                        String threshold = System.getProperty("orbital.testthreshold");
                        if(threshold != null){
                            int units = Integer.parseInt(threshold);
                            orbital.engine.units.UnitMovementTick.overrideThreshold(units);
                            orbital.engine.combat.ProjectileTick.overrideThreshold(units);
                        }
                        String stress = System.getProperty("orbital.stressbattle");
                        if(stress != null){
                            //the parallel phases are opt-in; turn them on for the A/B measurement
                            Core.settings.put(orbital.engine.units.UnitMovementTick.key, true);
                            Core.settings.put(orbital.engine.combat.ProjectileTick.key, true);
                            final int units = Integer.parseInt(stress);
                            //playMap() switches world asynchronously; give it a couple of seconds
                            arc.util.Time.run(2f, () -> {
                                boolean started = orbital.game.TestEnginePhase.start(units);
                                Log.info("[cpuprofile] stress battle requested=@ units, started=@", units, started);
                            });
                        }
                    }

                    //On desktop, open the star map focused on the last played sector right after
                    //loading, leaving the player ready to enter it. PlanetDialog.show() switches to
                    //the system view when there is no current sector, so lookAt() must run after it.
                    if(!mobile && ui != null && ui.planet != null){
                        try{
                            //Capture the target planet/sector BEFORE show(): show() calls
                            //viewSystem() when there is no current sector, replacing state.planet
                            //with the sun, which would make getLastSector() point at the wrong body.
                            Planet target = (state != null && state.rules.sector != null)
                                ? state.rules.sector.planet : Planets.qatia;
                            Sector sector = target.getLastSector();
                            ui.planet.show();
                            if(sector != null){
                                //lookAt restores planet/systemView, then select rebuilds the panel.
                                ui.planet.lookAt(sector);
                                ui.planet.selectSector(sector);
                                //The dialog animates uiAlpha/zoom over the next frames; defer the
                                //focus once more so it is not overwritten by that animation.
                                Core.app.post(() -> {
                                    ui.planet.lookAt(sector);
                                    ui.planet.selectSector(sector);
                                });
                            }
                        }catch(Throwable e){
                            Log.warn("[load] could not focus the star map: @", e.getMessage());
                        }
                    }

                    //mark initialization as complete
                    finishLaunch();
                }))));
            }
        }else{
            asyncCore.begin();

            super.update();

            asyncCore.end();
        }

        if(limitFps){
            long current = Time.nanos();
            if(nextFrame > current){
                long toSleep = nextFrame - current;
                Threads.sleep(toSleep / 1000000, (int)(toSleep % 1000000));
            }
        }

        PerfCounter.update.end();

        long rawUpdate = PerfCounter.update.latestValueNs();
        for(var other : PerfCounter.displayedCounters){
            if(other != PerfCounter.other) rawUpdate -= other.latestValueNs();
        }
        PerfCounter.other.add(rawUpdate);

        for(var counter : PerfCounter.all){
            counter.checkUpdate();
        }
    }

    /**
     * Turns on vsync and the configured frame cap for gameplay only. Deliberately not called during
     * loading: throttling the loading loop slows down assets.update() and makes the progress bar
     * stall near completion on slow integrated GPUs.
     */
    private void applyGameplayFrameSettings(){
        boolean vsync = settings.getBool("vsync");
        graphics.setVSync(vsync);
        int cap = settings.getInt("fpscap", 120);
        if(weakGPU && cap > 60) cap = 60;
        Log.info("[GL] gameplay vsync=@, fps cap=@ (weak/software GPU: @)",
            vsync, cap, weakGPU);
        //reset the limiter clock so the first gameplay frames are not throttled by stale state
        lastTargetFps = -1;
        nextFrame = Time.nanos();
    }

    /**
     * Writes the load timeline to the log: the percentage of the bar, the asset that is being
     * processed and how long that asset held the screen.
     *
     * <p>Arc's asset manager blocks the render thread while an asset is loaded (the async loader
     * is waited on with {@code Future.get()}, and the synchronous loaders run inline). While it is
     * blocked, no frame is drawn, so the screen keeps showing the last frame - a bar frozen at some
     * percentage. The log is the only place where the blocking asset is visible, so it is written
     * once per second and whenever an asset takes longer than half a second.</p>
     */
    private void logLoadingProgress(){
        if(assets == null) return;

        long now = Time.millis();
        String name = null;
        AssetDescriptor current = assets.getCurrentLoading();
        if(current != null) name = current.fileName;

        if(name != null && !name.equals(lastLoadingAsset)){
            long held = now - loadingAssetStart;
            //only report the assets that actually hold the screen for a noticeable time
            if(held >= 500){
                Log.info("[load] @ held the screen for @ms (total @ms)", lastLoadingAsset, held, now - beginTime);
            }
            lastLoadingAsset = name;
            loadingAssetStart = now;
        }

        if(now - lastLoadProgressLog >= 1000){
            lastLoadProgressLog = now;
            Log.info("[load] @% (@ms) current: @",
                Math.round(assets.getProgress() * 100f), now - beginTime, name == null ? "-" : name);
        }
    }

    @Override
    public void exit(){
        //on graceful exit, finish the launch normally.
        Vars.finishLaunch();
    }

    @Override
    public void init(){
        nextFrame = Time.nanos();
        //headless opt-in for CI and for a quick machine check: -Dorbital.engine.validate=true runs
        //the engine self-test before anything else and leaves the report in the log
        if(Boolean.getBoolean("orbital.engine.validate")){
            Log.info(EngineValidation.run().summary());
        }
        setup();
    }

    @Override
    public void resume(){
        if(finished){
            super.resume();
        }
    }

    @Override
    public void pause(){
        //when the user tabs out on mobile, the exit() event doesn't fire reliably - in that case, just assume they're about to kill the app
        //this isn't 100% reliable but it should work for most cases
        if(mobile){
            Vars.finishLaunch();
        }
        if(finished){
            super.pause();
        }
    }

    @Override
    public void fileDropped(Fi file){
        if(OS.isIos || OS.isAndroid) return;

        if(file.extEquals(saveExtension) || file.extEquals(schematicExtension)){
            handleFileImport(file);
        }
    }

    public static void runOnClientLoad(Runnable run){
        if(clientLoaded){
            run.run();
        }else{
            Events.on(ClientLoadEvent.class, e -> run.run());
        }
    }

    /** Can be called from any thread. The file must exist and not have any permission nonsense guarding it. */
    public static void handleFileImport(Fi file){
        Core.app.post(() -> {
            try{
                if(Schematics.isSchematic(file)){
                    ui.schematics.show();
                    ui.schematics.importAndShow(file);
                }else{
                    SaveMeta meta = SaveIO.getMeta(file);
                    if(!meta.isMap()){ //open save
                        if(meta.rules.sector == null){
                            //not sure if this is a great idea, but leaving the editor open would lead to catastrophic bugs
                            if(ui.editor.isShown()) ui.editor.hide();
                            if(ui.maps.isShown()) ui.maps.hide();

                            SaveSlot slot = control.saves.importSave(file);
                            ui.load.runLoadSave(slot);
                        }else{
                            ui.showErrorMessage("@save.nocampaign");
                        }
                    }else{ //open map
                        if(!ui.maps.isShown()) ui.maps.show();
                        ui.maps.tryImportMap(file, result -> ui.maps.showMap(result));
                    }
                }
            }catch(Throwable e){
                Log.err("Failed to import file", e);
                ui.showException("@save.import.invalid", e);
            }
        });
    }
}
