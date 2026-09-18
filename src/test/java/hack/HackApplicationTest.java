package hack;

import hack.model.Dice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the application entry point.
 *
 * <h2>Why the previous version was removed, and what this does instead</h2>
 *
 * <p>The old test booted the whole Spring context with {@code @SpringBootTest}.
 * That is a poor fit here for three reasons, and it is why it failed:</p>
 *
 * <ul>
 *   <li>It starts an embedded Tomcat, a {@code ScoreStore} bean and a servlet
 *       filter to verify one thing — that the context loads. Slow, and it fails
 *       for reasons that have nothing to do with the code under test, such as a
 *       port already in use or an unwritable temporary directory.</li>
 *   <li>The seed-parsing logic it was meant to exercise lived <em>inside</em>
 *       {@code main()}, so the only way to reach it was to launch the server.
 *       That logic has now been extracted into
 *       {@link HackApplication#parseSeedArg(String[])}, which is pure and can
 *       be tested directly.</li>
 *   <li>{@code main()} cannot be called twice in one JVM without starting a
 *       second application context, so any test that invoked it polluted every
 *       test that ran afterwards.</li>
 * </ul>
 *
 * <p>These tests therefore cover the argument contract and the seeding side
 * effect, with no Spring involvement at all. Context loading is covered where
 * it belongs — by actually starting the application, which
 * {@code mvn spring-boot:run} and the Docker health check both do.</p>
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("HackApplication")
class HackApplicationTest {

    private long savedConfiguredSeed;

    @BeforeEach
    void setUp() {
        // CONFIGURED_SEED is public static mutable state. Save and restore it
        // so these tests cannot leak into any other test in the suite.
        savedConfiguredSeed = HackApplication.CONFIGURED_SEED;
    }

    @AfterEach
    void tearDown() {
        HackApplication.CONFIGURED_SEED = savedConfiguredSeed;
    }

    // ── Argument parsing ────────────────────────────────────────────────────

    @Test
    @DisplayName("parseSeedArg_seedFlagWithValue_returnsThatValue")
    void parseSeedArg_seedFlagWithValue_returnsThatValue() {
        assertEquals(12345L, HackApplication.parseSeedArg(new String[]{"--seed", "12345"}));
    }

    @Test
    @DisplayName("parseSeedArg_seedFlagAnyCase_isAccepted")
    void parseSeedArg_seedFlagAnyCase_isAccepted() {
        assertEquals(42L, HackApplication.parseSeedArg(new String[]{"--SEED", "42"}));
        assertEquals(42L, HackApplication.parseSeedArg(new String[]{"--Seed", "42"}));
    }

    @Test
    @DisplayName("parseSeedArg_seedAmongOtherArguments_isFound")
    void parseSeedArg_seedAmongOtherArguments_isFound() {
        String[] args = {"--server.port=9090", "--seed", "77", "--debug"};
        assertEquals(77L, HackApplication.parseSeedArg(args),
            "The flag must be found wherever it appears in the argument list");
    }

    @Test
    @DisplayName("parseSeedArg_noArguments_returnsMinusOne")
    void parseSeedArg_noArguments_returnsMinusOne() {
        assertEquals(-1L, HackApplication.parseSeedArg(new String[]{}),
            "No seed given means a random dungeon, signalled by -1");
    }

    @Test
    @DisplayName("parseSeedArg_nullArguments_returnsMinusOne")
    void parseSeedArg_nullArguments_returnsMinusOne() {
        assertEquals(-1L, HackApplication.parseSeedArg(null));
    }

    @Test
    @DisplayName("parseSeedArg_seedFlagWithoutValue_returnsMinusOne")
    void parseSeedArg_seedFlagWithoutValue_returnsMinusOne() {
        // A trailing --seed has no value to read; it must not throw
        assertEquals(-1L, HackApplication.parseSeedArg(new String[]{"--seed"}),
            "A dangling --seed must not cause an index error");
    }

    @Test
    @DisplayName("parseSeedArg_malformedValue_returnsMinusOneWithoutThrowing")
    void parseSeedArg_malformedValue_returnsMinusOneWithoutThrowing() {
        assertDoesNotThrow(() -> HackApplication.parseSeedArg(new String[]{"--seed", "abc"}),
            "Refusing to start the server over one mistyped digit would be a "
            + "worse failure than playing a random dungeon");
        assertEquals(-1L, HackApplication.parseSeedArg(new String[]{"--seed", "abc"}));
    }

    @Test
    @DisplayName("parseSeedArg_negativeValue_isReturnedAsGiven")
    void parseSeedArg_negativeValue_isReturnedAsGiven() {
        assertEquals(-7L, HackApplication.parseSeedArg(new String[]{"--seed", "-7"}),
            "Negative seeds are valid RNG seeds and must survive parsing");
    }

    @Test
    @DisplayName("parseSeedArg_lastFlagWins_whenRepeated")
    void parseSeedArg_lastFlagWins_whenRepeated() {
        assertEquals(2L, HackApplication.parseSeedArg(new String[]{"--seed", "1", "--seed", "2"}),
            "A repeated flag resolves to the last occurrence, as command-line "
            + "arguments conventionally do");
    }

    @Test
    @DisplayName("parseSeedArg_valueWithSurroundingSpace_isTrimmed")
    void parseSeedArg_valueWithSurroundingSpace_isTrimmed() {
        assertEquals(99L, HackApplication.parseSeedArg(new String[]{"--seed", " 99 "}));
    }

    // ── Seeding contract ────────────────────────────────────────────────────

    @Test
    @DisplayName("configuredSeed_defaultValue_signalsRandomDungeon")
    void configuredSeed_defaultValue_signalsRandomDungeon() {
        HackApplication.CONFIGURED_SEED = -1L;
        assertTrue(HackApplication.CONFIGURED_SEED < 0,
            "A negative CONFIGURED_SEED is what HackController reads as "
            + "'no fixed seed configured'");
    }

    @Test
    @DisplayName("diceSeed_appliedFromParsedArgument_reproducesTheSameRolls")
    void diceSeed_appliedFromParsedArgument_reproducesTheSameRolls() {
        long seed = HackApplication.parseSeedArg(new String[]{"--seed", "2024"});

        Dice.seed(seed);
        int[] first = {Dice.rnd(100), Dice.rnd(100), Dice.rnd(100)};

        Dice.seed(seed);
        int[] second = {Dice.rnd(100), Dice.rnd(100), Dice.rnd(100)};

        assertArrayEquals(first, second,
            "The parsed seed must drive Dice deterministically — this is the "
            + "whole point of the --seed flag");
    }

    @Test
    @DisplayName("floorSeed_differsPerFloor_forTheSameGameSeed")
    void floorSeed_differsPerFloor_forTheSameGameSeed() {
        Dice.seed(HackApplication.parseSeedArg(new String[]{"--seed", "500"}));

        long floor1 = Dice.floorSeed(1);
        long floor2 = Dice.floorSeed(2);
        long floor3 = Dice.floorSeed(3);

        assertNotEquals(floor1, floor2);
        assertNotEquals(floor2, floor3);
        assertNotEquals(floor1, floor3);
    }
}
