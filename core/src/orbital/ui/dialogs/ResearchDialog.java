package orbital.ui.dialogs;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.content.*;
import orbital.content.TechTree.*;
import orbital.core.*;
import orbital.game.EventType.*;
import orbital.game.Objectives.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.input.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.ui.layout.*;
import orbital.ui.layout.TreeLayout.*;

import java.util.*;

import static orbital.Vars.*;
import static orbital.gen.Tex.*;

public class ResearchDialog extends BaseDialog{
    public static boolean debugShowRequirements = false;

    public final float nodeSize = Scl.scl(60f);
    public ObjectSet<TechTreeNode> nodes = new ObjectSet<>();
    public TechTreeNode root = new TechTreeNode(TechTree.roots.first(), null);
    public TechNode lastNode = root.node;
    public Rect bounds = new Rect();
    public ItemsDisplay itemDisplay;
    public View view;

    public ItemSeq items;

    private final Seq<Planet> rootPlanets = new Seq<>(false, 4);
    private boolean showTechSelect;
    private boolean needsRebuild;

    public ResearchDialog(){
        super("");

        Events.on(ResetEvent.class, e -> {
            hide();
        });

        Events.on(UnlockEvent.class, e -> {
            if(net.client() && !needsRebuild){
                needsRebuild = true;
                Core.app.post(() -> {
                    needsRebuild = false;

                    checkNodes(root);
                    view.hoverNode = null;
                    treeLayout();
                    view.rebuild();
                    Core.scene.act();
                });
            }
        });

        titleTable.remove();
        titleTable.clear();
        titleTable.top();
        titleTable.button(b -> {
            //TODO custom icon here.
            b.imageDraw(() -> root.node.icon()).padRight(8).size(iconMed);
            b.add().growX();
            b.label(() -> root.node.localizedName()).color(Pal.accent);
            b.add().growX();
            b.add().size(iconMed);
        }, () -> {
            new BaseDialog("@techtree.select"){{
                cont.pane(t -> {
                    t.table(Tex.button, in -> {
                        in.defaults().width(300f).height(60f);
                        for(TechNode node : TechTree.roots){
                            if(node.requiresUnlock && !node.content.unlockedHost() && node != getPrefRoot()) continue;

                            //TODO toggle
                            in.button(node.localizedName(), node.icon(), Styles.flatTogglet, iconMed, () -> {
                                if(node == lastNode){
                                    return;
                                }

                                rebuildTree(node);
                                hide();
                            }).marginLeft(12f).checked(node == lastNode).row();
                        }
                    });
                });

                addCloseButton();
            }}.show();
        }).visible(() -> showTechSelect = TechTree.roots.count(node -> !(node.requiresUnlock && !node.content.unlockedHost())) > 1).minWidth(300f);

        margin(0f).marginBottom(8);
        cont.stack(titleTable, view = new View(), itemDisplay = new ItemsDisplay()).grow();
        itemDisplay.visible(() -> !net.client());

        titleTable.toFront();

        shouldPause = true;

        onResize(this::checkMargin);

        shown(() -> {
            checkMargin();
            Core.app.post(this::checkMargin);

            Planet currPlanet = ui.planet.isShown() ?
                ui.planet.state.planet :
                state.isCampaign() ? state.rules.sector.planet : null;

            if(currPlanet != null && currPlanet.techTree != null){
                switchTree(currPlanet.techTree);
            }
            rebuildItems();

            checkNodes(root);
            treeLayout();

            view.hoverNode = null;
            view.infoTable.remove();
            view.infoTable.clear();
            //items are ready now: rebuild the list so requirement counts and buttons are accurate
            view.rebuildAll();
        });

        hidden(ui.planet::setup);

        addCloseButton();

        keyDown(key -> {
            if(key == Binding.research.value.key){
                Core.app.post(this::hide);
            }
        });

        buttons.button("@database", Icon.book, () -> {
            hide();
            ui.database.show();
        }).size(210f, 64f).name("database");

        touchable = Touchable.enabled;
    }

    void checkMargin(){
        if(Core.graphics.isPortrait() && showTechSelect){
            itemDisplay.marginTop(60f);
        }else{
            itemDisplay.marginTop(0f);
        }
        itemDisplay.invalidate();
        itemDisplay.layout();
    }

