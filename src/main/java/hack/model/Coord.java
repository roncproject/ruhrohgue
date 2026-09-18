package hack.model;

/**
 * Coord.java – Immutable (x, y) coordinate pair.
 *
 * Ports the {@code coord} typedef from {@code hack.h}:
 * <pre>
 *   typedef struct { xchar x, y; } coord;
 * </pre>
 *
 * Made immutable here for safety; construction happens via factory method
 * or the two-arg constructor.
 */
public final class Coord {

    /** Horizontal position (column, 1-based in the original game). */
    public final int x;
    /** Vertical position (row, 0-based). */
    public final int y;

    /** Shared zero-coord sentinel */
    public static final Coord ZERO = new Coord(0, 0);

    /**
     * Creates a new coordinate pair.
     *
     * @param x column
     * @param y row
     */
    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns a new Coord offset by {@code (dx, dy)}.
     *
     * @param dx horizontal delta
     * @param dy vertical delta
     * @return new Coord
     */
    public Coord add(int dx, int dy) {
        return new Coord(x + dx, y + dy);
    }

    /**
     * Returns the squared Euclidean distance to another coordinate.
     * Used in the original {@code dist()} function.
     *
     * @param other target coordinate
     * @return squared distance
     */
    public int dist2(Coord other) {
        int dx = x - other.x;
        int dy = y - other.y;
        return dx * dx + dy * dy;
    }

    /**
     * Returns the squared Euclidean distance to a raw (x, y) point.
     *
     * @param ox other x
     * @param oy other y
     * @return squared distance
     */
    public int dist2(int ox, int oy) {
        int dx = x - ox;
        int dy = y - oy;
        return dx * dx + dy * dy;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Coord)) return false;
        Coord other = (Coord) obj;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return 31 * x + y;
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
