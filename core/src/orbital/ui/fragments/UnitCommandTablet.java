package orbital.ui.fragments;

import arc.*;
import arc.func.*;
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
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import orbital.ai.*;
import orbital.ai.types.*;
import orbital.core.*;
import orbital.entities.units.*;
import orbital.gen.*;
import orbital.input.*;
import orbital.type.*;
import orbital.ui.*;
import orbital.ui.dialogs.*;
import orbital.world.*;
import orbital.world.blocks.storage.CoreBlock.*;

import static orbital.Vars.*;

/**
 * Square command tablet of the HUD.
 *
 * <p>It is a fixed-size, square board of demarcated tiles: one square per unit type, each with
 * its own health bar, its own order pad and a module button, plus the ALL square that sends one
 * order to everything. The board scrolls inside the square, so a battle with every unit type
 * never pushes the header off the screen. The frame is drawn in code (glass, corner brackets,
 * scan line and a pulsing link dot), so no extra textures are needed and it scales with the ui.</p>
 *
 * <p>Orders that need a point on the map (move, attack, patrol) arm the tablet together with the
 * group they belong to; the next tap outside the ui sends the order only to that group. A drag is
 * ignored, so panning the camera on mobile does not fire a command by accident.</p>
 */
public class UnitCommandTablet extends Table{
    /** Map orders that wait for a tap on the world. */
    public enum Order{
        move, attack, patrol
    }

    /** Cyan accent that sets the tablet apart from the orange default hud. */
    public static final Color accent = Color.valueOf("4ad7ff");
    private static final Color healthGood = Color.valueOf("6fe38a"),
        healthMid = Color.valueOf("ffd166"), healthLow = Color.valueOf("ff6b6b");

    private static final float maxSide = 640f;

    private final Table groupGrid = new Table();
    private final Table deckContent = new Table();
    private final Table footer = new Table();

    private @Nullable ScrollPane groupPane;

    private final Label status;
    private final Label hint;

    /** Every commandable unit owned by the player, refreshed every frame. */
    private final Seq<Unit> units = new Seq<>();
    /** Scratch list of ids for one group, reused by the actions. */
    private final IntSeq actionIds = new IntSeq();
    /** Types present among the player's units, in discovery order. */
    private final Seq<UnitType> present = new Seq<>();
    /** Types of the previous frame, so the group grid is only rebuilt when it really changes. */
    private final Seq<UnitType> lastPresent = new Seq<>();
    /** Per-type widgets that show live values. */
    private final ObjectMap<UnitType, GroupCard> cards = new ObjectMap<>();
    /** Widgets of the ALL tile, which is not a real unit type. */
    private @Nullable GroupCard allCard;
    /** Groups that are currently escorting the player. */
    private final Seq<UnitType> followGroups = new Seq<>();

    private @Nullable TurretLoadoutDialog loadout;
    /** Vertical space available to the expanded tablet, in ui units. */
    private float availableHeight = Float.NaN;
    /** Current logical side of the square tablet. */
    private float side = maxSide;
    private @Nullable Order pending;
    /** Group the armed order belongs to; null means the ALL section. */
    private @Nullable UnitType pendingGroup;
    private boolean expanded, followingAll, tapTracking;
    private float tapX, tapY, followTimer;

    public UnitCommandTablet(){
        background(Styles.black9);
        margin(0f);
        left();

        status = new Label("", Styles.monoLabel);
        hint = new Label("", Styles.monoLabel);

        buildLayout();
        update(this::tick);
    }

    //region layout

    /**
     * Calculates the square side from the screen width and the vertical space left by the other
     * hud panels. All values are logical ui units.
     */
    public static float calculateSide(float screenWidth, float availableHeight, float maxSide){
        if(Float.isNaN(screenWidth) || Float.isNaN(availableHeight)) return 0f;
        if(Float.isInfinite(screenWidth) || Float.isInfinite(availableHeight)) return 0f;

        float width = Math.max(0f, screenWidth - 16f);
        return Mathf.clamp(Math.min(width, Math.max(0f, availableHeight)), 0f, maxSide);
    }

