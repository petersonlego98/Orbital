package orbital.world.consumers;

import orbital.type.*;
import orbital.world.*;

public abstract class ConsumeLiquidBase extends Consume{
    /** amount used per frame */
    public float amount;

    public ConsumeLiquidBase(float amount){
        this.amount = amount;
    }

    public ConsumeLiquidBase(){}

    @Override
    public void apply(Block block){
        block.hasLiquids = true;
    }

    public abstract boolean consumes(Liquid liquid);
}
