package orbital.desktop.vulkan.gl;

import java.util.*;
import java.util.regex.*;

/**
 * Translates Arc's legacy GLSL ES 1.00 shader pair into Vulkan-compatible GLSL 450.
 *
 * <p>This is the shader half of the GL20-to-Vulkan port. It deliberately emits GLSL 450 source,
 * not SPIR-V: compiling to SPIR-V still requires an external compiler (glslang/shaderc), which is
 * wired separately. The translation is deterministic so that a given shader pair always produces
 * the same descriptor set, attribute locations and UBO layout.</p>
 *
 * <p>Limitations are reported in {@link Result#warnings}; in particular Vulkan's framebuffer
 * origin differs from OpenGL's and point-size/point-coord features need explicit device support.</p>
 */
public final class GLSLProgram{
    public static final int UNIFORM_SET = 0;
    public static final int UNIFORM_BINDING = 0;
    public static final int SAMPLER_BINDING_BASE = 1;
    public static final String FRAG_COLOR_NAME = "orbital_fragColor";
    public static final String UNIFORM_BLOCK_NAME = "OrbitalUniforms";

    // Arc hands this bridge two different dialects. On a GL2 path (Core.gl30 == null) it prepends
    // its precision prologue and keeps the GLSL ES 1.00 keywords; on a GL3 path it rewrites the
    // same source into "#version 300 es" with in/out qualifiers. Both are accepted here and lowered
    // to the same GLSL 450 declarations below.
    private static final Pattern DECLARATION = Pattern.compile(
        "^\\s*(attribute|varying|uniform|in|out)\\s+((?:(?:lowp|mediump|highp)\\s+)?[A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(\\[[^;]*\\])?\\s*;\\s*(?://.*)?$");
    private static final Pattern DEFINE = Pattern.compile("^\\s*#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([0-9]+)\\s*$");
    private static final Pattern PRECISION = Pattern.compile("^\\s*precision\\s+.*;\\s*$");
    private static final Pattern VERSION = Pattern.compile("^\\s*#version\\s+.*$");
    private static final Pattern EXTENSION = Pattern.compile("^\\s*#extension\\s+.*$");
    // Every conditional directive Arc's prologue can open: #ifdef GL_ES / #ifndef GL_ES / #else /
    // #endif. Vulkan GLSL 450 needs none of them once the precision qualifiers are gone.
    private static final Pattern CONDITIONAL = Pattern.compile("^\\s*#(ifdef|ifndef|if|else|elif|endif)\\b.*$");

    /** Precision qualifier macros Arc defines empty: {@code #define lowp } and friends. */
    private static final Pattern PRECISION_MACRO = Pattern.compile(
        "^\\s*#define\\s+(lowp|mediump|highp)\\s*$");

    private GLSLProgram(){
    }

    /** A single GLSL declaration. */
    public static final class Decl{
        public final String kind, type, name;
        public final String arraySuffix;

        Decl(String kind, String type, String name, String arraySuffix){
            this.kind = kind;
            this.type = type;
            this.name = name;
            this.arraySuffix = arraySuffix == null ? "" : arraySuffix;
        }

        public boolean isSampler(){
            return type.startsWith("sampler");
        }

        /** Fully-qualified declaration without the leading keyword. */
        public String declaration(){
            return type + " " + name + arraySuffix + ";";
        }
    }

    public static final class Result{
        public final String vertexSource, fragmentSource;
        public final Map<String, Integer> attributeLocations;
        public final Map<String, Integer> varyingLocations;
        public final Map<String, Integer> samplerBindings;
        public final UniformBlockLayout uniformLayout;
        /**
         * Components the shader declares for each attribute. Arc points an attribute at a buffer
         * that carries fewer components than the shader declares - a vec4 attribute fed by two
         * floats, for instance - and OpenGL fills the missing ones with zeros and a final 1.0.
         * Vulkan fills them with zeros, which makes w == 0 and every vertex divide to infinity,
         * so the caller needs these counts to fill the gap itself.
         */
        public final Map<String, Integer> attributeComponents;
        public final List<String> warnings;