    /** @return the current logical side of the square tablet. */
    private float tabletSide(){
        float scale = Math.max(0.01f, Scl.scl(1f));
        float width = (Core.scene == null ? 800f : Core.scene.getWidth()) / scale;
        float height = Float.isNaN(availableHeight)
            ? (Core.scene == null ? 800f : Core.scene.getHeight()) / scale
            : availableHeight;
        return calculateSide(width, height, maxSide);
    }

    /**
     * Sets the vertical space available to the expanded tablet, in ui units. The value already
     * excludes the build menu, test-tools panel and the gaps between them.
     */
    public void setAvailableHeight(float height){
        float next = Math.max(0f, height);
        availableHeight = next;

        if(refreshSide() && expanded) rebuildGroups();
    }

    /** Recalculates the cached side and invalidates every cached parent layout. */
    private boolean refreshSide(){
        float next = tabletSide();
        if(Math.abs(next - side) < 0.5f) return false;

        side = next;
        invalidateHierarchy();
        return true;
    }

    @Override
    public float getPrefWidth(){
        if(!expanded) return super.getPrefWidth();
        return Math.max(Scl.scl(side), super.getPrefWidth());
    }

    @Override
    public float getPrefHeight(){
        return expanded ? Scl.scl(side) : Math.max(Scl.scl(52f), super.getPrefHeight());
    }

    public boolean isExpanded(){
        return expanded;
    }

    /** Gap between the square tiles and the deck border. */
    private static final float tileGap = 8f;

    /** Fixed side of one square tile, so the board is made of equal squares. */
    private float tileSize(){
        int columns = tileColumns();
        return Math.max(0f, (deckInner() - columns * tileGap) / columns);
    }

    /** Usable width of the deck, without its side padding and the scrollbar gutter. */
    private float deckInner(){
        return Math.max(0f, side - 16f - 14f);
    }

    /** Number of square tiles per row, chosen so a tile stays readable. */
    private int tileColumns(){
        float inner = deckInner();
        float target = mobile ? 150f : 190f;
        int min = inner < 260f ? 1 : 2;
        return Mathf.clamp(Mathf.round(inner / target), min, 4);
    }

    /**
     * Builds the tablet for its current state. Collapsed it holds only the slim header, so its
     * preferred size really shrinks; expanded it adds the deck and the hint bar. The layout is
     * rebuilt on every toggle because a Table caches its cells: hiding them kept the old height
     * until something else forced the hud to be laid out again.
     */
    private void buildLayout(){
        clearChildren();
        left();

        Table header = new Table();
        header.left();

        header.add(new StatusDot()).size(12f).padLeft(12f).padRight(8f);

        Label title = header.add("@tablet.title").style(Styles.techLabel).color(accent).get();
        title.setFontScale(1.05f);

        header.add().growX();

        status.setColor(Color.lightGray);
        status.setFontScale(0.72f);
        header.add(status).padRight(6f);

        header.button(expanded ? Icon.upOpen : Icon.downOpen, Styles.clearNonei, this::toggle)
            .size(44f).padRight(6f).tooltip("@tablet.toggle");

        add(header).growX().height(48f).padTop(2f);

        if(expanded){
            row();

            Table body = new Table();
            body.left();
            groupPane = new ScrollPane(groupGrid, Styles.defaultPane);
            groupPane.setScrollingDisabled(true, false);
            groupPane.setFadeScrollBars(false);
            groupPane.setOverscroll(false, false);
            body.add(groupPane).grow().row();
            deckContent.clearChildren();
            deckContent.left();
            deckContent.add(body).grow().row();
            add(deckContent).grow().padLeft(8f).padRight(8f).padTop(4f).row();

            buildFooter();
            add(footer).growX().height(24f).padLeft(10f).padRight(10f).padBottom(2f);
        }else{
            groupPane = null;
        }

        invalidateHierarchy();
    }

    private void buildFooter(){
        footer.clearChildren();
        footer.left();
        hint.setFontScale(0.7f);
        hint.setColor(Color.lightGray);
        hint.setWrap(true);
        footer.add(hint).growX().padTop(2f);
    }

    private void toggle(){
        expanded = !expanded;
        refreshSide();

        if(expanded){
            rebuildContent();
        }else{
            pending = null;
            pendingGroup = null;
            tapTracking = false;
        }

        buildLayout();
        if(groupPane != null) groupPane.setScrollYForce(0f);

        if(expanded){
            deckContent.actions(Actions.alpha(0f), Actions.fadeIn(0.16f, Interp.fade));
        }
    }

