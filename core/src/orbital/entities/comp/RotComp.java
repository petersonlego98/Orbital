package orbital.entities.comp;

import orbital.annotations.Annotations.*;
import orbital.gen.*;

@Component
abstract class RotComp implements Entityc{
    @SyncField(false) @SyncLocal float rotation;
}
