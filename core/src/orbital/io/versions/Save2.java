package orbital.io.versions;

import orbital.io.*;

import java.io.*;

public class Save2 extends LegacySaveVersion{

    public Save2(){
        super(2);
    }

    @Override
    public void readEntities(DataInput stream, SaveReadState state) throws IOException{
        readLegacyEntities(stream);
    }
}
