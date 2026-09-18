package hack.model;

/**
 * Room.java – A rectangular room on the current dungeon level.
 *
 * Ports {@code struct mkroom} from {@code def.mkroom.h}:
 * <pre>
 *   struct mkroom {
 *       xchar lx, hx, ly, hy;     // bounding-box coordinates
 *       schar rtype;               // room type (ordinary, vault, shop…)
 *       schar rlit;                // permanently lit flag
 *       schar doorct;              // number of doors
 *       schar fdoor;               // index into doors[] of first door
 *   };
 * </pre>
 *
 * Door coordinates are stored directly as a {@link java.util.List} rather
 * than as offsets into a global array.
 */
public class Room {

    /** Left boundary (inclusive). */
    public int lx;
    /** Right boundary (inclusive). */
    public int hx;
    /** Top boundary (inclusive). */
    public int ly;
    /** Bottom boundary (inclusive). */
    public int hy;
    /** Room type (special or ordinary). */
    public RoomType type;
    /** True if this room is permanently lit. */
    public boolean lit;

    /**
     * Creates a room with the given bounding box.
     *
     * @param lx   left column
     * @param hx   right column
     * @param ly   top row
     * @param hy   bottom row
     * @param type room variety
     * @param lit  permanently lit
     */
    public Room(int lx, int hx, int ly, int hy, RoomType type, boolean lit) {
        this.lx   = lx;
        this.hx   = hx;
        this.ly   = ly;
        this.hy   = hy;
        this.type = type;
        this.lit  = lit;
    }

    /** Returns the width (number of interior columns). */
    public int width()  { return hx - lx + 1; }

    /** Returns the height (number of interior rows). */
    public int height() { return hy - ly + 1; }

    /**
     * Returns whether the coordinate (x, y) is inside this room's
     * interior (not counting walls).
     *
     * @param x column
     * @param y row
     * @return true if (x,y) is inside
     */
    public boolean contains(int x, int y) {
        return x >= lx && x <= hx && y >= ly && y <= hy;
    }

    /**
     * Returns whether the coordinate (x, y) is inside the room
     * including its one-cell-thick wall border.
     *
     * @param x column
     * @param y row
     * @return true if (x,y) is inside or on the border
     */
    public boolean containsWithBorder(int x, int y) {
        return x >= lx - 1 && x <= hx + 1 && y >= ly - 1 && y <= hy + 1;
    }

    /** Returns a column randomly chosen from within this room. */
    public int randomX() {
        return lx + Dice.rn2(width());
    }

    /** Returns a row randomly chosen from within this room. */
    public int randomY() {
        return ly + Dice.rn2(height());
    }
}
