package orbital.entities.comp;

import orbital.annotations.Annotations.*;
import orbital.gen.*;
import orbital.world.blocks.power.*;

@EntityDef(value = PowerGraphUpdaterc.class, serialize = false, genio = false)
@Component
abstract class PowerGraphUpdaterComp implements Entityc{
    public transient PowerGraph graph;

    @Override
    public void update(){
        graph.update();
    }
}
