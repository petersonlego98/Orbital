package orbital.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.ui.*;

import static arc.Core.*;

/**
 * Boot loading screen.
 *
 * This replaces the previous diagnostic grid (Pal.accent squares, stencil-clipped
 * panes with per-core bars, a bloom pass and an off-screen FxProcessor) with the
 * ORBITAL space theme: a deep-space gradient, a nebula wash, a twinkling starfield
 * and the orbital emblem - planet, ring, moon and sparkle - with a single progress
 * bar below it.
 *
 * Everything is drawn with plain primitives, no textures: this renderer runs while
 * the asset manager is still loading, so neither the key art nor most fonts are
 * guaranteed to exist yet. Text appears as soon as {@link Fonts#tech} is loaded.
 *
 * That constraint rules out {@code Fill.circle} (it does {@code atlas.find("circle")},
 * and the atlas is only a blank atlas at this point - see the IllegalArgumentException
 * that used to abort the launch) and {@code Draw.rect(TextureRegion...)}. Only the
 * atlas-independent primitives are used here: {@link Fill#light} (its own mesh),
 * {@link Fill#quad}/{@link Fill#poly} (white region of the blank atlas) and
 * {@link Lines} (line shader).
 *
 * It also removes the framebuffer entirely (no {@code FxProcessor}, no bloom, no
 * stencil), which drops a whole class of "no rgba8888 + stencil buffer" fallbacks
 * and the per-frame off-screen pass.
 */
public class LoadRenderer implements Disposable{
    private static final int starCount = 130;

    //screen-space time for the starfield twinkle; the engine clock is already running here
    private float time = 0f;

    /** Nothing to dispose: no mesh, no framebuffer, no filters. Kept for ClientLauncher. */
    @Override
    public void dispose(){
    }

    public void draw(){
        time += Time.delta;

        float w = graphics.getWidth(), h = graphics.getHeight(), s = Scl.scl();
        Draw.proj().setOrtho(0, 0, w, h);
        graphics.clear(Color.black);

        drawSpace(w, h);
        Draw.flush();

        drawEmblem(w, h, s);
        Draw.flush();

        drawWordmark(w, h, s);
        drawProgress(w, h, s);

        Draw.reset();
    }

    // ---------- backdrop ----------

    private void drawSpace(float w, float h){
        Draw.reset();

        //vertical deep-space wash: one quad, per-vertex colours, no seams (OrbitalTheme does the same)
        Fill.quad(
            0, 0, Color.toFloatBits(OrbitalTheme.spaceBlue.r, OrbitalTheme.spaceBlue.g, OrbitalTheme.spaceBlue.b, 1f),
            w, 0, Color.toFloatBits(OrbitalTheme.space.r, OrbitalTheme.space.g, OrbitalTheme.space.b, 1f),
            w, h, Color.toFloatBits(0.020f, 0.020f, 0.058f, 1f),
            0, h, Color.toFloatBits(0.035f, 0.039f, 0.098f, 1f)
        );

        //nebula blobs: radial light gradients, the same primitive the old screen used for its glow
        Draw.reset();
        light(w * 0.19f, h * 0.80f, Math.max(w, h) * 0.40f, 0.55f, OrbitalTheme.nebula);
        light(w * 0.84f, h * 0.72f, Math.max(w, h) * 0.30f, 0.34f, OrbitalTheme.nebulaLight);
        light(w * 0.34f, h * 0.14f, Math.max(w, h) * 0.36f, 0.30f, OrbitalTheme.cyanDeep);

        //deterministic starfield: identical every launch, so it never "shifts" between runs
        Draw.reset();
        for(int i = 0; i < starCount; i++){
            float sx = Mathf.randomSeed(i * 3 + 11, 0f, 1f) * w;
            float sy = Mathf.randomSeed(i * 3 + 12, 0f, 1f) * h;
            float size = Mathf.randomSeed(i + 41, 0.7f, 1.9f) * Scl.scl(1f);
            //pull stars away from the centre so the emblem stays readable
            float dist = Mathf.dst(sx, sy, w / 2f, h * 0.56f);
            float fade = Mathf.clamp((dist - Math.min(w, h) * 0.20f) / (Math.min(w, h) * 0.18f));
            float twinkle = Mathf.absin(time * 0.045f + i * 1.7f, 1f);
            Draw.color(i % 5 == 0 ? OrbitalTheme.ice : OrbitalTheme.silver, (0.08f + 0.5f * twinkle) * fade);
            disc(sx, sy, size);
        }
    }

