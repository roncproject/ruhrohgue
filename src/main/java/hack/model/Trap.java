package hack.model;

/**
 * Trap.java – A dungeon trap at a specific map position.
 *
 * Ports {@code struct trap} from {@code def.trap.h}:
 * <pre>
 *   struct trap {
 *       struct trap *ntrap;    // linked-list next ptr (not needed in Java)
 *       xchar  tx, ty;         // position
 *       unsigned ttyp:5;       // trap type
 *       unsigned tseen:1;      // player has seen this trap
 *       unsigned once:1;       // single-use (teleport vault trap)
 *   };
 * </pre>
 */
public class Trap {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Column of the trap. */
    private int x;
    /** Row of the trap. */
    private int y;
    /** Type of trap. */
    private TrapType type;
    /** True when the player has triggered or searched out this trap. */
    private boolean seen;
    /** True for single-use vault teleport traps. */
    private boolean once;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a new trap.
     *
     * @param x    column
     * @param y    row
     * @param type trap variety
     */
    public Trap(int x, int y, TrapType type) {
        this.x    = x;
        this.y    = y;
        this.type = type;
        this.seen = false;
        this.once = false;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    public int      getX()           { return x; }
    public int      getY()           { return y; }
    public TrapType getType()        { return type; }
    public boolean  isSeen()         { return seen; }
    public void     setSeen(boolean b){ seen = b; }
    public boolean  isOnce()         { return once; }
    public void     setOnce(boolean b){ once = b; }

    /** True if the trap occupies position (x, y). */
    public boolean isAt(int cx, int cy) { return x == cx && y == cy; }

    @Override
    public String toString() {
        return type.name() + "@(" + x + "," + y + ")" + (seen ? "[seen]" : "");
    }
}
