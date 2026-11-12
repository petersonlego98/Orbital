package orbital.entities.units;

import orbital.content.*;
import orbital.entities.abilities.*;
import orbital.entities.abilities.AimingModule;
import orbital.gen.Unit;
import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.*;
import orbital.world.blocks.defense.turrets.*;
import orbital.world.blocks.distribution.MassDriver;
import orbital.world.blocks.units.RepairTower;
import orbital.world.blocks.units.RepairTurret;

import static orbital.Vars.tilesize;

/**
 * Runtime-only dorsal support module: its physical anchor plus the gameplay effect it drives.
 *
 * <p>The effect is recreated from the mounted block's real parameters. It is never serialized
 * or synchronized, just like the {@code supportMounts} array that owns it.</p>
 *
 * <p>{@link #visualScale} is a drawing-only multiplier. {@link #footprint()} still reports the
 * full, unscaled block footprint used by every compatibility and overlap check.</p>
 *
 * <p>{@link #active} gates the effect only. A deactivated module stays mounted, keeps its
 * anchor, keeps occupying its category and still blocks a second module of that type; the
 * bottom HUD toggles it without touching the loadout, budget or serialization.</p>
 */
public class SupportMount{
    public final Block block;
    public final SupportModuleCategory category;
    public final int anchorIndex;
    /** hull-local position, in world units */
    public final float x, y;
    /** visual-only sprite scale, in (0, 1]; resolved from the hull and its anchor at mount time */
    public final float visualScale;

    /**
     * Whether the effect picks its own target. True for every support module except the
     * liquid jets ({@code wave} and {@code tsunami}): those spray at the closest enemy on
     * their own in the block version, and here the pilot drives them - the jet fires only
     * while the hull is firing, toward the hull's aim point.
     *
     * <p><b>Manual aiming is opt-in, not the default.</b> Every category - the liquid modules
     * included - starts on auto-targeting, because a dorsal module reproduces the block it
     * comes from and the block acts on its own. Leaving {@code wave} and {@code tsunami}
     * manual by default meant they never did anything unless the pilot held fire: the jet
     * stayed silent and, worse, the support half (which extinguishes allies and slows enemies)
     * was gated behind the same trigger, so the modules were inert while the hull idled.
     * The pilot can still switch the aim mode to manual from the loadout dialog, and the
     * effect stays able to follow the hull's aim point when that happens.</p>
     *
     * <p>Toggleable from the loadout dialog while the module stays installed; it never
     * changes {@link #active}, the anchor or the footprint.</p>
     */
    public boolean autoTarget = true;

    /** Gameplay effect driven each frame. Transient by ownership, never written to saves or sync. */
    public transient Ability ability;

    /**
     * Whether the effect is running. Toggled from the bottom HUD; the mount stays installed,
     * keeps its anchor and keeps occupying its category. Never serialized, just like the
     * {@code supportMounts} array that owns it.
     */
    public boolean active = true;

    public SupportMount(Block block, SupportModuleCategory category, int anchorIndex, float x, float y){
        this(block, category, anchorIndex, x, y, 1f);
    }

