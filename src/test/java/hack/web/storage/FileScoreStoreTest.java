package hack.web.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the file-backed score store.
 *
 * The write must be atomic. The previous implementation opened the real file
 * with a truncating writer and wrote records one at a time, so a crash or a
 * second writer mid-loop left a truncated, unreadable table.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("FileScoreStore")
class FileScoreStoreTest {

    @Test
    @DisplayName("readLines_missingFile_returnsEmptyList")
    void readLines_missingFile_returnsEmptyList(@TempDir Path dir) throws IOException {
        FileScoreStore store = new FileScoreStore(dir.resolve("absent.json"));
        assertTrue(store.readLines().isEmpty(),
            "A table that does not exist yet is not an error");
    }

    @Test
    @DisplayName("writeLines_thenRead_roundTripsContent")
    void writeLines_thenRead_roundTripsContent(@TempDir Path dir) throws IOException {
        FileScoreStore store = new FileScoreStore(dir.resolve("scores.json"));
        store.writeLines(List.of("{\"a\":1}", "{\"b\":2}"));
        assertEquals(List.of("{\"a\":1}", "{\"b\":2}"), store.readLines());
    }

    @Test
    @DisplayName("writeLines_missingParentDirectory_isCreated")
    void writeLines_missingParentDirectory_isCreated(@TempDir Path dir) throws IOException {
        Path nested = dir.resolve("a/b/c/scores.json");
        FileScoreStore store = new FileScoreStore(nested);
        store.writeLines(List.of("{\"x\":1}"));
        assertTrue(Files.exists(nested), "The parent directory must be created on demand");
    }

    @Test
    @DisplayName("writeLines_replacingLargerFile_leavesNoStaleTail")
    void writeLines_replacingLargerFile_leavesNoStaleTail(@TempDir Path dir) throws IOException {
        FileScoreStore store = new FileScoreStore(dir.resolve("scores.json"));
        store.writeLines(List.of("aaaa", "bbbb", "cccc", "dddd"));
        store.writeLines(List.of("z"));
        assertEquals(List.of("z"), store.readLines(),
            "A shorter write must replace the file, not overwrite its head");
    }

    @Test
    @DisplayName("writeLines_concurrentWriters_neverLeaveAPartialFile")
    void writeLines_concurrentWriters_neverLeaveAPartialFile(@TempDir Path dir)
            throws Exception {
        FileScoreStore store = new FileScoreStore(dir.resolve("scores.json"));
        int writers = 8, rounds = 30;

        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        for (int w = 0; w < writers; w++) {
            final int id = w;
            pool.submit(() -> {
                start.await();
                for (int r = 0; r < rounds; r++) {
                    store.writeLines(List.of("writer-" + id + "-round-" + r));
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "writers should finish");

        // Whoever wrote last, the file must be exactly one complete record —
        // never a blend of two writers' output.
        List<String> finalLines = store.readLines();
        assertEquals(1, finalLines.size(), "File must hold one complete write, not a blend");
        assertTrue(finalLines.get(0).matches("writer-\\d+-round-\\d+"),
            "Content must be an intact record, was: " + finalLines.get(0));
    }

    @Test
    @DisplayName("deleteAll_existingFile_removesIt")
    void deleteAll_existingFile_removesIt(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scores.json");
        FileScoreStore store = new FileScoreStore(file);
        store.writeLines(List.of("{\"a\":1}"));
        store.deleteAll();
        assertFalse(Files.exists(file));
        assertTrue(store.readLines().isEmpty());
    }

    @Test
    @DisplayName("describe_returnsResolvedLocation")
    void describe_returnsResolvedLocation(@TempDir Path dir) {
        FileScoreStore store = new FileScoreStore(dir.resolve("scores.json"));
        assertTrue(store.describe().startsWith("file:"));
        assertTrue(store.describe().contains("scores.json"));
    }
}
