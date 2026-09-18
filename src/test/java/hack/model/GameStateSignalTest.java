package hack.model;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the one-shot UI signal channel and the auto-describe bookkeeping.
 *
 * Root cause of the original failures:
 *   Bracketed control strings such as "[look:tripe ration]" were pushed
 *   through pline() into the visible message log. They stayed at
 *   messages[0] until some other message displaced them, so the browser
 *   re-opened the look overlay on every later state refresh, and the raw
 *   control string was printed in the upper message bar.
 *   Fix: pline() routes bracketed strings into a transient uiSignal field
 *   that consumeSignal() clears on read.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("GameState UI signals")
class GameStateSignalTest {

    private GameState state;

    @BeforeEach
    void setUp() {
        Dice.seed(99L);
        state = new GameState();
        state.startNewGame("T01", "Fighter");
    }

    @Test
    @DisplayName("pline_bracketedSignal_isNotAddedToMessageLog")
    void pline_bracketedSignal_isNotAddedToMessageLog() {
        int before = state.getMessages().size();
        state.pline("[look:tripe ration]");
        assertEquals(before, state.getMessages().size(),
            "A UI signal must never enter the visible message log");
    }

    @Test
    @DisplayName("pline_bracketedSignal_doesNotReachTopMessageBar")
    void pline_bracketedSignal_doesNotReachTopMessageBar() {
        state.pline("You hit the kobold!");
        state.pline("[look:tripe ration]");
        assertEquals("You hit the kobold!", state.getTopMessage(),
            "A UI signal must leave the upper message bar untouched");
    }

    @Test
    @DisplayName("consumeSignal_calledTwice_returnsSignalThenNull")
    void consumeSignal_calledTwice_returnsSignalThenNull() {
        state.pline("[look:tripe ration]");
        assertEquals("[look:tripe ration]", state.consumeSignal(),
            "The first read must deliver the pending signal");
        assertNull(state.consumeSignal(),
            "The signal must be cleared on read so the overlay cannot replay");
    }

    @Test
    @DisplayName("pline_ordinaryMessage_isLoggedAndShownOnTop")
    void pline_ordinaryMessage_isLoggedAndShownOnTop() {
        int before = state.getMessages().size();
        state.pline("You see here: tripe ration.");
        assertEquals(before + 1, state.getMessages().size(),
            "Ordinary messages must still be logged");
        assertEquals("You see here: tripe ration.", state.getTopMessage(),
            "Ordinary messages must still reach the upper message bar");
    }

    @Test
    @DisplayName("markDescribed_sameTileTwice_returnsFalseOnSecondCall")
    void markDescribed_sameTileTwice_returnsFalseOnSecondCall() {
        assertTrue(state.markDescribed(10, 5),
            "Arriving on a tile must allow one description");
        assertFalse(state.markDescribed(10, 5),
            "Standing still on the same tile must not repeat the description");
    }

    @Test
    @DisplayName("markDescribed_newTile_returnsTrue")
    void markDescribed_newTile_returnsTrue() {
        state.markDescribed(10, 5);
        assertTrue(state.markDescribed(11, 5),
            "Stepping onto a different tile must allow a fresh description");
    }

    @Test
    @DisplayName("resetDescribed_afterMarking_allowsSameTileAgain")
    void resetDescribed_afterMarking_allowsSameTileAgain() {
        state.markDescribed(10, 5);
        state.resetDescribed();
        assertTrue(state.markDescribed(10, 5),
            "After a reset the same tile may be described again");
    }
}
