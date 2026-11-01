package orbital.world.consumers;

import orbital.type.*;

public class ConsumeItemExplosive extends ConsumeItemEfficiency{
    public float minExplosiveness;

    public ConsumeItemExplosive(float minCharge){
        this.minExplosiveness = minCharge;
        filter = item -> item.explosiveness >= this.minExplosiveness;
    }

    public ConsumeItemExplosive(){
        this(0.2f);
    }

    @Override
    public float itemEfficiencyMultiplier(Item item){
        return item.explosiveness;
    }
}