    public void rebuildItems(){
        items = new ItemSeq(){
            //store sector item amounts for modifications
            ObjectMap<Sector, ItemSeq> cache = new ObjectMap<>();

            {
                //first, find a planets associated with the current tech tree
                rootPlanets.clear();
                for(var planet : content.planets()){
                    if(planet.techTree == lastNode || lastNode.planet == planet){
                        rootPlanets.add(planet);
                    }
                }

                //if there is no root, fall back to serpulo
                if(rootPlanets.size == 0){
                    rootPlanets.add(Planets.serpulo);
                }

                //add global counts of each sector
                for(Planet planet : rootPlanets){
                    for(Sector sector : planet.sectors){
                        if(sector.hasBase() && !sector.isFrozen()){
                            ItemSeq cached = sector.items();
                            cache.put(sector, cached);
                            cached.each((item, amount) -> {
                                values[item.id] += Math.max(amount, 0);
                                total += Math.max(amount, 0);
                            });
                        }
                    }
                }
            }

            //this is the only method that actually modifies the sequence itself.
            @Override
            public void add(Item item, int amount){
                //only have custom removal logic for when the sequence gets items taken out of it (e.g. research)
                if(amount < 0){
                    Sector curSector = state.isCampaign() ? state.rules.sector : null;
                    //remove items from each sector's storage, one by one

                    //negate amount since it's being *removed* - this makes it positive
                    amount = -amount;

                    //% that gets removed from each sector except the current one
                    double percentage = (double)amount / Math.max(get(item) - (curSector != null && cache.get(curSector) != null ? cache.get(curSector).get(item) : 0), 1);
                    int[] counter = {amount};

                    cache.each((sector, seq) -> {
                        if(counter[0] == 0 || sector == curSector) return;

                        int available = seq.get(item);
                        if(available <= 0) return;

                        //amount that will be removed
                        int toRemove = Math.min(counter[0], Math.min((int)Math.ceil(percentage * available), available));

                        //actually remove it from the sector
                        sector.removeItem(item, toRemove);
                        seq.remove(item, toRemove);

                        counter[0] -= toRemove;
                    });

                    //only consume from the active sector last
                    if(counter[0] > 0 && curSector != null){
                        var seq = cache.get(curSector);
                        if(seq != null){
                            int toRemove = Math.min(seq.get(item), counter[0]);

                            curSector.removeItem(item, toRemove);
                            seq.remove(item, toRemove);

                            counter[0] -= toRemove;
                        }
                    }

                    //negate again to display correct number
                    amount = -amount;
                }

                super.add(item, amount);
            }
        };

        itemDisplay.rebuild(items);
    }

    public @Nullable TechNode getPrefRoot(){
        Planet currPlanet = ui.planet.isShown() ?
            ui.planet.state.planet :
            state.isCampaign() ? state.rules.sector.planet : null;
        return currPlanet == null ? null : currPlanet.techTree;
    }

    public void switchTree(TechNode node){
        if(lastNode == node || node == null) return;
        nodes.clear();
        root = new TechTreeNode(node, null);
        lastNode = node;
        view.rebuildAll();

        rebuildItems();
    }

    public void rebuildTree(TechNode node){
        switchTree(node);
        view.panX = 0f;
        view.panY = -200f;
        view.setScale(1f);

        view.hoverNode = null;
        view.infoTable.remove();
        view.infoTable.clear();

        checkNodes(root);
        treeLayout();
    }

    void treeLayout(){
        //No-op: the research menu is now a vertical list, not a positioned node graph.
        if(true) return;
        float spacing = 20f;
        LayoutNode node = new LayoutNode(root, null);
        LayoutNode[] children = node.children;
        LayoutNode[] leftHalf = Arrays.copyOfRange(node.children, 0, Mathf.ceil(node.children.length/2f));
        LayoutNode[] rightHalf = Arrays.copyOfRange(node.children, Mathf.ceil(node.children.length/2f), node.children.length);

        node.children = leftHalf;
        new BranchTreeLayout(){{
            gapBetweenLevels = gapBetweenNodes = spacing;
            rootLocation = TreeLocation.top;
        }}.layout(node);

        float lastY = node.y;

        if(rightHalf.length > 0){

            node.children = rightHalf;
            new BranchTreeLayout(){{
                gapBetweenLevels = gapBetweenNodes = spacing;
                rootLocation = TreeLocation.bottom;
            }}.layout(node);

            shift(leftHalf, node.y - lastY);
        }

        node.children = children;

        float minx = 0f, miny = 0f, maxx = 0f, maxy = 0f;
        copyInfo(node);

        for(TechTreeNode n : nodes){
            if(!n.visible) continue;
            minx = Math.min(n.x - n.width/2f, minx);
            maxx = Math.max(n.x + n.width/2f, maxx);
            miny = Math.min(n.y - n.height/2f, miny);
            maxy = Math.max(n.y + n.height/2f, maxy);
        }
        bounds = new Rect(minx, miny, maxx - minx, maxy - miny);
        bounds.y += nodeSize*1.5f;
    }

    void shift(LayoutNode[] children, float amount){
        for(LayoutNode node : children){
            node.y += amount;
            if(node.children != null && node.children.length > 0) shift(node.children, amount);
        }
    }

