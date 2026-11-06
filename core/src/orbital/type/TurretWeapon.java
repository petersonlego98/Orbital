package orbital.type;

import orbital.entities.bullet.*;
import orbital.entities.part.*;
import orbital.gen.Unit;
import orbital.world.*;
import orbital.world.blocks.defense.turrets.*;

import static orbital.Vars.*;

/**
 * Adapts a block turret into a unit weapon.
 *
 * <p>This is the technical bridge for Phase A: the turret keeps its block definition
 * (stats, sprite, ammo), while the unit gains a regular {@link Weapon} that can be
 * mounted, fired and removed at runtime.</p>
 */
public class TurretWeapon extends Weapon{
    /** the block turret this weapon was created from. */
    public final Turret turret;
    /** the size of the original turret, in blocks. */
    public final int turretSize;

    public TurretWeapon(Turret turret, BulletType bullet){
        super(turret.name);
        this.turret = turret;
        this.turretSize = turret.size;
        this.bullet = bullet;

        removable = true;
        dorsalSize = turret.size;
        float p = turretSize * TurretSlot.pxTile;
        dorsalCost = p * p;

        //a dorsal module is a single weapon; it must not be mirrored like a hull weapon.
        mirror = false;
        rotate = true;
        controllable = true;
        aiControllable = true;
        predictTarget = turret.predictTarget;

        reload = turret.reload;
        rotateSpeed = turret.rotateSpeed;
        shootCone = turret.shootCone;
        minWarmup = turret.minWarmup;
        recoil = turret.recoil;
        recoilPow = turret.recoilPow;
        recoilTime = turret.reload;
        recoils = turret.recoils;
        cooldownTime = turret.cooldownTime;
        inaccuracy = turret.inaccuracy;
        velocityRnd = turret.velocityRnd;
        extraVelocity = turret.extraVelocity;
        lifeRnd = turret.lifeRnd;
        extraLife = turret.extraLife;
        shake = turret.shake;
        shootSound = turret.shootSound;
        shootSoundVolume = turret.shootSoundVolume;
        heatColor = turret.heatColor;

        //center the module on its mount point.
        x = 0f;
        y = 0f;
        shootX = turret.shootX;
        shootY = turret.shootY == Float.NEGATIVE_INFINITY ? turretSize * tilesize / 2f : turret.shootY;

        //keep the original firing pattern (bursts, alternation, ...)
        shoot = turret.shoot;

        //replicate the block turret's visual barrels, if they exist.
        if(turret.recoils > 0){
            for(int i = 0; i < turret.recoils; i++){
                int index = i;
                String suffix = turret.recoils == 2 ? (i == 0 ? "-barrel-l" : "-barrel-r") : "-barrel" + i;
                parts.add(new RegionPart(suffix){{
                    progress = PartProgress.recoil;
                    recoilIndex = index;
                    under = true;
                    moveY = -1.5f;
                }});
            }
        }
    }

    /** @return the dorsal footprint cost, in px². P = size * 32, C = P². Never scaled. */
    public float cost(){
        return dorsalCost;
    }

    /**
     * @return the drawing-only scale derived from the carrier's measured hull size. The exact
     * same {@link DorsalVisual} rule is used by support modules, so a turret and a pod of the
     * same size have the same visual weight on any given hull.
     *
     * <p>Resolved at draw time rather than at mount time so content-defined removable turrets
     * (the Duo on mono/flare) and runtime-mounted turrets follow the same path, and so a mod
     * that adds a {@link TurretWeapon} directly does not need to know about scaling.</p>
     */
    @Override
    public float visualScale(Unit unit){
        return DorsalGeometry.turretVisualScale(unit == null ? null : unit.type, turretSize, regionPx());
    }

    /** @return true if this turret has a bullet type that this adapter can use. */
    public static boolean canAdapt(Turret turret){
        if(turret instanceof ItemTurret itemTurret) return itemTurret.ammoTypes.size > 0;
        if(turret instanceof PowerTurret powerTurret) return powerTurret.shootType != null;
        return false;
    }

    /** Creates a weapon from a turret block, using its first ammo type. */
    public static TurretWeapon of(Block block){
        if(!(block instanceof Turret turret)){
            throw new IllegalArgumentException("Block '" + block.name + "' is not a turret.");
        }

        BulletType bullet = null;

        if(turret instanceof ItemTurret itemTurret && itemTurret.ammoTypes.size > 0){
            bullet = itemTurret.ammoTypes.values().next();
        }else if(turret instanceof PowerTurret powerTurret){
            bullet = powerTurret.shootType;
        }

        if(bullet == null){
            throw new IllegalArgumentException("Turret '" + turret.name + "' has no bullet type usable by TurretWeapon.");
        }

        return new TurretWeapon(turret, bullet);
    }
}
