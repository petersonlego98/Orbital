package orbital.desktop.vulkan.gl;


import arc.graphics.GL20;


import arc.util.Log;

import orbital.desktop.vulkan.VulkanDevice;


import java.nio.*;


import java.util.*;

/**
 * Implementation of the draw-relevant part of Arc's {@link GL20} contract on top of the Vulkan
 * rendering core ({@link VulkanDevice}).
 *
 * <p>OpenGL state is tracked in {@link VulkanGLState}; buffers, vertex attribute pointers,
 * uniforms and the texture unit are kept on the Java side and translated into the native
 * host-visible buffers at draw time. This is a translation layer, not a full GL emulation, and
 * these are the limits that remain:</p>
 *
 * <ul>
 *   <li>one texture unit and one sampler binding, so shaders using two samplers cannot render
 *       correctly yet (7 of the 33 game shaders do);</li>
 *   <li>compressed textures and {@code glCopyTexImage2D}/{@code glCopyTexSubImage2D} are refused
 *       with a warning instead of silently producing empty textures;</li>
 *   <li>{@code glGenerateMipmap} keeps level 0 and honours mip filters as a linear filter, so no
 *       mip chain exists on the Vulkan side;</li>
 *   <li>{@code glVertexAttrib[1-4]f[v]} (constant vertex attributes), {@code glPolygonOffset},
 *       {@code glSampleCoverage}, {@code glHint}, {@code glValidateProgram} and the shader
 *       compiler housekeeping calls are accepted and ignored: no Arc rendering class in this
 *       project calls them (they only appear in Arc's own {@code Gl} facade and interface);</li>
 *   <li>the Vulkan device is created offscreen; windowed presentation is not wired here.</li>
 * </ul>
 */



public class VulkanGL20 implements GL20{

    private final VulkanGLState state = new VulkanGLState();

    private float clearR = 0f, clearG = 0f, clearB = 0f, clearA = 1f;
    private int boundFramebuffer = 0;
    private int boundRenderbuffer = 0;
    /** Active texture unit (GL_TEXTURE0..): Arc binds fonts and atlases on different units. */
    private int activeUnit = 0;
    /** Texture bound per unit, indexed by unit number. */
    private final Map<Integer, Integer> unitTextures = new HashMap<>();

    private float clearDepth = 1f;

    private int clearStencil = 0;

    private float blendR, blendG, blendB, blendA = 1f;

    private float pipelineLineWidth = 1f;

    private int viewportX, viewportY, viewportWidth, viewportHeight;

    private int scissorX, scissorY, scissorWidth, scissorHeight;

    private int pixelAlignment = 4, pixelRowLength = 0;

    private float depthNear = 0f, depthFar = 1f;

    private int activeProgram = 0;

    private int boundArrayBuffer = 0;

    private int boundElementArrayBuffer = 0;

    private int boundTexture2D = 0;


    private int nextShaderId = 1;

    private int nextProgramId = 1;

    private int nextBufferId = 1;

    private int nextTextureId = 1;

    private int nextFramebufferId = 1;

    private int nextRenderbufferId = 1;

    private final Map<Integer, ShaderData> shaders = new HashMap<>();

    private final Map<Integer, ProgramData> programs = new HashMap<>();

    private final Map<Integer, BufferData> buffers = new HashMap<>();

    private final Map<Integer, TextureData> textures = new HashMap<>();

    private final Map<Integer, FramebufferData> framebuffers = new HashMap<>();

    private final Map<Integer, RenderbufferData> renderbuffers = new HashMap<>();

/** Texture id -> framebuffer id, for textures attached with glFramebufferTexture2D. */
private final Map<Integer, Integer> textureAttachments = new HashMap<>();
/** Framebuffer id -> native device handle created by {@link VulkanDevice#createFramebuffer}. */
private final Map<Integer, Integer> deviceFramebuffer = new HashMap<>();
    private final AttributeData[] attributes = new AttributeData[16];

    /**
     * One implicit buffer per attribute slot. Arc points attributes at a client-side
     * java.nio Buffer instead of a bound VBO, and those bytes have to live somewhere the draw
     * path can reach, because it reads vertices from BufferData. A slot per attribute keeps the
     * buffer map bounded: re-pointing an attribute in the next frame reuses the same buffer.
     */
    private final int[] implicitBuffers = new int[16];
    /** Counts down to the next periodic draw report; diagnostics only. */
    private int targetDebug = 500;

/** Pipeline key currently resident in the native device, or null. */
private VulkanPipelineState activePipeline;
/** Bind target the resident pipeline was created for: a pass change forces a rebuild. */
private int activePipelineTarget;
    /** Id of the texture currently uploaded to the native device, or 0. */     private int uploadedTexture;

    public VulkanGL20(){

        for(int i = 0;

i < attributes.length;

i++){

            attributes[i] = new AttributeData();

        }

    }

    public static class ShaderData{

        public final int type;

        public String source;

        public boolean compiled;

        public ShaderData(int type){

            this.type = type;

        }

    }

    public static class ProgramData{

        public int vertexShader;

        public int fragmentShader;

        public boolean linked;

        public byte[] vertexSpirv;

        public byte[] fragmentSpirv;

        public GLSLProgram.Result translated;

        public final Map<String, Integer> attributeLocations = new HashMap<>();

        public final Map<String, Integer> boundAttributes = new HashMap<>();

        public final Map<String, Integer> uniformLocations = new HashMap<>();

        public final Map<Integer, String> locationNames = new HashMap<>();

        public final Map<String, float[]> floatValues = new HashMap<>();

        public final Map<String, int[]> intValues = new HashMap<>();

        public final Set<String> uniformNames = new HashSet<>();

        public ByteBuffer uniformBuffer;

        private int nextUniformLocation = 1;

        public int getLocation(String rawName){

            String name = normalizeUniformName(rawName);

if(uniformNames != null && !uniformNames.isEmpty() && !uniformNames.contains(name)
&& !isSampler(name)) return -1;
            Integer existing = uniformLocations.get(name);

            if(existing != null) return existing;

            int location = nextUniformLocation++;

            uniformLocations.put(name, location);

            locationNames.put(location, name);

            return location;

        }

        private boolean isSampler(String name){

            return translated != null && translated.samplerBindings.containsKey(name);

        }

    }

    public static class BufferData{

        public byte[] bytes = new byte[0];

        public int size;

        public int usage;

    }

    public static class TextureData{

        public int width, height;

        public byte[] rgba;

        public int version;

        public int uploadedVersion = -1;
        /** Sampler state requested through glTexParameteri: filter and wrap modes. */
        public int minFilter = GL20.GL_NEAREST, magFilter = GL20.GL_LINEAR;
        public int wrapS = GL20.GL_CLAMP_TO_EDGE, wrapT = GL20.GL_CLAMP_TO_EDGE;
        public boolean mipmapped = false;

    }

    /** One framebuffer object: a colour attachment and an optional depth(-stencil) attachment. */

public static class FramebufferData{

        public int colorTexture, colorRenderbuffer;

        public int depthRenderbuffer;

        public boolean depth, stencil;

        public boolean hasColor(){

            return colorTexture != 0 || colorRenderbuffer != 0;

        }

    }

    public static class RenderbufferData{

        public int format;

        public int width, height;

    }

    private static class AttributeData{

        boolean enabled;

        int size = 4;

        /**
         * Components the client buffer actually carries. OpenGL lets a shader declare more
         * components than the buffer provides and pads the rest with zeros and a final 1.0;
         * Vulkan pads with zeros alone, which breaks w. {@link #size} is widened to what the
         * shader wants, and this keeps the true source width for reading and for the source
         * stride.
         */
        int sourceComponents;

        int type = GL20.GL_FLOAT;

        int stride;

        int offset;

        int buffer;

        boolean normalized;

    }

    public VulkanGLState getState(){

        return state;

    }

/**
* Test hook: packs the currently enabled attributes into the interleaved float layout the
* native pipeline expects, without touching the Vulkan device. Exposed package-private so the
* pure translation logic can be verified on hosts where no Vulkan driver is available.
*/
float[] packForTest(int first, int count){
        VertexLayout layout = buildLayout();

        if(layout == null || count <= 0) return null;

        return packVertices(layout, first, count, layout.stride);

    }

/** Test hook: {@code location, components, byteOffset} triples plus stride, as sent to native. */
int[] layoutForTest(){
        VertexLayout layout = buildLayout();

        if(layout == null) return null;

        int[] out = new int[layout.encoded.length + 1];

        out[0] = layout.stride;

        System.arraycopy(layout.encoded, 0, out, 1, layout.encoded.length);

        return out;

    }


@Override
    public void glClearColor(float red, float green, float blue, float alpha){

        this.clearR = red;

        this.clearG = green;

        this.clearB = blue;

        this.clearA = alpha;

        VulkanDevice.clearColor(red, green, blue, alpha);

    }


@Override
    public void glClear(int mask){

        if(!VulkanDevice.created()) return;

        // A clear is a render pass with a clear value and no geometry, on whatever target is
        // bound. The windowed device used to answer this with clearAndPresent - the smoke-test
        // path - which presented an empty frame and left the rendered target untouched, so every
        // frame presented uninitialised pixels instead of the cleared colour.
        if(!VulkanDevice.render(0, true)){
            Log.warn("[VulkanGL20] clear refused: @", VulkanDevice.lastError());
        }

    }

@Override

