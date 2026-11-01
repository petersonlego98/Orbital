package orbital.ai.types;

import orbital.entities.units.*;
import orbital.gen.*;

public class NoAI implements UnitController{
    protected Unit unit;

    @Override
    public Unit unit(){
        return unit;
    }

    @Override
    public void unit(Unit unit){
        this.unit = unit;
    }

    @Override
    public boolean isValidController(){
        return true;
    }

    @Override
    public void updateUnit(){}

    @Override
    public void removed(Unit unit){}
}