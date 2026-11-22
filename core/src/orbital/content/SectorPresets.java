package orbital.content;

import orbital.maps.*;
import orbital.type.*;

import static orbital.content.Planets.*;

public class SectorPresets{
    public static SectorPreset
    groundZero,
    crateredBattleground, biomassFacility, taintedWoods, frozenForest, ruinousShores, facility32m, windsweptIslands, stainedMountains, tarFields,
    frontier, fungalPass, infestedCanyons, atolls, sunkenPier, mycelialBastion, extractionOutpost, saltFlats, testingGrounds, overgrowth,
    impact0078, desolateRift, nuclearComplex, planetaryTerminal,
    coastline, navalFortress, weatheredChannels, perilousHarbor, littoralShipyard,

    onset, aegis, lake, intersect, basin, atlas, split, marsh, peaks, ravine, caldera,
    stronghold, crevice, siege, crossroads, karst, origin;

    public static void load(){
        //region qatia

        groundZero = new SectorPreset("groundZero", qatia, 15){{
            alwaysUnlocked = true;
            addStartingItems = true;
            captureWave = 10;
            difficulty = 1;
            overrideLaunchDefaults = true;
            noLighting = true;
            startWaveTimeMultiplier = 3f;
        }};

        saltFlats = new SectorPreset("saltFlats", qatia, 101){{
            difficulty = 5;
        }};

        testingGrounds = new SectorPreset("testingGrounds", qatia, 3){{
            difficulty = 7;
            captureWave = 33;
        }};

        frozenForest = new SectorPreset("frozenForest", qatia, 86){{
            captureWave = 15;
            difficulty = 2;
        }};

        biomassFacility = new SectorPreset("biomassFacility", qatia, 81){{
            captureWave = 20;
            difficulty = 3;
        }};

        taintedWoods = new SectorPreset("taintedWoods", qatia, 221){{
            captureWave = 33;
            difficulty = 5;
        }};

        crateredBattleground = new SectorPreset("crateredBattleground", qatia, 18){{
            captureWave = 20;
            difficulty = 2;
        }};

        ruinousShores = new SectorPreset("ruinousShores", qatia, 213){{
            captureWave = 30;
            difficulty = 3;
        }};

        perilousHarbor = new SectorPreset("perilousHarbor", qatia, 47){{
            difficulty = 4;
        }};

        facility32m = new SectorPreset("facility32m", qatia, 64){{
            captureWave = 25;
            difficulty = 4;
        }};

        windsweptIslands = new SectorPreset("windsweptIslands", qatia, 246){{
            captureWave = 30;
            difficulty = 4;
        }};

        stainedMountains = new SectorPreset("stainedMountains", qatia, 20){{
            captureWave = 30;
            difficulty = 3;
        }};

        extractionOutpost = new SectorPreset("extractionOutpost", qatia, 165){{
            difficulty = 5;
        }};

        coastline = new SectorPreset("coastline", qatia, 108){{
            captureWave = 30;
            difficulty = 5;
        }};

        weatheredChannels = new SectorPreset("weatheredChannels", qatia, 39){{
            captureWave = 40;
            difficulty = 9;
        }};

        navalFortress = new SectorPreset("navalFortress", qatia, 216){{
            difficulty = 8;
        }};

        frontier = new SectorPreset("frontier", qatia, 50){{
            difficulty = 4;
        }};

        fungalPass = new SectorPreset("fungalPass", qatia, 21){{
            difficulty = 2;
        }};

        infestedCanyons = new SectorPreset("infestedCanyons", qatia, 210){{
            difficulty = 4;
        }};

        atolls = new SectorPreset("atolls", qatia, 1){{
            difficulty = 7;
        }};

        sunkenPier = new SectorPreset("sunkenPier", qatia, -1){{
            captureWave = 50;
            difficulty = 8;
        }};

        mycelialBastion = new SectorPreset("mycelialBastion", qatia, 260){{
            difficulty = 8;
        }};

        overgrowth = new SectorPreset("overgrowth", qatia, 134){{
            difficulty = 5;
        }};

        tarFields = new SectorPreset("tarFields", qatia, 23){{
            captureWave = 40;
            difficulty = 5;
        }};

        impact0078 = new SectorPreset("impact0078", qatia, 227){{
            captureWave = 45;
            difficulty = 7;
        }};

        desolateRift = new SectorPreset("desolateRift", qatia, 123){{
            captureWave = 18;
            difficulty = 8;
        }};

        nuclearComplex = new SectorPreset("nuclearComplex", qatia, 130){{
            captureWave = 50;
            difficulty = 7;
        }};

        littoralShipyard = new SectorPreset("littoralShipyard", qatia, 204){{
            difficulty = 9;
        }};

        planetaryTerminal = new SectorPreset("planetaryTerminal", qatia, 93){{
            difficulty = 10;
            isLastSector = true;
        }};

        SectorSubmissions.registerSectors();

        //endregion
        //region qatia

        onset = new SectorPreset("onset", qatia, 10){{
            alwaysUnlocked = true;
            difficulty = 1;
        }};

        aegis = new SectorPreset("aegis", qatia, 88){{
            difficulty = 3;
        }};

        lake = new SectorPreset("lake", qatia, 41){{
            difficulty = 4;
        }};

        intersect = new SectorPreset("intersect", qatia, 36){{
            difficulty = 5;
            captureWave = 9;
            attackAfterWaves = true;
        }};

        atlas = new SectorPreset("atlas", qatia, 14){{
            difficulty = 5;
        }};

        split = new SectorPreset("split", qatia, 19){{
            difficulty = 2;
        }};

        basin = new SectorPreset("basin", qatia, 29){{
            difficulty = 6;
        }};

        marsh = new SectorPreset("marsh", qatia, 25){{
            difficulty = 4;
        }};

        peaks = new SectorPreset("peaks", qatia, 30){{
            difficulty = 3;
        }};

        ravine = new SectorPreset("ravine", qatia, 39){{
            difficulty = 4;
            captureWave = 24;
        }};

        caldera = new SectorPreset("caldera-qatia", qatia, 43){{
            difficulty = 4;
        }};

        stronghold = new SectorPreset("stronghold", qatia, 18){{
            difficulty = 7;
        }};

        crevice = new SectorPreset("crevice", qatia, 3){{
            difficulty = 6;
            captureWave = 46;
        }};

        siege = new SectorPreset("siege", qatia, 58){{
            difficulty = 8;
        }};

        crossroads = new SectorPreset("crossroads", qatia, 37){{
            difficulty = 7;
        }};

        karst = new SectorPreset("karst", qatia, 5){{
            difficulty = 9;
            captureWave = 10;
        }};

        origin = new SectorPreset("origin", qatia, 12){{
            difficulty = 10;
            isLastSector = true;
        }};

        //endregion
    }
}
