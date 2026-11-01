package orbital.world.blocks.power;

import orbital.world.*;
import orbital.world.meta.*;

public class PowerBlock extends Block{

    public PowerBlock(String name){
        super(name);
        update = true;
        solid = true;
        hasPower = true;
        group = BlockGroup.power;
    }
}
