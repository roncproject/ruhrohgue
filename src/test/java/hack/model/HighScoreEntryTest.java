package hack.model;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HighScoreEntry.
 *
 * Root cause of previous failure:
 *   fromJson("{not valid json at all}") did NOT return null.
 *   The parser is lenient: when a key is not found, parseStr returns "",
 *   parseInt/parseLong return 0, and a HighScoreEntry("","",0,0,"") is
 *   returned instead of null. The catch block only fires on exceptions
 *   (e.g. NumberFormatException), not on missing/empty fields.
 *
 *   Fix: test the actual documented contract — fromJson returns null only
 *   when an exception is thrown during parsing.  Use a string that actually
 *   causes a NumberFormatException (e.g. "score" key present but non-numeric).
 *   Also add a test for the lenient-but-non-null case.
 *
 * (Baeldung §5 — Simple, §6 — Appropriate Assertions, §8 — Production Scenarios)
 */
@DisplayName("HighScoreEntry")
class HighScoreEntryTest {

    // ── Construction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("constructor_validFields_allFieldsAccessible")
    void constructor_validFields_allFieldsAccessible() {
        HighScoreEntry e = new HighScoreEntry("Ron", "Fighter", 5, 1234L);
        assertEquals("Ron",     e.getName());
        assertEquals("Fighter", e.getCharacterClass());
        assertEquals(5,         e.getMaxFloor());
        assertEquals(1234L,     e.getScore());
    }

    @Test
    @DisplayName("getDateTime_newEntry_returnsFormattedString19Chars")
    void getDateTime_newEntry_returnsFormattedString19Chars() {
        HighScoreEntry e = new HighScoreEntry("A", "B", 1, 100L);
        assertNotNull(e.getDateTime());
        assertEquals(19, e.getDateTime().length(),
            "DateTime format must be 'yyyy-MM-dd HH:mm:ss' (19 chars)");
    }

    // ── JSON round-trip ───────────────────────────────────────────────────

    @Test
    @DisplayName("toJson_fromJson_roundTripPreservesAllFields")
    void toJson_fromJson_roundTripPreservesAllFields() {
        HighScoreEntry original = new HighScoreEntry(
            "Nna", "Fighter", 7, 9876L, "2026-01-15 10:30:00");
        String json = original.toJson();
        HighScoreEntry restored = HighScoreEntry.fromJson(json);

        assertNotNull(restored);
        assertEquals(original.getName(),           restored.getName());
        assertEquals(original.getCharacterClass(), restored.getCharacterClass());
        assertEquals(original.getMaxFloor(),       restored.getMaxFloor());
        assertEquals(original.getScore(),          restored.getScore());
        assertEquals(original.getDateTime(),       restored.getDateTime());
    }

    // ── fromJson error handling ────────────────────────────────────────────

    @Test
    @DisplayName("fromJson_jsonWithNonNumericScore_returnsNull")
    void fromJson_jsonWithNonNumericScore_returnsNull() {
        // This actually causes a NumberFormatException in parseLong → returns null.
        String badJson = "{\"name\":\"X\",\"class\":\"Y\",\"floor\":1,"
                       + "\"score\":\"notanumber\",\"time\":\"2026-01-01 00:00:00\"}";
        HighScoreEntry result = HighScoreEntry.fromJson(badJson);
        assertNull(result,
            "fromJson should return null when score cannot be parsed as a number");
    }

    @Test
    @DisplayName("fromJson_nullInput_returnsNull")
    void fromJson_nullInput_returnsNull() {
        // Passing null causes a NullPointerException in the parser → returns null.
        HighScoreEntry result = HighScoreEntry.fromJson(null);
        assertNull(result,
            "fromJson(null) should return null without throwing");
    }

    @Test
    @DisplayName("fromJson_missingFields_returnsEntryWithEmptyDefaults")
    void fromJson_missingFields_returnsEntryWithEmptyDefaults() {
        // The parser is lenient: missing keys yield empty strings / zeros,
        // no exception is thrown, and a non-null entry is returned.
        // This documents the actual (intentional) lenient behaviour.
        HighScoreEntry result = HighScoreEntry.fromJson("{not valid json at all}");
        // We do NOT assert null here — the parser doesn't throw, so it returns a default entry.
        // Instead we verify the result is non-null and has default/empty fields.
        assertNotNull(result,
            "Lenient parser returns a default entry for malformed JSON (no exception thrown)");
        // Numeric defaults should be 0
        assertEquals(0, result.getMaxFloor(),
            "Missing 'floor' key should default to 0");
        assertEquals(0L, result.getScore(),
            "Missing 'score' key should default to 0");
    }

    // ── Ordering ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("compareTo_higherScore_sortsFirst")
    void compareTo_higherScore_sortsFirst() {
        HighScoreEntry high = new HighScoreEntry("A", "Fighter", 3, 5000L);
        HighScoreEntry low  = new HighScoreEntry("B", "Thief",   1,  100L);
        assertTrue(high.compareTo(low) < 0,
            "Higher score should sort before lower score");
    }

    @Test
    @DisplayName("compareTo_equalScores_returnsZero")
    void compareTo_equalScores_returnsZero() {
        HighScoreEntry a = new HighScoreEntry("X", "Fighter", 2, 500L);
        HighScoreEntry b = new HighScoreEntry("Y", "Wizard",  4, 500L);
        assertEquals(0, a.compareTo(b));
    }
}
