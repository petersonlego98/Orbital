package orbital.entities.comp;

import orbital.annotations.Annotations.*;
import orbital.entities.*;
import orbital.entities.EntityCollisions.*;
import orbital.gen.*;

@Component
abstract class ElevationMoveComp implements Velc, Posc, Hitboxc, Unitc{
    @Import float x, y;

    @Replace
    @Override
    public SolidPred solidity(){
        return isFlying() || ignoreSolids() ? null : EntityCollisions::solid;
    }

}
