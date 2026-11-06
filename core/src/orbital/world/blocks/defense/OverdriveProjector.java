package orbital.world.blocks.defense;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.io.*;
import orbital.annotations.Annotations.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.logic.*;
import orbital.ui.*;
import orbital.world.*;
import orbital.world.consumers.*;
import orbital.world.meta.*;

import static orbital.Vars.*;

public class OverdriveProjector extends Block{
    public @Load("@-top") TextureRegion topRegion;
    public float reload = 60f;
    public float range = 80f;
    public float speedBoost = 1.5f;
    public float speedBoostPhase = 0.75f;
    public float useTime = 400f;
    public float phaseRangeBoost = 20f;
    public boolean hasBoost = true;
    public Color baseColor = Color.valueOf("feb380");
    public Color phaseColor = Color.valueOf("ffd59e");

    public OverdriveProjector(String name){
        super(name);
        solid = true;
        update = true;
        group = BlockGroup.projectors;
        hasPower = true;
        hasItems = true;
        canOverdrive = false;
        emitLight = true;
        lightRadius = 50f;
        envEnabled |= Env.space;
        ambientSound = Sounds.loopCircuit;
        ambientSoundVolume = 0.13f;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void init(){
        updateClipRadius(range + (hasBoost ? phaseRangeBoost : 0f) + tilesize);
        super.init();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        float worldX = x * tilesize + offset, worldY = y * tilesize + offset;
        Drawf.rangeCircle(worldX, worldY, range, baseColor, 0.95f, Layer.effect);
        if(hasBoost && phaseRangeBoost > 0f){
            Drawf.rangeCircleMax(worldX, worldY, range + phaseRangeBoost, phaseColor, 0.4f, Layer.effect);
        }

        float previewRange = range + (hasBoost ? phaseRangeBoost : 0f);
        indexer.eachBlock(player.team(), worldX, worldY, previewRange, other -> other.block.canOverdrive, other -> Drawf.selected(other, Tmp.c1.set(baseColor).a(Mathf.absin(4f, 1f))));
    }

    @Override
    public void setStats(){
        stats.timePeriod = useTime;
        super.setStats();

        stats.add(Stat.speedIncrease, "+" + (int)(speedBoost * 100f - 100) + "%");
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.productionTime, useTime / 60f, StatUnit.seconds);

        if(hasBoost && findConsumer(f -> f instanceof ConsumeItems) instanceof ConsumeItems items){
            stats.remove(Stat.booster);
            stats.add(Stat.booster, StatValues.itemBoosters("+{0}%", stats.timePeriod, speedBoostPhase * 100f, phaseRangeBoost, items.items));
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("boost", (OverdriveBuild entity) -> new Bar(() -> Core.bundle.format("bar.boost", Mathf.round(Math.max((entity.realBoost() * 100 - 100), 0))), () -> Pal.accent, () -> entity.realBoost() / (hasBoost ? speedBoost + speedBoostPhase : speedBoost)));
    }

    public class OverdriveBuild extends Building implements Ranged{
        public float heat, charge = Mathf.random(reload), phaseHeat, smoothEfficiency, useProgress;

        @Override
        public float range(){
            return range;
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, lightRadius * smoothEfficiency, baseColor, 0.7f * smoothEfficiency);
        }

        @Override
        public void updateTile(){
            smoothEfficiency = Mathf.lerpDelta(smoothEfficiency, efficiency, 0.08f);
            heat = Mathf.lerpDelta(heat, efficiency > 0 ? 1f : 0f, 0.08f);
            charge += heat * Time.delta;

            if(hasBoost){
                phaseHeat = Mathf.lerpDelta(phaseHeat, optionalEfficiency, 0.1f);
            }

            if(charge >= reload){
                float realRange = range + phaseHeat * phaseRangeBoost;

                charge = 0f;
                indexer.eachBlock(this, realRange, other -> other.block.canOverdrive, other -> other.applyBoost(realBoost(), reload + 1f));
            }

            if(efficiency > 0){
                useProgress += delta();
            }

            if(useProgress >= useTime){
                consume();
                useProgress %= useTime;
            }
        }

        public float realBoost(){
            return (speedBoost + phaseHeat * speedBoostPhase) * efficiency;
        }

        @Override
        public void drawSelect(){
            float realRange = range + phaseHeat * phaseRangeBoost;

            float maximumRange = range + (hasBoost ? phaseRangeBoost : 0f);
            indexer.eachBlock(this, maximumRange, other -> other.block.canOverdrive, other -> Drawf.selected(other, Tmp.c1.set(baseColor).a(Mathf.absin(4f, 1f))));

            Drawf.rangeCircle(x, y, realRange, baseColor, 0.95f, Layer.effect);
            if(maximumRange > realRange + 0.5f){
                Drawf.rangeCircleMax(x, y, maximumRange, phaseColor, 0.4f, Layer.effect);
            }
        }

        @Override
        public void draw(){
            super.draw();

            if(enabled && efficiency > 0f){
                float activeRange = range + phaseHeat * phaseRangeBoost;
                Drawf.rangeCircle(x, y, activeRange, baseColor, 0.78f, Layer.effect);
                float maximumRange = range + (hasBoost ? phaseRangeBoost : 0f);
                if(maximumRange > activeRange + 0.5f){
                    Drawf.rangeCircleMax(x, y, maximumRange, phaseColor, 0.32f, Layer.effect);
                }
            }

            if(!Lod.l2) return;

            float f = 1f - (Time.time / 100f) % 1f;

            Draw.color(baseColor, phaseColor, phaseHeat);
            Draw.alpha(heat * Mathf.absin(Time.time, 50f / Mathf.PI2, 1f) * 0.5f * Lod.alpha2);
            Draw.rect(topRegion, x, y);
            Draw.alpha(Lod.alpha2);
            Lines.stroke((2f * f + 0.1f) * heat);

            float r = Math.max(0f, Mathf.clamp(2f - f * 2f) * size * tilesize / 2f - f - 0.2f), w = Mathf.clamp(0.5f - f) * size * tilesize;
            Lines.beginLine();
            for(int i = 0; i < 4; i++){
                Lines.linePoint(x + Geometry.d4(i).x * r + Geometry.d4(i).y * w, y + Geometry.d4(i).y * r - Geometry.d4(i).x * w);
                if(f < 0.5f) Lines.linePoint(x + Geometry.d4(i).x * r - Geometry.d4(i).y * w, y + Geometry.d4(i).y * r + Geometry.d4(i).x * w);
            }
            Lines.endLine(true);

            Draw.reset();
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(heat);
            write.f(phaseHeat);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            heat = read.f();
            phaseHeat = read.f();
        }
    }
}
