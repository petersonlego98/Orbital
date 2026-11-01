package orbital.entities.comp;

import orbital.annotations.Annotations.*;
import orbital.gen.*;

@Component
abstract class DrawComp implements Posc{

    float clipSize(){
        return Float.MAX_VALUE;
    }

    void draw(){

    }
}
