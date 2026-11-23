package orbital.ui;

import arc.*;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.input.KeyCode;
import arc.math.*;
import arc.math.geom.Vec2;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import orbital.graphics.Pal;

import static orbital.gen.Tex.*;

/**
 * On-screen analog stick for touch controls.
 *
 * <p>The knob is grabbed anywhere inside the ring and follows the finger up to its rim; the
 * published vector is normalized, so a half tilt is a half throttle. Two extra affordances
 * make it usable without looking at it: grabbing the knob <b>outside</b> the ring still
 * steers (the vector is clamped to the rim), and the stick re-centers under the finger when it
 * is grabbed again, so the player never has to find a small target.</p>
 *
 * <p>The widget writes into a {@link Vec2} sink - normally
 * {@link orbital.input.MobileInput#analogVector} - and only while a finger is on it. Nothing
 * else in the game reads the touch screen for movement while it is live, so dragging the map
 * with a second finger keeps working exactly as before.</p>
 */
public class AnalogStick extends Table{
    /** Maximum knob travel, in raw scene pixels, before the vector saturates. */
    public final float radius;
    /** Drawn knob radius, in raw scene pixels. */
    public final float knobRadius;

    /** Total widget side, in raw scene pixels. */
    private final float widgetSize;

    private final Vec2 sink;
    /** Live knob offset from the ring centre, in raw scene pixels. */
    private final Vec2 knob = new Vec2();
    private final Runnable active;

    private boolean pressed;
    private int pointer = -1;
    private float originX, originY;

    /**
     * @param sink vector the stick writes into; (0, 0) when untouched.
     * @param active called when the grabbed state changes, so the input handler can tell a
     *               live stick from an idle one.
     */
    public AnalogStick(Vec2 sink, Runnable active){
        this(sink, active, 120f, 44f, 46f);
    }

    /**
     * @param size widget side, in ui units
     * @param radius maximum knob travel, in ui units
     * @param knobRadius drawn knob radius, in ui units
     */
    public AnalogStick(Vec2 sink, Runnable active, float size, float radius, float knobRadius){
        this.sink = sink;
        this.active = active;
        //input coordinates and drawing happen in raw scene pixels. Cell sizes, in contrast, are
        //scaled by Arc when they are laid out, so the two kinds of values are converted here.
        this.widgetSize = Scl.scl(size);
        this.radius = Scl.scl(radius);
        this.knobRadius = Scl.scl(knobRadius);

        background(orbital.gen.Tex.pane);
        touchable = Touchable.enabled;
        add(new Element(){
            @Override
            public float getPrefWidth(){
                return size;
            }

            @Override
            public float getPrefHeight(){
                return size;
            }
        }).size(size);

        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointerId, KeyCode button){
                if(pressed) return false;

                pressed = true;
                pointer = pointerId;
                originX = x;
                originY = y;
                //the ring is centred in the widget, so the grab point is relative to it
                grab(x, y);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointerId){
                if(!pressed || pointerId != pointer) return;
                drag(x, y);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointerId, KeyCode button){
                if(!pressed || pointerId != pointer) return;
                release();
            }
        });
    }

    /** Total widget side, in raw scene pixels. */
    public float size(){
        return widgetSize;
    }

    /** @return the current steering vector, normalized; never null. */
    public Vec2 vector(){
        return knob;
    }

    /** @return whether a finger is on the stick right now. */
    public boolean active(){
        return pressed;
    }

    private void grab(float x, float y){
        //drag() publishes the vector and the live/grabbed state together
        drag(x, y);
    }

    private void drag(float x, float y){
        //the grab point is kept, so dragging back toward the centre half-commits instead of
        //jumping the knob to the rim
        knob.set(x - originX, y - originY);
        //clamp to the rim: the vector is the knob over the radius, so it is already normalized
        if(knob.len() > radius) knob.setLength(radius);

        //scene coordinates have Y growing upward, just like world coordinates: pushing the
        //knob up must publish +Y. Negating here made the unit drive downwards.
        sink.set(knob.x / radius, knob.y / radius);
        //publish the vector and the state on every move. A finger that lands exactly on the
        //centre writes a zero vector on touch-down, so publishing only there would leave the
        //handler believing the stick was idle for the whole gesture - the unit would never
        //answer it, which is the bug this fixes.
        active.run();
    }

    private void release(){
        pressed = false;
        pointer = -1;
        knob.setZero();
        sink.setZero();
        active.run();
    }

    @Override
    public void draw(){
        super.draw();

        float cx = x + width / 2f, cy = y + height / 2f;
        float alpha = pressed ? 0.9f : 0.55f;

        //ring
        Lines.stroke(Scl.scl(3f), Color.lightGray);
        Draw.color(Color.white, alpha * 0.35f);
        Fill.circle(cx, cy, radius);
        Draw.color(Color.white, alpha);
        Lines.circle(cx, cy, radius);
        //cross hair, so "straight up" is readable without the hud hint
        Lines.line(cx - Scl.scl(10f), cy, cx + Scl.scl(10f), cy);
        Lines.line(cx, cy - Scl.scl(10f), cx, cy + Scl.scl(10f));

        //knob
        Draw.color(Pal.accent, alpha);
        Fill.circle(cx + knob.x, cy + knob.y, knobRadius);
        Draw.color(Color.white, alpha);
        Lines.stroke(Scl.scl(2f));
        Lines.circle(cx + knob.x, cy + knob.y, knobRadius);

        Draw.reset();
    }
}
