package orbital.game;

import arc.*;
import arc.math.*;
import arc.struct.*;
import orbital.content.*;
import orbital.entities.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.maps.*;
import orbital.type.*;
import orbital.world.*;

import static orbital.Vars.*;

/** Isolated, fixed-map sandbox used to test newly-added unit and turret features. */
public final class TestNewFeaturesMode{
    public static final String rulesName = "test-new-features";
    public static final int mapSize = 160;

    private static Map testMap;
    private static boolean initializeMono;
    private static boolean listenerInstalled;

    private TestNewFeaturesMode(){
    }

    public static boolean active(){
        return state != null && state.rules != null && rulesName.equals(state.rules.modeName);
    }

    /** Starts a fresh, non-persistent test session on the built-in flat map. */
    public static void start(){
        if(testMap == null){
            testMap = new Map(StringMap.of(
                "name", Core.bundle.get("mode.testfeatures.map", "Teste Novos Recursos"),
                "author", "Orbital"
            ));
            testMap.width = mapSize;
            testMap.height = mapSize;
            world.addMapLoader(testMap, () -> {
                state.map = testMap;
                world.loadGenerator(mapSize, mapSize, tiles -> {
                    tiles.fill();
                    for(Tile tile : tiles){
                        tile.setFloor(Blocks.grass.asFloor());
                    }
                    tiles.getn(mapSize / 2, mapSize / 2).setBlock(Blocks.coreShard, Team.sharded);
                });
            });
        }

        if(!listenerInstalled){
            Events.run(Trigger.newGame, TestNewFeaturesMode::spawnInitialMono);
            listenerInstalled = true;
        }

        Rules rules = Gamemode.testFeatures.apply(new Rules());
        rules.planet = Planets.qatia;
        rules.waves = false;
        rules.waveTimer = false;
        rules.loadout.clear();
        //unit creation is free, but the normal unit cap still applies
        rules.disableUnitCap = false;

        initializeMono = true;
        //createSave = false: this is a disposable test session, it must never touch the save list
        control.playMap(testMap, rules, false, false);
    }

    private static void spawnInitialMono(){
        if(!initializeMono || !active()) return;
        initializeMono = false;

        player.team(Team.sharded);
        Unit mono = UnitTypes.mono.spawn(Team.sharded, world.unitWidth() / 2f, world.unitHeight() / 2f);
        player.unit(mono);
        player.set(mono);
        Core.camera.position.set(mono);
    }

    /** Spawns an aerial unit beside the player's unit, without taking control of it. */
    public static boolean spawn(UnitType type){
        if(!active() || type == null || !type.flying || type.isHidden()) return false;
        if(!Units.canCreate(player.team(), type)) return false;

        Unit source = player.unit();
        if(source == null || source.dead) return false;

        float distance = source.hitSize / 2f + type.hitSize / 2f + tilesize * 2f;
        float angle = Mathf.random(360f);
        float x = Mathf.clamp(source.x + Angles.trnsx(angle, distance), type.hitSize, world.unitWidth() - type.hitSize);
        float y = Mathf.clamp(source.y + Angles.trnsy(angle, distance), type.hitSize, world.unitHeight() - type.hitSize);

        type.spawn(player.team(), x, y, source.rotation);
        return true;
    }

    /** @return every aerial unit in the content list, regardless of unlock state. */
    public static Seq<UnitType> aerialTypes(){
        Seq<UnitType> result = content.units().select(type -> type.flying && !type.isHidden());
        result.sort((a, b) -> a.localizedName.compareToIgnoreCase(b.localizedName));
        return result;
    }
}
