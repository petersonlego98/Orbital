package orbital.entities.units;

import orbital.entities.abilities.*;
import orbital.gen.Unit;
import orbital.type.*;
import orbital.world.Block;

import arc.graphics.Color;

import static orbital.Vars.tilesize;

/**
 * Runtime-only dorsal wall module: one wall or rampart bolted to a hull's turret space.
 *
 * <p>Wall plates deliberately do <b>not</b> use a support anchor (see
 * {@code SupportModuleCategory.armor}): they compete with the removable turrets for both dorsal
 * slots and dorsal area, which is what the design asked for. A hull may therefore carry any
 * number of wall plates; the limit is space and budget, never category exclusivity.</p>
 *
 * <p>The effect is recreated from the wall block's own parameters by
 * {@link WallModuleAbility}. Like support mounts, it is never serialized or synchronized.</p>
 *
 * <p>{@link #visualScale} is a drawing-only multiplier: {@link #footprint()} keeps reporting the
 * full, unscaled block footprint used by every placement and overlap check.</p>
 */
public class WallMount{
    public final Block block;
    /** index of the dorsal slot this plate occupies - the same space a turret would use */
    public final int slotIndex;
    /** hull-local position, in world units */
    public final float x, y;
    /** visual-only sprite scale, in (0, 1]; resolved from the hull at mount time */
    public final float visualScale;

    /** Gameplay effect driven each frame. Transient by ownership, never written to saves or sync. */
    public transient WallModuleAbility ability;

    /** Material colour of the wall - applied as a tint on the mounted plate, not the whole hull. */
    public Color wallColor = Color.white;

    /**
     * Whether the effect is running. A deactivated plate stays mounted, keeps its slot and keeps
     * its shape on the hull; it only stops defending.
     */
    public boolean active = true;

    public WallMount(Block block, int slotIndex, float x, float y, float visualScale){
        this.block = block;
        this.slotIndex = slotIndex;
        this.x = x;
        this.y = y;
        this.visualScale = visualScale > 0f ? Math.min(1f, visualScale) : 1f;
        this.ability = new WallModuleAbility(block);
        this.wallColor = ability != null ? ability.wallColor : Color.white;
    }

    /** @return the square physical footprint side length, in world units. Never scaled. */
    public float footprint(){
        return block.size * tilesize;
    }

    /** Native sprite side in atlas pixels, with a deterministic fallback for headless runs. */
    public float regionPx(){
        if(block.region != null && block.region.found()){
            return Math.max(1f, Math.max(block.region.width, block.region.height));
        }
        return Math.max(1f, block.size * TurretSlot.pxTile);
    }

    /** @return the drawn sprite width in world units, at the game's standard sprite scale. */
    public float visualWidth(){
        return regionPx() * visualScale / DorsalGeometry.pixelsPerWorldUnit();
    }

    /** @return the drawn sprite width as a fraction of this hull's drawn width. */
    public float visualWidthFraction(UnitType type){
        if(type == null) return 1f;
        float hullPx = (float)Math.sqrt(type.spriteArea());
        return hullPx > 0f ? regionPx() * visualScale / hullPx : 1f;
    }

    /**
     * Turns the effect on/off while keeping the plate installed. Deactivating releases the shared
     * shield pool; reactivating reinitializes it from the wall's real parameters.
     */
    public void setActive(Unit unit, boolean value){
        if(active == value) return;

        active = value;
        if(active){
            created(unit);
        }else{
            removed(unit);
        }
    }

    /** Initializes shared unit state owned by this effect (for example the shield pool). */
    public void created(Unit unit){
        if(active && ability != null && unit != null){
            ability.created(unit);
            //tint the hull with this plate's material colour
            unit.wallColor.set(ability.wallColor);
        }
    }

    /** Ticks the effect. Called from the unit's per-frame update, alongside weapon and support mounts. */
    public void update(Unit unit){
        if(active && ability != null && unit != null){
            ability.update(unit);
            unit.wallColor.set(ability.wallColor);
        }else if(!active && unit != null){
            unit.wallColor.set(Color.white);
        }
    }

    /** Draws the effect with the same convention as native abilities. */
    public void draw(Unit unit){
        if(active && ability != null && unit != null) ability.draw(unit);
    }

    /** Releases shared unit state when the plate is removed or deactivated. */
    public void removed(Unit unit){
        if(ability == null || unit == null) return;

        //clear hull tint when the plate is removed
        unit.wallColor.set(Color.white);

        if(ability.shieldHealth <= 0f) return;

        //unit.shield is shared state: never clear a pool owned by a native ability or by
        //another active force module
        if(unit.type != null && unit.type.hasNativeSupport(SupportModuleCategory.force)) return;
        if(unit.supportMounts != null){
            for(Object mount : unit.supportMounts){
                if(mount instanceof SupportMount other && other != null && other.active &&
                    other.ability instanceof ForceFieldAbility) return;
            }
        }
        if(unit.wallMounts != null){
            for(WallMount other : unit.wallMounts){
                if(other != null && other != this && other.active && other.ability != null &&
                    other.ability.shieldHealth > 0f) return;
            }
        }
        unit.shield = 0f;
    }
}