    public SupportMount(Block block, SupportModuleCategory category, int anchorIndex, float x, float y, float visualScale){
        this.block = block;
        this.category = category;
        this.anchorIndex = anchorIndex;
        this.x = x;
        this.y = y;
        this.visualScale = visualScale > 0f ? Math.min(1f, visualScale) : 1f;
        //every module aims by itself, except the liquid jets: wave and tsunami are piloted
        //weapons, so the pilot's fire control is their trigger (see LiquidSupportAbility).
        //An AI hull has no fire control, so those modules keep their own target there and
        //never sit dead on a wave ship.
        this.autoTarget = category != SupportModuleCategory.liquid;
        this.ability = createAbility(block);
        //the effect keeps its own copy of the aim mode, so both start on the same value
        if(ability instanceof LiquidSupportAbility liquid) liquid.autoTarget = this.autoTarget;
        //a module that aims like its block needs to know where it sits on the hull, or its
        //turret would turn around the carrier's centre and its shot would leave from there
        if(ability instanceof AimingModule aim) aim.anchor(x, y);
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
     * Turns the effect on/off while keeping the module installed. Deactivating releases the
     * shared shield pool; reactivating reinitializes it from the block's real parameters.
     * A no-op when the state is unchanged, so a HUD click is safe to repeat.
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

    /** Initializes shared unit state owned by this effect (for example a shield pool). */
    public void created(Unit unit){
        if(active && ability != null && unit != null) ability.created(unit);
    }

    /**
     * Switches the module between aiming by itself and being piloted, keeping it installed.
     * The effect follows the flag: the liquid jet of {@code wave} and {@code tsunami} reads
     * its own copy, so both are updated here - never one without the other.
     */
    public void setAutoTarget(boolean value){
        autoTarget = value;
        if(ability instanceof LiquidSupportAbility liquid) liquid.autoTarget = value;
    }

    /** Ticks the effect. Called from the unit's per-frame update, alongside weapon mounts. */
    public void update(Unit unit){
        if(active && ability != null && unit != null) ability.update(unit);
    }

    /** Draws the effect with the same convention as native abilities. */
    public void draw(Unit unit){
        if(active && ability != null && unit != null) ability.draw(unit);
    }

    /** Releases shared unit state when the module is removed or deactivated. */
    public void removed(Unit unit){
        if(!(ability instanceof ForceFieldAbility) || unit == null) return;

        //unit.shield is shared state: never clear a pool owned by a native ability or by
        //another active installed force module.
        if(unit.type != null && unit.type.hasNativeSupport(SupportModuleCategory.force)) return;
        if(unit.supportMounts != null){
            for(SupportMount other : unit.supportMounts){
                if(other != null && other != this && other.active && other.ability instanceof ForceFieldAbility) return;
            }
        }
        unit.shield = 0f;
    }

    /**
     * Builds the unit-scale analogue of a projector block's effect.
     *
     * <p>The original blocks affect buildings and require power/items. Units have neither,
     * so the equivalent area abilities are used instead, with the block's own range, reload,
     * strength, boost multiplier, shield health and shield radius. Phase-fabric boosts are
     * intentionally not applied, since units have no item/power inventory.</p>
     *
     * <p>The repair modules heal <b>flying</b> units and damaged allied <b>buildings</b>:
     * a dorsal repair field belongs to an air hull, so it serves the wing the carrier flies
     * with, while still patching the base it is defending.</p>
     */
    private static Ability createAbility(Block block){
        if(block instanceof MendProjector mend){
            //block: heals buildings for healPercent% of max health every reload ticks
            //unit analogue: the same percentage over allied air units and constructions
            RepairFieldAbility field = new RepairFieldAbility(0f, mend.reload, mend.range, mend.healPercent);
            field.airOnly = true;
            field.buildings = true;
            return field;
        }

        if(block instanceof RepairTurret repair){
            //block: repairs the closest unit at repairSpeed hp per tick inside repairRadius,
            //with a beam of beamWidth; no power or coolant on units
            RepairBeamAbility beam = new RepairBeamAbility(repair.repairRadius, repair.repairSpeed, repair.beamWidth);
            beam.airOnly = true;
            beam.buildings = true;
            //the block's beam is aimed, not a turning turret, so the module's turret tracks
            //about as fast as the beam can be re-pointed
            beam.rotateSpeed = 45f;
            return beam;
        }

        if(block instanceof RepairTower tower){
            //block: heals every damaged unit in range by healAmount per tick
            //unit analogue: the same field, refreshed every tick, over flying units and buildings
            RepairFieldAbility field = new RepairFieldAbility(tower.healAmount, 1f, tower.range);
            field.airOnly = true;
            field.buildings = true;
            return field;
        }

        if(block instanceof TractorBeamTurret tractor){
            //block: pulls the closest enemy air unit with force + proximity bonus,
            //damaging it continuously; the same profile, centred on the carrier
            TractorAbility pull = new TractorAbility(tractor.range, tractor.force, tractor.scaledForce, tractor.damage);
            //the block's turret turns at rotateSpeed, so the module's does too
            pull.rotateSpeed = tractor.rotateSpeed;
            return pull;
        }

        if(block instanceof PointDefenseTurret point){
            //block: shoots down the closest hittable enemy bullet every reload ticks
            //with bulletDamage; the same interception, around the carrier
            return new PointDefenseAbility(point.range, point.bulletDamage, point.reload);
        }

        if(block instanceof LiquidTurret liquid){
            //block: sprays a liquid jet at enemies and extinguishes fires; unit analogue:
            //the same jet from the carrier, fed by the hull's chosen liquid
            return LiquidSupportAbility.from(liquid);
        }

        if(block instanceof MassDriver driver){
            //block: launches up to itemCapacity items within range to a linked building;
            //unit analogue: auto-targeting logistics to buildings or allied units
            return new MassDriverAbility(driver.range, driver.reload, driver.itemCapacity);
        }

        if(block instanceof OverdriveProjector drive){
            //block: applyBoost(speedBoost) to nearby buildings
            //unit analogue: a timed status with the same speed multiplier, applied in the same
            //range and reload; the dome differs by its 2.5x boost and 200-unit range
            StatusEffect status = SupportEffects.forBlock(block);
            if(status == null){
                //modded/renamed projector fallback: no dedicated effect to refresh
                status = StatusEffects.overclock;
            }else{
                //refresh at mount time: JSON patches from mods can change speedBoost after
                //base content was created, so the load-time default may be stale
                status.speedMultiplier = drive.speedBoost;
            }
            return new StatusFieldAbility(status, drive.reload + 1f, drive.reload, drive.range);
        }

        if(block instanceof ForceProjector force){
            //block: polygonal shield with shieldHealth, cooldownNormal regen/tick, radius and sides
            //unit analogue: ForceFieldAbility; cooldown is derived as the time to regen the full pool
            float regen = Math.max(force.cooldownNormal, 0.01f);
            return new ForceFieldAbility(force.radius, regen, force.shieldHealth,
                force.shieldHealth / regen, force.sides, force.shieldRotation);
        }

        return null;
    }
}
