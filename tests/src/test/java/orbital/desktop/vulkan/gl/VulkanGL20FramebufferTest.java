package orbital.desktop.vulkan.gl;

import arc.graphics.GL20;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Framebuffer bookkeeping, clear masking and the pipeline state encoding, without a Vulkan device:
 * these run on any host.
 */
class VulkanGL20FramebufferTest{
    private final VulkanGL20 gl = new VulkanGL20();

    private static final int framebufferTarget = GL20.GL_FRAMEBUFFER;
    private static final int colorAttachment0 = GL20.GL_COLOR_ATTACHMENT0;
    private static final int texture2D = GL20.GL_TEXTURE_2D;

    @Test
    void framebuffersAreTrackedAndReportTheirAttachments(){
        int framebuffer = gl.glGenFramebuffer();
        assertTrue(framebuffer > 0);
        assertTrue(gl.glIsFramebuffer(framebuffer));

        //An empty framebuffer is incomplete, and it is not silently reported as complete.
        gl.glBindFramebuffer(framebufferTarget, framebuffer);
        assertEquals(GL20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT, gl.glCheckFramebufferStatus(framebufferTarget));

        gl.glFramebufferTexture2D(framebufferTarget, colorAttachment0, texture2D, 77, 0);
        gl.glFramebufferTexture2D(framebufferTarget, colorAttachment0, texture2D, 0, 0); // detach
        assertEquals(GL20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT, gl.glCheckFramebufferStatus(framebufferTarget));

        gl.glDeleteFramebuffer(framebuffer);
        assertFalse(gl.glIsFramebuffer(framebuffer));
    }

    @Test
    void renderbuffersCarryTheirStorage(){
        int renderbuffer = gl.glGenRenderbuffer();
        assertTrue(renderbuffer > 0);
        gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, renderbuffer);
        gl.glRenderbufferStorage(GL20.GL_RENDERBUFFER, 0x8058 /* GL_RGBA8 */, 32, 16);

