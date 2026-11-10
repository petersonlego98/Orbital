package orbital.desktop.vulkan;

import arc.*;
import arc.audio.Audio;
import arc.backend.sdl.*;
import arc.backend.sdl.jni.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.gl.*;
import arc.struct.*;
import arc.util.*;

import orbital.core.*;
import orbital.desktop.vulkan.gl.*;

import java.util.*;

/**
 * Application backend that runs Arc over Vulkan instead of OpenGL.
 *
 * <p>This is the stage that actually lets the game render through {@link VulkanGL20}. The
 * difficulty is ordering: Arc creates its GL resources (shaders, textures, fonts, the atlas) during
 * {@code listener.init()}, which runs <i>after</i> the application constructor has installed every
 * {@code Core} field but <i>before</i> the first frame. So this class installs {@link VulkanGL20}
 * into {@code Core.gl20}/{@code Core.gl}/{@code Core.gl30} exactly in that window, and everything
 * Arc builds afterwards is built on Vulkan.</p>
 *
 * <p>It reuses {@link SdlInput} and {@link SdlFiles}, which do not touch OpenGL; only the graphics
 * and the frame presentation are replaced - the window is created without {@code SDL_WINDOW_OPENGL}
 * and each frame ends with a Vulkan present instead of a context swap.</p>
 */
public final class VulkanApplication implements Application{
    private final Seq<ApplicationListener> listeners = new Seq<>();
    private final TaskQueue runnables = new TaskQueue();
    private final int[] inputs = new int[64];
    private final ApplicationListener listener;
    private final String title;
    private final boolean audio;
    private final SdlInput input;
    //SdlInput.handleInput/update/postUpdate are package-private in Arc; the loop drives them here

    private VulkanGraphics graphics;
    private VulkanWindow vulkanWindow;
    private long window;
    private boolean running;
    private Thread mainThread;
    private boolean disposed;

    public VulkanApplication(ApplicationListener listener, String title, int width, int height,
                             boolean audio, boolean pixelReport){
        this.listener = listener;
        this.title = title;
        this.audio = audio;
        this.width = width;
        this.height = height;
        this.pixelReport = pixelReport;

        listeners.add(listener);
        mainThread = Thread.currentThread();
        // The loop must be running before init(): Arc's own application implementations set this
        // in their constructor, and a false value silently skips every frame.
        running = true;

        init();
        Core.app = this;
        Core.files = new SdlFiles();

        // The window is opened in init(), because the Vulkan surface must exist before any GL
        // resource is created, and ClientLauncher.setup() reads the GL version from Core.graphics
        // during listener.init().
        graphics = new VulkanGraphics(vulkanWindow);
        Core.graphics = graphics;

        input = new SdlInput();
        Core.input = input;
        Core.settings = new Settings();
        Core.audio = new Audio(audio);

        //The one line that makes this a Vulkan backend: from here on every GL call Arc makes is
        //served by VulkanGL20, and no GL resource has been created yet.
        Core.gl20 = new VulkanGL20();
        Core.gl = Core.gl20;
        Core.gl30 = null;

        Log.info("[Vulkan] application backend active, GL20 is VulkanGL20");

        try{
            listen(l -> l.init());
            loop();
        }finally{
            cleanup();
        }
    }

    private int width, height;

    /** SDL and the window. No GL context is created: the Vulkan surface owns the window. */
    private void init(){
        ArcNativesLoader.load();
        NativeUtils.forceUtf8Locale();

        int flags = SDL.SDL_INIT_TIMER | SDL.SDL_INIT_AUDIO | SDL.SDL_INIT_VIDEO | SDL.SDL_INIT_EVENTS | SDL.SDL_INIT_NOPARACHUTE;
        check(SDL.SDL_Init(flags));

        SDL.SDL_SetHint("SDL_IME_SHOW_UI", "1");
        SDL.SDL_SetHint("SDL_WINDOWS_DPI_SCALING", "1");

        window = SDL.SDL_CreateWindow(title, width, height, 4); // SDL_WINDOW_SHOWN
        if(window == 0) throw new SdlApplication.SdlError();

        //the Vulkan surface is attached to the window that was just created
        vulkanWindow = new VulkanWindow(title, width, height);
        if(!vulkanWindow.open()){
            throw new ArcRuntimeException("Could not create a Vulkan surface on the game window: " + VulkanDevice.lastError());
        }
        Log.info("[Vulkan] surface @x@ on '@' (swapchain images: @)",
            vulkanWindow.width(), vulkanWindow.height(), title, VulkanDevice.imageCount());
    }

    private void check(int result){
        if(result != 0) throw new SdlApplication.SdlError();
    }

    private static java.lang.reflect.Method inputHandle, inputUpdate, inputPostUpdate;

    static{
        try{
            inputHandle = arc.backend.sdl.SdlInput.class.getDeclaredMethod("handleInput", int[].class);
            inputHandle.setAccessible(true);
            inputUpdate = arc.backend.sdl.SdlInput.class.getDeclaredMethod("update");
            inputUpdate.setAccessible(true);
            inputPostUpdate = arc.backend.sdl.SdlInput.class.getDeclaredMethod("postUpdate");
            inputPostUpdate.setAccessible(true);
        }catch(Throwable error){
            throw new ArcRuntimeException("The packaged Arc SDL backend does not expose the input pump methods this backend drives", error);
        }
    }

