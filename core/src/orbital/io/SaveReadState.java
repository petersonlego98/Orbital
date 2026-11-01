package orbital.io;

import arc.struct.*;
import arc.util.*;
import orbital.gen.*;
import orbital.world.*;

public class SaveReadState{
    public final WorldContext context;
    /** true when generating a map preview */
    public boolean preview;
    public @Nullable String ruleString;
    public Seq<Building> allBuildings = new Seq<>();

    public SaveReadState(WorldContext context){
        this.context = context;
    }
}