    private void rebuildContent(){
        rebuildGroups();
    }

    /** Size of one order button inside a tile: a quarter of the tile width, with a floor. */
    private float tileIcon(float size){
        return Mathf.clamp((size - 32f) / 4f, 22f, 46f);
    }

    /** The ALL tile: one demarcated square that commands every unit at once. */
    private void buildAllTile(float size){
        GroupCard entry = new GroupCard();
        entry.hp = new BarElement();

        Tile tile = new Tile();
        tile.emphasized = true;
        tile.margin(6f);
        tile.top().left();

        tileHeader(tile, null, Core.bundle.get("tablet.filter.all"), size, entry);

        Table hpRow = new Table();
        hpRow.left();
        hpRow.add(entry.hp).height(4f).growX().padRight(5f);
        entry.hpText = hpRow.add("").style(Styles.monoLabel).color(Color.lightGray).get();
        entry.hpText.setFontScale(0.55f);
        tile.add(hpRow).growX().padTop(3f).row();

        Table orders = new Table();
        orders.left();
        addOrders(orders, null, size);
        tile.add(orders).growX().padTop(2f).row();

        groupGrid.add(tile).size(size).padRight(tileGap).padBottom(tileGap);

        allCard = entry;
    }

    /** Header of a square tile: icon (groups only), name, live count and the module button. */
    private void tileHeader(Table tile, @Nullable UnitType type, String title, float size, GroupCard entry){
        Table header = new Table();
        header.left();

        if(type != null){
            header.image(type.uiIcon).size(Mathf.clamp(size * 0.11f, 14f, 22f)).padRight(5f);
        }

        Label name = header.add(title).style(Styles.monoLabel).color(accent).get();
        name.setFontScale(Mathf.clamp(size * 0.0034f, 0.6f, 0.78f));

        header.add().growX();

        entry.count = header.add("").style(Styles.monoLabel).color(Color.lightGray).get();
        entry.count.setFontScale(Mathf.clamp(size * 0.0031f, 0.54f, 0.68f));

        if(type != null){
            header.add().width(3f);
            header.button(Icon.settings, Styles.clearNonei, () -> openModules(type))
                .size(Mathf.clamp(size * 0.13f, 18f, 26f)).tooltip("@tablet.modules");
        }

        tile.add(header).growX().row();
    }

    /** One 4x2 order pad; a null group means the ALL section. */
    private void addOrders(Table pad, @Nullable UnitType group, float size){
        float icon = tileIcon(size);

        compactOrder(pad, group, Icon.move, "@tablet.order.move", () -> armMove(group), b -> pending == Order.move && pendingGroup == group, b -> noUnits(group), icon);
        compactOrder(pad, group, Icon.commandAttack, "@tablet.order.attack", () -> armAttack(group), b -> pending == Order.attack && pendingGroup == group, b -> noUnits(group), icon);
        compactOrder(pad, group, Icon.refresh, "@tablet.order.patrol", () -> armPatrol(group), b -> pending == Order.patrol && pendingGroup == group, b -> noUnits(group), icon);
        compactOrder(pad, group, Icon.players, "@tablet.order.follow", () -> toggleFollow(group), b -> isFollowing(group), b -> noUnits(group), icon);
        pad.row();
        compactOrder(pad, group, Icon.pause, "@tablet.order.stop", () -> stopOrders(group), b -> false, b -> noUnits(group), icon);
        compactOrder(pad, group, Icon.lock, "@tablet.order.hold", () -> holdPosition(group), b -> allHolding(group), b -> noUnits(group), icon);
        compactOrder(pad, group, Icon.home, "@tablet.order.recall", () -> recall(group), b -> false, b -> noUnits(group), icon);
        compactOrder(pad, group, Icon.cancel, "@tablet.order.cancel", () -> cancelOrder(group), b -> pending != null && pendingGroup == group, b -> !(pending != null && pendingGroup == group) && !isFollowing(group), icon);
        pad.row();
        //construction mode: the group takes the rebuild command and executes the team's
        //queued build plans (see AutoBuildSync). Toggling off returns it to the move command,
        //whose command() call clears any plan the group was still holding.
        compactOrder(pad, group, Icon.hammer, "@tablet.order.build", () -> toggleConstruction(group), b -> allConstructing(group), b -> noBuilders(group), icon);
    }

