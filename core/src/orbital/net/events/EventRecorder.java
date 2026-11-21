package orbital.net.events;

import arc.*;
import arc.struct.*;
import arc.util.*;
import orbital.game.EventType.*;

import java.io.*;

/**
 * Bridge between the in-process {@code Events.fire(...)} stream and the authoritative append-only
 * {@link EventStore}. Installed by the server (never by a client): the server is the only writer,
 * so every logged event has exactly one sequence number assigned in commit order.
 *
 * <p>Payloads are intentionally tiny. Entities and tiles are identified by their integer id/pos;
 * anything the client already knows is not repeated. This is the whole point of the format: the
 * wire carries "what happened", not "what the world looks like now".</p>
 */
public final class EventRecorder{
    private final EventStore store;

    /** True while the recorder is installed; guarded so a second install cannot duplicate events. */
    private static boolean installed;

    public EventRecorder(EventStore store){
        this.store = store;
    }

    /** Registers the listeners once. Call on the server before the world starts. */
    public void install(){
        if(installed) return;
        installed = true;

        Events.on(TileChangeEvent.class, e -> {
            if(e.tile != null) record(EventTypeIds.TILE_CHANGE,
                varint(e.tile.pos()), varint(e.tile.block().id), varint(e.tile.build == null ? -1 : e.tile.build.team.id));
        });
        Events.on(TileFloorChangeEvent.class, e -> {
            if(e.tile != null) record(EventTypeIds.TILE_FLOOR_CHANGE,
                varint(e.tile.pos()), varint(e.floor.id));
        });
        Events.on(TileOverlayChangeEvent.class, e -> {
            if(e.tile != null) record(EventTypeIds.TILE_OVERLAY_CHANGE,
                varint(e.tile.pos()), varint(e.overlay.id));
        });

        Events.on(BlockBuildBeginEvent.class, e -> record(EventTypeIds.BUILD_BEGIN,
            varint(e.tile.pos()), varint(e.team.id), bool(e.breaking)));
        Events.on(BlockBuildEndEvent.class, e -> record(EventTypeIds.BUILD_END,
            varint(e.tile.pos()), varint(e.team.id), bool(e.breaking)));

        Events.on(BlockDestroyEvent.class, e -> record(EventTypeIds.BUILD_DESTROYED, varint(e.tile.pos())));
        Events.on(BuildTeamChangeEvent.class, e -> {
            if(e.build != null) record(EventTypeIds.BUILD_TEAM_CHANGE,
                varint(e.build.pos()), varint(e.build.team.id), varint(e.previous.id));
        });
        Events.on(ConfigEvent.class, e -> {
            if(e.tile != null) record(EventTypeIds.CONFIG, varint(e.tile.pos()), string(String.valueOf(e.value)));
        });

        Events.on(UnitCreateEvent.class, e -> {
            if(e.unit != null) record(EventTypeIds.UNIT_CREATE, varint(e.unit.id), varint(e.unit.type.id));
        });
        Events.on(UnitDestroyEvent.class, e -> {
            if(e.unit != null) record(EventTypeIds.UNIT_DESTROY, varint(e.unit.id));
        });
        Events.on(BulletCreateEvent.class, e -> {
            if(e.bullet != null) record(EventTypeIds.BULLET_CREATE, varint(e.bullet.id()));
        });

        Events.on(WithdrawEvent.class, e -> record(EventTypeIds.ITEM_WITHDRAW,
            varint(e.tile.pos()), varint(e.item.id), varint(e.amount)));
        Events.on(DepositEvent.class, e -> record(EventTypeIds.ITEM_DEPOSIT,
            varint(e.tile.pos()), varint(e.item.id), varint(e.amount)));

        Events.on(WaveEvent.class, e -> record(EventTypeIds.WAVE,
            varint(orbital.Vars.state == null ? 0 : orbital.Vars.state.wave)));
        Events.on(GameOverEvent.class, e -> record(EventTypeIds.GAME_OVER, bool(e.winner != null)));
        Events.on(PlayerChatEvent.class, e -> record(EventTypeIds.CHAT,
            varint(e.player == null ? -1 : e.player.id), string(e.message)));
    }

    /** Records one event with the current authoritative tick and a server source id. */
    public long record(int typeId, byte[]... fields){
        return recordFrom(EventRecord.SERVER_SOURCE, typeId, fields);
    }

    /** Records a player-originated event (input, chat command, build order). */
    public long recordFrom(int playerId, int typeId, byte[]... fields){
        try{
            return store.append(currentTick(), playerId, typeId, join(fields));
        }catch(IOException e){
            Log.err("[eventstore] failed to record @ from player @: @",
                EventTypeIds.name(typeId), playerId, e.getMessage());
            return -1;
        }
    }

    public EventStore store(){
        return store;
    }

    private static long currentTick(){
        return orbital.Vars.state == null ? 0 : (long)orbital.Vars.state.tick;
    }

    //region compact payload helpers

    public static byte[] varint(long value){
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try{
            EventCodec.writeVarInt(new DataOutputStream(bytes), value);
        }catch(IOException impossible){
        }
        return bytes.toByteArray();
    }

    public static byte[] bool(boolean value){
        return new byte[]{(byte)(value ? 1 : 0)};
    }

    public static byte[] string(String value){
        if(value == null) return new byte[0];
        byte[] utf8 = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try{
            EventCodec.writeVarInt(new DataOutputStream(bytes), utf8.length);
            bytes.write(utf8);
        }catch(IOException impossible){
        }
        return bytes.toByteArray();
    }

    /** Concatenates already-encoded fields into one payload. */
    public static byte[] join(byte[]... parts){
        int length = 0;
        for(byte[] part : parts) length += part.length;
        byte[] result = new byte[length];
        int offset = 0;
        for(byte[] part : parts){
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    //endregion
}
