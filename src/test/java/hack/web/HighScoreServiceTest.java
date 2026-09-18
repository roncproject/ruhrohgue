package hack.web;

import hack.model.HighScoreEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HighScoreService.
 *
 * Root cause of previous failures:
 *   HighScoreService.SCORE_FILE is a static final field — its value is
 *   assigned exactly once when the class is loaded, BEFORE any test method
 *   runs. System.setProperty("hack.scores.file", ...) in @BeforeEach fires
 *   too late and has NO effect on the already-evaluated static final.
 *   As a result, all test instances shared the same production score file,
 *   causing cross-contamination between tests and unexpected data.
 *
 *   Fix: A package-private constructor HighScoreService(Path) was added to
 *   the production class (same package: hack.web). It accepts an explicit
 *   file path and uses that instead of SCORE_FILE. Each test gets a fresh
 *   isolated file via @TempDir, so tests are fully independent.
 *
 * (Baeldung §9 — Mock External Services, §10 — Avoid Redundancy,
 *               §11 — Annotations)
 */
@DisplayName("HighScoreService")
class HighScoreServiceTest {

    @TempDir
    Path tempDir;

    private HighScoreService service;

    @BeforeEach
    void setUp() {
        // Use the package-private test constructor to pass an isolated file path.
        // This bypasses the static-final SCORE_FILE that would otherwise be shared.
        Path scoreFile = tempDir.resolve("test-scores.json");
        service = new HighScoreService(scoreFile);
    }

    // ── Empty service ─────────────────────────────────────────────────────

    @Test
    @DisplayName("topN_emptyService_returnsEmptyList")
    void topN_emptyService_returnsEmptyList() {
        assertTrue(service.topN().isEmpty(),
            "Fresh service with no entries should return empty top-N list");
    }

    // ── addAndGet ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("addAndGet_singleEntry_returnsListOfSizeOne")
    void addAndGet_singleEntry_returnsListOfSizeOne() {
        List<HighScoreEntry> result = service.addAndGet(
            new HighScoreEntry("Alice", "Fighter", 3, 1000L));
        assertEquals(1, result.size(),
            "After adding one entry, topN should contain exactly one result");
    }

    @Test
    @DisplayName("addAndGet_multipleEntries_returnsSortedHighestFirst")
    void addAndGet_multipleEntries_returnsSortedHighestFirst() {
        service.addAndGet(new HighScoreEntry("Bob",   "Thief",   2,  500L));
        service.addAndGet(new HighScoreEntry("Carol", "Fighter", 5, 3000L));
        List<HighScoreEntry> top = service.addAndGet(
            new HighScoreEntry("Dave", "Wizard", 1, 1500L));

        assertEquals("Carol", top.get(0).getName(),
            "Highest scorer (Carol=3000) should be first");
        assertEquals("Dave",  top.get(1).getName(),
            "Second scorer (Dave=1500) should be second");
        assertEquals("Bob",   top.get(2).getName(),
            "Lowest scorer (Bob=500) should be last");
    }

    @Test
    @DisplayName("addAndGet_moreThanSevenEntries_topNReturnsAtMostSeven")
    void addAndGet_moreThanSevenEntries_topNReturnsAtMostSeven() {
        for (int i = 1; i <= 10; i++) {
            service.addAndGet(new HighScoreEntry("P" + i, "Fighter", i, i * 100L));
        }
        assertTrue(service.topN().size() <= 7,
            "topN should return at most 7 entries (Magnificent 7)");
    }

    // ── Persistence ───────────────────────────────────────────────────────

    @Test
    @DisplayName("addAndGet_entriesPersisted_reloadedServicePreservesRanking")
    void addAndGet_entriesPersisted_reloadedServicePreservesRanking() {
        Path scoreFile = tempDir.resolve("persist-test.json");
        HighScoreService writer = new HighScoreService(scoreFile);
        writer.addAndGet(new HighScoreEntry("Nna",  "Fighter", 4, 2222L));
        writer.addAndGet(new HighScoreEntry("Bla",  "Wizard",  2, 1111L));

        // Reload from the SAME file — simulates a server restart
        HighScoreService reader = new HighScoreService(scoreFile);
        List<HighScoreEntry> top = reader.topN();

        assertFalse(top.isEmpty(), "Reloaded service should have persisted entries");
        assertEquals("Nna", top.get(0).getName(),
            "Highest scorer (Nna=2222) should still be first after reload");
    }
}
