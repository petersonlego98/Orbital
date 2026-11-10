package orbital.entities.abilities;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import orbital.entities.Units;
import orbital.gen.*;
import orbital.graphics.*;

import static orbital.Vars.*;

/**
 * Unit analogue of {@link orbital.world.blocks.units.RepairTurret} (the
 * {@code repair-point} and {@code repair-turret} blocks).
 *
 * <p>Continuously repairs the closest damaged allied unit with the block's real
 * {@code repairRadius}, {@code repairSpeed} and {@code beamWidth}. The block heals
 * {@code repairSpeed} hit points per tick with a beam; the module reproduces exactly
 * that, without power or coolant. Created by {@link orbital.entities.units.SupportMount};
 * never serialized and lives only in the mount.</p>
 */
public class RepairBeamAbility extends Ability{
    public float range = 60f, repairSpeed = 0.3f, beamWidth = 1f;
    public Color color = Color.valueOf("98ffa9");

    protected @Nullable Unit target;
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
    public void update(Unit unit){
        retimer += Time.delta;
        if(retimer >= retargetTime || target == null){
            retimer = 0f;
            target = Units.closest(unit.team, unit.x, unit.y, u -> u.damaged());
        }

        if(target != null && target.within(unit, range)){
            target.heal(repairSpeed * Time.delta);
            strength = Mathf.lerpDelta(strength, 1f, 0.1f);
        }else{
            target = null;
            strength = Mathf.lerpDelta(strength, 0f, 0.1f);
        }
    }

    @Override
    public void draw(Unit unit){
        if(target == null || strength <= 0.02f) return;

        float z = unit.type != null && unit.type.lowAltitude ? Layer.flyingUnitLow + 0.1f : Layer.flyingUnit + 0.1f;
        Draw.z(z);
        Draw.color(color, strength);
        Lines.stroke(beamWidth * strength);
        Lines.line(unit.x, unit.y, target.x, target.y, false);
        Lines.stroke(1f);
        Draw.color(Color.white);
        Draw.reset();
    }
}
