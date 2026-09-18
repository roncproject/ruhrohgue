package hack.engine;

import hack.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for staircase symbols.
 *
 * Root cause of the original failure:
 *   scrsym is a scratch field. The player, the shopkeeper and the bank
 *   manager all render as '@' and overwrite it, and monster letters land on
 *   it too. The serializer's "unknown symbol" fallback then replaced the
 *   contaminated value with CellType.STAIRS.defaultSymbol, which is '<' — so
 *   a down staircase silently became a second up staircase and the floor
 *   appeared to have no exit.
 *   Fix: both stair symbols are derived from the level's recorded stair
 *   coordinates, never inferred from scrsym.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("Staircase symbols")
class StairSymbolTest {

    private static GameState floor(long seed, int dlevel) {
        Dice.seed(seed);
        GameState gs = new GameState();
        gs.setSeedInfo(seed, true);
        gs.startNewGame("T01", "Fighter");
        LevelGenerator.makeLevel(gs, dlevel);
        return gs;
    }

    @Test
    @DisplayName("makeLevel_anyFloor_upAndDownStairsAreDistinct")
    void makeLevel_anyFloor_upAndDownStairsAreDistinct() {
        for (long seed = 1; seed <= 20; seed++) {
            for (int f = 1; f <= 4; f++) {
                DungeonLevel l = floor(seed, f).getLevel();
                boolean same = l.getXDnStair() == l.getXUpStair()
                            && l.getYDnStair() == l.getYUpStair();
                assertFalse(same,
                    "Seed " + seed + " floor " + f + " must have two distinct staircases");
            }
        }
    }

    @Test
    @DisplayName("makeLevel_anyFloor_bothStairCellsAreStairsType")
    void makeLevel_anyFloor_bothStairCellsAreStairsType() {
        for (long seed = 1; seed <= 20; seed++) {
            DungeonLevel l = floor(seed, 2).getLevel();
            assertEquals(CellType.STAIRS,
                l.cellAt(l.getXDnStair(), l.getYDnStair()).getType(),
                "The recorded down-stair cell must be a STAIRS cell");
            assertEquals(CellType.STAIRS,
                l.cellAt(l.getXUpStair(), l.getYUpStair()).getType(),
                "The recorded up-stair cell must be a STAIRS cell");
        }
    }

    @Test
    @DisplayName("restoreCell_downStairContaminatedWithPlayerSymbol_restoresGreaterThan")
    void restoreCell_downStairContaminatedWithPlayerSymbol_restoresGreaterThan() {
        GameState gs = floor(10L, 2);
        DungeonLevel l = gs.getLevel();
        int x = l.getXDnStair(), y = l.getYDnStair();

        l.cellAt(x, y).setScrsym('@');      // shopkeeper / player stood here
        MonsterEngine.restoreCell(gs, x, y);

        assertEquals('>', l.cellAt(x, y).getScrsym(),
            "A down staircase must never be left displaying anything but '>'");
    }

    @Test
    @DisplayName("restoreCell_upStairContaminatedWithPlayerSymbol_restoresLessThan")
    void restoreCell_upStairContaminatedWithPlayerSymbol_restoresLessThan() {
        GameState gs = floor(10L, 2);
        DungeonLevel l = gs.getLevel();
        int x = l.getXUpStair(), y = l.getYUpStair();

        l.cellAt(x, y).setScrsym('@');
        MonsterEngine.restoreCell(gs, x, y);

        assertEquals('<', l.cellAt(x, y).getScrsym(),
            "An up staircase must never be left displaying anything but '<'");
    }

    @Test
    @DisplayName("makeLevel_anyFloor_stairsAreNotInsideSpecialRoom")
    void makeLevel_anyFloor_stairsAreNotInsideSpecialRoom() {
        for (long seed = 1; seed <= 20; seed++) {
            for (int f = 1; f <= 4; f++) {
                DungeonLevel l = floor(seed, f).getLevel();
                for (Room r : l.getRooms()) {
                    if (r.type != RoomType.VAULT && !r.type.isShop()) continue;
                    assertFalse(inside(r, l.getXDnStair(), l.getYDnStair()),
                        "A down staircase must not be inside the Bank or Shop");
                    assertFalse(inside(r, l.getXUpStair(), l.getYUpStair()),
                        "An up staircase must not be inside the Bank or Shop");
                }
            }
        }
    }

    @Test
    @DisplayName("makeLevel_oddFloor_alwaysHasBank")
    void makeLevel_oddFloor_alwaysHasBank() {
        for (long seed = 1; seed <= 30; seed++) {
            boolean hasVault = false;
            for (Room r : floor(seed, 1).getLevel().getRooms())
                if (r.type == RoomType.VAULT) hasVault = true;
            assertTrue(hasVault, "Seed " + seed + ": every odd floor must have a Bank");
        }
    }

    @Test
    @DisplayName("makeLevel_evenFloor_alwaysHasShop")
    void makeLevel_evenFloor_alwaysHasShop() {
        for (long seed = 1; seed <= 30; seed++) {
            boolean hasShop = false;
            for (Room r : floor(seed, 2).getLevel().getRooms())
                if (r.type.isShop()) hasShop = true;
            assertTrue(hasShop, "Seed " + seed + ": every even floor must have a Shop");
        }
    }

    private static boolean inside(Room r, int x, int y) {
        return x >= r.lx && x <= r.hx && y >= r.ly && y <= r.hy;
    }
}
