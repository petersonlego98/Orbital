package orbital.net.online;

import arc.*;
import arc.util.*;
import orbital.content.*;
import orbital.entities.*;
import orbital.game.*;
import orbital.game.EventType.*;
import orbital.gen.*;
import orbital.net.events.*;
import orbital.world.*;

import java.io.*;

import static orbital.Vars.*;

/**
 * Turns authoritative {@link EventRecord}s into concrete world changes on the client.
 *
 * <p>Every payload is decoded with the same varint helpers used to write it. The operations mirror
 * what the legacy {@code Call.*} handlers already do, but are driven by the server-assigned event
 * stream instead of by raw state snapshots. Unknown event ids are ignored with a warning so an old
 * client never crashes on a newer server event — it will simply request a keyframe.</p>
 */
public final class WorldEventApplier{
    private final EventStore store;

    public WorldEventApplier(@Nullable EventStore store){
        this.store = store;
    }

    /** Handles one (typeId, payload) pair from {@link EventApplier}. */
    public void apply(int typeId, byte[] payload){
        try{
            DataInput in = new DataInputStream(new ByteArrayInputStream(payload));
            switch(typeId){
                case EventTypeIds.TILE_CHANGE -> {
                    int pos = (int)EventCodec.readVarInt(in);
                    int blockId = (int)EventCodec.readVarInt(in);
                    int teamId = (int)EventCodec.readZigZag(in);
                    Tile tile = world.tile(pos);
                    if(tile != null && blockId >= 0 && blockId < content.blocks().size){
                        Block block = content.block(blockId);
                        Team team = teamId >= 0 && teamId < Team.all.length ? Team.all[teamId] : Team.derelict;
                        tile.setNet(block, team, 0);
                    }
                }
                case EventTypeIds.TILE_FLOOR_CHANGE -> {
                    int pos = (int)EventCodec.readVarInt(in);
                    int floorId = (int)EventCodec.readVarInt(in);
                    Tile tile = world.tile(pos);
                    if(tile != null && floorId >= 0 && floorId < content.blocks().size){
                        tile.setFloorNet(content.block(floorId));
                    }
                }
                case EventTypeIds.TILE_OVERLAY_CHANGE -> {
                    int pos = (int)EventCodec.readVarInt(in);
                    int overlayId = (int)EventCodec.readVarInt(in);
                    Tile tile = world.tile(pos);
                    if(tile != null && overlayId >= 0 && overlayId < content.blocks().size){
                        tile.setOverlayNet(content.block(overlayId));
                    }
                }
                case EventTypeIds.BUILD_DESTROYED -> {
                    int pos = (int)EventCodec.readVarInt(in);
                    Tile tile = world.tile(pos);
                    if(tile != null) tile.removeNet();
                }
                case EventTypeIds.UNIT_DESTROY -> {
                    int id = (int)EventCodec.readVarInt(in);
                    Syncc entity = Groups.sync.getByID(id);
                    if(entity != null) entity.remove();
                }
                case EventTypeIds.CONFIG -> {
                    int pos = (int)EventCodec.readVarInt(in);
                    Building build = world.build(pos);
                    if(build != null) build.configure(readString(in));
                }
                case EventTypeIds.CHAT -> {
                    //player id + utf8 message; chat is already handled by the legacy channel, so
                    //this is a no-op here to avoid duplicate messages.
                }
                case EventTypeIds.WAVE -> {
                    int wave = (int)EventCodec.readVarInt(in);
                    if(wave > state.wave){
                        state.wave = wave;
                        Events.fire(new WaveEvent());
                    }
                }
                case EventTypeIds.GAME_OVER -> {
                    state.gameOver = true;
                }
                case EventTypeIds.KEYFRAME -> {
                    //Keyframes are applied by OnlineSession before the event stream; see there.
                }
                default -> Log.warn("[online] unhandled event id @", typeId);
            }
        }catch(IOException e){
            Log.err("[online] could not decode event @: @", typeId, e.getMessage());
        }
    }

    private static String readString(DataInput in) throws IOException{
        int length = (int)EventCodec.readVarInt(in);
        if(length < 0 || length > 4096) return "";
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
