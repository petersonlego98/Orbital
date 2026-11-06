package orbital.entities.comp;

import arc.Core;
import arc.math.geom.*;
import arc.util.*;
import orbital.annotations.Annotations.*;
import orbital.content.*;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.*;

@Component
abstract class WeaponsComp implements Teamc, Posc, Rotc, Velc, Statusc{
    @Import float x, y;
    @Import boolean disarmed;
    @Import UnitType type;

    /** weapon mount array, never null */
    @SyncLocal WeaponMount[] mounts = {};
    /**
     * Runtime-only support mounts. Each one drives a gameplay effect derived from its block.
     * Deliberately transient: the loadout is session-only, with no save or network serialization.
     */
    transient SupportMount[] supportMounts = {};
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

    /** @return the dorsal cost of all removable modules currently mounted on this unit. */
    public float loadoutCost(){
        float sum = 0f;
        for(WeaponMount mount : mounts){
            if(mount.weapon.removable){
                sum += mount.weapon.dorsalCost;
            }
        }
        return sum;
    }

    /** @return how many removable modules are currently mounted on this unit. */
    public int mountedTurrets(){
        int count = 0;
        for(WeaponMount mount : mounts){
            if(mount.weapon.removable) count++;
        }
        return count;
    }

    private SupportMount[] supports(){
        if(supportMounts == null) supportMounts = new SupportMount[0];
        return supportMounts;
    }

    /** @return true if a support block from this category is already installed. */
    public boolean hasSupportCategory(SupportModuleCategory category){
        if(category == null) return false;
        for(SupportMount mount : supports()){
            if(mount != null && mount.category == category) return true;
        }
        return false;
    }

    /** @return true if this support block has a distinct, non-overlapping anchor available. */
    public boolean canMountSupport(Block block){
        return findSupportAnchor(block) >= 0;
    }

    /** @return the first free support anchor for this block, or -1 if it cannot be mounted. */
    public int findSupportAnchor(Block block){
        SupportModuleCategory category = SupportModuleCategory.of(block);
        if(category == null || type == null || hasSupportCategory(category)) return -1;
        //a hull with a built-in equivalent already provides this effect as a fixed module
        if(type.hasNativeSupport(category)) return -1;

        float footprint = DorsalGeometry.footprint(block.size);
        for(int i = 0; i < type.supportAnchors.size; i++){
            SupportAnchor anchor = type.supportAnchors.get(i);
            if(!anchor.accepts(block)) continue;
            if(!DorsalGeometry.fitsHull(type, anchor.offsetX, anchor.offsetY, footprint)) continue;
            float drawn = supportVisualFootprintAt(anchor, block);
            if(overlapsTurretMounts(anchor.offsetX, anchor.offsetY, drawn)) continue;
            if(overlapsSupportMounts(anchor.offsetX, anchor.offsetY, drawn)) continue;
            return i;
        }
        return -1;
    }

    /**
     * Mounts a support block and activates its gameplay effect.
     * Exactly one block per category is allowed; different categories may coexist if anchors are free.
     */
    public @Nullable SupportMount mountSupport(Block block){
        int index = findSupportAnchor(block);
        if(index < 0 || type == null) return null;

        SupportAnchor anchor = type.supportAnchors.get(index);
        //drawing-only: the resolved scale is never fed back into footprints or overlap checks
        float visualScale = DorsalGeometry.supportVisualScale(type, anchor, block.size, blockRegionPx(block));
        SupportMount mount = new SupportMount(block, anchor.category, index,
            anchor.offsetX, anchor.offsetY, visualScale);
        SupportMount[] current = supports();
        SupportMount[] next = java.util.Arrays.copyOf(current, current.length + 1);
        next[current.length] = mount;
        supportMounts = next;
        Unit unit = self();
        mount.created(unit);
        return mount;
    }