    private void compactOrder(Table row, @Nullable UnitType group, Drawable icon, String tooltipKey, Runnable action, Boolf<ImageButton> checked, Boolf<ImageButton> disabled, float size){
        ImageButton button = row.button(icon, Styles.squareTogglei, action).size(size).pad(2f)
            .checked(checked).disabled(disabled).tooltip(tooltipKey).get();
        button.resizeImage(size * 0.7f);
    }

    /**
     * Rebuilds the board: one demarcated square per unit type, with the ALL square first. The
     * squares keep a fixed side, so the deck reads as a board of tiles and the scroll pane is the
     * only thing that grows with the unit roster.
     */
    private void rebuildGroups(){
        groupGrid.clearChildren();
        groupGrid.left();
        cards.clear();
        allCard = null;

        float size = tileSize();
        int columns = tileColumns();
        int index = 0;

        buildAllTile(size);
        index++;

        if(present.isEmpty()){
            groupGrid.row();

            Label none = groupGrid.add("@tablet.none").colspan(columns).color(Color.gray).pad(8f)
                .width(deckInner()).get();
            none.setFontScale(0.7f);
            return;
        }

        for(UnitType type : present){
            buildGroupTile(type, size);
            if(++index % columns == 0) groupGrid.row();
        }
    }

    private void buildGroupTile(UnitType type, float size){
        GroupCard entry = new GroupCard();
        entry.hp = new BarElement();

        Tile tile = new Tile();
        tile.margin(6f);
        tile.top().left();

        tileHeader(tile, type, type.localizedName, size, entry);

        Table hpRow = new Table();
        hpRow.left();
        hpRow.add(entry.hp).height(4f).growX().padRight(5f);
        entry.hpText = hpRow.add("").style(Styles.monoLabel).color(Color.lightGray).get();
        entry.hpText.setFontScale(0.55f);
        tile.add(hpRow).growX().padTop(3f).row();

        Table orders = new Table();
        orders.left();
        addOrders(orders, type, size);
        tile.add(orders).growX().padTop(2f).row();

        groupGrid.add(tile).size(size).padRight(tileGap).padBottom(tileGap);

        cards.put(type, entry);
    }

    //endregion

    //region state

    private boolean noUnits(@Nullable UnitType group){
        if(group == null) return units.isEmpty();
        for(Unit unit : units){
            if(unit.type == group) return false;
        }
        return true;
    }

    private boolean allHolding(@Nullable UnitType group){
        boolean any = false;
        for(Unit unit : units){
            if(group != null && unit.type != group) continue;
            any = true;
            if(!(unit.controller() instanceof CommandAI ai) || !ai.hasStance(UnitStance.holdPosition)) return false;
        }
        return any;
    }

    /** @return whether at least one unit of this group can take the rebuild command. */
    private boolean noBuilders(@Nullable UnitType group){
        for(Unit unit : units){
            if(group != null && unit.type != group) continue;
            if(unit.type.allowCommand(unit, UnitCommand.rebuildCommand)) return false;
        }
        return true;
    }

    /** @return whether every builder-capable unit of this group is already in construction mode. */
    private boolean allConstructing(@Nullable UnitType group){
        boolean any = false;
        for(Unit unit : units){
            if(group != null && unit.type != group) continue;
            if(!unit.type.allowCommand(unit, UnitCommand.rebuildCommand)) continue;
            any = true;
            if(!(unit.controller() instanceof CommandAI ai) || ai.currentCommand() != UnitCommand.rebuildCommand) return false;
        }
        return any;
    }

    private boolean isFollowing(@Nullable UnitType group){
        return group == null ? followingAll : followingAll || followGroups.contains(group);
    }

    private int countOf(@Nullable UnitType group){
        if(group == null) return units.size;
        int count = 0;
        for(Unit unit : units){
            if(unit.type == group) count++;
        }
        return count;
    }

    private float averageHealth(@Nullable UnitType group){
        float sum = 0f;
        int count = 0;

        for(Unit unit : units){
            if(group != null && unit.type != group) continue;
            if(unit.maxHealth <= 0f) continue;
            sum += Mathf.clamp(unit.health / unit.maxHealth);
            count++;
        }

        return count == 0 ? 0f : sum / count;
    }

