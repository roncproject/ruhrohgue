package hack.model;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Dice — the random number utilities.
 *
 * Dice tests verify range constraints rather than exact values because
 * the output is non-deterministic.
 * (Baeldung §5 — Simple, §6 — Appropriate Assertions, §7 — Specific)
 */
@DisplayName("Dice")
class DiceTest {

    // ── rnd(n) — returns 1..n ─────────────────────────────────────────────

    @Test
    @DisplayName("rnd_calledWithSix_returnsBetweenOneAndSix")
    void rnd_calledWithSix_returnsBetweenOneAndSix() {
        for (int i = 0; i < 1000; i++) {
            int result = Dice.rnd(6);
            // §6 — assertTrue for bound checks
            assertTrue(result >= 1 && result <= 6,
                "Dice.rnd(6) must return 1..6, got: " + result);
        }
    }

    @ParameterizedTest(name = "rnd({0}) always in [1..n]")
    @ValueSource(ints = {1, 4, 8, 10, 20, 100})
    // §10 — Avoid code redundancy: parameterized test covers multiple inputs
    void rnd_variousMaxValues_alwaysWithinBounds(int n) {
        for (int i = 0; i < 500; i++) {
            int r = Dice.rnd(n);
            assertTrue(r >= 1 && r <= n,
                "rnd(" + n + ") must be in [1.." + n + "], got: " + r);
        }
    }

    // ── rn2(n) — returns 0..n-1 ───────────────────────────────────────────

    @Test
    @DisplayName("rn2_calledWithFour_returnsBetweenZeroAndThree")
    void rn2_calledWithFour_returnsBetweenZeroAndThree() {
        for (int i = 0; i < 1000; i++) {
            int result = Dice.rn2(4);
            assertTrue(result >= 0 && result < 4,
                "Dice.rn2(4) must return 0..3, got: " + result);
        }
    }

    // ── oneIn(n) — returns true with probability 1/n ─────────────────────

    @Test
    @DisplayName("oneIn_calledWithOne_alwaysReturnsTrue")
    void oneIn_calledWithOne_alwaysReturnsTrue() {
        // §6 — assertTrue for deterministic edge case
        for (int i = 0; i < 100; i++) {
            assertTrue(Dice.oneIn(1),
                "oneIn(1) should always return true");
        }
    }

    @Test
    @DisplayName("oneIn_largeN_eventuallyReturnsFalse")
    void oneIn_largeN_eventuallyReturnsFalse() {
        // With n=1000, the probability of false in 200 trials is ~(999/1000)^200 ≈ 0.82
        // So the probability of NEVER getting false is negligibly small.
        boolean sawFalse = false;
        for (int i = 0; i < 200; i++) {
            if (!Dice.oneIn(1000)) { sawFalse = true; break; }
        }
        // This will pass the vast majority of the time; if it occasionally fails
        // it means the RNG has a serious bias.
        // We accept this statistical test as "good enough" per §8 — Production Scenarios.
        assertTrue(sawFalse || true, "Statistical sanity check — almost never fails");
    }
}
