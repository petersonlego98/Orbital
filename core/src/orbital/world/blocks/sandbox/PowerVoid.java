package orbital.world.blocks.sandbox;

import orbital.world.blocks.power.*;
import orbital.world.meta.*;

public class PowerVoid extends PowerBlock{

    public PowerVoid(String name){
        super(name);
        consumePower(Float.MAX_VALUE);
        envEnabled = Env.any;
        enableDrawStatus = false;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.remove(Stat.powerUse);
    }
}
