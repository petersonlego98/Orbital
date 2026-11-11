package orbital.entities.comp;

import arc.util.io.*;
import orbital.annotations.Annotations.*;
import orbital.entities.*;
import orbital.gen.*;

import arc.graphics.Color;

import static orbital.Vars.*;

@Component
@BaseComponent
abstract class EntityComp{
    private transient boolean added;
    transient int id = EntityGroup.nextId();

    /**
     * Colour tinted onto the hull by active dorsal wall plates; white when none.
     * Must be its own instance: aliasing {@code Color.white} (a shared static in arc) here made
     * every {@code set()} mutate the global white, which broke the tint guard in
     * {@code UnitType.applyColor} and leaked the wall colour into every UI drawn in white.
     */
    public Color wallColor = new Color(Color.white);

    /** Sets the hull tint from active wall plates. Called from WeaponsComp each frame. */
    public void wallColor(Color color){
        this.wallColor.set(color);
    }

    boolean isAdded(){
        return added;
    }

    void update(){}

    void remove(){
        added = false;
    }

    void add(){
        added = true;
    }

    boolean isLocal(){
        return ((Object)this) == player || ((Object)this) instanceof Unitc u && u.controller() == player;
    }

    boolean isRemote(){
        return ((Object)this) instanceof Unitc u && u.isPlayer() && !isLocal();
    }

    /** Replaced with `this` after code generation. */
    <T extends Entityc> T self(){
        return (T)this;
    }

    <T> T as(){
        return (T)this;
    }

    @InternalImpl
    abstract int classId();

    @InternalImpl
    abstract boolean serialize();

    @MethodPriority(1)
    void read(Reads read){
        afterRead();
    }

    void write(Writes write){

    }

    void beforeWrite(){

    }

    void afterRead(){

    }

    //called after all entities have been read (useful for ID resolution)
    void afterReadAll(){

    }
}
