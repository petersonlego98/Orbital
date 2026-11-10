package orbital.desktop.vulkan;

import arc.util.*;

/**
 * Java binding for the native Vulkan 1.0 rendering core ({@code VulkanDevice.cpp}).
 *
 * <p>The default path provides an offscreen render target, one graphics pipeline at a time,
 * host-visible vertex/uniform buffers and pixel readback. An opt-in windowed smoke path accepts
 * explicit Win32 handles and presents a clear; it does not integrate with the launcher.</p>
 *
 * <p>Vertex layout (matched on the native side):</p>
 * <pre>
 *   location 0: vec2 position   offset 0
 *   location 1: vec4 color      offset 8
 *   stride:     6 floats        (24 bytes)
 * </pre>
 *
 * <p>Descriptor set layout (matched on the native side):</p>
 * <pre>
 *   set 0, binding 0: uniform buffer (UBO, {@link orbital.desktop.vulkan.gl.GLSLProgram#UNIFORM_BINDING})
 *   set 0, binding 1: combined image sampler, initially a 1x1 white texture
 * </pre>
 *
 * <p>Known limits of this stage:</p>
 * <ul>
 *   <li>the render pass has a single color attachment, so depth/stencil state cannot be enabled yet;</li>
 *   <li>blending is fixed to {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA} when enabled;</li>
 *   <li>only one texture exists, so shaders using more than one sampler are not supported;</li>
 *   <li>the pipeline is baked at creation time, including topology, which is why {@link #pipeline}
 *       takes the primitive mode instead of a separate state call.</li>
 * </ul>
 */
public final class VulkanDevice{
    /** Floats per vertex: {@code x, y, r, g, b, a}. */
    public static final int floatsPerVertex = 6;
    /** Bytes per vertex ({@link #floatsPerVertex} * 4). */
    public static final int bytesPerVertex = floatsPerVertex * Float.BYTES;
    /** Bytes per RGBA pixel in a readback frame. */
    public static final int bytesPerPixel = 4;

    private static boolean created;
    private static boolean windowed;

    /** Whether the device owns a default offscreen render target; cached after creation. */
    private static boolean targetCreated;
    /** Draw/present counters, for diagnostics only; never read on the hot path. */
    private static long renders, presents, renderFailures, presentFailures;
    private static boolean vSync;
    private static int width, height;
    private static int imageIndex = -1;
    private static int surfaceWidth, surfaceHeight;
    private static int imageCount = 0;
    private static String failure;

    /**
     * Resolves one native entry point eagerly, so that a stale packaged DLL degrades into a clear
     * failure message instead of an UnsatisfiedLinkError in the middle of a frame.
     */
    private static final boolean linked = probeLink();

    private VulkanDevice(){
    }

    /** True when the packaged native library could be loaded on this host. */
    public static boolean available(){
        return linked;
    }

    private static boolean probeLink(){
        if(!VulkanLibrary.loaded()) return false;
        try{
            // nativeLastError() only reads a zero-initialized buffer: it is safe to call before
            // create(), and it fails fast when the packaged DLL predates the VulkanDevice code.
            nativeLastError();
            return true;
        }catch(LinkageError error){
            failure = "native Vulkan entry points are missing: the packaged orbital_vulkan.dll is older than this build";
            Log.debug("[Vulkan] @", error.toString());
            return false;
        }
    }

    /** True after a successful {@link #create} or {@link #createWindowed} and before {@link #destroy}. */
    public static boolean created(){
        return created;
    }

    /** True when the active device is attached to a Win32 window swapchain. */
    public static boolean isWindowed(){
        return windowed;
    }

    /** True when vertical synchronization is on (FIFO present mode); false otherwise. */
    public static boolean hasVSync(){
        return vSync;
    }

    /** Reason of the last rejected call, or null. */
    public static String failureReason(){
        return failure;
    }

    public static int width(){
        return width;
    }

    public static int height(){
        return height;
    }

    /**
     * Creates the offscreen device: instance, physical device with a graphics queue, command
     * pool/buffer/fence, vertex/uniform/readback buffers, color image, render pass, framebuffer,
     * sampler, descriptor set and a 1x1 white placeholder texture.
     *
     * @return false when the native library is missing or Vulkan is unusable; the reason is then in
     * {@link #lastError()} (and {@link #failureReason()} when the call never reached native code).
     */
    public static boolean create(int width, int height){
        created = false;
        windowed = false;
        failure = null;

        if(!available()){
            failure = unavailableReason();
            return false;
        }
        if(width <= 0 || height <= 0){
            failure = "invalid size " + width + "x" + height;
            return false;
        }
        if(!nativeCreate(width, height)){
            failure = "vkCreateDevice failed";
            return false;
        }

        VulkanDevice.width = width;
        VulkanDevice.height = height;
        created = true;
        targetCreated = true;
        return true;
    }

