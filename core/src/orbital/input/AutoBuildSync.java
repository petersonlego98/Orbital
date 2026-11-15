package orbital.input;

import arc.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import orbital.ai.*;
import orbital.ai.types.*;
import orbital.entities.units.*;
import orbital.game.*;
import orbital.game.Teams.*;
import orbital.gen.*;
import orbital.world.*;

import static orbital.Vars.*;

/**
 * Encaminhamento dos planos do jogador para as unidades construtoras — o modo "as unidades
 * constroem" do menu Construções nas configurações.
 *
 * <p>Com a chave {@code autobuild} ligada (padrão), cada plano que o jogador coloca na fila
 * da própria unidade chega aqui por {@link orbital.entities.comp.BuilderComp#addBuild(BuildPlan, boolean)}
 * e ganha dois destinos:</p>
 *
 * <ul>
 * <li><b>Colocação</b>: um {@link BlockPlan} entra na frente da fila de planos do time
 * ({@code team.data().plans}), de onde as unidades com {@link BuilderAI} e {@link PrebuildAI}
 * já sabem pegar — e as construtoras do time são "pingadas" para pegar no próximo frame
 * em vez de esperar o temporizador de reconstrução.</li>
 * <li><b>Demolição</b>: a fila do time não tem conceito de plano de quebra
 * ({@link BlockPlan} só descreve colocação), então o plano de quebra vai direto para a
 * unidade construtora disponível mais rápida ({@code buildSpeed * buildSpeedMultiplier}),
 * com empate resolvido por distância. Se nenhuma construtora estiver disponível, a
 * atribuição é re-tentada periodicamente pelo ciclo de limpeza.</li>
 * </ul>
 *
 * <p>O jogador continua podendo construir e demolir junto com as unidades: o plano segue
 * na fila dele normalmente. Cancelar o plano do jogador retira a entrada correspondente
 * da fila do time ou da unidade atribuída; o ciclo de limpeza (chamado todo frame pelo
 * manipulador de entrada local) retira o que foi concluído, invalidado ou perdido.</p>
 *
 * <p>Toda a tabela de acompanhamento é local e não sobrevive ao fim da partida: um novo
 * estado de jogo recomeça com ela vazia. Em partida em rede, o cliente remoto não encaminha
 * (a fila do time não é dele) — o encaminhamento vale para partida local e para o anfitrião.</p>
 */
public class AutoBuildSync{
    /** entradas de colocação que este sistema criou: posição empacotada -> entrada na fila do time */
    private static final ObjectMap<Integer, BlockPlan> placeEntries = new ObjectMap<>();
    /** demolições atribuídas: posição empacotada -> unidade que recebeu o plano de quebra */
    private static final ObjectMap<Integer, Unit> breakAssignments = new ObjectMap<>();
    /** demolições ainda sem construtora disponível; re-tentadas pelo ciclo de limpeza */
    private static final IntSet pendingBreaks = new IntSet();

    private static float lastPing = -1f;
    private static float retryTimer = 0f;
    /** intervalo entre re-tentativas de atribuição de demolição, em ticks */
    private static final float retryInterval = 60f;

    /** whether the forwarding is active for the local player right now. */
    public static boolean active(){
        return Core.settings.getBool("autobuild", true)
            && state.isGame() && !state.isEditor() && !net.client();
    }

    /** Called by the local player's builder whenever a plan is added to the player's queue. */
    public static void onPlayerPlanAdded(BuildPlan plan, Team team){
        if(!active() || team == null || plan == null) return;

        if(plan.breaking){
            queueBreak(plan.x, plan.y, team);
        }else{
            queuePlace(plan, team);
        }
    }

    /** Called by the local player's builder whenever a plan is removed from the player's queue. */
    public static void onPlayerPlanRemoved(int x, int y, boolean breaking, Team team){
        if(team == null) return;

        int pos = Point2.pack(x, y);
        if(breaking){
            Unit assigned = breakAssignments.remove(pos);
            pendingBreaks.remove(pos);
            if(assigned != null && assigned.isValid()){
                assigned.removeBuild(x, y, true);
            }
        }else{
            removePlaceEntry(team, pos);
        }
    }

    /**
     * Per-frame cleanup, called from the local input handler. Withdraws completed or
     * stale entries, retries unassigned demolitions and resets state between games.
     */
    public static void update(Team team){
        if(!state.isGame()){
            resetState();
            return;
        }

        if(!Core.settings.getBool("autobuild", true)){
            withdrawAll(team);
            return;
        }

        if(state.isEditor() || team == null) return;

        //place entries: leave the team queue when the player's plan is gone or the block exists
        for(var it = placeEntries.iterator(); it.hasNext;){
            var e = it.next();
            BlockPlan entry = e.value;
            int pos = e.key;
            if(!playerHasPlan(Point2.x(pos), Point2.y(pos), false) || tileHasBlock(Point2.x(pos), Point2.y(pos), entry.block)){
                it.remove();
                var data = team.data();
                if(data != null) data.plans.remove(entry);
            }
        }

        //break assignments: release the unit when the tile is gone, re-queue when it dies
        for(var it = breakAssignments.entries().iterator(); it.hasNext;){
            var e = it.next();
            int pos = e.key;
            Unit u = e.value;
            if(!u.isValid() || u.dead() || u.team() != team){
                it.remove();
                pendingBreaks.add(pos);
            }else{
                Tile tile = world.tile(Point2.x(pos), Point2.y(pos));
                if(tile == null || tile.build == null){
                    it.remove();
                    u.removeBuild(Point2.x(pos), Point2.y(pos), true);
                }
            }
        }

        //retries for demolitions that had no builder available
        if(!pendingBreaks.isEmpty()){
            retryTimer += Time.delta;
            if(retryTimer >= retryInterval){
                retryTimer = 0f;
                //copied: a successful assignment removes from the set
                IntSet retries = new IntSet();
                retries.addAll(pendingBreaks);
                for(var it = retries.iterator(); it.hasNext;){
                    tryAssignBreak(it.next(), team);
                }
            }
        }
    }

