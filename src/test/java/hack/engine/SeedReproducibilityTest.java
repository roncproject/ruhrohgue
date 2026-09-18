package hack.engine;

import hack.model.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for seeded dungeon reproducibility.
 *
 * Root cause of the original failure:
 *   HackController.newGame() seeded Dice correctly, then
 *   GameState.startNewGame() immediately re-seeded with
 *   System.currentTimeMillis() on its first line, discarding the requested
 *   seed. Two games started with the same seed therefore produced entirely
 *   different dungeons.
 *   Fix: startNewGame() no longer seeds. Dice is seeded exactly once per
 *   game, by the caller, before any world building.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("Seed reproducibility")
class SeedReproducibilityTest {

    /**
     * Builds a floor-1 world exactly the way HackController.newGame() does:
     * seed, then startNewGame, then makeLevel, then monsters, then the dog.
     */
    private static GameState buildWorld(long seed) {
        Dice.seed(seed);
        GameState gs = new GameState();
        gs.setSeedInfo(seed, true);
        gs.startNewGame("T01", "Fighter");
        LevelGenerator.makeLevel(gs, 1);
        List<Room> rooms = gs.getLevel().getRooms();
        for (int i = 0; i < Math.min(rooms.size(), 5); i++) {
            Room r = rooms.get(i);
            if (Dice.oneIn(2)) MonsterEngine.makemon(gs, null, r.randomX(), r.randomY());
        }
        VisibilityEngine.setSee(gs);
        LevelGenerator.spawnDogNearPlayer(gs);
        return gs;
    }

    /** Fingerprints map, stairs, rooms, items, gold, traps and monsters. */
    private static String fingerprint(GameState gs) {
        StringBuilder sb = new StringBuilder();
        DungeonLevel l = gs.getLevel();
        for (int y = 0; y < DungeonLevel.ROWNO; y++) {
            for (int x = 0; x < DungeonLevel.COLNO; x++) sb.append(l.cellAt(x, y).getScrsym());
            sb.append('\n');
        }
        sb.append("up=").append(l.getXUpStair()).append(',').append(l.getYUpStair())
          .append(" dn=").append(l.getXDnStair()).append(',').append(l.getYDnStair())
          .append(" player=").append(gs.getPlayer().getX()).append(',')
          .append(gs.getPlayer().getY()).append('\n');
        for (Room r : l.getRooms())
            sb.append("room ").append(r.lx).append(',').append(r.ly).append('-')
              .append(r.hx).append(',').append(r.hy).append(' ').append(r.type).append('\n');
        List<String> parts = new ArrayList<>();
        for (Item it : gs.getFloorItems())
            parts.add("item " + it.doname() + "@" + it.getX() + "," + it.getY());
        for (GoldPile g : l.getGold())
            parts.add("gold " + g.getAmount() + "@" + g.getX() + "," + g.getY());
        for (Trap t : l.getTraps())
            parts.add("trap " + t.getType() + "@" + t.getX() + "," + t.getY());
        for (Monster m : gs.getLiveMonsters())
            parts.add("mon " + m.getData().name + "@" + m.getX() + "," + m.getY());
        Collections.sort(parts);
        parts.forEach(s -> sb.append(s).append('\n'));
        return sb.toString();
    }

    @Test
    @DisplayName("buildWorld_sameSeedTwice_producesIdenticalDungeon")
    void buildWorld_sameSeedTwice_producesIdenticalDungeon() {
        String expected = fingerprint(buildWorld(1L));
        String actual   = fingerprint(buildWorld(1L));
        assertEquals(expected, actual,
            "Seed 1 must produce the same map, items, gold, traps and monsters every time");
    }

    @Test
    @DisplayName("buildWorld_differentSeeds_produceDifferentDungeons")
    void buildWorld_differentSeeds_produceDifferentDungeons() {
        String seed1  = fingerprint(buildWorld(1L));
        String seed42 = fingerprint(buildWorld(42L));
        assertNotEquals(seed1, seed42,
            "Different seeds should produce different dungeons");
    }

    @Test
    @DisplayName("buildWorld_seedReusedAfterUnrelatedGame_stillReproduces")
    void buildWorld_seedReusedAfterUnrelatedGame_stillReproduces() {
        String expected = fingerprint(buildWorld(7L));
        buildWorld(999L);                       // an unrelated game in between
        String actual = fingerprint(buildWorld(7L));
        assertEquals(expected, actual,
            "An intervening game must not disturb replay of an earlier seed");
    }

    @Test
    @DisplayName("buildWorld_sameSeedTenTimes_allRunsIdentical")
    void buildWorld_sameSeedTenTimes_allRunsIdentical() {
        String expected = fingerprint(buildWorld(12345L));
        for (int i = 0; i < 10; i++) {
            assertEquals(expected, fingerprint(buildWorld(12345L)),
                "Replay " + (i + 1) + " of seed 12345 must match the first run");
        }
    }

    @Test
    @DisplayName("setSeedInfo_explicitSeed_isRecordedOnState")
    void setSeedInfo_explicitSeed_isRecordedOnState() {
        GameState gs = buildWorld(2024L);
        assertEquals(2024L, gs.getGameSeed(), "The seed used must be readable from state");
        assertTrue(gs.isSeedExplicit(), "An explicitly supplied seed must be flagged as such");
    }
}
