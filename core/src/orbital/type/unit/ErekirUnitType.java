package orbital.type.unit;

import orbital.graphics.*;
import orbital.type.*;
import orbital.world.meta.*;

/** Config class for special Erekir unit properties. */
public class ErekirUnitType extends UnitType{

    public ErekirUnitType(String name){
        super(name);
        outlineColor = Pal.darkOutline;
        envDisabled = Env.space;
        researchCostMultiplier = 10f;
    }
}
