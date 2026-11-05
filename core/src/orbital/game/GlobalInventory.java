package orbital.game;

import arc.*;
import arc.util.*;
import orbital.core.*;
import orbital.type.*;
import orbital.world.blocks.storage.CoreBlock.*;

import static orbital.Vars.*;

/**
 * Player-wide item storage, shared by every colony and expedition.
 * Contents are persisted in the global settings, independently of sector saves.
 */
public final class GlobalInventory{
    private static final String key = "global-inventory";

    /** Cached contents; loaded lazily after content initialization. */
    private static @Nullable ItemSeq items;

    private GlobalInventory(){
    }

    /** @return the global inventory, loading it from settings if necessary. */
    public static ItemSeq items(){
        if(items == null){
            items = Core.settings.getJson(key, ItemSeq.class, ItemSeq::new);
        }
        return items;
    }

    /** Adds items to the global inventory and persists the result. */
    public static void add(ItemSeq stacks){
        if(stacks == null || stacks.total <= 0) return;

        items().add(stacks);
        save();
    }

    /** Persists the current global inventory. */
    public static void save(){
        Core.settings.putJson(key, ItemSeq.class, items());
        //the sector save happens right after collection, so flush the inventory too
        Core.settings.forceSave();
    }

    /**
     * Drains the current sector core into the global inventory.
     * The caller is responsible for saving the sector afterwards, so the drained core is not restored on reload.
     * @return the items that were collected.
     */
    public static ItemSeq collectFromCore(){
        ItemSeq collected = new ItemSeq();

        if(!state.isGame()) return collected;

        CoreBuild core = state.rules.defaultTeam.core();
        if(core == null) return collected;

        collected.add(core.items);
        if(collected.total <= 0) return collected;

        core.items.clear();
        add(collected);

        return collected;
    }

    /** @return a short, comma-separated description of the sequence, for toasts and labels. */
    public static String format(ItemSeq stacks){
        if(stacks == null || stacks.total <= 0) return "nenhum recurso";

        StringBuilder result = new StringBuilder();
        stacks.each((item, amount) -> {
            if(result.length() > 0) result.append(", ");
            result.append(UI.formatAmount(amount)).append(' ').append(item.localizedName);
        });
        return result.toString();
    }
}
