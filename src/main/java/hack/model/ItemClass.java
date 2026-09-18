package hack.model;

/**
 * ItemClass.java – Immutable prototype data for one object type.
 *
 * Ports {@code struct objclass} from {@code def.objclass.h}.
 *
 * <pre>
 *   struct objclass {
 *       char *oc_name;           // actual name
 *       char *oc_descr;          // randomised description
 *       char *oc_uname;          // user-given name
 *       Bitfield(oc_name_known); // identification flag
 *       Bitfield(oc_merge);      // stack-merge flag
 *       char  oc_olet;           // symbol character
 *       schar oc_prob;           // spawn probability weight
 *       schar oc_delay;          // delay turns when using
 *       uchar oc_weight;
 *       schar oc_oc1, oc_oc2;   // multi-purpose: AC, wldam, bits, g_val
 *       int   oc_oi;             // nutrition / g_val
 *   };
 * </pre>
 *
 * @see Item
 * @see ItemTable
 */
public final class ItemClass {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** Real name of the object (oc_name) */
    public final String  name;
    /**
     * Randomised / unknown description, e.g. "potion of purple stuff"
     * (oc_descr). Null for objects whose name is always known.
     */
    public final String  description;
    /** ASCII symbol used on the map (oc_olet) */
    public final char    symbol;
    /** Spawn probability weight used in random object generation (oc_prob) */
    public final int     spawnWeight;
    /** Turns taken to use/read/eat this object (oc_delay) */
    public final int     useDelay;
    /** Object weight in units (oc_weight) */
    public final int     weight;
    /**
     * Multi-purpose integer 1 (oc_oc1):
     * AC bonus for armour; small-monster damage dice for weapons;
     * wand-type bits (NODIR/IMMEDIATE/RAY) for wands;
     * SPEC flag for rings.
     */
    public final int     oc1;
    /**
     * Multi-purpose integer 2 (oc_oc2):
     * max-AC-cancel for armour; large-monster damage dice for weapons.
     */
    public final int     oc2;
    /**
     * Multi-purpose integer (oc_oi):
     * nutritional value for food;
     * gem value in Zorkmids.
     */
    public final int     multiValue;
    /**
     * Shop price in gold pieces, derived at construction time.
     * Food: max(1, nutrition/8). Gems: max(1, gemValue/10). Other: max(1, weight/5).
     */
    public final int     cost;
    /** True when every instance of this type is always identified. */
    public final boolean nameAlwaysKnown;
    /** True when equal items of this type stack into a single inventory slot. */
    public final boolean mergeable;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Full constructor.
     *
     * @param name            real name
     * @param description     unknown description (null if always known)
     * @param symbol          display character
     * @param spawnWeight     spawn weight (0 = not randomly spawned)
     * @param useDelay        turns to use
     * @param weight          item weight
     * @param oc1             first multi-purpose value
     * @param oc2             second multi-purpose value
     * @param multiValue      nutrition / gem value
     * @param nameAlwaysKnown true if the item is always identified
     * @param mergeable       true if identical items stack
     */
    public ItemClass(String name, String description, char symbol,
                     int spawnWeight, int useDelay, int weight,
                     int oc1, int oc2, int multiValue,
                     boolean nameAlwaysKnown, boolean mergeable) {
        this.name            = name;
        this.description     = description;
        this.symbol          = symbol;
        this.spawnWeight     = spawnWeight;
        this.useDelay        = useDelay;
        this.weight          = weight;
        this.oc1             = oc1;
        this.oc2             = oc2;
        this.multiValue      = multiValue;
        this.nameAlwaysKnown = nameAlwaysKnown;
        this.mergeable       = mergeable;
        // Shop price derived from item type and value.
        // Food ('%'): price = max(1, nutrition / 8). Tripe(200)→25g, FoodRation(800)→100g.
        // Gems ('*'): price = max(1, gemValue / 10).
        // Everything else: max(1, weight / 5) as a proxy for rarity.
        if (symbol == '%') {
            this.cost = Math.max(1, multiValue / 8);
        } else if (symbol == '*') {
            this.cost = Math.max(1, multiValue / 10);
        } else {
            this.cost = Math.max(1, weight / 5);
        }
    }

    @Override
    public String toString() { return name + " (" + symbol + ')'; }
}
