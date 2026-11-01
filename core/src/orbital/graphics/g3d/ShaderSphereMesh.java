package orbital.graphics.g3d;

import arc.graphics.gl.*;
import orbital.type.*;

public class ShaderSphereMesh extends PlanetMesh{

    public ShaderSphereMesh(Planet planet, Shader shader, int divisions){
        super(planet, MeshBuilder.buildIcosphere(divisions, planet.radius), shader);
    }
}
