package orbital.net.online;

import arc.util.Log;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Minimal Server-Sent Events transport for the online event channel.
 *
 * <p>Drop-in replacement for {@link WsClient} with the same connect/send/receive/close API. A
 * long-lived GET receives newline-delimited base64 frames; commands are posted as binary bodies.
 * One SSE connection is one Cloudflare Worker request, so the free 100k requests/day cap is never
 * approached by normal play. No third-party dependency.</p>
 */
public final class SseClient implements AutoCloseable{
    private final String scheme, host, eventPath, postPath;
    private final int port;
    private volatile long after;
    private volatile boolean connected;
    private Socket socket;
    private BufferedReader reader;
    private final BlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(256);
    private Thread readerThread;

    /** @param url an http(s) URL such as {@code https://orbital.workers.dev/events?room=default}. */
    public SseClient(String url) throws IOException{
        URI uri;
        try{
            uri = URI.create(url);
        }catch(IllegalArgumentException bad){
            throw new IOException("invalid SSE URL: " + url);
        }
        scheme = uri.getScheme();
        if(!"http".equals(scheme) && !"https".equals(scheme)) throw new IOException("unsupported scheme: " + scheme);
        host = uri.getHost();
        if(host == null) throw new IOException("missing host in " + url);
        port = uri.getPort() > 0 ? uri.getPort() : ("https".equals(scheme) ? 443 : 80);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/events" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        eventPath = path + (query.isEmpty() ? "" : "?" + query);
        //POST commands to the sibling /event endpoint, preserving the room parameter.
        String roomQuery = query.isEmpty() ? "" : query;
        postPath = "/event" + (roomQuery.isEmpty() ? "" : "?" + roomQuery);
        after = parseAfter(query);
    }

    private static long parseAfter(String query){
        for(String part : query.split("&")){
            if(part.startsWith("after=")){
                try{ return Long.parseLong(part.substring(6)); }catch(NumberFormatException ignored){}
            }
        }
        return 0;
    }

    /** Opens the SSE stream; events after the cursor are delivered by the server. */
    public void connect() throws IOException{
        Socket raw;
        if("https".equals(scheme)){
            SSLSocket ssl = (SSLSocket)SSLSocketFactory.getDefault().createSocket();
            ssl.connect(new InetSocketAddress(host, port), 10_000);
            ssl.startHandshake();
            raw = ssl;
        }else{
            raw = new Socket();
            raw.connect(new InetSocketAddress(host, port), 10_000);
        }
        //No read timeout: an SSE stream is long-lived and may be quiet for a while.
        raw.setSoTimeout(0);
        socket = raw;

        String request = "GET " + eventPath + " HTTP/1.1\r\n"
            + "Host: " + host + ":" + port + "\r\n"
            + "Accept: text/event-stream\r\n"
            + "Cache-Control: no-cache\r\n"
            + "Connection: keep-alive\r\n\r\n";
        raw.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
        raw.getOutputStream().flush();

        reader = new BufferedReader(new InputStreamReader(raw.getInputStream(), StandardCharsets.UTF_8));
        //Validate the HTTP status line before handing the stream to the reader thread.
        String status = reader.readLine();
        if(status == null || !status.contains(" 200")){
            throw new IOException("SSE handshake failed: " + status);
        }
        String line;
        boolean headers = true;
        while((line = reader.readLine()) != null && headers){
            if(line.isEmpty()) headers = false;
        }

        connected = true;
        readerThread = new Thread(this::readLoop, "Orbital-SSE-Reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop(){
        try{
            String line;
            while(connected && (line = reader.readLine()) != null){
                if(line.startsWith("data: ") && line.length() > 6){
                    try{
                        byte[] frame = Base64.getDecoder().decode(line.substring(6).trim());
                        frames.offer(frame);
                    }catch(IllegalArgumentException bad){
                        Log.warn("[online] ignored malformed SSE frame");
                    }
                }
            }
        }catch(IOException e){
            if(connected) Log.warn("[online] SSE stream ended: @", e.getMessage());
        }finally{
            connected = false;
        }
    }

    /** Posts one command frame to the relay. */
    public void send(byte[] frame) throws IOException{
        if(socket == null || socket.isClosed()) throw new IOException("SSE client not connected");
        HttpURLConnection con = (HttpURLConnection)new URL(scheme + "://" + host + ":" + port + postPath).openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setFixedLengthStreamingMode(frame.length);
        con.setRequestProperty("Content-Type", "application/octet-stream");
        con.setConnectTimeout(10_000);
        con.setReadTimeout(10_000);
        try(OutputStream out = con.getOutputStream()){
            out.write(frame);
        }
        int code = con.getResponseCode();
        con.disconnect();
        if(code < 200 || code >= 300) throw new IOException("command post failed: HTTP " + code);
    }

    /** @return the next frame, blocking until one arrives or the stream closes. */
    public byte[] receive() throws IOException{
        try{
            return frames.take();
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while waiting for frame", e);
        }
    }

    /** Cursor for a future reconnect; the server sends events after this seq. */
    public void setAfter(long seq){
        this.after = seq;
    }

    public long after(){
        return after;
    }

    public boolean isConnected(){
        return connected;
    }

    @Override public void close(){
        connected = false;
        if(readerThread != null) readerThread.interrupt();
        try{
            if(socket != null && !socket.isClosed()) socket.close();
        }catch(IOException ignored){}
    }
}
