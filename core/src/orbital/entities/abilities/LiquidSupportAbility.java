package orbital.entities.abilities;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.*;
import orbital.entities.bullet.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.world.blocks.defense.turrets.LiquidTurret;

import static orbital.Vars.*;

/**
 * Unit analogue of a {@link LiquidTurret} used as a support module ({@code wave},
 * {@code tsunami}).
 *
 * <p>The block sprays a liquid jet at the closest enemy inside its range on its own
 * {@code reload} cadence, and that is what the module does too: the jet is the block's own
 * liquid ammo, so it carries the same damage, status and effects. The liquid itself comes from
 * the hull's supply pool - the chosen liquid of the liquid bar - and never from the item the
 * carrier happens to be holding. While it sprays it also keeps the support role of the block:
 * it extinguishes burning allies and applies the liquid status to enemies inside the field.</p>
 *
 * <p>{@link #autoTarget} starts <b>false</b>, like every other module of the liquid category:
 * the jets are piloted, not automatic. The jet only sprays while the hull is firing and follows
 * the hull's aim point, on both desktop (mouse) and mobile (tap/stick aim). The pilot can hand
 * the module back to its own targeting from the loadout dialog, and then it sprays at the
 * closest enemy the block would target. Either way the support half below never waits for the
 * fire control, since extinguishing an ally that is burning has nothing to do with the aim.</p>
 *
 * <p>Created by {@link orbital.entities.units.SupportMount}; never serialized and lives only
 * in the mount.</p>
 */
public class LiquidSupportAbility extends Ability implements AimingModule{
    /** every liquid the block accepts, with the bullet it sprays. */
    public final ObjectMap<Liquid, BulletType> ammoTypes = new OrderedMap<>();
    /** what the block's own jet may target. */
    public boolean targetAir = true, targetGround = false;
    public float range = 110f, reload = 3f;
    public Color color = Color.royal;
    public Effect shootEffect = Fx.shootLiquid;
    /** shot sound, kept from the block when it has one. */
    public Sound shootSound = Sounds.none;
    public float soundVolume = 0.5f;
    /**
     * When false the pilot drives the jet instead of the module picking its own target: it
     * sprays only while the hull is firing, toward the hull's aim point, and only within the
     * module's own range. Starts false, so a mounted {@code wave} or {@code tsunami} is a
     * piloted weapon like every other liquid module; the support role is never gated by this
     * flag.
     */
    public boolean autoTarget = false;

    /** How fast the turret turns toward its aim, in degrees per tick. */
    public float rotateSpeed = 10f;
    /** hull-local dock of the module, in world units. */
    public float anchorX, anchorY;
    /** current aim, relative to the hull, in degrees. */
    public float rotation = 90f;
    /** offset of the muzzle from the dock, so the jet visibly leaves the module. */
    public float muzzleLength = 3f;

    protected float timer = 0f;
    /** last aim, so automatic mode knows what it was tracking between shots. */
    protected @Nullable Unit target;

    public LiquidSupportAbility(float range, float reload){
        this.range = range;
        this.reload = reload;
    }

    /**
     * @return the ability for a block, with its real ammo map, range, reload, target filters and
     * jet colour.
     */
    public static LiquidSupportAbility from(LiquidTurret block){
        LiquidSupportAbility ability = new LiquidSupportAbility(block.range, block.reload);
        ability.ammoTypes.putAll(block.ammoTypes);
        ability.targetAir = block.targetAir;
        ability.targetGround = block.targetGround;
        ability.rotateSpeed = block.rotateSpeed;
        BulletType first = ability.firstAmmo();
        if(first instanceof LiquidBulletType liquid && liquid.liquid != null) ability.color = liquid.liquid.color;
        return ability;
    }

    /** @return the block's first liquid ammo, or null when it has none. */
    private @Nullable BulletType firstAmmo(){
        for(BulletType type : ammoTypes.values()) return type;
        return null;
    }

