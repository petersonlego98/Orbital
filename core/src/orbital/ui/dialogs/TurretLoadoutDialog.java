package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.TextureRegion;
import arc.func.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.content.Blocks;
import orbital.content.WeaponModules;
import orbital.ctype.*;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.world.*;
import orbital.world.blocks.defense.turrets.*;

import static orbital.Vars.*;

/**
 * Read/write turret loadout for one selected unit.
 *
 * <p>The dialog is an equipment screen in the familiar mould: a fixed strip of
 * <b>dorsal slots</b> at the top - the squares that mirror the hull's mount points - and
 * the scrolling catalogue below. A free slot shows a {@code +} square; clicking it arms it,
 * and the next module clicked in the catalogue lands in that slot. A module can also be
 * dragged straight onto a free slot, and a mounted module dragged off its square to be
 * removed. Native hardpoints are locked boxes that cannot be moved.</p>
 *
 * <p>The catalogue is split into collapsible category sections - tier for weapon modules,
 * footprint for turrets, effect for support modules - so a long list can be folded away.
 * Every card carries the same three lines - <b>name, role, footprint</b> - with the verbose
 * budget numbers in the tooltip, repeated mounts collapsed into one row with a count badge,
 * and the footprint color on the usual green/yellow/red scale.</p>
 */
public class TurretLoadoutDialog extends BaseDialog{
    private Table list;
    /** the scrolling catalogue pane, kept to restore its offset across rebuilds */
    private @Nullable ScrollPane pane;
    /** fixed slot rail on the right of the dialog; never scrolls */
    private Table slots;
    private @Nullable Unit unit;
    private Runnable changed = () -> {};
    /** dorsal slot the next catalogue pick goes into, -1 when none is armed */
    private int armedSlot = -1;
    /** card metrics, refreshed on every rebuild because they depend on the screen width */
    private float cardWidth, btnSize, imgSize, cardMinHeight, nameLineHeight, roleLineHeight;
    /** the slot boxes, in slot order, so a drag can highlight the one under the pointer */
    private final Seq<Table> slotBoxes = new Seq<>();
    /** slot highlighted by the drag in progress, -1 when none */
    private int hoverSlot = -1;

    public TurretLoadoutDialog(){
        super("@turret.loadout.title");
        cont.top();

        //the dialog is split in two columns: the scrolling catalogue on the left and the fixed
        //slot rail on the right, which fills the empty area beside the module grid
        Table columns = new Table();
        columns.left();
        cont.add(columns).grow().row();
        pane = columns.pane(t -> list = t).grow().scrollX(false).get();
        columns.add(slots = new Table()).growY().width(slotWidth()).top();

        buttons.button("@back", Icon.left, this::hide).size(210f, 64f);
        addCloseListener();
    }

    /** width of the slot column on the right, from the module grid's right edge to the menu edge */
    private static float slotWidth(){
        return mobile ? 340f : Mathf.clamp(Core.graphics.getWidth() * 0.34f, 380f, 620f);
    }

    public void show(Unit unit, Runnable changed){
        this.unit = unit;
        this.changed = changed;
        armedSlot = -1;
        rebuild();
        show();
        //the pane width is only known once it is laid out, so the grid is rebuilt with the
        //real width on the next frame - this keeps the rows aligned to the column edges
        Core.app.post(() -> {
            if(isShown()) rebuild();
        });
    }

    /**
     * Rebuilds both columns.
     *
     * <p>Rebuilding clears the catalogue, which would normally throw the reader back to the
     * top of the list every time a module is mounted, removed or switched. The scroll position
     * is therefore saved before and restored after, so adding a module never moves the page
     * under the player's cursor.</p>
     */
    private void rebuild(){
        float keep = scrollPosition();
        list.clearChildren();
        slots.clearChildren();
        slots.left().top();
        slots.defaults().left().pad(4f);
        list.top().left().margin(12f);
        slotBoxes.clear();
        hoverSlot = -1;

        if(unit == null) return;

        rebuildSlots();

        UnitType type = unit.type;

        list.add(type.localizedName).style(Styles.outlineLabel).left().padBottom(4f).row();
        list.add(Core.bundle.format("turret.loadout.budget",
            (int)unit.loadoutCost(), (int)type.dorsalBudget(),
            unit.mountedTurrets(), type.turretSlots.size))
            .color(Color.lightGray).left().padBottom(6f).row();
        list.image().color(Pal.accent).height(3f).growX().padBottom(8f).row();

        list.add("@turret.loadout.installed").style(Styles.outlineLabel).left().padBottom(4f).row();
        rebuildInstalled();

        list.image().color(Pal.accent).height(3f).growX().padBottom(8f).row();
        //what the unit already carries, and the rule that follows from it - the catalogue below
        //only offers turrets that run on that ammo
        list.label(() -> Core.bundle.format("turret.loadout.ammo", ammoText(unit))).color(Color.lightGray)
            .left().wrap().padBottom(2f).row();
        list.add("@turret.loadout.ammo.rule").color(Color.lightGray).left().wrap().padBottom(6f).row();
        heading("@turret.loadout.available", turretKeys());
        rebuildTurretGrid();

        list.image().color(Pal.accent).height(3f).growX().padTop(12f).padBottom(8f).row();
        heading("@weapon.module.available", weaponKeys());
        list.add("@weapon.module.hint").color(Color.lightGray).left().wrap().padBottom(6f).row();
        rebuildWeaponGrid();

        list.image().color(Pal.accent).height(3f).growX().padTop(12f).padBottom(8f).row();
        list.add("@support.loadout.installed").style(Styles.outlineLabel).left().padBottom(4f).row();
        //the HUD can switch modules off without reopening this dialog, so read the live state
        list.label(() -> Core.bundle.get(supportStateKey())).color(Color.lightGray).left().wrap().padBottom(6f).row();
        rebuildSupportInstalled();

        //A hull that already has an equivalent ability owns that effect permanently. Show it as
        //a fixed module instead of letting the player install a second, conflicting copy.
        if(hasNativeSupport(unit.type)){
            list.add("@support.loadout.native").style(Styles.outlineLabel).left().padTop(10f).padBottom(4f).row();

            for(SupportModuleCategory category : SupportModuleCategory.values()){
                if(!unit.type.hasNativeSupport(category)) continue;

                list.table(Styles.grayPanel, row -> {
                    row.left().margin(6f);
                    row.image(nativeIcon(category).uiIcon).size(40f).padRight(10f).scaling(Scaling.fit);
                    row.add(Core.bundle.get("support.category." + category.name())).left().growX().ellipsis(false).wrap();
                    row.add("@support.loadout.fixed").color(Color.lightGray).padLeft(8f).padRight(8f);
                    row.image(Icon.lockSmall).size(24f).padLeft(8f).padRight(12f).get().setColor(Pal.gray);
                }).growX().pad(3f).row();
            }

            list.add("@support.loadout.native.locked").color(Color.lightGray).left().padBottom(4f).row();
        }

        //the catalogue comes last, under its own heading and rule text
        list.image().color(Pal.accent).height(3f).growX().padTop(12f).padBottom(8f).row();
        heading("@support.loadout.available", supportKeys());
        list.add("@support.loadout.limit").color(Color.lightGray).left().wrap().padBottom(6f).row();
        rebuildSupportGrid();

        //restoring the offset keeps a mount or a section toggle from moving the page
        restoreScroll(keep);
    }