    /**
     * Creates the opt-in Win32 presentation smoke device. The HWND and HINSTANCE are supplied by
     * the caller as native pointer values; this method never attempts to discover them.
     */
    public static boolean createWindowed(int width, int height, long hwnd, long hinstance){
        created = false;
        windowed = false;
        failure = null;

        if(!available()){
            failure = unavailableReason();
            return false;
        }
        if(width <= 0 || height <= 0){
            failure = "invalid size " + width + "x" + height;
            return false;
        }
        if(hwnd == 0L || hinstance == 0L){
            failure = "HWND and HINSTANCE must be non-zero native handles";
            return false;
        }
        if(!nativeCreateWindowed(width, height, hwnd, hinstance)){
            failure = nativeLastError();
            return false;
        }

        VulkanDevice.width = width;
        VulkanDevice.height = height;
        surfaceWidth = width;
        surfaceHeight = height;
        imageIndex = -1;
        imageCount = nativeImageCount();
        created = true;
        windowed = true;
        targetCreated = nativeHasRenderTarget();
        return true;
    }

    /** Destroys every native resource; safe to call when no device exists. */
    public static void destroy(){
        if(created){
            nativeDestroy();
            created = false;
        }
        width = 0;
        height = 0;
        windowed = false;
        targetCreated = false;
        failure = null;
    }

    /**
     * Builds the graphics pipeline from SPIR-V modules. Replaces the previously active pipeline.
     *
     * @param vertexSpirv   vertex stage SPIR-V, word aligned
     * @param fragmentSpirv fragment stage SPIR-V, word aligned
     * @param blend         enables {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA} blending
     * @param primitive     GL primitive enum value ({@code GL_TRIANGLES} and friends); mapped to a
     *                      Vulkan topology here
     */
    public static boolean pipeline(byte[] vertexSpirv, byte[] fragmentSpirv, boolean blend, int primitive){
        return pipelineWithLayout(vertexSpirv, fragmentSpirv, blend, primitive, bytesPerVertex,
            new int[]{0, 2, 0, 1, 4, 2 * Float.BYTES});
    }

    /**
     * Builds a pipeline with an explicit interleaved vertex layout.
     *
     * @param stride  bytes per vertex
     * @param layout  flat triples of {@code location, componentCount (1..4), byteOffset}
     */
    public static boolean pipelineWithLayout(byte[] vertexSpirv, byte[] fragmentSpirv, boolean blend,
                                             int primitive, int stride, int[] layout){
        if(!guard()) return false;
        if(vertexSpirv == null || fragmentSpirv == null){
            failure = "null SPIR-V module";
            return false;
        }
        if(vertexSpirv.length % 4 != 0 || fragmentSpirv.length % 4 != 0){
            failure = "SPIR-V module is not word aligned";
            return false;
        }
        if(primitive == GL_LINE_LOOP){
            failure = "GL_LINE_LOOP has no Vulkan topology";
            return false;
        }
        if(stride <= 0){
            failure = "invalid vertex stride " + stride;
            return false;
        }
        if(layout == null || layout.length == 0 || layout.length % 3 != 0){
            failure = "vertex layout must be a non-empty multiple of three integers";
            return false;
        }
        return check(nativeCreatePipelineWithLayout(vertexSpirv, fragmentSpirv, blend, primitive, stride, layout));
    }

    /** Sets the clear color used by {@link #render} or {@link #clearAndPresent}. */
    public static boolean clearColor(float r, float g, float b, float a){
        if(!guard()) return false;
        nativeClearColor(r, g, b, a);
        return true;
    }

    /** Copies interleaved vertex data into the host-visible vertex buffer. */
    public static boolean vertices(float[] data){
        if(!guard()) return false;
        if(data == null){
            failure = "null vertex data";
            return false;
        }
        return check(nativeWriteVertices(data));
    }

    /**
     * Copies 32-bit indices into the host-visible index buffer. Indices are always widened to
     * {@code VK_INDEX_TYPE_UINT32}, so callers do not have to track 16/32-bit GL variants.
     */
    public static boolean indices(int[] data){
        if(!guard()) return false;
        if(data == null){
            failure = "null index data";
            return false;
        }
        return check(nativeWriteIndices(data));
    }

