package hack.model;

/**
 * RoomType.java – Enumeration of special dungeon room varieties.
 *
 * Ports {@code def.mkroom.h} constants.  Ordinary rooms have value 0.
 * Shop sub-types occupy values 8–15 (mirrors SHOPBASE through GENERAL).
 *
 * @see Room
 */
public enum RoomType {
    /** Plain room with no special purpose */
    ORDINARY (0),
    /** Swamp — extra pools and hostile creatures */
    SWAMP    (3),
    /** Bank — deposit/withdraw gold, managed by a bank manager */
    VAULT    (4),
    /** Beehive — full of killer bees */
    BEEHIVE  (5),
    /** Morgue — full of undead */
    MORGUE   (6),
    /** Zoo — assorted hostile animals */
    ZOO      (7),
    /** Generic shop base type */
    SHOPBASE (8),
    /** Wand shop */
    WANDSHOP (9),
    /** General store */
    GENERAL  (15);

    /** Original integer value */
    public final int value;

    RoomType(int value) { this.value = value; }

    /** True if this room type represents a shop (value ≥ SHOPBASE). */
    public boolean isShop() { return value >= SHOPBASE.value; }

    /**
     * Looks up a RoomType by value.
     *
     * @param v integer from original C
     * @return matching RoomType or {@link #ORDINARY}
     */
    public static RoomType fromValue(int v) {
        for (RoomType rt : values()) {
            if (rt.value == v) return rt;
        }
        return ORDINARY;
    }
}
