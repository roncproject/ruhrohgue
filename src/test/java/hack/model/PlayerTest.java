package hack.model;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Player model — bank, gold, HP, and name.
 *
 * Uses the correct constructor: Player(String name, String characterClass).
 * Player has no setName() — getName() is verified via constructor argument.
 *
 * (Baeldung §3 — Naming Convention, §4 — Expected vs Actual,
 *               §5 — Simple, §6 — Appropriate Assertions)
 */
@DisplayName("Player")
class PlayerTest {

    private Player player;

    @BeforeEach
    void setUp() {
        // Player requires name and characterClass arguments
        player = new Player("TestHero", "Fighter");
    }

    // ── Bank ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getBank_newPlayer_returnsZero")
    void getBank_newPlayer_returnsZero() {
        assertEquals(0L, player.getBank(),
            "A new player's Bank balance should start at 0");
    }

    @Test
    @DisplayName("setBank_positiveAmount_bankUpdated")
    void setBank_positiveAmount_bankUpdated() {
        player.setBank(250L);
        assertEquals(250L, player.getBank(), "Bank should reflect the set value");
    }

    @Test
    @DisplayName("setBank_zeroAfterPositive_bankReturnsToZero")
    void setBank_zeroAfterPositive_bankReturnsToZero() {
        player.setBank(100L);
        player.setBank(0L);
        assertEquals(0L, player.getBank(), "Setting bank to 0 should clear it");
    }

    @Test
    @DisplayName("setBank_largeValue_bankUpdatedCorrectly")
    void setBank_largeValue_bankUpdatedCorrectly() {
        long big = 1_000_000L;
        player.setBank(big);
        assertEquals(big, player.getBank(), "Bank should support large long values");
    }

    // ── Gold ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getGold_newPlayer_returnsZero")
    void getGold_newPlayer_returnsZero() {
        assertEquals(0L, player.getGold(), "New player starts with no gold");
    }

    @Test
    @DisplayName("setGold_validAmount_goldUpdated")
    void setGold_validAmount_goldUpdated() {
        player.setGold(99L);
        assertEquals(99L, player.getGold(), "Gold should match what was set");
    }

    // ── HP ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getHp_newPlayer_hpEqualsHpMax")
    void getHp_newPlayer_hpEqualsHpMax() {
        assertEquals(player.getHpMax(), player.getHp(),
            "New player HP should equal HpMax (full health)");
    }

    @Test
    @DisplayName("setHp_reducedValue_hpReflectsChange")
    void setHp_reducedValue_hpReflectsChange() {
        int half = player.getHp() / 2;
        player.setHp(half);
        assertEquals(half, player.getHp(), "HP should reflect damage taken");
    }

    // ── Name (read-only after construction) ────────────────────────────────

    @Test
    @DisplayName("getName_constructedWithName_returnsCorrectName")
    void getName_constructedWithName_returnsCorrectName() {
        assertEquals("TestHero", player.getName(),
            "Player name should match the constructor argument");
    }

    @Test
    @DisplayName("getName_anyPlayer_returnsNonNull")
    void getName_anyPlayer_returnsNonNull() {
        assertNotNull(player.getName(), "Player name must never be null");
    }

    @Test
    @DisplayName("getCharacterClass_constructedWithClass_returnsCorrectClass")
    void getCharacterClass_constructedWithClass_returnsCorrectClass() {
        assertEquals("Fighter", player.getCharacterClass(),
            "Character class should match the constructor argument");
    }
}
