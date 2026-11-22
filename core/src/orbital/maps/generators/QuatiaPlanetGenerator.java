package orbital.maps.generators;

import arc.graphics.*;
import arc.math.geom.*;
import orbital.content.*;
import orbital.game.*;
import orbital.type.*;
import orbital.world.*;

/**
 * Planet-level generator for Quatia. Sector terrain is produced by
 * {@link QuatiaSectorGenerator}; this class supplies the sector size, landing
 * rules and the green rainforest colours used by the planet mesh.
 */
public class QuatiaPlanetGenerator extends BlankPlanetGenerator{

    @Override
    public int getSectorSize(Sector sector){
        return QuatiaSectorGenerator.size;
    }

    /** Every sector of Quatia is habitable, numbered ones included. */
    @Override
    public boolean allowLanding(Sector sector){
        return true;
    }

    @Override
    public void generate(Tiles tiles, Sector sector, WorldParams params){
        new QuatiaSectorGenerator(sector).generate(tiles, params);
    }

    /** Two rainforest greens for the planet's noise mesh. */
    @Override
    public void getColor(Vec3 position, Color out){
        float n = noise(position.x * 0.7f, position.y * 0.7f, 3, 0.55, 1.6, 1f);
        out.set(n > 0f ? 0x4f9a4aff : 0x2f6b38ff);
    }
}
