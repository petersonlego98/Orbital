package orbital.ui.dialogs;

import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.Align;
import orbital.core.*;
import orbital.game.*;
import orbital.gen.*;
import orbital.graphics.*;
import orbital.type.*;
import orbital.ui.*;

import static orbital.Vars.*;

/** Entry screen for the timed asteroid expedition on Gier. */
public class ExpeditionDialog extends BaseDialog{
    private final Label status = new Label("", Styles.outlineLabel);
    private final Label timer = new Label("", Styles.outlineLabel);
    private final Table globalItems = new Table();
    private final Table cargoItems = new Table();

    public ExpeditionDialog(){
        super("Expedição asteroidária");
        addCloseButton();

        int minutes = (int)(ExpeditionClock.durationMillis() / 60_000L);

        cont.add("Uma janela de " + minutes + " minutos em tempo real em Gier. O prazo continua correndo com o jogo fechado; quando ele termina, a partida é encerrada e você volta ao mapa.")
            .width(520f).wrap().labelAlign(Align.center);
        cont.row();
        cont.add(status).width(520f).padTop(14f).labelAlign(Align.center);
        cont.row();
        cont.add(timer).width(520f).padTop(4f).labelAlign(Align.center);
        cont.row();
        cont.table(Styles.black6, t -> {
            t.margin(10f).left();
            t.add("Inventário global").color(Pal.accent).left();
            t.row();
            t.add(globalItems).left().padTop(6f);
        }).width(520f).padTop(14f).left();
        cont.row();
        cont.table(Styles.black6, t -> {
            t.margin(10f).left();
            t.add("Carga no núcleo de Gier").color(Pal.accent).left();
            t.row();
            t.add(cargoItems).left().padTop(6f);
            t.row();
            t.add("Durante a expedição, recolha a carga pelo botão ao lado do relógio no HUD.").color(Color.lightGray).left().padTop(6f);
        }).width(520f).padTop(8f).left();

        buttons.defaults().size(220f, 60f).pad(4f);
        buttons.button("Iniciar expedição", Icon.play, () -> {
            ExpeditionClock.start();
            leave();
        }).disabled(b -> ExpeditionClock.active());
        buttons.button("Entrar na expedição", Icon.play, this::leave)
            .disabled(b -> !ExpeditionClock.active() || ExpeditionClock.inExpedition());
        buttons.row();
        buttons.button("Recolher carga", Icon.download, this::collectCargo)
            .disabled(b -> !ExpeditionClock.hasCargo())
            .tooltip("Entre em Gier e mantenha recursos no núcleo para recolher.");
        buttons.button("Finalizar expedição", Icon.cancel, this::finishExpedition)
            .disabled(b -> !ExpeditionClock.active());

        shown(() -> {
            rebuildItems();
            rebuild();
        });
        update(this::rebuild);
    }

    private void rebuild(){
        Sector sector = ExpeditionClock.sector();
        boolean active = ExpeditionClock.active();
        boolean hasBase = sector != null && sector.hasBase();

        if(active){
            status.setText(hasBase ? "Base em Gier: continuar a última base." : "Base em Gier: nenhuma ainda.");
            timer.setText("Tempo restante: " + ExpeditionClock.formattedRemaining());
        }else{
            status.setText(ExpeditionClock.lastWithdrawn ? "A última expedição terminou por prazo vencido." : "Nenhuma expedição ativa.");
            timer.setText("Inicie uma expedição para abrir Gier.");
        }

        timer.setColor(ExpeditionClock.lowTime() ? Color.scarlet : Color.white);
    }

    /** Rebuilds both item lists; called when the dialog is shown and after a manual collection. */
    private void rebuildItems(){
        buildItemList(globalItems, GlobalInventory.items());

        Sector sector = ExpeditionClock.sector();
        buildItemList(cargoItems, sector == null ? new ItemSeq() : sector.items());
    }

    private void buildItemList(Table table, ItemSeq stacks){
        table.clear();
        table.top().left();

        if(stacks == null || stacks.total <= 0){
            table.add("Nenhum recurso").color(Color.lightGray).left();
            return;
        }

        int i = 0;
        for(ItemStack stack : stacks){
            table.image(stack.item.uiIcon).size(iconSmall).padRight(4f);
            table.add(UI.formatAmount(stack.amount)).minWidth(48f).left();
            table.add(stack.item.localizedName).color(Color.lightGray).left().padRight(14f);

            if(++i % 2 == 0){
                table.row();
            }
        }
    }

    private void collectCargo(){
        ItemSeq collected = ExpeditionClock.collectCargo();

        if(collected.total <= 0){
            ui.showInfoToast("Nenhuma carga no núcleo de Gier.", 4f);
        }else{
            ui.showInfoToast("Carga recolhida: " + GlobalInventory.format(collected), 6f);
        }

        rebuildItems();
        rebuild();
    }

    private void finishExpedition(){
        Sector sector = ExpeditionClock.sector();
        boolean cargoWaiting = sector != null && sector.items().total > 0;

        if(!ExpeditionClock.inExpedition() && cargoWaiting){
            ui.showConfirm("Finalizar expedição",
                "A carga ainda está na base de Gier e permanecerá lá. Entre na expedição e use \"Recolher carga\" antes de finalizar.\n\nFinalizar mesmo assim?",
                ExpeditionClock::finish);
        }else{
            ExpeditionClock.finish();
        }
    }

    /** Leaves to the expedition sector without showing the whole planet map first. */
    private void leave(){
        hide();

        Sector sector = ExpeditionClock.sector();
        if(sector == null) return;

        PlanetDialog dialog = ui.planet;
        dialog.mode = PlanetDialog.Mode.look;
        dialog.launchSector = null;
        dialog.selectSector(sector);
        dialog.playSelected();
    }
}
