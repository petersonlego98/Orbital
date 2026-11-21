package orbital.net.online;

import arc.util.*;
import orbital.gen.*;
import orbital.io.*;
import orbital.net.*;

import java.io.*;
import java.nio.file.*;

/**
 * Periodic full-state snapshots (keyframes) for the event log.
 *
 * <p>Events alone are useless to a client that just joined or missed too many: it needs one
 * authoritative world state to start from, then replays events with {@code seq > keyframe.seq}.
 * Reuses {@link NetworkIO#writeWorld} / {@link NetworkIO#loadWorld}, the same format the legacy
 * join already uses, so no duplicate serializer is introduced. Frames are deflated and stored as
 * numbered files: {@code keyframe-0000000123.kf}, where the number is the event seq.</p>
 */
public final class KeyframeStore{
    private static final String prefix = "keyframe-", suffix = ".kf";

    private final Path directory;

    public KeyframeStore(Path directory){
        this.directory = directory;
    }

    /** Writes the current world as a keyframe for the given event seq. */
    public void write(long seq, Player player) throws IOException{
        Files.createDirectories(directory);
        Path file = directory.resolve(prefix + String.format("%010d", seq) + suffix);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        //NetworkIO.loadWorld reads the raw NetworkIO format; do NOT wrap in DeflaterOutputStream.
        try(OutputStream out = new BufferedOutputStream(Files.newOutputStream(tmp))){
            NetworkIO.writeWorld(player, out);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        Log.info("[online] keyframe @ written (@ bytes)", seq, Files.size(file));
    }

    /** @return the newest keyframe with {@code seq <= limit}, or null when none exists. */
    public @arc.util.Nullable Keyframe latest(long limit) throws IOException{
        Files.createDirectories(directory);
        long bestSeq = -1;
        Path best = null;
        try(DirectoryStream<Path> files = Files.newDirectoryStream(directory, prefix + "*" + suffix)){
            for(Path file : files){
                long seq = parseSeq(file);
                if(seq >= 0 && seq <= limit && seq > bestSeq){ bestSeq = seq; best = file; }
            }
        }
        return best == null ? null : new Keyframe(bestSeq, best);
    }

    /** Loads a keyframe into the current world. Call on the game thread. */
    public void load(Keyframe keyframe) throws IOException{
        try(InputStream in = new BufferedInputStream(Files.newInputStream(keyframe.file))){
            NetworkIO.loadWorld(in);
        }
        Log.info("[online] keyframe @ loaded", keyframe.seq);
    }

    /** Removes keyframes older than the newest one, keeping {@code keep} most recent. */
    public int prune(int keep) throws IOException{
        Files.createDirectories(directory);
        java.util.List<Path> all = new java.util.ArrayList<>();
        try(DirectoryStream<Path> files = Files.newDirectoryStream(directory, prefix + "*" + suffix)){
            files.forEach(all::add);
        }
        all.sort((a, b) -> Long.compare(parseSeq(b), parseSeq(a)));
        int deleted = 0;
        for(int i = keep; i < all.size(); i++){
            if(Files.deleteIfExists(all.get(i))) deleted++;
        }
        return deleted;
    }

    private static long parseSeq(Path file){
        String name = file.getFileName().toString();
        if(!name.startsWith(prefix) || !name.endsWith(suffix)) return -1;
        try{
            return Long.parseLong(name.substring(prefix.length(), name.length() - suffix.length()));
        }catch(NumberFormatException invalid){
            return -1;
        }
    }

    /** A keyframe file plus the event seq it corresponds to. */
    public record Keyframe(long seq, Path file){}
}