    public void glViewport(int x, int y, int width, int height){

        this.viewportX = x;

        this.viewportY = y;

        this.viewportWidth = width;

        this.viewportHeight = height;
        // A framebuffer of another size needs its own viewport, otherwise geometry is drawn
        // against the default offscreen dimensions.

        VulkanDevice.setViewport(x, y, width, height);

    }

    public void glEnable(int cap){

        state.enable(cap);

    }


@Override
    public void glDisable(int cap){

        state.disable(cap);

    }


@Override
    public void glBlendFunc(int sfactor, int dfactor){

        state.blendFunc(sfactor, dfactor);

    }


@Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha){

        state.blendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);

    }


@Override
    public void glBlendEquation(int mode){

        state.blendEquation(mode);

    }


@Override
    public void glBlendEquationSeparate(int modeRGB, int modeAlpha){

        state.blendEquation(modeRGB);

    }


@Override
    public void glDepthMask(boolean flag){

        state.depthMask(flag);

    }


@Override
    public void glDepthFunc(int func){

        state.depthFunc(func);

    }


@Override
    public void glCullFace(int mode){

        state.cullFace(mode);

    }


@Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha){

        state.colorMask(red, green, blue, alpha);

    }


@Override
    public void glFrontFace(int mode){

        state.frontFace(mode);

    }


@Override
    public void glLineWidth(float width){

        state.lineWidth(width);

    }


@Override
    public void glPolygonOffset(float factor, float units){

    }


@Override

    public void glScissor(int x, int y, int width, int height){

        this.scissorX = x;

        this.scissorY = y;

        this.scissorWidth = width;

        this.scissorHeight = height;

        VulkanDevice.setScissor(x, y, width, height);

    }

    public void glPixelStorei(int pname, int param)

{

    }


@Override
    public void glHint(int target, int mode){

    }


@Override
    public void glSampleCoverage(float value, boolean invert){

    }


@Override
    public int glCreateShader(int type){

        int id = nextShaderId++;

        shaders.put(id, new ShaderData(type));

        return id;

    }


@Override
    public void glShaderSource(int shader, String string){

        ShaderData data = shaders.get(shader);

        if(data != null) data.source = string;

    }


@Override
    public void glCompileShader(int shader){

        ShaderData data = shaders.get(shader);

        if(data != null && data.source != null){

            data.compiled = true;

        }

    }


@Override
    public void glGetShaderiv(int shader, int pname, IntBuffer params){

        ShaderData data = shaders.get(shader);

        if(pname == GL20.GL_COMPILE_STATUS){

            params.put(0, data != null && data.compiled ? GL20.GL_TRUE : GL20.GL_FALSE);

        }

        else if(pname == GL20.GL_SHADER_TYPE){

            params.put(0, data == null ? 0 : data.type);

        }

    }


@Override
    public String glGetShaderInfoLog(int shader){

        return "";

    }


@Override
    public void glReleaseShaderCompiler(){

    }


@Override
    public void glDeleteShader(int shader){

        shaders.remove(shader);

    }


@Override
    public int glCreateProgram(){

        int id = nextProgramId++;

        programs.put(id, new ProgramData());

        return id;

    }


@Override
    public void glAttachShader(int program, int shader){

        ProgramData pData = programs.get(program);

        ShaderData sData = shaders.get(shader);

        if(pData == null || sData == null) return;

        if(sData.type == GL20.GL_VERTEX_SHADER) pData.vertexShader = shader;

        else if(sData.type == GL20.GL_FRAGMENT_SHADER) pData.fragmentShader = shader;

    }


@Override
    public void glBindAttribLocation(int program, int index, String name){

        ProgramData pData = programs.get(program);

        if(pData != null) pData.boundAttributes.put(name, index);

    }


@Override
    public void glLinkProgram(int program){

        ProgramData pData = programs.get(program);

        if(pData == null) return;

        ShaderData vert = shaders.get(pData.vertexShader);

        ShaderData frag = shaders.get(pData.fragmentShader);

        pData.linked = false;

        if(vert == null || frag == null || vert.source == null || frag.source == null) return;

        try{

            GLSLProgram.Result translated = GLSLProgram.translate(vert.source, frag.source, pData.boundAttributes);

            byte[] vertexSpirv = SpirvCompiler.compile(translated.vertexSource, SpirvCompiler.Stage.VERTEX);

            byte[] fragmentSpirv = SpirvCompiler.compile(translated.fragmentSource, SpirvCompiler.Stage.FRAGMENT);

            if(vertexSpirv == null || fragmentSpirv == null) return;

            pData.translated = translated;

            pData.vertexSpirv = vertexSpirv;

            pData.fragmentSpirv = fragmentSpirv;

            pData.attributeLocations.clear();

            pData.attributeLocations.putAll(translated.attributeLocations);

            pData.uniformNames.clear();

pData.uniformNames.addAll(translated.uniformLayout.members().stream()
.map(member -> member.name).toList());
            pData.uniformNames.addAll(translated.samplerBindings.keySet());

            if(translated.uniformLayout.size() > 0){

                pData.uniformBuffer = translated.uniformLayout.createBuffer();

            }

            else{

                pData.uniformBuffer = null;

            }

            pData.linked = true;

            activePipeline = null;

        }

        catch(Throwable error){

            Log.warn("[VulkanGL20] Link failed for program @: @", program, error.getMessage());

            pData.linked = false;

        }

    }


@Override
    public void glGetProgramiv(int program, int pname, IntBuffer params){

        ProgramData data = programs.get(program);

        if(pname == GL20.GL_LINK_STATUS){

            params.put(0, data != null && data.linked ? GL20.GL_TRUE : GL20.GL_FALSE);

        }

        else if(pname == GL20.GL_VALIDATE_STATUS){

            params.put(0, data != null && data.linked ? GL20.GL_TRUE : GL20.GL_FALSE);

        }

    }


@Override
    public String glGetProgramInfoLog(int program){

        return "";

    }


@Override
    public void glValidateProgram(int program){

    }


@Override
    public void glDetachShader(int program, int shader){

    }


@Override
    public void glDeleteProgram(int program){

        programs.remove(program);

        activePipeline = null;

    }


@Override
    public void glUseProgram(int program){

        this.activeProgram = program;

        ProgramData pData = programs.get(program);

        if(pData != null && pData.linked){

            state.program(pData.vertexShader, pData.fragmentShader);

        }

        activePipeline = null;

    }


@Override
    public int glGetAttribLocation(int program, String name){

        ProgramData data = programs.get(program);

        if(data == null || !data.linked) return -1;

        Integer location = data.attributeLocations.get(name);

        return location == null ? -1 : location;

    }


@Override
    public int glGetUniformLocation(int program, String name){

        ProgramData data = programs.get(program);

        return data != null && data.linked ? data.getLocation(name) : -1;

    }


@Override
    public String glGetActiveAttrib(int program, int index, IntBuffer size, IntBuffer type){

        return "";

    }


@Override
    public String glGetActiveUniform(int program, int index, IntBuffer size, IntBuffer type){

        return "";

    }


@Override
    public int glGenBuffer(){

        int id = nextBufferId++;

        buffers.put(id, new BufferData());

        return id;

    }


@Override
    public void glBindBuffer(int target, int buffer){

        if(target == GL20.GL_ARRAY_BUFFER) boundArrayBuffer = buffer;

        else if(target == GL20.GL_ELEMENT_ARRAY_BUFFER) boundElementArrayBuffer = buffer;

    }


@Override
    public void glBufferData(int target, int size, Buffer data, int usage){

        int id = target == GL20.GL_ELEMENT_ARRAY_BUFFER ? boundElementArrayBuffer : boundArrayBuffer;

        BufferData buffer = buffers.get(id);

        if(buffer == null && id != 0){

            buffer = new BufferData();

            buffers.put(id, buffer);

        }

        if(buffer == null) return;

        byte[] bytes = bytesOf(data);

        buffer.size = Math.max(size, 0);

        buffer.usage = usage;

        if(bytes.length == buffer.size){

            buffer.bytes = bytes;

        }

        else{

            buffer.bytes = new byte[buffer.size];

            System.arraycopy(bytes, 0, buffer.bytes, 0, Math.min(bytes.length, buffer.size));

        }

    }


