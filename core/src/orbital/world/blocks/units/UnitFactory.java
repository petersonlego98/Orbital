package orbital.world.blocks.units;

import arc.*;
import arc.audio.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import orbital.*;
import orbital.ai.*;
import orbital.ai.types.CommandAI;
import orbital.content.WeaponModules;
import orbital.ctype.*;
import orbital.entities.*;
import orbital.entities.units.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.io.*;
import orbital.logic.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.world.*;
import orbital.world.blocks.*;
import orbital.world.blocks.payloads.*;
import orbital.world.consumers.*;
import orbital.world.meta.*;

import java.util.*;

import static orbital.Vars.*;

public class UnitFactory extends UnitBlock{
    public int[] capacities = {};

    public Seq<UnitPlan> plans = new Seq<>(4);
    public Sound createSound = Sounds.unitCreate;
    public float createSoundVolume = 1f;

    /** Whether this factory evolves in levels instead of relying on separate reconstructor blocks. */
    public boolean leveled = false;
    /** Highest evolution level this factory can reach. */
    public int maxLevel = 5;
    /** Hulls selectable by a leveled factory, priced by their full requirement chain. */
    public Seq<UnitType> evolutionUnits = new Seq<>();
    /** Factory visual per evolution level; index 0 is T2, 1 is T3 and so on. */
    public Block[] evolutionVisuals = {};
    /** Unit lines by tier: each array is {T1, T2, ...} for one hull family. */
    public Seq<UnitType[]> evolutionChains = new Seq<>();


    public UnitFactory(String name){
        super(name);
        update = true;
        hasPower = true;
        hasItems = true;
        solid = true;
        configurable = true;
        clearOnDoubleTap = true;
        outputsPayload = true;
        rotate = true;
        regionRotated1 = 1;
        commandable = true;
        ambientSound = Sounds.loopUnitBuilding;
        ambientSoundVolume = 0.09f;

        config(Integer.class, (UnitFactoryBuild build, Integer i) -> {
            if(!configurable) return;

            if(build.currentPlan == i) return;
            build.currentPlan = i < 0 || i >= plans.size ? -1 : i;
            build.progress = 0;
            if(build.command != null && (build.unit() == null || !build.unit().commands.contains(build.command))){
                build.command = null;
            }
        });

        config(UnitType.class, (UnitFactoryBuild build, UnitType val) -> {
            if(!configurable) return;

            int next = plans.indexOf(p -> p.unit == val);
            if(build.currentPlan == next) return;
            build.currentPlan = next;
            build.progress = 0;
            if(build.command != null && !val.commands.contains(build.command)){
                build.command = null;
            }
        });

        config(UnitCommand.class, (UnitFactoryBuild build, UnitCommand command) -> build.command = command);
        configClear((UnitFactoryBuild build) -> build.command = null);

        consume(new ConsumeItemDynamic((UnitFactoryBuild e) ->
            e.currentPlan != -1 ? plans.get(Math.min(e.currentPlan, plans.size - 1)).requirements : ItemStack.empty));
    }

    @Override
    public void init(){
        initCapacities();
        super.init();
    }

    @Override
    public void afterPatch(){
        initCapacities();
        super.afterPatch();
    }

    public void initCapacities(){
        capacities = new int[Vars.content.items().size];
        itemCapacity = 10; //unit factories can't control their own capacity externally, setting the value does nothing
        for(UnitPlan plan : plans){
            for(ItemStack stack : plan.requirements){
                capacities[stack.item.id] = Math.max(capacities[stack.item.id], stack.amount * 2);
                itemCapacity = Math.max(itemCapacity, stack.amount * 2);
            }
        }

        consumeBuilder.each(c -> c.multiplier = b -> state.rules.unitCost(b.team));
    }

