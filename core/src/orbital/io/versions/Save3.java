package orbital.io.versions;

import orbital.game.*;
import orbital.game.Teams.*;
import orbital.io.*;

import java.io.*;

import static orbital.Vars.*;

public class Save3 extends LegacySaveVersion{

    public Save3(){
        super(3);
    }

    @Override
    public void readEntities(DataInput stream, SaveReadState state) throws IOException{
        int teamc = stream.readInt();
        for(int i = 0; i < teamc; i++){
            Team team = Team.get(stream.readInt());
            TeamData data = team.data();
            int blocks = stream.readInt();
            for(int j = 0; j < blocks; j++){
                data.plans.addLast(new BlockPlan(stream.readShort(), stream.readShort(), stream.readShort(), content.block(stream.readShort()), stream.readInt()));
            }
        }

        readLegacyEntities(stream);
    }
}
