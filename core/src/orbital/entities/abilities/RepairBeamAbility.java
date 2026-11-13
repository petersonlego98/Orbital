package orbital.entities.abilities;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import orbital.entities.Units;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.ModuleSupply;
import orbital.world.consumers.ConsumeLiquidBase;

import static orbital.Vars.*;

/**
 * Unit analogue of {@link orbital.world.blocks.units.RepairTurret} (the
 * {@code repair-point} and {@code repair-turret} blocks).
 *
 * <p>Continuously repairs the closest damaged allied unit with the block's real
 * {@code repairRadius}, {@code repairSpeed} and {@code beamWidth}. The block heals
 * {@code repairSpeed} hit points per tick with a beam; the module reproduces exactly
 * that. When the block accepts coolant (the {@code repair-turret}), the module draws that
 * coolant from the hull's liquid pool and heals faster by the same heat-capacity bonus.
 * Created by {@link orbital.entities.units.SupportMount}; never serialized and lives only
 * in the mount.</p>
 *
 * <p>When {@link #buildings} is set, damaged allied constructions are valid targets too,
 * so a dorsal repair module also patches the base it is defending.</p>
 */
public class RepairBeamAbility extends Ability implements AimingModule{
    public float range = 60f, repairSpeed = 0.3f, beamWidth = 1f;
    public Color color = Color.valueOf("98ffa9");
    /** When true only flying units are healed: the carrier's beam serves its own wing. */
    public boolean airOnly = false;
    /** When true damaged allied buildings can also be repaired. */
    public boolean buildings = false;
    /** How fast the module's turret turns toward the repaired target, in degrees per tick. */
    public float rotateSpeed = 10f;
    /** hull-local dock of the module, in world units. */
    public float anchorX, anchorY;
    /** current aim, relative to the hull, in degrees. */
    public float rotation = 90f;
    /**
     * Optional coolant consumer copied from the block. The block's own {@code repair-turret}
     * accepts coolant and heals faster; the module reads the same consumer, so it pays the
     * same liquid per tick out of the hull's pool.
     */
    public @Nullable ConsumeLiquidBase coolant;
    /** Extra healing per unit of the coolant's heat capacity, from the block. */
    public float coolantMultiplier = 0f;

    protected @Nullable Unit target;
    protected @Nullable Building buildingTarget;
    protected float strength;
    /** How often to re-acquire the closest damaged ally. */
    public float retargetTime = 10f;
    protected float retimer = 0f;

    public RepairBeamAbility(float range, float repairSpeed, float beamWidth){
        this.range = range;
        this.repairSpeed = repairSpeed;
        this.beamWidth = beamWidth;
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
        retimer += Time.delta;
        if(retimer >= retargetTime || (target == null && buildingTarget == null)){
            retimer = 0f;
            //the beam starts at the module, so it measures the block's radius from the module:
            //ally across the other side of the hull is out of the beam's reach, as with the block
            float dockX = moduleX(unit), dockY = moduleY(unit);
            target = Units.closest(unit.team, dockX, dockY, u -> u.damaged() && (!airOnly || u.isFlying()));
            buildingTarget = buildings
                ? Units.closestBuilding(unit.team, dockX, dockY, range, b -> b.damaged() && !b.isHealSuppressed())
                : null;

            //keep only the closest candidate, so the beam goes to whatever needs it most
            if(target != null && buildingTarget != null){
                float unitDst = target.dst(dockX, dockY) - target.hitSize / 2f;
                float buildingDst = buildingTarget.dst(dockX, dockY) - buildingTarget.hitSize() / 2f;
                if(unitDst <= buildingDst){
                    buildingTarget = null;
                }else{
                    target = null;
                }
            }
        }

        float dockX = moduleX(unit), dockY = moduleY(unit);
        boolean unitValid = target != null && target.dst(dockX, dockY) - target.hitSize / 2f <= range;
        boolean buildingValid = buildingTarget != null
            && buildingTarget.dst(dockX, dockY) - buildingTarget.hitSize() <= range && buildingTarget.isValid();

        if(unitValid || buildingValid){//no else
            //what the beam is pointed at, so the turret can turn to face it
            float aimX, aimY;
            if(unitValid){
                aimX = target.x;
                aimY = target.y;
            }else{
                aimX = buildingTarget.x;
                aimY = buildingTarget.y;
            }
            rotation = ModuleDock.turn(rotation, ModuleDock.aimAt(unit, dockX, dockY, aimX, aimY),
                rotateSpeed, Time.delta);
        }

        //the block pays its coolant only while it has a target, and the heat capacity of the
        //liquid it drew boosts the repair speed. The module does the same out of the hull pool.
        ModuleSupply supply = unit.moduleSupply();
        float coolantEfficiency = 0f;
        if((unitValid || buildingValid) && coolant != null && supply != null){
            coolantEfficiency = supply.useLiquid(coolant, Time.delta);
        }
        float coolantHeat = supply != null && supply.liquid != null ? supply.liquid.heatCapacity : 0f;
        float repairMultiplier = 1f + coolantHeat * coolantMultiplier * coolantEfficiency;

        if(unitValid){
            target.heal(repairSpeed * repairMultiplier * Time.delta);
            strength = Mathf.lerpDelta(strength, 1f, 0.1f);
        }else if(buildingValid){
            buildingTarget.heal(repairSpeed * repairMultiplier * Time.delta);
            strength = Mathf.lerpDelta(strength, 1f, 0.1f);
        }else{
            target = null;
            buildingTarget = null;
            strength = Mathf.lerpDelta(strength, 0f, 0.1f);
        }
    }

    @Override
    public void draw(Unit unit){
        if((target == null && buildingTarget == null) || strength <= 0.02f) return;

        float dockX = moduleX(unit), dockY = moduleY(unit);
        float tx = target != null ? target.x : buildingTarget.x;
        float ty = target != null ? target.y : buildingTarget.y;
        float z = unit.type != null && unit.type.lowAltitude ? Layer.flyingUnitLow + 0.1f : Layer.flyingUnit + 0.1f;
        Draw.z(z);
        Draw.color(color, strength);
        Lines.stroke(beamWidth * strength);
        Lines.line(dockX, dockY, tx, ty, false);
        Lines.stroke(1f);
        Draw.color(Color.white);
        Draw.reset();
    }
}
