package orbital.net.online;

import arc.*;
import arc.struct.*;
import arc.util.*;
import orbital.core.*;
import orbital.gen.*;
import orbital.net.*;
import orbital.net.events.*;
import orbital.gen.Call;

import static orbital.Vars.*;

import java.io.*;
import java.util.function.Consumer;

/**
 * Sends the authoritative event stream to clients, replacing full-state snapshots.
 *
 * <p>The server keeps one cursor per connection. On each tick it appends newly committed events
 * (already in the {@link EventStore}) into a batch, then sends one binary frame containing the
 * whole batch. Batching is what keeps the byte count low: at 20 events/group with ~12 bytes each
 * the payload is ~240 bytes per 100 ms, instead of kilobytes of entity state 5 times a second.</p>
 *
 * <p>Uses the existing binary packet channel, so no new generated packets are needed and the
 * legacy protocol keeps working for clients that do not opt in.</p>
 */
public final class EventReplicator{
    /** Channel name used with {@code Call.serverBinaryPacket*}. */
    public static final String CHANNEL = "orbital.events";

    private final EventStore store;
    private final ObjectMap<NetConnection, Long> cursors = new ObjectMap<>();
    private long lastSentSeq = -1;

    public EventReplicator(EventStore store){
        this.store = store;
    }

    /** @return the highest sequence number already sent to every client. */
    public long lastSentSeq(){
        return lastSentSeq;
    }

    /** Sends all events committed since the last flush to every connected client. */
    public void flush(){
        if(!AuthoritativeMode.enabled()) return;

        long from = lastSentSeq + 1;
        long end = store.nextSeq();
        if(end <= from) return;

        //Build one frame with all pending events (varint count, then each record body).
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(bytes);
        int[] count = {0};
        try{
            store.forEachFrom(from, event -> {
                try{
                    EventCodec.write(out, event);
                    count[0]++;
                }catch(IOException e){
                    Log.err("[online] could not encode event @: @", event.seq(), e.getMessage());
                }
            });
        }catch(IOException e){
            Log.err("[online] could not read event log: @", e.getMessage());
            return;
        }
        if(count[0] == 0) return;

        byte[] batch = bytes.toByteArray();
        lastSentSeq = end - 1;

        for(NetConnection con : net.getConnections()){
            if(!con.isConnected()) continue;
            //clientBinaryPacket* is the server -> client channel; the generated overload takes the
            //target connection. Reliable: an event must never be lost or the client diverges.
            Call.clientBinaryPacketReliable(con, CHANNEL, batch);
        }

        //Periodic full-state keyframe so clients can catch up after a long disconnect.
        KeyframeManager keyframes = OnlineHolder.keyframes();
        if(keyframes != null){
            for(NetConnection con : net.getConnections()){
                if(con.isConnected() && con.player != null){
                    keyframes.onEvents(count[0], lastSentSeq, con.player);
                    break;
                }
            }
        }
    }

    /** Client-side: feeds a received batch into the supplied consumer. */
    public static void decode(byte[] batch, Consumer<EventRecord> consumer) throws IOException{
        DataInput in = new DataInputStream(new ByteArrayInputStream(batch));
        while(true){
            EventRecord event = EventCodec.read(in);
            if(event == null) return;
            consumer.accept(event);
        }
    }
}
