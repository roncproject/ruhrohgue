package hack.engine;

import hack.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LevelGenerator.
 *
 * Root cause of previous failures:
 *   startNewGame() does NOT call makeLevel(). state.getLevel() is null
 *   after startNewGame(). Tests must call LevelGenerator.makeLevel(state, dlevel)
 *   explicitly to populate state.getLevel().
 *
 * (Baeldung §5 — Simple, §7 — Specific, §8 — Production Scenarios)
 */
@DisplayName("LevelGenerator")
class LevelGeneratorTest {

    private GameState state1;
    private GameState state2;

    @BeforeEach
    void setUp() {
        // startNewGame() does NOT call makeLevel() — we must call it ourselves.
        state1 = new GameState();
        state1.startNewGame("TestHero", "Fighter");
        LevelGenerator.makeLevel(state1, 1);   // populate floor 1

        state2 = new GameState();
        state2.startNewGame("TestHero2", "Fighter");
        LevelGenerator.makeLevel(state2, 2);   // floor 2 (may get a shop)
    }

    // ── Basic structure ───────────────────────────────────────────────────

    @Test
    @DisplayName("makeLevel_anyFloor_levelIsNotNull")
    void makeLevel_anyFloor_levelIsNotNull() {
        assertNotNull(state1.getLevel(),
            "After makeLevel, state.getLevel() must not be null");
    }

    @Test
    @DisplayName("makeLevel_anyFloor_hasAtLeastOneRoom")
    void makeLevel_anyFloor_hasAtLeastOneRoom() {
        assertFalse(state1.getLevel().getRooms().isEmpty(),
            "Every dungeon level must contain at least one room");
    }

    @Test
    @DisplayName("makeLevel_anyFloor_stairCellPresent")
    void makeLevel_anyFloor_stairCellPresent() {
        DungeonLevel level = state1.getLevel();
        boolean hasStairs = false;
        for (int x = 0; x < DungeonLevel.COLNO; x++) {
            for (int y = 0; y < DungeonLevel.ROWNO; y++) {
                if (level.cellAt(x, y).getType() == CellType.STAIRS) {
                    hasStairs = true;
                    break;
                }
            }
            if (hasStairs) break;
        }
        assertTrue(hasStairs,
            "Dungeon level must contain stairs so the player can descend");
    }

    // ── Door symbols ──────────────────────────────────────────────────────

    /*
     * Why this test was previously commented out, and what replaced it.
     *
     * The original assertion read the door's SCREEN SYMBOL and required it to
     * be '+' or ' '. It failed, and it was right to fail: scrsym is a scratch
     * field that whatever stands on a cell overwrites. makeLevel() spawns the
     * floor's monsters, and a monster standing in a doorway writes its own
     * letter there. Across 40 generated floors, 13 of 558 door cells carried a
     * monster letter (B, E, G, H, J, O, a, i, r) instead of a door symbol.
     *
     * The same flaw made MonsterEngine.restoreCell() guess: it hard-coded '+',
     * so every open door a monster walked through was silently redrawn closed.
     *
     * The fix was to give Cell an authoritative doorOpen field, exactly as the
     * staircases already have their coordinates. These tests now assert on
     * that state, which is the thing that actually has to be correct.
     */

    @Test
    @DisplayName("makeLevel_anyFloor_everyDoorHasConsistentStateAndSymbol")
    void makeLevel_anyFloor_everyDoorHasConsistentStateAndSymbol() {
        DungeonLevel level = state1.getLevel();
        for (int x = 0; x < DungeonLevel.COLNO; x++) {
            for (int y = 0; y < DungeonLevel.ROWNO; y++) {
                Cell c = level.cellAt(x, y);
                if (c.getType() != CellType.DOOR) continue;

                // Restoring the cell must reproduce the recorded state, no
                // matter what was standing on it during generation.
                MonsterEngine.restoreCell(state1, x, y);
                char expected = c.isDoorOpen() ? ' ' : '+';
                assertEquals(expected, c.getScrsym(),
                    "Door at " + x + "," + y + " is recorded as "
                    + (c.isDoorOpen() ? "open" : "closed")
                    + " so it must restore to '" + expected + "'");
            }
        }
    }

    @Test
    @DisplayName("restoreCell_openDoorUnderAMonster_staysOpen")
    void restoreCell_openDoorUnderAMonster_staysOpen() {
        DungeonLevel level = state1.getLevel();
        for (int x = 0; x < DungeonLevel.COLNO; x++) {
            for (int y = 0; y < DungeonLevel.ROWNO; y++) {
                Cell c = level.cellAt(x, y);
                if (c.getType() != CellType.DOOR) continue;

                c.setDoorOpen(true);
                c.setScrsym('d');                       // a dog in the doorway
                MonsterEngine.restoreCell(state1, x, y);

                assertTrue(c.isDoorOpen(), "The door must still be recorded open");
                assertEquals(' ', c.getScrsym(),
                    "An open door must not be redrawn as closed once the "
                    + "monster standing in it moves away");
                return;                                  // one door is enough
            }
        }
    }

    @Test
    @DisplayName("makeLevel_multipleRuns_producesBothClosedAndOpenDoors")
    void makeLevel_multipleRuns_producesBothClosedAndOpenDoors() {
        boolean sawClosed = false, sawOpen = false;
        for (int trial = 0; trial < 30 && !(sawClosed && sawOpen); trial++) {
            GameState s = new GameState();
            s.startNewGame("T", "Fighter");
            LevelGenerator.makeLevel(s, 1);   // explicit makeLevel call
            DungeonLevel lv = s.getLevel();
            for (int x = 0; x < DungeonLevel.COLNO; x++) {
                for (int y = 0; y < DungeonLevel.ROWNO; y++) {
                    Cell c = lv.cellAt(x, y);
                    if (c.getType() != CellType.DOOR) continue;
                    // Read the recorded state, not the symbol
                    if (c.isDoorOpen()) sawOpen   = true;
                    else                sawClosed = true;
                }
            }
        }
        assertTrue(sawClosed, "Should see at least one closed door across 30 levels");
        assertTrue(sawOpen,   "Should see at least one open door across 30 levels");
    }

    // ── Floor 2 / shop ────────────────────────────────────────────────────

    @Test
    @DisplayName("makeLevel_floor2_generatesWithoutException")
    void makeLevel_floor2_generatesWithoutException() {
        assertDoesNotThrow(() -> {
            GameState s = new GameState();
            s.startNewGame("T", "Fighter");
            LevelGenerator.makeLevel(s, 2);
        }, "Level generation for floor 2 should not throw");
    }

    @Test
    @DisplayName("makeLevel_shopRooms_areDetectedViaIsShop")
    void makeLevel_shopRooms_areDetectedViaIsShop() {
        DungeonLevel level = state2.getLevel();
        for (Room r : level.getRooms()) {
            if (r.type.isShop()) {
                assertTrue(r.type.value >= 8,
                    "Shop room type value must be >= SHOPBASE (8)");
            }
        }
    }
}