        Result(String vertexSource, String fragmentSource, Map<String, Integer> attributeLocations,
               Map<String, Integer> varyingLocations, Map<String, Integer> samplerBindings,
               UniformBlockLayout uniformLayout, Map<String, Integer> attributeComponents,
               List<String> warnings){
            this.vertexSource = vertexSource;
            this.fragmentSource = fragmentSource;
            this.attributeLocations = Collections.unmodifiableMap(attributeLocations);
            this.varyingLocations = Collections.unmodifiableMap(varyingLocations);
            this.samplerBindings = Collections.unmodifiableMap(samplerBindings);
            this.uniformLayout = uniformLayout;
            this.attributeComponents = Collections.unmodifiableMap(attributeComponents);
            this.warnings = Collections.unmodifiableList(warnings);
        }
    }

    private static final class Parsed{
        final List<String> lines = new ArrayList<>();
        final List<Decl> attributes = new ArrayList<>();
        final List<Decl> varyings = new ArrayList<>();
        final List<Decl> samplers = new ArrayList<>();
        final List<Decl> uniforms = new ArrayList<>();
        final Map<String, Integer> defines = new LinkedHashMap<>();
        final Set<String> unsupported = new LinkedHashSet<>();
        boolean usesFragColor;
        int firstUniformLine = -1;
        /** The fragment shader's own output declaration, when it declares one explicitly. */
        Decl output;
    }

    public static Result translate(String vertexSource, String fragmentSource){
        return translate(vertexSource, fragmentSource, Map.of());
    }

    /**
     * Translates a shader pair, honouring explicit {@code glBindAttribLocation} overrides.
     * The override is emitted as the attribute's {@code layout(location = ...)} and reported back in
     * {@link Result#attributeLocations}, so the caller can drive vertex pointer setup with the same
     * numbering the vertex shader uses.
     */
    public static Result translate(String vertexSource, String fragmentSource,
                                   Map<String, Integer> attributeOverrides){
        Parsed vertex = parse(vertexSource, true);
        Parsed fragment = parse(fragmentSource, false);
        List<String> warnings = new ArrayList<>();

        Map<String, Integer> attributeLocations = assign(vertex.attributes, 0, "attribute", warnings);
        if(attributeOverrides != null && !attributeOverrides.isEmpty()){
            for(Decl decl : vertex.attributes){
                Integer location = attributeOverrides.get(decl.name);
                if(location != null && location >= 0){
                    attributeLocations.put(decl.name, location);
                }
            }
        }
        Map<String, Integer> varyingLocations = assignVaryings(vertex.varyings, fragment.varyings, warnings);

        List<Decl> allSamplers = merge(vertex.samplers, fragment.samplers, "sampler", warnings);
        Map<String, Integer> samplerBindings = new LinkedHashMap<>();
        for(int i = 0; i < allSamplers.size(); i++){
            samplerBindings.put(allSamplers.get(i).name, SAMPLER_BINDING_BASE + i);
        }

        List<Decl> allUniforms = merge(vertex.uniforms, fragment.uniforms, "uniform", warnings);
        Map<String, Integer> defines = new LinkedHashMap<>(vertex.defines);
        defines.putAll(fragment.defines);
        UniformBlockLayout layout = UniformBlockLayout.from(allUniforms, defines, warnings);

        for(String s : vertex.unsupported) warnings.add("vertex: " + s);
        for(String s : fragment.unsupported) warnings.add("fragment: " + s);
        if(fragment.usesFragColor){
            warnings.add("gl_FragColor rewritten to " + FRAG_COLOR_NAME
                + "; Vulkan framebuffer origin is top-left - Y flip is required.");
        }
        if(fragmentSource.contains("gl_PointCoord") || vertexSource.contains("gl_PointSize")){
            warnings.add("point rasterization requires the Vulkan pointSize feature and a point-list topology.");
        }

        return new Result(
            emit(vertex, true, attributeLocations, varyingLocations, samplerBindings, allSamplers, allUniforms, layout, fragment.usesFragColor),
            emit(fragment, false, attributeLocations, varyingLocations, samplerBindings, allSamplers, allUniforms, layout, fragment.usesFragColor),
            attributeLocations, varyingLocations, samplerBindings, layout,
            attributeComponents(vertex), warnings);
    }