    private static Color healthColor(float fraction){
        if(fraction > 0.6f) return healthGood;
        if(fraction > 0.3f) return healthMid;
        return healthLow;
    }

    /** Rebuilds the current view of the player's units and of the type groups. */
    private void collect(){
        units.clear();
        present.clear();

        Unit self = player.unit();

        for(Unit unit : Groups.unit){
            if(unit.team != player.team() || unit == self || !unit.isCommandable()) continue;

            if(!present.contains(unit.type)) present.add(unit.type);

            units.add(unit);
        }

        boolean changed = present.size != lastPresent.size;
        if(!changed){
            for(UnitType type : present){
                if(!lastPresent.contains(type)){
                    changed = true;
                    break;
                }
            }
        }

        if(changed){
            syncPresent();
        }

        //a group that disappeared must not keep following or an armed order
        if(followingAll && present.isEmpty()) followingAll = false;
        followGroups.removeAll(type -> !present.contains(type));
        if(pendingGroup != null && !present.contains(pendingGroup)){
            pending = null;
            pendingGroup = null;
        }
    }

    private void syncPresent(){
        lastPresent.clear();
        lastPresent.addAll(present);

        if(expanded){
            rebuildGroups();
        }
    }

    private void tick(){
        if(state == null || !state.isGame()) return;

        collect();

        status.setText(Core.bundle.format("tablet.status", units.size, present.size));

        if(expanded){
            //a group that appeared after the last rebuild must not stay invisible
            if(cards.size != present.size) rebuildGroups();
            updateGroups();
        }

        if(followingAll || followGroups.size > 0){
            followTimer -= Time.delta;
            if(followTimer <= 0f){
                followTimer = 45f;
                Vec2 anchor = anchor();
                if(anchor != null){
                    if(followingAll){
                        sendMove(idsFor(null), anchor);
                    }else{
                        for(UnitType type : followGroups){
                            sendMove(idsFor(type), anchor);
                        }
                    }
                }
            }
        }

        if(pending != null){
            handleTap();
        }

        updateHint();
    }

    private void updateGroups(){
        if(allCard != null){
            updateCard(allCard, null);
        }

        for(ObjectMap.Entry<UnitType, GroupCard> entry : cards){
            updateCard(entry.value, entry.key);
        }
    }

    private void updateCard(GroupCard card, @Nullable UnitType group){
        card.count.setText("×" + countOf(group));

        float fraction = averageHealth(group);
        card.hp.fraction = fraction;
        card.hp.color = healthColor(fraction);
        card.hpText.setText(Mathf.round(fraction * 100f) + "%");
    }

    private void updateHint(){
        if(pending != null){
            hint.setColor(accent);
            hint.setText(groupName(pendingGroup) + " — " + Core.bundle.get("tablet.hint." + pending.name()));
        }else if(followingAll || followGroups.size > 0){
            hint.setColor(accent);
            hint.setText(Core.bundle.get("tablet.follow.on"));
        }else if(units.isEmpty()){
            hint.setColor(Color.gray);
            hint.setText(Core.bundle.get("tablet.none"));
        }else{
            hint.setColor(Color.lightGray);
            hint.setText(Core.bundle.get("tablet.idle"));
        }
    }

    private String groupName(@Nullable UnitType group){
        return group == null ? Core.bundle.get("tablet.filter.all") : group.localizedName;
    }

    /** Opens the regular loadout editor for a live unit of this group. */
    private void openModules(UnitType type){
        Unit pick = representative(type);
        if(pick == null){
            ui.showInfoToast("@tablet.none", 2f);
            return;
        }

        if(loadout == null){
            loadout = new TurretLoadoutDialog();
        }
        loadout.show(pick, () -> {});
    }

    private @Nullable Unit representative(UnitType type){
        for(Unit unit : units){
            if(unit.type == type && unit.isValid()) return unit;
        }
        return null;
    }

    /** @return the ids that belong to one group, or to every group when {@code group} is null. */
    private IntSeq idsFor(@Nullable UnitType group){
        actionIds.clear();
        for(Unit unit : units){
            if(group == null || unit.type == group){
                actionIds.add(unit.id);
            }
        }
        return actionIds;
    }

