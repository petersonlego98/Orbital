package orbital.entities.abilities;

import arc.graphics.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.Effect;
import orbital.entities.Units;
import orbital.gen.*;
import orbital.graphics.*;

/**
 * Unit analogue of {@link orbital.world.blocks.defense.turrets.PointDefenseTurret}.
 *
 * <p>Shoots down the closest enemy bullet in range with the block's real
 * {@code range}, {@code bulletDamage} and {@code reload}. Created by
 * {@link orbital.entities.units.SupportMount} when a {@code segment} support module
 * is installed; it is never serialized and lives only in the mount.</p>
 */
public class PointDefenseAbility extends Ability{
    public float range = 60f, reload = 8f, bulletDamage = 11f;
    public Color color = Color.white;
    public Effect beamEffect = Fx.pointBeam;
    public Effect hitEffect = Fx.pointHit;

    protected @Nullable Bullet target;
    protected float reloadCounter = 0f, retimer = 0f;

    /** How often to re-acquire the closest hittable enemy bullet. */
    public float retargetTime = 5f;

    public PointDefenseAbility(float range, float bulletDamage, float reload){
        this.range = range;
        this.bulletDamage = bulletDamage;
        this.reload = reload;
    }

    @Override
    public void update(Unit unit){
        //drop dead, offscreen, friendly or unhittable bullets
        if(target != null && (!target.isAdded() || target.team == unit.team
            || !target.within(unit, range) || target.type == null || !target.type.hittable)){
            target = null;
        }

        retimer += Time.delta;
        if(retimer >= retargetTime){
            retimer = 0f;
            //linear scan: the group's spatial tree is only rebuilt by the logic loop, so
            //intersect() misses freshly created bullets
            float best = Float.MAX_VALUE;
            for(Bullet b : Groups.bullet){
                if(b.team == unit.team || b.type == null || !b.type.hittable) continue;
                float d = b.dst2(unit);
                if(d < best && d < range * range){
                    target = b;
                    best = d;
                }
            }
        }

        if(target == null) return;

        reloadCounter += Time.delta;
        if(reloadCounter >= reload){
            float realDamage = bulletDamage;
            if(target.damage() > realDamage){
                target.damage(target.damage() - realDamage);
            }else{
                target.remove();
            }

            beamEffect.at(unit.x, unit.y, unit.angleTo(target), color, new Vec2().set(target));
            hitEffect.at(target.x, target.y, color);
            reloadCounter = 0f;
        }
    }
}