    void copyInfo(LayoutNode node){
        node.node.x = node.x;
        node.node.y = node.y;
        if(node.children != null){
            for(LayoutNode child : node.children){
                copyInfo(child);
            }
        }
    }

    void checkNodes(TechTreeNode node){
        boolean locked = locked(node.node);
        if(!locked && (node.parent == null || node.parent.visible)) node.visible = true;
        node.selectable = selectable(node.node);
        for(TechTreeNode l : node.children){
            l.visible = !locked && l.parent.visible;
            checkNodes(l);
        }

        itemDisplay.rebuild(items);
    }

    boolean selectable(TechNode node){
        //there's a desync here as far as sectors go, since the client doesn't know about that, but I'm not too concerned
        return node.content.unlockedHost() || !node.objectives.contains(i -> !i.complete());
    }

    boolean locked(TechNode node){
        return !node.content.unlockedHost();
    }

    class LayoutNode extends TreeNode<LayoutNode>{
        final TechTreeNode node;

        LayoutNode(TechTreeNode node, LayoutNode parent){
            this.node = node;
            this.parent = parent;
            this.width = this.height = nodeSize;
            if(node.children != null){
                children = Seq.with(node.children).map(t -> new LayoutNode(t, this)).toArray(LayoutNode.class);
            }
        }
    }

    public class TechTreeNode extends TreeNode<TechTreeNode>{
        public final TechNode node;
        public boolean visible = true, selectable = true;

        public TechTreeNode(TechNode node, TechTreeNode parent){
            this.node = node;
            this.parent = parent;
            this.width = this.height = nodeSize;
            nodes.add(this);
            children = new TechTreeNode[node.children.size];
            for(int i = 0; i < children.length; i++){
                children[i] = new TechTreeNode(node.children.get(i), this);
            }
        }
    }

    public class View extends Group{
        public float panX = 0, panY = -200, lastZoom = -1;
        public boolean moved = false;
        public ImageButton hoverNode;
        public Table infoTable = new Table();

        /** Vertical list of research nodes; replaces the old draggable node graph. */
        private final Table list = new Table();
        private final ScrollPane listPane = new ScrollPane(list, Styles.smallPane);

        {
            rebuildAll();
        }

        @Override
        protected void sizeChanged(){
            super.sizeChanged();
            //keep the scrollable list filling the dialog area
            listPane.setSize(getWidth(), getHeight());
            list.setWidth(Math.max(getWidth() - 20f, 100f));
        }

        public void rebuildAll(){
            clear();
            hoverNode = null;
            infoTable.clear();
            infoTable.touchable = Touchable.enabled;

            //Build a flat, scrollable list of every visible research node in tree order.
            list.clear();
            list.top().left();
            list.defaults().left().padBottom(4f).padLeft(4f).padTop(4f);

            lastNode.each(tech -> {
                TechTreeNode node = findNode(tech);
                if(node == null || !node.visible) return;
                list.add(buildListRow(node)).growX().left().row();
            });

            listPane.setScrollingDisabled(true, false);
            listPane.setSize(getWidth(), getHeight());
            list.setWidth(Math.max(getWidth() - 20f, 100f));
            addChild(listPane);
            listPane.invalidateHierarchy();
        }

        /** @return the wrapper node for a TechNode in the current tree, or null. */
        private TechTreeNode findNode(TechNode tech){
            for(TechTreeNode n : nodes){
                if(n.node == tech) return n;
            }
            return null;
        }