    /** @return only the ids that can actually take the rebuild command. */
    private IntSeq builderIdsFor(@Nullable UnitType group){
        actionIds.clear();
        for(Unit unit : units){
            if(group == null || unit.type == group){
                if(unit.type.allowCommand(unit, UnitCommand.rebuildCommand)) actionIds.add(unit.id);
            }
        }
        return actionIds;
    }

    /** Removes a group (or every group) from the escort list without touching unit stances. */
    private void clearFollow(@Nullable UnitType group){
        if(group == null){
            followingAll = false;
            followGroups.clear();
        }else if(followingAll){
            //turning follow off for one group converts the global escort into per-group escorts
            followingAll = false;
            followGroups.clear();
            followGroups.addAll(present);
            followGroups.remove(group);
        }else{
            followGroups.remove(group);
        }
    }

    //endregion

    //region orders

    private void armMove(@Nullable UnitType group){
        if(!prepareArm(group)) return;
        boolean same = pending == Order.move && pendingGroup == group;
        pending = same ? null : Order.move;
        pendingGroup = same ? null : group;
    }

    private void armAttack(@Nullable UnitType group){
        if(!prepareArm(group)) return;
        boolean same = pending == Order.attack && pendingGroup == group;
        pending = same ? null : Order.attack;
        pendingGroup = same ? null : group;
    }

    private void armPatrol(@Nullable UnitType group){
        if(!prepareArm(group)) return;

        if(pending == Order.patrol && pendingGroup == group){
            pending = null;
            pendingGroup = null;
            return;
        }

        IntSeq ids = idsFor(group);
        InputHandler.setUnitStanceChunked(ids, UnitStance.holdPosition, false);
        InputHandler.setUnitStanceChunked(ids, UnitStance.pursueTarget, true);
        InputHandler.setUnitStanceChunked(ids, UnitStance.patrol, true);
        pending = Order.patrol;
        pendingGroup = group;
    }

    private boolean prepareArm(@Nullable UnitType group){
        collect();
        clearFollow(group);

        if(noUnits(group)){
            ui.showInfoToast("@tablet.none", 2f);
            return false;
        }

        return true;
    }

    private void stopOrders(@Nullable UnitType group){
        collect();
        IntSeq ids = idsFor(group);
        if(ids.isEmpty()) return;

        InputHandler.setUnitStanceChunked(ids, UnitStance.stop, false);
        clearFollow(group);
        if(pendingGroup == group){
            pending = null;
            pendingGroup = null;
        }
        ui.showInfoToast("@tablet.sent.stop", 2f);
    }

    private void holdPosition(@Nullable UnitType group){
        collect();
        IntSeq ids = idsFor(group);
        if(ids.isEmpty()) return;

        InputHandler.setUnitStanceChunked(ids, UnitStance.stop, false);
        InputHandler.setUnitStanceChunked(ids, UnitStance.patrol, false);
        InputHandler.setUnitStanceChunked(ids, UnitStance.pursueTarget, false);
        InputHandler.setUnitStanceChunked(ids, UnitStance.holdPosition, true);
        clearFollow(group);
        if(pendingGroup == group){
            pending = null;
            pendingGroup = null;
        }
        ui.showInfoToast("@tablet.sent.hold", 2f);
    }

    private void toggleFollow(@Nullable UnitType group){
        collect();
        if(noUnits(group)){
            ui.showInfoToast("@tablet.none", 2f);
            return;
        }

        boolean on = !isFollowing(group);
        IntSeq ids = idsFor(group);

        if(on){
            InputHandler.setUnitStanceChunked(ids, UnitStance.patrol, false);
            InputHandler.setUnitStanceChunked(ids, UnitStance.holdPosition, false);
            if(group == null){
                followingAll = true;
                followGroups.clear();
            }else{
                if(followingAll){
                    followingAll = false;
                    followGroups.clear();
                    followGroups.addAll(present);
                }
                followGroups.addUnique(group);
            }
            followTimer = 0f;
            ui.showInfoToast("@tablet.sent.follow", 2f);
        }else{
            clearFollow(group);
            InputHandler.setUnitStanceChunked(ids, UnitStance.stop, false);
        }
        if(pendingGroup == group){
            pending = null;
            pendingGroup = null;
        }
    }

