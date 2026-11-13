package orbital.entities.comp;

import arc.Core;
import arc.graphics.Color;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import orbital.annotations.Annotations.*;
import orbital.content.*;
import orbital.ctype.UnlockableContent;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.type.*;
import orbital.world.Block;
import orbital.world.blocks.defense.turrets.*;

import static orbital.Vars.*;

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
    /**
     * Runtime-only wall plates. They are not support modules: a wall plate occupies a dorsal
     * <b>turret</b> slot and dorsal area (see {@link #findWallSlot}), so a hull can carry any
     * number of them while space and budget last. Deliberately transient, exactly like
     * {@code supportMounts}.
     */
    transient WallMount[] wallMounts = {};
    /**
     * Runtime-only supply pool shared by every dorsal module of this unit: one ammo/liquid
     * resource plus energy, with the values derived from the blocks the modules come from.
     * Deliberately transient, exactly like the loadout arrays that decide its size.
     */
    transient ModuleSupply moduleSupply = new ModuleSupply();
    /**
     * Runtime-only, client-side pilot selection for this hull's own weapons: every weapon in
     * this set is <b>switched off</b>. It stays mounted, keeps its slot and its dorsal budget
     * entry and keeps rotating with the hull, but it never fires until it is switched back on
     * from the bottom module bar or with the module number keys.
     *
     * <p>The set is keyed by weapon identity, and the entry dies with the weapon: removing a
     * module drops it (see {@link #removeWeapon}). Deliberately transient: this is the local
     * pilot's choice, so it never enters a save or the network stream.</p>
     */
    public transient ObjectSet<Weapon> disarmedModules = new ObjectSet<>();
    /**
     * Runtime-only, client-side pilot selection: the dorsal weapon module the pilot picked as
     * the <b>only one to fire</b>. While it is set, every other weapon module of this hull is
     * silenced (see {@link #disarmedModules}) - a weapon module the pilot did not pick stays
     * mounted and keeps tracking the aim, it just never shoots. {@code null} means no module is
     * picked and every weapon module fires together, which is the state a fresh hull starts in.
     *
     * <p>Support modules are not part of the selection: they have nothing to fire, so the same
     * gesture only switches their effect on and off (see {@link #toggleModule}). Deliberately
     * transient, for the same reason as {@link #disarmedModules}: it is the local pilot's
     * choice, made on the hull it was made with, and it dies with both.</p>
     */
    public transient @Nullable Weapon selectedModule;
    /**
     * Runtime-only, client-side targeting mode for this hull's own weapons: every weapon in
     * this set picks its own target inside its own range and bullet filters, instead of firing
     * where the pilot aims. A weapon outside the set is <b>manual</b>: it fires toward the
     * pilot's aim point, which is what the default mobile and desktop controls produce.
     *
     * <p>Transient for the same reason as {@link #disarmedModules}. Support modules are not
     * affected: their effects run on their own (see {@link SupportMount#autoTarget}), except
     * the liquid jets of {@code wave} and {@code tsunami}, which the pilot drives manually.</p>
     */
    public transient ObjectSet<Weapon> autoAimModules = new ObjectSet<>();
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
        //a fresh hull starts with every module armed and in manual mode: the pilot's choice
        //belongs to the unit it was made on, and a previous hull's weapons are gone with it
        disarmedModules.clear();
        autoAimModules.clear();
        selectedModule = null;
        //content-defined removable modules (the Duo of the mono) exist before any runtime
        //mounting, so the pool is built from the spawn-time loadout as well
        refreshModuleSupply();
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

    /** @return this unit's shared module supply pool; never null. */
    public ModuleSupply moduleSupply(){
        if(moduleSupply == null) moduleSupply = new ModuleSupply();
        return moduleSupply;
    }

    /**
     * @return every item accepted as item ammo by a mounted module, in content order and
     * without repeats.
     *
     * <p>This is the list the loadout menu offers for the item-ammo bar. It mirrors what the
     * compatibility rule already allows: a turret that shares no ammo with the rest of the
     * loadout cannot be mounted, so the pool can never be fed by something the ship does not
     * carry.</p>
     */
    private Seq<Item> acceptedItems(){
        Seq<Item> options = new Seq<>(Item.class);
        for(WeaponMount mount : mounts){
            if(!(mount.weapon instanceof TurretWeapon turret)) continue;
            for(Item item : ModuleSupplies.items(turret.turret)){
                if(!options.contains(item, true)) options.add(item);
            }
        }
        for(SupportMount mount : supports()){
            if(mount.block == null) continue;
            for(Item item : ModuleSupplies.items(mount.block)){
                if(!options.contains(item, true)) options.add(item);
            }
        }
        return options;
    }

    /**
     * @return every liquid a mounted module can draw from the hull's pool, in content order
     * and without repeats.
     *
     * <p>For turret modules this is their ammo (a liquid turret's spray) plus their own coolant;
     * for support modules it also includes required liquids, so the {@code force-projector} and
     * the {@code repair-turret} receive the same liquid they would receive as blocks. The liquids
     * of the liquid modules the hull could still host come along, so the pool's resource can be
     * chosen before one is installed - the menu offers the liquid picker from the first module
     * added, not only after a wave or a tsunami is mounted. Liquid modules draw from no item
     * supply, so listing them in advance strands nothing.</p>
     */
    private Seq<Liquid> acceptedLiquids(){
        Seq<Liquid> options = new Seq<>(Liquid.class);
        for(WeaponMount mount : mounts){
            if(!(mount.weapon instanceof TurretWeapon turret)) continue;
            //a turret module's ammo liquid (a liquid turret) plus its own coolant: the coolant
            //buffer the block carries is real hull storage, so its liquids are on offer too
            for(Liquid liquid : ModuleSupplies.supportLiquids(turret.turret)){
                if(!options.contains(liquid, true)) options.add(liquid);
            }
        }
        for(SupportMount mount : supports()){
            if(mount.block == null) continue;
            for(Liquid liquid : ModuleSupplies.supportLiquids(mount.block)){
                if(!options.contains(liquid, true)) options.add(liquid);
            }
        }
        for(Block block : content.blocks()){
            //any support block that takes liquid: liquid turrets (wave/tsunami) and blocks
            //that use coolant, such as the force-projector and the repair-turret
            if(!SupportModuleCategory.isSupport(block) || ModuleSupplies.liquidCapacity(block) <= 0f) continue;
            SupportModuleCategory category = SupportModuleCategory.of(block);
            if(category == null || type == null) continue;
            //a category already taken cannot gain a second module, so its liquids are not on
            //offer - picking one would leave a pool no installed module can draw from
            if(hasSupportCategory(category) || type.hasNativeSupport(category)) continue;
            if(!type.hasAnchorFor(category, block.size)) continue;
            for(Liquid liquid : ModuleSupplies.supportLiquids(block)){
                if(!options.contains(liquid, true)) options.add(liquid);
            }
        }
        return options;
    }

    /**
     * @return every item the hull can feed its item-ammo pool with, in <b>content order</b>:
     * copper before graphite, and so on. The accepted map order is a hash order, which would
     * hand the pool an arbitrary default.
     */
    public Seq<Item> ammoItemOptions(){
        return inContentOrder(acceptedItems(), content.items());
    }

    /** @return every liquid the hull can feed its liquid pool with, in content order. */
    public Seq<Liquid> ammoLiquidOptions(){
        return inContentOrder(acceptedLiquids(), content.liquids());
    }

    /** @return the offered resources, reordered by the content's own order. */
    private static <T extends UnlockableContent> Seq<T> inContentOrder(Seq<T> offered, Seq<T> order){
        Seq<T> ordered = new Seq<>();
        for(T resource : order){
            if(offered.contains(resource, true)) ordered.add(resource);
        }
        //anything modded in after the content sweep keeps its place at the end
        for(T resource : offered){
            if(!ordered.contains(resource, true)) ordered.add(resource);
        }
        return ordered;
    }

    /**
     * Recomputes the three supply pools from the modules installed right now: each bar sums the
     * own buffers of every module of its kind, and the energy pool sums every module's power
     * drain. The chosen resource only decides what the pool is fed with, never how much it
     * holds, so the capacities do not depend on it. Called after every mount and removal, so
     * the bars never disagree with the loadout.
     *
     * <p>A chosen resource survives while it is still on offer; a pool whose resource stopped
     * being carried (its turret was removed) falls back to the first remaining one.</p>
     */
    public void refreshModuleSupply(){
        ModuleSupply supply = moduleSupply();

        Seq<Item> items = ammoItemOptions();
        if(supply.item == null || !items.contains(supply.item, true)){
            supply.item = items.size > 0 ? items.first() : null;
        }

        Seq<Liquid> liquids = ammoLiquidOptions();
        if(supply.liquid == null || !liquids.contains(supply.liquid, true)){
            supply.liquid = liquids.size > 0 ? liquids.first() : null;
        }

        float ammo = 0f, liquid = 0f, energy = 0f;
        //every module of a kind adds its own buffer to its bar, no matter which resource the
        //pool is currently fed with: capacity is a physical property of the installed modules,
        //so the bar must read the sum of all of them. A turret module's liquid buffer is the
        //coolant tank the block carries (Duo 0.1/tick, Lancer 0.2/tick, ...), so it counts
        //toward the liquid bar exactly like any other module's own storage.
        for(WeaponMount mount : mounts){
            if(!(mount.weapon instanceof TurretWeapon turret)) continue;
            ammo += ModuleSupplies.ammoCapacity(turret.turret);
            liquid += ModuleSupplies.liquidCapacity(turret.turret);
            energy += ModuleSupplies.energyCapacity(turret.turret);
        }
        for(SupportMount mount : supports()){
            if(mount.block == null) continue;
            ammo += ModuleSupplies.ammoCapacity(mount.block);
            liquid += ModuleSupplies.liquidCapacity(mount.block);
            energy += ModuleSupplies.energyCapacity(mount.block);
        }

        supply.ammoCapacity = ammo;
        supply.liquidCapacity = liquid;
        supply.energyCapacity = energy;
        supply.clamp();
    }

    /**
     * Recharges one tick of the hull's supply; it takes nothing out of the pools.
     *
     * <p><b>Energy is never consumed.</b> A power consumer's drain defines the size of its
     * energy bar, and nothing comes out of it: a dorsal module has no power line to run on, so
     * charging for energy would only take the module away from the player - which is exactly
     * what happened when the drain was real, because the recharge could not keep up and the
     * module went dark for good. Ammo and liquid stay per-shot costs (see {@link #spendAmmo}
     * and {@code LiquidSupportAbility}), because those pools describe what the module carries.</p>
     *
     * <p>The pools recharge slowly on their own - there is no supply chain carrying items or
     * liquid to a dorsal module yet - so a hull that stops firing recovers, and one that fires
     * continuously settles at the level its per-shot cost and recharge balance out to.</p>
     */
    private void upkeepModuleSupply(){
        ModuleSupply supply = moduleSupply();
        if(!supply.usesAmmo() && !supply.usesLiquid() && !supply.usesEnergy()) return;

        //recharge: the placeholder supply chain, until something real feeds these pools
        float recharge = Time.delta / ModuleSupply.refillTicks;
        supply.ammo = Math.min(supply.ammoCapacity, supply.ammo + supply.ammoCapacity * recharge);
        supply.liquidAmount = Math.min(supply.liquidCapacity, supply.liquidAmount + supply.liquidCapacity * recharge);
        supply.energy = Math.min(supply.energyCapacity, supply.energy + supply.energyCapacity * recharge);
    }

    /**
     * @return whether this mount can fire right now. The hull's own native weapons never draw
     * on the pool; a dorsal turret needs the ammo of its next shot. Energy never gates a
     * module: a dorsal module has no power line, so its energy bar is a reading, not a bill.
     */
    private boolean moduleHasAmmo(WeaponMount mount){
        if(!(mount.weapon instanceof TurretWeapon turret)) return true;

        ModuleSupply supply = moduleSupply();
        //energy never gates a module: a dorsal module has no power line to run on, so its
        //energy bar is a reading, not a bill
        if(!ModuleSupplies.accepts(turret.turret, supply.item)) return true;
        return supply.ammo >= ModuleSupplies.ammoPerShot(turret.turret);
    }

    /** @return charges the pool for one shot of a dorsal turret, if its ammo is the pool's. */
    public void spendAmmo(Weapon weapon){
        if(!(weapon instanceof TurretWeapon turret)) return;

        ModuleSupply supply = moduleSupply();
        if(!ModuleSupplies.accepts(turret.turret, supply.item)) return;
        supply.ammo = Math.max(0f, supply.ammo - ModuleSupplies.ammoPerShot(turret.turret));
    }

    /**
     * @return every item ammo this unit's turrets already accept.
     *
     * <p>The hull's native hardpoints count too: a Duo welded to a mono defines the ammo the unit
     * carries exactly like a module the player mounted later. A unit with no item-ammo turret
     * returns an empty array - its first turret is still free to pick any ammo.</p>
     */
    public Item[] loadoutAmmo(){
        Seq<Item> ammo = new Seq<>(Item.class);
        for(WeaponMount mount : mounts){
            if(mount.weapon instanceof TurretWeapon turret) ammo.addAll(turret.ammoItems());
        }
        return ammo.toArray(Item.class);
    }

    /**
     * @return whether the turret's ammo is compatible with what this unit already carries.
     *
     * <p>One dorsal loadout shares a single ammo supply, so once a turret defines it, every turret
     * mounted afterwards may only accept ammo the unit already has: otherwise part of that supply
     * would be stranded behind a turret that can never use it. Turrets without item ammo (power
     * turrets) are always compatible, since they draw no items at all.</p>
     */
    public boolean ammoCompatible(Turret turret){
        if(!ammoDefined()) return true;

        Seq<Item> ammo = new Seq<>(Item.class);
        for(Item item : loadoutAmmo()) ammo.add(item);

        for(Item item : TurretWeapon.ammoItems(turret)){
            if(!ammo.contains(item, true)) return false;
        }
        return true;
    }

    /** @return true if any mounted turret accepts item ammo - that is, the unit's ammo is defined. */
    private boolean ammoDefined(){
        for(WeaponMount mount : mounts){
            if(mount.weapon instanceof TurretWeapon turret && turret.ammoItems().size > 0) return true;
        }
        return false;
    }

    private SupportMount[] supports(){
        if(supportMounts == null) supportMounts = new SupportMount[0];
        return supportMounts;
    }

    /** @return the wall plates currently installed. */
    private WallMount[] walls(){
        if(wallMounts == null) wallMounts = new WallMount[0];
        return wallMounts;
    }

    /**
     * @return the dorsal area, in px², already taken by wall plates.
     *
     * <p>Wall plates live in the same budget as the removable turrets, so their area is accounted
     * separately and added on top of {@link #loadoutCost()} by the placement checks.</p>
     */
    public float wallLoadoutCost(){
        float sum = 0f;
        for(WallMount mount : walls()){
            if(mount != null && mount.block != null){
                float side = mount.block.size * TurretSlot.pxTile;
                sum += side * side;
            }
        }
        return sum;
    }

    /** @return true when a wall plate already occupies this dorsal slot. */
    private boolean wallSlotOccupied(int slotIndex){
        if(slotIndex < 0) return false;
        for(WallMount mount : walls()){
            if(mount != null && mount.slotIndex == slotIndex) return true;
        }
        return false;
    }

    /**
     * @return true when this wall plate fits the hull's turret space right now.
     *
     * <p>Rules, in order: the block must be a wall module of the single armour category, the hull
     * must have a free slot big enough, the plate must fit the hull silhouette, and the dorsal
     * budget must still cover turrets plus every mounted plate.</p>
     */
    public boolean canMountWall(Block block){
        return findWallSlot(block) >= 0;
    }

    /** @return the first dorsal slot this wall plate may take, or -1 when it does not fit. */
    public int findWallSlot(Block block){
        if(block == null || type == null || !SupportModuleCategory.isWallModule(block)) return -1;
        for(int i = 0; i < type.turretSlots.size; i++){
            if(acceptableWallSlot(block, i)) return i;
        }
        return -1;
    }

    /**
     * @return true when this wall plate may take this exact dorsal slot.
     *
     * <p>Same rules as {@link #findWallSlot}, checked for one slot: the plate must fit the slot's
     * size limit, sit inside the hull silhouette, not overlap anything already mounted, and leave
     * the dorsal budget covering every module on the hull.</p>
     */
    public boolean canPlaceWallAt(Block block, int slotIndex){
        if(block == null || type == null || !SupportModuleCategory.isWallModule(block)) return false;
        return acceptableWallSlot(block, slotIndex);
    }

    private boolean acceptableWallSlot(Block block, int slotIndex){
        if(!type.hasSlotFor(block.size)) return false;
        if(!type.fitsSilhouette(block.size)) return false;
        //a plate takes a slot exactly like a turret does, so there must be one left
        if(mountedTurrets() + walls().length >= type.turretSlots.size && !wallSlotOccupied(slotIndex)) return false;
        if(slotIndex < 0 || slotIndex >= type.turretSlots.size) return false;

        float side = block.size * TurretSlot.pxTile;
        if(loadoutCost() + wallLoadoutCost() + side * side > type.dorsalBudget()) return false;

        TurretSlot slot = type.turretSlots.get(slotIndex);
        if(slot.maxSize < block.size) return false;
        if(slotOccupied(slotIndex)) return false;
        if(wallSlotOccupied(slotIndex)) return false;

        //slot offsets are validated once in TurretLoadouts (see everyTurretSlotAndAnchorFitsItsHull),
        //so a plate reuses the turret rules: the drawn module must not touch anything mounted
        float drawn = DorsalGeometry.visualFootprint(type, block.size, blockRegionPx(block));
        if(overlapsTurretMounts(slot.offsetX, slot.offsetY, drawn)) return false;
        if(overlapsSupportMounts(slot.offsetX, slot.offsetY, drawn)) return false;
        return !overlapsWallMounts(slot.offsetX, slot.offsetY, drawn);
    }

    /**
     * Mounts a wall plate in the first free dorsal slot and activates its effect.
     * Plates stack freely: only space, silhouette and dorsal area limit them.
     */
    public @Nullable WallMount mountWall(Block block){
        return mountWallAt(block, findWallSlot(block));
    }

    /** Mounts a wall plate in one specific dorsal slot, or nothing when the slot is not usable. */
    public @Nullable WallMount mountWallAt(Block block, int slotIndex){
        if(!canPlaceWallAt(block, slotIndex) || type == null) return null;

        TurretSlot slot = type.turretSlots.get(slotIndex);
        //drawing-only: the resolved scale is never fed back into footprints or overlap checks
        float visualScale = DorsalGeometry.turretVisualScale(type, block.size, blockRegionPx(block));
        WallMount mount = new WallMount(block, slotIndex, slot.offsetX, slot.offsetY, visualScale);

        WallMount[] current = walls();
        WallMount[] next = java.util.Arrays.copyOf(current, current.length + 1);
        next[current.length] = mount;
        wallMounts = next;

        mount.created(self());
        refreshModuleSupply();
        return mount;
    }

    public boolean removeWallMount(WallMount mount){
        if(mount == null || walls().length == 0) return false;

        WallMount[] current = walls();
        int index = -1;
        for(int i = 0; i < current.length; i++){
            if(current[i] == mount){
                index = i;
                break;
            }
        }
        if(index < 0) return false;

        WallMount[] next = new WallMount[current.length - 1];
        System.arraycopy(current, 0, next, 0, index);
        System.arraycopy(current, index + 1, next, index, next.length - index);
        wallMounts = next;

        //release shared state (the plate's shield pool) after the array has been updated,
        //so removed() can see whether another module still owns it
        mount.removed(self());
        refreshModuleSupply();
        return true;
    }

    public boolean removeWall(Block block){
        for(WallMount mount : walls()){
            if(mount != null && mount.block == block) return removeWallMount(mount);
        }
        return false;
    }

    private boolean overlapsWallMounts(float x, float y, float visualFootprint){
        for(WallMount mount : walls()){
            if(mount == null || mount.block == null) continue;
            if(DorsalGeometry.overlaps(x, y, visualFootprint, mount.x, mount.y, mount.visualWidth())) return true;
        }
        return false;
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
        //wall plates take turret space, not a support anchor; they mount through mountWall()
        if(SupportModuleCategory.isWallModule(block)) return -1;

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
        refreshModuleSupply();
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
        refreshModuleSupply();
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
        //one supply per loadout: the turret's ammo must be part of what the unit already carries
        if(!ammoCompatible(turret)) return false;
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
        return mountTurret(turret, findTurretSlot(turret));
    }

    /**
     * Mounts a turret into one specific dorsal slot - the target of the slot grid in the
     * loadout dialog, where the player picks the slot first and the module second.
     *
     * @return the created weapon, or null if that slot does not accept the turret.
     */
    public @Nullable TurretWeapon mountTurret(Turret turret, int slotIndex){
        if(!canMountAt(turret, slotIndex)) return null;

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
        //the pool follows the loadout: the turret's own ammo buffer becomes hull storage
        refreshModuleSupply();
        return weapon;
    }

    /** @return true if this exact dorsal slot accepts the turret right now. */
    public boolean canMountAt(Turret turret, int slotIndex){
        if(turret == null || !TurretWeapon.canAdapt(turret) || type == null) return false;
        if(!ammoCompatible(turret)) return false;
        if(slotIndex < 0 || slotIndex >= type.turretSlots.size) return false;
        if(!type.hasSlotFor(turret.size) || !type.fitsSilhouette(turret.size)) return false;
        if(loadoutCost() + turret.size * TurretSlot.pxTile * turret.size * TurretSlot.pxTile > type.dorsalBudget()) return false;

        TurretSlot slot = type.turretSlots.get(slotIndex);
        return slot.maxSize >= turret.size
            && !slotOccupied(slotIndex)
            && DorsalGeometry.fitsHull(type, slot.offsetX, slot.offsetY, DorsalGeometry.footprint(turret.size));
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
        if(!canMountWeaponModule(module)) return null;
        return mountWeaponModule(module, findSlot(module.size, module.regionPx()));
    }

    /**
     * Mounts a native unit weapon into one specific dorsal slot.
     *
     * @return the created weapon, or null when that slot does not accept the module.
     */
    public @Nullable Weapon mountWeaponModule(WeaponModules.Definition module, int slotIndex){
        if(!canMountWeaponModuleAt(module, slotIndex)) return null;

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
        refreshModuleSupply();
        return weapon;
    }

    /** @return true if this exact dorsal slot accepts the combat module right now. */
    public boolean canMountWeaponModuleAt(WeaponModules.Definition module, int slotIndex){
        if(module == null || module.weapon == null || type == null) return false;
        if(slotIndex < 0 || slotIndex >= type.turretSlots.size) return false;
        if(!type.hasSlotFor(module.size) || !type.fitsSilhouette(module.size)) return false;
        if(loadoutCost() + module.cost > type.dorsalBudget()) return false;

        TurretSlot slot = type.turretSlots.get(slotIndex);
        return slot.maxSize >= module.size
            && !slotOccupied(slotIndex)
            && DorsalGeometry.fitsHull(type, slot.offsetX, slot.offsetY, DorsalGeometry.footprint(module.size));
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
        //the module is gone: its pilot selection goes with it
        disarmedModules.remove(weapon);
        autoAimModules.remove(weapon);
        //the module the pilot picked no longer exists: the rest of the loadout keeps its
        //selection only while the chosen module is still mounted, otherwise the hull would
        //silence itself for a module that is no longer there. A group keeps its selection
        //while another copy of it is mounted - dropping one Duo of two does not un-pick Duo
        if(selectedModule != null && sameModule(selectedModule, weapon)){
            Weapon survivor = null;
            for(WeaponMount other : mounts){
                if(sameModule(other.weapon, selectedModule) && !disarmedModules.contains(other.weapon)){
                    survivor = other.weapon;
                    break;
                }
            }
            selectedModule = survivor;
            if(survivor == null) disarmedModules.clear();
        }
        refreshModuleSupply();
        return true;
    }

    //region pilot-facing module control (bottom module bar, number keys, loadout dialog)

    /**
     * @return how many switchable modules this unit carries, in the order the bottom module
     * bar draws them: <b>one per group of identical dorsal weapons</b>, then every support
     * module. Wall plates are not counted - a plate defends while it is mounted, so it has
     * nothing to switch.
     *
     * <p>A hull with three Duo modules counts three weapon mounts in {@code mounts} but reports
     * <b>one</b> module here: the bar draws one cell for the group, and the number keys point
     * at the group, so the pilot never has to press the same key three times.</p>
     */
    public int moduleCount(){
        int count = moduleGroups().size;
        for(SupportMount mount : supports()){
            if(mount != null && mount.block != null) count++;
        }
        return count;
    }

    /**
     * @return one representative weapon per group of identical dorsal weapons, in the order
     * the bottom module bar draws them (first occurrence of each kind).
     *
     * <p>This is the model of the bar itself: one cell per group. Everything the pilot can
     * address - the number keys, a touch on a cell, the loadout rows that map to it - walks
     * this list, so two Duo mounted are one module with two copies.</p>
     */
    public Seq<Weapon> moduleGroups(){
        Seq<Weapon> groups = new Seq<>(Weapon.class);
        for(WeaponMount mount : mounts){
            if(!mount.weapon.isTurretMount()) continue;
            boolean seen = false;
            for(Weapon group : groups){
                if(sameModule(group, mount.weapon)){ seen = true; break; }
            }
            if(!seen) groups.add(mount.weapon);
        }
        return groups;
    }

    /** @return how many copies of this module kind are mounted on the hull. */
    public int moduleCopies(Weapon weapon){
        int copies = 0;
        for(WeaponMount mount : mounts){
            if(sameModule(mount.weapon, weapon)) copies++;
        }
        return copies;
    }

    /**
     * @return the weapon module occupying this bar position, or null when the position is a
     * support module (or past the end of the bar).
     */
    private @Nullable Weapon weaponModule(int index){
        Seq<Weapon> groups = moduleGroups();
        return index >= 0 && index < groups.size ? groups.get(index) : null;
    }

    /** @return the support module occupying this bar position, or null when none does. */
    private @Nullable SupportMount supportModule(int index){
        int i = index - moduleGroups().size;
        if(i < 0) return null;
        for(SupportMount mount : supports()){
            if(mount == null || mount.block == null) continue;
            if(i-- == 0) return mount;
        }
        return null;
    }

    /** @return the name of the module at this position, or null when there is none. */
    public @Nullable String moduleName(int index){
        Weapon weapon = weaponModule(index);
        if(weapon != null){
            return weapon.moduleDisplayName != null && !weapon.moduleDisplayName.isEmpty()
                ? weapon.moduleDisplayName : weapon.name;
        }
        SupportMount mount = supportModule(index);
        return mount == null ? null : mount.block.localizedName;
    }

    /**
     * @return whether the module at this position fires right now: a weapon module fires only
     * while it is the pilot's pick, or while nothing is picked and every module fires together.
     * A support module reports whether its effect runs.
     */
    public boolean moduleActive(int index){
        Weapon weapon = weaponModule(index);
        if(weapon != null) return !disarmedModules.contains(weapon);

        SupportMount mount = supportModule(index);
        return mount != null && mount.active;
    }

    /**
     * @return whether the module at this position is a weapon module - one that fires. Wall
     * plates never reach this numbering, so a support module answers false, and so does an
     * index past the end of the bar.
     */
    public boolean moduleFires(int index){
        return weaponModule(index) != null;
    }

    /** @return the dorsal weapon module the pilot picked, or null when every module fires. */
    public @Nullable Weapon selectedModule(){
        return selectedModule;
    }

    /** @return whether the module at this position is the one the pilot picked to fire. */
    public boolean moduleSelected(int index){
        if(selectedModule == null) return false;
        Weapon weapon = weaponModule(index);
        return weapon != null && sameModule(weapon, selectedModule);
    }

    /**
     * Picks the module at this position as the <b>only one to fire</b>: every other weapon
     * module of this hull is silenced - it stays mounted, keeps its slot, anchor, category and
     * dorsal budget and keeps tracking the aim, it just never shoots - while the chosen module
     * and <b>every copy of it</b> are armed and fire. Picking the same module again releases the
     * selection, and every weapon module fires together again.
     *
     * <p>The gesture is what the bottom bar cells and the module number keys both call, so the
     * two always agree on which module is shooting.</p>
     *
     * @return true when this module now fires alone (weapon) or its effect now runs (support);
     * false when the selection was released.
     */
    public boolean selectModule(int index){
        if(!moduleFires(index)) return toggleModule(index);

        Weapon weapon = weaponModule(index);
        if(weapon == null) return false;

        //the same module again: the pilot lets every weapon module fire again
        if(selectedModule != null && sameModule(selectedModule, weapon)){
            releaseSelection();
            return false;
        }

        //a fresh pick silences the rest of the loadout and arms every copy of this module
        for(WeaponMount other : mounts){
            if(!other.weapon.isTurretMount()) continue;
            if(sameModule(other.weapon, weapon)){
                disarmedModules.remove(other.weapon);
            }else{
                disarmedModules.add(other.weapon);
            }
        }
        selectedModule = weapon;
        return true;
    }

    /** Drops the pilot selection and lets every weapon module fire again. */
    public void releaseSelection(){
        selectedModule = null;
        disarmedModules.clear();
    }

    /**
     * Acts on the module at this position the way the bottom bar and the number keys do: a
     * <b>weapon</b> module follows the selection rule of {@link #selectModule} - the pilot picks
     * the one that fires alone - while a <b>support</b> module is switched on or off, keeping it
     * installed: its effect stops and starts while slot, anchor, category and dorsal budget stay
     * reserved.
     *
     * @return true when the module is now armed and firing (weapon) or running (support).
     */
    public boolean toggleModule(int index){
        if(moduleFires(index)) return selectModule(index);

        SupportMount mount = supportModule(index);
        if(mount == null) return false;
        mount.setActive(self(), !mount.active);
        return mount.active;
    }

    /**
     * @return whether the module at this position aims by itself.
     *
     * <p>Weapon modules default to manual - they fire where the pilot aims. Support modules
     * default to automatic, the liquid jets ({@code wave}, {@code tsunami}) included, because
     * each one reproduces a block that acts on its own; manual aim is opt-in from the loadout
     * dialog.</p>
     */
    public boolean moduleAuto(int index){
        Weapon weapon = weaponModule(index);
        if(weapon != null) return autoAimModules.contains(weapon);

        SupportMount mount = supportModule(index);
        return mount != null && mount.autoTarget;
    }

    /**
     * Switches the module at this position between automatic targeting (it picks its own
     * target inside its own range and filters) and manual targeting (it fires where the pilot
     * aims). Every mounted copy of the same module kind switches with it. Support modules only
     * change their aim, never their on/off state.
     *
     * @return true when the mode actually changed.
     */
    public boolean setModuleAuto(int index, boolean auto){
        Weapon weapon = weaponModule(index);
        if(weapon != null) return setWeaponAimsSelf(weapon, auto);

        SupportMount mount = supportModule(index);
        if(mount == null) return false;
        if(mount.autoTarget == auto) return false;
        //the effect follows the flag: the liquid jet reads its own copy
        mount.setAutoTarget(auto);
        return true;
    }

    /**
     * @return whether this mount aims by itself instead of following the pilot's aim point.
     * Read by {@link Weapon#update} to decide who picks the target.
     */
    public boolean mountSelfTargets(WeaponMount mount){
        return autoAimModules.contains(mount.weapon);
    }

    /**
     * @return whether two mounted weapons are the same module kind: two copies of the same
     * turret block, or two copies of the same hull weapon taken from the same unit type.
     *
     * <p>The loadout menu lists identical modules as a single row ("Duo ×2"), so a choice made
     * on that row means the same thing for every copy of it. This is what makes the aim mode
     * one decision for the whole group instead of a different state per physical mount; native
     * hull weapons are never grouped, because each one is its own hardpoint.</p>
     */
    public static boolean sameModule(Weapon a, Weapon b){
        if(a == b) return true;
        if(a == null || b == null || !a.removable || !b.removable) return false;
        if(a instanceof TurretWeapon ta && b instanceof TurretWeapon tb) return ta.turret == tb.turret;
        return a.dorsalSourceWeapon != null && a.dorsalSourceWeapon == b.dorsalSourceWeapon
            && a.dorsalSource == b.dorsalSource;
    }

    /** @return the first weapon mount carrying this weapon, or null when it is not mounted. */
    public @Nullable WeaponMount mountOf(Weapon weapon){
        if(weapon == null) return null;
        for(WeaponMount mount : mounts){
            if(mount.weapon == weapon) return mount;
        }
        return null;
    }

    /** @return the first weapon mount carrying a weapon of this module kind, or null. */
    public @Nullable WeaponMount representativeMountOf(Weapon weapon){
        if(weapon == null) return null;
        for(WeaponMount mount : mounts){
            if(sameModule(mount.weapon, weapon)) return mount;
        }
        return null;
    }

    /** @return every mounted weapon of the same kind as this one, including itself. */
    public Seq<Weapon> sameModules(Weapon weapon){
        Seq<Weapon> matches = new Seq<>(Weapon.class);
        if(weapon == null) return matches;
        for(WeaponMount mount : mounts){
            if(sameModule(mount.weapon, weapon)) matches.add(mount.weapon);
        }
        return matches;
    }

    /** @return whether this weapon aims by itself (automatic targeting). */
    public boolean weaponAimsSelf(Weapon weapon){
        return autoAimModules.contains(weapon);
    }

    /**
     * Switches a module between automatic targeting and manual targeting, for <b>every</b>
     * mounted copy of it: identical weapons are one module as far as the pilot is concerned,
     * so the row in the loadout menu that says "Duo ×2" hands both over at once. Without this,
     * only the copy the menu happened to pick changed, and the rest kept aiming at the hand.
     *
     * @return whether any mounted weapon changed its aim mode.
     */
    public boolean setWeaponAimsSelf(Weapon weapon, boolean auto){
        if(weapon == null) return false;
        boolean any = false;
        for(Weapon same : sameModules(weapon)){
            any |= auto ? autoAimModules.add(same) : autoAimModules.remove(same);
        }
        return any;
    }

    /** @return whether this weapon is switched off: mounted, tracking, but never firing. */
    public boolean weaponDisarmed(Weapon weapon){
        return disarmedModules.contains(weapon);
    }

    /**
     * Silences one mount when its module is switched off, leaving the rotation order alone: a
     * disarmed module keeps tracking the aim, it just never fires. Called from {@link #update}
     * before the weapon runs, so nothing - a controller, a manual aim or an automatic target -
     * can put a shot out of a module the pilot switched off.
     *
     * <p>Switched off here means "not the one the pilot picked": while {@link #selectModule}
     * holds a selection, every other weapon module is in {@link #disarmedModules} and only the
     * selected one fires.</p>
     */
    public void applyDisarmed(WeaponMount mount){
        if(mount != null && disarmedModules.contains(mount.weapon)) mount.shoot = false;
    }

    //endregion

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

    /**
     * Drives the hull's own weapons, leaving {@link TurretWeapon} mounts alone.
     *
     * <p>Command controllers (repair, for instance) steer the hull's own weapon while they work;
     * a mounted dorsal turret must keep the target its own targeting picked, otherwise the
     * module is drawn on the hull and never fires.</p>
     */
    void controlNonTurretWeapons(boolean rotateShoot){
        controlNonTurretWeapons(rotateShoot, rotateShoot);
    }

    void controlNonTurretWeapons(boolean rotate, boolean shoot){
        for(WeaponMount mount : mounts){
            if(mount.weapon.controllable && !(mount.weapon instanceof TurretWeapon)){
                mount.rotate = rotate;
                mount.shoot = shoot;
            }
        }
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
                //a weapon that picks its own target keeps it: clearing it every frame would
                //throw away the target it just found, and it would re-search on the next one
                if(clearTarget && !mountSelfTargets(mount)) mount.target = null;
                //...and keeps its own aim point. The pilot's hand is the aiming input of a manual
                //module: an automatic one that inherits it aims at the mouse whenever it has no
                //target of its own, which is exactly what "auto-aim points at the cursor" is.
                if(!mountSelfTargets(mount)){
                    mount.aimX = x;
                    mount.aimY = y;
                }
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
        //the pools pay for the modules: ammo as shots happen and liquid while a liquid module
        //sprays; energy is never taken out of (see upkeepModuleSupply)
        upkeepModuleSupply();

        for(WeaponMount mount : mounts){
            //a module the pilot switched off keeps tracking the aim but never fires; it is
            //checked before the weapon update, so nothing can shoot behind the pilot's back
            applyDisarmed(mount);

            //a module out of ammo stays loaded but silent instead of firing for free
            if(!moduleHasAmmo(mount)) continue;

            int shots = mount.totalShots;
            mount.weapon.update(self(), mount);
            if(mount.totalShots > shots) spendAmmo(mount.weapon);
        }

        //support abilities are not part of UnitType.abilities, so they must be ticked here;
        //this is inlined into every generated unit's update() by the entity processor
        for(SupportMount mount : supports()){
            if(mount != null) mount.update(self());
        }

        //wall plates are not support modules either, but they are placed turret-side, so they are
        //ticked beside them with the same convention
        for(WallMount mount : walls()){
            if(mount != null) mount.update(self());
        }

        //tint the hull with the colour of the first active wall plate; Tmp.c2, never the
        //shared Color.white - aliasing it was mutating the global static every frame
        Color wallTint = Tmp.c2.set(Color.white);
        for(WallMount mount : walls()){
            if(mount != null && mount.active && mount.wallColor != null){
                wallTint.set(mount.wallColor);
                break;
            }
        }
        self().wallColor(wallTint);
    }
}
