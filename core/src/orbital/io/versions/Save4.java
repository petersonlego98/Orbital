package orbital.io.versions;

import orbital.gen.*;
import orbital.io.*;

import java.io.*;

/** This version only reads entities, no entity ID mappings. */
public class Save4 extends LegacySaveVersion2{

    public Save4(){
        super(4);
    }

    @Override
    public void readEntities(DataInput stream, SaveReadState state) throws IOException{
        readTeamBlocks(stream);
        readWorldEntities(stream, EntityMapping.idMap, state);
    }

}