    /**
     * Filled disc built from {@link Fill#poly}: the atlas only has its blank white region
     * this early, so {@code Fill.circle} ("circle" region) would throw.
     */
    private static void disc(float x, float y, float radius){
        Fill.poly(x, y, discVertices(radius), radius);
    }

    /** Fewer sides for tiny discs: 100+ stars would otherwise cost thousands of vertices. */
    private static int discVertices(float radius){
        if(radius < 3f) return 5;
        if(radius < 8f) return 8;
        if(radius < 24f) return 20;
        return 64;
    }

    /** Soft radial glow centred on (x, y); {@link Fill#light} with alpha in the inner colour. */
    private void light(float x, float y, float radius, float alpha, Color color){
        Tmp.c1.set(color.r, color.g, color.b, alpha);
        Tmp.c2.set(color.r, color.g, color.b, 0f);
        Fill.light(x, y, 24, radius, Tmp.c1, Tmp.c2);
    }

    // ---------- orbital emblem ----------

    /**
     * Planet + tilted ring + moon + sparkle, drawn in the same spirit as the app icon
     * (tools/ArtConverter.drawIcon), but vectorised: no texture is available this early.
     */
    private void drawEmblem(float w, float h, float s){
        float cx = w / 2f, cy = h * 0.56f;
        float r = Math.min(Math.min(w, h) * 0.115f, 170f * s);
        float rx = r * 1.62f, ry = r * 0.52f, rot = -18f;

        //halo behind the disc
        Draw.reset();
        light(cx, cy, r * 2.3f, 0.30f, OrbitalTheme.cyan);

        //ring, far half (behind the planet): dimmer
        Draw.reset();
        ring(cx, cy, rx, ry, rot, 184f, 178f, OrbitalTheme.cyan, 0.45f, 5f * s);
        Draw.flush();

        //planet: one radial light instead of stacked discs, so it reads as a shaded sphere
        //(overlapping circles looked like three stickers, not a body)
        Draw.reset();
        Fill.light(cx, cy, 48, r, 0.60f, Tmp.c1.set(OrbitalTheme.ice).a(1f), Tmp.c2.set(0.043f, 0.051f, 0.106f, 1f));
        Draw.flush();

        Lines.stroke(2.4f * s);
        Draw.color(OrbitalTheme.ice.r, OrbitalTheme.ice.g, OrbitalTheme.ice.b, 0.85f);
        Lines.circle(cx, cy, r);
        Draw.flush();

        //ring, near half (in front of the planet): full alpha, warm tip
        Draw.reset();
        ring(cx, cy, rx, ry, rot, 2f, 92f, OrbitalTheme.cyan, 1f, 5.6f * s);
        ring(cx, cy, rx, ry, rot, 94f, 34f, OrbitalTheme.amber, 0.85f, 5.2f * s);
        ring(cx, cy, rx * 0.80f, ry * 0.80f, rot, 4f, 60f, Color.white, 0.30f, 1.4f * s);
        Draw.flush();

        //moon + sparkle, scaled with the emblem and hidden when there is no room for them
        Draw.reset();
        if(r > 60f * s){
            float mx = cx + rx * 1.05f, my = cy + ry * 1.45f, mr = r * 0.22f;
            //same radial-light treatment as the planet
            Fill.light(mx, my, 24, mr, 0.55f, Tmp.c1.set(OrbitalTheme.steel).a(1f), Tmp.c2.set(0.086f, 0.098f, 0.157f, 1f));
            Draw.flush();

            //four-point sparkle, mirrored below the emblem
            Draw.color(Color.white, 0.85f);
            float sp = r * 0.42f, sx = cx - rx * 1.15f, sy = cy + ry * 1.55f;
            disc(sx, sy, sp * 0.22f);
            Lines.stroke(1.8f * s);
            Lines.line(sx - sp, sy, sx + sp, sy);
            Lines.line(sx, sy - sp, sx, sy + sp);
        }
        Draw.reset();
    }