    /**
     * Components the vertex shader declares for each attribute, by name. Vulkan does not pad a
     * short vertex attribute to the shader's declared width the way OpenGL does, so the count has
     * to travel to the vertex packer.
     */
    private static Map<String, Integer> attributeComponents(Parsed vertex){
        Map<String, Integer> result = new LinkedHashMap<>();
        for(Decl decl : vertex.attributes){
            Integer components = componentsOf(decl.type);
            if(components != null) result.put(decl.name, components);
        }
        return result;
    }

    /** Components of a GLSL vector type, or null when it is not an attribute-sized vector. */
    static Integer componentsOf(String type){
        return switch(type){
            case "float" -> 1;
            case "vec2" -> 2;
            case "vec3" -> 3;
            case "vec4" -> 4;
            default -> null;
        };
    }

    private static Parsed parse(String source, boolean isVertex){
        Parsed parsed = new Parsed();
        for(String raw : source.split("\\r?\\n", -1)){
            String line = raw;
            if(VERSION.matcher(line).matches()){
                parsed.lines.add("");
                continue;
            }
            if(EXTENSION.matcher(line).matches()){
                parsed.unsupported.add("dropped #extension directive: " + line.trim());
                parsed.lines.add("");
                continue;
            }
            if(PRECISION.matcher(line).matches()){
                parsed.lines.add("");
                continue;
            }
            Matcher define = DEFINE.matcher(line);
            if(define.matches()){
                parsed.defines.put(define.group(1), Integer.parseInt(define.group(2)));
            }

            Matcher declaration = DECLARATION.matcher(line);
            if(declaration.matches()){
                String kind = declaration.group(1);
                String type = declaration.group(2).replaceAll("\\b(lowp|mediump|highp)\\s+", "").trim();
                String name = declaration.group(3);
                String array = declaration.group(4) == null ? "" : declaration.group(4).trim();
                Decl decl = new Decl(kind, type, name, array);
                // GLSL 450 has no "varying": a vertex "in" is an attribute, a vertex "out" and a
                // fragment "in" are varyings, and a fragment "out" is the framebuffer output.
                switch(kind){
                    case "attribute" -> {
                        if(isVertex) parsed.attributes.add(decl);
                        else parsed.unsupported.add("attribute in fragment shader: " + name);
                    }
                    case "varying" -> parsed.varyings.add(decl);
                    case "in" -> {
                        if(isVertex) parsed.attributes.add(decl);
                        else parsed.varyings.add(decl);
                    }
                    case "out" -> {
                        if(isVertex) parsed.varyings.add(decl);
                        else parsed.output = decl; // the framebuffer output, emitted with location 0
                    }
                    case "uniform" -> {
                        if(decl.isSampler()) parsed.samplers.add(decl);
                        else{
                            parsed.uniforms.add(decl);
                            if(parsed.firstUniformLine < 0) parsed.firstUniformLine = parsed.lines.size();
                        }
                    }
                    default -> throw new IllegalStateException(kind);
                }
                // a fragment "out" is re-emitted with an explicit location below, so it is blanked
                // here as well: leaving "out vec4 fragColor;" in the body would be a second output
                // without a location, which SPIR-V rejects
                parsed.lines.add(null);
                continue;
            }
            parsed.lines.add(line);
        }
        parsed.usesFragColor = source.contains("gl_FragColor");
        return parsed;
    }

    private static Map<String, Integer> assign(List<Decl> decls, int base, String what, List<String> warnings){
        Map<String, Integer> result = new LinkedHashMap<>();
        for(Decl decl : decls){
            if(result.containsKey(decl.name)){
                warnings.add("duplicate " + what + " declaration: " + decl.name);
                continue;
            }
            result.put(decl.name, base + result.size());
        }
        return result;
    }

