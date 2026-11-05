package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
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
    }

    private static String weaponName(Weapon weapon){
        if(weapon instanceof TurretWeapon turretWeapon){
            return turretWeapon.turret.localizedName;
        }
        return Core.bundle.get("weapon." + weapon.name + ".name", weapon.name);
    }
}
