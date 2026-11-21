package orbital.net.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/** Keyframe selection by seq, without needing a live world. */
class KeyframeStoreTest{
    @TempDir Path temp;

    private static void touch(Path dir, long seq) throws IOException{
        Files.createDirectories(dir);
        Files.write(dir.resolve(String.format("keyframe-%010d.kf", seq)), new byte[]{1, 2, 3});
    }

    @Test void latestRespectsLimit() throws IOException{
        Path dir = temp.resolve("kf1");
        KeyframeStore store = new KeyframeStore(dir);
        touch(dir, 100);
        touch(dir, 200);
        touch(dir, 300);

        KeyframeStore.Keyframe kf = store.latest(250);
        assertNotNull(kf);
        assertEquals(200, kf.seq());

        assertEquals(300, store.latest(999).seq());
        assertNull(store.latest(50), "no keyframe should predate the cursor");
    }

    @Test void pruneKeepsNewest() throws IOException{
        Path dir = temp.resolve("kf2");
        KeyframeStore store = new KeyframeStore(dir);
        for(long seq : new long[]{100, 200, 300, 400, 500}) touch(dir, seq);

        assertEquals(2, store.prune(3));
        assertNull(fromFile(dir, 100));
        assertNull(fromFile(dir, 200));
        assertNotNull(fromFile(dir, 300));
        assertEquals(500, store.latest(999).seq());
    }

    private static Path fromFile(Path dir, long seq){
        Path p = dir.resolve(String.format("keyframe-%010d.kf", seq));
        return Files.exists(p) ? p : null;
    }
}
