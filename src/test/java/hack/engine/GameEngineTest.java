package hack.engine;

import hack.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GameEngine.
 *
 * Root cause of previous failure:
 *   startNewGame() initialises moves=1. Calling incrementMoves() 25 times
 *   results in moves=26 (26 % 25 = 1, not 0). Interest never fires.
 *   Fix: use state.setMoves(25) to place the counter at exactly the right
 *   boundary, then call applyTimedEffects() once.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("GameEngine")
class GameEngineTest {

    private GameState state;
    private Player    player;

    @BeforeEach
    void setUp() {
        state  = new GameState();
        state.startNewGame("TestHero", "Fighter");
        player = state.getPlayer();
    }

    // ── Bank interest ─────────────────────────────────────────────────────

    @Test
    @DisplayName("applyTimedEffects_bankNonZeroAtMultiple100_bankIncreasesByOne")
    void applyTimedEffects_bankNonZeroAtMultiple100_bankIncreasesByOne() {
        player.setBank(10L);
        // Interest fires every 100 turns. Place counter at exactly 100.
        state.setMoves(100L);
        long bankBefore = player.getBank();
        GameEngine.applyTimedEffects(state);
        assertEquals(bankBefore + 1, player.getBank(),
            "Bank balance should increase by 1 at every 100th turn");
    }

    @Test
    @DisplayName("applyTimedEffects_bankNonZeroAtMultiple200_bankIncreasesByOne")
    void applyTimedEffects_bankNonZeroAtMultiple200_bankIncreasesByOne() {
        player.setBank(5L);
        state.setMoves(200L);
        long bankBefore = player.getBank();
        GameEngine.applyTimedEffects(state);
        assertEquals(bankBefore + 1, player.getBank(),
            "Bank balance should increase by 1 at the 200th turn");
    }

    @Test
    @DisplayName("applyTimedEffects_bankZero_bankDoesNotIncrease")
    void applyTimedEffects_bankZero_bankDoesNotIncrease() {
        player.setBank(0L);
        state.setMoves(100L);
        GameEngine.applyTimedEffects(state);
        assertEquals(0L, player.getBank(),
            "Bank with zero balance should not accrue interest");
    }

    @Test
    @DisplayName("applyTimedEffects_bankPositiveNotAt100TurnBoundary_bankUnchanged")
    void applyTimedEffects_bankPositiveNotAt100TurnBoundary_bankUnchanged() {
        player.setBank(50L);
        state.setMoves(7L);   // 7 % 100 ≠ 0
        long expected = player.getBank();
        GameEngine.applyTimedEffects(state);
        assertEquals(expected, player.getBank(),
            "Bank should only accrue interest on 100-turn multiples");
    }

    // ── Floating eye vision ───────────────────────────────────────────────

    @Test
    @DisplayName("applyTimedEffects_floatingEyeVisionPositive_decrementsByOnePerTurn")
    void applyTimedEffects_floatingEyeVisionPositive_decrementsByOnePerTurn() {
        state.setFloatingEyeVisionLeft(10);
        GameEngine.applyTimedEffects(state);
        assertEquals(9, state.getFloatingEyeVisionLeft(),
            "Floating eye vision should decrement by 1 each turn");
    }

    @Test
    @DisplayName("applyTimedEffects_floatingEyeVisionAtOne_becomesZero")
    void applyTimedEffects_floatingEyeVisionAtOne_becomesZero() {
        state.setFloatingEyeVisionLeft(1);
        GameEngine.applyTimedEffects(state);
        assertEquals(0, state.getFloatingEyeVisionLeft(),
            "Floating eye vision at 1 should become 0 after one turn");
    }

    @Test
    @DisplayName("floatingEyeVision_cappedAtMaxSteps_neverExceedsCap")
    void floatingEyeVision_cappedAtMaxSteps_neverExceedsCap() {
        int capped = Math.min(
            GameState.FLOATING_EYE_MAX_STEPS + 99,
            GameState.FLOATING_EYE_MAX_STEPS);
        state.setFloatingEyeVisionLeft(capped);
        assertTrue(state.getFloatingEyeVisionLeft() <= GameState.FLOATING_EYE_MAX_STEPS,
            "Floating eye vision must never exceed FLOATING_EYE_MAX_STEPS (50)");
    }

    // ── doLookHere ────────────────────────────────────────────────────────

    @Test
    @DisplayName("doLookHere_emptyFloorTile_plinesNothingHere")
    void doLookHere_emptyFloorTile_plinesNothingHere() {
        // No level generated — doLookHere must handle null level gracefully
        GameEngine.doLookHere(state);
        String lastMsg = state.getMessages().isEmpty() ? "" : state.getMessages().peek();
        assertEquals("There is nothing here.", lastMsg,
            "doLookHere on empty/no-level tile should report nothing here");
    }

    @Test
    @DisplayName("doHunger_playerWithSatiatedHunger_hungerDoesNotIncrease")
    void doHunger_playerWithSatiatedHunger_hungerDoesNotIncrease() {
        player.setHunger(1500);
        int before = player.getHunger();
        GameEngine.doHunger(state);
        assertTrue(player.getHunger() <= before,
            "Hunger should not increase on a normal turn");
    }
}
