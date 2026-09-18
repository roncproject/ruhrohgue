package hack.model;

import java.util.ArrayList;
import java.util.List;

/**
 * DungeonLevel.java – The full state of one dungeon level.
 *
 * Contains the 80×22 cell grid (mirrors {@code levl[COLNO][ROWNO]}),
 * the room list, traps, gold piles, and stair positions.
 *
 * @see Cell
 * @see Room
 * @see Trap
 * @see GoldPile
 */
public class DungeonLevel {

    // -----------------------------------------------------------------------
    // Map constants  (from config.h)
    // -----------------------------------------------------------------------

    /** Number of map columns (COLNO in C). */
    public static final int COLNO = 80;
    /** Number of map rows (ROWNO in C). */
    public static final int ROWNO = 22;

    // -----------------------------------------------------------------------
    // Cell grid
    // -----------------------------------------------------------------------

    /**
     * The 80×22 cell array — indexed as {@code cells[x][y]}.
     * Mirrors {@code struct rm levl[COLNO][ROWNO]}.
     */
    private final Cell[][] cells = new Cell[COLNO][ROWNO];

    // -----------------------------------------------------------------------
    // Level content
    // -----------------------------------------------------------------------

    /** All rooms on this level. */
    private final List<Room>     rooms    = new ArrayList<>();
    /** All traps on this level. */
    private final List<Trap>     traps    = new ArrayList<>();
    /** All gold piles on this level. */
    private final List<GoldPile> gold     = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Stair positions
    // -----------------------------------------------------------------------

    /** Down-stair column (xdnstair). */
    private int xDnStair;
    /** Down-stair row (ydnstair). */
    private int yDnStair;
    /** Up-stair column (xupstair). */
    private int xUpStair;
    /** Up-stair row (yupstair). */
    private int yUpStair;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /** Creates an empty dungeon level with all cells blank. */
    public DungeonLevel() {
        for (int x = 0; x < COLNO; x++) {
            for (int y = 0; y < ROWNO; y++) {
                cells[x][y] = new Cell();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Cell access
    // -----------------------------------------------------------------------

    /**
     * Returns the cell at column {@code x}, row {@code y}.
     *
     * @param x column (0–79)
     * @param y row (0–21)
     * @return cell at that position
     * @throws ArrayIndexOutOfBoundsException if coordinates are out of range
     */
    public Cell cellAt(int x, int y) {
        return cells[x][y];
    }

    /**
     * Returns true if the coordinates are within the map bounds.
     * Mirrors {@code isok(x, y)} in {@code hack.cmd.c}.
     *
     * @param x column
     * @param y row
     * @return true if in bounds
     */
    public static boolean isOk(int x, int y) {
        return x >= 1 && x < COLNO - 1 && y >= 0 && y < ROWNO;
    }

    /**
     * Resets all cells to the blank state.  Called when a new level
     * is generated to overwrite any previous content.
     */
    public void resetCells() {
        for (int x = 0; x < COLNO; x++) {
            for (int y = 0; y < ROWNO; y++) {
                cells[x][y].reset();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Room queries
    // -----------------------------------------------------------------------

    /**
     * Returns the room (if any) whose interior contains point (x, y).
     *
     * @param x column
     * @param y row
     * @return containing Room or null
     */
    public Room roomAt(int x, int y) {
        for (Room r : rooms) {
            if (r.contains(x, y)) return r;
        }
        return null;
    }

    /**
     * Returns the index of the room containing (x, y), or -1 if none.
     * Mirrors {@code inroom(x, y)} in the original C.
     *
     * @param x column
     * @param y row
     * @return room index or -1
     */
    public int inRoom(int x, int y) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).contains(x, y)) return i;
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // Trap queries
    // -----------------------------------------------------------------------

    /**
     * Returns the trap at (x, y) or null if none.
     * Mirrors {@code t_at(x, y)}.
     *
     * @param x column
     * @param y row
     * @return trap or null
     */
    public Trap trapAt(int x, int y) {
        for (Trap t : traps) {
            if (t.isAt(x, y)) return t;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Gold queries
    // -----------------------------------------------------------------------

    /**
     * Returns the gold pile at (x, y) or null if none.
     * Mirrors {@code g_at(x, y)}.
     *
     * @param x column
     * @param y row
     * @return gold pile or null
     */
    public GoldPile goldAt(int x, int y) {
        for (GoldPile g : gold) {
            if (g.isAt(x, y)) return g;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Lighting helper
    // -----------------------------------------------------------------------

    /**
     * Marks all cells in a given room as seen and lit.
     *
     * @param r the room to illuminate
     */
    public void illuminateRoom(Room r) {
        for (int x = r.lx - 1; x <= r.hx + 1; x++) {
            for (int y = r.ly - 1; y <= r.hy + 1; y++) {
                if (isOk(x, y)) {
                    cells[x][y].setLit(true);
                    cells[x][y].setSeen(true);
                    cells[x][y].markDirty();
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Getters / setters for collections and stair positions
    // -----------------------------------------------------------------------

    public List<Room>     getRooms()  { return rooms; }
    public List<Trap>     getTraps()  { return traps; }
    public List<GoldPile> getGold()   { return gold; }

    public int getXDnStair() { return xDnStair; }
    public int getYDnStair() { return yDnStair; }
    public void setDnStair(int x, int y) { xDnStair=x; yDnStair=y; }
    public int getXUpStair() { return xUpStair; }
    public int getYUpStair() { return yUpStair; }
    public void setUpStair(int x, int y) { xUpStair=x; yUpStair=y; }
}
