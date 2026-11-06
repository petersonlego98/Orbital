package orbital.ui;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import orbital.gen.*;

/**
 * Visual identity taken from the ORBITAL key art.
 *
 * The palette below comes from measured averages of the supplied poster (see docs/TEMA_ORBITAL.md):
 * deep space #04040c, violet nebula #492a89, silver lettering #979bac, planet blue #5d9fe6,
 * engine cyan #55a9e0 and lower azure #83bff6. The brighter accents (#4fd2ff, #8b4fe0, #ff8a24)
 * are the saturated highlights visible in the same regions, lifted so they stay readable on a dark UI.
 *
 * This theme is intentionally scoped to the opening menu and the loading screen. Pal.accent is
 * left untouched because it carries gameplay meaning all over the world renderer.
 */
public class OrbitalTheme{
    private static final String posterPath = "ui/orbital-poster.png";
    private static final String wordmarkPath = "ui/orbital-wordmark.png";
    private static final String markPath = "ui/orbital-mark.png";

    public static final Color
        space = Color.valueOf("04040c"),
        spaceBlue = Color.valueOf("070d24"),
        nebula = Color.valueOf("492a89"),
        nebulaLight = Color.valueOf("8b4fe0"),
        cyan = Color.valueOf("4fd2ff"),
        cyanDeep = Color.valueOf("1560d8"),
        ice = Color.valueOf("83bff6"),
        silver = Color.valueOf("cfd8f2"),
        steel = Color.valueOf("979bac"),
        amber = Color.valueOf("ff8a24"),
        gold = Color.valueOf("ffd28a");

    private static TextureRegion poster, wordmark, mark;
    private static float time;

    private static TextureRegion region(String path, TextureRegion cached){
        if(cached != null) return cached;
        if(Core.assets == null) return null;
        Texture tex = Core.assets.getOrNull(path, Texture.class);
        return tex == null ? null : new TextureRegion(tex);
    }

    public static TextureRegion poster(){ return poster = region(posterPath, poster); }
    public static TextureRegion wordmark(){ return wordmark = region(wordmarkPath, wordmark); }
    public static TextureRegion mark(){ return mark = region(markPath, mark); }

    /** False when the key art has not been loaded yet (headless runs, early loading screen). */
    public static boolean available(){ return poster() != null; }

    private static Drawable solid(Color color, float alpha){
        if(!(Tex.whiteui instanceof TextureRegionDrawable d)) return Styles.none;
        return d.tint(new Color(color.r, color.g, color.b, alpha));
    }

    /** Translucent navy panel used behind the menu button columns and the attribution banner. */
    public static Drawable panel(){ return solid(spaceBlue, 0.78f); }

    /** Menu button style: silver text, violet hover, electric-blue checked state. */
    public static TextButtonStyle menuButton(){
        return new TextButtonStyle(){{
            font = Fonts.def;
            fontColor = silver;
            overFontColor = Color.white;
            disabledFontColor = Color.gray;
            up = solid(spaceBlue, 0.35f);
            over = solid(nebula, 0.75f);
            down = solid(cyanDeep, 0.85f);
            checked = solid(cyanDeep, 0.70f);
            disabled = solid(spaceBlue, 0.30f);
        }};
    }

    /** Button style for the loading screen's cancel action. */
    public static TextButtonStyle loadButton(){
        return new TextButtonStyle(){{
            font = Fonts.def;
            fontColor = silver;
            overFontColor = Color.white;
            disabledFontColor = Color.gray;
            up = solid(spaceBlue, 0.85f);
            over = solid(nebula, 0.95f);
            down = solid(cyanDeep, 0.95f);
        }};
    }

    /** Opening menu backdrop. */
    public static void renderBackdrop(){
        renderKeyArt(0.44f, 0.56f, true);
    }

    /** Loading screen backdrop: darker and calmer so the progress bar stays the focus. */
    public static void renderLoadingBackdrop(){
        renderKeyArt(0.66f, 0f, true);
    }

    private static void renderKeyArt(float veil, float leftShade, boolean animated){
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        time += Time.delta;

        Draw.reset();
        Draw.color(space);
        Fill.crect(0f, 0f, w, h);

        TextureRegion art = poster();
        if(art != null){
            //cover-scale the square poster, keeping the wordmark and the ship in frame
            float scale = Math.max(w / art.width, h / art.height);
            Draw.color(1f, 1f, 1f, 1f);
            Draw.rect(art, w / 2f, h / 2f, art.width * scale, art.height * scale);
        }

        //Gradients are single quads with per-vertex colors. The old implementation drew strips
        //with a +1px overlap; every seam got double alpha and showed up as a thin vertical line.
        //Vertex interpolation has no strip boundary at all, so the wash is continuous.
        Draw.reset();
        gradient(0f, 0f, w, h, nebula, 0f, 0.12f);

        //deep navy veil: keeps white/silver text legible over the busy poster
        Draw.color(spaceBlue.r, spaceBlue.g, spaceBlue.b, veil);
        Fill.crect(0f, 0f, w, h);

        if(animated){
            //the poster already contains the cyan orbital rings; extra line strokes over it
            //read as stray hairlines, so only the starfield is added here
            drawStarfield(w, h);
        }

        //left side darkening behind the button column: adjacent quads with SHARED endpoint
        //alphas, so the quadratic falloff stays smooth without doubling any boundary pixel
        if(leftShade > 0f){
            float span = w * 0.55f;
            Draw.reset();
            for(int i = 0; i < shadeSegments; i++){
                float[] ends = shadeSegment(i, shadeSegments, leftShade);
                float x0 = span * i / (float)shadeSegments;
                float x1 = span * (i + 1f) / shadeSegments;
                gradient(x0, 0f, x1 - x0, h, space, ends[0], ends[1]);
            }
        }

        Draw.reset();
    }

    /** Number of adjoining quads used for the left shade; more segments = closer to the quadratic curve. */
    public static final int shadeSegments = 16;

    /** Quadratic alpha falloff of the left shade, from 1 at t=0 to 0 at t=1. */
    public static float shadeAlpha(float t, float amount){
        float f = 1f - t;
        return amount * f * f;
    }

    /** Endpoint alphas of one shade quad. Consecutive quads share the same value at their joint. */
    public static float[] shadeSegment(int i, int segments, float amount){
        return new float[]{
            shadeAlpha(i / (float)segments, amount),
            shadeAlpha((i + 1f) / segments, amount)
        };
    }

    /** Draws a horizontal alpha gradient as ONE quad; the mix color must be white/reset beforehand. */
    private static void gradient(float x, float y, float w, float h, Color color, float a0, float a1){
        float c0 = Color.toFloatBits(color.r, color.g, color.b, a0);
        float c1 = Color.toFloatBits(color.r, color.g, color.b, a1);
        Fill.quad(
            x, y, c0,
            x + w, y, c1,
            x + w, y + h, c1,
            x, y + h, c0
        );
    }

    private static void drawStarfield(float w, float h){
        for(int i = 0; i < 76; i++){
            float sx = Mathf.randomSeed(i + 7, 0f, 1f) * w;
            float sy = Mathf.randomSeed(i + 911, 0f, 1f) * h;
            float twinkle = Mathf.absin(time * 0.045f + i * 1.7f, 1f, 1f);
            float size = Mathf.randomSeed(i + 31, 0.7f, 2.0f) * Scl.scl(1f);
            Draw.color(ice.r, ice.g, ice.b, 0.12f + 0.42f * twinkle);
            Fill.circle(sx, sy, size);
        }
    }

}