    /** Copies raw uniform buffer data; the layout is described by the uniform block. */
    public static boolean uniforms(byte[] data){
        if(!guard()) return false;
        if(data == null){
            failure = "null uniform data";
            return false;
        }
        return check(nativeWriteUniforms(data));
    }

    /**
     * Records and submits one draw call.
     *
     * @param vertexCount number of vertices to draw from the vertex buffer
     * @param clear       begins the render pass with a color clear
     */
    public static boolean render(int vertexCount, boolean clear){
        if(!offscreenGuard()) return false;
        if(vertexCount < 0){
            failure = "negative vertex count";
            return false;
        }
        renders++;
        boolean ok = check(nativeRender(vertexCount, clear));
        if(!ok) renderFailures++;
        return ok;
    }

    /** Acquires one swapchain image, clears it to RGBA, and presents exactly one frame. */
    public static boolean clearAndPresent(float r, float g, float b, float a){
        if(!windowedGuard()) return false;
        return check(nativeClearAndPresent(r, g, b, a));
    }

    /**
     * Presents whatever the render path drew into the current target. Requires a windowed device,
     * because the target is blitted into that device's swapchain.
     */
    public static boolean presentFrame(){
        if(!windowedGuard()) return false;
        presents++;
        boolean ok = check(nativePresentFrame());
        if(!ok) presentFailures++;
        return ok;
    }

    /** Draw calls submitted through {@link #render}/{@link #renderIndexed}; diagnostics only. */
    public static long renders(){
        return renders;
    }

    /** Presents issued through {@link #presentFrame}; diagnostics only. */
    public static long presents(){
        return presents;
    }

    /** Draw calls the device refused; diagnostics only. */
    public static long renderFailures(){
        return renderFailures;
    }

    /** Presents the device refused; diagnostics only. */
    public static long presentFailures(){
        return presentFailures;
    }

    /** Records and submits one indexed draw call against the last {@link #indices} upload. */
    public static boolean renderIndexed(int indexCount, boolean clear){
        if(!offscreenGuard()) return false;
        if(indexCount < 0){
            failure = "negative index count";
            return false;
        }
        renders++;
        boolean ok = check(nativeRenderIndexed(indexCount, clear));
        if(!ok) renderFailures++;
        return ok;
    }

    /** {@link #render} without clearing, for layered draws inside one frame. */
    public static boolean draw(int vertexCount){
        return render(vertexCount, false);
    }

    /** {@link #renderIndexed} without clearing, for layered draws inside one frame. */
    public static boolean drawIndexed(int indexCount){
        return renderIndexed(indexCount, false);
    }

    /** Configures vertical synchronization (FIFO when true, MAILBOX/IMMEDIATE when false). */
    public static boolean setVSync(boolean enabled){
        if(!windowedGuard()) return false;
        return check(nativeSetVSync(enabled));
    }

    /** Resizes swapchain and framebuffers to new window dimensions. */
    public static boolean resizeWindow(int width, int height){
        if(!windowedGuard()) return false;
        if(width <= 0 || height <= 0){
            failure = "invalid size " + width + "x" + height;
            return false;
        }
        if(check(nativeResizeWindow(width, height))){
            VulkanDevice.width = width;
            VulkanDevice.height = height;
            return true;
        }
        return false;
    }

    /** Uploads RGBA8 pixel data to the active texture image and updates descriptor sets. */
    public static boolean uploadTexture(int width, int height, byte[] rgba){
        if(!guard()) return false;
        if(width <= 0 || height <= 0){
            failure = "invalid texture dimensions " + width + "x" + height;
            return false;
        }
        if(rgba == null){
            failure = "null texture pixel buffer";
            return false;
        }
        return check(nativeUploadTexture(width, height, rgba));
    }

    /** Copies the rendered RGBA frame into {@code rgba}, which must be {@link #bytesPerPixel} bytes per pixel. */
    public static boolean pixels(byte[] rgba){
        if(!offscreenGuard()) return false;
        if(rgba == null){
            failure = "null pixel buffer";
            return false;
        }
        int expected = boundWidth() * boundHeight() * bytesPerPixel;
        if(rgba.length != expected){
            failure = "pixel buffer must hold " + expected + " bytes (got " + rgba.length + ")";
            return false;
        }
        return check(nativeReadPixels(rgba));
    }

