package orbital.desktop.vulkan;

import arc.*;
import arc.audio.Audio;
import arc.graphics.*;
import arc.graphics.gl.*;
import arc.struct.*;
import arc.util.*;

import java.util.*;

/**
 * {@code arc.Graphics} implementation over the Vulkan rendering core.
 *
 * <p>It exists so an Arc application can run without an OpenGL context: the GL20/GL30 fields point
 * at {@link VulkanGL20} instead of a backend GL implementation, and every frame is presented by
 * {@link VulkanWindow} rather than by a context swap.</p>
 *
 * <p>Deliberately minimal: only what Arc's startup path and render loop touch. Anything the Vulkan
 * core cannot answer yet returns a documented default instead of pretending.</p>
 */
public final class VulkanGraphics extends Graphics{
    private final VulkanWindow window;
    private long frameId;
    private long lastFrame = Time.millis();
    private float delta = 1f / 60f;
    private int fps = 60;
    private int frames;
    private long fpsTime = lastFrame;
    /** Clear colour used by {@link #clear}; kept in sync with the GL20 clear colour. */
    float clearR, clearG, clearB;

    public VulkanGraphics(VulkanWindow window){
        this.window = window;
    }

    @Override
    public GL20 getGL20(){
        return Core.gl20;
    }

    @Override
    public void setGL20(GL20 gl20){
        Core.gl20 = gl20;
        Core.gl = gl20;
    }

    @Override
    public GL30 getGL30(){
        return Core.gl30;
    }

    @Override
    public void setGL30(GL30 gl30){
        Core.gl30 = gl30;
    }

    @Override
    public boolean isGL30Available(){
        return Core.gl30 != null;
    }

    @Override
    public boolean supportsInstancing(){
        return false;
    }

    /** Advances the frame counters; called once per presented frame by {@link VulkanApplication}. */
    public void updateFrame(){
        long now = Time.millis();
        delta = Math.max((now - lastFrame) / 1000f, 1f / 1000f);
        lastFrame = now;
        frameId++;
        frames++;

        if(now - fpsTime >= 1000){
            fps = frames;
            frames = 0;
            fpsTime = now;
        }
    }

    /** Presents the current Vulkan frame; the Vulkan counterpart of a context swap. */
    public void presentFrame(){
        if(!window.isOpen()) return;
        //The GL20 layer keeps the clear colour the game last asked for; presenting without a
        //rendered frame would show that colour through the device's own default target.
        if(!window.presentFrame()){
            Log.warn("[Vulkan] present failed: @", VulkanDevice.lastError());
        }
    }

    @Override
    public int getWidth(){
        return window.width();
    }

    @Override
    public int getHeight(){
        return window.height();
    }

    @Override
    public int getBackBufferWidth(){
        return window.width();
    }

    @Override
    public int getBackBufferHeight(){
        return window.height();
    }

    @Override
    public long getFrameId(){
        return frameId;
    }

    @Override
    public float getDeltaTime(){
        return delta;
    }

    @Override
    public int getFramesPerSecond(){
        return fps;
    }

    @Override
    public GLVersion getGLVersion(){
        return new GLVersion(Core.app.getType(),
            "1.0",
            "Vulkan " + (VulkanDevice.deviceName() != null ? VulkanDevice.deviceName() : "unavailable"),
            "OpenGL ES 2.0 (Vulkan 1.0 Port)");
    }

    @Override
    public float getPpiX(){
        return 96f;
    }

    @Override
    public float getPpiY(){
        return 96f;
    }

    @Override
    public float getPpcX(){
        return 96f / 2.54f;
    }

    @Override
    public float getPpcY(){
        return 96f / 2.54f;
    }

    @Override
    public float getDensity(){
        return 1f;
    }

    @Override
    public void setTitle(String title){
        //the window is owned by the SDL backend; renaming it is not wired yet
    }

    @Override
    public void setVSync(boolean vsync){
        window.setVSync(vsync);
    }

    @Override
    public BufferFormat getBufferFormat(){
        return new BufferFormat(8, 8, 8, 8, 24, 8, 0, false);
    }

    @Override
    protected void setCursor(Graphics.Cursor cursor){
    }

    @Override
    public void setSystemCursor(Graphics.Cursor.SystemCursor cursor){
    }

    @Override
    public boolean supportsExtension(String extensionName){
        return false;
    }

    @Override
    public boolean isContinuousRendering(){
        return true;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous){
    }

    @Override
    public void requestRendering(){
    }

    @Override
    public boolean isFullscreen(){
        return false;
    }

    @Override
    public boolean setFullscreen(boolean fullscreen){
        return false;
    }

    @Override
    public Graphics.Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot){
        return null;
    }

    @Override
    public Graphics.Cursor newCursor(String resourcePath, int scale){
        return null;
    }

    @Override
    public void dispose(){
        window.close();
    }
}