    @Override
    public void checkContentArrayCapacity(int items, int liquids){
        super.checkContentArrayCapacity(items, liquids);
        if(capacities.length != items) capacities = Arrays.copyOf(capacities, items);
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("progress", (UnitFactoryBuild e) -> new Bar(
            () -> Core.bundle.format("bar.progress", Strings.autoFixed(e.fraction() * 100f, 0)),
            () -> Pal.ammo,
            e::fraction
        ));

        addBar("units", (UnitFactoryBuild e) ->
        new Bar(
            () -> e.unit() == null ? "[lightgray]" + Iconc.cancel :
                Core.bundle.format("bar.unitcap",
                    Fonts.getUnicodeStr(e.unit().name),
                    e.team.data().countType(e.unit()),
                    e.unit() == null ? Units.getStringCap(e.team) : (e.unit().useUnitCap ? Units.getStringCap(e.team) : "∞")
                ),
            () -> Pal.power,
            () -> e.unit() == null ? 0f : (e.unit().useUnitCap ? (float)e.team.data().countType(e.unit()) / Units.getCap(e.team) : 1f)
        ));
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(Stat.itemCapacity);

        stats.add(Stat.output, table -> {
            table.row();

            for(var plan : plans){
                table.table(Styles.grayPanel, t -> {

                    if(plan.unit.isBanned()){
                        t.image(Icon.cancel).color(Pal.remove).size(40);
                        return;
                    }

                    if(plan.unit.unlockedNow()){
                        t.image(plan.unit.uiIcon).size(40).pad(10f).left().scaling(Scaling.fit).with(i -> StatValues.withTooltip(i, plan.unit));
                        t.table(info -> {
                            info.add(plan.unit.localizedName).left();
                            info.row();
                            info.add(Strings.autoFixed(plan.time / 60f, 1) + " " + Core.bundle.get("unit.seconds")).color(Color.lightGray);
                        }).left();

                        t.table(req -> {
                            req.right();
                            for(int i = 0; i < plan.requirements.length; i++){
                                if(i % 6 == 0){
                                    req.row();
                                }

                                ItemStack stack = plan.requirements[i];
                                req.add(StatValues.displayItem(stack.item, stack.amount, plan.time, true)).pad(5);
                            }
                        }).right().grow().pad(10f);
                    }else{
                        t.image(Icon.lock).color(Pal.darkerGray).size(40);
                    }
                }).growX().pad(5);
                table.row();
            }
        });
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region, outRegion, topRegion};
    }

    @Override
    public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
        Draw.rect(region, plan.drawx(), plan.drawy());
        Draw.rect(outRegion, plan.drawx(), plan.drawy(), plan.rotation * 90);
        Draw.rect(topRegion, plan.drawx(), plan.drawy());
    }

    @Override
    public void getPlanConfigs(Seq<UnlockableContent> options){
        for(var plan : plans){
            if(!plan.unit.isBanned()){
                options.add(plan.unit);
            }
        }
    }

    public static class UnitPlan{
        public UnitType unit;
        public ItemStack[] requirements;
        public float time;

        public UnitPlan(UnitType unit, float time, ItemStack[] requirements){
            this.unit = unit;
            this.time = time;
            this.requirements = requirements;
        }

        UnitPlan(){}
    }

    public class UnitFactoryBuild extends UnitBuild{
        public @Nullable Vec2 commandPos;
        public @Nullable UnitCommand command;
        public int currentPlan = -1;
        /** Current evolution level of a leveled factory; 1 until it is upgraded. */
        public int level = 1;

        public float fraction(){
            return currentPlan == -1 ? 0 : progress / plans.get(currentPlan).time;
        }

        public boolean canSetCommand(){
            var output = unit();
            return output != null && output.commands.size > 1 && output.allowChangeCommands;
        }

        @Override
        public void created(){
            //auto-set to the first plan, it's better than nothing.
            if(currentPlan == -1){
                currentPlan = plans.indexOf(u -> u.unit.unlockedNow());
            }
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            if(plans.size > 1 && currentPlan != -1 && currentPlan < plans.size){
                drawItemSelection(plans.get(currentPlan).unit);
            }
        }

        @Override
        public Vec2 getCommandPosition(){
            return commandPos;
        }

        @Override
        public void onCommand(Vec2 target){
            commandPos = target;
            if(command != null && command.snapToBuilding){
                var build = world.buildWorld(target.x, target.y);
                if(build != null && build.team == this.team){
                    commandPos.set(build);
                }
            } 
        }

        @Override
        public Object senseObject(LAccess sensor){
            if(sensor == LAccess.config) return currentPlan == -1 ? null : plans.get(currentPlan).unit;
            return super.senseObject(sensor);
        }

        @Override
        public double sense(LAccess sensor){
            if(sensor == LAccess.progress) return Mathf.clamp(fraction());
            if(sensor == LAccess.itemCapacity) return Mathf.round(itemCapacity * state.rules.unitCost(team));
            return super.sense(sensor);
        }

        /**
         * @return the cost of the next evolution level: the items the matching reconstructor
         * would have consumed to reach that tier. Each tier is paid once, so moving from T1 to
         * T3 costs the additive plus the multiplicative recipe.
         */
        public ItemStack[] levelCost(int target){
            if(!leveled || target < 2 || target - 1 > evolutionVisuals.length) return ItemStack.empty;
            Block visual = evolutionVisuals[target - 2];
            if(visual == null) return ItemStack.empty;
            return visual.findConsumer(i -> i instanceof ConsumeItems) instanceof ConsumeItems ci
                ? ci.items : ItemStack.empty;
        }

        public boolean canLevelUp(){
            if(!leveled || level >= maxLevel) return false;
            ItemStack[] cost = levelCost(level + 1);
            Building core = team.core();
            return cost.length > 0 && core != null && core.items.has(cost, 1f);
        }

        /** Spends the cumulative cost from the core, swaps the visual and evolves the whole line. */
        public void levelUp(){
            if(!canLevelUp()) return;
            for(ItemStack stack : levelCost(level + 1)) team.core().items.remove(stack.item, stack.amount);
            int previous = level;
            level++;
            replaceAllUnits(previous, level);
        }

        /**
         * Upgrading a line replaces every unit the player already has in that line: the new hull
         * appears in the same place with the same controller, orders, health ratio and all mounted
         * modules. Enemy units are deliberately left alone.
         */
        public void replaceAllUnits(int fromTier, int toTier){
            if(levelChainsEmpty()) return;
            for(UnitType[] chain : evolutionChains){
                if(fromTier < 1 || toTier > chain.length) continue;
                UnitType from = chain[fromTier - 1], to = chain[toTier - 1];
                if(from == null || to == null || from == to) continue;

                Seq<Unit> affected = new Seq<>();
                for(Unit other : Groups.unit){
                    if(other.team == team && other.type == from) affected.add(other);
                }

                for(Unit old : affected){
                    UnitController controller = old.controller();
                    UnitCommand order = controller instanceof CommandAI ai ? ai.command : null;
                    float healthRatio = old.maxHealth <= 0f ? 1f : old.health / old.maxHealth;
                    float px = old.x, py = old.y, protation = old.rotation;
                    WeaponMount[] oldMounts = old.mounts;
                    SupportMount[] oldSupport = old.supportMounts;
                    WallMount[] oldWalls = old.wallMounts;
                    ModuleSupply oldSupply = old.moduleSupply;

                    old.remove();

                    Unit evolved = to.spawn(team, px, py, protation, null);
                    if(controller != null) evolved.controller(controller);
                    evolved.health = Math.max(1f, healthRatio * evolved.maxHealth);
                    //the player asked to keep every module even if the new hull has no slot for it
                    evolved.mounts = oldMounts;
                    evolved.supportMounts = oldSupport;
                    evolved.wallMounts = oldWalls;
                    evolved.moduleSupply = oldSupply;
                    if(evolved.controller() instanceof CommandAI ai && order != null) ai.command = order;
                    Events.fire(new UnitCreateEvent(evolved, this));
                }

                //a unit waiting inside this factory is part of the line too
                if(payload instanceof UnitPayload && ((UnitPayload)payload).unit.type == from){
                    payload = new UnitPayload(to.create(team));
                }
            }
        }

        private boolean levelChainsEmpty(){
            return evolutionChains == null || evolutionChains.isEmpty();
        }

        @Override
        public void buildConfiguration(Table table){
            Seq<UnitType> units = Seq.with(plans).map(u -> u.unit).retainAll(u -> u.unlockedNow() && !u.isBanned()
                && (!leveled || WeaponModules.tierOf(u) <= level));

            if(units.any()){
                ItemSelection.buildTable(UnitFactory.this, table, units, () -> currentPlan == -1 ? null : plans.get(currentPlan).unit, unit -> configure(plans.indexOf(u -> u.unit == unit)), selectionRows, selectionColumns);

                table.row();

                Table commands = new Table();
                commands.top().left();

                Runnable rebuildCommands = () -> {
                    commands.clear();
                    commands.background(null);
                    var unit = unit();
                    if(unit != null && canSetCommand()){
                        commands.background(Styles.black6);
                        var group = new ButtonGroup<ImageButton>();
                        group.setMinCheckCount(0);
                        int i = 0, columns = Mathf.clamp(units.size, 2, selectionColumns);
                        var list = unit.commands;

                        commands.image(Tex.whiteui, Pal.gray).height(4f).growX().colspan(columns).row();

                        for(var item : list){
                            ImageButton button = commands.button(item.getIcon(), Styles.clearNoneTogglei, 40f, () -> {
                                configure(item);
                            }).tooltip(item.localized()).group(group).get();

                            button.update(() -> button.setChecked(command == item || (command == null && unit.defaultCommand == item)));

                            if(++i % columns == 0){
                                commands.row();
                            }
                        }

                        if(list.size < columns){
                            for(int j = 0; j < (columns - list.size); j++){
                                commands.add().size(40f);
                            }
                        }
                    }
                };

                rebuildCommands.run();

                //Since the menu gets hidden when a new unit is selected, this is unnecessary.
                /*
                UnitType[] lastUnit = {unit()};

                commands.update(() -> {
                    if(lastUnit[0] != unit()){
                        lastUnit[0] = unit();
                        rebuildCommands.run();
                    }
                });*/

                table.row();

                table.add(commands).fillX().left();

                if(leveled){
                    table.row();
                    Table evolution = new Table();
                    evolution.top().left().background(Styles.black6).margin(6f);
                    evolution.add(Core.bundle.format("factory.evolution.level", level, maxLevel))
                        .color(Pal.accent).left().row();
                    if(level < maxLevel){
                        evolution.button(Core.bundle.format("factory.evolution.upgrade", level + 1),
                            Styles.defaultt, this::levelUp).size(220f, 36f).left()
                            .disabled(b -> !canLevelUp()).row();
                    }
                    table.add(evolution).fillX().left();
                }
            }else{
                table.table(Styles.black3, t -> t.add("@none").color(Color.lightGray));
            }
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload){
            return false;
        }

        @Override
        public void display(Table table){
            super.display(table);

            TextureRegionDrawable reg = new TextureRegionDrawable();

            table.row();
            table.table(t -> {
                t.left();
                t.image().update(i -> {
                    i.setDrawable(currentPlan == -1 ? Icon.cancel : reg.set(plans.get(currentPlan).unit.uiIcon));
                    i.setScaling(Scaling.fit);
                    i.setColor(currentPlan == -1 ? Color.lightGray : Color.white);
                }).size(32).padBottom(-4).padRight(2);
                t.label(() -> currentPlan == -1 ? "@none" : plans.get(currentPlan).unit.localizedName).wrap().width(230f).color(Color.lightGray);
            }).left();
        }

        @Override
        public Object config(){
            return currentPlan;
        }

        @Override
        public void draw(){
            //a leveled factory wears the reconstructor sprite of its current tier; the
            //reconstructor blocks themselves stay in the game for ground/naval progression
            TextureRegion shell = region;
            if(leveled && level > 1 && level - 2 < evolutionVisuals.length
                && evolutionVisuals[level - 2] != null){
                shell = evolutionVisuals[level - 2].region;
            }
            Draw.rect(shell, x, y);
            Draw.rect(outRegion, x, y, rotdeg());

            if(currentPlan != -1){
                UnitPlan plan = plans.get(currentPlan);
                Draw.draw(Layer.blockOver, () -> Drawf.construct(this, plan.unit, rotdeg() - 90f, progress / plan.time, speedScl, time));
            }

            Draw.z(Layer.blockOver);

            payRotation = rotdeg();
            drawPayload();

            Draw.z(Layer.blockOver + 0.1f);

            Draw.rect(topRegion, x, y);
        }

        @Override
        public void updateTile(){
            if(!configurable){
                currentPlan = 0;
            }

            if(currentPlan < 0 || currentPlan >= plans.size){
                currentPlan = -1;
            }

            if(efficiency > 0 && currentPlan != -1){
                time += edelta() * speedScl * Vars.state.rules.unitBuildSpeed(team);
                progress += edelta() * Vars.state.rules.unitBuildSpeed(team);
                speedScl = Mathf.lerpDelta(speedScl, 1f, 0.05f);
            }else{
                speedScl = Mathf.lerpDelta(speedScl, 0f, 0.05f);
            }

            moveOutPayload();

            if(currentPlan != -1 && payload == null){
                UnitPlan plan = plans.get(currentPlan);

                //make sure to reset plan when the unit got banned after placement
                if(plan.unit.isBanned()){
                    currentPlan = -1;
                    return;
                }

                if(progress >= plan.time){
                    progress %= 1f;

                    Unit unit = plan.unit.create(team);
                    if(unit.isCommandable()){
                        if(commandPos != null){
                            unit.command().commandPosition(commandPos);
                        }

                        unit.command().command(command == null && unit.type.defaultCommand != null ? unit.type.defaultCommand : command);
                    }

                    createSound.at(this, 1f + Mathf.range(0.06f), createSoundVolume);
                    payload = new UnitPayload(unit);
                    payVector.setZero();
                    consume();
                    Events.fire(new UnitCreateEvent(payload.unit, this));
                }

                progress = Mathf.clamp(progress, 0, plan.time);
            }else{
                progress = 0f;
            }
        }

        @Override
        public boolean shouldConsume(){
            if(currentPlan == -1) return false;
            return enabled && payload == null && team.activateUnitFactories();
        }

        @Override
        public BlockStatus status(){
            if(!team.activateUnitFactories()) return BlockStatus.inactiveUnitFactory;
            return super.status();
        }

        @Override
        public int getMaximumAccepted(Item item){
            return Mathf.round(capacities[item.id] * state.rules.unitCost(team));
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return currentPlan != -1 && items.get(item) < getMaximumAccepted(item) &&
                Structs.contains(plans.get(currentPlan).requirements, stack -> stack.item == item);
        }

        public @Nullable UnitType unit(){
            return currentPlan == - 1 ? null : plans.get(currentPlan).unit;
        }

        @Override
        public byte version(){
            return 4;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(progress);
            write.s(currentPlan);
            TypeIO.writeVecNullable(write, commandPos);
            TypeIO.writeCommand(write, command);
            //the factory's evolution level persists; the unit line is derived from the block
            write.b(level);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            progress = read.f();
            currentPlan = read.s();
            if(revision >= 2){
                commandPos = TypeIO.readVecNullable(read);
            }

            if(revision >= 3){
                command = TypeIO.readCommand(read);
            }

            level = revision >= 4 ? read.b() : 1;
        }
    }
}