    /** Convenience readback: allocates the frame buffer and returns the RGBA pixels, or null on failure. */
    public static byte[] frame(){
        if(!created) return null;
        int width = boundWidth(), height = boundHeight();
        if(width <= 0 || height <= 0) return null;
        byte[] rgba = new byte[width * height * bytesPerPixel];
        return pixels(rgba) ? rgba : null;
    }


    // ------------------------------------------------------------------ window surface

    /**
     * Win32 handle of an SDL window, found by title. Lets a Vulkan surface be created for a window
     * the application already owns, which is what the windowed rendering path needs.
     */
    public static long findWindow(String title){
        if(title == null || title.isEmpty()) return 0L;
        return nativeFindWindow(title);
    }

    /** HINSTANCE of this process, the other half of a Win32 surface creation. */
    public static long moduleHandle(){
        return nativeModuleHandle();
    }

    /**
     * Draws into the swapchain, presents, and copies the presented image back. The readback is what
     * makes a headless check of the presentation path possible: without it there is no way to see
     * what actually reached the screen.
     *
     * @param rgba buffer of {@link #windowWidth() * windowHeight() * bytesPerPixel} bytes
     */
    public static boolean presentAndReadBack(float r, float g, float b, float a, byte[] rgba){
        return guard() && check(nativePresentAndReadBack(r, g, b, a, rgba));
    }

    /** Drawable size of the window surface. The swapchain follows the window, not the request. */
    public static int windowWidth(){
        return nativeBoundWidth();
    }

    /** Drawable size of the window surface. The swapchain follows the window, not the request. */
    public static int windowHeight(){
        return nativeBoundHeight();
    }

    /** Frame currently acquired from the swapchain, or -1 when no frame is open. */
    public static int imageIndex(){
        return guard() ? nativeImageIndex() : -1;
    }

    /** Number of images in the swapchain. */
    public static int imageCount(){
        return imageCount;
    }

    /** Physical device name reported by the driver, or null when unavailable. */
    public static String deviceName(){
        return available() ? nativeDeviceName() : null;
    }

    // ------------------------------------------------------------------ framebuffer objects

    /**
     * Creates an offscreen framebuffer whose color can be rendered into and then sampled.
     *
     * @param depth    attach a depth buffer (depth only, or depth+stencil when {@code stencil} is set)
     * @param stencil attach a stencil buffer as well; ignored when {@code depth} is false
     * @return the framebuffer handle, or 0 on failure (reason in {@link #lastError()}). Handle 0
     * always denotes the default offscreen target.
     */
    public static int createFramebuffer(int width, int height, boolean depth, boolean stencil){
        if(!guard()) return 0;
        if(width <= 0 || height <= 0){
            failure = "invalid framebuffer size " + width + "x" + height;
            return 0;
        }
        int id = nativeCreateFramebuffer(width, height, depth, stencil);
        if(id == 0) failure = nativeLastError();
        return id;
    }

    /** Binds a framebuffer created by {@link #createFramebuffer}, or 0 for the default target. */
    public static boolean bindFramebuffer(int framebuffer){
        return guard() && check(nativeBindFramebuffer(framebuffer));
    }

    /** Destroys a framebuffer; the default target (0) cannot be deleted. */
    public static boolean deleteFramebuffer(int framebuffer){
        return guard() && check(nativeDeleteFramebuffer(framebuffer));
    }

    /** 1 = GL_FRAMEBUFFER_COMPLETE, 0 = incomplete or unknown; mirrors {@code glCheckFramebufferStatus}. */
    public static int framebufferStatus(int framebuffer){
        return guard() ? nativeFramebufferStatus(framebuffer) : 0;
    }

    /**
     * Points the sampler at the color of a rendered framebuffer ({@code framebuffer > 0}), or back
     * at the texture uploaded through {@link #uploadTexture} ({@code framebuffer <= 0}).
     */
    public static boolean sampleFramebuffer(int framebuffer){
        return guard() && check(nativeSampleFramebuffer(framebuffer));
    }

    /** Width of the bound target: the default offscreen size, or the framebuffer extent. */
    public static int boundWidth(){
        return nativeBoundWidth();
    }

    /** Height of the bound target: the default offscreen size, or the framebuffer extent. */
    public static int boundHeight(){
        return nativeBoundHeight();
    }

    // ------------------------------------------------------------------ clear, viewport, sync

