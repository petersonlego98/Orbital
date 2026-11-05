package orbital.type;

import orbital.world.blocks.defense.turrets.*;

/**
 * A dorsal mounting point on a unit.
 * Slots only describe the available space; the turret itself is a removable module.
 */
public class TurretSlot{
    /** side of one block in sprite pixels. The dorsal budget is measured in sprite pixels. */
    public static final float pxTile = 32f;
    /** maximum turret size, in blocks (1 = 1x1, 5 = 5x5). */
    public int maxSize;
    /** offset from the unit center, in world units. */
    public float offsetX, offsetY;
    /** optional display name, useful for UI and debugging. */
    public String name;

    public TurretSlot(int maxSize){
        this(maxSize, 0f, 0f);
    }

    public TurretSlot(int maxSize, float offsetX, float offsetY){
        this.maxSize = maxSize;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public TurretSlot(String name, int maxSize, float offsetX, float offsetY){
        this(maxSize, offsetX, offsetY);
        this.name = name;
    }

    /** @return true if this slot can physically hold the turret's footprint. */
    public boolean accepts(Turret turret){
        return turret != null && turret.size <= maxSize;
    }
}
