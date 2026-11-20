package orbital.ui.fragments;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import orbital.content.*;
import orbital.game.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.ui.*;

import static orbital.Vars.*;

public class BlockConfigFragment{
    Table table = new Table();
    Building selected;

    public void build(Group parent){
        table.visible = false;
        parent.addChild(table);

        Events.on(ResetEvent.class, e -> forceHide());
    }

    public void forceHide(){
        table.visible = false;
        selected = null;
    }

    public boolean isShown(){
        return table.visible && selected != null;
    }

    public Building getSelected(){
        return selected;
    }

    public void showConfig(Building tile){
        if(selected != null) selected.onConfigureClosed();
        if(tile.configTapped()){
            selected = tile;

            table.visible = true;
            table.clear();
            table.background(null);
            tile.buildConfiguration(table);

            table.row();
            table.add(Core.bundle.format("building.level.label", tile.level, BuildingLevels.maxLevel))
                .left().padTop(4f);
            //evolution progress bar while the upgrade is in progress
            if(tile.evolving){
                table.row();
                Table bar = new Table();
                bar.add(new Bar(() -> Core.bundle.format("building.level.evolving", tile.level, tile.evolveTarget),
                    () -> Pal.accent, () -> Math.max(0f, tile.evolutionProgress()))).growX().height(18f);
                bar.visible(() -> tile.evolving);
                table.add(bar).growX().padTop(4f);
            }

            table.row();
            if(tile.level < BuildingLevels.maxLevel){
                var upgrade = table.button(Core.bundle.format("building.level.upgrade", BuildingLevels.costString(tile)), () -> {
                    if(BuildingLevels.upgrade(tile, player.team())) showConfig(tile);
                }).padTop(4f).get();
                upgrade.setDisabled(!BuildingLevels.canUpgrade(tile, player.team()));
                upgrade.update(() -> upgrade.setDisabled(!BuildingLevels.canUpgrade(tile, player.team())));
            }
            table.pack();
            table.setTransform(true);
            table.actions(Actions.scaleTo(0f, 1f), Actions.visible(true),
            Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out));

            table.update(() -> {
                if(selected != null && selected.shouldHideConfigure(player)){
                    hideConfig();
                    return;
                }

                table.setOrigin(Align.center);
                if(selected == null || selected.block == Blocks.air || !selected.isValid()){
                    hideConfig();
                }else{
                    selected.updateTableAlign(table);
                }
            });
        }
    }

    public boolean hasConfigMouse(){
        Element e = Core.scene.getHoverElement();
        return e != null && (e == table || e.isDescendantOf(table));
    }

    public void hideConfig(){
        if(selected != null) selected.onConfigureClosed();
        selected = null;
        table.actions(Actions.scaleTo(0f, 1f, 0.06f, Interp.pow3Out), Actions.visible(false));
    }
}