    private static Map<String, Integer> assignVaryings(List<Decl> vertex, List<Decl> fragment, List<String> warnings){
        Map<String, Integer> result = assign(vertex, 0, "varying", warnings);
        Set<String> vertexNames = new HashSet<>(result.keySet());
        for(Decl decl : fragment){
            if(!result.containsKey(decl.name)){
                result.put(decl.name, result.size());
                warnings.add("fragment varying has no vertex output: " + decl.name);
            }
        }
        for(String name : vertexNames){
            boolean found = false;
            for(Decl decl : fragment){
                if(decl.name.equals(name)){
                    found = true;
                    break;
                }
            }
            if(!found) warnings.add("vertex varying has no fragment input: " + name);
        }
        return result;
    }

    private static List<Decl> merge(List<Decl> a, List<Decl> b, String what, List<String> warnings){
        Map<String, Decl> result = new LinkedHashMap<>();
        for(Decl decl : a) result.put(decl.name, decl);
        for(Decl decl : b){
            Decl existing = result.get(decl.name);
            if(existing == null){
                result.put(decl.name, decl);
            }else if(!existing.type.equals(decl.type) || !existing.arraySuffix.equals(decl.arraySuffix)){
                warnings.add("conflicting " + what + " declarations across stages: " + decl.name);
            }
        }
        return new ArrayList<>(result.values());
    }

    private static String emit(Parsed parsed, boolean isVertex,
                               Map<String, Integer> attributeLocations,
                               Map<String, Integer> varyingLocations,
                               Map<String, Integer> samplerBindings,
                               List<Decl> allSamplers, List<Decl> allUniforms,
                               UniformBlockLayout layout, boolean usesFragColor){
        StringBuilder declarations = new StringBuilder();
        if(!isVertex){
            if(parsed.output != null){
                declarations.append("layout(location = 0) out ")
                    .append(parsed.output.declaration()).append('\n');
            }else if(usesFragColor){
                declarations.append("layout(location = 0) out vec4 ").append(FRAG_COLOR_NAME).append(";\n");
            }
        }
        declarations.append("layout(set = ").append(UNIFORM_SET)
            .append(", binding = ").append(UNIFORM_BINDING)
            .append(", std140) uniform ").append(UNIFORM_BLOCK_NAME).append(" {\n");
        if(allUniforms.isEmpty()){
            declarations.append("    float orbital_unused;\n");
        }else{
            for(Decl decl : allUniforms){
                declarations.append("    ").append(uboDeclaration(decl, layout)).append('\n');
            }
        }
        declarations.append("};\n");
        declarations.append(declarationsFor(isVertex ? parsed.attributes : List.of(), attributeLocations, isVertex ? "in" : null));
        declarations.append(declarationsFor(parsed.varyings, varyingLocations, isVertex ? "out" : "in"));
        declarations.append(samplerDeclarations(allSamplers, samplerBindings));

        // Arc's Shader#preprocess wraps every shader in a conditional block whose only purpose is
        // the GLSL ES precision qualifiers:
        //
        //     #ifdef GL_ES
        //     precision highp float;
        //     precision mediump int;
        //     #else
        //     #define lowp
        //     #define mediump
        //     #define highp
        //     #endif
        //
        // or, on the GL2 path, the shorter "#ifndef GL_ES / #define lowp ... / #endif". None of it
        // exists in GLSL 450. A whole block is dropped only when every #define inside it is an
        // empty precision macro: a block that gives a macro the shader actually uses (a uniform
        // array size, a constant) is kept verbatim, because dropping it would change meaning.
        // Precision lines were already blanked by parse().
        StringBuilder prologue = new StringBuilder();
        StringBuilder body = new StringBuilder();
        boolean pastPrologue = false;
        int conditionalDepth = 0;
        for(int index = 0; index < parsed.lines.size(); index++){
            String raw = parsed.lines.get(index);
            if(raw == null) continue;
            String trimmed = raw.trim();
            if(pastPrologue){
                body.append(rewrite(raw)).append('\n');
                continue;
            }
            if(trimmed.isEmpty()) continue;
            if(trimmed.startsWith("#define")){
                if(PRECISION_MACRO.matcher(raw).matches()) continue; // empty precision macro
                prologue.append(raw).append('\n');
                continue;
            }
            if(trimmed.startsWith("#")){
                if(conditionalDepth == 0 && CONDITIONAL.matcher(trimmed).matches()
                        && opensConditional(trimmed)){
                    if(blockDefinesNothing(parsed.lines, index)){
                        // skip the whole block, including its matching #endif
                        int depth = 0;
                        for(; index < parsed.lines.size(); index++){
                            String line = parsed.lines.get(index);
                            if(line == null) continue;
                            String t = line.trim();
                            if(opensConditional(t)) depth++;
                            else if(t.startsWith("#endif")){
                                depth--;
                                if(depth == 0) break;
                            }
                        }
                        continue;
                    }
                }
                if(opensConditional(trimmed)) conditionalDepth++;
                if(trimmed.startsWith("#endif")) conditionalDepth = Math.max(0, conditionalDepth - 1);
                prologue.append(raw).append('\n');
                continue;
            }
            pastPrologue = true;
            body.append(rewrite(raw)).append('\n');
        }

        return "#version 450\n" + prologue + declarations + body;
    }

