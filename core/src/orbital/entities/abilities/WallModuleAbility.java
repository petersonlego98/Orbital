package orbital.entities.abilities;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.*;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.world.*;
import orbital.world.blocks.defense.*;

import static orbital.Vars.*;

/**
 * Effect of a dorsal wall module: the wall's real parameters, translated to the carrier unit.
 *
 * <p>A wall block never damages anything itself - it resists attacks and, for some families,
 * retaliates. The unit analogue here does the same with the block's own numbers:</p>
 *
 * <ul>
 *   <li>{@code armor} (beryllium 2, tungsten 14, carbide 16, reinforced surge 20,
 *       shielded 15) is applied with the wall formula: {@code damage - armor}, floor of 10%.</li>
 *   <li>{@code health} is added to the hull's health, because a wall is a block of hit points:
 *       plates <b>sum</b>.</li>
 *   <li>{@code chanceDeflect} (phase 10, shielded 8) reflects bullets out of the hull, using the
 *       same {@code chanceDeflect / bullet damage} curve the wall uses - so the module is strong
 *       against weak bullets and nearly useless against heavy single hits.</li>
 *   <li>{@code lightningChance} / {@code lightningDamage} (surge 5% for 20, reinforced surge 30)
 *       zaps the attacker with the same chain lightning the wall spawns, aimed back along the
 *       bullet that hit it.</li>
 *   <li>{@code shieldHealth} (shielded wall, 900) becomes a shield pool that absorbs damage
 *       before health, regenerates and breaks exactly like the block.</li>
 * </ul>
 *
 * <p><b>Stacking.</b> Several wall modules may be mounted on the same hull. Their {@code health}
 * values add up; every other attribute takes the <b>maximum</b> currently installed, because two
 * plates of the same kind would otherwise double the same mechanic. Each instance therefore reads
 * the aggregate from the hull's wall mounts every frame and writes it back idempotently, so
 * unmounting a module restores the previous values without any bookkeeping.</p>
 *
 * <p>Plastanium's {@code absorbLasers}/{@code insulated} have no unit analogue (a hull does not
 * carry power nodes or a laser path to interrupt) and are deliberately not translated.</p>
 */
public class WallModuleAbility extends Ability{

    /** the wall or rampart this module was built from */
    public final Block block;

    /** wall parameters, captured when the module is mounted */
    public float armor;
    public float health;
    public float chanceDeflect;
    public float lightningChance, lightningDamage;
    public int lightningLength = 17;
    public Color lightningColor = Pal.surge;
    public Sound lightningSound = Sounds.shootArc;
    public float shieldHealth, shieldRegen = 2f, shieldCooldown = 60f * 10f;

    /** shield state; the pool itself lives in {@code unit.shield}, shared with native shields */
    protected float shieldRadiusScale;
    protected boolean wasBroken = true;

    /** bullets already zapped by this module, so one projectile never triggers twice */
    protected final IntSet zapped = new IntSet();
    private static final int zappedCap = 512;

    /** cache of this unit's own reflective behaviour, so headless runs stay deterministic */
    protected float hullRadius = -1f;

    public WallModuleAbility(Block block){
        this.block = block;

        if(block instanceof Wall wall){
            armor = wall.armor;
            health = wall.health;
            chanceDeflect = wall.chanceDeflect;
            lightningChance = wall.lightningChance;
            lightningDamage = wall.lightningDamage;
            lightningLength = wall.lightningLength;
            lightningColor = wall.lightningColor;
            lightningSound = wall.lightningSound;
        }

        if(block instanceof ShieldWall shield){
            shieldHealth = shield.shieldHealth;
            shieldRegen = shield.regenSpeed;
            shieldCooldown = shield.breakCooldown;
        }
    }

    private static WallMount[] wallMounts(Unit unit){
        return unit == null || unit.wallMounts == null ? new WallMount[0] : unit.wallMounts;
    }

    /** @return the total hit points the mounted walls add to this hull. */
    public static float carriedHealth(Unit unit){
        float sum = 0f;
        for(WallMount mount : wallMounts(unit)){
            if(mount != null && mount.ability != null) sum += mount.ability.health;
        }
        return sum;
    }

    /**
     * The hull's hit points become {@code type.health + Σ wall health}: mounting a plate adds
     * its hit points to the pool, unmounting one takes them back. Written from the aggregate
     * every frame, so it is idempotent and self-restoring without any bookkeeping.
     */
    private void applyHealth(Unit unit){
        float target = unit.type.health + carriedHealth(unit);
        if(Math.abs(unit.maxHealth - target) < 0.01f) return;

        float delta = target - unit.maxHealth;
        unit.maxHealth = target;
        unit.health = Mathf.clamp(unit.health + delta, 0.01f, target);
    }

    @Override
    public void update(Unit unit){
        if(unit == null || unit.type == null) return;

        //hit points add to the hull's pool, everything else takes the maximum installed
        applyHealth(unit);
        float armor = 0f, deflect = 0f, lightningChance = 0f, lightningDamage = 0f, shield = 0f;
        for(WallMount mount : wallMounts(unit)){
            if(mount == null || mount.ability == null) continue;
            armor = Math.max(armor, mount.ability.armor);
            deflect = Math.max(deflect, mount.ability.chanceDeflect);
            lightningChance = Math.max(lightningChance, mount.ability.lightningChance);
            lightningDamage = Math.max(lightningDamage, mount.ability.lightningDamage);
            shield = Math.max(shield, mount.ability.shieldHealth);
        }

        //written every frame from the aggregate: idempotent, and it restores the hull's own
        //armour as soon as the last armoured plate is gone
        unit.armor = Math.max(unit.type.armor, armor);

        if(deflect > 0f || lightningChance > 0f || shield > 0f){
            scanBullets(unit, deflect, lightningChance, lightningDamage, shield);
        }

        if(shield > 0f) updateShield(unit, shield);
    }

