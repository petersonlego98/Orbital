package orbital.graphics;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.pooling.Pools;
import orbital.game.*;
import orbital.ui.Fonts;

import static orbital.Vars.*;

/**
 * Draws the dropped modules of the 8x8 battle royale: a pulsing border in the category colour,
 * the module sprite, an always-on name/type label and a floating effect tooltip while inspected.
 */
public final class LootRenderer{
    private static final float markerHalf = 14f;
    private static @Nullable DroppedModule inspected;
    private static float inspectTimer;

    private LootRenderer(){
    }

    /** Keeps the tooltip alive while the mouse hovers a drop on desktop. */
    public static void hover(DroppedModule drop){
        if(drop == null) return;
        if(inspected != drop){
            inspected = drop;
            inspectTimer = 0.6f;
        }else{
            inspectTimer = Math.max(inspectTimer, 0.4f);
        }
    }

    public static void clearInspection(){
        inspected = null;
        inspectTimer = 0f;
    }

    public static void clearInspection(DroppedModule drop){
        if(inspected == drop) clearInspection();
    }

    /** @return the drop whose effect tooltip is currently visible, or null. */
    public static @Nullable DroppedModule inspected(){
        return inspectTimer > 0f ? inspected : null;
    }

    public static void tick(){
        if(inspectTimer <= 0f) return;
        inspectTimer -= Time.delta;
        if(inspectTimer <= 0f) inspected = null;
    }

    /** Called from the draw trigger; queues markers and labels on their own layers. */
    public static void draw(){
        if(!BattleRoyaleMode.active() || BattleRoyaleLoot.all().isEmpty() || Core.camera == null) return;
        Draw.draw(Layer.effect, LootRenderer::drawMarkers);
        Draw.draw(Layer.playerName + 2, LootRenderer::drawLabels);
    }

    private static void drawMarkers(){
        Rect bounds = Core.camera.bounds(Tmp.r1).grow(48f);
        Lines.stroke(1f);

        for(DroppedModule drop : BattleRoyaleLoot.all()){
            if(!bounds.contains(drop.x, drop.y)) continue;

            float pulse = 0.55f + 0.35f * Mathf.sin(Time.time * 3f + drop.id);
            float size = markerHalf * 2f;

            Draw.color(drop.kind.color, 0.16f);
            Fill.rect(drop.x, drop.y, size + 10f, size + 10f);

            Draw.blend(Blending.additive);
            Draw.color(drop.kind.color, pulse);
            Lines.stroke(2f);
            Lines.rect(drop.x - markerHalf, drop.y - markerHalf, size, size);
            Draw.color(drop.kind.color, pulse * 0.45f);
            Lines.stroke(1f);
            Lines.rect(drop.x - markerHalf - 3f, drop.y - markerHalf - 3f, size + 6f, size + 6f);
            Draw.blend();

            //dark plate first so a light sprite stays readable over any terrain
            Draw.color(0f, 0f, 0f, 0.42f);
            Fill.rect(drop.x, drop.y, markerHalf * 1.8f, markerHalf * 1.8f);

            TextureRegion icon = drop.icon();
            if(icon != null){
                Draw.color(Color.white);
                Draw.rect(icon, drop.x, drop.y, 24f, 24f);
            }else{
                Draw.color(drop.kind.color);
                Fill.rect(drop.x, drop.y, 12f, 12f);
            }
        }
        Draw.color();
        Lines.stroke(1f);
    }

    private static void drawLabels(){
        Rect bounds = Core.camera.bounds(Tmp.r1).grow(48f);
        DroppedModule tooltip = inspected();

        for(DroppedModule drop : BattleRoyaleLoot.all()){
            if(!bounds.contains(drop.x, drop.y)) continue;
            drawWorldText(drop.name() + "\n" + drop.type(), drop.x, drop.y + 30f,
                Layer.playerName + 2, 0.9f);
        }

        if(tooltip != null && bounds.contains(tooltip.x, tooltip.y)){
            String effect = tooltip.effect();
            drawWorldText(tooltip.name() + "\n" + tooltip.type() + (effect.isEmpty() ? "" : "\n" + effect),
                tooltip.x, tooltip.y + 58f, Layer.playerName + 3, 0.8f);
        }
    }

    /**
     * Draws a small outlined label directly through the font. Component classes such as
     * WorldLabelComp are compile-time sources only and are not shipped in the packaged jar, so
     * their static helpers cannot be called from regular game code.
     */
    private static void drawWorldText(String text, float x, float y, float layer, float size){
        if(text == null || text.isEmpty()) return;

        Draw.z(layer);
        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean integers = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        //same scale rule as Drawf.text/player names: world-space text already shrinks with zoom,
        //so an extra camera-scale divisor only made the labels microscopic when zoomed out
        font.getData().setScale(0.25f / Scl.scl(1f) * size);
        layout.setText(font, text);

        float left = x - layout.width / 2f, bottom = y - layout.height / 2f;
        Draw.color(0f, 0f, 0f, 0.4f);
        Fill.rect(left + layout.width / 2f, bottom + layout.height / 2f, layout.width + 5f, layout.height + 5f);
        Draw.color(Color.white);
        font.setColor(Color.white);
        font.draw(text, left, bottom);
        Pools.free(layout);
        Draw.reset();
        //Fonts.outline is shared with every scene2d label (including the FPS overlay): leaving
        //our scale behind is what made the whole HUD text render at the wrong size.
        font.getData().setScale(1f);
        font.setColor(Color.white);
        font.setUseIntegerPositions(integers);
    }
}
