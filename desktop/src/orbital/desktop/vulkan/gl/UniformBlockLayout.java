package orbital.desktop.vulkan.gl;

import java.nio.*;
import java.util.*;
import java.util.regex.*;

/**
 * Computes std140 offsets for the UBO emitted by {@link GLSLProgram}.
 *
 * <p>Vulkan does not accept default-block uniforms, so all non-opaque uniforms are packed into one
 * shared block. Arc's glUniform* calls must write to this exact layout instead of setting individual
 * uniforms; a wrong offset silently corrupts rendering, which is why this class is exercised by
 * tests rather than derived ad hoc in the backend.</p>
 */
public final class UniformBlockLayout{
    private static final Pattern ARRAY = Pattern.compile("\\[\\s*([A-Za-z_][A-Za-z0-9_]*|[0-9]+)\\s*\\]");

    public static final class Member{
        public final String name, type;
        public final int arrayLength, offset, size, alignment, stride;

        Member(String name, String type, int arrayLength, int offset, int size, int alignment, int stride){
            this.name = name;
            this.type = type;
            this.arrayLength = arrayLength;
            this.offset = offset;
            this.size = size;
            this.alignment = alignment;
            this.stride = stride;
        }
    }

    private final Map<String, Member> members;
    private final int size;

    private UniformBlockLayout(Map<String, Member> members, int size){
        this.members = Collections.unmodifiableMap(members);
        this.size = size;
    }

    public static UniformBlockLayout from(List<GLSLProgram.Decl> declarations,
                                          Map<String, Integer> defines,
                                          List<String> warnings){
        Map<String, Member> result = new LinkedHashMap<>();
        int cursor = 0;
        for(GLSLProgram.Decl decl : declarations){
            Primitive primitive = Primitive.of(decl.type, warnings);
            int arrayLength = arrayLength(decl.arraySuffix, defines, warnings);
            int stride = roundUp(primitive.size, 16);
            if(primitive == Primitive.MAT3) stride = 48;
            if(primitive == Primitive.MAT4) stride = 64;
            int offset = roundUp(cursor, primitive.alignment);
            int memberSize = arrayLength > 0 ? stride * arrayLength : primitive.size;
            result.put(decl.name, new Member(decl.name, decl.type, arrayLength, offset,
                memberSize, primitive.alignment, stride));
            cursor = offset + memberSize;
        }
        return new UniformBlockLayout(result, roundUp(cursor, 16));
    }

    public int size(){
        return size;
    }

    public Collection<Member> members(){
        return members.values();
    }

    public int offsetOf(String name){
        Member member = members.get(name);
        if(member == null) throw new IllegalArgumentException("Unknown uniform: " + name);
        return member.offset;
    }

    public Member member(String name){
        Member member = members.get(name);
        if(member == null) throw new IllegalArgumentException("Unknown uniform: " + name);
        return member;
    }

    public ByteBuffer createBuffer(){
        return ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    public void putFloat(ByteBuffer buffer, String name, float value){
        buffer.putFloat(offsetOf(name), value);
    }

    public void putInt(ByteBuffer buffer, String name, int value){
        buffer.putInt(offsetOf(name), value);
    }

    public void putVec2(ByteBuffer buffer, String name, float x, float y){
        int offset = offsetOf(name);
        buffer.putFloat(offset, x);
        buffer.putFloat(offset + 4, y);
    }

    public void putVec3(ByteBuffer buffer, String name, float x, float y, float z){
        int offset = offsetOf(name);
        buffer.putFloat(offset, x);
        buffer.putFloat(offset + 4, y);
        buffer.putFloat(offset + 8, z);
    }

    public void putVec4(ByteBuffer buffer, String name, float x, float y, float z, float w){
        int offset = offsetOf(name);
        buffer.putFloat(offset, x);
        buffer.putFloat(offset + 4, y);
        buffer.putFloat(offset + 8, z);
        buffer.putFloat(offset + 12, w);
    }

    /** Writes a column-major mat4 (the layout Arc already uses). */
    public void putMat4(ByteBuffer buffer, String name, float[] values, int offset){
        int at = offsetOf(name);
        for(int i = 0; i < 16; i++) buffer.putFloat(at + i * 4, values[offset + i]);
    }

    private static int arrayLength(String suffix, Map<String, Integer> defines, List<String> warnings){
        if(suffix.isEmpty()) return 0;
        Matcher matcher = ARRAY.matcher(suffix);
        if(!matcher.find()){
            warnings.add("unparsable uniform array size: " + suffix);
            return 1;
        }
        String token = matcher.group(1);
        try{
            return Integer.parseInt(token);
        }catch(NumberFormatException ignored){
        }
        Integer defined = defines.get(token);
        if(defined == null || defined <= 0){
            warnings.add("unresolved uniform array size: " + token + " (defaulting to 1)");
            return 1;
        }
        return defined;
    }

    private static int roundUp(int value, int alignment){
        return (value + alignment - 1) / alignment * alignment;
    }

    private enum Primitive{
        SCALAR(4, 4),
        VEC2(8, 8),
        VEC3(12, 16),
        VEC4(16, 16),
        MAT3(48, 16),
        MAT4(64, 16),
        UNSUPPORTED(4, 4);

        final int size, alignment;

        Primitive(int size, int alignment){
            this.size = size;
            this.alignment = alignment;
        }

        static Primitive of(String type, List<String> warnings){
            return switch(type){
                case "float", "int", "bool", "uint" -> SCALAR;
                case "vec2", "ivec2", "bvec2", "uvec2" -> VEC2;
                case "vec3", "ivec3", "bvec3", "uvec3" -> VEC3;
                case "vec4", "ivec4", "bvec4", "uvec4" -> VEC4;
                case "mat3" -> MAT3;
                case "mat4" -> MAT4;
                default -> {
                    warnings.add("unsupported uniform type '" + type + "' treated as scalar");
                    yield UNSUPPORTED;
                }
            };
        }
    }
}