    /** Sets the values a clear uses for depth and stencil. */
    public static boolean clearDepthStencil(float depth, int stencil){
        if(!guard()) return false;
        nativeClearDepthStencil(depth, stencil);
        return true;
    }

    /** Viewport used by draws into the bound target. */
    public static boolean setViewport(int x, int y, int width, int height){
        if(!guard()) return false;
        nativeSetViewport(x, y, width, height);
        return true;
    }

    /** Scissor rectangle used by draws into the bound target. */
    public static boolean setScissor(int x, int y, int width, int height){
        if(!guard()) return false;
        nativeSetScissor(x, y, width, height);
        return true;
    }

    /** Waits for the queue to drain. Every render path already blocks, so this is a safety net. */
    public static boolean finish(){
        if(!guard()) return false;
        nativeFinish();
        return true;
    }

    /** Sampling of a texture slot: the sampler this binding reads from. */
    public static final class PipelineStateIndices{
        public static final int depthTest = 0, depthWrite = 1, depthFunc = 2;
        public static final int stencilTest = 3, stencilFunc = 4, stencilRef = 5, stencilMask = 6;
        public static final int stencilFail = 7, stencilDepthFail = 8, stencilDepthPass = 9;
        public static final int cullFace = 10, frontFaceCCW = 11;
        public static final int blend = 12, blendSrcRGB = 13, blendDstRGB = 14, blendSrcAlpha = 15;
        public static final int blendDstAlpha = 16, blendEqRGB = 17, blendEqAlpha = 18;
        public static final int colorMaskR = 19, colorMaskG = 20, colorMaskB = 21, colorMaskA = 22;
        public static final int lineWidth = 23;
        public static final int blendConstantR = 24, blendConstantG = 25, blendConstantB = 26, blendConstantA = 27;
        public static final int length = 28;

        private PipelineStateIndices(){
        }
    }

    /**
     * Builds a graphics pipeline from SPIR-V modules, freezing the state a Vulkan pipeline
     * cannot change later: depth and stencil, blending (including the constant factors and
     * glBlendColor), the color mask, cull face, front face and the line width.
     *
     * <p>{@code state} holds {@link PipelineStateIndices#length} integers, in the order documented
     * there; a malformed array makes the native side reject the call instead of defaulting fields.</p>
     *
     * @param vertexSpirv   vertex stage SPIR-V, word aligned
     * @param fragmentSpirv fragment stage SPIR-V, word aligned
     * @param primitive     GL primitive enum value; mapped to a Vulkan topology here
     * @param stride        bytes per vertex
     * @param layout        flat triples of {@code location, componentCount (1..4), byteOffset}
     * @param state         {@link PipelineStateIndices} describing the frozen pipeline state
     */
    public static boolean pipelineState(byte[] vertexSpirv, byte[] fragmentSpirv, int primitive,
                                        int stride, int[] layout, int[] state){
        if(!guard()) return false;
        if(vertexSpirv == null || fragmentSpirv == null){
            failure = "null SPIR-V module";
            return false;
        }
        if(vertexSpirv.length % 4 != 0 || fragmentSpirv.length % 4 != 0){
            failure = "SPIR-V module is not word aligned";
            return false;
        }
        if(primitive == GL_LINE_LOOP){
            failure = "GL_LINE_LOOP has no Vulkan topology";
            return false;
        }
        if(stride <= 0){
            failure = "invalid vertex stride " + stride;
            return false;
        }
        if(layout == null || layout.length == 0 || layout.length % 3 != 0){
            failure = "vertex layout must be a non-empty multiple of three integers";
            return false;
        }
        if(state == null || state.length != PipelineStateIndices.length){
            failure = "pipeline state must hold exactly " + PipelineStateIndices.length + " integers";
            return false;
        }
        return check(nativeCreatePipelineWithState(vertexSpirv, fragmentSpirv, primitive, stride, layout, state));
    }

    /** Native error string of the last failing native call, or the reason of a rejected Java call. */
    public static String lastError(){
        if(!available()) return failure != null ? failure : unavailableReason();

        String error = nativeLastError();
        if(error != null && !error.isEmpty()) return error;
        return failure;
    }

    private static boolean guard(){
        if(!available()){
            failure = unavailableReason();
            return false;
        }
        if(!created){
            failure = "device is not created";
            return false;
        }
        failure = null;
        return true;
    }

