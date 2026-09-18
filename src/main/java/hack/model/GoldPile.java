package hack.model;

/**
 * GoldPile.java – A pile of gold coins on the dungeon floor.
 *
 * Ports {@code struct gold} from {@code def.gold.h}:
 * <pre>
 *   struct gold {
 *       struct gold *ngold;   // linked-list ptr (not needed in Java)
 *       xchar gx, gy;
 *       long  amount;
 *   };
 * </pre>
 *
 * Gold piles are tracked separately from regular items because their
 * amount is a {@code long} and they use the special '{@code $}' display.
 */
public class GoldPile {

    /** Column position. */
    private int x;
    /** Row position. */
    private int y;
    /** Number of gold pieces in this pile. */
    private long amount;

    /**
     * Creates a gold pile.
     *
     * @param x      column
     * @param y      row
     * @param amount gold pieces
     */
    public GoldPile(int x, int y, long amount) {
        this.x      = x;
        this.y      = y;
        this.amount = amount;
    }

    public int  getX()            { return x; }
    public int  getY()            { return y; }
    public long getAmount()       { return amount; }
    public void setAmount(long a) { amount = a; }

    /** True if this pile is at position (cx, cy). */
    public boolean isAt(int cx, int cy) { return x == cx && y == cy; }

    /**
     * Adds to the pile and returns the new total.
     *
     * @param extra additional gold
     * @return new total
     */
    public long add(long extra) {
        amount += extra;
        return amount;
    }
}