@Override
    public void glBufferSubData(int target, int offset, int size, Buffer data){

        int id = target == GL20.GL_ELEMENT_ARRAY_BUFFER ? boundElementArrayBuffer : boundArrayBuffer;

        BufferData buffer = buffers.get(id);

        if(buffer == null || offset < 0) return;

        byte[] bytes = bytesOf(data);

        int length = Math.min(Math.min(size, bytes.length), buffer.bytes.length - offset);

        if(length > 0) System.arraycopy(bytes, 0, buffer.bytes, offset, length);

    }


@Override
    public void glDeleteBuffer(int buffer){

        buffers.remove(buffer);

        if(boundArrayBuffer == buffer) boundArrayBuffer = 0;

        if(boundElementArrayBuffer == buffer) boundElementArrayBuffer = 0;

    }


@Override
    public boolean glIsBuffer(int buffer){

        return buffers.containsKey(buffer);

    }


@Override
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int offset){

        if(indx < 0 || indx >= attributes.length) return;

        AttributeData attribute = attributes[indx];

        attribute.enabled = true;

        attribute.size = Math.max(1, Math.min(4, size));

        attribute.type = type;

        attribute.normalized = normalized;

        attribute.stride = Math.max(stride, 0);

        attribute.offset = Math.max(offset, 0);

        attribute.buffer = boundArrayBuffer;

        activePipeline = null;

    }


@Override
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, Buffer ptr){

        if(indx < 0 || indx >= attributes.length) return;

        AttributeData attribute = attributes[indx];

        attribute.enabled = true;
        attribute.size = Math.max(1, Math.min(4, size));
        attribute.type = type;
        attribute.normalized = normalized;
        attribute.stride = Math.max(stride, 0);

        if(ptr != null && boundArrayBuffer == 0){
            // Client-side data: copy what remains of the buffer into this slot's implicit
            // buffer. Those bytes already start at ptr.position(), so the offset is zero.
            attribute.buffer = implicitBufferFor(indx, ptr);
            attribute.offset = 0;
        }else{
            attribute.buffer = boundArrayBuffer;
            attribute.offset = ptr == null ? 0 : ptr.position() * componentBytes(type);
        }

        activePipeline = null;
    }

    /**
     * Copies a client-side buffer into this attribute slot's implicit buffer and returns its
     * id. The slot is reused on every call, so re-pointing an attribute does not grow the
     * buffer map frame after frame.
     */
    private int implicitBufferFor(int slot, Buffer ptr){
        int id = implicitBuffers[slot];
        if(id == 0){
            id = nextBufferId++;
            implicitBuffers[slot] = id;
        }

        BufferData buffer = buffers.get(id);
        if(buffer == null){
            buffer = new BufferData();
            buffers.put(id, buffer);
        }

        buffer.bytes = bytesOf(ptr);
        buffer.size = buffer.bytes.length;
        return id;
    }


@Override
    public void glEnableVertexAttribArray(int index){

        if(index >= 0 && index < attributes.length){

            attributes[index].enabled = true;

            activePipeline = null;

        }

    }


@Override
    public void glDisableVertexAttribArray(int index){

        if(index >= 0 && index < attributes.length){

            attributes[index].enabled = false;

            activePipeline = null;

        }

    }


@Override
    public void glVertexAttrib1f(int indx, float x){

    }


@Override
    public void glVertexAttrib1fv(int indx, FloatBuffer values){

    }


@Override
    public void glVertexAttrib2f(int indx, float x, float y){

    }


@Override
    public void glVertexAttrib2fv(int indx, FloatBuffer values){

    }


@Override
    public void glVertexAttrib3f(int indx, float x, float y, float z){

    }


@Override
    public void glVertexAttrib3fv(int indx, FloatBuffer values){

    }


@Override
    public void glVertexAttrib4f(int indx, float x, float y, float z, float w){

    }


@Override
    public void glVertexAttrib4fv(int indx, FloatBuffer values){

    }


@Override
    public int glGenTexture(){

        int id = nextTextureId++;

        textures.put(id, new TextureData());

        return id;

    }


@Override
    public void glBindTexture(int target, int texture){

        if(target == GL20.GL_TEXTURE_2D) boundTexture2D = texture;

    }


@Override
public void glTexImage2D(int target, int level, int internalformat, int width, int height,
int border, int format, int type, Buffer pixels){
        if(target != GL20.GL_TEXTURE_2D || level != 0) return;

        TextureData texture = textures.get(boundTexture2D);

        if(texture == null){

            texture = new TextureData();

            textures.put(boundTexture2D, texture);

        }

        byte[] rgba = rgbaBytes(pixels, width, height, format, type);

        texture.width = width;

        texture.height = height;

        texture.rgba = rgba;

        texture.version++;

        if(uploadedTexture == boundTexture2D) uploadedTexture = 0;

    }


@Override
public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
int format, int type, Buffer pixels){
        TextureData texture = textures.get(boundTexture2D);

        if(texture == null || texture.rgba == null || level != 0) return;

        byte[] source = rgbaBytes(pixels, width, height, format, type);

        for(int row = 0; row < height; row++){

            int sourceRow = row * width * 4;

            int destRow = ((yoffset + row) * texture.width + xoffset) * 4;

            int length = Math.min(width * 4, texture.rgba.length - destRow);

            if(length > 0) System.arraycopy(source, sourceRow, texture.rgba, destRow, length);

        }

        texture.version++;

        if(uploadedTexture == boundTexture2D) uploadedTexture = 0;

    }


@Override
    public void glDeleteTexture(int texture){

        textures.remove(texture);

        if(boundTexture2D == texture) boundTexture2D = 0;

        if(uploadedTexture == texture) uploadedTexture = 0;

    }


@Override
    public boolean glIsTexture(int texture){

        return textures.containsKey(texture);

    }
    @Override
    public void glTexParameterf(int target, int pname, float param){
        glTexParameteri(target, pname, Math.round(param));
    }
    @Override
    public void glTexParameterfv(int target, int pname, FloatBuffer params){
        if(params != null && params.remaining() > 0) glTexParameteri(target, pname, Math.round(params.get(0)));
    }
    @Override
    public void glTexParameteri(int target, int pname, int param){
        TextureData texture = textures.get(boundTexture2D);
        if(texture == null) return;
        switch(pname){
            case GL20.GL_TEXTURE_MIN_FILTER -> texture.minFilter = param;
            case GL20.GL_TEXTURE_MAG_FILTER -> texture.magFilter = param;
            case GL20.GL_TEXTURE_WRAP_S -> texture.wrapS = param;
            case GL20.GL_TEXTURE_WRAP_T -> texture.wrapT = param;
            default -> { }
        }
        texture.version++;
        if(uploadedTexture == boundTexture2D) uploadedTexture = 0;
    }
    @Override
    public void glTexParameteriv(int target, int pname, IntBuffer params){
        if(params != null && params.remaining() > 0) glTexParameteri(target, pname, params.get(0));
    }
    @Override
    public void glGenerateMipmap(int target){
        TextureData texture = textures.get(boundTexture2D);
        if(texture == null) return;
        //Mip levels are not generated on the GPU; the texture keeps level 0 and the sampler honours
        //the mip filters as a plain linear filter.
        texture.mipmapped = true;
        texture.version++;
        if(uploadedTexture == boundTexture2D) uploadedTexture = 0;
    }
@Override

    public void glActiveTexture(int texture){

        int unit = texture - GL20.GL_TEXTURE0;

        if(unit < 0) unit = 0;

        if(unit != activeUnit){
        // Remember the texture bound on the unit being left, then restore the new one.

            unitTextures.put(activeUnit, boundTexture2D);

            activeUnit = unit;

            boundTexture2D = unitTextures.getOrDefault(unit, 0);

        }

    }

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height,

                                        int border, int imageSize, Buffer data){

        Log.warn("[VulkanGL20] glCompressedTexImage2D is unsupported; the texture stays empty");

    }

    @Override
    public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width,

                                          int height, int format, int imageSize, Buffer data){

        Log.warn("[VulkanGL20] glCompressedTexSubImage2D is unsupported");

    }

    @Override
    public void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width,

                                 int height, int border){

        Log.warn("[VulkanGL20] glCopyTexImage2D is unsupported");

    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y,

                                    int width, int height){

        Log.warn("[VulkanGL20] glCopyTexSubImage2D is unsupported");

    }

    public void glUniform1f(int location, float x){ putFloats(location, new float[]{x}); }



@Override
    public void glUniform1fv(int location, int count, FloatBuffer v){ putFloats(location, readFloats(v, count)); }



@Override
public void glUniform1fv(int location, int count, float[] v, int offset){ putFloats(location, slice(v, offset, count)); }



@Override
    public void glUniform1i(int location, int x){ putInts(location, new int[]{x}); }



@Override
    public void glUniform1iv(int location, int count, IntBuffer v){ putInts(location, readInts(v, count)); }



@Override
public void glUniform1iv(int location, int count, int[] v, int offset){ putInts(location, slice(v, offset, count)); }



