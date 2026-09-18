package hack.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the player-name validation rule.
 *
 * The field is three characters long, which reads as harmless. It is not: the
 * value is persisted into the JSON-Lines score file by string concatenation
 * and later rendered on the score board, so an unrestricted field is a stored
 * XSS and JSON-injection vector. These tests pin the rule that closes it.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("Player name validation")
class NewGameRequestTest {

    @Test
    @DisplayName("isValidName_threeAlphanumerics_isAccepted")
    void isValidName_threeAlphanumerics_isAccepted() {
        assertTrue(NewGameRequest.isValidName("T01"));
        assertTrue(NewGameRequest.isValidName("abc"));
        assertTrue(NewGameRequest.isValidName("XYZ"));
        assertTrue(NewGameRequest.isValidName("999"));
    }

    @Test
    @DisplayName("isValidName_wrongLength_isRejected")
    void isValidName_wrongLength_isRejected() {
        assertFalse(NewGameRequest.isValidName(""),     "empty");
        assertFalse(NewGameRequest.isValidName("A"),    "one character");
        assertFalse(NewGameRequest.isValidName("AB"),   "two characters");
        assertFalse(NewGameRequest.isValidName("ABCD"), "four characters");
    }

    @Test
    @DisplayName("isValidName_nullInput_isRejected")
    void isValidName_nullInput_isRejected() {
        assertFalse(NewGameRequest.isValidName(null));
    }

    @Test
    @DisplayName("isValidName_scriptInjectionAttempts_areRejected")
    void isValidName_scriptInjectionAttempts_areRejected() {
        // Stored XSS: these end up on the score board
        assertFalse(NewGameRequest.isValidName("<b>"));
        assertFalse(NewGameRequest.isValidName("\"><"));
        assertFalse(NewGameRequest.isValidName("'`;"));
        assertFalse(NewGameRequest.isValidName("&lt"));
    }

    @Test
    @DisplayName("isValidName_jsonSplittingAttempts_areRejected")
    void isValidName_jsonSplittingAttempts_areRejected() {
        // JSON syntax splitting: the score file is JSON Lines
        assertFalse(NewGameRequest.isValidName("a\"b"));
        assertFalse(NewGameRequest.isValidName("a\\b"));
        assertFalse(NewGameRequest.isValidName("}{}"));
        assertFalse(NewGameRequest.isValidName("a:b"));
    }

    @Test
    @DisplayName("isValidName_pathTraversalAttempts_areRejected")
    void isValidName_pathTraversalAttempts_areRejected() {
        assertFalse(NewGameRequest.isValidName("../"));
        assertFalse(NewGameRequest.isValidName("..\\"));
        assertFalse(NewGameRequest.isValidName("/et"));
    }

    @Test
    @DisplayName("isValidName_whitespaceAndControlCharacters_areRejected")
    void isValidName_whitespaceAndControlCharacters_areRejected() {
        assertFalse(NewGameRequest.isValidName("a b"));
        assertFalse(NewGameRequest.isValidName("a\nb"));
        assertFalse(NewGameRequest.isValidName("a\tb"));
        assertFalse(NewGameRequest.isValidName("   "));
    }

    @Test
    @DisplayName("isValidName_nonAsciiLetters_areRejected")
    void isValidName_nonAsciiLetters_areRejected() {
        // The pattern is deliberately ASCII-only: it must be trivially
        // auditable, and the score board is a fixed-width terminal grid.
        assertFalse(NewGameRequest.isValidName("日本語"));
        assertFalse(NewGameRequest.isValidName("éé é".substring(0, 3)));
    }
}
