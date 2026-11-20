package orbital.world.blocks.production;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.Nullable;
import arc.util.io.*;
import orbital.annotations.Annotations.*;
import orbital.ctype.*;
import orbital.engine.world.SectorResources;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.world.*;
import orbital.world.blocks.defense.Radar;
import orbital.world.meta.*;

import static orbital.Vars.*;

/**
 * A planetary drill: once powered, it gradually uncovers the whole sector and scans it for natural
 * resources. When the scan completes it records which ores and liquids exist in the sector and
 * shows them in its configuration panel, and reveals the terrain through the normal fog system.
 */
public class PlanetDrill extends Radar{
    /** Seconds for a full planetary scan (the Radar discoveryTime is reused for fog growth). */
    public float scanTime = 60f * 25f;

    public PlanetDrill(String name){
        super(name);
        //The sector is large; a huge fog radius uncovers it as progress grows.
        fogRadius = 2048;
        configurable = true;
        consumesPower = true;
        //update/solid and the hasFogRadius flag are inherited from Radar.
    }

    public class PlanetDrillBuild extends RadarBuild{
        /** Resources discovered by the completed scan. */
        public Seq<Item> foundItems = new Seq<>();
        public Seq<Liquid> foundLiquids = new Seq<>();
        public boolean scanned = false;

        @Override
        public void updateTile(){
            super.updateTile();

            //When the radar fog growth finishes, perform the resource scan once.
            if(!scanned && progress >= 1f && efficiency > 0f){
                performScan();
            }
        }

        public void performScan(){
            scanned = true;
            foundItems.clear();
            foundLiquids.clear();

            SectorResources resources = SectorResources.get();
            for(Item item : content.items()){
                if(resources.has(item) && resources.ores(item).size > 0) foundItems.add(item);
            }
            for(Liquid liquid : content.liquids()){
                if(resources.has(liquid) && resources.liquids(liquid).size > 0) foundLiquids.add(liquid);
            }

            //Reveal this team's fog once the scan is done (deterministic, safe in multiplayer).
            fogControl.forceUpdate(team, this);

            if(player != null && team == player.team() && ui != null && ui.hudfrag != null){
                ui.hudfrag.showToast(Icon.terrain,
                    Core.bundle.format("planetdrill.found", foundItems.size + foundLiquids.size));
            }
        }

        @Override
        public void buildConfiguration(Table table){
            table.clear();
            table.left().top();

            if(!scanned){
                table.label(() -> Core.bundle.format("planetdrill.progress",
                    Math.min(100, (int)(progress * 100f)))).row();
                return;
            }

            if(foundItems.isEmpty() && foundLiquids.isEmpty()){
                table.add("@planetdrill.none").row();
                return;
            }

            table.add("@planetdrill.title").row();
            for(Item item : foundItems){
                table.image(item.uiIcon).size(24f).padRight(4f);
                table.add(item.localizedName).left().row();
            }
            for(Liquid liquid : foundLiquids){
                table.image(liquid.uiIcon).size(24f).padRight(4f);
                table.add(liquid.localizedName).left().row();
            }
        }

        @Override
        public boolean shouldShowConfigure(@Nullable orbital.gen.Player player){
            return true;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(scanned);
            write.i(foundItems.size);
            for(Item item : foundItems) write.s((short)item.id);
            write.i(foundLiquids.size);
            for(Liquid liquid : foundLiquids) write.s((short)liquid.id);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            scanned = read.bool();
            int n = read.i();
            foundItems.clear();
            for(int i = 0; i < n; i++) foundItems.add(content.items().get(read.s()));
            int m = read.i();
            foundLiquids.clear();
            for(int i = 0; i < m; i++) foundLiquids.add(content.liquids().get(read.s()));
        }
    }

}