@Override
    public void glUniform2f(int location, float x, float y){ putFloats(location, new float[]{x, y}); }



@Override
    public void glUniform2fv(int location, int count, FloatBuffer v){ putFloats(location, readFloats(v, count * 2)); }



@Override
public void glUniform2fv(int location, int count, float[] v, int offset){ putFloats(location, slice(v, offset, count * 2)); }



@Override
    public void glUniform2i(int location, int x, int y){ putInts(location, new int[]{x, y}); }



@Override
    public void glUniform2iv(int location, int count, IntBuffer v){ putInts(location, readInts(v, count * 2)); }



@Override
public void glUniform2iv(int location, int count, int[] v, int offset){ putInts(location, slice(v, offset, count * 2)); }



@Override
    public void glUniform3f(int location, float x, float y, float z){ putFloats(location, new float[]{x, y, z}); }



@Override
    public void glUniform3fv(int location, int count, FloatBuffer v){ putFloats(location, readFloats(v, count * 3)); }



@Override
public void glUniform3fv(int location, int count, float[] v, int offset){ putFloats(location, slice(v, offset, count * 3)); }



@Override
    public void glUniform3i(int location, int x, int y, int z){ putInts(location, new int[]{x, y, z}); }



@Override
    public void glUniform3iv(int location, int count, IntBuffer v){ putInts(location, readInts(v, count * 3)); }



@Override
public void glUniform3iv(int location, int count, int[] v, int offset){ putInts(location, slice(v, offset, count * 3)); }



@Override
public void glUniform4f(int location, float x, float y, float z, float w){ putFloats(location, new float[]{x, y, z, w}); }



@Override
    public void glUniform4fv(int location, int count, FloatBuffer v){ putFloats(location, readFloats(v, count * 4)); }



@Override
public void glUniform4fv(int location, int count, float[] v, int offset){ putFloats(location, slice(v, offset, count * 4)); }



@Override
    public void glUniform4i(int location, int x, int y, int z, int w){ putInts(location, new int[]{x, y, z, w}); }



@Override
    public void glUniform4iv(int location, int count, IntBuffer v){ putInts(location, readInts(v, count * 4)); }



@Override
public void glUniform4iv(int location, int count, int[] v, int offset){ putInts(location, slice(v, offset, count * 4)); }



@Override
public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value){
putFloats(location, readFloats(value, count * 4));
}



@Override
public void glUniformMatrix2fv(int location, int count, boolean transpose, float[] value, int offset){
putFloats(location, slice(value, offset, count * 4));
}



@Override
public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value){
putFloats(location, readFloats(value, count * 9));
}



@Override
public void glUniformMatrix3fv(int location, int count, boolean transpose, float[] value, int offset){
putFloats(location, slice(value, offset, count * 9));
}



@Override
public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value){
putFloats(location, readFloats(value, count * 16));
}



@Override
public void glUniformMatrix4fv(int location, int count, boolean transpose, float[] value, int offset){
putFloats(location, slice(value, offset, count * 16));
}
    @Override
    public void glGetUniformfv(int program, int location, FloatBuffer params){
        ProgramData data = programs.get(program);
        String name = data == null ? null : data.locationNames.get(location);
        if(name == null || params == null) return;
        float[] values = data.floatValues.get(name);
        if(values == null) return;
        for(int i = 0; i < values.length && i < params.remaining(); i++) params.put(values[i]);
    }
    @Override
    public void glGetUniformiv(int program, int location, IntBuffer params){
        ProgramData data = programs.get(program);
        String name = data == null ? null : data.locationNames.get(location);
        if(name == null || params == null) return;
        UniformBlockLayout layout = data.translated == null ? null : data.translated.uniformLayout;
        if(layout == null) return;
        for(UniformBlockLayout.Member member : layout.members()){
            if(!member.name.equals(name)) continue;
            if(params.remaining() > 0) params.put(componentsOf(member.type) * Math.max(member.arrayLength, 1));
            return;
        }
    }
@Override
public void glDrawArrays(int mode, int first, int count){
if(count <= 0) return;
draw(mode, first, count, null, -1, 0);
}



@Override
public void glDrawElements(int mode, int count, int type, Buffer indices){
if(count <= 0) return;
int[] source = indices == null ? null : readIndices(indices, count, type);
if(source == null) return;
draw(mode, 0, -1, source, -1, count);
}



@Override
    public void glDrawElements(int mode, int count, int type, int offset){

if(count <= 0) return;

draw(mode, 0, -1, null, offset, count);

}

/**
* Common draw path. { @code count < 0 } means "all vertices in the bound array buffer"; * { @code explicitIndices != null } uses a CPU index array, otherwise the bound element buffer
* is read at { @code indexOffset } .
*/
/** Diagnostics only: why draw calls stop before reaching the device. */
    static long drawCalls, drawNoProgram, drawNoLayout, drawNoVertices, drawNoPack,
        drawNoIndex, drawPipelineFailure, drawSubmitFailure, drawSubmitted;

    public static void reportDrawReasons(){
        if(drawCalls == 0) return;
        Log.info("[VulkanGL20] draws: @ entered, noProgram=@ noLayout=@ noVertices=@ noPack=@ "
                + "noIndex=@ pipelineFailed=@ submitFailed=@ submitted=@",
            drawCalls, drawNoProgram, drawNoLayout, drawNoVertices, drawNoPack,
            drawNoIndex, drawPipelineFailure, drawSubmitFailure, drawSubmitted);
    }

private void draw(int mode, int first, int count, int[] explicitIndices, int indexOffset, int indexCount){
    drawCalls++;
    ProgramData program = programs.get(activeProgram);

    if(program == null || !program.linked || program.vertexSpirv == null || program.fragmentSpirv == null){
            drawNoProgram++;
            Log.warn("[VulkanGL20] draw stopped: no program (active=@ linked=@)", activeProgram, program != null && program.linked);
            return;
        }

VertexLayout layout = buildLayout();

if(layout == null || layout.attributes.isEmpty()){
            drawNoLayout++;
            Log.warn("[VulkanGL20] draw stopped: no vertex layout");
            return;
        }

int stride = layout.stride;

int vertexCount = count >= 0 ? count : availableVertices(layout, stride);

if(vertexCount <= 0){
            drawNoVertices++;
            Log.warn("[VulkanGL20] draw stopped: no vertices (count=@ first=@ mode=@)", count, first, mode);
            return;
        }

float[] vertexData = packVertices(layout, first, vertexCount, stride);

if(vertexData == null){
            drawNoPack++;
            Log.warn("[VulkanGL20] draw stopped: vertices could not be packed");
            return;
        }

int[] indexData = null;

if(explicitIndices != null){

indexData = explicitIndices;

}

else if(indexOffset >= 0){

indexData = readIndexBuffer(indexOffset, indexCount);

if(indexData == null){
                drawNoIndex++;
                Log.warn("[VulkanGL20] index buffer unreadable (offset @, count @)", indexOffset, indexCount);
                return;
            }

}

state.primitive(mode);

state.vertexLayout(layout.hash);

state.framebuffer(boundFramebuffer, 0);

VulkanPipelineState snapshot = state.snapshot();

if(!snapshot.equals(activePipeline) || activePipelineTarget != boundFramebuffer){

if(!VulkanDevice.pipelineState(program.vertexSpirv, program.fragmentSpirv,
mode, stride, layout.encoded, pipelineState(snapshot))){
Log.warn("[VulkanGL20] Pipeline creation failed: @", VulkanDevice.lastError());

activePipeline = null;

return;

}

activePipeline = snapshot;

activePipelineTarget = boundFramebuffer;

}

uploadTexture();

uploadUniforms(program);

if(targetDebug-- <= 0){
            targetDebug = 500;
            Log.info("[VulkanGL20] draw: fb=@ target @x@ stride=@ verts=@ mode=@ pipeline=@",
                boundFramebuffer, VulkanDevice.boundWidth(), VulkanDevice.boundHeight(), stride,
                vertexCount, mode, snapshot);
        }

        if(!VulkanDevice.vertices(vertexData)){
            drawSubmitFailure++;
            Log.warn("[VulkanGL20] vertex upload refused (@ vertices): @", vertexCount, VulkanDevice.lastError());
            return;
        }

if(indexData != null){

if(!VulkanDevice.indices(indexData)){
                drawNoIndex++;
                Log.warn("[VulkanGL20] index upload refused (@ indices): @", indexData.length, VulkanDevice.lastError());
                return;
            }

if(VulkanDevice.renderIndexed(indexData.length, false)){

drawSubmitted++;

}

}

else if(VulkanDevice.render(vertexCount, false)){

drawSubmitted++;

}

}

/**
* Applies the viewport of the bound target: the framebuffer extent, or the offscreen size.
* A framebuffer of another size must not be drawn with the previous viewport.
*/
private void applyTargetViewport(){
if(boundFramebuffer == 0 || !VulkanDevice.created()){

VulkanDevice.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);

return;

}

