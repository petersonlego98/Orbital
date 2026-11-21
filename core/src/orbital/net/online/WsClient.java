package orbital.net.online;

import arc.util.Log;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Minimal RFC 6455 WebSocket client for the online event channel.
 *
 * <p>No third-party dependency: a TLS socket, the HTTP upgrade handshake, and the small subset of
 * frames the game needs (binary payloads, ping/pong, close). Client frames are masked per spec.
 * This is deliberately only a transport — {@link OnlineClient} defines the event envelope.</p>
 */
public final class WsClient implements AutoCloseable{
    private final String scheme;
    private final String host;
    private final int port;
    private final String path;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private volatile boolean connected;
    private final SecureRandom random = new SecureRandom();

    /** @param url a ws:// or wss:// URL, e.g. {@code wss://orbital.example.workers.dev/ws}. */
    public WsClient(String url) throws IOException{
        URI uri;
        try{
            uri = URI.create(url);
        }catch(IllegalArgumentException bad){
            throw new IOException("invalid WebSocket URL: " + url);
        }
        scheme = uri.getScheme();
        if(!"ws".equals(scheme) && !"wss".equals(scheme)) throw new IOException("unsupported scheme: " + scheme);
        host = uri.getHost();
        if(host == null) throw new IOException("missing host in " + url);
        port = uri.getPort() > 0 ? uri.getPort() : ("wss".equals(scheme) ? 443 : 80);
        String rawPath = uri.getRawPath();
        path = (rawPath == null || rawPath.isEmpty() ? "/" : rawPath)
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    /** Connects and performs the HTTP upgrade. */
    public void connect() throws IOException{
        Socket raw;
        if("wss".equals(scheme)){
            SSLSocket ssl = (SSLSocket)SSLSocketFactory.getDefault().createSocket();
            ssl.connect(new InetSocketAddress(host, port), 10_000);
            ssl.startHandshake();
            raw = ssl;
        }else{
            raw = new Socket();
            raw.connect(new InetSocketAddress(host, port), 10_000);
        }
        raw.setSoTimeout(30_000);
        socket = raw;

        OutputStream stream = raw.getOutputStream();
        InputStream input = raw.getInputStream();

        byte[] key = new byte[16];
        random.nextBytes(key);
        String request = "GET " + path + " HTTP/1.1\r\n"
            + "Host: " + host + ":" + port + "\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Key: " + Base64.getEncoder().encodeToString(key) + "\r\n"
            + "Sec-WebSocket-Version: 13\r\n\r\n";
        stream.write(request.getBytes(StandardCharsets.UTF_8));
        stream.flush();

        ByteArrayOutputStream header = new ByteArrayOutputStream();
        boolean previousCr = false, previousLf = false;
        while(true){
            int b = input.read();
            if(b < 0) throw new IOException("connection closed during WebSocket handshake");
            header.write(b);
            if(previousCr && b == '\n' && previousLf) break;
            previousCr = b == '\r';
            if(!previousCr) previousLf = b == '\n';
        }
        String response = header.toString(StandardCharsets.UTF_8);
        if(!response.startsWith("HTTP/1.1 101")){
            throw new IOException("WebSocket upgrade failed: " + response.split("\r\n")[0]);
        }

        in = new DataInputStream(input);
        out = stream;
        connected = true;
        Log.info("[online] connected to @", host + ":" + port + path);
    }

    /** Sends one binary message. Synchronized so the game thread and reader cannot interleave frames. */
    public synchronized void send(byte[] payload) throws IOException{
        ensureConnected();
        out.write(0x82); // FIN + binary opcode

        int length = payload.length;
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        if(length < 126){
            out.write(0x80 | length);
        }else if(length <= 65535){
            out.write(0x80 | 126);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        }else{
            out.write(0x80 | 127);
            for(int i = 7; i >= 0; i--) out.write((int)((long)length >>> (i * 8)) & 0xFF);
        }
        out.write(mask);
        for(int i = 0; i < length; i++) out.write(payload[i] ^ mask[i & 3]);
        out.flush();
    }

    /** Reads the next binary message, handling ping/pong/close internally. */
    public byte[] receive() throws IOException{
        ensureConnected();
        while(true){
            int first = in.readUnsignedByte();
            boolean fin = (first & 0x80) != 0;
            int opcode = first & 0x0F;

            int second = in.readUnsignedByte();
            boolean masked = (second & 0x80) != 0;
            long length = second & 0x7F;
            if(length == 126) length = in.readUnsignedShort();
            else if(length == 127) length = in.readLong();
            if(length < 0 || length > 8L * 1024 * 1024) throw new IOException("WebSocket frame too large: " + length);

            byte[] mask = masked ? new byte[4] : null;
            if(masked) in.readFully(mask);
            byte[] payload = new byte[(int)length];
            in.readFully(payload);
            if(masked) for(int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];

            switch(opcode){
                case 0x9 -> pong(payload);            //ping
                case 0xA -> { /* pong */ }
                case 0x8 -> { connected = false; return null; } //close
                case 0x2 -> {
                    if(!fin) throw new IOException("fragmented frames are not supported");
                    return payload;
                }
                default -> throw new IOException("unsupported WebSocket opcode: " + opcode);
            }
        }
    }

    private synchronized void pong(byte[] payload) throws IOException{
        out.write(0x8A);
        out.write(payload.length & 0x7F);
        out.write(payload);
        out.flush();
    }

    private void ensureConnected() throws IOException{
        if(!connected || socket == null || socket.isClosed()) throw new IOException("WebSocket is not connected");
    }

    @Override public synchronized void close(){
        connected = false;
        try{
            if(out != null){ out.write(0x88); out.flush(); }
        }catch(IOException ignored){
        }
        try{
            if(socket != null) socket.close();
        }catch(IOException ignored){
        }
    }

    public boolean isConnected(){
        return connected;
    }

    public String url(){
        return scheme + "://" + host + ":" + port + path;
    }
}
