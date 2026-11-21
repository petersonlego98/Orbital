package orbital.net.online;

import arc.util.*;
import orbital.net.events.*;

import java.io.*;
import java.util.function.Consumer;

/**
 * Online session over a Cloudflare Worker.
 *
 * <p>The client sends only player commands as compact {@link EventRecord}s and receives only the
 * events it is missing (from its last applied {@code seq}). There is no full-state snapshot on
 * every tick: a keyframe is requested once on join and, afterwards, only the event stream flows.
 * That is what keeps the session in the kbps range.</p>
 */
public final class OnlineClient implements AutoCloseable{
    /** First frame the client sends: "give me the keyframe and events after this seq". */
    private static final int OP_JOIN = 1;
    private static final int OP_EVENT = 2;
    private static final int OP_KEYFRAME = 3;
    private static final int OP_ACK = 4;

    private final WsClient ws;
    private long appliedSeq = -1;
    private volatile boolean running;
    private Thread reader;
    private final Consumer<EventRecord> listener;

    public OnlineClient(String url, Consumer<EventRecord> listener) throws IOException{
        this.ws = new WsClient(url);
        this.listener = listener;
    }

    /** Connects and requests a fresh keyframe from the server. */
    public void connect() throws IOException{
        ws.connect();
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(frame);
        out.writeByte(OP_JOIN);
        EventCodec.writeVarInt(out, appliedSeq);
        ws.send(frame.toByteArray());

        running = true;
        reader = new Thread(this::readLoop, "Orbital-Online-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Sends one player command. The server assigns the authoritative seq and tick. */
    public void sendCommand(int typeId, byte[]... fields){
        try{
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(frame);
            out.writeByte(OP_EVENT);
            EventCodec.writeVarInt(out, typeId);
            byte[] payload = EventRecorder.join(fields);
            EventCodec.writeVarInt(out, payload.length);
            out.write(payload);
            ws.send(frame.toByteArray());
        }catch(IOException e){
            Log.err("[online] could not send command: @", e.getMessage());
        }
    }

    /** Acknowledges the last event applied locally so the server can drop old entries. */
    public void acknowledge(long seq){
        this.appliedSeq = Math.max(this.appliedSeq, seq);
        try{
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(frame);
            out.writeByte(OP_ACK);
            EventCodec.writeVarInt(out, appliedSeq);
            ws.send(frame.toByteArray());
        }catch(IOException e){
            Log.err("[online] could not acknowledge seq @: @", seq, e.getMessage());
        }
    }

    private void readLoop(){
        while(running && ws.isConnected()){
            try{
                byte[] frame = ws.receive();
                if(frame == null) break;
                handle(frame);
            }catch(IOException e){
                if(running) Log.err("[online] connection lost: @", e.getMessage());
                break;
            }
        }
        running = false;
    }

    private void handle(byte[] frame) throws IOException{
        DataInput in = new DataInputStream(new ByteArrayInputStream(frame));
        int op = in.readUnsignedByte();
        switch(op){
            case OP_EVENT -> {
                long seq = EventCodec.readVarInt(in);
                long tick = EventCodec.readVarInt(in);
                int playerId = (int)EventCodec.readZigZag(in);
                int typeId = (int)EventCodec.readVarInt(in);
                int length = (int)EventCodec.readVarInt(in);
                byte[] payload = new byte[length];
                in.readFully(payload);
                EventRecord record = new EventRecord(seq, tick, playerId, typeId, payload);
                if(seq > appliedSeq){
                    listener.accept(record);
                    appliedSeq = seq;
                }
            }
            case OP_KEYFRAME -> {
                //Keyframes are handled by the game layer (world setup); the envelope only carries
                //the seq the snapshot corresponds to.
                long seq = EventCodec.readVarInt(in);
                Log.info("[online] keyframe at seq @", seq);
                appliedSeq = seq;
            }
            case OP_ACK -> { /* server echo, nothing to do */ }
            default -> Log.warn("[online] unknown frame op @", op);
        }
    }

    public long appliedSeq(){
        return appliedSeq;
    }

    public boolean isConnected(){
        return running && ws.isConnected();
    }

    @Override public void close(){
        running = false;
        ws.close();
    }
}
