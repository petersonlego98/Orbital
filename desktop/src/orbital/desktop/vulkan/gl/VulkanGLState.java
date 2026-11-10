package orbital.desktop.vulkan.gl;

import arc.graphics.GL20;

/**
 * Mutable OpenGL-style state tracker. This is the compatibility layer that a Vulkan
 * GL20 implementation would drive: every glEnable/glBlendFunc/glDepthFunc call mutates this
 * object, and a draw call snapshots it into an immutable {@link VulkanPipelineState}.
 *
 * <p>Using Arc's own GL20 constants keeps the mapping honest: the emulation receives exactly the
 * enum values Orbital already sends to OpenGL.</p>
 */
public final class VulkanGLState{
    long vertexShader, fragmentShader, renderPass, vertexLayoutHash;
    int subpass;
    int topology = GL20.GL_TRIANGLES;
    boolean blendEnabled, depthTest, depthWrite = true, cullEnabled, scissorEnabled;
    boolean colorMaskR = true, colorMaskG = true, colorMaskB = true, colorMaskA = true;
    int blendSrcRGB = GL20.GL_SRC_ALPHA, blendDstRGB = GL20.GL_ONE_MINUS_SRC_ALPHA;
    int blendSrcAlpha = GL20.GL_SRC_ALPHA, blendDstAlpha = GL20.GL_ONE_MINUS_SRC_ALPHA;
    int blendEqRGB = GL20.GL_FUNC_ADD, blendEqAlpha = GL20.GL_FUNC_ADD;
    int depthFunc = GL20.GL_LESS;
    int cullFace = GL20.GL_BACK;
    int frontFace = GL20.GL_CCW;
    int stencilTest = 0, stencilFunc = GL20.GL_ALWAYS, stencilRef = 0, stencilMask = 0xffffffff;
    int stencilFail = GL20.GL_KEEP, stencilDepthFail = GL20.GL_KEEP, stencilDepthPass = GL20.GL_KEEP;
    int stencilWriteMask = 0xffffffff;
    float lineWidth = 1.0f;
    float blendR = 0f, blendG = 0f, blendB = 0f, blendA = 1f;

    public void program(long vertex, long fragment){
        vertexShader = vertex;
        fragmentShader = fragment;
    }

    public void framebuffer(long pass, int subpassIndex){
        renderPass = pass;
        subpass = subpassIndex;
    }

    public void primitive(int mode){
        topology = mode;
    }

    /** Hash of the active vertex attribute descriptions (binding/format/offset). */
    public void vertexLayout(long hash){
        vertexLayoutHash = hash;
    }

    public void enable(int capability){
        switch(capability){
            case GL20.GL_BLEND -> blendEnabled = true;
            case GL20.GL_DEPTH_TEST -> depthTest = true;
            case GL20.GL_CULL_FACE -> cullEnabled = true;
            case GL20.GL_SCISSOR_TEST -> scissorEnabled = true;
            default -> { /* DITHER, STENCIL, POLYGON_OFFSET: pipeline-neutral for now */ }
        }
    }

    public void disable(int capability){
        switch(capability){
            case GL20.GL_BLEND -> blendEnabled = false;
            case GL20.GL_DEPTH_TEST -> depthTest = false;
            case GL20.GL_CULL_FACE -> cullEnabled = false;
            case GL20.GL_SCISSOR_TEST -> scissorEnabled = false;
            default -> { }
        }
    }

    public void blendFunc(int src, int dst){
        blendSrcRGB = blendSrcAlpha = src;
        blendDstRGB = blendDstAlpha = dst;
    }

    public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha){
        blendSrcRGB = srcRGB;
        blendDstRGB = dstRGB;
        blendSrcAlpha = srcAlpha;
        blendDstAlpha = dstAlpha;
    }

    public void blendEquation(int mode){
        blendEqRGB = blendEqAlpha = mode;
    }

    public void depthMask(boolean write){
        depthWrite = write;
    }

    public void depthFunc(int func){
        depthFunc = func;
    }

    public void cullFace(int face){
        cullFace = face;
    }

    public void frontFace(int face){
        frontFace = face;
    }

    /** Tracks GL_STENCIL_TEST: the only stencil flag GL exposes. */
    public void stencilTest(boolean enabled){
        stencilTest = enabled ? 1 : 0;
    }

    public void stencil(int func, int ref, int mask){
        stencilFunc = func;
        stencilRef = ref;
        stencilMask = mask;
    }

    public void stencilMask(int mask){
        stencilWriteMask = stencilMask = mask;
    }

    public void stencilOp(int fail, int depthFail, int depthPass){
        stencilFail = fail;
        stencilDepthFail = depthFail;
        stencilDepthPass = depthPass;
    }

    public void lineWidth(float width){
        lineWidth = Math.max(width, 0f);
    }

    public void blendColor(float r, float g, float b, float a){
        blendR = r;
        blendG = g;
        blendB = b;
        blendA = a;
    }

    public void colorMask(boolean r, boolean g, boolean b, boolean a){
        colorMaskR = r;
        colorMaskG = g;
        colorMaskB = b;
        colorMaskA = a;
    }

    public VulkanPipelineState snapshot(){
        return new VulkanPipelineState(this);
    }
}
