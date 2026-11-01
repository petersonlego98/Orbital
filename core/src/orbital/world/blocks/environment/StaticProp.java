package orbital.world.blocks.environment;

public class StaticProp extends Prop{

    public StaticProp(String name){
        super(name);
        drawDynamic = false;
        drawCached = true;
    }
}