    public boolean removeSupportMount(SupportMount mount){
        if(mount == null) return false;

        SupportMount[] current = supports();
        int index = -1;
        for(int i = 0; i < current.length; i++){
            if(current[i] == mount){
                index = i;
                break;
            }
        }
        if(index < 0) return false;

        SupportMount[] next = new SupportMount[current.length - 1];
        System.arraycopy(current, 0, next, 0, index);
        System.arraycopy(current, index + 1, next, index, next.length - index);
        supportMounts = next;
        //release shared state (for example the force module's shield pool) after the array
        //has been updated, so removed() can see whether another module still owns it
        mount.removed(self());
        return true;
    }

    public boolean removeSupport(Block block){
        for(SupportMount mount : supports()){
            if(mount != null && mount.block == block) return removeSupportMount(mount);
        }
        return false;
    }

    /** Native sprite side in atlas pixels; falls back to the deterministic tiled size headlessly. */
    private static float blockRegionPx(Block block){
        if(block.region != null && block.region.found()){
            return Math.max(1f, Math.max(block.region.width, block.region.height));
        }
        return Math.max(1f, block.size * TurretSlot.pxTile);
    }

    private boolean overlapsTurretMounts(float x, float y, float visualFootprint){
        for(WeaponMount mount : mounts){
            if(mount != null && mount.weapon.removable){
                if(DorsalGeometry.overlaps(x, y, visualFootprint,
                    mount.weapon.x, mount.weapon.y, turretVisualFootprint(mount.weapon))) return true;
            }
        }
        return false;
    }

    private boolean overlapsSupportMounts(float x, float y, float visualFootprint){
        for(SupportMount mount : supports()){
            if(mount == null || mount.block == null) continue;
            if(DorsalGeometry.overlaps(x, y, visualFootprint, mount.x, mount.y, mount.visualWidth())) return true;
        }
        return false;
    }

    private boolean slotOccupied(int slotIndex){
        if(slotIndex < 0) return false;
        for(WeaponMount mount : mounts){
            if(mount != null && mount.weapon.slotIndex == slotIndex && mount.weapon.removable) return true;
        }
        return false;
    }

    /**
     * @return the space this mounted turret actually occupies on the hull, in world units.
     *
     * <p>Since phase 4 the dorsal module sprite is drawn smaller than its block footprint, so the
     * physical module on the hull is the <b>drawn</b> sprite. Module-vs-module separation uses
     * this drawn footprint, while silhouette fitting and the dorsal budget keep using the full
     * block footprint.</p>
     */
    private float turretVisualFootprint(Weapon weapon){
        int size = Math.max(1, weapon.dorsalSize);
        if(type == null) return DorsalGeometry.footprint(size);
        return DorsalGeometry.visualFootprint(type, size, weapon.regionPx());
    }

    /** @return the drawn footprint, in world units, of a support block that would use this anchor. */
    private float supportVisualFootprintAt(SupportAnchor anchor, Block block){
        if(type == null || block == null) return DorsalGeometry.footprint(block == null ? 1 : block.size);
        float regionPx = blockRegionPx(block);
        return DorsalGeometry.supportVisualScale(type, anchor, block.size, regionPx) * regionPx
            / DorsalGeometry.pixelsPerWorldUnit();
    }

    /** @return the first free turret slot that also avoids installed support modules. */
    public int findTurretSlot(Turret turret){
        if(turret == null || type == null) return -1;
        return findSlot(turret.size, blockRegionPx(turret));
    }

    private int findSlot(int size, float regionPx){
        if(type == null) return -1;

        float footprint = DorsalGeometry.footprint(size);
        float drawn = DorsalGeometry.visualFootprint(type, size, regionPx);
        for(int i = 0; i < type.turretSlots.size; i++){
            TurretSlot slot = type.turretSlots.get(i);
            if(slot.maxSize < size) continue;
            if(!DorsalGeometry.fitsHull(type, slot.offsetX, slot.offsetY, footprint)) continue;
            if(slotOccupied(i)) continue;
            if(overlapsTurretMounts(slot.offsetX, slot.offsetY, drawn)) continue;
            if(overlapsSupportMounts(slot.offsetX, slot.offsetY, drawn)) continue;
            return i;
        }
        return -1;
    }

