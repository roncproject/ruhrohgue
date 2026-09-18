package hack.model;

/**
 * CellType.java – Enumeration of dungeon map cell types.
 *
 * Ports the {@code #define} integer constants in {@code def.rm.h} to a
 * type-safe Java enum with built-in predicate methods.
 *
 * <pre>
 *  Original C            Java enum variant
 *  ---------             -----------------
 *  #define HWALL  1  →  HWALL
 *  #define VWALL  2  →  VWALL
 *  #define SDOOR  3  →  SDOOR
 *  #define SCORR  4  →  SCORR
 *  #define LDOOR  5  →  LDOOR
 *  #define POOL   6  →  POOL
 *  #define DOOR   7  →  DOOR
 *  #define CORR   8  →  CORR
 *  #define ROOM   9  →  ROOM
 *  #define STAIRS 10 →  STAIRS
 * </pre>
 *
 * @see Cell
 */
public enum CellType {
    /** Uninitialized empty space */
    EMPTY   (0,  ' '),
    /** Horizontal wall (top / bottom of room) */
    HWALL   (1,  '-'),
    /** Vertical wall (left / right side of room) */
    VWALL   (2,  '|'),
    /** Secret door — appears as wall until the player searches */
    SDOOR   (3,  ' '),
    /** Secret corridor — hidden passage */
    SCORR   (4,  ' '),
    /** Locked door */
    LDOOR   (5,  '+'),
    /** Pool of water — dangerous to non-aquatic creatures */
    POOL    (6,  '}'),
    /** Open door */
    DOOR    (7,  '+'),
    /** Corridor passage */
    CORR    (8,  '#'),
    /** Room floor */
    ROOM    (9,  '.'),
    /** Staircase — symbol set by level generator (&lt; or &gt;) */
    STAIRS  (10, '<');

    /** Original C integer value for serialisation compatibility */
    public final int value;
    /** Default ASCII display character */
    public final char defaultSymbol;

    CellType(int value, char defaultSymbol) {
        this.value         = value;
        this.defaultSymbol = defaultSymbol;
    }

    // -----------------------------------------------------------------------
    // Predicate helpers  (mirror def.rm.h macros)
    // -----------------------------------------------------------------------

    /** IS_WALL — horizontal or vertical wall */
    public boolean isWall()       { return this == HWALL || this == VWALL; }

    /** IS_ROCK — absolutely impassable (wall, secret door/corridor) */
    public boolean isRock()       { return value > 0 && value < POOL.value; }

    /** ACCESSIBLE — creature can enter (door, corridor, room, stairs) */
    public boolean isAccessible() { return value >= DOOR.value; }

    /** IS_ROOM — open room floor or staircase */
    public boolean isRoom()       { return value >= ROOM.value; }

    /** ZAP_POS — missile / spell can pass through */
    public boolean isZapPos()     { return value > DOOR.value; }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Returns the CellType whose {@code value} equals {@code v},
     * or {@link #EMPTY} if no match exists.
     *
     * @param v integer from the original C constant
     * @return matching CellType
     */
    public static CellType fromValue(int v) {
        for (CellType ct : values()) {
            if (ct.value == v) return ct;
        }
        return EMPTY;
    }
}