    /** Arc of a rotated ellipse, walked in small segments so it reads as one stroke of colour. */
    private void ring(float cx, float cy, float rx, float ry, float rot, float start, float extent, Color color, float alpha, float thickness){
        Lines.stroke(thickness);
        Draw.color(color.r, color.g, color.b, alpha);

        FloatSeq seq = new FloatSeq();
        int steps = Math.max(12, (int)(extent / 4f));
        float cr = Mathf.cos(rot * Mathf.degRad), sr = Mathf.sin(rot * Mathf.degRad);
        for(int i = 0; i <= steps; i++){
            float ang = (start + extent * (i / (float)steps)) * Mathf.degRad;
            float ex = Mathf.cos(ang) * rx, ey = Mathf.sin(ang) * ry;
            seq.add(cx + ex * cr - ey * sr, cy + ex * sr + ey * cr);
        }
        Lines.polyline(seq, false);
    }

    // ---------- text and progress ----------

    private void drawWordmark(float w, float h, float s){
        Font font = Fonts.tech;
        if(font == null) return;

        Draw.flush();
        font.setColor(OrbitalTheme.silver);
        font.getData().setScale(s * 2.6f);

        //spaced letters read as a wordmark without needing a display typeface
        GlyphLayout layout = GlyphLayout.obtain();
        layout.setText(font, "O R B I T A L");
        font.draw("O R B I T A L", w / 2f - layout.width / 2f, h * 0.40f);
        layout.free();
        font.getData().setScale(1f);
        Draw.flush();
    }

    private void drawProgress(float w, float h, float s){
        float barW = Math.min(w * 0.62f, 620f * s), barH = 22f * s;
        float bx = w / 2f - barW / 2f, by = h * 0.30f;

        Draw.reset();
        Draw.color(OrbitalTheme.spaceBlue.r, OrbitalTheme.spaceBlue.g, OrbitalTheme.spaceBlue.b, 0.62f);
        Fill.rect(bx + barW / 2f, by + barH / 2f, barW, barH);
        Draw.flush();

        Lines.stroke(2f * s);
        Draw.color(OrbitalTheme.cyan.r, OrbitalTheme.cyan.g, OrbitalTheme.cyan.b, 0.85f);
        Lines.rect(bx, by, barW, barH);
        Draw.flush();

        float p = Mathf.clamp(assets == null ? 0f : assets.getProgress());
        float fw = barW * p;
        if(fw > 1f){
            Draw.reset();
            //cyan -> ice sweep, with an amber leading edge so the fill direction is obvious
            Fill.quad(
                bx, by, Color.toFloatBits(OrbitalTheme.cyanDeep.r, OrbitalTheme.cyanDeep.g, OrbitalTheme.cyanDeep.b, 1f),
                bx + fw, by, Color.toFloatBits(OrbitalTheme.ice.r, OrbitalTheme.ice.g, OrbitalTheme.ice.b, 1f),
                bx + fw, by + barH, Color.toFloatBits(OrbitalTheme.cyan.r, OrbitalTheme.cyan.g, OrbitalTheme.cyan.b, 1f),
                bx, by + barH, Color.toFloatBits(OrbitalTheme.cyan.r, OrbitalTheme.cyan.g, OrbitalTheme.cyan.b, 1f)
            );
            Draw.flush();

            Draw.color(OrbitalTheme.amber.r, OrbitalTheme.amber.g, OrbitalTheme.amber.b, 0.9f);
            Fill.rect(bx + fw, by + barH / 2f, 3f * s, barH * 1.5f);
            Draw.flush();
        }

        Font font = Fonts.tech;
        if(font == null) return;

        font.setColor(OrbitalTheme.cyan);
        font.getData().setScale(s * 1.5f);
        GlyphLayout layout = GlyphLayout.obtain();

        //status line: the localised "loading..." label, plus what is being loaded right now
        String status = Core.bundle.get("loading");
        String name = assets != null && assets.getCurrentLoading() != null
            ? assets.getCurrentLoading().fileName.toLowerCase() : null;
        String line = name == null ? status : status + "   " + name.replace("/", "::");

        font.setColor(OrbitalTheme.silver);
        layout.setText(font, line);
        font.draw(line, w / 2f - layout.width / 2f, by - 18f * s);

        String pct = Mathf.round(p * 100f) + "%";
        font.setColor(OrbitalTheme.silver);
        layout.setText(font, pct);
        font.draw(pct, w / 2f - layout.width / 2f, by + barH + 12f * s);

        layout.free();
        font.getData().setScale(1f);
        Draw.flush();
    }
}
