package orbital.entities.abilities;

import arc.graphics.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.*;
import orbital.entities.bullet.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.blocks.defense.turrets.LiquidTurret;

import static orbital.Vars.*;

/**
 * Unit analogue of a {@link LiquidTurret} used as a support module ({@code wave},
 * {@code tsunami}).
 *
 * <p>Every {@code reload} ticks it applies the liquid's status effect with the block's
 * real {@code range} and {@code reload}:
 * <ul>
 *   <li>allies: extinguishes burning units by applying the liquid status (wet/freezing
 *   cancels burning through the status reaction system);</li>
 *   <li>enemies: slows or freezes units inside the field, as the liquid jet would.</li>
 * </ul>
 *
 * <p>The status and duration come from the block's default liquid ammo (water when
 * present, otherwise the first registered liquid). No damage is applied: the module is
 * support, not a weapon. Created by {@link orbital.entities.units.SupportMount}; never
 * serialized and lives only in the mount.</p>
 */
public class LiquidSupportAbility extends Ability{
    public float range = 110f, reload = 3f;
    public StatusEffect status = StatusEffects.none;
    public float statusDuration = 60f * 4f;
    public Color color = Color.royal;
    public Effect shootEffect = Fx.shootLiquid;

    protected float timer = 0f;

    public LiquidSupportAbility(float range, float reload, StatusEffect status, float statusDuration, Color color){
        this.range = range;
        this.reload = reload;
        this.status = status;
        this.statusDuration = statusDuration;
        this.color = color;
    }

    /** @return the status/duration pair carried by the block's default liquid ammo. */
    public static LiquidSupportAbility from(LiquidTurret block){
        BulletType bullet = block.ammoTypes.get(Liquids.water);
        if(bullet == null){
            for(BulletType type : block.ammoTypes.values()){
                bullet = type;
                break;
            }
        }
        if(bullet == null) return new LiquidSupportAbility(block.range, block.reload, StatusEffects.none, 0f, Color.royal);

        return new LiquidSupportAbility(block.range, block.reload,
            bullet.status, bullet.statusDuration,
            bullet instanceof LiquidBulletType liquid && liquid.liquid != null ? liquid.liquid.color : Color.royal);
    }

    @Override
    public void update(Unit unit){
        timer += Time.delta;
        if(timer < reload || status == StatusEffects.none) return;
        timer = 0f;

        //extinguish burning allies first - that is the extinguisher role of the block
        Units.nearby(unit.team, unit.x, unit.y, range, ally -> {
            //getDuration reads the live status list; hasEffect() misses statuses applied
            //through opposite transitions
            if(ally.getDuration(StatusEffects.burning) > 0f){
                ally.apply(status, statusDuration);
            }
        });

        //hinder enemies inside the field with the same status
        Units.nearbyEnemies(unit.team, unit.x, unit.y, range, enemy -> {
            if(enemy.checkTarget(true, true)){
                enemy.apply(status, statusDuration);
            }
        });
    }
}