    private void loop(){
        listen(l -> {
        });

        while(running){
            if(SDL.SDL_PollEvent(inputs)){
                if(inputs[0] == 0){
                    running = false;
                    continue;
                }
                if(inputs[0] == 1){
                    int sub = inputs[1];
                    if(sub == 6){
                        graphics.updateFrame();
                        listen(l -> l.resize(inputs[2], inputs[3]));
                    }else if(sub == 12 || sub == 13){
                        listen(l -> {
                            if(sub == 12) l.pause();
                            else l.resume();
                        });
                    }
                }else{
                    call(inputHandle, input, (Object)inputs);
                }
            }

            graphics.updateFrame();
            call(inputUpdate, input);
            defaultUpdate();
            listen(l -> l.update());
            runnables.run();

            //the Vulkan present replaces the OpenGL buffer swap
            graphics.presentFrame();

            call(inputPostUpdate, input);

            // A heartbeat proves the render loop is actually advancing, not just alive: without it
            // a hung frame and a running one look identical from the outside.
            long now = Time.millis();
            if(now - lastReport >= 5000){
                lastReport = now;
                Log.info("[Vulkan] frame @ at @ fps (@x@)", graphics.getFrameId(),
                    graphics.getFramesPerSecond(), graphics.getWidth(), graphics.getHeight());
                reportPixels();
                Log.info("[Vulkan] draws: @ submitted (@ refused) / presents: @ (@ refused)",
                    VulkanDevice.renders(), VulkanDevice.renderFailures(),
                    VulkanDevice.presents(), VulkanDevice.presentFailures());
                orbital.desktop.vulkan.gl.VulkanGL20.reportDrawReasons();
            }
        }
    }

    private long lastReport = Time.millis();
    /** Enables the periodic frame readback report; diagnostic only. */
    private final boolean pixelReport;

    /**
     * Reads the last presented target back and logs what it contains. Enabled by
     * {@code -vulkan-app-pixels}: it costs a full-frame readback, so it is diagnostic only and
     * stays off by default. A screen with one flat colour means nothing was drawn.
     */
    private void reportPixels(){
        if(!pixelReport) return;
        int width = VulkanDevice.boundWidth(), height = VulkanDevice.boundHeight();
        if(width <= 0 || height <= 0) return;

        byte[] rgba = new byte[width * height * VulkanDevice.bytesPerPixel];
        if(!VulkanDevice.pixels(rgba)){
            Log.warn("[Vulkan] pixel report failed: @", VulkanDevice.lastError());
            return;
        }

        // 5 bits per channel keeps the histogram small while still separating text, UI and world.
        int[] histogram = new int[1 << 15];
        int distinct = 0;
        for(int i = 0; i < rgba.length; i += 4){
            int r = (rgba[i] & 0xff) >> 3, g = (rgba[i + 1] & 0xff) >> 3, b = (rgba[i + 2] & 0xff) >> 3;
            int slot = (r << 10) | (g << 5) | b;
            if(histogram[slot]++ == 0) distinct++;
        }
        int[] top = new int[3];
        int[] best = new int[3];
        int bestCount = -1;
        for(int slot = 0; slot < histogram.length; slot++){
            if(histogram[slot] > bestCount){
                bestCount = histogram[slot];
                best[0] = (slot >> 10) & 31;
                best[1] = (slot >> 5) & 31;
                best[2] = slot & 31;
            }
            if(histogram[slot] > top[2]){
                top[2] = histogram[slot];
                java.util.Arrays.sort(top);
            }
        }

        int total = width * height;
        Log.info("[Vulkan] pixels @x@: @ distinct colours, dominant rgb(@,@,@) covers @%",
            width, height, distinct, best[0] << 3, best[1] << 3, best[2] << 3,
            Math.round(100f * bestCount / total));
    }

    private void cleanup(){
        if(disposed) return;
        disposed = true;
        if(graphics != null){
            graphics.dispose();
        }
        Core.app.post(SDL::SDL_Quit);
    }

    private void presentFrame(){
    }


    /** Invokes a package-private SdlInput pump method, rethrowing failures as runtime errors. */
    private static void call(java.lang.reflect.Method method, Object target, Object... args){
        try{
            method.invoke(target, args);
        }catch(ReflectiveOperationException error){
            throw new ArcRuntimeException("Could not drive the SDL input pump: " + error.getCause(), error);
        }
    }

    private void listen(Cons<ApplicationListener> action){
        synchronized(listeners){
            for(ApplicationListener listener : listeners){
                action.get(listener);
            }
        }
    }

    @Override
    public Seq<ApplicationListener> getListeners(){
        return listeners;
    }

    @Override
    public ApplicationType getType(){
        return ApplicationType.desktop;
    }

    @Override
    public String getClipboardText(){
        return SDL.SDL_GetClipboardText();
    }

    @Override
    public void setClipboardText(String text){
        SDL.SDL_SetClipboardText(text);
    }

    @Override
    public boolean openFolder(String path){
        try{
            OS.exec("explorer", path);
            return true;
        }catch(Throwable error){
            return false;
        }
    }

    @Override
    public boolean openURI(String url){
        try{
            OS.exec("explorer", url);
            return true;
        }catch(Throwable error){
            return false;
        }
    }

    @Override
    public void post(Runnable runnable){
        runnables.post(runnable);
    }

    @Override
    public void exit(){
        running = false;
    }

    @Override
    public void dispose(){
        running = false;
    }
}