FramebufferData data = framebuffers.get(boundFramebuffer);

int width = data != null ? textureOrRenderbufferSize(data.colorTexture, data.colorRenderbuffer, true) : 0;

int height = data != null ? textureOrRenderbufferSize(data.colorTexture, data.colorRenderbuffer, false) : 0;

if(width <= 0 || height <= 0){

width = VulkanDevice.boundWidth();

height = VulkanDevice.boundHeight();

}

VulkanDevice.setViewport(0, 0, width, height);

VulkanDevice.setScissor(0, 0, width, height);

}

private int textureOrRenderbufferSize(int texture, int renderbuffer, boolean width){

TextureData data = textures.get(texture);

if(data != null && data.width > 0) return width ? data.width : data.height;

RenderbufferData buffer = renderbuffers.get(renderbuffer);

if(buffer != null && buffer.width > 0) return width ? buffer.width : buffer.height;

return 0;

}

/**
* Encodes the pipeline state in the order { @link VulkanDevice.PipelineStateIndices } documents.
* The camera depth range is the one Vulkan always uses (0..1) after mapping.
*/
/** GL cull face enum to the compact code the native side documents: 0 none, 1 back, 2 front, 3 both. */
private static int cullFaceCode(int glCullFace){
return switch(glCullFace){

case GL20.GL_BACK -> 1;

case GL20.GL_FRONT -> 2;

case GL20.GL_FRONT_AND_BACK -> 3;

default -> 0;

}

;

}

private int[] pipelineState(VulkanPipelineState snapshot){

int[] state = new int[VulkanDevice.PipelineStateIndices.length];

state[VulkanDevice.PipelineStateIndices.depthTest] = snapshot.depthTest ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.depthWrite] = snapshot.depthWrite ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.depthFunc] = snapshot.depthFunc;

state[VulkanDevice.PipelineStateIndices.stencilTest] = snapshot.stencilTest;

state[VulkanDevice.PipelineStateIndices.stencilFunc] = snapshot.stencilFunc;

state[VulkanDevice.PipelineStateIndices.stencilRef] = snapshot.stencilRef;

state[VulkanDevice.PipelineStateIndices.stencilMask] = snapshot.stencilMask;

state[VulkanDevice.PipelineStateIndices.stencilFail] = snapshot.stencilFail;

state[VulkanDevice.PipelineStateIndices.stencilDepthFail] = snapshot.stencilDepthFail;

state[VulkanDevice.PipelineStateIndices.stencilDepthPass] = snapshot.stencilDepthPass;

//Cull face and front face travel as compact codes, not GL enums.
state[VulkanDevice.PipelineStateIndices.cullFace] = snapshot.cullEnabled ? cullFaceCode(snapshot.cullFace) : 0;
state[VulkanDevice.PipelineStateIndices.frontFaceCCW] = snapshot.frontFace == GL20.GL_CCW ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.blend] = snapshot.blendEnabled ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.blendSrcRGB] = snapshot.blendSrcRGB;

state[VulkanDevice.PipelineStateIndices.blendDstRGB] = snapshot.blendDstRGB;

state[VulkanDevice.PipelineStateIndices.blendSrcAlpha] = snapshot.blendSrcAlpha;

state[VulkanDevice.PipelineStateIndices.blendDstAlpha] = snapshot.blendDstAlpha;

state[VulkanDevice.PipelineStateIndices.blendEqRGB] = snapshot.blendEqRGB;

state[VulkanDevice.PipelineStateIndices.blendEqAlpha] = snapshot.blendEqAlpha;

state[VulkanDevice.PipelineStateIndices.colorMaskR] = snapshot.colorMaskR ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.colorMaskG] = snapshot.colorMaskG ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.colorMaskB] = snapshot.colorMaskB ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.colorMaskA] = snapshot.colorMaskA ? 1 : 0;

state[VulkanDevice.PipelineStateIndices.lineWidth] = Math.round(pipelineLineWidth * 1000f);

state[VulkanDevice.PipelineStateIndices.blendConstantR] = Math.round(blendR * 65535f);

state[VulkanDevice.PipelineStateIndices.blendConstantG] = Math.round(blendG * 65535f);

state[VulkanDevice.PipelineStateIndices.blendConstantB] = Math.round(blendB * 65535f);

state[VulkanDevice.PipelineStateIndices.blendConstantA] = Math.round(blendA * 65535f);

return state;

}

private static class VertexLayout{

final int stride;

final int hash;

final int[] encoded;

final List<AttributeData> attributes = new ArrayList<>();

VertexLayout(int stride, int[] encoded){

this.stride = stride;

this.encoded = encoded;

this.hash = Arrays.hashCode(encoded);

}

}

/**
 * Resolves enabled attributes into a packed interleaved layout.
 *
 * <p>An attribute is widened to the width its shader declares, because OpenGL pads a short
 * client attribute with zeros and a final 1.0 while Vulkan pads with zeros alone. Without that
 * widening a vec4 attribute fed by two floats arrives with w == 0, every vertex divides to
 * infinity and nothing reaches the framebuffer.</p>
 */
    private VertexLayout buildLayout(){
        return buildLayout(programs.get(activeProgram));
    }

    private VertexLayout buildLayout(ProgramData program){

List<AttributeData> active = new ArrayList<>();

for(AttributeData attribute : attributes){

if(attribute.enabled) active.add(attribute);

}

if(active.isEmpty()) return null;

        // Widen before anything reads size: stride, pipeline encoding and packVertices all do.
        Map<String, Integer> declared = program != null && program.translated != null
            ? program.translated.attributeComponents : Collections.emptyMap();

        for(AttributeData attribute : active){
            attribute.sourceComponents = attribute.size;

            Integer wanted = declared.get(attributeName(program, attribute));
            if(wanted != null && wanted > attribute.size) attribute.size = wanted;
        }

int packedOffset = 0;

for(AttributeData attribute : active){
        // The packed buffer is always float-based, so one component is 4 bytes regardless of
        // the GL source type.
        packedOffset += Float.BYTES * attribute.size;

}

int stride = packedOffset;
        // The native pipeline is fed a freshly packed float array, so the pipeline's offsets are
        // always the packed offsets - never the GL source offsets. Source strides/offsets only
        // matter while reading (see packVertices).
        int[] encoded = new int[active.size() * 3];

int packed = 0;

int cursor = 0;

for(AttributeData attribute : active){

encoded[cursor++] = indexOf(attribute);

encoded[cursor++] = attribute.size;

encoded[cursor++] = packed;

packed += Float.BYTES * attribute.size;

}

VertexLayout layout = new VertexLayout(stride, encoded);

layout.attributes.addAll(active);

return layout;

}

    /**
     * Name an attribute carries in the active shader, or null when the shader does not declare
     * it. Without the name there is nothing to widen towards.
     */
    private String attributeName(ProgramData program, AttributeData attribute){
        if(program == null) return null;

        int location = indexOf(attribute);

        // The shader pair owns the authoritative attribute names; glGetAttribLocation does.
        for(Map.Entry<String, Integer> entry : program.attributeLocations.entrySet()){
            if(entry.getValue() != null && entry.getValue() == location) return entry.getKey();
        }
        // An explicit glBindAttribLocation wins when it disagrees with the translation.
        for(Map.Entry<String, Integer> entry : program.boundAttributes.entrySet()){
            if(entry.getValue() != null && entry.getValue() == location) return entry.getKey();
        }
        return null;
    }

private int indexOf(AttributeData target){

for(int i = 0; i < attributes.length; i++){

if(attributes[i] == target) return i;

}

return 0;

}

/** Bytes one source vertex occupies in the buffer backing { @code attribute } . */
private static int sourceStride(AttributeData attribute){
return attribute.stride > 0 ? attribute.stride
            : attribute.sourceComponents * componentBytes(attribute.type);

}

private int availableVertices(VertexLayout layout, int stride){

if(stride <= 0) return 0;

int vertices = Integer.MAX_VALUE;

for(AttributeData attribute : layout.attributes){

BufferData buffer = buffers.get(attribute.buffer);

if(buffer == null) return 0;

vertices = Math.min(vertices, buffer.bytes.length / sourceStride(attribute));

}

if(vertices == Integer.MAX_VALUE){

BufferData buffer = buffers.get(boundArrayBuffer);

return buffer == null ? 0 : buffer.bytes.length / stride;

}

return vertices;

}

