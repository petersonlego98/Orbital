package orbital.world.blocks.defense;

import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import orbital.annotations.Annotations.*;
import orbital.content.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.logic.*;
import orbital.world.*;
import orbital.world.consumers.*;
import orbital.world.meta.*;

import static orbital.Vars.*;

public class MendProjector extends Block{
    public final int timerUse = timers++;
    public Color baseColor = Color.valueOf("84f491");
    public Color phaseColor = baseColor;
    public @Load("@-top") TextureRegion topRegion;
    public float reload = 250f;
    public float range = 60f;
    public float healPercent = 12f;
    public float phaseBoost = 12f;
    public float phaseRangeBoost = 50f;
    public float useTime = 400f;
    public Sound mendSound = Sounds.healWave;
    public float mendSoundVolume = 0.5f;

    private boolean any = false;

    public MendProjector(String name){
        super(name);
        solid = true;
        update = true;
        group = BlockGroup.projectors;
        hasPower = true;
        hasItems = true;
        emitLight = true;
        lightRadius = 50f;
        suppressable = true;
        envEnabled |= Env.space;
        flags = EnumSet.of(BlockFlag.blockRepair);
        drawCached = true;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        stats.timePeriod = useTime;
        super.setStats();

        stats.add(Stat.repairTime, (int)(100f / healPercent * reload / 60f), StatUnit.seconds);
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);

        if(findConsumer(c -> c instanceof ConsumeItems) instanceof ConsumeItems cons){
            stats.remove(Stat.booster);
            stats.add(Stat.booster, StatValues.itemBoosters(
                "{0}" + StatUnit.timesSpeed.localized(),
                stats.timePeriod, (phaseBoost + healPercent) / healPercent, phaseRangeBoost,
                cons.items)
            );
        }
    }

    @Override
    public void init(){
        updateClipRadius(range + phaseRangeBoost + tilesize);
        super.init();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        float worldX = x * tilesize + offset, worldY = y * tilesize + offset;
        Drawf.rangeCircle(worldX, worldY, range, baseColor, 0.95f, Layer.effect);
        if(phaseRangeBoost > 0f){
            Drawf.rangeCircleMax(worldX, worldY, range + phaseRangeBoost, phaseColor, 0.4f, Layer.effect);
        }

        indexer.eachBlock(player.team(), worldX, worldY, range + phaseRangeBoost, other -> true, other -> Drawf.selected(other, Tmp.c1.set(baseColor).a(Mathf.absin(4f, 1f))));
    }

    public class MendBuild extends Building implements Ranged{
        public float heat, charge = Mathf.random(reload), phaseHeat, smoothEfficiency;

        @Override
        public float range(){
            return range;
        }

        @Override
        public void updateTile(){
            boolean canHeal = !checkSuppression();

            smoothEfficiency = Mathf.lerpDelta(smoothEfficiency, efficiency, 0.08f);
            heat = Mathf.lerpDelta(heat, efficiency > 0 && canHeal ? 1f : 0f, 0.08f);
            charge += heat * delta();

            phaseHeat = Mathf.lerpDelta(phaseHeat, optionalEfficiency, 0.1f);

            if(optionalEfficiency > 0 && timer(timerUse, useTime / timeScale) && canHeal){
                consume();
            }

            if(charge >= reload && canHeal){
                float realRange = range + phaseHeat * phaseRangeBoost;
                charge = 0f;

                any = false;

                indexer.eachBlock(this, realRange, b -> b.damaged() && !b.isHealSuppressed(), other -> {
                    other.heal(other.maxHealth() * (healPercent + phaseHeat * phaseBoost) / 100f * efficiency);
                    other.recentlyHealed();
                    Fx.healBlockFull.at(other.x, other.y, other.block.size, baseColor, other.block);
                    any = true;
                });

                if(any){
                    mendSound.at(this, 1f + Mathf.range(0.1f), mendSoundVolume);
                }
            }
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.progress) return Mathf.clamp(charge / reload);
            return super.sense(sensor);
        }

        @Override
        public void drawSelect(){
            float realRange = range + phaseHeat * phaseRangeBoost;

            indexer.eachBlock(this, range + phaseRangeBoost, other -> true, other -> Drawf.selected(other, Tmp.c1.set(baseColor).a(Mathf.absin(4f, 1f))));

            Drawf.rangeCircle(x, y, realRange, baseColor, 0.95f, Layer.effect);
            if(range + phaseRangeBoost > realRange + 0.5f){
                Drawf.rangeCircleMax(x, y, range + phaseRangeBoost, phaseColor, 0.4f, Layer.effect);
            }
        }

        @Override
        public void drawCached(){
            super.draw();
        }

        @Override
        public void draw(){
            if(enabled && efficiency > 0f && !isHealSuppressed()){
                float activeRange = range + phaseHeat * phaseRangeBoost;
                Drawf.rangeCircle(x, y, activeRange, baseColor, 0.78f, Layer.effect);
                if(range + phaseRangeBoost > activeRange + 0.5f){
                    Drawf.rangeCircleMax(x, y, range + phaseRangeBoost, phaseColor, 0.32f, Layer.effect);
                }
            }

            float f = 1f - (Time.time / 100f) % 1f;

            if(!Lod.l2) return;

            Draw.color(baseColor, phaseColor, phaseHeat);
            Draw.alpha(heat * Mathf.absin(Time.time, 50f / Mathf.PI2, 1f) * 0.5f * Lod.alpha2);
            Draw.rect(topRegion, x, y);
            Draw.alpha(Lod.alpha2);
            Lines.stroke((2f * f + 0.2f) * heat);
            Lines.square(x, y, Math.min(1f + (1f - f) * size * tilesize / 2f, size * tilesize/2f));

            Draw.reset();
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, lightRadius * smoothEfficiency, baseColor, 0.7f * smoothEfficiency);
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
