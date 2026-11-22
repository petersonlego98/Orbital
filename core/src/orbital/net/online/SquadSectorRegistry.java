package orbital.net.online;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import orbital.io.*;

/**
 * Persistent map of squad (group of up to 4 players) -> unique sector seed.
 *
 * <p>Saved on the server under a single settings key, so a group that reconnects always gets the
 * same sector and two different groups never share one. The seed is derived from the group id and
 * checked against a collision set: if two distinct group ids ever hash to the same seed, a counter
 * is bumped until a free seed is found and the result is persisted immediately.</p>
 */
public final class SquadSectorRegistry{
    /** Players per squad. */
    public static final int squadSize = 4;
    private static final String key = "orbital.squad.sectors";

    /** One persisted group entry. Public fields for JsonIO. */
    public static class Squad{
        public String id;
        public long seed;
        /** Which of Quatia's 272 sectors belongs to this squad. */
        public int sectorId = -1;
        public Seq<String> uuids = new Seq<>();
        public long createdAt;

        public Squad(){
        }

        public Squad(String id, long seed, int sectorId){
            this.id = id;
            this.seed = seed;
            this.sectorId = sectorId;
            this.createdAt = Time.millis();
        }
    }

    /** Number of sectors on Quatia's full grid. */
    private static final int sectorCount = 272;

    private final ObjectMap<String, Squad> squads = new ObjectMap<>();
    /** Seed -> group id, to guarantee uniqueness across groups. */
    private final ObjectMap<Long, String> usedSeeds = new ObjectMap<>();
    /** Sector id -> group id, so two squads never share a planet sector. */
    private final ObjectMap<Integer, String> usedSectors = new ObjectMap<>();

    public SquadSectorRegistry(){
        load();
    }

    /** Associates a player uuid with a squad and returns it, creating one if needed. */
    public Squad assign(String uuid){
        //Already assigned: reuse.
        for(Squad squad : squads.values()){
            if(squad.uuids.contains(uuid)) return squad;
        }

        //Join an open squad.
        for(Squad squad : squads.values()){
            if(squad.uuids.size < squadSize){
                squad.uuids.add(uuid);
                save();
                return squad;
            }
        }

        //Create a new squad with a fresh unique seed and its own planet sector.
        String id = newSquadId();
        long seed = freshSeed(id);
        int sectorId = freshSector(seed);
        Squad squad = new Squad(id, seed, sectorId);
        squad.uuids.add(uuid);
        squads.put(id, squad);
        usedSeeds.put(seed, id);
        usedSectors.put(sectorId, id);
        save();
        return squad;
    }

    /** @return the team slot (0-3) of a player inside its squad, or -1. */
    public int slot(String squadId, String uuid){
        Squad squad = squads.get(squadId);
        return squad == null ? -1 : squad.uuids.indexOf(uuid);
    }

    public @Nullable Squad byId(String id){
        return squads.get(id);
    }

    public int size(){
        return squads.size;
    }

    /** @return all known squads, for the server console listing. */
    public Seq<Squad> all(){
        return squads.values().toSeq();
    }

    /** @return the sector seed for a squad id, or -1 when unknown. */
    public long seedFor(String id){
        Squad squad = squads.get(id);
        return squad == null ? -1 : squad.seed;
    }

    /** @return the planet sector id assigned to a squad, or -1 when unknown. */
    public int sectorIdFor(String id){
        Squad squad = squads.get(id);
        return squad == null ? -1 : squad.sectorId;
    }

    /** @return the squad that owns a planet sector, or null when unassigned. */
    public @Nullable Squad bySector(int sectorId){
        String id = usedSectors.get(sectorId);
        return id == null ? null : squads.get(id);
    }

    /** @return the planet sector index a squad seed maps to (before collision resolution). */
    public static int sectorIdForSeed(long seed){
        return (int)Mathf.mod(seed, sectorCount);
    }

    private int freshSector(long seed){
        int candidate = sectorIdForSeed(seed);
        while(usedSectors.containsKey(candidate)){
            candidate = (candidate + 1) % sectorCount;
        }
        return candidate;
    }

    private String newSquadId(){
        //Counter-based id is stable and human readable; the seed is derived separately.
        long next = squads.size + 1;
        while(squads.containsKey("squad-" + next)) next++;
        return "squad-" + next;
    }

    private long freshSeed(String id){
        long base = mix(id.hashCode());
        long candidate = base;
        int salt = 0;
        while(usedSeeds.containsKey(candidate)){
            candidate = mix(base + (++salt) * 0x9E3779B97F4A7C15L);
        }
        return candidate;
    }

    private static long mix(long value){
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private void load(){
        try{
            @SuppressWarnings("unchecked")
            ObjectMap<String, Squad> stored = Core.settings.getJson(key, ObjectMap.class, ObjectMap::new);
            if(stored != null){
                for(var entry : stored.entries()){
                    if(entry.value == null || entry.value.seed == 0 && entry.value.uuids == null) continue;
                    if(entry.value.uuids == null) entry.value.uuids = new Seq<>();
                    //Older registries had no sector assignment; derive a free one from the seed.
                    if(entry.value.sectorId < 0 || entry.value.sectorId >= sectorCount || usedSectors.containsKey(entry.value.sectorId)){
                        entry.value.sectorId = freshSector(entry.value.seed);
                    }
                    squads.put(entry.key, entry.value);
                    usedSeeds.put(entry.value.seed, entry.key);
                    usedSectors.put(entry.value.sectorId, entry.key);
                }
            }
        }catch(Throwable e){
            Log.warn("[squad] could not load sector registry: @", e.getMessage());
        }
    }

    /** Persists immediately — a crash must not lose a squad's sector assignment. */
    public void save(){
        try{
            Core.settings.putJson(key, squads);
            Core.settings.forceSave();
        }catch(Throwable e){
            Log.err("[squad] could not save sector registry: @", e.getMessage());
        }
    }
}
