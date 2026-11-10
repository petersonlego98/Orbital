package orbital.desktop.vulkan.gl;

/**
 * Immutable key for one Vulkan graphics pipeline.
 *
 * <p>OpenGL lets applications mutate state at any time; Vulkan freezes most of it into
 * VkGraphicsPipelineCreateInfo. This key captures every GL state that changes pipeline
 * compatibility, so {@link VulkanPipelineCache} can reuse pipelines instead of rebuilding them
 * on every draw call. Fields keep GL enum values - the native layer translates them to Vk enums.</p>
 */
public final class VulkanPipelineState{
    public final long vertexShader, fragmentShader, renderPass;
    public final int subpass, topology;
    public final long vertexLayoutHash;
    public final boolean blendEnabled, depthTest, depthWrite, cullEnabled, scissorEnabled;
    public final boolean colorMaskR, colorMaskG, colorMaskB, colorMaskA;
    public final int blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha;
    public final int blendEqRGB, blendEqAlpha, depthFunc, cullFace, frontFace;
    public final int stencilTest, stencilFunc, stencilRef, stencilMask;
    public final int stencilFail, stencilDepthFail, stencilDepthPass;
    public final float lineWidth, blendR, blendG, blendB, blendA;

    VulkanPipelineState(VulkanGLState s){
        vertexShader = s.vertexShader;
        fragmentShader = s.fragmentShader;
        renderPass = s.renderPass;
        subpass = s.subpass;
        topology = s.topology;
        vertexLayoutHash = s.vertexLayoutHash;
        blendEnabled = s.blendEnabled;
        depthTest = s.depthTest;
        depthWrite = s.depthWrite;
        cullEnabled = s.cullEnabled;
        scissorEnabled = s.scissorEnabled;
        colorMaskR = s.colorMaskR;
        colorMaskG = s.colorMaskG;
        colorMaskB = s.colorMaskB;
        colorMaskA = s.colorMaskA;
        blendSrcRGB = s.blendSrcRGB;
        blendDstRGB = s.blendDstRGB;
        blendSrcAlpha = s.blendSrcAlpha;
        blendDstAlpha = s.blendDstAlpha;
        blendEqRGB = s.blendEqRGB;
        blendEqAlpha = s.blendEqAlpha;
        depthFunc = s.depthFunc;
        cullFace = s.cullFace;
        frontFace = s.frontFace;
        stencilTest = s.stencilTest;
        stencilFunc = s.stencilFunc;
        stencilRef = s.stencilRef;
        stencilMask = Math.max(s.stencilMask, s.stencilWriteMask);
        stencilFail = s.stencilFail;
        stencilDepthFail = s.stencilDepthFail;
        stencilDepthPass = s.stencilDepthPass;
        lineWidth = s.lineWidth;
        blendR = s.blendR;
        blendG = s.blendG;
        blendB = s.blendB;
        blendA = s.blendA;
    }

    @Override
    public boolean equals(Object o){
        if(this == o) return true;
        if(!(o instanceof VulkanPipelineState s)) return false;
        return vertexShader == s.vertexShader && fragmentShader == s.fragmentShader
            && renderPass == s.renderPass && subpass == s.subpass && topology == s.topology
            && vertexLayoutHash == s.vertexLayoutHash
            && blendEnabled == s.blendEnabled && depthTest == s.depthTest && depthWrite == s.depthWrite
            && cullEnabled == s.cullEnabled && scissorEnabled == s.scissorEnabled
            && colorMaskR == s.colorMaskR && colorMaskG == s.colorMaskG
            && colorMaskB == s.colorMaskB && colorMaskA == s.colorMaskA
            && blendSrcRGB == s.blendSrcRGB && blendDstRGB == s.blendDstRGB
            && blendSrcAlpha == s.blendSrcAlpha && blendDstAlpha == s.blendDstAlpha
            && blendEqRGB == s.blendEqRGB && blendEqAlpha == s.blendEqAlpha
            && depthFunc == s.depthFunc && cullFace == s.cullFace && frontFace == s.frontFace
            && stencilTest == s.stencilTest && stencilFunc == s.stencilFunc && stencilRef == s.stencilRef
            && stencilMask == s.stencilMask && stencilFail == s.stencilFail
            && stencilDepthFail == s.stencilDepthFail && stencilDepthPass == s.stencilDepthPass
            && lineWidth == s.lineWidth && blendR == s.blendR && blendG == s.blendG
            && blendB == s.blendB && blendA == s.blendA;
    }

    @Override
    public int hashCode(){
        int hash = Long.hashCode(vertexShader);
        hash = 31 * hash + Long.hashCode(fragmentShader);
        hash = 31 * hash + Long.hashCode(renderPass);
        hash = 31 * hash + subpass;
        hash = 31 * hash + topology;
        hash = 31 * hash + Long.hashCode(vertexLayoutHash);
        hash = 31 * hash + Boolean.hashCode(blendEnabled);
        hash = 31 * hash + Boolean.hashCode(depthTest);
        hash = 31 * hash + Boolean.hashCode(depthWrite);
        hash = 31 * hash + Boolean.hashCode(cullEnabled);
        hash = 31 * hash + Boolean.hashCode(scissorEnabled);
        hash = 31 * hash + Boolean.hashCode(colorMaskR);
        hash = 31 * hash + Boolean.hashCode(colorMaskG);
        hash = 31 * hash + Boolean.hashCode(colorMaskB);
        hash = 31 * hash + Boolean.hashCode(colorMaskA);
        hash = 31 * hash + blendSrcRGB;
        hash = 31 * hash + blendDstRGB;
        hash = 31 * hash + blendSrcAlpha;
        hash = 31 * hash + blendDstAlpha;
        hash = 31 * hash + blendEqRGB;
        hash = 31 * hash + blendEqAlpha;
        hash = 31 * hash + depthFunc;
        hash = 31 * hash + cullFace;
        hash = 31 * hash + frontFace;
        hash = 31 * hash + stencilTest;
        hash = 31 * hash + stencilFunc;
        hash = 31 * hash + stencilRef;
        hash = 31 * hash + stencilMask;
        hash = 31 * hash + stencilFail;
        hash = 31 * hash + stencilDepthFail;
        hash = 31 * hash + stencilDepthPass;
        hash = 31 * hash + Float.floatToIntBits(lineWidth);
        hash = 31 * hash + Float.floatToIntBits(blendR);
        hash = 31 * hash + Float.floatToIntBits(blendG);
        hash = 31 * hash + Float.floatToIntBits(blendB);
        hash = 31 * hash + Float.floatToIntBits(blendA);
        return hash;
    }

    @Override
    public String toString(){
        return "VulkanPipelineState{program=" + vertexShader + ":" + fragmentShader
            + ", topology=" + topology + ", blend=" + blendEnabled
            + ", depth=" + depthTest + "/" + depthWrite + ", cull=" + cullEnabled + '}';
    }
}
