package hack.engine;

import hack.model.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that every floor — not just floor 1 — is reproducible from the seed,
 * and that every floor is fully walkable.
 *
 * Root cause of the original failure:
 *   Deeper floors were generated from whatever state the shared RNG happened
 *   to be in when the player took the stairs. Because combat, searching and
 *   monster movement all draw random numbers, two games with the same seed
 *   reached floor 2 with different RNG states and built different floors.
 *   Fix: each floor is generated from a seed derived from (game seed, floor
 *   number), on a private stream that is swapped in and out around
 *   generation.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("Deterministic floors")
class DeterministicFloorsTest {

    private static String fingerprint(GameState gs) {
        StringBuilder sb = new StringBuilder();
        DungeonLevel l = gs.getLevel();
        for (int y = 0; y < DungeonLevel.ROWNO; y++) {
            for (int x = 0; x < DungeonLevel.COLNO; x++) sb.append(l.cellAt(x, y).getType().ordinal() % 10);
            sb.append('\n');
        }
        sb.append("up=").append(l.getXUpStair()).append(',').append(l.getYUpStair())
          .append(" dn=").append(l.getXDnStair()).append(',').append(l.getYDnStair()).append('\n');
        List<String> parts = new ArrayList<>();
        for (Item it : gs.getFloorItems()) parts.add("i " + it.doname() + "@" + it.getX() + "," + it.getY());
        for (GoldPile g : l.getGold())     parts.add("g " + g.getAmount() + "@" + g.getX() + "," + g.getY());
        for (Trap t : l.getTraps())        parts.add("t " + t.getType() + "@" + t.getX() + "," + t.getY());
        for (Monster m : gs.getLiveMonsters()) parts.add("m " + m.getData().name + "@" + m.getX() + "," + m.getY());
        Collections.sort(parts);
        parts.forEach(s -> sb.append(s).append('\n'));
        return sb.toString();
    }

    /** Plays down to maxFloor, burning {@code noise} random draws per descent. */
    private static List<String> play(long seed, int maxFloor, int noise) {
        Dice.seed(seed);
        GameState gs = new GameState();
        gs.setSeedInfo(seed, true);
        gs.startNewGame("T01", "Fighter");
        LevelGenerator.makeLevel(gs, 1);
        List<String> out = new ArrayList<>();
        out.add(fingerprint(gs));
        for (int f = 2; f <= maxFloor; f++) {
            for (int i = 0; i < noise; i++) Dice.rnd(20);
            GameEngine.gotoLevel(gs, f);
            out.add(fingerprint(gs));
        }
        return out;
    }

    @Test
    @DisplayName("play_sameSeedTwice_allSixFloorsIdentical")
    void play_sameSeedTwice_allSixFloorsIdentical() {
        List<String> a = play(20L, 6, 0);
        List<String> b = play(20L, 6, 0);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i), b.get(i), "Floor " + (i + 1) + " must reproduce");
        }
    }

    @Test
    @DisplayName("play_differentAmountsOfPlay_floorsStillIdentical")
    void play_differentAmountsOfPlay_floorsStillIdentical() {
        List<String> quiet = play(20L, 6, 0);
        List<String> busy  = play(20L, 6, 137);
        List<String> epic  = play(20L, 6, 4096);
        for (int i = 0; i < quiet.size(); i++) {
            assertEquals(quiet.get(i), busy.get(i),
                "Floor " + (i + 1) + " must not depend on how much play preceded it");
            assertEquals(quiet.get(i), epic.get(i),
                "Floor " + (i + 1) + " must not depend on how much play preceded it");
        }
    }

    @Test
    @DisplayName("play_differentSeeds_produceDifferentFloors")
    void play_differentSeeds_produceDifferentFloors() {
        List<String> a = play(20L, 6, 0);
        List<String> b = play(21L, 6, 0);
        for (int i = 0; i < a.size(); i++) {
            assertNotEquals(a.get(i), b.get(i), "Floor " + (i + 1) + " should differ by seed");
        }
    }

    @Test
    @DisplayName("floorSeed_differentFloors_produceDifferentSeeds")
    void floorSeed_differentFloors_produceDifferentSeeds() {
        Dice.seed(20L);
        assertNotEquals(Dice.floorSeed(1), Dice.floorSeed(2));
        assertNotEquals(Dice.floorSeed(2), Dice.floorSeed(3));
    }

    @Test
    @DisplayName("makeLevel_everyFloor_isFullyReachableFromUpStair")
    void makeLevel_everyFloor_isFullyReachableFromUpStair() {
        for (long seed = 1; seed <= 20; seed++) {
            Dice.seed(seed);
            GameState gs = new GameState();
            gs.setSeedInfo(seed, true);
            gs.startNewGame("T01", "Fighter");
            for (int f = 1; f <= 5; f++) {
                LevelGenerator.makeLevel(gs, f);
                DungeonLevel l = gs.getLevel();
                boolean[][] seen = flood(l, l.getXUpStair(), l.getYUpStair());
                assertTrue(seen[l.getXDnStair()][l.getYDnStair()],
                    "Seed " + seed + " floor " + f + ": the down staircase must be reachable");
                for (Room r : l.getRooms()) {
                    boolean any = false;
                    for (int x = r.lx; x <= r.hx && !any; x++)
                        for (int y = r.ly; y <= r.hy && !any; y++) if (seen[x][y]) any = true;
                    assertTrue(any, "Seed " + seed + " floor " + f + ": room " + r.type
                        + " must be reachable");
                }
            }
        }
    }

    private static boolean walkable(DungeonLevel l, int x, int y) {
        if (!DungeonLevel.isOk(x, y)) return false;
        CellType t = l.cellAt(x, y).getType();
        return t == CellType.ROOM || t == CellType.CORR || t == CellType.SCORR
            || t == CellType.DOOR || t == CellType.LDOOR || t == CellType.SDOOR
            || t == CellType.STAIRS;
    }

    private static boolean[][] flood(DungeonLevel l, int sx, int sy) {
        boolean[][] seen = new boolean[DungeonLevel.COLNO][DungeonLevel.ROWNO];
        if (!walkable(l, sx, sy)) return seen;
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sx, sy});
        seen[sx][sy] = true;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (!DungeonLevel.isOk(nx, ny) || seen[nx][ny] || !walkable(l, nx, ny)) continue;
                seen[nx][ny] = true;
                q.add(new int[]{nx, ny});
            }
        }
        return seen;
    }
}
