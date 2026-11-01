package orbital.entities.comp;

import arc.util.*;
import orbital.annotations.Annotations.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.type.*;

/** A unit that depends on a building's existence; if that building is removed, it despawns. */
@Component
abstract class BuildingTetherComp implements Unitc{
    @Import UnitType type;
    @Import Team team;

    public @Nullable Building building;

    @Override
    public void update(){
        if(building == null || !building.isValid() || building.team != team){
            Call.unitDespawn(self());
        }
    }
}