    /**
     * Guards the operations that need a render target. A windowed device now builds the same
     * default target as an offscreen one, so the only difference left between the two is how the
     * final frame reaches the screen - see {@link #presentFrame}.
     */
    private static boolean offscreenGuard(){
        if(!guard()) return false;
        if(windowed && !targetCreated){
            failure = "operation requires an offscreen Vulkan device";
            return false;
        }
        return true;
    }

    private static boolean windowedGuard(){
        if(!guard()) return false;
        if(!windowed){
            failure = "operation requires a windowed Vulkan device";
            return false;
        }
        return true;
    }

    private static String unavailableReason(){
        if(!VulkanLibrary.loaded()) return "native library unavailable: " + VulkanLibrary.failureReason();
        return "native Vulkan entry points are missing: the packaged orbital_vulkan.dll is older than this build";
    }

    private static boolean check(boolean result){
        if(!result) failure = nativeLastError();
        return result;
    }

    // GL primitive enum values (arc.graphics.GL20). They are duplicated here on purpose: the native
    // layer maps them to Vulkan topologies, and this binding must not depend on the GL contract.
    private static final int GL_LINE_LOOP = 2;

    private static native boolean nativeCreate(int width, int height);
    private static native boolean nativeCreateWindowed(int width, int height, long hwnd, long hinstance);
    private static native void nativeDestroy();
    private static native boolean nativeCreatePipeline(byte[] vertexSpirv, byte[] fragmentSpirv, boolean blend, int primitive);
    private static native boolean nativeCreatePipelineWithLayout(byte[] vertexSpirv, byte[] fragmentSpirv, boolean blend, int primitive, int stride, int[] layout);
    private static native boolean nativeCreatePipelineWithState(byte[] vertexSpirv, byte[] fragmentSpirv, int primitive, int stride, int[] layout, int[] state);
    private static native void nativeClearColor(float r, float g, float b, float a);
    private static native boolean nativeWriteVertices(float[] vertices);
    private static native boolean nativeWriteIndices(int[] indices);
    private static native boolean nativeWriteUniforms(byte[] data);
    private static native boolean nativeRender(int vertexCount, boolean clear);
    private static native boolean nativeRenderIndexed(int indexCount, boolean clear);
    private static native boolean nativeClearAndPresent(float r, float g, float b, float a);
    private static native boolean nativeSetVSync(boolean enabled);
    private static native boolean nativeResizeWindow(int width, int height);
    private static native boolean nativeUploadTexture(int width, int height, byte[] rgba);
    private static native boolean nativeReadPixels(byte[] output);
    private static native String nativeLastError();
    private static native String nativeDeviceName();
    private static native long nativeFindWindow(String title);
    private static native long nativeModuleHandle();
    private static native boolean nativePresentAndReadBack(float r, float g, float b, float a, byte[] rgba);
    /**
     * Blits the rendered target (the default offscreen image, or a bound framebuffer) into the
     * next swapchain image and presents it. This is the Vulkan counterpart of a buffer swap.
     */
    private static native boolean nativePresentFrame();
    /** True when the device owns a default render target, i.e. it can render offscreen. */
    private static native boolean nativeHasRenderTarget();
    /**
     * Points the Vulkan loader at a specific ICD, as {@code VK_ICD_FILENAMES} would. The loader
     * resolves its driver list when the instance is created, so this has to run before
     * {@link #create} - which is exactly what {@link VulkanLibrary} does.
     */
    private static native void nativeSetIcdPath(String path);

    /**
     * Overrides the driver the loader picks. Diagnostic use on machines whose only Vulkan driver
     * is a software one, where no {@code VK_ICD_FILENAMES} environment variable is practical.
     */
    public static void setIcdPath(String path){
        if(path == null || path.isEmpty()) return;
        nativeSetIcdPath(path);
    }
    private static native int nativeImageCount();
    private static native int nativeImageIndex();

    private static native int nativeCreateFramebuffer(int width, int height, boolean depth, boolean stencil);
    private static native boolean nativeBindFramebuffer(int framebuffer);
    private static native boolean nativeDeleteFramebuffer(int framebuffer);
    private static native int nativeFramebufferStatus(int framebuffer);
    private static native boolean nativeSampleFramebuffer(int framebuffer);
    private static native int nativeBoundWidth();
    private static native int nativeBoundHeight();
    private static native void nativeClearDepthStencil(float depth, int stencil);
    private static native void nativeSetViewport(int x, int y, int width, int height);
    private static native void nativeSetScissor(int x, int y, int width, int height);
    private static native void nativeFinish();
}
