package orbital.ai.types;

import arc.util.*;
import orbital.ai.*;
import orbital.entities.*;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.world.blocks.ConstructBlock.*;

public class RepairAI extends AIController{
    public static float retreatDst = 160f, fleeRange = 310f, retreatDelay = Time.toSeconds * 3f;

    /** Own timer slot: {@code timerTarget} (0) belongs to weapon retargeting in {@link #updateWeapons()}. */
    private static final int timerDamaged = timerTarget5;

    @Nullable Teamc avoid;
    float retreatTimer;
    Building damagedTarget;

    @Override
    public void updateMovement(){
        if(target instanceof Building){
            boolean shoot = false;

            if(target.within(unit, unit.type.range)){
                unit.aim(target);
                shoot = true;
            }

            //the repair beam steers the hull's own weapons; a mounted dorsal turret keeps the
            //target its own targeting picked, or it would be silenced while the unit repairs
            unit.controlNonTurretWeapons(shoot);
        }else if(target == null){
            unit.controlNonTurretWeapons(false);
        }

        boolean hold = hasStance(UnitStance.holdPosition);

        if(target != null && target instanceof Building b && b.team == unit.team){
            if(!hold){
                if(unit.type.circleTarget){
                    circleAttack(unit.type.circleTargetRadius);
                }else if(!target.within(unit, unit.type.range * 0.65f)){
                    moveTo(target, unit.type.range * 0.65f);
                }
            }

            if(!unit.type.circleTarget){
                unit.lookAt(target);
            }
        }

        //not repairing
        if(!(target instanceof Building) && !hold){
            if(timer.get(timerTarget4, 40)){
                avoid = target(unit.x, unit.y, fleeRange, true, true);
            }

            if((retreatTimer += Time.delta) >= retreatDelay){
                //fly away from enemy when not doing anything
                if(avoid != null){
                    var core = unit.closestCore();
                    if(core != null && !unit.within(core, retreatDst)){
                        moveTo(core, retreatDst);
                    }
                }
            }
        }else{
            retreatTimer = 0f;
        }
    }

    @Override
    public void updateTargeting(){
        if(timer.get(timerDamaged, 15)){
            damagedTarget = Units.findDamagedTile(unit.team, unit.x, unit.y);
            if(damagedTarget instanceof ConstructBuild) damagedTarget = null;
        }

        //weapons keep their own targeting while the unit repairs: a dorsal turret mounted on a
        //repair ship must still engage whatever its own range and ammo allow
        if(unit.hasWeapons()) updateWeapons();

        if(damagedTarget != null){
            this.target = damagedTarget;
        }
    }
}
