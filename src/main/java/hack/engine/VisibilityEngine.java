package hack.engine;

import hack.model.*;

/**
 * VisibilityEngine.java – Line-of-sight and vision calculations.
 *
 * Ports {@code cansee()}, {@code setsee()}, and {@code seeoff()} from
 * {@code hack.c}, plus {@code canseemon()} from {@code hack.mon.c}.
 *
 * <h2>Visibility rules (matching NetHack / Hack 1.0.2 behaviour)</h2>
 * <ul>
 *   <li><b>Corridors</b> – visible only when the player is within 1 step
 *       (the 3×3 neighbourhood). Visited corridor cells keep their '#' scrsym
 *       permanently (seen flag set by {@code doMove}) and are rendered when
 *       the serialiser detects {@code isSeen()==true}. {@code setSee()} never
 *       marks corridor cells seen via the room-expansion loop, preventing
 *       unvisited corridor branches from appearing prematurely.</li>
 *   <li><b>Rooms</b> – all floor/wall tiles become visible the moment the
 *       player enters or stands adjacent to a doorway that has line-of-sight
 *       into a lit room. Room tiles remain visible after the player leaves
 *       (seen flag persists).</li>
 *   <li><b>Monsters</b> – visible only when {@code canSee} is true for their
 *       cell. They vanish from the screen when the player leaves the room or
 *       corridor they share.</li>
 * </ul>
 */
public final class VisibilityEngine {

    private VisibilityEngine() {}

    // -----------------------------------------------------------------------
    // Core visibility
    // -----------------------------------------------------------------------

    /**
     * Returns true if the player can currently see position (x, y).
     *
     * Rules:
     * <ol>
     *   <li>Blind or swallowed → always false.</li>
     *   <li>Distance ≤ 1 step (8 neighbours + self) → always true.</li>
     *   <li>Cell is in a lit room AND the player is also in that same lit room
     *       → true (full room visibility when inside).</li>
     *   <li>Otherwise → false (dark corridors, other rooms).</li>
     * </ol>
     */
    public static boolean canSee(GameState state, int x, int y) {
        Player p = state.getPlayer();
        if (p.isBlind() || p.isSwallowed()) return false;
        if (!DungeonLevel.isOk(x, y)) return false;

        int dx = x - p.getX();
        int dy = y - p.getY();

        // Always see the immediate 3×3 neighbourhood
        if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) return true;

        DungeonLevel level = state.getLevel();
        if (level == null) return false;

        Cell c = level.cellAt(x, y);

        // Corridors: only visible when adjacent — never via room expansion
        if (c.getType() == CellType.CORR || c.getType() == CellType.SCORR) {
            return false;
        }

        // Lit room: visible only if player is ALSO in the same lit room
        if (!c.isLit()) return false;

        Cell here = level.cellAt(p.getX(), p.getY());
        if (!here.isLit()) return false; // player is not in a lit room