    /** @return true if the turret fits this unit's current runtime loadout. */
    public boolean canMount(Turret turret){
        if(turret == null || !TurretWeapon.canAdapt(turret)) return false;
        if(!type.hasSlotFor(turret.size) || !type.fitsSilhouette(turret.size)) return false;
        if(mountedTurrets() >= type.turretSlots.size) return false;

        float p = turret.size * TurretSlot.pxTile;
        if(loadoutCost() + p * p > type.dorsalBudget()) return false;
        return findTurretSlot(turret) >= 0;
    }

    /**
     * Mounts a turret at runtime, validating slot size, silhouette, dorsal budget and physical placement.
     * @return the created weapon, or null if the turret does not fit.
     */
    public @Nullable TurretWeapon mountTurret(Turret turret){
        if(!canMount(turret)) return null;
        int slotIndex = findTurretSlot(turret);
        if(slotIndex < 0) return null;

        TurretWeapon weapon = TurretWeapon.of(turret);
        //runtime weapons are not covered by UnitType's content init, so load sprites and init here.
        //Headless runs have no Core.atlas; skip sprite lookup there and keep the deterministic
        //size fallback instead of throwing while mounting.
        if(Core.atlas != null) weapon.load();
        weapon.init();

        TurretSlot slot = type.turretSlots.get(slotIndex);
        weapon.x = slot.offsetX;
        weapon.y = slot.offsetY;
        weapon.slotIndex = slotIndex;
        addWeapon(weapon);
        return weapon;
    }

    /** @return true if a native unit weapon fits this unit's current runtime loadout. */
    public boolean canMountWeaponModule(WeaponModules.Definition module){
        if(module == null || module.weapon == null || type == null) return false;
        if(!type.hasSlotFor(module.size) || !type.fitsSilhouette(module.size)) return false;
        if(mountedTurrets() >= type.turretSlots.size) return false;
        if(loadoutCost() + module.cost > type.dorsalBudget()) return false;
        return findSlot(module.size, module.regionPx()) >= 0;
    }

    /**
     * Mounts a native unit weapon as a dorsal combat module.
     * @return the created weapon, or null when the weapon does not fit.
     */
    public @Nullable Weapon mountWeaponModule(WeaponModules.Definition module){
        if(module == null || type == null || !canMountWeaponModule(module)) return null;

        int slotIndex = findSlot(module.size, module.regionPx());
        if(slotIndex < 0) return null;

        Weapon weapon = module.copy();
        //runtime weapons are not covered by UnitType's content init, so load sprites and init here.
        //Headless runs have no Core.atlas; skip sprite lookup there and keep the deterministic
        //size fallback instead of throwing while mounting.
        if(Core.atlas != null) weapon.load();
        weapon.init();

        //a dorsal module is a single weapon centered on its mount point.
        weapon.mirror = false;
        weapon.rotate = true;
        weapon.controllable = true;
        weapon.aiControllable = true;

        TurretSlot slot = type.turretSlots.get(slotIndex);
        weapon.x = slot.offsetX;
        weapon.y = slot.offsetY;
        weapon.slotIndex = slotIndex;
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
        if(weapon == null || !weapon.removable) return false;

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

    /** Update shooting, rotation and the effects of installed support modules. */
    @Override
    public void update(){
        for(WeaponMount mount : mounts){
            mount.weapon.update(self(), mount);
        }

        //support abilities are not part of UnitType.abilities, so they must be ticked here;
        //this is inlined into every generated unit's update() by the entity processor
        for(SupportMount mount : supports()){
            if(mount != null) mount.update(self());
        }
    }
}
