package orbital.net.events;

import arc.util.Log;

import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * Append-only, segmented event log on the local disk.
 *
 * <p>One process (the server) owns the {@code seq} counter and appends with a synchronized method,
 * so there is no contention and no reordering: clients always receive events in the exact order the
 * server committed them. Segments are rotated at {@code segmentBytes} so an old closed file can be
 * deleted independently of the one being written.</p>
 *
 * <p>The store is deliberately independent of the game: it records opaque {@link EventRecord}s.
 * The game maps its events to {@link #typeId}s and writes their payload separately. That keeps the
 * persistence layer testable without a world, and lets the WebSocket transport replay straight
 * from {@link #forEachFrom(long, Consumer)}.</p>
 */
public final class EventStore implements AutoCloseable{
    private static final String prefix = "events-", suffix = ".log";

    private final Path directory;
    private final long segmentBytes;
    private final Object lock = new Object();

    private int segmentIndex = -1;
    private DataOutputStream current;
    private long segmentSize;
    private long nextSeq;
    private long lastTick;
    private int appended;

    /** @param directory folder that will hold the segment files (created if missing). */
    public EventStore(Path directory){
        this(directory, 32L * 1024 * 1024);
    }

    public EventStore(Path directory, long segmentBytes){
        this.directory = directory;
        this.segmentBytes = Math.max(1024L, segmentBytes);
    }

    /** Opens the store, recovering the next sequence number and last tick from existing segments. */
    public void open() throws IOException{
        synchronized(lock){
            Files.createDirectories(directory);

            int maxIndex = -1;
            try(DirectoryStream<Path> files = Files.newDirectoryStream(directory, prefix + "*" + suffix)){
                for(Path file : files){
                    int index = parseIndex(file);
                    if(index > maxIndex) maxIndex = index;
                }
            }

            if(maxIndex < 0){
                nextSeq = 0;
                startSegment(0);
                return;
            }

            for(int i = 0; i <= maxIndex; i++){
                Path file = segmentFile(i);
                if(!Files.exists(file)) continue;
                try(DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){
                    EventCodec.readHeader(in);
                    while(true){
                        EventRecord event = EventCodec.read(in);
                        if(event == null) break;
                        nextSeq = event.seq() + 1;
                        lastTick = event.tick();
                    }
                }
            }

            segmentIndex = maxIndex;
            current = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
                segmentFile(maxIndex), StandardOpenOption.CREATE, StandardOpenOption.APPEND)));
            segmentSize = Files.size(segmentFile(maxIndex));
            Log.info("[eventstore] recovered @ events (last seq @, tick @) from @", appended, nextSeq, lastTick, directory);
        }
    }

    /** Appends one event and returns its assigned sequence number. */
    public synchronized long append(long tick, int playerId, int typeId, byte[] payload) throws IOException{
        ensureOpen();
        EventRecord event = new EventRecord(nextSeq++, Math.max(tick, lastTick), playerId, typeId, payload);
        EventCodec.write(current, event);
        lastTick = event.tick();
        appended++;

        //Count the bytes without flushing every event: the buffered stream reports the logical size.
        segmentSize += 16L + event.payload().length;
        if(segmentSize >= segmentBytes) rotate();
        return event.seq();
    }

    /** Flushes buffered records to disk. Call on keyframes and on graceful shutdown. */
    public synchronized void flush() throws IOException{
        if(current != null) current.flush();
    }

    @Override public synchronized void close() throws IOException{
        if(current != null){
            current.flush();
            current.close();
            current = null;
        }
    }

    /** Starts a new segment. Old segments stay readable until {@link #compact(long)} is called. */
    public synchronized void rotate() throws IOException{
        ensureOpen();
        current.flush();
        current.close();
        startSegment(segmentIndex + 1);
    }

    /** Deletes every segment whose highest sequence number is below {@code beforeSeq}. */
    public synchronized int compact(long beforeSeq) throws IOException{
        int deleted = 0;
        for(int i = 0; i < segmentIndex; i++){
            Path file = segmentFile(i);
            if(!Files.exists(file)) continue;
            long highest = -1;
            try(DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){
                EventCodec.readHeader(in);
                while(true){
                    EventRecord event = EventCodec.read(in);
                    if(event == null) break;
                    highest = event.seq();
                }
            }
            if(highest >= 0 && highest < beforeSeq && Files.deleteIfExists(file)) deleted++;
        }
        return deleted;
    }

    /** Streams every event with {@code seq >= from} in commit order. */
    public synchronized void forEachFrom(long from, Consumer<EventRecord> consumer) throws IOException{
        for(int i = 0; i <= segmentIndex; i++){
            Path file = segmentFile(i);
            if(!Files.exists(file)) continue;
            try(DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))){
                EventCodec.readHeader(in);
                while(true){
                    EventRecord event = EventCodec.read(in);
                    if(event == null) break;
                    if(event.seq() >= from) consumer.accept(event);
                }
            }
        }
    }

    /** @return the last committed event, or null when the log is empty. */
    public synchronized EventRecord last() throws IOException{
        EventRecord[] result = new EventRecord[1];
        forEachFrom(Math.max(0, nextSeq - 1), event -> result[0] = event);
        return result[0];
    }

    public synchronized long nextSeq(){
        return nextSeq;
    }

    public synchronized long lastTick(){
        return lastTick;
    }

    public synchronized int appended(){
        return appended;
    }

    public synchronized boolean isOpen(){
        return current != null;
    }

    private void startSegment(int index) throws IOException{
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(
            segmentFile(index), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)));
        EventCodec.writeHeader(out);
        out.flush();
        current = out;
        segmentIndex = index;
        segmentSize = 12L; //magic + version
    }

    private void ensureOpen() throws IOException{
        if(current == null) open();
    }

    private Path segmentFile(int index){
        return directory.resolve(prefix + String.format("%010d", index) + suffix);
    }

    private static int parseIndex(Path file){
        String name = file.getFileName().toString();
        if(!name.startsWith(prefix) || !name.endsWith(suffix)) return -1;
        String digits = name.substring(prefix.length(), name.length() - suffix.length());
        try{
            return Integer.parseInt(digits);
        }catch(NumberFormatException invalid){
            return -1;
        }
    }
}
