package orbital.entities.abilities;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import orbital.entities.Units;
import orbital.gen.*;
import orbital.graphics.*;

import static orbital.Vars.*;

/**
 * Unit analogue of {@link orbital.world.blocks.defense.turrets.TractorBeamTurret}.
 *
 * <p>Pulls the closest enemy air unit towards the carrier with the block's real
 * {@code force}, {@code scaledForce}, {@code range} and {@code damage}. Created by
 * {@link orbital.entities.units.SupportMount} when a {@code parallax} support module
 * is installed; it is never serialized and lives only in the mount.</p>
 */
public class TractorAbility extends Ability{
    public float range = 100f, force = 0.3f, scaledForce = 0f, damage = 0f;
    public Color color = Color.white;
    public float laserWidth = 0.6f;

    protected @Nullable Unit target;
    protected float strength;

    public TractorAbility(float range, float force, float scaledForce, float damage){
        this.range = range;
        this.force = force;
        this.scaledForce = scaledForce;
        this.damage = damage;
    }

    @Override
    public void update(Unit unit){
        target = Units.closestEnemy(unit.team, unit.x, unit.y, range, u -> u.checkTarget(true, false));

        if(target != null && target.within(unit, range + target.hitSize() / 2f)){
            strength = Mathf.lerpDelta(strength, 1f, 0.1f);

            if(damage > 0){
                target.damageContinuousPierce(damage * Time.delta);
            }
            //same impulse profile as the turret: base force plus a proximity bonus
            target.impulseNet(Tmp.v1.set(unit).sub(target)
                .limit((force + (1f - target.dst(unit) / range) * scaledForce) * Time.delta));
        }else{
            strength = Mathf.lerpDelta(strength, 0f, 0.1f);
            target = null;
        }
    }

    @Override
    public void draw(Unit unit){
        if(target == null || strength <= 0.02f) return;

        float z = unit.type != null && unit.type.lowAltitude ? Layer.flyingUnitLow + 0.1f : Layer.flyingUnit + 0.1f;
        Draw.z(z);
        Draw.color(color, strength);
        Lines.stroke(laserWidth * strength);
        Lines.line(unit.x, unit.y, target.x, target.y, false);
        Lines.stroke(1f);
        Draw.color(Color.white);
        Draw.reset();
    }
}
