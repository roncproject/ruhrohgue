package hack.web;

import hack.model.HighScoreEntry;
import hack.web.storage.FileScoreStore;
import hack.web.storage.ScoreStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persists the Magnificent 7 high-score table.
 */
@Component
public class HighScoreService {

    private static final int TOP_N = 7;
    /** Maximum records retained, to bound storage growth. */
    private static final int MAX_RETAINED = 100;

    private final ScoreStore store;
    private final List<HighScoreEntry> entries = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * @param store where records are persisted; supplied by Spring
     */
    @Autowired
    public HighScoreService(ScoreStore store) {
        this.store = store;
        load();
    }

    /**
     * Test constructor: writes to a specific file.
     * Marked deprecated so Spring's autowiring engine skips it entirely during scan loops.
     *
     * @param scoreFile path to a writable file
     */
    @Deprecated
    HighScoreService(Path scoreFile) {
        this(new FileScoreStore(scoreFile));
    }

    /** A description of where records are stored, for operators. */
    public String scoreFilePath() { return store.describe(); }

    // -- Queries -------------------------------------------------------------

    /**
     * Adds an entry, re-sorts and persists.
     *
     * @param e the completed game record
     * @return a snapshot of the top seven
     */
    public List<HighScoreEntry> addAndGet(HighScoreEntry e) {
        lock.writeLock().lock();
        try {
            entries.add(e);
            Collections.sort(entries);          // highest score first
            save();
            return snapshotTopN();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Returns the current top seven without modifying anything. */
    public List<HighScoreEntry> topN() {
        lock.readLock().lock();
        try {
            return snapshotTopN();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Removes every entry and wipes the persisted records.
     * Exposed through {@code DELETE /dev/scores} for development resets.
     */
    public void clearAll() {
        lock.writeLock().lock();
        try {
            entries.clear();
            store.deleteAll();
        } catch (IOException ex) {
            System.err.println("[HighScoreService] Could not clear scores: " + ex.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Caller must hold a lock. */
    private List<HighScoreEntry> snapshotTopN() {
        return new ArrayList<>(entries.subList(0, Math.min(TOP_N, entries.size())));
    }

    // -- Persistence ---------------------------------------------------------

    private void load() {
        lock.writeLock().lock();
        try {
            for (String line : store.readLines()) {
                HighScoreEntry e = HighScoreEntry.fromJson(line);
                if (e != null) entries.add(e);
            }
            Collections.sort(entries);
        } catch (IOException ex) {
            System.err.println("[HighScoreService] Could not load scores: " + ex.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Caller must hold the write lock. */
    private void save() {
        int limit = Math.min(MAX_RETAINED, entries.size());
        List<String> lines = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) lines.add(entries.get(i).toJson());
        try {
            store.writeLines(lines);
        } catch (IOException ex) {
            System.err.println("[HighScoreService] Could not save scores: " + ex.getMessage());
        }
    }
}