        // Both target and player are lit — use lit-room bounding box
        int[] box = getLitBox(state);
        return x >= box[0] && x <= box[1] && y >= box[2] && y <= box[3];
    }

    /**
     * Returns true if the player can see a specific monster.
     * Mirrors {@code canseemon()} from {@code hack.mon.c}.
     */
    public static boolean canSeeMonster(GameState state, Monster m) {
        if (m.isInvisible() && !state.getPlayer().hasSeeInvisible()) return false;
        if (m.isHiding() && state.itemAt(m.getX(), m.getY()) != null) return false;
        return canSee(state, m.getX(), m.getY());
    }

    // -----------------------------------------------------------------------
    // setSee – reveal cells the player can currently see
    // -----------------------------------------------------------------------

    /**
     * Marks all cells in the player's current field of view as seen.
     * Mirrors {@code setsee()} from {@code hack.c}.
     *
     * <p><b>Important:</b> corridor cells are intentionally skipped in the
     * room-expansion loop. They are marked seen individually by
     * {@code doMove()} when the player physically enters them. This prevents
     * unvisited corridor branches from appearing on the map.</p>
     */
    public static void setSee(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null || p.isBlind()) return;

        // Mark lit-room cells as seen — skip corridors and secret passages
        int[] box = getLitBox(state);
        for (int y = box[2]; y <= box[3]; y++) {
            for (int x = box[0]; x <= box[1]; x++) {
                if (!DungeonLevel.isOk(x, y)) continue;
                Cell c = level.cellAt(x, y);
                // Do NOT mark corridor cells seen via room expansion
                if (c.getType() == CellType.CORR || c.getType() == CellType.SCORR) continue;
                c.setSeen(true);
                c.markDirty();
            }
        }

        // Always reveal the 3×3 neighbourhood regardless of lighting —
        // this covers corridor cells the player is currently adjacent to.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = p.getX() + dx, ny = p.getY() + dy;
                if (!DungeonLevel.isOk(nx, ny)) continue;
                Cell c = level.cellAt(nx, ny);
                // For corridor cells in the 3×3: only mark seen if they have a
                // real '#' scrsym (i.e. they have been carved, not secret/blank)
                if ((c.getType() == CellType.CORR) && c.getScrsym() == '#') {
                    c.setSeen(true);
                    c.markDirty();
                } else if (c.getType() != CellType.SCORR) {
                    c.setSeen(true);
                    c.markDirty();
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // seeOff / unSee
    // -----------------------------------------------------------------------

    /**
     * Called when the player goes blind or changes levels.
     * Mirrors {@code seeoff(mode)} from {@code hack.c}.
     */
    public static void seeOff(GameState state, boolean movement) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null) return;

        if (!movement) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int nx = p.getX() + dx, ny = p.getY() + dy;
                    if (!DungeonLevel.isOk(nx, ny)) continue;
                    Cell c = level.cellAt(nx, ny);
                    if (!c.isLit() && c.getScrsym() == '.') {
                        c.setSeen(false);
                        c.setScrsym(' ');
                        c.markDirty();
                    }
                }
            }
        }
    }

    public static void seeOff(GameState state) { seeOff(state, false); }

    /**
     * Hides unlit floor cells near the player when they leave a room.
     * Mirrors {@code unsee()} from {@code hack.c}.
     */
    public static void unSee(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null) return;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = p.getX() + dx, ny = p.getY() + dy;
                if (!DungeonLevel.isOk(nx, ny)) continue;
                Cell c = level.cellAt(nx, ny);
                if (!c.isLit() && c.getScrsym() == '.') {
                    c.setScrsym(' ');
                    c.markDirty();
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Lit-room bounding box
    // -----------------------------------------------------------------------

    /**
     * Returns the bounding box of the lit area visible from the player's
     * current position.
     *
     * Only expands into cells that are lit (room cells). The expansion stops
     * at corridors, walls, and dark cells, so it stays within the room the
     * player is currently in.
     *
     * @return int[4]: {minX, maxX, minY, maxY}
     */
    static int[] getLitBox(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null) {
            return new int[]{p.getX()-1, p.getX()+1, p.getY()-1, p.getY()+1};
        }

        int px = Math.max(0, Math.min(DungeonLevel.COLNO-1, p.getX()));
        int py = Math.max(0, Math.min(DungeonLevel.ROWNO-1, p.getY()));
        Cell here = level.cellAt(px, py);

        if (!here.isLit()) {
            // Dark area / corridor: only see adjacent cells
            return new int[]{p.getX()-1, p.getX()+1, p.getY()-1, p.getY()+1};
        }

        // Lit room: expand horizontally and vertically, stopping at unlit cells
        // Use the player's row/column for expansion to stay within the current room
        int lx = px, hx = px, ly = py, hy = py;

        while (lx > 1 && DungeonLevel.isOk(lx-1, py)
               && level.cellAt(lx-1, py).isLit()
               && level.cellAt(lx-1, py).getType() != CellType.CORR) lx--;

        while (hx < DungeonLevel.COLNO-2 && DungeonLevel.isOk(hx+1, py)
               && level.cellAt(hx+1, py).isLit()
               && level.cellAt(hx+1, py).getType() != CellType.CORR) hx++;

        while (ly > 0 && DungeonLevel.isOk(px, ly-1)
               && level.cellAt(px, ly-1).isLit()
               && level.cellAt(px, ly-1).getType() != CellType.CORR) ly--;

        while (hy < DungeonLevel.ROWNO-1 && DungeonLevel.isOk(px, hy+1)
               && level.cellAt(px, hy+1).isLit()
               && level.cellAt(px, hy+1).getType() != CellType.CORR) hy++;

        return new int[]{lx, hx, ly, hy};
    }
}
