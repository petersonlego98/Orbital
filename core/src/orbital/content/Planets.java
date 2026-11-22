package orbital.content;

import arc.func.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import java.util.Locale;
import orbital.game.*;
import orbital.graphics.*;
import orbital.graphics.g3d.*;
import orbital.graphics.g3d.PlanetGrid.*;
import orbital.maps.generators.QuatiaPlanetGenerator;
import orbital.type.*;
import orbital.world.*;
import orbital.world.meta.*;

/**
 * Celestial bodies. Orbital has exactly one playable world: Quatia. Serpulo, Erekir, Tantros and
 * the old asteroid expeditions were removed — all content (items, liquids, blocks and units) is
 * unified under Quatia as "Recursos Universais". The sun remains only as the star at the centre of
 * the map; it is not accessible.
 */
public class Planets{
    public static Planet
    sun,
    qatia;

    /** Key of the first procedural planet. Changing this changes its name, mesh and orbit seed. */
    private static final String originKey = "orbital:origin";

    public static void load(){
        sun = new Planet("sun", null, 4f){{
            bloom = true;
            accessible = false;

            meshLoader = () -> new SunMesh(
                this, 4,
                5, 0.3, 1.7, 1.2, 1,
                1.1f,
                Color.valueOf("ff7a38"),
                Color.valueOf("ff9638"),
                Color.valueOf("ffc64c"),
                Color.valueOf("ffc64c"),
                Color.valueOf("ffe371"),
                Color.valueOf("f4ee8e")
            );
        }};

        createOrigin();
    }

    /**
     * Quatia is the only playable colony: one procedural sector with the unified resources of both
     * former planets. Loadouts and stored sector data are never written to the campaign save list.
     */
    private static void createOrigin(){
        String generated = PlanetNames.generate(originKey, 3);
        String internal = generated.toLowerCase(Locale.ROOT);

        //Use a full sector grid (272 tiles) so the unified campaign's SectorPresets keep their
        //historical sector indices. With a single sector, `sector %= sectors.size` collapsed every
        //preset onto sector 0 and broke spawn/core validation.
        qatia = new Planet(internal, sun, 1.1f, 3){{
            generator = new QuatiaPlanetGenerator();
            defaultCore = Blocks.coreShard;
            startSector = 0;
            alwaysUnlocked = true;
            accessible = true;
            visible = true;
            mapView = false;
            drawOrbit = true;

            //orbit close to the sun, so both bodies fit in a top-down view
            orbitRadius = 9.2f;
            totalRadius = radius;
            orbitTime = Mathf.pow(orbitRadius, 1.5f) * 1000f;

            //soft look: blue-green ocean + light continents and a thin atmosphere
            meshLoader = () -> new NoiseMesh(
            this, 3, 4, 1.1f, 5, 0.55f, 9f, 0.14f,
            Color.valueOf("2b5f86"),
            Color.valueOf("4c9a5f"),
            5, 0.55f, 7.5f, 0.12f
            );
            cloudMeshLoader = () -> new HexSkyMesh(
            this, 2, 0.12f, 0.14f, 4, Color.valueOf("e8f4ff").a(0.55f), 5, 0.5f, 6.5f, 0.42f
            );

            atmosphereColor = Color.valueOf("4ba3e0");
            atmosphereRadIn = 0.02f;
            atmosphereRadOut = 0.3f;
            hasAtmosphere = true;
            iconColor = Color.valueOf("4f9bd6");
            icon = "planet";
            bloom = false;
            updateLighting = true;
            defaultEnv = Env.terrestrial;
            allowLaunchToNumbered = false;
            ruleSetter = rules -> {
                rules.waveTeam = Team.crux;
                rules.waves = false;
                rules.attackMode = false;
            };
        }};
    }
}
