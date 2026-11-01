package orbital.world.draw;

import arc.graphics.g2d.*;
import arc.util.*;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.world.*;

public class DrawDefault extends DrawBlock{

    @Override
    public void draw(Building build){
        Draw.rect(build.block.region, build.x, build.y, build.drawrot());
    }

    @Override
    public void drawPlan(Block block, BuildPlan plan, Eachable<BuildPlan> list){
        block.drawDefaultPlanRegion(plan, list);
    }

    @Override
    public TextureRegion[] icons(Block block){
        return new TextureRegion[]{block.region};
    }
}
