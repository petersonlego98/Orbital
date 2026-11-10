package orbital.desktop.vulkan.gl;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Arc's {@code glUniform*v} values land at the std140 offsets emitted for the UBO,
 * including uniform arrays (which previously uploaded only their first element).
 */
class VulkanGL20UniformUploadTest{
    private static final String VERTEX = """
        attribute vec2 a_position;
        void main(){
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT = """
        uniform vec4 u_values[3];
        uniform int u_flags[2];
        void main(){
            gl_FragColor = u_values[1] + vec4(float(u_flags[1]));
        }
        """;

    @Test
    void writesEveryElementOfVec4ArrayAtStd140Offsets(){
        GLSLProgram.Result result = GLSLProgram.translate(VERTEX, FRAGMENT);
        UniformBlockLayout layout = result.uniformLayout;
        UniformBlockLayout.Member values = layout.member("u_values");
        assertEquals(3, values.arrayLength);

        ByteBuffer buffer = ByteBuffer.allocateDirect(layout.size()).order(ByteOrder.LITTLE_ENDIAN);
        float[] data = {1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f};
        VulkanGL20.writeMemberFloats(buffer, values, data);

        for(int element = 0; element < 3; element++){
            int base = values.offset + element * values.stride;
            for(int component = 0; component < 4; component++){
                assertEquals(data[element * 4 + component], buffer.getFloat(base + component * 4), 0.0001f,
                    "u_values[" + element + "]." + component);
            }
        }
    }

    @Test
    void writesEveryElementOfIntArray(){
        GLSLProgram.Result result = GLSLProgram.translate(VERTEX, FRAGMENT);
        UniformBlockLayout layout = result.uniformLayout;
        UniformBlockLayout.Member flags = layout.member("u_flags");
        assertEquals(2, flags.arrayLength);

        ByteBuffer buffer = ByteBuffer.allocateDirect(layout.size()).order(ByteOrder.LITTLE_ENDIAN);
        VulkanGL20.writeMemberInts(buffer, flags, new int[]{7, 42});

        assertEquals(7, buffer.getInt(flags.offset));
        assertEquals(42, buffer.getInt(flags.offset + flags.stride));
    }
}
