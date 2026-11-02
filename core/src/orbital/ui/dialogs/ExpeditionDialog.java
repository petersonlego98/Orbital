package orbital.ui.dialogs;

import arc.graphics.*;
import arc.scene.ui.*;
import arc.util.Align;
import orbital.game.ExpeditionClock;
import orbital.gen.*;
import orbital.type.Sector;
import orbital.ui.*;

import static orbital.Vars.*;

/** Entry screen for the timed asteroid expedition on Gier. */
public class ExpeditionDialog extends BaseDialog{
    private final Label status = new Label("", Styles.outlineLabel);
    private final Label timer = new Label("", Styles.outlineLabel);

    public ExpeditionDialog(){
        super("Expedição asteroidária");
        addCloseButton();

        cont.add("Uma janela de 30 minutos em tempo real em Gier. O prazo continua correndo com o jogo fechado; quando ele termina, a partida é encerrada e você volta ao mapa.")
            .width(480f).wrap().labelAlign(Align.center);
        cont.row();
        cont.add(status).width(480f).padTop(14f).labelAlign(Align.center);
        cont.row();
        cont.add(timer).width(480f).padTop(4f).labelAlign(Align.center);

        buttons.defaults().size(220f, 60f).pad(4f);
        buttons.button("Iniciar expedição", Icon.play, () -> {
            ExpeditionClock.start();
            leave();
        }).disabled(b -> ExpeditionClock.active());
        buttons.button("Entrar na expedição", Icon.play, this::leave).disabled(b -> !ExpeditionClock.active());
        buttons.row();
        buttons.button("Finalizar expedição", Icon.cancel, ExpeditionClock::finish).disabled(b -> !ExpeditionClock.active());

        shown(this::rebuild);
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
