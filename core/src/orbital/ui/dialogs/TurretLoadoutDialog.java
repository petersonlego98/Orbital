package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import orbital.content.Blocks;
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
 * <p>Installed native turrets are listed as locked slots. Removable modules can be
 * removed here, and any compatible turret can be mounted while the unit has dorsal
 * budget and an appropriate slot.</p>
 */
public class TurretLoadoutDialog extends BaseDialog{
    private Table list;
    private @Nullable Unit unit;
    private Runnable changed = () -> {};

    public TurretLoadoutDialog(){
        super("@turret.loadout.title");
        cont.pane(t -> list = t).grow().scrollX(false);
        buttons.button("@back", Icon.left, this::hide).size(210f, 64f);
        addCloseListener();
    }

    public void show(Unit unit, Runnable changed){
        this.unit = unit;
        this.changed = changed;
        rebuild();
        show();
    }

    private void rebuild(){
        list.clearChildren();
        list.top().left().margin(12f);

        if(unit == null) return;

        UnitType type = unit.type;

        list.add(type.localizedName).style(Styles.outlineLabel).left().padBottom(4f).row();
        list.add(Core.bundle.format("turret.loadout.budget",
            (int)unit.loadoutCost(), (int)type.dorsalBudget(),
            unit.mountedTurrets(), type.turretSlots.size))
        .color(Color.lightGray).left().padBottom(6f).row();
        list.image().color(Pal.accent).height(3f).growX().padBottom(8f).row();

        list.add("@turret.loadout.installed").style(Styles.outlineLabel).left().padBottom(4f).row();

        int installed = 0;
        for(WeaponMount mount : unit.mounts){
            Weapon weapon = mount.weapon;
            if(!weapon.isTurretMount()) continue;

            installed++;
            boolean removable = weapon instanceof TurretWeapon turretWeapon && turretWeapon.removable;

            list.table(Styles.grayPanel, row -> {
                row.left().margin(6f);
                row.image(weapon.region != null && weapon.region.found() ? weapon.region : type.uiIcon)
                    .size(40f).padRight(10f).scaling(Scaling.fit);
                row.add(weaponName(weapon)).left().growX().ellipsis(true);
                row.add(removable ? "@turret.hud.removable" : "@turret.hud.native")
                    .color(Color.lightGray).padLeft(8f).padRight(8f);

                if(removable){
                    row.button(Icon.cancel, Styles.clearNonei, () -> {
                        unit.removeWeapon(weapon);
                        changed.run();
                        rebuild();
                    }).size(48f).padLeft(4f).tooltip(Core.bundle.get("turret.loadout.remove"))
                        .get().getStyle().imageUpColor = Pal.remove;
                }else{
                    row.image(Icon.lockSmall).size(24f).padLeft(8f).padRight(12f).get().setColor(Pal.gray);
                }
            }).growX().pad(3f).row();
        }

        if(installed == 0){
            list.add("@turret.loadout.none").color(Color.lightGray).left().padBottom(4f).row();
        }

        list.add("@turret.loadout.available").style(Styles.outlineLabel).left().padTop(10f).padBottom(4f).row();

        Table grid = new Table();
        grid.left();
        list.add(grid).growX().row();

        int count = 0;
        int columns = mobile ? 3 : Mathf.clamp((int)(Core.graphics.getWidth() / Scl.scl(140f)), 4, 8);
        float cardWidth = mobile ? 130f : 124f;
        float btnSize = mobile ? 64f : 68f;
        float imgSize = mobile ? 44f : 48f;

        for(Block block : content.blocks()){
            if(!(block instanceof Turret turret) || block.isHidden() || !TurretWeapon.canAdapt(turret)) continue;
            if(!unit.canMount(turret)) continue;

            count++;
            grid.table(Tex.pane, cell -> {
                cell.margin(6f).top();
                var btn = cell.button(new TextureRegionDrawable(turret.uiIcon), Styles.selecti, () -> {
                    if(unit.mountTurret(turret) != null){
                        changed.run();
                        rebuild();
                    }
                }).size(btnSize).tooltip(turret.localizedName).padBottom(4f).get();
                btn.resizeImage(imgSize);
                cell.row();
                cell.add(turret.localizedName).width(cardWidth - 8f).height(24f)
                    .ellipsis(true).center().padTop(2f).row();
                float cost = turret.size * TurretSlot.pxTile;
                cell.add(Core.bundle.format("turret.loadout.size", turret.size, turret.size, (int)(cost * cost)))
                    .color(Color.lightGray).fontScale(0.85f).center();
            }).width(cardWidth).minHeight(mobile ? 124f : 130f).pad(4f);

            if(count % columns == 0) grid.row();
        }

        if(count == 0){
            list.add("@turret.loadout.noavailable").color(Color.lightGray).left().row();
        }

        list.image().color(Pal.accent).height(3f).growX().padTop(12f).padBottom(8f).row();
        list.add("@support.loadout.installed").style(Styles.outlineLabel).left().padBottom(4f).row();
        list.add("@support.loadout.limit").color(Color.lightGray).left().padBottom(4f).row();
        //the HUD can switch modules off without reopening this dialog, so read the live state
        list.label(() -> {
            boolean hasSupport = false, anyActive = false;
            if(unit.supportMounts != null){
                for(SupportMount support : unit.supportMounts){
                    if(support == null || support.block == null) continue;
                    hasSupport = true;
                    if(support.active) anyActive = true;
                }
            }
            return Core.bundle.get(hasSupport && !anyActive ? "support.loadout.inactive" : "support.loadout.active");
        }).color(Color.lightGray).left().wrap().padBottom(6f).row();

        int supportCount = 0;
        for(SupportMount support : unit.supportMounts){
            if(support == null || support.block == null) continue;
            supportCount++;

            list.table(Styles.grayPanel, row -> {
                row.left().margin(6f);
                row.image(support.block.uiIcon).size(40f).padRight(10f).scaling(Scaling.fit);
                row.add(support.block.localizedName).left().growX().ellipsis(true);
                row.add(Core.bundle.get("support.category." + support.category.name()))
                    .color(Color.lightGray).padLeft(8f).padRight(8f);
                Label state = row.label(() -> Core.bundle.get(support.active
                    ? "support.hud.active" : "support.hud.inactive"))
                    .padLeft(8f).padRight(8f).get();
                state.update(() -> state.setColor(support.active ? Pal.heal : Pal.remove));
                row.button(Icon.cancel, Styles.clearNonei, () -> {
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

        //A hull that already has an equivalent ability owns that effect permanently. Show it as
        //a fixed module instead of letting the player install a second, conflicting copy.
        int nativeCount = 0;
        for(SupportModuleCategory category : SupportModuleCategory.values()){
            if(type.hasNativeSupport(category)) nativeCount++;
        }

        if(nativeCount > 0){
            list.add("@support.loadout.native").style(Styles.outlineLabel).left().padTop(10f).padBottom(4f).row();

            for(SupportModuleCategory category : SupportModuleCategory.values()){
                if(!type.hasNativeSupport(category)) continue;

                list.table(Styles.grayPanel, row -> {
                    row.left().margin(6f);
                    row.image(nativeIcon(category).uiIcon).size(40f).padRight(10f).scaling(Scaling.fit);
                    row.add(Core.bundle.get("support.category." + category.name())).left().growX().ellipsis(true);
                    row.add("@support.loadout.fixed").color(Color.lightGray).padLeft(8f).padRight(8f);
                    row.image(Icon.lockSmall).size(24f).padLeft(8f).padRight(12f).get().setColor(Pal.gray);
                }).growX().pad(3f).row();
            }

            list.add("@support.loadout.native.locked").color(Color.lightGray).left().padBottom(4f).row();
        }

        list.add("@support.loadout.available").style(Styles.outlineLabel).left().padTop(10f).padBottom(4f).row();

        Table supportGrid = new Table();
        supportGrid.left();
        list.add(supportGrid).growX().row();

        int supportOptions = 0;
        for(Block block : content.blocks()){
            if(block == null || block.isHidden() || !SupportModuleCategory.isSupport(block)) continue;
            if(!unit.canMountSupport(block)) continue;

            supportOptions++;
            supportGrid.table(Tex.pane, cell -> {
                cell.margin(6f).top();
                var btn = cell.button(new TextureRegionDrawable(block.uiIcon), Styles.selecti, () -> {
                    if(unit.mountSupport(block) != null){
                        changed.run();
                        rebuild();
                    }
                }).size(btnSize).tooltip(block.localizedName).padBottom(4f).get();
                btn.resizeImage(imgSize);
                cell.row();
                cell.add(block.localizedName).width(cardWidth - 8f).height(24f)
                    .ellipsis(true).center().padTop(2f).row();
                cell.add(Core.bundle.get("support.category." + SupportModuleCategory.of(block).name()))
                    .color(Color.lightGray).fontScale(0.85f).center().padTop(2f).row();
                cell.add(Core.bundle.format("support.loadout.size", block.size, block.size))
                    .color(Color.lightGray).fontScale(0.85f).center();
            }).width(cardWidth).minHeight(mobile ? 124f : 130f).pad(4f);

            if(supportOptions % columns == 0) supportGrid.row();
        }

        if(supportOptions == 0){
            list.add("@support.loadout.noavailable").color(Color.lightGray).left().row();
        }
    }

    /** Representative block icon for a built-in support effect. */
    private static Block nativeIcon(SupportModuleCategory category){
        return switch(category){
            case repair -> Blocks.mender;
            case overdrive -> Blocks.overdriveProjector;
            case force -> Blocks.forceProjector;
        };
    }

    private static String weaponName(Weapon weapon){
        if(weapon instanceof TurretWeapon turretWeapon){
            return turretWeapon.turret.localizedName;
        }
        return Core.bundle.get("weapon." + weapon.name + ".name", weapon.name);
    }
}