private float[] packVertices(VertexLayout layout, int first, int count, int stride){

int floatsPerVertex = 0;

for(AttributeData attribute : layout.attributes) floatsPerVertex += attribute.size;

float[] out = new float[floatsPerVertex * count];

Map<Integer, ByteBuffer> sources = new HashMap<>();

int outIndex = 0;

for(int vertex = first; vertex < first + count; vertex++){

for(AttributeData attribute : layout.attributes){

BufferData buffer = buffers.get(attribute.buffer);

if(buffer == null) return null;

ByteBuffer source = sources.computeIfAbsent(attribute.buffer,
id -> ByteBuffer.wrap(buffer.bytes).order(ByteOrder.LITTLE_ENDIAN));
int base = vertex * sourceStride(attribute) + attribute.offset;

for(int component = 0; component < attribute.size; component++){
                // OpenGL pads a short client attribute with zeros and a final 1.0; do the same,
                // because Vulkan would leave w at 0 and collapse every vertex to a point.
                out[outIndex++] = component < attribute.sourceComponents
                    ? readComponent(source, base, component, attribute.type, attribute.normalized)
                    : (component == 3 ? 1f : 0f);

}

}

}

return out;

}

private static float readComponent(ByteBuffer source, int base, int component, int type, boolean normalized){

int offset = base + component * componentBytes(type);

if(offset < 0 || offset + componentBytes(type) > source.capacity()) return 0f;

return switch(type){

case GL20.GL_FLOAT -> source.getFloat(offset);

case GL20.GL_UNSIGNED_BYTE -> {

int value = source.get(offset) & 0xff;

yield normalized ? value / 255f : value;

}

case GL20.GL_BYTE -> {

int value = source.get(offset);

yield normalized ? Math.max(value / 127f, -1f) : value;

}

case GL20.GL_UNSIGNED_SHORT -> {

int value = source.getShort(offset) & 0xffff;

yield normalized ? value / 65535f : value;

}

case GL20.GL_SHORT -> {

int value = source.getShort(offset);

yield normalized ? Math.max(value / 32767f, -1f) : value;

}

default -> 0f;

}

;

}

private int[] readIndexBuffer(int offset, int count){

BufferData buffer = buffers.get(boundElementArrayBuffer);

if(buffer == null || offset < 0 || offset >= buffer.bytes.length || count <= 0) return null;

ByteBuffer source = ByteBuffer.wrap(buffer.bytes).order(ByteOrder.LITTLE_ENDIAN);

int available = (buffer.bytes.length - offset) / 4;

int length = Math.min(count, available);

int[] indices = new int[length];

for(int i = 0; i < length; i++) indices[i] = source.getInt(offset + i * 4);

return indices;

}

private static int[] readIndices(Buffer indices, int count, int type){

ByteBuffer source = ByteBuffer.wrap(bytesOf(indices)).order(ByteOrder.LITTLE_ENDIAN);

int[] out = new int[count];

if(type == GL20.GL_UNSIGNED_BYTE){

for(int i = 0; i < count; i++) out[i] = source.get() & 0xff;

}

else if(type == GL20.GL_UNSIGNED_SHORT){

for(int i = 0; i < count; i++) out[i] = source.getShort() & 0xffff;

}

else{

for(int i = 0; i < count; i++) out[i] = source.getInt();

}

return out;

}

private void uploadUniforms(ProgramData program){
    UniformBlockLayout layout = program.translated == null ? null : program.translated.uniformLayout;

    if(layout == null || layout.size() == 0 || program.uniformBuffer == null) return;

ByteBuffer buffer = program.uniformBuffer;

for(int i = 0; i < buffer.capacity(); i++) buffer.put(i, (byte)0);

for(UniformBlockLayout.Member member : layout.members()){

float[] floats = program.floatValues.get(member.name);

int[] ints = program.intValues.get(member.name);

if(floats != null) writeMemberFloats(buffer, member, floats);

else if(ints != null) writeMemberInts(buffer, member, ints);

}

byte[] bytes = new byte[buffer.capacity()];

buffer.position(0);

buffer.get(bytes);

VulkanDevice.uniforms(bytes);

}

static void writeMemberFloats(ByteBuffer buffer, UniformBlockLayout.Member member, float[] values){

int elements = member.arrayLength > 0 ? member.arrayLength : 1;

int stride = member.arrayLength > 0 ? member.stride : 0;

int perElement = componentsOf(member.type);

for(int element = 0; element < elements; element++){

int base = member.offset + element * stride;

if(member.type.equals("mat3")){

for(int column = 0; column < 3; column++){

for(int component = 0; component < 3; component++){

int source = element * 9 + column * 3 + component;

if(source < values.length) buffer.putFloat(base + column * 16 + component * 4, values[source]);

}

}

}

else{

for(int component = 0; component < perElement; component++){

int source = element * perElement + component;

if(base + component * 4 + 4 <= buffer.capacity() && source < values.length){

buffer.putFloat(base + component * 4, values[source]);

}

}

}

}

}

static void writeMemberInts(ByteBuffer buffer, UniformBlockLayout.Member member, int[] values){

int elements = member.arrayLength > 0 ? member.arrayLength : 1;

int stride = member.arrayLength > 0 ? member.stride : 0;

int perElement = componentsOf(member.type);

for(int element = 0; element < elements; element++){

int base = member.offset + element * stride;

for(int component = 0; component < perElement; component++){

int source = element * perElement + component;

if(base + component * 4 + 4 <= buffer.capacity() && source < values.length){

buffer.putInt(base + component * 4, values[source]);

}

}

}

}

/** Components per array element for the scalar/vector/matrix types used by Arc shaders. */
private static int componentsOf(String type){
return switch(type){

case "mat4" -> 16;

case "mat3" -> 9;

case "vec4", "ivec4", "bvec4", "uvec4" -> 4;

case "vec3", "ivec3", "bvec3", "uvec3" -> 3;

case "vec2", "ivec2", "bvec2", "uvec2" -> 2;

default -> 1;

}

;

}

private void uploadTexture(){
        // A texture attached to a framebuffer holds that framebuffer's rendered color, so the
// sampler points at the framebuffer instead of an uploaded image.
Integer attachment = textureAttachments.get(boundTexture2D);
if(attachment != null){

VulkanDevice.sampleFramebuffer(attachment);

return;

}

TextureData texture = textures.get(boundTexture2D);

if(texture == null || texture.rgba == null) return;

if(uploadedTexture == boundTexture2D && texture.uploadedVersion == texture.version) return;

if(VulkanDevice.uploadTexture(texture.width, texture.height, texture.rgba)){

uploadedTexture = boundTexture2D;

texture.uploadedVersion = texture.version;

}

}

private void putFloats(int location, float[] values){

ProgramData program = programs.get(activeProgram);

if(program == null || values == null) return;

String name = program.locationNames.get(location);

if(name != null) program.floatValues.put(name, values);

}

private void putInts(int location, int[] values){

ProgramData program = programs.get(activeProgram);

if(program == null || values == null) return;

String name = program.locationNames.get(location);

if(name == null) return;

if(program.translated != null && program.translated.samplerBindings.containsKey(name)) return;

program.intValues.put(name, values);

}
        // ------------------------------------------------------------------ misc queries



@Override
    public int glGetError(){ return GL20.GL_NO_ERROR; }



@Override
public String glGetString(int name){
return name == GL20.GL_RENDERER ? (VulkanDevice.deviceName() != null ? VulkanDevice.deviceName() : "Vulkan Bridge") :
name == GL20.GL_VERSION ? "OpenGL ES 2.0 (Vulkan 1.0 Port)" :
name == GL20.GL_VENDOR ? "Orbital Vulkan" :
name == GL20.GL_EXTENSIONS ? "" : "";
}



@Override
    public void glGetIntegerv(int pname, IntBuffer params){

switch(pname){

case GL20.GL_MAX_TEXTURE_SIZE -> params.put(0, 4096);

case GL20.GL_MAX_VERTEX_ATTRIBS -> params.put(0, attributes.length);

case GL20.GL_MAX_TEXTURE_IMAGE_UNITS, GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS -> params.put(0, 1);

case GL20.GL_ARRAY_BUFFER_BINDING -> params.put(0, boundArrayBuffer);

case GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING -> params.put(0, boundElementArrayBuffer);

case GL20.GL_TEXTURE_BINDING_2D -> params.put(0, boundTexture2D);

case GL20.GL_CURRENT_PROGRAM -> params.put(0, activeProgram);

default -> {

}

}

}
    @Override
    public void glGetFloatv(int pname, FloatBuffer params){
        if(params == null) return;
        switch(pname){
            case GL20.GL_COLOR_CLEAR_VALUE -> {
                params.put(clearR); params.put(clearG); params.put(clearB); params.put(clearA);
            }
            case GL20.GL_DEPTH_CLEAR_VALUE -> params.put(clearDepth);
            case GL20.GL_DEPTH_RANGE -> { params.put(depthNear); params.put(depthFar); }
            case GL20.GL_LINE_WIDTH -> params.put(pipelineLineWidth);
            case GL20.GL_VIEWPORT -> {
                params.put(viewportX); params.put(viewportY); params.put(viewportWidth); params.put(viewportHeight);
            }
            case GL20.GL_SCISSOR_BOX -> {
                params.put(scissorX); params.put(scissorY); params.put(scissorWidth); params.put(scissorHeight);
            }
            case GL20.GL_BLEND_COLOR -> { params.put(blendR); params.put(blendG); params.put(blendB); params.put(blendA); }
            case GL20.GL_ALIASED_LINE_WIDTH_RANGE -> { params.put(1f); params.put(1f); }
            default -> { }
        }
    }
    @Override
    public void glGetBooleanv(int pname, Buffer params){
        if(!(params instanceof IntBuffer out) || out.remaining() < 1) return;
        VulkanPipelineState snapshot = state.snapshot();
        switch(pname){
            case GL20.GL_DEPTH_TEST -> out.put(snapshot.depthTest ? 1 : 0);
            case GL20.GL_BLEND -> out.put(snapshot.blendEnabled ? 1 : 0);
            case GL20.GL_DEPTH_WRITEMASK -> out.put(snapshot.depthWrite ? 1 : 0);
            case GL20.GL_CULL_FACE -> out.put(snapshot.cullEnabled ? 1 : 0);
            case GL20.GL_STENCIL_TEST -> out.put(snapshot.stencilTest);
            default -> out.put(0);
        }
    }
