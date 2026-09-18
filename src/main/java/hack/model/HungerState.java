package hack.model;

/**
 * HungerState.java – Represents the player's current hunger level.
 *
 * Ports the seven {@code #define} constants in {@code hack.eat.c}.
 * Each state carries its 8-character status-bar label (original
 * {@code hu_stat[]} array) and the {@code uhunger} threshold at which
 * the player enters that state.
 *
 * @see Player
 */
public enum HungerState {
    /** Player is completely full — eating more is dangerous */
    SATIATED   (6, "Satiated",  1000),
    /** Normal hunger level */
    NOT_HUNGRY (5, "        ",   150),
    /** Slightly hungry — will degrade soon */
    HUNGRY     (4, "Hungry  ",    50),
    /** Dangerously weak from lack of food */
    WEAK       (3, "Weak    ",     0),
    /** Starting to faint — random multi-turn paralysis */
    FAINTING   (2, "Fainting",  -999),
    /** Has fainted at least once */
    FAINTED    (1, "Fainted ",  -999),
    /** Dead from starvation */
    STARVED    (0, "Starved ", -9999);

    /** Ordinal index in the original {@code hu_stat[]} array */
    public final int ordinalIndex;
    /** 8-character status-bar label */
    public final String label;
    /** {@code uhunger} threshold: player is in this state when hunger &gt; threshold */
    public final int threshold;

    HungerState(int ordinalIndex, String label, int threshold) {
        this.ordinalIndex = ordinalIndex;
        this.label        = label;
        this.threshold    = threshold;
    }

    /**
     * Computes the appropriate hunger state for the given {@code uhunger} value.
     *
     * @param uhunger current hunger points
     * @return matching HungerState
     */
    public static HungerState forHunger(int uhunger) {
        if (uhunger > 1000) return SATIATED;
        if (uhunger > 150)  return NOT_HUNGRY;
        if (uhunger > 50)   return HUNGRY;
        if (uhunger > 0)    return WEAK;
        return FAINTING;
    }
}