    /**
     * Puts a group into construction mode (rebuild command) or takes it out again (move
     * command). Only builder-capable units are sent; a group without any of them never gets
     * here because its button is disabled.
     */
    private void toggleConstruction(@Nullable UnitType group){
        collect();
        if(noBuilders(group)){
            ui.showInfoToast("@tablet.build.none", 2f);
            return;
        }

        boolean on = !allConstructing(group);
        InputHandler.setUnitCommandChunked(builderIdsFor(group), on ? UnitCommand.rebuildCommand : UnitCommand.moveCommand);

        //escort orders would keep sending move destinations every 45 frames and silently
        //drag the group away from its construction work
        clearFollow(group);
        if(pendingGroup == group){
            pending = null;
            pendingGroup = null;
        }

        if(on){
            //plans queued before the button was pressed must be picked up now, not after
            //the rebuild timer (up to 120s) expires
            AutoBuildSync.pingBuildersNow(player.team());
        }
        ui.showInfoToast(Core.bundle.get(on ? "tablet.sent.build" : "tablet.build.off"), 2f);
    }

    private void recall(@Nullable UnitType group){
        Vec2 anchor = anchor();
        if(anchor == null){
            ui.showInfoToast("@tablet.noAnchor", 2f);
            return;
        }

        collect();
        IntSeq ids = idsFor(group);
        if(ids.isEmpty()){
            ui.showInfoToast("@tablet.none", 2f);
            return;
        }

        clearFollow(group);
        sendMove(ids, anchor);
        if(pendingGroup == group){
            pending = null;
            pendingGroup = null;
        }
        ui.showInfoToast("@tablet.sent.recall", 2f);
    }

    private void cancelOrder(@Nullable UnitType group){
        if(pending != null && pendingGroup == group){
            pending = null;
            pendingGroup = null;
        }
        if(isFollowing(group)){
            collect();
            clearFollow(group);
            InputHandler.setUnitStanceChunked(idsFor(group), UnitStance.stop, false);
        }
    }

    /** @return the point the units should return to: the player, or the last core when dead. */
    private @Nullable Vec2 anchor(){
        Unit self = player.unit();
        if(self != null && !player.dead()) return new Vec2(self.x, self.y);

        Building core = player.core();
        return core == null ? null : new Vec2(core.x, core.y);
    }

    private void handleTap(){
        float x = Core.input.mouseX(), y = Core.input.mouseY();

        if(Core.scene.hasDialog() || Core.scene.hasKeyboard() || Core.scene.hasMouse(x, y)){
            tapTracking = false;
            return;
        }

        if(Core.input.keyTap(KeyCode.escape)){
            pending = null;
            pendingGroup = null;
            tapTracking = false;
            return;
        }

        if(Core.input.isTouched()){
            if(!tapTracking){
                tapTracking = true;
                tapX = x;
                tapY = y;
            }
        }else if(tapTracking){
            tapTracking = false;

            //a drag is the camera, only a short tap is an order
            float limit = Scl.scl(14f);
            if(Mathf.dst2(tapX, tapY, x, y) <= limit * limit){
                execute(Core.input.mouseWorld(x, y).cpy());
            }
        }
    }

    private void execute(Vec2 pos){
        Order order = pending;
        @Nullable UnitType group = pendingGroup;
        pending = null;
        pendingGroup = null;

        IntSeq targets = idsFor(group);
        if(targets.isEmpty()){
            ui.showInfoToast("@tablet.none", 2f);
            return;
        }

        if(order == Order.move){
            sendMove(targets, pos);
            ui.showInfoToast("@tablet.sent.move", 2f);
        }else if(order == Order.patrol){
            sendMove(targets, pos);
            ui.showInfoToast("@tablet.sent.patrol", 2f);
        }else if(order == Order.attack){
            Building build = world.buildWorld(pos.x, pos.y);
            if(build != null && build.team != player.team() && build.isValid()){
                Call.commandUnits(player, targets.toArray(), build, null, pos, false, true);
                ui.showInfoToast("@tablet.sent.attack", 2f);
                return;
            }

            Unit target = control.input.selectedEnemyUnit(pos.x, pos.y);
            if(target != null){
                Call.commandUnits(player, targets.toArray(), null, target, pos, false, true);
                ui.showInfoToast("@tablet.sent.attack", 2f);
            }else{
                ui.showInfoToast("@tablet.noTarget", 2f);
            }
        }
    }

