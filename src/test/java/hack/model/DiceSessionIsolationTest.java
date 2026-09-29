package hack.model;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for TRK-02A / TRK-02B.
 *
 * <p>Dice used to hold its RNG, game seed, and level-generation state as
 * plain static fields shared by every browser session on the server. A
 * {@code /new} in one session reseeded the stream every other session was
 * reading from (TRK-02A), and two sessions generating a floor at the same
 * time raced on the same non-atomic {@code generationDepth} /
 * {@code savedState} fields (TRK-02B). These tests exercise exactly that
 * scenario — two independent {@link Dice.State} instances, switched on the
 * same thread and driven from separate threads — and assert neither
 * disturbs the other, which the pre-fix static implementation could not
 * have passed.
 */
@DisplayName("Dice session isolation (TRK-02A / TRK-02B)")
class DiceSessionIsolationTest {

    @AfterEach
    void clearBinding() {
        // Don't leak a binding from one test into the next.
        Dice.unbind();
    }

    @Test
    @DisplayName("seed_inOneSession_doesNotDisturbAnotherSessionsStream")
    void seed_inOneSession_doesNotDisturbAnotherSessionsStream() {
        Dice.State sessionA = new Dice.State();
        Dice.State sessionB = new Dice.State();

        Dice.bind(sessionA);
        Dice.seed(111L);
        int[] expectedA = new int[20];
        for (int i = 0; i < 5; i++) expectedA[i] = Dice.rnd(1000);

        // Session B starts a brand new game (its own /new) mid-stream for A -
        // this is exactly what the old static Dice.seed() would have
        // reseeded out from under session A.
        Dice.bind(sessionB);
        Dice.seed(999L);
        for (int i = 0; i < 5; i++) Dice.rnd(1000);

        // Back to A: its stream must continue exactly as if B never ran.
        Dice.bind(sessionA);
        for (int i = 5; i < 10; i++) expectedA[i] = Dice.rnd(1000);

        Dice.State replayA = new Dice.State();
        Dice.bind(replayA);
        Dice.seed(111L);
        for (int i = 0; i < 10; i++) {
            assertEquals(expectedA[i], Dice.rnd(1000),
                "session A's draw " + i + " must match an uninterrupted replay of seed 111");
        }
    }

    @Test
    @DisplayName("beginEndLevelGeneration_interleavedAcrossSessions_eachRestoresItsOwnSavedState")
    void beginEndLevelGeneration_interleavedAcrossSessions_eachRestoresItsOwnSavedState() {
        Dice.State sessionA = new Dice.State();
        Dice.State sessionB = new Dice.State();

        Dice.bind(sessionA);
        Dice.seed(1L);
        Dice.rnd(1000); // consume a draw so the main stream has advanced
        Dice.beginLevelGeneration(1);   // captures A's continuation point

        // Session B begins and ENDS its own level generation while A's is
        // still open. Under the old static fields this would stomp the one
        // shared generationDepth/savedState pair.
        Dice.bind(sessionB);
        Dice.seed(2L);
        Dice.beginLevelGeneration(1);
        int bDuringGen = Dice.rnd(1000);
        Dice.endLevelGeneration();
        int bAfterGen = Dice.rnd(1000);

        // A resumes and ends its own generation - must restore A's own
        // pre-generation continuation point, unaffected by B's activity.
        Dice.bind(sessionA);
        int aDuringGen = Dice.rnd(1000);
        Dice.endLevelGeneration();
        int aAfterGen = Dice.rnd(1000);

        // Replay each session alone, uninterrupted, and confirm identical
        // draws - proves the interleaving above changed nothing.
        Dice.State replayA = new Dice.State();
        Dice.bind(replayA);
        Dice.seed(1L);
        Dice.rnd(1000);
        Dice.beginLevelGeneration(1);
        int expectedADuringGen = Dice.rnd(1000);
        Dice.endLevelGeneration();
        int expectedAAfterGen = Dice.rnd(1000);
        assertEquals(expectedADuringGen, aDuringGen, "session A's in-generation draw must be undisturbed by B");
        assertEquals(expectedAAfterGen, aAfterGen, "session A's post-generation draw must be undisturbed by B");

        Dice.State replayB = new Dice.State();
        Dice.bind(replayB);
        Dice.seed(2L);
        Dice.beginLevelGeneration(1);
        int expectedBDuringGen = Dice.rnd(1000);
        Dice.endLevelGeneration();
        int expectedBAfterGen = Dice.rnd(1000);
        assertEquals(expectedBDuringGen, bDuringGen, "session B's in-generation draw must match its own replay");
        assertEquals(expectedBAfterGen, bAfterGen, "session B's post-generation draw must match its own replay");
    }

    @Test
    @DisplayName("concurrentSessions_onRealThreads_eachStreamMatchesItsOwnSingleThreadedReplay")
    void concurrentSessions_onRealThreads_eachStreamMatchesItsOwnSingleThreadedReplay() throws Exception {
        int sessionCount = 8;
        int drawsPerSession = 500;
        ExecutorService pool = Executors.newFixedThreadPool(sessionCount);
        CyclicBarrier barrier = new CyclicBarrier(sessionCount);
        try {
            List<Future<int[]>> futures = new ArrayList<>();
            for (int s = 0; s < sessionCount; s++) {
                final long seed = 1000L + s;
                futures.add(pool.submit(() -> {
                    Dice.State state = new Dice.State();
                    Dice.bind(state);
                    try {
                        barrier.await(); // maximise actual concurrent overlap
                        Dice.seed(seed);
                        int[] draws = new int[drawsPerSession];
                        for (int i = 0; i < drawsPerSession; i++) {
                            if (i % 7 == 0) {
                                Dice.beginLevelGeneration(i);
                                draws[i] = Dice.rnd(1_000_000);
                                Dice.endLevelGeneration();
                            } else {
                                draws[i] = Dice.rnd(1_000_000);
                            }
                        }
                        return draws;
                    } finally {
                        Dice.unbind();
                    }
                }));
            }

            for (int s = 0; s < sessionCount; s++) {
                int[] actual = futures.get(s).get(10, TimeUnit.SECONDS);

                // Single-threaded replay of the exact same seed/operation sequence.
                Dice.State replay = new Dice.State();
                Dice.bind(replay);
                Dice.seed(1000L + s);
                int[] expected = new int[drawsPerSession];
                for (int i = 0; i < drawsPerSession; i++) {
                    if (i % 7 == 0) {
                        Dice.beginLevelGeneration(i);
                        expected[i] = Dice.rnd(1_000_000);
                        Dice.endLevelGeneration();
                    } else {
                        expected[i] = Dice.rnd(1_000_000);
                    }
                }
                Dice.unbind();

                assertArrayEquals(expected, actual,
                    "session " + s + " run under real concurrency must exactly match its own isolated replay");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
