package hack.web;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the optional Seed field on the new-game screen.
 *
 * A blank or malformed field must mean "no seed given" rather than an error,
 * so a typo quietly falls back to a random dungeon instead of rejecting the
 * new-game request.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("HackController seed parsing")
class SeedParseTest {

    @Test
    @DisplayName("parseSeed_plainNumber_returnsValue")
    void parseSeed_plainNumber_returnsValue() {
        assertEquals(Long.valueOf(1L), HackController.parseSeed("1"));
    }

    @Test
    @DisplayName("parseSeed_surroundedByWhitespace_isTrimmed")
    void parseSeed_surroundedByWhitespace_isTrimmed() {
        assertEquals(Long.valueOf(42L), HackController.parseSeed("  42  "));
    }

    @Test
    @DisplayName("parseSeed_negativeNumber_returnsValue")
    void parseSeed_negativeNumber_returnsValue() {
        assertEquals(Long.valueOf(-7L), HackController.parseSeed("-7"));
    }

    @Test
    @DisplayName("parseSeed_nullInput_returnsNull")
    void parseSeed_nullInput_returnsNull() {
        assertNull(HackController.parseSeed(null),
            "A missing field means no seed was requested");
    }

    @Test
    @DisplayName("parseSeed_blankInput_returnsNull")
    void parseSeed_blankInput_returnsNull() {
        assertNull(HackController.parseSeed("   "),
            "A blank field means no seed was requested");
    }

    @Test
    @DisplayName("parseSeed_nonNumericInput_returnsNull")
    void parseSeed_nonNumericInput_returnsNull() {
        assertNull(HackController.parseSeed("abc"),
            "A malformed seed must fall back to a random dungeon, not an error");
    }
}
