package orbital.world.blocks.environment;

import orbital.editor.*;
import orbital.world.*;

public class SpawnBlock extends OverlayFloor{

    public SpawnBlock(String name){
        super(name);
        variants = 0;
        needsSurface = false;
    }

    @Override
    public void drawBase(Tile tile){
        if(tile instanceof EditorTile){
            super.drawBase(tile);
        }
    }
}
