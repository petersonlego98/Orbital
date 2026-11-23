package orbital.input;

import arc.*;
import arc.math.*;
import orbital.content.*;
import orbital.game.*;
import orbital.graphics.*;
import orbital.gen.Unit;

import static orbital.Vars.*;

/**
 * Shared world-tap hit-test for dropped modules. Both desktop and mobile input call this so the
 * install rule, the range check and the user feedback stay identical on every platform.
 */
public final class LootInput{
    private LootInput(){
    }

    /**
     * Tries to install the module under a world point.
     * @return true when the tap was consumed (a drop was hit), regardless of install success, so
     * the click never falls through to mining, config or shooting.
     */
    public static boolean tryInstall(float worldX, float worldY, int count, boolean touch){
        if(count != 1 || !BattleRoyaleMode.active() || !BattleRoyaleMode.battleStarted()) return false;
        if(player == null || player.dead()) return false;

        Unit unit = player.unit();
        if(unit == null || unit.dead) return false;

        DroppedModule drop = BattleRoyaleLoot.findAt(worldX, worldY, touch ? 20f : 14f);
        if(drop == null) return false;

        if(Mathf.dst2(unit.x, unit.y, drop.x, drop.y) > BattleRoyaleLoot.pickupRange * BattleRoyaleLoot.pickupRange){
            toast(Core.bundle.get("module.drop.far"));
            return true;
        }

        if(drop.install(unit)){
            BattleRoyaleLoot.remove(drop);
            Fx.pickup.at(drop.x, drop.y);
            toast(Core.bundle.format("module.drop.installed", drop.name()));
        }else{
            toast(Core.bundle.format("module.drop.invalid", drop.name(), drop.reason(unit)));
        }
        return true;
    }

    /** Shows the floating effect tooltip when the mouse hovers a drop. */
    public static void hover(float worldX, float worldY){
        if(!BattleRoyaleMode.active()) return;
        LootRenderer.hover(BattleRoyaleLoot.findAt(worldX, worldY, 16f));
    }

    private static void toast(String text){
        if(ui != null) ui.showInfoToast(text, 3f);
    }
}