    /** True for the directives that open a conditional block. */
    private static boolean opensConditional(String trimmed){
        return trimmed.startsWith("#if");
    }

    /**
     * True when the conditional block starting at {@code start} defines nothing the shader can
     * use, which is Arc's precision wrapper and nothing else. An empty {@code #define lowp} is
     * exactly what makes the block droppable.
     */
    private static boolean blockDefinesNothing(List<String> lines, int start){
        int depth = 0;
        for(int index = start; index < lines.size(); index++){
            String line = lines.get(index);
            if(line == null) continue;
            String trimmed = line.trim();
            if(opensConditional(trimmed)){
                depth++;
                continue;
            }
            if(trimmed.startsWith("#endif")){
                depth--;
                if(depth == 0) return true;
                continue;
            }
            if(depth == 1 && trimmed.startsWith("#define")
                    && !PRECISION_MACRO.matcher(line).matches()){
                return false; // a macro with a definition the shader can use
            }
        }
        return true; // unterminated, so not worth keeping either
    }

    /**
     * Uniform array sizes are emitted as resolved integer literals. A #define that supplies the
     * size may exist in only one stage, while the UBO is emitted identically in both stages.
     */
    private static String uboDeclaration(Decl decl, UniformBlockLayout layout){
        if(decl.arraySuffix.isEmpty()) return decl.declaration();
        int length = layout.member(decl.name).arrayLength;
        return decl.type + " " + decl.name + "[" + length + "];";
    }

    private static String declarationsFor(List<Decl> decls, Map<String, Integer> locations, String direction){
        if(decls.isEmpty() || direction == null) return "";
        StringBuilder result = new StringBuilder();
        for(Decl decl : decls){
            Integer location = locations.get(decl.name);
            if(location == null) continue;
            result.append("layout(location = ").append(location).append(") ")
                .append(direction).append(' ').append(decl.declaration()).append('\n');
        }
        return result.toString();
    }

    private static String samplerDeclarations(List<Decl> decls, Map<String, Integer> bindings){
        StringBuilder result = new StringBuilder();
        for(Decl decl : decls){
            Integer binding = bindings.get(decl.name);
            if(binding == null) continue;
            result.append("layout(set = ").append(UNIFORM_SET)
                .append(", binding = ").append(binding).append(") uniform ")
                .append(decl.declaration()).append('\n');
        }
        return result.toString();
    }

    private static String rewrite(String line){
        return line
            .replaceAll("\\btexture2DProj\\s*\\(", "textureProj(")
            .replaceAll("\\btexture2DLod\\s*\\(", "textureLod(")
            .replaceAll("\\btexture2D\\s*\\(", "texture(")
            .replaceAll("\\btextureCube\\s*\\(", "texture(")
            .replaceAll("\\bgl_FragColor\\b", FRAG_COLOR_NAME)
            .replaceAll("\\b(lowp|mediump|highp)\\s+", "");
    }
}
