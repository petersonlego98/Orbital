package orbital.entities.abilities;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.world.*;

import static orbital.Vars.*;

/**
 * Unit analogue of {@link orbital.world.blocks.distribution.MassDriver} (the
 * {@code mass-driver} block) used as a logistics support module.
 *
 * <p>Every {@code reload} ticks the carrier launches items, up to the block's real
 * {@code itemCapacity} per launch and inside its real {@code range}:
 * <ul>
 *   <li>items the carrier is holding go to the closest allied building or unit that
 *   accepts them (unloader role);</li>
 *   <li>otherwise the team's core supplies items that are launched to the closest allied
 *   building or unit with free capacity (resupply role).</li>
 * </ul>
 *
 * <p>No power, no link configuration: the nearest valid target is chosen automatically.
 * Created by {@link orbital.entities.units.SupportMount}; never serialized and lives only
 * in the mount.</p>
 */
public class MassDriverAbility extends Ability{
    public float range = 440f, reload = 200f;
    public int launchMax = 120;
    public float boltDuration = 20f;
    public Color boltColor = Pal.accent;

    protected float timer = 0f;
    protected float boltTimer = 0f;
    protected float boltX, boltY;
    protected Color lastColor = Color.white;

    public MassDriverAbility(float range, float reload, int launchMax){
        this.range = range;
        this.reload = reload;
        this.launchMax = launchMax;
    }

    @Override
    public void update(Unit unit){
        boltTimer -= Time.delta;
        timer += Time.delta;
        if(timer < reload) return;

        //1) launch from the hull's own supply pool. The carrier keeps whatever it is holding:
        //a logistics module ships the hull's stores, not the unit's cargo
        ModuleSupply supply = unit.moduleSupply();
        Item item = supply != null ? supply.item : null;
        if(item == null || supply.ammo < 1f){
            timer = 0f;
            return;
        }

        int amount = Math.min(launchMax, (int)supply.ammo);
        Building target = Units.closestBuilding(unit.team, unit.x, unit.y, range,
            b -> b.items != null && b.block.itemCapacity > 0
                && b.items.total() < b.block.itemCapacity && b.acceptStack(item, 1, unit) > 0);
        if(target != null){
            int accepted = Math.min(amount, target.acceptStack(item, amount, unit));
            if(accepted > 0){
                Call.transferItemTo(unit, item, accepted,
                    unit.x + Mathf.range(4f), unit.y + Mathf.range(4f), target);
                supply.ammo -= accepted;
                bolt(target.x, target.y, item.color);
            }
            timer = 0f;
            return;
        }

        //no building accepts it: hand it to an allied unit instead
        Unit receiver = null;
        float best = Float.MAX_VALUE;
        for(Unit u : Groups.unit){
            if(u == unit || u.team() != unit.team || u.maxAccepted(item) <= 0) continue;
            float d = u.dst2(unit);
            if(d < best && d < range * range){
                receiver = u;
                best = d;
            }
        }
        if(receiver != null){
            int accepted = Math.min(amount, receiver.maxAccepted(item));
            if(accepted > 0){
                if(!receiver.hasItem()) receiver.stack().item = item;
                receiver.stack().amount += accepted;
                supply.ammo -= accepted;
                bolt(receiver.x, receiver.y, item.color);
            }
        }
        timer = 0f;
    }

    protected void bolt(float x, float y, Color color){
        boltTimer = boltDuration;
        boltX = x;
        boltY = y;
        lastColor = color;
        Fx.itemTransfer.at(x, y);
    }

    @Override
    public void draw(Unit unit){
        if(boltTimer <= 0f) return;

        float z = unit.type != null && unit.type.lowAltitude ? Layer.flyingUnitLow + 0.1f : Layer.flyingUnit + 0.1f;
        Draw.z(z);
        Draw.color(lastColor, boltTimer / boltDuration);
        Lines.stroke(1f);
        Lines.line(unit.x, unit.y, boltX, boltY, false);
        Lines.stroke(1f);
        Draw.color(Color.white);
        Draw.reset();
    }
}