    /** @return the jet to spray now: the pool's chosen liquid when the block accepts it. */
    private @Nullable BulletType jet(Unit unit){
        Liquid chosen = unit.moduleSupply() != null ? unit.moduleSupply().liquid : null;
        if(chosen != null && ammoTypes.containsKey(chosen)) return ammoTypes.get(chosen);
        return firstAmmo();
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
        //support first and unconditionally: a support module keeps supporting its wing while
        //the pilot idles. The fire control only decides where the jet goes.
        timer += Time.delta;

        //one resolution per pulse: the jet, its status and its duration all come from the
        //liquid the hull chose, exactly like the block sprays the liquid it loaded
        BulletType bullet = jet(unit);
        StatusEffect status = bullet != null ? bullet.status : StatusEffects.none;
        float duration = bullet != null ? bullet.statusDuration : 0f;

        //the support half: extinguish burning allies, hinder enemies inside the field
        if(timer >= reload && status != StatusEffects.none){
            Units.nearby(unit.team, unit.x, unit.y, range, ally -> {
                //getDuration reads the live status list; hasEffect() misses statuses applied
                //through opposite transitions
                if(ally.getDuration(StatusEffects.burning) > 0f){
                    ally.apply(status, duration);
                }
            });

            Units.nearbyEnemies(unit.team, unit.x, unit.y, range, enemy -> {
                if(enemy.checkTarget(true, true)) enemy.apply(status, duration);
            });
        }

        if(timer < reload){
            target = null;
            return;
        }
        timer = 0f;

        if(bullet == null){
            target = null;
            return;
        }

        //manual: the pilot's fire control is the trigger, and the jet follows the hull's aim
        //point instead of the module choosing its own target. isShooting reflects player.shooting
        //for player-controlled hulls and mount.shoot for AI, so it is the right gate in both
        //cases. The old hasWeapons() guard was wrong: a hull with no native weapons still has
        //isShooting driven by the input layer, so the guard was making the module fire
        //unconditionally whenever the hull carried no weapon of its own.
        boolean manual = !autoTarget;
        if(manual && !unit.isShooting()){
            target = null;
            return;
        }

        //the weapon half: the pilot's aim point in manual mode, the closest enemy the block
        //would target in automatic mode
        float aimX, aimY;

        if(manual){
            aimX = unit.aimX();
            aimY = unit.aimY();
            target = null;
        }else{
            Unit enemy = Units.closestEnemy(unit.team, unit.x, unit.y, range,
                u -> u.checkTarget(targetAir, targetGround) && u.targetable(unit.team));
            if(enemy == null){
                target = null;
                return;
            }

            target = enemy;
            aimX = enemy.x;
            aimY = enemy.y;
        }

        //the turret turns first, so the shot and the muzzle effect both follow the barrel
        float dockX = moduleX(unit), dockY = moduleY(unit);
        rotation = ModuleDock.turn(rotation, ModuleDock.aimAt(unit, dockX, dockY, aimX, aimY),
            rotateSpeed, Time.delta);
        float angle = ModuleDock.worldAngle(unit, rotation);

        //never spray past the module's own range, whatever the pilot aims at
        float dst = Mathf.clamp(Math.max(Mathf.dst(dockX, dockY, aimX, aimY), 4f), 4f, range);

        //the jet is paid out of the hull's liquid pool, like the block drains its own liquid:
        //one unit per shot, in the bullet's own ammo multiplier
        ModuleSupply supply = unit.moduleSupply();
        if(supply != null && supply.usesLiquid()){
            float cost = 1f / Math.max(1f, bullet instanceof LiquidBulletType l ? l.ammoMultiplier : 1f);
            supply.liquidAmount = Math.max(0f, supply.liquidAmount - cost);
        }

        //spawned at the module's own muzzle, the way the block's barrel sits on the block
        float mx = dockX + Angles.trnsx(angle, muzzleLength);
        float my = dockY + Angles.trnsy(angle, muzzleLength);
        //the jet's own lifetime covers the module's range; scale it so a manual shot lands
        //exactly where the pilot aimed instead of always travelling the full range
        float life = bullet.speed > 0.01f ? dst / bullet.speed / bullet.lifetime : 1f;
        bullet.create(unit, unit.team, mx, my, angle, 1f, life);
        shootEffect.at(mx, my, angle, color);
        if(shootSound != Sounds.none) shootSound.at(mx, my, 1f, soundVolume);
    }

    /**
     * No aim guide is drawn: the jet itself and its shoot effect already show where the module
     * is pointing. The old line from the dock to the pilot's mouse point was the "rectangle"
     * that appeared to grow out of the turret, and it also survived between shots.
     */
    @Override
    public void draw(Unit unit){
    }
}