        //Java-side queries read what was stored: GL has no array form for these.
        int[] out = new int[1];
        gl.glGetRenderbufferParameteriv(GL20.GL_RENDERBUFFER, GL20.GL_RENDERBUFFER_WIDTH, wrap(out));
        assertEquals(32, out[0]);
        gl.glGetRenderbufferParameteriv(GL20.GL_RENDERBUFFER, GL20.GL_RENDERBUFFER_HEIGHT, wrap(out));
        assertEquals(16, out[0]);
    }

    @Test
    void thePipelineStateEncodesEveryFieldTheNativeSideExpects(){
        VulkanGLState state = new VulkanGLState();
        state.enable(GL20.GL_DEPTH_TEST);
        state.enable(GL20.GL_BLEND);
        state.depthMask(true);
        state.depthFunc(GL20.GL_LESS);
        state.stencilTest(true);
        state.stencil(GL20.GL_ALWAYS, 4, 0xff);
        state.stencilOp(GL20.GL_KEEP, GL20.GL_INCR_WRAP, GL20.GL_DECR_WRAP);
        state.lineWidth(2.5f);
        state.blendColor(0.25f, 0.5f, 0.75f, 1f);
        state.enable(GL20.GL_CULL_FACE);
        state.cullFace(GL20.GL_BACK);

        int[] encoded = encode(state.snapshot());

        assertEquals(28, encoded.length, "the native side expects exactly this many integers");
        assertEquals(1, encoded[0], "depthTest");
        assertEquals(1, encoded[1], "depthWrite");
        assertEquals(GL20.GL_LESS, encoded[2], "depthFunc");
        assertEquals(1, encoded[3], "stencilTest");
        assertEquals(GL20.GL_ALWAYS, encoded[4], "stencilFunc");
        assertEquals(4, encoded[5], "stencilRef");
        assertEquals(0xff, encoded[6], "stencilMask");
        assertEquals(GL20.GL_KEEP, encoded[7], "stencilFail");
        assertEquals(GL20.GL_INCR_WRAP, encoded[8], "stencilDepthFail");
        assertEquals(GL20.GL_DECR_WRAP, encoded[9], "stencilDepthPass");
        assertEquals(1, encoded[10], "cullFace: GL_BACK travels as the compact code 1");
        assertEquals(1, encoded[11], "frontFaceCCW");
        assertEquals(1, encoded[12], "blend");
        assertEquals(GL20.GL_SRC_ALPHA, encoded[13], "blendSrcRGB");
        assertEquals(GL20.GL_ONE_MINUS_SRC_ALPHA, encoded[14], "blendDstRGB");
        assertEquals(1, encoded[19], "colorMaskR");
        assertEquals(1, encoded[22], "colorMaskA");
        assertEquals(2500, encoded[23], "line width in thousandths");
        assertEquals(Math.round(0.25f * 65535f), encoded[24], "blend constant red");
        assertEquals(Math.round(0.5f * 65535f), encoded[25], "blend constant green");
        assertEquals(Math.round(0.75f * 65535f), encoded[26], "blend constant blue");
        assertEquals(Math.round(65535f), encoded[27], "blend constant alpha");
    }

    @Test
    void cullFaceAndFrontFaceTravelAsCompactCodes(){
        VulkanGLState state = new VulkanGLState();
        state.enable(GL20.GL_CULL_FACE);
        state.cullFace(GL20.GL_FRONT_AND_BACK);
        state.frontFace(GL20.GL_CW);
        int[] encoded = encode(state.snapshot());

        assertEquals(3, encoded[10], "GL_FRONT_AND_BACK");
        assertEquals(0, encoded[11], "GL_CW is not counter-clockwise");

        state.disable(GL20.GL_CULL_FACE);
        assertEquals(0, encode(state.snapshot())[10], "no cull face means the code 0");
    }

    @Test
    void stateChangesThatNeedANewPipelineDoChangeTheKey(){
        VulkanGLState state = new VulkanGLState();
        VulkanPipelineState first = state.snapshot();

        state.stencilTest(true);
        assertNotEquals(first, state.snapshot(), "enabling stencil must rebuild the pipeline");
        state.stencilTest(false);
        assertEquals(first, state.snapshot(), "and disabling it returns to the original key");

        state.lineWidth(3f);
        assertNotEquals(first, state.snapshot(), "a different line width needs a new pipeline");
        state.lineWidth(1f);
        assertEquals(first, state.snapshot());

        state.blendColor(0.5f, 0.5f, 0.5f, 1f);
        assertNotEquals(first, state.snapshot(), "glBlendColor feeds the pipeline state");
        state.blendColor(0f, 0f, 0f, 1f);
        assertEquals(first, state.snapshot());
    }

    @Test
    void theColorMaskTravelsPerComponent(){
        VulkanGLState state = new VulkanGLState();
        state.colorMask(true, false, true, false);
        int[] encoded = encode(state.snapshot());

        assertEquals(1, encoded[19]);
        assertEquals(0, encoded[20]);
        assertEquals(1, encoded[21]);
        assertEquals(0, encoded[22]);
    }

    /** Mirrors the encoding in {@link VulkanGL20#pipelineState}. */
    private static int[] encode(VulkanPipelineState snapshot){
        int[] state = new int[28];
        state[0] = snapshot.depthTest ? 1 : 0;
        state[1] = snapshot.depthWrite ? 1 : 0;
        state[2] = snapshot.depthFunc;
        state[3] = snapshot.stencilTest;
        state[4] = snapshot.stencilFunc;
        state[5] = snapshot.stencilRef;
        state[6] = snapshot.stencilMask;
        state[7] = snapshot.stencilFail;
        state[8] = snapshot.stencilDepthFail;
        state[9] = snapshot.stencilDepthPass;
        state[10] = snapshot.cullEnabled ? switch(snapshot.cullFace){
            case GL20.GL_BACK -> 1;
            case GL20.GL_FRONT -> 2;
            case GL20.GL_FRONT_AND_BACK -> 3;
            default -> 0;
        } : 0;
        state[11] = snapshot.frontFace == GL20.GL_CCW ? 1 : 0;
        state[12] = snapshot.blendEnabled ? 1 : 0;
        state[13] = snapshot.blendSrcRGB;
        state[14] = snapshot.blendDstRGB;
        state[15] = snapshot.blendSrcAlpha;
        state[16] = snapshot.blendDstAlpha;
        state[17] = snapshot.blendEqRGB;
        state[18] = snapshot.blendEqAlpha;
        state[19] = snapshot.colorMaskR ? 1 : 0;
        state[20] = snapshot.colorMaskG ? 1 : 0;
        state[21] = snapshot.colorMaskB ? 1 : 0;
        state[22] = snapshot.colorMaskA ? 1 : 0;
        state[23] = Math.round(snapshot.lineWidth * 1000f);
        state[24] = Math.round(snapshot.blendR * 65535f);
        state[25] = Math.round(snapshot.blendG * 65535f);
        state[26] = Math.round(snapshot.blendB * 65535f);
        state[27] = Math.round(snapshot.blendA * 65535f);
        return state;
    }

    private static java.nio.IntBuffer wrap(int[] values){
        return java.nio.IntBuffer.wrap(values);
    }
}
