package orbital.world.blocks.defense.turrets;

import arc.struct.*;
import orbital.entities.bullet.*;
import orbital.logic.*;
import orbital.world.meta.*;

public class PowerTurret extends Turret{
    public BulletType shootType;

    public PowerTurret(String name){
        super(name);
        hasPower = true;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.ammo, StatValues.ammo(ObjectMap.of(this, shootType)));
    }

    public void limitRange(float margin){
        limitRange(shootType, margin);
    }

    public class PowerTurretBuild extends TurretBuild{

        @Override
        public float getAmmoFraction(){
            return power == null ? 0f : power.status;
        }

        @Override
        public double sense(LAccess sensor){
            return switch(sensor){
                case ammo -> power == null ? 0f : power.status;
                case ammoCapacity -> 1;
                case heat -> heatRequirement > 0 ? heatReq : Float.NaN;
                default -> super.sense(sensor);
            };
        }

        @Override
        public BulletType useAmmo(){
            //nothing used directly
            return shootType;
        }

        @Override
        public boolean hasAmmo(){
            //you can always rotate, but never shoot if there's no power
            return true;
        }

        @Override
        public BulletType peekAmmo(){
            return shootType;
        }
    }
}