    private void sendMove(IntSeq ids, Vec2 pos){
        if(ids.isEmpty()) return;
        InputHandler.setUnitStanceChunked(ids, UnitStance.holdPosition, false);
        Call.commandUnits(player, ids.toArray(), null, null, pos, false, true);
    }

    //endregion

    //region drawing

    @Override
    public void draw(){
        super.draw();

        float x = this.x, y = this.y, w = getWidth(), h = getHeight();

        //glass tint and outer frame
        Draw.color(accent, 0.05f);
        Fill.crect(x, y, w, h);

        Draw.color(accent, 0.45f);
        Lines.stroke(Scl.scl(1f));
        Lines.rect(x + 0.5f, y + 0.5f, w - 1f, h - 1f);

        //corner brackets
        float c = Scl.scl(14f);
        Draw.color(accent, 0.95f);
        Lines.stroke(Scl.scl(2f));

        Lines.line(x, y + h, x + c, y + h);
        Lines.line(x, y + h, x, y + h - c);

        Lines.line(x + w, y + h, x + w - c, y + h);
        Lines.line(x + w, y + h, x + w, y + h - c);

        Lines.line(x, y, x + c, y);
        Lines.line(x, y, x, y + c);

        Lines.line(x + w, y, x + w - c, y);
        Lines.line(x + w, y, x + w, y + c);

        //slow scan line, like a screen refresh
        float sy = y + Mathf.mod(Time.time * Scl.scl(16f), Math.max(1f, h));
        Draw.color(accent, 0.06f);
        Lines.stroke(Scl.scl(1f));
        Lines.line(x + Scl.scl(3f), sy, x + w - Scl.scl(3f), sy);

        Draw.reset();
    }

    /** Pulsing link indicator: bright when there is something to command. */
    private class StatusDot extends Element{
        @Override
        public void draw(){
            float cx = x + width / 2f, cy = y + height / 2f;
            float radius = Math.min(width, height) / 2f;
            boolean online = !units.isEmpty();

            Color color = online ? accent : Color.gray;

            Draw.color(color, 0.22f + Mathf.absin(Time.time, 5f, 0.16f));
            Fill.circle(cx, cy, radius);

            Draw.color(color, online ? 1f : 0.6f);
            Fill.circle(cx, cy, radius * 0.45f);

            Draw.reset();
        }
    }

    /** Flat fill bar used by the health displays. */
    private static class BarElement extends Element{
        float fraction;
        Color color = Color.white;

        @Override
        public void draw(){
            Draw.color(Color.black, 0.5f);
            Fill.crect(x, y, width, height);

            Draw.color(color);
            Fill.crect(x, y, width * Mathf.clamp(fraction), height);

            Draw.reset();
        }
    }

    /**
     * A square, demarcated tile of the board: dark glass, a cyan frame, a thin top edge and two
     * corner ticks. The frame is drawn after the children, just inside the border, so the contents
     * (which keep a 6px margin) never touch it.
     */
    private static class Tile extends Table{
        boolean emphasized;

        Tile(){
            background(Styles.black6);
        }

        @Override
        public void draw(){
            super.draw();

            float x = this.x, y = this.y, w = getWidth(), h = getHeight();

            Draw.color(accent, emphasized ? 0.85f : 0.4f);
            Lines.stroke(Scl.scl(1.2f));
            Lines.rect(x + 0.5f, y + 0.5f, w - 1f, h - 1f);

            //top edge reads as a screen title bar
            Draw.color(accent, emphasized ? 0.32f : 0.14f);
            Fill.crect(x + 1f, y + h - Scl.scl(2f), w - 2f, Scl.scl(2f));

            float c = Scl.scl(9f);
            Draw.color(accent, emphasized ? 0.95f : 0.55f);
            Lines.stroke(Scl.scl(1.6f));

            Lines.line(x, y + h, x + c, y + h);
            Lines.line(x, y + h, x, y + h - c);

            Lines.line(x + w, y, x + w - c, y);
            Lines.line(x + w, y, x + w, y + c);

            Draw.reset();
        }
    }

    /** Live widgets of one group card. */
    private static class GroupCard{
        Label count, hpText;
        BarElement hp;
    }

    //endregion
}
