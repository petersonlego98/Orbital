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
public class TractorAbility extends Ability implements AimingModule{
    public float range = 100f, force = 0.3f, scaledForce = 0f, damage = 0f;
    public Color color = Color.white;
    public float laserWidth = 0.6f;
    /** how fast the module's turret turns toward the pulled unit, in degrees per tick. */
    public float rotateSpeed = 10f;
    /** hull-local dock of the module, in world units. */
    public float anchorX, anchorY;
    /** current aim, relative to the hull, in degrees. */
    public float rotation = 90f;

    protected @Nullable Unit target;
    protected float strength;

    public TractorAbility(float range, float force, float scaledForce, float damage){
        this.range = range;
        this.force = force;
        this.scaledForce = scaledForce;
        this.damage = damage;
    }

    @Override
    public void anchor(float anchorX, float anchorY){
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    @Override
    public float rotation(){
        return rotation;
    }

    @Override
    public float moduleX(Unit unit){
        return ModuleDock.worldX(unit, anchorX, anchorY);
    }

    @Override
    public float moduleY(Unit unit){
        return ModuleDock.worldY(unit, anchorX, anchorY);
    }

    @Override
    public void update(Unit unit){
        float dockX = moduleX(unit), dockY = moduleY(unit);
        //range and filters are the block's own, measured from the module's dock - a beam that
        //leaves the module must also reach what the block could reach from where it stands
        target = Units.closestEnemy(unit.team, dockX, dockY, range, u -> u.checkTarget(true, false));

        if(target != null && Mathf.dst(target.x, target.y, dockX, dockY) <= range + target.hitSize() / 2f){
            //the turret tracks the unit it is pulling, so the beam and the sprite turn together
            rotation = ModuleDock.turn(rotation, ModuleDock.aimAt(unit, dockX, dockY, target.x, target.y),
                rotateSpeed, Time.delta);
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

        float dockX = moduleX(unit), dockY = moduleY(unit);
        float z = unit.type != null && unit.type.lowAltitude ? Layer.flyingUnitLow + 0.1f : Layer.flyingUnit + 0.1f;
        Draw.z(z);
        Draw.color(color, strength);
        Lines.stroke(laserWidth * strength);
        Lines.line(dockX, dockY, target.x, target.y, false);
        Lines.stroke(1f);
        Draw.color(Color.white);
        Draw.reset();
    }
}