    /** removes everything this system put on the team queue or on builder units (mode switched to "player builds"). */
    public static void withdrawAll(Team team){
        if(state.isGame() && team != null){
            var data = team.data();
            if(data != null){
                for(BlockPlan entry : placeEntries.values()){
                    data.plans.remove(entry);
                }
            }
            for(var e : breakAssignments.entries()){
                if(e.value.isValid()){
                    e.value.removeBuild(Point2.x(e.key), Point2.y(e.key), true);
                }
            }
        }
        resetState();
    }

    /** clears only the tracking tables; the game state that owned the old entries is gone with it. */
    public static void resetState(){
        placeEntries.clear();
        breakAssignments.clear();
        pendingBreaks.clear();
        lastPing = -1f;
        retryTimer = 0f;
    }

    //region internals

    private static void queuePlace(BuildPlan plan, Team team){
        int pos = Point2.pack(plan.x, plan.y);

        //replace a stale entry of our own at the same tile, if any
        removePlaceEntry(team, pos);

        var data = team.data();
        if(data == null) return;

        //no duplicates: an existing team plan on the same tile (for example the rebuild plan
        //of a damaged building) already does this work
        if(teamPlanAt(data, plan.x, plan.y) != null) return;

        BlockPlan entry = new BlockPlan(plan.x, plan.y, (short)plan.rotation, plan.block, plan.config);
        data.plans.addFirst(entry);
        placeEntries.put(pos, entry);
        pingBuilders(team);
    }

    private static void queueBreak(int x, int y, Team team){
        int pos = Point2.pack(x, y);

        Unit assigned = breakAssignments.get(pos);
        if(assigned != null){
            //a valid assignment keeps going; a dead one goes back to the retry queue
            if(assigned.isValid() && assigned.team() == team){
                return;
            }
            breakAssignments.remove(pos);
        }

        pendingBreaks.add(pos);
        tryAssignBreak(pos, team);
    }

    private static void tryAssignBreak(int pos, Team team){
        int x = Point2.x(pos), y = Point2.y(pos);

        Tile tile = world.tile(x, y);
        if(tile == null || tile.build == null){
            //nothing left to demolish
            pendingBreaks.remove(pos);
            return;
        }

        Unit best = null;
        float bestScore = -1f;
        float dst = 0f;

        for(Unit u : Groups.unit){
            if(u.team() != team || u.dead() || !u.canBuild() || u.isPlayer()) continue;
            //only controllers that know how to walk to a plan take demolition work
            if(!handlesPlans(u)) continue;
            BuildPlan cur = u.buildPlan();
            if(cur != null && cur.breaking && cur.x == x && cur.y == y) continue;

            float score = u.type().buildSpeed * u.buildSpeedMultiplier();
            if(score <= 0f) continue;

            float udst = u.dst2(x * tilesize, y * tilesize);
            //faster builder wins; near-equal scores fall back to the closer one
            if(best == null || score > bestScore * 1.01f || (score > bestScore * 0.99f && udst < dst)){
                best = u;
                bestScore = score;
                dst = udst;
            }
        }

        if(best == null) return; //retried from update()

        pendingBreaks.remove(pos);
        //head of the queue: the demolition starts as soon as the controller ticks
        best.addBuild(new BuildPlan(x, y), false);
        breakAssignments.put(pos, best);
    }

    /** @return whether this unit's controller actually walks to and executes build plans. */
    private static boolean handlesPlans(Unit u){
        var controller = u.controller();
        if(controller instanceof BuilderAI || controller instanceof PrebuildAI) return true;
        if(controller instanceof CommandAI command){
            var cmd = command.currentCommand();
            return cmd == UnitCommand.rebuildCommand || cmd == UnitCommand.assistCommand;
        }
        return false;
    }

    private static void removePlaceEntry(Team team, int pos){
        BlockPlan entry = placeEntries.remove(pos);
        if(entry != null && team.data() != null){
            team.data().plans.remove(entry);
        }
    }

    private static BlockPlan teamPlanAt(Teams.TeamData data, int x, int y){
        for(BlockPlan p : data.plans){
            if(p.x == x && p.y == y) return p;
        }
        return null;
    }

    private static boolean playerHasPlan(int x, int y, boolean breaking){
        if(player == null || player.unit() == null) return false;
        for(BuildPlan p : player.unit().plans){
            if(p.x == x && p.y == y && p.breaking == breaking) return true;
        }
        return false;
    }

    private static boolean tileHasBlock(int x, int y, Block block){
        if(block == null) return false;
        Tile tile = world.tile(x, y);
        return tile != null && tile.block() == block;
    }

    /** Unconditional ping, used when a whole group has just been put into construction mode. */
    public static void pingBuildersNow(Team team){
        for(Unit u : Groups.unit){
            if(u.team() != team) continue;
            if(u.controller() instanceof BuilderAI ai){
                ai.pingForTeamPlans();
            }else if(u.controller() instanceof CommandAI command){
                //on the player's team most builders use CommandAI, which delegates
                //the rebuild/assist commands to a BuilderAI of its own
                command.pingForTeamPlans();
            }
        }
    }

    private static void pingBuilders(Team team){
        //one ping per frame at most: a drag line adds dozens of plans in a single frame
        if(lastPing == Time.time) return;
        lastPing = Time.time;
        pingBuildersNow(team);
    }

    //endregion
}