@Override
    public boolean glIsEnabled(int cap){ return false; }



@Override
    public boolean glIsFramebuffer(int framebuffer){ return framebuffers.containsKey(framebuffer); }



@Override
    public boolean glIsRenderbuffer(int renderbuffer){ return renderbuffers.containsKey(renderbuffer); }



@Override
    public boolean glIsProgram(int program){ return programs.containsKey(program); }



@Override
    public boolean glIsShader(int shader){ return shaders.containsKey(shader); }
        // ------------------------------------------------------------------ unimplemented GL surface



@Override
    public int glGenFramebuffer(){

int id = nextFramebufferId++;

framebuffers.put(id, new FramebufferData());

return id;

}

/**
* Creates the native framebuffer for a GL object, on first use. Size and attachments come from
* the attached texture or renderbuffer; a framebuffer without a color attachment is refused.
*/
private int ensureNativeFramebuffer(FramebufferData data, int id){
if(data == null || id <= 0 || !data.hasColor()) return 0;

if(data.colorRenderbuffer != 0){

RenderbufferData buffer = renderbuffers.get(data.colorRenderbuffer);

if(buffer == null) return 0;

if(!deviceHasFramebuffer(id)){

return deviceFramebuffer(id, buffer.width, buffer.height, data.depth, data.stencil);

}

return id;

}

TextureData texture = textures.get(data.colorTexture);

if(texture == null) return 0;

if(!deviceHasFramebuffer(id)){

return deviceFramebuffer(id, texture.width, texture.height, data.depth, data.stencil);

}

return id;

}

private boolean deviceHasFramebuffer(int id){

return deviceFramebuffer.containsKey(id);

}

private int deviceFramebuffer(int id, int width, int height, boolean depth, boolean stencil){

if(width <= 0 || height <= 0) return 0;

int created = VulkanDevice.createFramebuffer(width, height, depth, stencil);

if(created > 0) deviceFramebuffer.put(id, created);

if(created > 0) Log.debug("[VulkanGL20] framebuffer @: @x@ depth=@ stencil=@ -> device @",
id, width, height, depth, stencil, created);
else Log.warn("[VulkanGL20] framebuffer @ could not be created: @", id, VulkanDevice.lastError());

return created;

}



@Override
public void glBindFramebuffer(int target, int framebuffer){
    // GL_FRAMEBUFFER and GL_READ_FRAMEBUFFER/GL_DRAW_FRAMEBUFFER share one slot here: the Vulkan
    // device keeps a single bound target.
    FramebufferData data = framebuffers.get(framebuffer);
    if(data != null && data.hasColor()) ensureNativeFramebuffer(data, framebuffer);
    VulkanDevice.bindFramebuffer(framebuffer);
    boundFramebuffer = framebuffer;
    applyTargetViewport();
}



@Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level){

if(attachment != GL20.GL_COLOR_ATTACHMENT0 || level != 0) return;

FramebufferData data = framebuffers.get(boundFramebuffer);

if(data == null) return;

data.colorTexture = texture;

data.colorRenderbuffer = 0;

textureAttachments.put(texture, boundFramebuffer);

}



@Override
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer){

FramebufferData data = framebuffers.get(boundFramebuffer);

if(data == null) return;

if(attachment == GL20.GL_COLOR_ATTACHMENT0){

data.colorRenderbuffer = renderbuffer;

data.colorTexture = 0;

}

else if(attachment == GL20.GL_DEPTH_ATTACHMENT || attachment == 0x821A /* GL_DEPTH_STENCIL_ATTACHMENT */){

data.depthRenderbuffer = renderbuffer;

}

}



@Override
public int glGenRenderbuffer(){
int id = nextRenderbufferId++;
renderbuffers.put(id, new RenderbufferData());
return id;
}



@Override
    public void glBindRenderbuffer(int target, int renderbuffer){         boundRenderbuffer = renderbuffer;     }



@Override
public void glRenderbufferStorage(int target, int internalformat, int width, int height){
RenderbufferData data = renderbuffers.get(boundRenderbuffer);
if(data == null) return;
data.format = internalformat;
data.width = width;
data.height = height;
}



@Override
    public void glDeleteFramebuffer(int framebuffer){

if(framebuffer <= 0) return;

FramebufferData data = framebuffers.remove(framebuffer);

if(data == null) return;

if(data.colorTexture != 0) textureAttachments.remove(data.colorTexture);

if(boundFramebuffer == framebuffer){

boundFramebuffer = 0;

VulkanDevice.bindFramebuffer(0);

applyTargetViewport();

}

VulkanDevice.deleteFramebuffer(framebuffer);

}



@Override
    public void glDeleteRenderbuffer(int renderbuffer){         renderbuffers.remove(renderbuffer);     }



@Override
public int glCheckFramebufferStatus(int target){
FramebufferData data = framebuffers.get(boundFramebuffer);
if(data != null && !data.hasColor()) return GL20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
// The device only knows about a framebuffer once it is created, and every attachment is known by
// the time Arc checks the status - so this is where the offscreen target is actually built.
int handle = ensureNativeFramebuffer(data, boundFramebuffer);
if(handle == 0) return GL20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
// The device verifies the real attachments: colour image, render pass and framebuffer.
int status = VulkanDevice.framebufferStatus(handle);
return status == 1 ? GL20.GL_FRAMEBUFFER_COMPLETE : GL20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
}



@Override
    public void glFinish(){ VulkanDevice.finish(); }



@Override
    public void glFlush(){ /* every path submits synchronously */ }



@Override
public void glBlendColor(float red, float green, float blue, float alpha){
blendR = red;
blendG = green;
blendB = blue;
blendA = alpha;
state.blendColor(red, green, blue, alpha);
}



@Override
    public void glClearDepthf(float depth){         clearDepth = depth;     }



@Override
    public void glClearStencil(int s){         clearStencil = s;     }



@Override
public void glDepthRangef(float zNear, float zFar){
this.depthNear = zNear;
this.depthFar = zFar;
}



@Override
public void glGetBufferParameteriv(int target, int pname, IntBuffer params){
int id = target == GL20.GL_ELEMENT_ARRAY_BUFFER ? boundElementArrayBuffer : boundArrayBuffer;
BufferData buffer = buffers.get(id);
if(buffer == null) return;
if(pname == GL20.GL_BUFFER_SIZE) params.put(0, buffer.size);
}



@Override
    public void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params){

FramebufferData data = framebuffers.get(boundFramebuffer);

if(data == null) return;

if(attachment == GL20.GL_COLOR_ATTACHMENT0){

if(pname == GL20.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE){

params.put(0, data.colorTexture != 0 ? GL20.GL_TEXTURE : GL20.GL_RENDERBUFFER);

}

else if(pname == GL20.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME){

params.put(0, data.colorTexture != 0 ? data.colorTexture : data.colorRenderbuffer);

}

}

}



@Override
    public void glGetRenderbufferParameteriv(int target, int pname, IntBuffer params){

RenderbufferData data = renderbuffers.get(boundRenderbuffer);

if(data == null) return;

switch(pname){

case GL20.GL_RENDERBUFFER_WIDTH -> params.put(0, data.width);

case GL20.GL_RENDERBUFFER_HEIGHT -> params.put(0, data.height);

case GL20.GL_RENDERBUFFER_INTERNAL_FORMAT -> params.put(0, data.format);

default -> {

}

}

}



