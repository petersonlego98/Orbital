package orbital.entities.comp;

import orbital.annotations.Annotations.*;
import orbital.async.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;

@Component
abstract class UnderwaterMoveComp implements WaterMovec{
    @Import UnitType type;

    @MethodPriority(10f)
    @Replace
    public void draw(){
        //TODO draw status effects?

        Drawf.underwater(() -> {
            type.draw(self());
        });
    }

    @Override
    public int collisionLayer(){
        return PhysicsProcess.layerUnderwater;
    }

    @Override
    public boolean hittable(){
        return false && type.hittable(self());
    }

    @Override
    public boolean targetable(Team targeter){
        return false && type.targetable(self(), targeter);
    }
}

