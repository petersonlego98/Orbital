package orbital.game;

import arc.*;
import arc.util.*;
import orbital.content.Planets;
import orbital.type.*;

import static orbital.Vars.*;

/**
 * Real-time clock for the asteroid expedition on Gier.
 * The deadline is stored in the global settings, so it keeps counting while the game is closed.
 * When it expires during a run, the player is forced back to the planet map.
 */
public final class ExpeditionClock{
    /** Default prototype expedition length: 30 minutes. */
    public static final int defaultDurationMinutes = 30;
    /** Real time the player sees the expiry warning before being withdrawn. */
    public static final long withdrawDelayMillis = 2_500L;

    /** Remaining time (in milliseconds) thresholds at which the player is warned. */
    private static final long[] warnThresholds = {10L * 60_000L, 5L * 60_000L, 60_000L};
    private static final long warnColorMillis = 5L * 60_000L;

    private static final String startedAtKey = "expedition-started-at";

    /** True if the last expedition ended because its deadline expired. */
    public static boolean lastWithdrawn;
    /** Wall-clock time at which the current withdrawal started, or 0 if no withdrawal is in progress. */
    private static long withdrawStartedAt;
    /** Last warning threshold already shown in this run. */
    private static long lastWarning = Long.MAX_VALUE;
    /** Whether the clock is being observed inside the expedition sector. */
    private static boolean tracked;
    /** Whether the withdrawal is already scheduled; prevents posting the leave sequence twice. */
    private static boolean leaving;

    private ExpeditionClock(){
    }

    /** @return the expedition sector, or null if content is not loaded yet. */
    public static @Nullable Sector sector(){
        return Planets.gier == null || Planets.gier.sectors.isEmpty() ? null : Planets.gier.sectors.first();
    }

    public static boolean isExpedition(@Nullable Sector sector){
        return sector != null && sector == sector();
    }

    /** @return whether the player is currently inside the expedition sector. */
    public static boolean inExpedition(){
        Sector sector = sector();
        return sector != null && state.isGame() && state.getSector() == sector;
    }

    public static boolean active(){
        return remainingMillis() > 0L;
    }

    /** @return expedition length in milliseconds; the settings key exists so the withdrawal can be tested quickly. */
    public static long durationMillis(){
        int minutes = Core.settings.getInt("expedition-duration-minutes", defaultDurationMinutes);
        return Math.max(1, minutes) * 60_000L;
    }

    /** @return remaining time in milliseconds; 0 if no expedition is active. */
    public static long remainingMillis(){
        long startedAt = Core.settings.getLong(startedAtKey, 0L);
        if(startedAt <= 0L) return 0L;
        return Math.max(0L, startedAt + durationMillis() - System.currentTimeMillis());
    }

    /** @return whether the remaining time should be displayed as a warning. */
    public static boolean lowTime(){
        return active() && remainingMillis() <= warnColorMillis;
    }

    /** @return the remaining time formatted as MM:SS. */
    public static String formattedRemaining(){
        long remaining = remainingMillis();
        long minutes = remaining / 60_000L;
        long seconds = (remaining % 60_000L) / 1_000L;
        return (minutes < 10L ? "0" : "") + minutes + ":" + (seconds < 10L ? "0" : "") + seconds;
    }

    /** Starts (or restarts) the expedition clock. */
    public static void start(){
        Core.settings.put(startedAtKey, System.currentTimeMillis());
        lastWarning = Long.MAX_VALUE;
        tracked = false;
        lastWithdrawn = false;
        withdrawStartedAt = 0L;
        leaving = false;
    }

    /** Ends the expedition; if the player is inside, the run is closed and they are withdrawn. */
    public static void finish(){
        Core.settings.remove(startedAtKey);
        lastWarning = Long.MAX_VALUE;
        tracked = false;

        if(inExpedition()){
            beginWithdrawal(false);
        }else{
            lastWithdrawn = false;
            withdrawStartedAt = 0L;
        }
    }

    /** @return whether the expedition core currently holds items that can be collected. */
    public static boolean hasCargo(){
        if(!inExpedition()) return false;

        var core = state.rules.defaultTeam.core();
        return core != null && core.items.any();
    }

    /** Moves the expedition core contents into the global inventory and saves the sector immediately. */
    public static ItemSeq collectCargo(){
        return collectCargo(true);
    }

    /**
     * Moves the expedition core contents into the global inventory.
     * @param saveSector whether to write the sector save after draining the core.
     * @return the items that were collected.
     */
    public static ItemSeq collectCargo(boolean saveSector){
        if(!inExpedition()) return new ItemSeq();

        Sector sector = sector();
        if(sector == null) return new ItemSeq();

        ItemSeq collected = GlobalInventory.collectFromCore();
        if(collected.total > 0 && saveSector){
            saveCurrentSector(sector);
        }
        return collected;
    }

    /** Saves the sector that is currently being played. */
    private static void saveCurrentSector(Sector sector){
        try{
            if(control.saves.getCurrent() != null){
                control.saves.getCurrent().save();
            }else{
                //should not happen mid-run, but never lose the base because of it
                control.saves.saveSector(sector);
            }
        }catch(Throwable e){
            Log.err(e);
        }
    }

    /** Called every frame by the UI; handles the warnings and the forced withdrawal. */
    public static void update(){
        if(ui == null || headless) return;
        //the leave sequence is already queued for the end of this frame
        if(leaving) return;

        //uses wall-clock time, so the withdrawal also works while the game is paused or loading
        if(withdrawStartedAt > 0L){
            if(System.currentTimeMillis() - withdrawStartedAt >= withdrawDelayMillis){
                leaveSector();
            }
            return;
        }

        if(!inExpedition() || state.gameOver){
            tracked = false;
            return;
        }

        long remaining = remainingMillis();

        if(remaining <= 0L){
            beginWithdrawal(true);
            return;
        }

        if(!tracked){
            tracked = true;
            lastWarning = remaining;
        }

        for(long threshold : warnThresholds){
            if(remaining <= threshold && lastWarning > threshold){
                lastWarning = threshold;
                ui.showInfoToast("Expedição: " + formattedRemaining() + " restantes", 6f);
                break;
            }
        }
    }

    private static void beginWithdrawal(boolean expired){
        if(withdrawStartedAt > 0L) return;

        withdrawStartedAt = System.currentTimeMillis();
        if(expired) lastWithdrawn = true;

        ui.announce(expired ? "Prazo da expedição encerrado.\nRetirada forçada de Gier." : "Expedição encerrada.", 3f);
    }

    private static void leaveSector(){
        if(leaving) return;

        withdrawStartedAt = 0L;
        tracked = false;

        Sector sector = sector();
        if(sector == null || !inExpedition()) return;

        leaving = true;

        Core.app.post(() -> {
            if(inExpedition()){
                //drain the core into the global inventory before saving, so the cargo is not duplicated on reload
                collectCargo(false);
                saveCurrentSector(sector);
            }

            //these dialogs belong to the run that is being closed
            ui.paused.hide();
            ui.restart.hide();

            logic.reset();

            //point the planet map at Gier before showing it, so no stale planet is used
            ui.planet.viewPlanet(Planets.gier, false);
            ui.planet.show();
            ui.planet.selectSector(sector);
            ui.expedition.show();

            leaving = false;
        });
    }
}