@Override
    public void glGetShaderPrecisionFormat(int shadertype, int precisiontype, IntBuffer range, IntBuffer precision){}
    @Override
    public void glGetTexParameterfv(int target, int pname, FloatBuffer params){
        TextureData texture = textures.get(boundTexture2D);
        if(texture == null || params == null) return;
        if(pname == GL20.GL_TEXTURE_MIN_FILTER) params.put(texture.minFilter);
        else if(pname == GL20.GL_TEXTURE_MAG_FILTER) params.put(texture.magFilter);
        else if(pname == GL20.GL_TEXTURE_WRAP_S) params.put(texture.wrapS);
        else if(pname == GL20.GL_TEXTURE_WRAP_T) params.put(texture.wrapT);
    }
    @Override
    public void glGetTexParameteriv(int target, int pname, IntBuffer params){
        TextureData texture = textures.get(boundTexture2D);
        if(texture == null || params == null) return;
        if(pname == GL20.GL_TEXTURE_MIN_FILTER) params.put(texture.minFilter);
        else if(pname == GL20.GL_TEXTURE_MAG_FILTER) params.put(texture.magFilter);
        else if(pname == GL20.GL_TEXTURE_WRAP_S) params.put(texture.wrapS);
        else if(pname == GL20.GL_TEXTURE_WRAP_T) params.put(texture.wrapT);
    }
    @Override
    public void glGetVertexAttribfv(int index, int pname, FloatBuffer params){
        if(index < 0 || index >= attributes.length || params == null) return;
        AttributeData attribute = attributes[index];
        if(pname == GL20.GL_CURRENT_VERTEX_ATTRIB){
            for(int i = 0; i < 4; i++) params.put(0f); // GL_CURRENT_VERTEX_ATTRIB is not tracked for pointer attributes
        }else if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE){
            params.put(attribute.size);
        }else if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE){
            params.put(attribute.stride);
        }
    }
    @Override
    public void glGetVertexAttribiv(int index, int pname, IntBuffer params){
        if(index < 0 || index >= attributes.length || params == null) return;
        AttributeData attribute = attributes[index];
        if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED){
            params.put(attribute.enabled ? 1 : 0);
        }else if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE){
            params.put(attribute.size);
        }else if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE){
            params.put(attribute.type);
        }else if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED){
            params.put(attribute.normalized ? 1 : 0);
        }else if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE){
            params.put(attribute.stride);
        }else if(pname == GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING){
            params.put(attribute.buffer);
        }
    }
@Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels){

if(pixels == null || width <= 0 || height <= 0) return;

if(format != GL20.GL_RGBA || (type != GL20.GL_UNSIGNED_BYTE && type != 0x1401)) return;

if(!VulkanDevice.created()) return;
        // The device reads the whole bound target; a sub-rectangle is extracted on the Java side.
int targetWidth = VulkanDevice.boundWidth(), targetHeight = VulkanDevice.boundHeight();
if(targetWidth <= 0 || targetHeight <= 0) return;

if(x < 0 || y < 0 || x + width > targetWidth || y + height > targetHeight) return;

byte[] frame = VulkanDevice.frame();

if(frame == null) return;

int stride = Math.min(width, targetWidth - x);

int rows = Math.min(height, targetHeight - y);

int rowLength = align(width * 4, pixelAlignment);

int bytes = rows * rowLength;

if(pixels.remaining() < bytes) return;

byte[] packed = new byte[Math.max(bytes, 0)];

for(int row = 0; row < rows; row++){

int source = ((y + row) * targetWidth + x) * 4;

System.arraycopy(frame, source, packed, row * rowLength, stride * 4);

}

if(pixels instanceof ByteBuffer buffer){

buffer.put(packed, 0, bytes);

}

else if(pixels instanceof IntBuffer ints){

for(int i = 0; i < bytes / 4; i++){

int rgba = (packed[i * 4] & 0xff) | ((packed[i * 4 + 1] & 0xff) << 8)
| ((packed[i * 4 + 2] & 0xff) << 16) | ((packed[i * 4 + 3] & 0xff) << 24);
ints.put(rgba);

}

}

}

/** GL rounds a row length up to the next multiple of the pack/unpack alignment. */
private static int align(int bytes, int alignment){
if(alignment <= 1) return bytes;

return (bytes + alignment - 1) / alignment * alignment;

}



@Override
    public void glStencilFunc(int func, int ref, int mask){ state.stencil(func, ref, mask); }



@Override
    public void glStencilFuncSeparate(int face, int func, int ref, int mask){ state.stencil(func, ref, mask); }



@Override
    public void glStencilMask(int mask){ state.stencilMask(mask); }



@Override
    public void glStencilMaskSeparate(int face, int mask){ state.stencilMask(mask); }



@Override
    public void glStencilOp(int fail, int zfail, int zpass){ state.stencilOp(fail, zfail, zpass); }



@Override
    public void glStencilOpSeparate(int face, int fail, int zfail, int zpass){

state.stencilOp(fail, zfail, zpass);

}
// ------------------------------------------------------------------ helpers
private static String normalizeUniformName(String name){
return name != null && name.endsWith("[0]") ? name.substring(0, name.length() - 3) : name;

}

private static int componentBytes(int type){

return switch(type){

case GL20.GL_BYTE, GL20.GL_UNSIGNED_BYTE -> 1;

case GL20.GL_SHORT, GL20.GL_UNSIGNED_SHORT -> 2;

default -> 4;

}

;

}

private static byte[] bytesOf(Buffer data){

if(data == null) return new byte[0];

Buffer source = data.duplicate();

source.rewind();

int position = data.position();

int remaining = Math.max(data.remaining(), 0);

source.position(position);

if(source instanceof ByteBuffer buffer){

byte[] out = new byte[remaining];

buffer.get(out);

return out;

}

if(source instanceof FloatBuffer buffer){

ByteBuffer bytes = ByteBuffer.allocate(remaining * 4).order(ByteOrder.LITTLE_ENDIAN);

FloatBuffer floats = bytes.asFloatBuffer();

for(int i = 0; i < remaining; i++) floats.put(buffer.get());

return bytes.array();

}

if(source instanceof IntBuffer buffer){

ByteBuffer bytes = ByteBuffer.allocate(remaining * 4).order(ByteOrder.LITTLE_ENDIAN);

IntBuffer ints = bytes.asIntBuffer();

for(int i = 0; i < remaining; i++) ints.put(buffer.get());

return bytes.array();

}

if(source instanceof ShortBuffer buffer){

ByteBuffer bytes = ByteBuffer.allocate(remaining * 2).order(ByteOrder.LITTLE_ENDIAN);

ShortBuffer shorts = bytes.asShortBuffer();

for(int i = 0; i < remaining; i++) shorts.put(buffer.get());

return bytes.array();

}

return new byte[0];

}

private static byte[] rgbaBytes(Buffer pixels, int width, int height, int format, int type){

int pixelCount = Math.max(width, 0) * Math.max(height, 0);

if(pixels == null) return new byte[pixelCount * 4];

if(format == GL20.GL_RGBA && type == GL20.GL_UNSIGNED_BYTE){

byte[] source = bytesOf(pixels);

byte[] out = new byte[pixelCount * 4];

System.arraycopy(source, 0, out, 0, Math.min(source.length, out.length));

return out;

}
// Expand RGB/LUMINANCE/ALPHA inputs into RGBA so the native texture format stays fixed.
ByteBuffer source = ByteBuffer.wrap(bytesOf(pixels));
byte[] out = new byte[pixelCount * 4];

for(int i = 0; i < pixelCount; i++){

int r, g, b, a = 255;

if(format == GL20.GL_RGB){

r = source.get() & 0xff;

g = source.get() & 0xff;

b = source.get() & 0xff;

}

else if(format == GL20.GL_LUMINANCE_ALPHA){

r = g = b = source.get() & 0xff;

a = source.get() & 0xff;

}

else if(format == GL20.GL_ALPHA){

r = g = b = 255;

a = source.get() & 0xff;

}

else{

r = g = b = source.get() & 0xff;

}

out[i * 4] = (byte)r;

out[i * 4 + 1] = (byte)g;

out[i * 4 + 2] = (byte)b;

out[i * 4 + 3] = (byte)a;

}

return out;

}

private static float[] readFloats(FloatBuffer buffer, int count){

float[] out = new float[count];

FloatBuffer source = buffer.duplicate();

for(int i = 0; i < count && source.hasRemaining(); i++) out[i] = source.get();

return out;

}

private static int[] readInts(IntBuffer buffer, int count){

int[] out = new int[count];

IntBuffer source = buffer.duplicate();

for(int i = 0; i < count && source.hasRemaining(); i++) out[i] = source.get();

return out;

}

private static float[] slice(float[] values, int offset, int count){

float[] out = new float[count];

int length = Math.min(count, values.length - offset);

if(length > 0) System.arraycopy(values, offset, out, 0, length);

return out;

}

private static int[] slice(int[] values, int offset, int count){

int[] out = new int[count];

int length = Math.min(count, values.length - offset);

if(length > 0) System.arraycopy(values, offset, out, 0, length);

return out;

}

}