        /** One row in the research list: icon, name, requirements and a Research button. */
        private Table buildListRow(TechTreeNode node){
            TechNode tech = node.node;
            boolean isLocked = locked(tech);
            boolean canSelect = selectable(tech);
            boolean canResearch = canSpend(tech) && isLocked && !net.client();

            Table row = new Table();
            row.background(Tex.button);
            row.left();
            row.defaults().left().padRight(8f).padTop(4f).padBottom(4f);

            //icon: lock when not yet available, real icon otherwise
            Drawable icon = canSelect ? new TextureRegionDrawable(tech.content.uiIcon) : Icon.lock;
            row.add(new Image(icon)).size(iconMed).padLeft(8f).padRight(10f);

            //name column with requirements underneath
            Table info = new Table();
            info.left().defaults().left();
            info.add(canSelect ? tech.content.localizedName : "[accent]???")
                .color(canSelect ? Color.white : Pal.gray);
            info.row();

            if(isLocked && !net.client()){
                if(tech.requirements.length > 0){
                    boolean progress = Structs.contains(tech.finishedRequirements, s -> s.amount > 0);
                    if(progress){
                        float sum = 0f, used = 0f;
                        for(int i = 0; i < tech.requirements.length; i++){
                            sum += tech.requirements[i].item.cost * tech.requirements[i].amount;
                            used += tech.finishedRequirements[i].item.cost * tech.finishedRequirements[i].amount;
                        }
                        info.add(Core.bundle.format("research.progress", Math.min((int)(used / Math.max(sum, 1f) * 100), 99)))
                            .color(Color.lightGray).row();
                    }
                    for(int i = 0; i < tech.requirements.length; i++){
                        ItemStack req = tech.requirements[i], done = tech.finishedRequirements[i];
                        if(req.amount <= done.amount && !debugShowRequirements) continue;
                        int need = debugShowRequirements ? req.amount : req.amount - done.amount;
                        Table stack = new Table();
                        stack.left();
                        stack.image(req.item.uiIcon).size(24f).padRight(4f);
                        stack.add(req.item.localizedName).color(Color.lightGray).padRight(6f);
                        Label label = stack.label(() -> " " +
                            UI.formatAmount(items == null ? 0 : Math.min(items.get(req.item), need)) + " / " + UI.formatAmount(need)).get();
                        label.setColor(items != null && items.has(req.item) ? Color.lightGray : Color.scarlet);
                        stack.add(label);
                        info.add(stack).fillX().left().row();
                    }
                }else if(tech.objectives.size > 0){
                    for(Objective o : tech.objectives){
                        if(o.complete()) continue;
                        info.add("> " + o.display()).color(Color.lightGray).row();
                    }
                }
            }else if(!isLocked){
                info.add("@completed").color(Color.lightGray);
            }
            row.add(info).growX().left().padRight(8f);

            //info button for unlocked content
            if(canSelect){
                row.button(Icon.info, Styles.flati, () -> ui.content.show(tech.content))
                    .size(44f).padRight(6f);
            }

            //Research button on the right
            if(!net.client()){
                row.button("@research", Icon.ok, new TextButtonStyle(){{
                        disabled = Tex.button;
                        font = Fonts.def;
                        fontColor = Color.white;
                        disabledFontColor = Color.gray;
                        up = buttonOver;
                        over = buttonDown;
                    }}, () -> spend(tech))
                    .disabled(b -> !canSpend(tech) || !locked(tech))
                    .size(140f, 44f);
            }

            return row;
        }

        void clamp(){
            float pad = nodeSize;

            float ox = width/2f, oy = height/2f;
            float rx = bounds.x + panX + ox, ry = panY + oy + bounds.y;
            float rw = bounds.width, rh = bounds.height;
            rx = Mathf.clamp(rx, -rw + pad, Core.graphics.getWidth() - pad);
            ry = Mathf.clamp(ry, -rh + pad, Core.graphics.getHeight() - pad);
            panX = rx - bounds.x - ox;
            panY = ry - bounds.y - oy;
        }

        boolean canSpend(TechNode node){
            if(!selectable(node) || net.client() || items == null) return false;

            if(node.requirements.length == 0) return true;

            //can spend when there's at least 1 item that can be spent (non complete)
            for(int i = 0; i < node.requirements.length; i++){
                if(node.finishedRequirements[i].amount < node.requirements[i].amount && items.has(node.requirements[i].item)){
                    return true;
                }
            }

            //can always spend when locked
            return node.content.locked();
        }

        void spend(TechNode node){
            if(net.client()) return;

            boolean complete = true;

            boolean[] shine = new boolean[node.requirements.length];
            boolean[] usedShine = new boolean[content.items().size];

            for(int i = 0; i < node.requirements.length; i++){
                ItemStack req = node.requirements[i];
                ItemStack completed = node.finishedRequirements[i];

                //amount actually taken from inventory
                int used = Math.max(Math.min(req.amount - completed.amount, items.get(req.item)), 0);
                items.remove(req.item, used);
                completed.amount += used;

                if(used > 0){
                    shine[i] = true;
                    usedShine[req.item.id] = true;
                }

                //disable completion if the completed amount has not reached requirements
                if(completed.amount < req.amount){
                    complete = false;
                }
            }

            if(complete){
                unlock(node);
            }

            node.save();

            //??????
            Core.scene.act();
            rebuild(shine);
            itemDisplay.rebuild(items, usedShine);
            checkMargin();
        }

        void unlock(TechNode node){
            node.content.unlock();

            //unlock parent nodes in multiplayer.
            TechNode parent = node.parent;
            while(parent != null){
                parent.content.unlock();
                parent = parent.parent;
            }

            checkNodes(root);
            hoverNode = null;
            treeLayout();
            rebuild();
            Core.scene.act();
            Sounds.uiUnlock.play();
            Events.fire(new ResearchEvent(node.content));
        }

        void rebuild(){
            rebuildAll();
        }

        //the shine highlight is no longer shown; just rebuild the list
        void rebuild(@Nullable boolean[] shine){
            rebuildAll();
        }

        @Override
        public void drawChildren(){
            //the list is drawn by the ScrollPane child; no node graph lines to draw
            super.drawChildren();
        }
    }
}