    /**
     * Puts the catalogue back where it was. The value is applied immediately and re-clamped
     * on the next frame, because the pane is only laid out after the rebuild returns and the
     * content may have grown (new module) or shrunk (removed one).
     */
    private void restoreScroll(float offset){
        if(pane == null) return;
        pane.setScrollYForce(offset);
        float applied = offset;
        Core.app.post(() -> {
            if(pane != null && isShown()) pane.setScrollYForce(Mathf.clamp(applied, 0f, Math.max(0f, pane.getMaxY())));
        });
    }

    /** @return the catalogue's scroll offset, saved while the list is rebuilt. */
    private float scrollPosition(){
        return pane == null ? 0f : pane.getScrollY();
    }

    /**
     * The fixed slot strip: dorsal turret slots, the hull's native hardpoints, and the
     * support anchors. It mirrors what is mounted on the unit and is the drop target for
     * every drag coming from the catalogue below.
     */
    private void rebuildSlots(){
        float boxSize = mobile ? 104f : 126f;
        float slotImg = mobile ? 62f : 74f;
        int perRow = boxesPerRow();

        //one left offset for every element of the rail: headings, box rows and the hint line
        slots.add(Core.bundle.get("weapon.module.slots")).style(Styles.outlineLabel).left().row();

        //native hardpoints belong to the hull: shown as locked boxes, never removable
        Seq<Installed> fixed = new Seq<>(Installed.class);
        for(WeaponMount mount : unit.mounts){
            if(mount.weapon == null || mount.weapon.removable) continue;
            fixed.add(new Installed(weaponName(mount.weapon), mount.weapon));
        }

        if(fixed.any()){
            slots.label(() -> Core.bundle.get("weapon.module.slots.fixed")).color(Color.lightGray).left().row();

            Table fixedRow = new Table();
            fixedRow.left();
            slots.add(fixedRow).growX().row();

            int count = 0;
            for(Installed row : fixed){
                count++;
                slotBox(fixedRow, new TextureRegionDrawable(row.weapon.region != null && row.weapon.region.found()
                    ? row.weapon.region : unit.type.uiIcon), Color.white, row.name, boxSize, slotImg, null, null);
                if(count % perRow == 0) fixedRow.row();
            }
        }

        //dorsal slots: the + square is both the arm-slot and the drop target
        slots.add(Core.bundle.format("weapon.module.slot.count", unit.type.turretSlots.size))
            .color(Color.lightGray).left().row();

        Table slotRow = new Table();
        slotRow.left();
        slots.add(slotRow).growX().row();

        for(int i = 0; i < unit.type.turretSlots.size; i++){
            int index = i;
            boolean armed = armedSlot == index;
            TurretSlot slot = unit.type.turretSlots.get(index);
            Weapon weapon = mountedAt(index);
            String label = weapon == null
                ? Core.bundle.format("weapon.module.slot.max", slot.maxSize, slot.maxSize)
                : weaponName(weapon);
            Drawable icon = weapon == null ? Icon.add : weaponIcon(weapon);

            //the drop target is the box itself, so the index travels with the actor
            Table box = slotBox(slotRow, icon, weapon == null ? Pal.accent : Color.white, label,
                boxSize, slotImg, () -> {
                    if(weapon != null){
                        //dropping a module off its square also removes it; so does a click
                        if(unit.removeWeapon(weapon)){
                            if(armedSlot == index) armedSlot = -1;
                            changed.run();
                            rebuild();
                        }
                    }else{
                        armedSlot = armed ? -1 : index;
                        rebuild();
                    }
                }, weapon == null ? null : new SlotDrag(weapon));

            box.setBackground(armed || hoverSlot == index ? tinted(Tex.pane, Pal.accent) : Tex.pane);
            box.userObject = new SlotTag(index);
            slotBoxes.add(box);

            if((index + 1) % perRow == 0 && index != unit.type.turretSlots.size - 1) slotRow.row();
        }

        //support anchors: one box per effect the hull can host
        if(unit.type.supportAnchors.size > 0){
            slots.label(() -> Core.bundle.get("weapon.module.slot.support")).color(Color.lightGray).left().row();

            Table supportRow = new Table();
            supportRow.left();
            slots.add(supportRow).growX().row();

            int count = 0;
            for(SupportAnchor anchor : unit.type.supportAnchors){
                count++;
                SupportMount mounted = mountedSupport(anchor.category);
                //a built-in effect occupies its anchor for good: shown locked, like any other
                //fixed module, instead of pretending the slot is free
                boolean natived = unit.type.hasNativeSupport(anchor.category);

                Drawable icon;
                Color color;
                String name;
                Runnable click = null;

                if(mounted != null){
                    icon = new TextureRegionDrawable(mounted.block.uiIcon);
                    color = Color.white;
                    name = mounted.block.localizedName;
                    click = () -> {
                        unit.removeSupportMount(mounted);
                        changed.run();
                        rebuild();
                    };
                }else if(natived){
                    icon = new TextureRegionDrawable(nativeIcon(anchor.category).uiIcon);
                    color = Color.white;
                    name = Core.bundle.get("support.category." + anchor.category.name());
                }else{
                    icon = Icon.add;
                    color = Pal.accent;
                    name = Core.bundle.get("support.category." + anchor.category.name());
                }

                slotBox(supportRow, icon, color, name, boxSize, slotImg, click, null);
                if(count % perRow == 0) supportRow.row();
            }
        }

        //a single line that says what the rail is waiting for
        String hint = armedSlot >= 0
            ? Core.bundle.format("weapon.module.slot.armed", armedSlot + 1)
            : Core.bundle.get("weapon.module.slot.hint");
        slots.add(hint).width(slotWidth() - 12f).color(armedSlot >= 0 ? Pal.accent : Color.lightGray)
            .left().wrap().fontScale(0.9f).row();
    }

