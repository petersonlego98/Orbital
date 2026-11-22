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
import orbital.game.*;
import orbital.game.EventType.*;
import orbital.game.Objectives.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.input.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.ui.layout.*;
import orbital.ui.layout.TreeLayout.*;
import orbital.world.*;

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

    //Layout filters (adapted from the reference): only show unlocked content, and only show
    //entries that still have something to research. Both default to off, matching the old list.
    private boolean onlyUnlocked = false;
    private boolean onlyResearchable = false;

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
        cont.clear();
        buildLayout();

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
            rebuildModules();
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

    /**
     * Research menu laid out like the module catalogue: a header, a live "evolutions in progress"
     * strip, two filter switches, then two scrollable groups - the research tree and the real
     * dorsal modules. All data comes from existing game state; no synthetic values are introduced.
     */
    private void buildLayout(){
        //An opaque background is essential here: the old research list was drawn over the map,
        //but this layout stacks cards and bars that become unreadable over terrain.
        cont.background(Styles.black9);
        //The dialog fills the stage; make the content fill it too so the groups use the whole
        //window on Windows instead of hugging their preferred height.
        cont.setFillParent(true);
        cont.top().left();
        cont.defaults().left();

        //header
        cont.table(head -> {
            head.add("@research.modules.title").style(Styles.outlineLabel).fontScale(1.4f).color(Pal.accent);
            head.add().growX();
        }).growX().pad(6f, 14f, 2f, 14f).row();

        //evolutions actually running on the map
        cont.table(evo -> buildEvolutions(evo)).growX().pad(0f, 14f, 4f, 14f).row();

        //filters
        cont.table(filters -> {
            filters.defaults().left().padBottom(6f);
            //larger hit area than the mouse cursor on desktop
            filters.check("@research.modules.onlyunlocked", onlyUnlocked, v -> { onlyUnlocked = v; view.rebuildAll(); })
                .padTop(4f).padBottom(4f).row();
            filters.check("@research.modules.onlyresearchable", onlyResearchable, v -> { onlyResearchable = v; view.rebuildAll(); })
                .padTop(4f).padBottom(4f).row();
        }).growX().pad(0f, 14f, 6f, 14f).row();

        //both groups scroll inside a bounded height so the dialog never outgrows the screen
        //Use the full height available on desktop; leave a small reserve for the header,
        //upgrades, filters and the bottom buttons.
        float groupHeight = mobile
            ? Math.max(130f, Math.min(260f, Core.graphics.getHeight() / Scl.scl(1f) * 0.25f))
            : Math.max(240f, Core.graphics.getHeight() / Scl.scl(1f) * 0.38f);

        //group 1: research, reusing the existing list logic
        cont.table(g -> {
            g.add("@research.modules.research").color(Pal.accent).row();
            view = new View();
            g.add(new ScrollPane(view, Styles.smallPane)).growX().height(groupHeight);
        }).growX().pad(4f, 14f, 4f, 14f).row();

        //group 2: real dorsal modules, grouped by type, each card backed by its block's tech node
        cont.table(g -> {
            g.add("@research.modules.modules").color(Pal.accent).row();
            moduleGroup = new Table();
            moduleGroup.top().left();
            g.add(new ScrollPane(moduleGroup, Styles.smallPane)).growX().height(groupHeight);
        }).growX().pad(4f, 14f, 4f, 14f).row();

        //item counts, kept in the hierarchy so the existing rebuild calls still work
        cont.add(itemDisplay = new ItemsDisplay()).growX().pad(4f, 14f, 10f, 14f).row();
        itemDisplay.visible(() -> !net.client());
        rebuildModules();
    }

    /** Lists buildings on the current map that are evolving to a higher level, with a live bar. */
    private void buildEvolutions(Table table){
        table.top().left();
        table.add("@research.modules.evolving").color(Pal.accent).row();

        boolean any = false;
        if(state != null && state.isGame()){
            for(Building build : Groups.build){
                if(!build.evolving) continue;
                any = true;
                final Building b = build;
                Table row = new Table();
                row.left();
                row.add(b.block.localizedName).color(Color.lightGray).padRight(8f);
                row.add(new Bar(() -> Core.bundle.format("research.modules.evolving.to", b.evolveTarget),
                    () -> Pal.accent, () -> Math.max(0f, b.evolutionProgress()))).growX().height(18f).padRight(8f);
                table.add(row).growX().padBottom(3f).row();
            }
        }
        if(!any) table.add("@research.modules.evolving.none").color(Color.gray).row();
    }

    /** Cards are built on show/rebuild because they depend on live unlock and item state. */
    private @Nullable Table moduleGroup;

    /** Rebuilds the module cards from real moduleOnly blocks, grouped by type. */
    private void rebuildModules(){
        if(moduleGroup == null) return;
        moduleGroup.clearChildren();
        moduleGroup.top().left();

        //type -> blocks, only blocks that are real dorsal modules
        ObjectMap<String, Seq<Block>> byType = new ObjectMap<>();
        Seq<String> order = new Seq<>();
        for(Block block : content.blocks()){
            if(block == null || !block.moduleOnly) continue;
            String type = moduleType(block);
            Seq<Block> list = byType.get(type);
            if(list == null){ list = new Seq<>(); byType.put(type, list); order.add(type); }
            list.add(block);
        }

        for(String type : order){
            Seq<Block> blocks = byType.get(type);
            Table section = new Table();
            section.background(Tex.button);
            section.add(type).color(Pal.accent).padRight(8f);
            section.add(blocks.size + "/" + blocks.size).color(Color.lightGray);
            moduleGroup.add(section).growX().padBottom(4f).row();

            Table grid = new Table();
            grid.left();
            int col = 0;
            for(Block block : blocks){
                if(onlyUnlocked && !block.unlockedHost()) continue;
                TechNode node = block.techNode;
                if(onlyResearchable && (node == null || !canSpendNode(node) || !locked(node))) continue;
                //wider cards so the armour icons and long translated names always fit
                grid.add(moduleCard(block, node)).size(mobile ? 220f : 300f, mobile ? 76f : 88f).pad(4f);
                if(++col % (mobile ? 3 : 5) == 0) grid.row();
            }
            moduleGroup.add(grid).growX().row();
        }
    }

    /** @return the localized group name of a real module block. */
    private String moduleType(Block block){
        SupportModuleCategory support = SupportModuleCategory.of(block);
        if(support != null) return Core.bundle.get("support.category." + support.name(), support.name());
        if(block instanceof orbital.world.blocks.defense.turrets.Turret) return Core.bundle.get("research.modules.turrets");
        return Core.bundle.get("research.modules.armor");
    }

    /** One module card: icon, name and the real resource icons/amounts of its tech node. */
    private Table moduleCard(Block block, @Nullable TechNode node){
        //A fixed outer table clips its children, so no label or icon can paint past the border.
        //Its own background keeps the frame intact when the inner card is shorter than the cell.
        Table outer = new Table(Tex.button);
        outer.setClip(true);
        Table card = new Table(Tex.button);
        card.left();
        card.top();
        card.margin(4f);
        outer.add(card).grow();

        //icon + short name on one line; ellipsis keeps long translated names inside the card
        card.table(head -> {
            head.image(block.uiIcon).size(28f).padRight(6f).minWidth(28f);
            Label name = head.add(block.localizedName).color(Color.lightGray).ellipsis(true).get();
            name.setFontScale(0.85f);
            if(block.unlockedHost()) head.add("✓").color(Color.lightGray);
        }).growX().row();

        //bottom band: real resources on the left, research button on the right, both on one line
        card.table(bottom -> {
            bottom.left();
            if(node != null){
                Table reqs = new Table();
                reqs.left();
                int shown = 0;
                for(int i = 0; i < node.requirements.length && shown < 3; i++){
                    ItemStack req = node.requirements[i];
                    int need = Math.max(0, req.amount - node.finishedRequirements[i].amount);
                    if(onlyResearchable && need == 0) continue;
                    shown++;
                    reqs.image(req.item.uiIcon).size(14f).padRight(2f);
                    Label amount = reqs.add(UI.formatAmount(need)).color(need == 0 ? Color.lightGray : Color.scarlet).padRight(4f).get();
                    amount.setFontScale(0.8f);
                }
                bottom.add(reqs).left();
            }
            bottom.add().growX();
            if(!net.client() && node != null){
                //icon-only button with a tooltip, comfortably larger than the mouse pointer
                bottom.button(Icon.ok, Styles.clearNonei, () -> { view.spend(node); rebuildModules(); })
                    .disabled(b -> !canSpendNode(node) || !locked(node))
                    .tooltip("@research")
                    .size(mobile ? 54f : 64f, mobile ? 34f : 40f);
            }
        }).growX().padTop(2f);
        return outer;
    }

    private boolean canSpendNode(TechNode node){
        if(items == null || !selectable(node)) return false;
        if(node.requirements.length == 0) return true;
        for(int i = 0; i < node.requirements.length; i++){
            if(node.finishedRequirements[i].amount < node.requirements[i].amount && items.has(node.requirements[i].item)) return true;
        }
        return node.content.locked();
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
                    rootPlanets.add(Planets.qatia);
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

            //Grid of compact research cards: several per row so nothing is stretched thin.
            list.clear();
            list.top().left();
            list.defaults().left().padBottom(4f).padLeft(4f).padTop(4f);

            float cardWidth = mobile ? 200f : 240f;
            //The View may still report its old (small) width during the first rebuild, so size the
            //row count from the actual window width on desktop - otherwise only 3 cards fit and the
            //rest of the full-screen dialog is left empty.
            float available = mobile
                ? Math.max(getWidth(), 400f)
                : Core.graphics.getWidth() / Scl.scl(1f) - 60f;
            int perRow = Math.max(1, (int)Math.floor(available / (cardWidth + 8f)));

            int[] col = {0};
            lastNode.each(tech -> {
                TechTreeNode node = findNode(tech);
                if(node == null || !node.visible) return;
                list.add(buildListRow(node)).size(cardWidth, mobile ? 120f : 140f).pad(4f);
                if(++col[0] % perRow == 0) list.row();
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

        /** One compact research card, sized to sit in a grid beside its peers. */
        private Table buildListRow(TechTreeNode node){
            TechNode tech = node.node;
            boolean isLocked = locked(tech);
            boolean canSelect = selectable(tech);

            Table card = new Table(Tex.button);
            card.left();
            card.top();
            card.margin(4f);

            //icon + ellipsized name on the same line
            card.table(head -> {
                Drawable icon = canSelect ? new TextureRegionDrawable(tech.content.uiIcon) : Icon.lock;
                head.image(icon).size(24f).padRight(4f);
                Label name = head.add(canSelect ? tech.content.localizedName : "[accent]???")
                    .color(canSelect ? Color.white : Pal.gray).ellipsis(true).get();
                name.setFontScale(0.9f);
                if(!isLocked) head.add("@completed").color(Color.lightGray);
            }).growX().row();

            if(isLocked && !net.client()){
                //real resources: icon + have/need, kept compact and aligned
                card.table(reqs -> {
                    reqs.left();
                    boolean any = false;
                    if(tech.requirements.length > 0){
                        for(int i = 0; i < tech.requirements.length; i++){
                            ItemStack req = tech.requirements[i], done = tech.finishedRequirements[i];
                            if(req.amount <= done.amount && !debugShowRequirements) continue;
                            any = true;
                            int need = debugShowRequirements ? req.amount : req.amount - done.amount;
                            reqs.image(req.item.uiIcon).size(18f).padRight(2f);
                            Label amount = reqs.add(UI.formatAmount(Math.min(items == null ? 0 : items.get(req.item), need))
                                + "/" + UI.formatAmount(need)).color(Color.lightGray).padRight(6f).get();
                            amount.setFontScale(0.78f);
                        }
                    }else{
                        for(Objective o : tech.objectives){
                            if(o.complete()) continue;
                            any = true;
                            Label obj = reqs.add("> " + o.display()).color(Color.lightGray).get();
                            obj.setFontScale(0.78f);
                            break;
                        }
                    }
                    if(!any) reqs.add("@completed").color(Color.lightGray);
                }).growX().row();
            }

            //actions: info + a large research button, aligned side by side
            card.table(actions -> {
                actions.left();
                if(canSelect){
                    actions.button(Icon.info, Styles.flati, () -> ui.content.show(tech.content))
                        .size(mobile ? 40f : 52f).padRight(6f);
                }
                if(!net.client()){
                    actions.button("@research", Icon.ok, new TextButtonStyle(){{
                            disabled = Tex.button;
                            font = Fonts.def;
                            fontColor = Color.white;
                            disabledFontColor = Color.gray;
                            up = buttonOver;
                            over = buttonDown;
                        }}, () -> spend(tech))
                        .disabled(b -> !canSpend(tech) || !locked(tech))
                        .size(mobile ? 110f : 150f, mobile ? 40f : 50f);
                }
            }).growX().padTop(4f);

            return card;
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
