package orbital.content;

import arc.util.*;
import orbital.content.TechTree.*;
import orbital.type.*;

/**
 * Single research tree for the only playable world, Quatia.
 *
 * <p>The historical Serpulo and Erekir trees are loaded for their nodes and dependencies, then
 * merged: the second root's children are re-parented under the first, and the combined root is
 * named "universal" and attached to Quatia. No research node or requirement is lost.</p>
 */
public final class UniversalTechTree{
    private UniversalTechTree(){
    }

    public static void load(){
        SerpuloTechTree.load();
        ErekirTechTree.load();

        if(TechTree.roots.size < 2){
            Log.warn("[techtree] expected two legacy roots, found @", TechTree.roots.size);
            return;
        }

        TechNode combined = TechTree.roots.first();
        TechNode second = TechTree.roots.get(1);

        //Re-parent the second root's children into the combined root, preserving node order.
        for(TechNode child : second.children){
            child.parent = combined;
            child.rootNode = combined;
            combined.children.add(child);
        }
        second.children.clear();

        combined.name = "universal";
        combined.planet = Planets.qatia;
        TechTree.roots.remove(second);

        Log.info("[techtree] merged @ nodes into the universal tree (@ children)",
            TechTree.all.size, combined.children.size);
    }
}