    /**
     * One square of the slot rail: an icon with a caption below it, sized and aligned the same
     * way in every row so the rail never looks ragged.
     */
    private Table slotBox(Table row, Drawable icon, Color iconColor, String label, float boxSize,
        float slotImg, @Nullable Runnable click, @Nullable EventListener drag){
        Table box = row.table(Tex.pane, cell -> {
            cell.margin(4f).top();
            //a fixed cell height for the icon keeps the captions on the same baseline everywhere,
            //whatever the aspect ratio of the block sprite
            cell.image(icon).size(slotImg).scaling(Scaling.fit).color(iconColor);
            cell.row();
            cell.add(label).width(boxSize - 12f).wrap().center().fontScale(mobile ? 1f : 0.85f).color(Color.lightGray);
        }).size(boxSize).pad(3f).get();

        if(click != null){
            box.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    click.run();
                }
            });
        }

        if(drag != null) box.addListener(drag);
        return box;
    }

    /**
     * Mounts a module picked from the catalogue.
     *
     * <p>When a slot is armed the pick goes there; otherwise the unit places it in the first
     * compatible slot, which is what the slot strip already shows.</p>
     */
    private void place(Boolf<Integer> at, Prov<Boolean> anyFree){
        if(armedSlot >= 0){
            if(at.get(armedSlot)){
                armedSlot = -1;
                changed.run();
                rebuild();
            }
            return;
        }

        if(anyFree.get()){
            changed.run();
            rebuild();
        }
    }

    /**
     * @return a tinted copy of a drawable, or the original when it cannot be tinted.
     *
     * <p>{@code Drawable} has no {@code tint} method: {@link NinePatchDrawable} and
     * {@link TextureRegionDrawable} each provide their own. {@code Tex.pane} is a scaled nine
     * patch, so casting it to {@code TextureRegionDrawable} - as an earlier version of this
     * dialog did - crashed the game the moment a slot was highlighted.</p>
     */
    public static Drawable tinted(Drawable drawable, Color color){
        if(drawable instanceof NinePatchDrawable patch) return patch.tint(color);
        if(drawable instanceof TextureRegionDrawable region) return region.tint(color);
        return drawable;
    }

    /**
     * how many slot boxes fit on one row of the rail.
     *
     * <p>Computed from the rail width instead of a fixed number, so a wider rail shows more
     * boxes and phones show as many as their 340 unit column allows (3) instead of two.</p>
     */
    private int boxesPerRow(){
        float cell = (mobile ? 104f : 126f) + 6f;
        return Mathf.clamp((int)(slotWidth() / cell), 2, 8);
    }

    /** @return the mounted weapon occupying a dorsal slot, or null when it is free. */
    private @Nullable Weapon mountedAt(int slotIndex){
        for(WeaponMount mount : unit.mounts){
            if(mount.weapon.removable && mount.weapon.slotIndex == slotIndex) return mount.weapon;
        }
        return null;
    }

    /** @return the support module installed on the anchor of a category, or null. */
    private @Nullable SupportMount mountedSupport(SupportModuleCategory category){
        if(unit.supportMounts == null) return null;
        for(SupportMount mount : unit.supportMounts){
            if(mount != null && mount.block != null && mount.category == category) return mount;
        }
        return null;
    }

    /**
     * Installed mounts, one row per kind of weapon.
     *
     * <p>Every weapon the hull carries is listed, not just the dorsal modules: a native
     * hardpoint is part of the unit, and this is the only place that shows it. Repeated
     * hardpoints collapse into one row with a {@code xN} badge.</p>
     */
    private void rebuildInstalled(){
        ObjectMap<String, Installed> grouped = new ObjectMap<>();
        for(WeaponMount mount : unit.mounts){
            Weapon weapon = mount.weapon;
            if(weapon == null) continue;

            String name = weaponName(weapon);
            Installed row = grouped.get(name);
            if(row == null) grouped.put(name, row = new Installed(name, weapon));
            row.count++;
        }

        Seq<Installed> rows = new Seq<>(Installed.class);
        for(Installed row : grouped.values()) rows.add(row);
        if(rows.isEmpty()){
            list.add("@turret.loadout.none").color(Color.lightGray).left().padBottom(4f).row();
            return;
        }

        //removable modules are the actionable ones: they come first, then the locked hulls
        rows.sort((a, b) -> a.weapon.removable != b.weapon.removable
            ? (a.weapon.removable ? -1 : 1)
            : a.name.compareTo(b.name));

        for(Installed row : rows){
            Weapon weapon = row.weapon;
            boolean removable = weapon.removable;

            list.table(Styles.grayPanel, cell -> {
                cell.left().margin(6f);
                cell.image(weaponIcon(row.weapon)).size(40f).padRight(10f).scaling(Scaling.fit);
                cell.add(row.name).left().growX().ellipsis(false).wrap();
                if(row.count > 1){
                    cell.add(Core.bundle.format("weapon.module.count", row.count))
                        .color(Pal.accent).padLeft(8f).padRight(8f);
                }
                cell.add(removable ? "@turret.hud.removable" : "@turret.hud.native")
                    .color(Color.lightGray).padLeft(8f).padRight(8f);

                if(removable){
                    cell.button(Icon.cancel, Styles.clearNonei, () -> {
                        unit.removeWeapon(weapon);
                        changed.run();
                        rebuild();
                    }).size(48f).padLeft(4f).tooltip(Core.bundle.get("turret.loadout.remove"))
                        .get().getStyle().imageUpColor = Pal.remove;
                }else{
                    cell.image(Icon.lockSmall).size(24f).padLeft(8f).padRight(12f).get().setColor(Pal.gray);
                }
            }).growX().pad(3f).row();
        }
    }

    /**
     * @return the names of the ammo the unit's turrets carry, or the "free" placeholder when
     * nothing mounted yet defines one item - in that state the first turret may pick any ammo.
     */
    private static String ammoText(Unit unit){
        Item[] ammo = unit.loadoutAmmo();
        if(ammo.length == 0) return text("@turret.loadout.ammo.free");

        StringBuilder names = new StringBuilder();
        for(Item item : ammo){
            if(names.length() > 0) names.append(", ");
            names.append(item.localizedName);
        }
        return names.toString();
    }

    /** Turret blocks, in one collapsible section per footprint. */
    private void rebuildTurretGrid(){
        cardMetrics();

        Seq<Turret> turrets = new Seq<>(Turret.class);
        for(Block block : content.blocks()){
            if(!(block instanceof Turret turret) || block.isHidden() || !TurretWeapon.canAdapt(turret)) continue;
            //the hull decides what this catalogue may offer, not the current loadout: a turret
            //that stopped fitting stays listed - locked - instead of vanishing and moving the page
            if(!unit.type.hasSlotFor(turret.size) || !unit.type.fitsSilhouette(turret.size)) continue;
            //ammo is the one rule that filters entries out instead of locking them: the loadout
            //shares a single supply, so an incompatible turret would strand part of it
            if(!unit.ammoCompatible(turret)) continue;
            turrets.add(turret);
        }

        if(turrets.isEmpty()){
            lockedLine("@turret.loadout.noavailable");
            return;
        }
        turrets.sort((a, b) -> a.size != b.size ? a.size - b.size : a.localizedName.compareTo(b.localizedName));

        for(Section<Turret> section : sections(turrets, t -> "turret-" + t.size,
            t -> Core.bundle.format("turret.loadout.footprintgroup", t.size, t.size))){

            section(section, () -> {
                Table grid = newGrid();
                int count = 0;
                for(Turret turret : section.items){
                    count++;
                    boolean fits = unit.canMount(turret);
                    moduleCard(grid, new TextureRegionDrawable(turret.uiIcon), turret.localizedName, null, turret.size, budget(turret.size),
                        () -> place(slot -> unit.mountTurret(turret, slot) != null,
                            () -> unit.mountTurret(turret) != null),
                        slot -> unit.mountTurret(turret, slot) != null,
                        fits ? null : reasonNoRoom());
                    if(count % columns() == 0) grid.row();
                }
            });
        }
    }

    /**
     * Weapon modules, in one collapsible section per research tier.
     *
     * <p>Tier is the grouping players expect from research/progression UIs: the same hull
     * line stays together and cheaper modules come before the expensive capitals.</p>
     */
    private void rebuildWeaponGrid(){
        cardMetrics();

        Seq<WeaponModules.Definition> modules = new Seq<>(WeaponModules.Definition.class);
        for(WeaponModules.Definition module : WeaponModules.all()){
            if(module == null || module.weapon == null) continue;
            //see rebuildTurretGrid: hull compatibility only, the lock marks what has no room now
            if(!unit.type.hasSlotFor(module.size) || !unit.type.fitsSilhouette(module.size)) continue;
            modules.add(module);
        }

        if(modules.isEmpty()){
            lockedLine("@weapon.module.noavailable");
            return;
        }

        modules.sort((a, b) -> a.tier != b.tier
            ? a.tier - b.tier
            : a.ship.compareTo(b.ship) != 0
                ? a.ship.compareTo(b.ship)
                : a.shot.compareTo(b.shot));

        for(Section<WeaponModules.Definition> section : sections(modules, m -> "weapon-tier-" + m.tier,
            m -> m.tier > 0
                ? Core.bundle.format("weapon.module.tier", m.tier)
                : Core.bundle.get("weapon.module.notier"))){

            section(section, () -> {
                Table grid = newGrid();
                int inRow = 0;
                for(WeaponModules.Definition module : section.items){
                    boolean fits = unit.canMountWeaponModule(module);
                    //a weapon sprite missing from the atlas shows a generic module glyph, not the hull
                    Drawable icon = module.weapon.region != null && module.weapon.region.found()
                        ? new TextureRegionDrawable(module.weapon.region) : Icon.turret;

                    moduleCard(grid, icon, module.ship, module.shot, module.size, module.cost,
                        () -> place(slot -> unit.mountWeaponModule(module, slot) != null,
                            () -> unit.mountWeaponModule(module) != null),
                        slot -> unit.mountWeaponModule(module, slot) != null,
                        fits ? null : reasonNoRoom());
                    inRow++;
                    if(inRow % columns() == 0) grid.row();
                }
            });
        }
    }

    /** Support modules, in one collapsible section per effect. */
    private void rebuildSupportGrid(){
        cardMetrics();

        Seq<Block> supports = new Seq<>(Block.class);
        for(Block block : content.blocks()){
            if(block == null || block.isHidden() || !SupportModuleCategory.isSupport(block)) continue;
            if(!unit.type.hasAnchorFor(SupportModuleCategory.of(block), block.size)) continue;
            supports.add(block);
        }

        if(supports.isEmpty()){
            lockedLine("@support.loadout.noavailable");
            return;
        }

        supports.sort((a, b) -> {
            int ca = SupportModuleCategory.of(a).ordinal(), cb = SupportModuleCategory.of(b).ordinal();
            return ca != cb ? ca - cb : a.size != b.size ? a.size - b.size : a.localizedName.compareTo(b.localizedName);
        });

        for(Section<Block> section : sections(supports, b -> "support-" + SupportModuleCategory.of(b).name(),
            b -> Core.bundle.get("support.category." + SupportModuleCategory.of(b).name()))){

            section(section, () -> {
                Table grid = newGrid();
                int count = 0;
                for(Block block : section.items){
                    count++;
                    boolean fits = unit.canMountSupport(block);
                    moduleCard(grid, new TextureRegionDrawable(block.uiIcon), block.localizedName,
                        Core.bundle.get("support.category." + SupportModuleCategory.of(block).name()),
                        block.size, budget(block.size), () -> {
                            if(unit.mountSupport(block) != null){
                                changed.run();
                                rebuild();
                            }
                        }, null, fits ? null : reasonNoSupport(block));
                    if(count % columns() == 0) grid.row();
                }
            });
        }
    }

    /** Installed support modules and their live on/off state. */
    private void rebuildSupportInstalled(){
        int supportCount = 0;
        for(SupportMount support : unit.supportMounts){
            if(support == null || support.block == null) continue;
            supportCount++;

            list.table(Styles.grayPanel, cell -> {
                cell.left().margin(6f);
                cell.image(support.block.uiIcon).size(40f).padRight(10f).scaling(Scaling.fit);
                cell.add(support.block.localizedName).left().growX().ellipsis(false).wrap();
                cell.add(Core.bundle.get("support.category." + support.category.name()))
                    .color(Color.lightGray).padLeft(8f).padRight(8f);
                Label state = cell.label(() -> Core.bundle.get(support.active
                    ? "support.hud.active" : "support.hud.inactive"))
                    .padLeft(8f).padRight(8f).get();
                state.update(() -> state.setColor(support.active ? Pal.heal : Pal.remove));
                //switching the effect off keeps the module installed - the same toggle as the HUD
                cell.button(Icon.power, Styles.clearNonei, () -> {
                    support.setActive(unit, !support.active);
                    changed.run();
                    rebuild();
                }).size(48f).padLeft(4f).tooltip(Core.bundle.get("support.hud.toggle"))
                    .get().getStyle().imageUpColor = support.active ? Pal.heal : Pal.remove;
                cell.button(Icon.cancel, Styles.clearNonei, () -> {
                    unit.removeSupportMount(support);
                    changed.run();
                    rebuild();
                }).size(48f).padLeft(4f).tooltip(Core.bundle.get("support.loadout.remove"))
                    .get().getStyle().imageUpColor = Pal.remove;
            }).growX().pad(3f).row();
        }

        if(supportCount == 0){
            list.add("@support.loadout.none").color(Color.lightGray).left().padBottom(4f).row();
        }
    }

    /** live state line under the support heading */
    private String supportStateKey(){
        boolean hasSupport = false, anyActive = false;
        if(unit.supportMounts != null){
            for(SupportMount support : unit.supportMounts){
                if(support == null || support.block == null) continue;
                hasSupport = true;
                if(support.active) anyActive = true;
            }
        }
        return hasSupport && !anyActive ? "support.loadout.inactive" : "support.loadout.active";
    }

    private static boolean hasNativeSupport(UnitType type){
        for(SupportModuleCategory category : SupportModuleCategory.values()){
            if(type.hasNativeSupport(category)) return true;
        }
        return false;
    }

    /**
     * Catalogue heading plus the shortcut that folds or unfolds every section under it,
     * so a catalogue of five tiers does not have to be folded section by section.
     */
    /**
     * Line for a catalogue with nothing to offer, with the padlock that says "no module fits
     * this hull right now" - the reason the section is empty, and not a missing catalogue.
     */
    private void lockedLine(String key){
        list.table(row -> {
            row.left();
            row.image(Icon.lockSmall).size(26f).padRight(8f).get().setColor(Pal.gray);
            row.add(text(key)).color(Color.lightGray).left().wrap();
        }).left().padBottom(4f).row();
    }

    private void heading(String key, Seq<String> sectionKeys){
        //the lambda below runs later, so the state has to be captured as a plain value
        boolean allCollapsed = true;
        for(String section : sectionKeys){
            if(!collapsed(section)){
                allCollapsed = false;
                break;
            }
        }
        boolean collapse = !allCollapsed;

        list.table(header -> {
            header.left();
            //a Label resolves the arc "@key" prefix on its own, but Core.bundle.get does not
            header.add(text(key)).style(Styles.outlineLabel).left().growX();
            header.button(collapse ? "@weapon.module.collapseall" : "@weapon.module.expandall",
                Styles.cleart, () -> {
                    for(String section : sectionKeys) setCollapsed(section, collapse);
                    rebuild();
                });
        }).growX().padBottom(4f).row();
    }

    /**
     * A collapsible section: the header carries the title and the item count, and clicking
     * it shows or hides the grid below. The eye icon is the usual show/hide affordance.
     */
    private void section(Section<?> section, Runnable grid){
        boolean hidden = collapsed(section.key);
        list.button(Core.bundle.format("weapon.module.header", section.title, section.items.size),
                hidden ? Icon.eyeOff : Icon.eye, Styles.grayt, () -> {
                    setCollapsed(section.key, !hidden);
                    rebuild();
                })
            .growX().pad(3f).padBottom(2f).row();

        if(!hidden) grid.run();
    }

    /** @return whether the section is folded away, remembered per session and between sessions. */
    private static boolean collapsed(String section){
        return Core.settings.getBool(settingKey(section), false);
    }

    private static void setCollapsed(String section, boolean value){
        Core.settings.put(settingKey(section), value);
    }

    private static String settingKey(String section){
        return "turret.loadout.collapsed." + section;
    }

    /** every turret section key: one per footprint */
    private static Seq<String> turretKeys(){
        Seq<String> keys = new Seq<>(String.class);
        for(int size = 1; size <= 5; size++) keys.add("turret-" + size);
        return keys;
    }

    /** every weapon-module section key: one per tier, plus the hulls without one */
    private static Seq<String> weaponKeys(){
        Seq<String> keys = new Seq<>(String.class);
        for(int tier = 0; tier <= 5; tier++) keys.add("weapon-tier-" + tier);
        return keys;
    }

    /** every support-module section key: one per effect */
    private static Seq<String> supportKeys(){
        Seq<String> keys = new Seq<>(String.class);
        for(SupportModuleCategory category : SupportModuleCategory.values()) keys.add("support-" + category.name());
        return keys;
    }

    /** recomputes the card metrics for the current screen width */
    private void cardMetrics(){
        if(mobile){
            //A module card is a reading surface, so on phones it keeps a fixed physical size
            //instead of shrinking with the UI scale: at the 125% default it is 118 units wide,
            //and at 100% it grows in units so the card on the screen stays just as large.
            float unit = 1f / Scl.scl(1f);
            cardWidth = 295f * unit;
            btnSize = 175f * unit;
            imgSize = 128f * unit;
            //two text lines under the icon, so one- and two-line labels still line up
            nameLineHeight = 30f * unit;
            roleLineHeight = 26f * unit;
            cardMinHeight = 144f * unit;
        }else{
            cardWidth = 168f;
            btnSize = 98f;
            imgSize = 68f;
            nameLineHeight = 40f;
            roleLineHeight = 32f;
            cardMinHeight = 186f;
        }
    }

    /** columns that fit the catalogue pane, not the whole screen: the slot rail takes the rest */
    private int columns(){
        float width;
        if(mobile){
            //list.getWidth() under-reports the space the pane actually offers (margins and the
            //scroll bar), which on phones cost a whole column. The available width is therefore
            //computed from the screen minus the rail, so seven modules fit per row.
            width = Core.graphics.getWidth() - slotWidth() * Scl.scl(1f) - Scl.scl(48f);
            return Mathf.clamp((int)(width / Scl.scl(cardWidth + 4f)), 3, 7);
        }
        //the pane is laid out by the frame it is drawn, so its width is only known the second time
        width = list.getWidth() > 1f
            ? list.getWidth() - Scl.scl(24f)
            : Core.graphics.getWidth() - slotWidth() - Scl.scl(60f);
        return Mathf.clamp((int)(width / Scl.scl(cardWidth + 18f)), 3, 8);
    }

    /** a new left-aligned grid attached to the scrolling list */
    private Table newGrid(){
        Table grid = new Table();
        grid.left();
        list.add(grid).growX().row();
        return grid;
    }

    /** Dorsal budget consumed by a footprint, in px². */
    private static float budget(int size){
        return size * TurretSlot.pxTile;
    }

    /**
     * @return the sprite of a weapon: the turret block icon for a turret module, the weapon's
     * own sprite otherwise. A weapon sprite that the atlas does not carry falls back to a
     * generic module glyph, never to the hull's icon - the hull would read as "this module
     * turns my unit into a ship", which it does not.
     */
    private static Drawable weaponIcon(@Nullable Weapon weapon){
        if(weapon instanceof TurretWeapon turret) return new TextureRegionDrawable(turret.turret.uiIcon);
        return weapon != null && weapon.region != null && weapon.region.found()
            ? new TextureRegionDrawable(weapon.region) : Icon.turret;
    }

    /** One collapsible category of a catalogue. */
    private static final class Section<T>{
        final String key, title;
        final Seq<T> items = new Seq<>();

        Section(String key, String title){
            this.key = key;
            this.title = title;
        }
    }

    /**
     * Groups items into ordered sections, one per distinct key, preserving arrival order so
     * sorting stays with the caller's comparator. The caller supplies the key (which
     * sections exist) and the localized title.
     */
    private static <T> Seq<Section<T>> sections(Seq<T> items, Func<T, String> key, Func<T, String> title){
        Seq<Section<T>> sections = new Seq<>(Section.class);
        for(T item : items){
            String k = key.get(item);
            Section<T> section = sections.find(s -> s.key.equals(k));
            if(section == null) sections.add(section = new Section<>(k, title.get(item)));
            section.items.add(item);
        }
        return sections;
    }

    /**
     * @return the bundle text of a key written with or without the arc {@code @} prefix.
     * Widgets such as {@code Label} strip the prefix themselves; reading it manually does not.
     */
    private static String text(String key){
        return Core.bundle.get(key.startsWith("@") ? key.substring(1) : key);
    }

    /**
     * One card of the module grids.
     *
     * <p>Every card has the same three lines - <b>name</b>, <b>role</b> (the shot type or the
     * support category) and the <b>footprint</b> badge - and the whole square answers to a
     * click, with the sprite drawn as large as the card allows. The dorsal budget in px², a
     * number too large to read at a glance, lives in the tooltip instead. Cards that occupy a
     * dorsal slot are drag sources: {@code placeAt} drops the module into the slot it is
     * released over, while a click mounts it in the armed or first free slot.
     *
     * @param mount click handler: mounts in the armed slot, or in the first free one
     * @param placeAt drop handler for one specific dorsal slot, or null for support modules
     * @param locked reason the module does not fit right now, or null when it does. A locked
     *     card keeps its place in the grid - so mounting never rearranges the catalogue under
     *     the player's cursor - and shows a padlock with the reason instead of mounting.
     */
    private void moduleCard(Table grid, Drawable icon, String name, String role, int size, float cost,
        Runnable mount, @Nullable Boolf<Integer> placeAt, @Nullable String locked){
        //a locked card is dimmed: same size, same place, no interaction
        Drawable background = locked == null ? Tex.pane : tinted(Tex.whiteui, Color.darkGray);
        Table card = grid.table(background, cell -> {
            cell.margin(6f).top();
            cell.image(icon).size(btnSize).scaling(Scaling.fit).padBottom(4f);
            cell.row();
            //the name and the role scale with the card: on phones the cards are narrow, and a
            //full-size label would break the module name in the middle of a word
            cell.add(name).width(cardWidth - 8f).height(nameLineHeight).wrap().center()
                .fontScale(mobile ? 0.72f : 1f).padTop(2f).row();
            //the role line carries the shot type or the support category, or the lock reason.
            //Both lines have an explicit width and height: a wrapped label without a width has
            //a preferred width of zero, so arc breaks the text after every glyph
            String line = locked != null ? locked : role;
            cell.add(line == null ? "" : line).width(cardWidth - 8f).height(roleLineHeight)
                .color(locked == null ? Color.lightGray : Pal.remove)
                .fontScale(mobile ? 0.72f : 0.85f).center().wrap().row();
            cell.add(Core.bundle.format("weapon.module.footprint", size, size))
                .color(sizeColor(size)).fontScale(mobile ? 0.7f : 0.85f).center();
        }).width(cardWidth).minHeight(cardMinHeight).pad(2f).tooltip(tooltip(name, role, size, cost))
            .get();

        //a locked card is inert: no click, no drag, no tooltip change
        if(locked != null){
            card.touchable = Touchable.disabled;
            return;
        }

        //clicking anywhere on the card mounts it, so there is no dead border around the icon
        card.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                mount.run();
            }
        });
        if(placeAt != null) card.addListener(new CardDrag(icon, size, placeAt));
    }

    /** @return why a dorsal module has no room: a full slot set, or an exhausted budget. */
    private String reasonNoRoom(){
        return unit.mountedTurrets() >= unit.type.turretSlots.size
            ? Core.bundle.get("weapon.module.locked.slot")
            : Core.bundle.get("weapon.module.locked.budget");
    }

    /** @return why a support module cannot be installed: its category is already taken. */
    private String reasonNoSupport(Block block){
        SupportModuleCategory category = SupportModuleCategory.of(block);
        return unit.hasSupportCategory(category)
            ? Core.bundle.get("weapon.module.locked.installed")
            : Core.bundle.get("weapon.module.locked.slot");
    }

    /**
     * Drags a module card over the slot strip: a ghost follows the pointer and dropping it
     * on a free dorsal slot mounts the module in that exact slot.
     */
    private class CardDrag extends DragListener{
        private final Drawable icon;
        private final int size;
        private final Boolf<Integer> place;
        private @Nullable Image ghost;

        CardDrag(Drawable icon, int size, Boolf<Integer> place){
            this.icon = icon;
            this.size = size;
            this.place = place;
        }

        /**
         * The pane's flick-scroll listener also watches this touch and cancels the touch focus
         * as soon as the pointer moves, which would abort the drag. Stopping the event here
         * keeps the drag in this listener's hands.
         */
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
            boolean handled = super.touchDown(event, x, y, pointer, button);
            if(handled) event.stop();
            return handled;
        }

        @Override
        public void dragStart(InputEvent event, float x, float y, int pointer){
            ghost = new Image(icon);
            float g = Scl.scl(size * 12f + 20f);
            ghost.setSize(g, g);
            ghost.setColor(1f, 1f, 1f, 0.75f);
            Core.scene.add(ghost);
            drag(event, x, y, pointer);
        }

        @Override
        public void drag(InputEvent event, float x, float y, int pointer){
            if(ghost == null) return;
            ghost.setPosition(event.stageX - ghost.getWidth() / 2f, event.stageY - ghost.getHeight() / 2f);

            //the slot under the pointer lights up, so it is clear where the module will land
            int slot = -1;
            SlotTag tag = tagAt(event.stageX, event.stageY);
            if(tag != null && mountedAt(tag.index) == null) slot = tag.index;

            if(slot != hoverSlot){
                hoverSlot = slot;
                paintSlot(slot);
            }
        }

        @Override
        public void dragStop(InputEvent event, float x, float y, int pointer){
            if(ghost != null){
                ghost.remove();
                ghost = null;
            }
            int slot = hoverSlot;
            hoverSlot = -1;
            paintSlot(-1);

            SlotTag tag = tagAt(event.stageX, event.stageY);
            //a release over a free slot mounts there; anything else cancels the drag
            if(slot >= 0 && place.get(slot)){
                if(armedSlot == slot) armedSlot = -1;
                changed.run();
                rebuild();
            }
        }
    }

    /**
     * Drags a module already mounted: the ghost follows the pointer and letting go away
     * from the slot strip removes the module.
     */
    private class SlotDrag extends DragListener{
        private final Weapon weapon;
        private @Nullable Image ghost;

        SlotDrag(Weapon weapon){
            this.weapon = weapon;
        }

        //see CardDrag#touchDown: the pane's flick scroll would otherwise cancel this drag
        @Override
        public boolean touchDown(InputEvent event, float x, float y, int pointer, int button){
            boolean handled = super.touchDown(event, x, y, pointer, button);
            if(handled) event.stop();
            return handled;
        }

        @Override
        public void dragStart(InputEvent event, float x, float y, int pointer){
            ghost = new Image(weaponIcon(weapon));
            ghost.setSize(Scl.scl(72f), Scl.scl(72f));
            ghost.setColor(1f, 1f, 1f, 0.75f);
            Core.scene.add(ghost);
            drag(event, x, y, pointer);
        }

        @Override
        public void drag(InputEvent event, float x, float y, int pointer){
            if(ghost == null) return;
            ghost.setPosition(event.stageX - ghost.getWidth() / 2f, event.stageY - ghost.getHeight() / 2f);
        }

        @Override
        public void dragStop(InputEvent event, float x, float y, int pointer){
            if(ghost != null){
                ghost.remove();
                ghost = null;
            }

            //released over a slot square: nothing to do, the module is already mounted
            if(tagAt(event.stageX, event.stageY) != null) return;

            if(unit.removeWeapon(weapon)){
                if(weapon.slotIndex == armedSlot) armedSlot = -1;
                changed.run();
                rebuild();
            }
        }
    }

    /** @return the dorsal slot under a stage position, if any. */
    private static @Nullable SlotTag tagAt(float stageX, float stageY){
        Element hit = Core.scene.hit(stageX, stageY, true);
        while(hit != null){
            if(hit.userObject instanceof SlotTag tag) return tag;
            hit = hit.parent;
        }
        return null;
    }

    /** Tints the slot highlighted by the drag in progress. */
    private void paintSlot(int index){
        for(int i = 0; i < slotBoxes.size; i++){
            Table box = slotBoxes.get(i);
            box.setBackground(i == index || i == armedSlot
                ? tinted(Tex.pane, Pal.accent)
                : Tex.pane);
        }
    }

    /** Marks a slot square so a drop can be resolved back to a slot index. */
    private static final class SlotTag{
        final int index;

        SlotTag(int index){
            this.index = index;
        }
    }

    /** Card tooltip: full name, role and the dorsal footprint. */
    private static String tooltip(String name, String role, int size, float cost){
        StringBuilder text = new StringBuilder(name);
        if(role != null) text.append('\n').append(role);
        if(cost > 0f) text.append('\n').append(Core.bundle.format("turret.loadout.size", size, size, (int)(cost * cost)));
        return text.toString();
    }

    /** Footprint colors on the usual green/yellow/red scale: 1x1, 2x2, larger. */
    private static Color sizeColor(int size){
        return size <= 1 ? Pal.heal : size == 2 ? Pal.accent : Pal.remove;
    }

    /** Representative block icon for a built-in support effect. */
    private static Block nativeIcon(SupportModuleCategory category){
        return switch(category){
            case repair -> Blocks.mender;
            case overdrive -> Blocks.overdriveProjector;
            case force -> Blocks.forceProjector;
        };
    }

    /** One row of the installed list: every mount that resolves to the same display name. */
    private static final class Installed{
        final String name;
        final Weapon weapon;
        int count;

        Installed(String name, Weapon weapon){
            this.name = name;
            this.weapon = weapon;
        }
    }

    private static String weaponName(Weapon weapon){
        if(weapon instanceof TurretWeapon turretWeapon){
            return turretWeapon.turret.localizedName;
        }
        if(weapon.moduleDisplayName != null && !weapon.moduleDisplayName.isEmpty()){
            return weapon.moduleDisplayName;
        }
        String key = "weapon." + weapon.name + ".name";
        if(Core.bundle.has(key)) return Core.bundle.get(key);
        //native hull weapons have no bundle key: name them by the shot they fire, so a
        //hardpoint never shows an internal id such as "missiles-mount"
        return WeaponModules.shotLabel(weapon);
    }
}
