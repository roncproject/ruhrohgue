package hack.model;

/**
 * Cell.java – A single dungeon map tile.
 *
 * Ports {@code struct rm} from {@code def.rm.h}:
 * <pre>
 *   struct rm {
 *       char scrsym;           // display character
 *       unsigned typ:5;        // cell type (CellType enum)
 *       unsigned new:1;        // needs redraw
 *       unsigned seen:1;       // player has seen this cell
 *       unsigned lit:1;        // permanently lit (torch/sunlight)
 *   };
 * </pre>
 *
 * The C bitfield packing is replaced by plain {@code boolean} and
 * {@code char} fields, which are more readable in Java.
 */
public final class Cell {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** The character currently displayed on screen for this tile. */
    private char scrsym;

    /** The structural type of this cell. */
    private CellType type;

    /** True when the cell has changed and must be redrawn. */
    private boolean needsRedraw;

    /** True when the player has seen this cell at least once. */
    private boolean seen;

    /** True when the cell is permanently lit (bright room or magic light). */
    private boolean lit;

    /**
     * Whether a door cell stands open.
     *
     * <p>Authoritative door state. {@link #scrsym} cannot carry it: scrsym is a
     * scratch field that any monster letter or the player's '@' overwrites when
     * something stands on the cell, and {@code MonsterEngine.restoreCell()} then
     * had to guess what to put back — it always guessed '+', silently closing
     * every open door a monster walked through. Across 40 generated floors, 13
     * of 558 door cells were left displaying a monster letter.</p>
     *
     * <p>Same defect class as the staircase symbols, and the same fix: keep the
     * state in a field of its own and render from that, never from scrsym.</p>
     */
    private boolean doorOpen;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a blank, unseen, unlit cell of type {@link CellType#EMPTY}.
     */
    public Cell() {
        this.scrsym       = ' ';
        this.type         = CellType.EMPTY;
        this.needsRedraw  = false;
        this.seen         = false;
        this.lit          = false;
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Returns a deep copy of this cell.
     *
     * @return new Cell with identical field values
     */
    public Cell copy() {
        Cell c = new Cell();
        c.scrsym      = this.scrsym;
        c.type        = this.type;
        c.needsRedraw = this.needsRedraw;
        c.seen        = this.seen;
        c.lit         = this.lit;
        c.doorOpen    = this.doorOpen;
        return c;
    }

    // -----------------------------------------------------------------------
    // Reset
    // -----------------------------------------------------------------------

    /**
     * Resets this cell to the default blank state.
     * Used when a new level is generated.
     */
    public void reset() {
        scrsym      = ' ';
        type        = CellType.EMPTY;
        needsRedraw = false;
        seen        = false;
        lit         = false;
        doorOpen    = false;
    }

    // -----------------------------------------------------------------------
    // Getters and setters
    // -----------------------------------------------------------------------

    /** Returns the display symbol. */
    public char getScrsym()  { return scrsym; }

    /** Sets the display symbol and marks for redraw. */
    public void setScrsym(char c) {
        this.scrsym      = c;
        this.needsRedraw = true;
    }

    /** Returns the structural cell type. */
    public CellType getType() { return type; }

    /**
     * Sets the cell type and updates the default display symbol.
     *
     * @param t new cell type
     */
    public void setType(CellType t) {
        this.type         = t;
        this.needsRedraw  = true;
    }

    /** Returns whether the cell has changed and needs redrawing. */
    public boolean needsRedraw()   { return needsRedraw; }

    /** Clears the needs-redraw flag. */
    public void clearRedraw()      { needsRedraw = false; }

    /** Marks the cell as needing redraw. */
    public void markDirty()        { needsRedraw = true; }

    /** Returns whether the player has seen this cell. */
    public boolean isSeen()        { return seen; }

    /** Marks the cell as seen by the player. */
    public void setSeen(boolean s) { this.seen = s; }

    /** Returns whether the cell is permanently lit. */
    public boolean isLit()         { return lit; }

    /** Sets the permanent-light flag. */
    public void setLit(boolean l)  { this.lit = l; }

    /** True when this door cell stands open. Meaningless for non-door cells. */
    public boolean isDoorOpen()            { return doorOpen; }

    /** Sets the door state and keeps {@link #scrsym} consistent with it. */
    public void setDoorOpen(boolean open) {
        this.doorOpen = open;
        this.scrsym   = open ? ' ' : '+';
        this.needsRedraw = true;
    }

    // -----------------------------------------------------------------------
    // Delegating predicates  (forward to CellType for convenience)
    // -----------------------------------------------------------------------

    /** True if this tile is a wall. */
    public boolean isWall()        { return type.isWall(); }

    /** True if this tile is absolutely impassable. */
    public boolean isRock()        { return type.isRock(); }

    /** True if a creature can enter this tile. */
    public boolean isAccessible()  { return type.isAccessible(); }

    /** True if this tile is open room or staircase. */
    public boolean isRoom()        { return type.isRoom(); }
}
