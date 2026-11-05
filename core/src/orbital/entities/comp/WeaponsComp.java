package orbital.entities.comp;

import arc.math.geom.*;
import arc.util.*;
import orbital.annotations.Annotations.*;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.blocks.defense.turrets.*;

@Component
abstract class WeaponsComp implements Teamc, Posc, Rotc, Velc, Statusc{
    @Import float x, y;
    @Import boolean disarmed;
    @Import UnitType type;

    /** weapon mount array, never null */
    @SyncLocal WeaponMount[] mounts = {};
    @ReadOnly transient boolean isRotate;
    @NoSerialize float aimX, aimY;
    boolean isShooting;

    void setWeaponRotation(float rotation){
        for(WeaponMount mount : mounts){
            mount.rotation = rotation;
        }
    }

    void setupWeapons(UnitType def){
        mounts = new WeaponMount[def.weapons.size];
        for(int i = 0; i < mounts.length; i++){
            mounts[i] = def.weapons.get(i).mountType.get(def.weapons.get(i));
        }
    }

    /** @return the dorsal cost of all removable turrets currently mounted on this unit. */
    public float loadoutCost(){
        float sum = 0f;
        for(WeaponMount mount : mounts){
            if(mount.weapon instanceof TurretWeapon turretWeapon && turretWeapon.removable){
                sum += turretWeapon.cost();
            }
        }
        return sum;
    }

    /** @return how many removable turrets are currently mounted on this unit. */
    public int mountedTurrets(){
        int count = 0;
        for(WeaponMount mount : mounts){
            if(mount.weapon instanceof TurretWeapon turretWeapon && turretWeapon.removable) count++;
        }
        return count;
    }

    /** @return true if the turret fits this unit's current runtime loadout. */
    public boolean canMount(Turret turret){
        if(turret == null || !TurretWeapon.canAdapt(turret)) return false;
        if(!type.hasSlotFor(turret.size) || !type.fitsSilhouette(turret.size)) return false;
        if(mountedTurrets() >= type.turretSlots.size) return false;

        float p = turret.size * TurretSlot.pxTile;
        return loadoutCost() + p * p <= type.dorsalBudget();
    }

    /**
     * Mounts a turret at runtime, validating slot size, silhouette and dorsal budget.
     * @return the created weapon, or null if the turret does not fit.
     */
    public @Nullable TurretWeapon mountTurret(Turret turret){
        if(!canMount(turret)) return null;

        TurretWeapon weapon = TurretWeapon.of(turret);
        //runtime weapons are not covered by UnitType's content init, so load sprites and init here.
        weapon.load();
        weapon.init();
        addWeapon(weapon);
        return weapon;
    }

    /** Adds a weapon mount at runtime. Used for removable dorsal turret modules. */
    public void addWeapon(Weapon weapon){
        WeaponMount mount = weapon.mountType.get(weapon);
        WeaponMount[] next = java.util.Arrays.copyOf(mounts, mounts.length + 1);
        next[mounts.length] = mount;
        mounts = next;
    }

    /** Removes a weapon mount at runtime. Native weapons are never removable. */
    public boolean removeWeapon(Weapon weapon){
        if(!(weapon instanceof TurretWeapon turretWeapon) || !turretWeapon.removable) return false;

        int index = -1;
        for(int i = 0; i < mounts.length; i++){
            if(mounts[i].weapon == weapon){
                index = i;
                break;
            }
        }
        if(index < 0) return false;

        WeaponMount[] next = new WeaponMount[mounts.length - 1];
        System.arraycopy(mounts, 0, next, 0, index);
        System.arraycopy(mounts, index + 1, next, index, next.length - index);

        if(mounts[index].sound != null){
            mounts[index].sound.stop();
        }

        mounts = next;
        return true;
    }

    void controlWeapons(boolean rotateShoot){
        controlWeapons(rotateShoot, rotateShoot);
    }

    void controlWeapons(boolean rotate, boolean shoot){
        for(WeaponMount mount : mounts){
            if(mount.weapon.controllable){
                mount.rotate = rotate;
                mount.shoot = shoot;
            }
        }
        isRotate = rotate;
        isShooting = shoot;
    }

    void aim(Position pos){
        aim(pos.getX(), pos.getY());
    }

    /** Aim at something. This will make all mounts point at it. */
    void aim(float x, float y){
        aim(x, y, false);
    }

    /** Aim at something. This will make all mounts point at it. */
    void aim(float x, float y, boolean clearTarget){
        Tmp.v1.set(x, y).sub(this.x, this.y);
        if(Tmp.v1.len() < type.aimDst) Tmp.v1.setLength(type.aimDst);

        x = Tmp.v1.x + this.x;
        y = Tmp.v1.y + this.y;

        for(WeaponMount mount : mounts){
            if(mount.weapon.controllable){
                if(clearTarget) mount.target = null;
                mount.aimX = x;
                mount.aimY = y;
            }
        }

        aimX = x;
        aimY = y;
    }

    boolean canShoot(){
        return !disarmed;
    }

    @Override
    public void remove(){
        for(WeaponMount mount : mounts){
            if(mount.weapon.continuous && mount.bullet != null && mount.bullet.owner == self()){
                mount.bullet.time = mount.bullet.lifetime - 10f;
                mount.bullet = null;
            }

            if(mount.sound != null){
                mount.sound.stop();
            }
        }
    }

    /** Update shooting and rotation for this unit. */
    @Override
    public void update(){
        for(WeaponMount mount : mounts){
            mount.weapon.update(self(), mount);
        }
    }
}