    /**
     * One pass over the bullets touching the hull, in wall order: deflect first (a reflected
     * bullet is gone and must not be absorbed or counted as a hit), then the shield, then the
     * retaliation lightning.
     */
    private void scanBullets(Unit unit, float deflect, float lightningChance, float lightningDamage, float shield){
        float reach = unit.hitSize + 3f;

        Groups.bullet.intersect(unit.x - reach, unit.y - reach, reach * 2f, reach * 2f, b -> {
            if(b == null || b.team == unit.team || b.owner == unit) return false;
            if(!unit.within(b.x, b.y, reach)) return false;

            //deflection: same curve as Wall.WallBuild#collision
            if(deflect > 0f && b.type.reflectable && b.vel.len() > 0.1f && Mathf.chance(deflect / b.damage())){
                reflect(unit, b);
                return false;
            }

            //shield absorbs the bullet before it damages the hull
            if(shield > 0f && unit.shield > 0f){
                b.absorb();
                Fx.absorb.at(b.x, b.y);
                Sounds.shieldHit.at(b.x, b.y, 1f + Mathf.range(0.1f), 0.12f);
                unit.shield -= b.type.shieldDamage(b);
                return false;
            }

            //retaliation lightning, once per bullet
            if(lightningChance > 0f && !zapped.contains(b.id())){
                zapped.add(b.id());
                if(Mathf.chance(lightningChance)){
                    Lightning.create(unit.team, lightningColor, lightningDamage, unit.x, unit.y,
                        b.rotation() + 180f, lightningLength);
                    lightningSound.at(unit.x, unit.y, Mathf.random(0.9f, 1.1f));
                }
            }

            return false;
        });

        //bullet ids grow forever; the window only needs the newest ones
        if(zapped.size > zappedCap) zapped.clear();
    }

    /** Mirrors the phase wall: sends the bullet back where it came from, owned by this hull. */
    private void reflect(Unit unit, Bullet b){
        b.trns(-b.vel.x, -b.vel.y);

        float nx = b.x - unit.x, ny = b.y - unit.y, len = Mathf.len(nx, ny);
        if(len > 0.0001f){
            nx /= len;
            ny /= len;
        }else{
            nx = -1f;
            ny = 0f;
        }

        float dot = b.vel.x * nx + b.vel.y * ny;
        b.vel.set(b.vel.x - 2f * dot * nx, b.vel.y - 2f * dot * ny);
        b.owner = unit;
        b.team = unit.team;
    }

    /** Shield pool: absorbs damage, regenerates, breaks and cools down like the shielded wall. */
    private void updateShield(Unit unit, float shieldHealth){
        float radius = shieldRadius(unit);

        if(unit.shield <= 0.00001f && !wasBroken){
            wasBroken = true;
            Fx.shieldBreak.at(unit.x, unit.y, radius, unit.type.shieldColor(unit), 6);
            Sounds.shieldBreakSmall.at(unit.x, unit.y);
        }

        if(wasBroken){
            shieldRadiusScale = Mathf.lerpDelta(shieldRadiusScale, 0f, 0.11f);
        }else if(unit.shield < shieldHealth){
            unit.shield = Math.min(shieldHealth, unit.shield + shieldRegen * edelta());
            shieldRadiusScale = Mathf.lerpDelta(shieldRadiusScale, 1f, 0.12f);
        }
    }

    private float edelta(){
        return Time.delta; //no efficiency/power on a hull: the wall's regen is per tick
    }

    /** @return the drawn radius of the shield, in world units, once the hull is known. */
    public float shieldRadius(Unit unit){
        if(hullRadius < 0f) hullRadius = Math.max(8f, unit.hitSize * 1.15f);
        return hullRadius;
    }

    @Override
    public void created(Unit unit){
        if(shieldHealth > 0f) unit.shield = shieldHealth;
        wasBroken = unit.shield <= 0.00001f;
    }

    @Override
    public void draw(Unit unit){
        if(shieldHealth <= 0f || unit.shield <= 0f) return;

        Draw.z(Layer.shields);
        Draw.color(unit.type.shieldColor(unit), Color.white, Mathf.clamp(unit.shield / shieldHealth));
        Lines.stroke(1.5f);
        Draw.alpha(0.4f);
        Fill.square(unit.x, unit.y, shieldRadius(unit));
        Draw.alpha(1f);
        Lines.poly(unit.x, unit.y, 6, shieldRadius(unit), 0f);
        Draw.reset();
    }

    @Override
    public void displayBars(Unit unit, Table bars){
        if(shieldHealth > 0f){
            bars.add(new Bar("stat.shieldhealth", Pal.accent, () -> Mathf.clamp(unit.shield / shieldHealth))).row();
        }
    }

    @Override
    public void addStats(Table t){
        super.addStats(t);

        if(health > 0f){
            t.add(abilityStat("health", Strings.autoFixed(health, 0))).row();
        }
        if(armor > 0f){
            t.add(abilityStat("armor", Strings.autoFixed(armor, 1))).row();
        }
        if(chanceDeflect > 0f){
            t.add(abilityStat("deflectchance", Strings.autoFixed(chanceDeflect, 0))).row();
        }
        if(lightningChance > 0f && lightningDamage > 0f){
            t.add(abilityStat("lightningchance", Strings.autoFixed(lightningChance * 100f, 0))).row();
            t.add(abilityStat("lightningdamage", Strings.autoFixed(lightningDamage, 0))).row();
        }
        if(shieldHealth > 0f){
            t.add(abilityStat("shield", Strings.autoFixed(shieldHealth, 0))).row();
        }
    }
}
