package orbital.entities.units;

import arc.struct.Seq;
import orbital.content.WeaponModules;

/**
 * Runtime-only combined weapon module: one dorsal slot, one weapon copy per shot tier.
 * Every active tier fires together; the pilot switches tiers from the module menu.
 */
public class GroupedWeaponMount{
    public final WeaponModules.Group group;
    public final int slot;
    public final WeaponMount[] tiers = new WeaponMount[5];
    public final boolean[] active = new boolean[5];

    public GroupedWeaponMount(WeaponModules.Group group, int slot){
        this.group = group;
        this.slot = slot;
    }

    public int activeCount(){
        int count = 0;
        for(boolean on : active) if(on) count++;
        return count;
    }
}
