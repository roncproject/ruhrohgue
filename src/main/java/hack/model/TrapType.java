package hack.model;

/**
 * TrapType.java – Enumeration of all trap varieties.
 *
 * Ports the nine {@code #define} constants in {@code def.trap.h}.
 *
 * <pre>
 *  Original C                Java enum variant
 *  ---------                 -----------------
 *  #define BEAR_TRAP    0 →  BEAR_TRAP
 *  #define ARROW_TRAP   1 →  ARROW_TRAP
 *  #define DART_TRAP    2 →  DART_TRAP
 *  #define TRAPDOOR     3 →  TRAPDOOR
 *  #define TELEP_TRAP   4 →  TELEP_TRAP
 *  #define PIT          5 →  PIT
 *  #define SLP_GAS_TRAP 6 →  SLP_GAS_TRAP
 *  #define PIERC        7 →  PIERC
 *  #define MIMIC        8 →  MIMIC   (level-gen only)
 * </pre>
 *
 * @see Trap
 */
public enum TrapType {
    /** Bear trap — immobilises large creatures and the player */
    BEAR_TRAP    (0, " bear trap"),
    /** Arrow trap — fires an arrow at whoever steps on it */
    ARROW_TRAP   (1, "n arrow trap"),
    /** Dart trap — fires a possibly-poisoned dart */
    DART_TRAP    (2, " dart trap"),
    /** Trapdoor — drops the player to a lower level */
    TRAPDOOR     (3, " trapdoor"),
    /** Teleportation trap — teleports the triggering creature */
    TELEP_TRAP   (4, " teleportation trap"),
    /** Pit — player falls in and must climb out */
    PIT          (5, " pit"),
    /** Sleeping gas trap — puts creatures to sleep */
    SLP_GAS_TRAP (6, " sleeping gas trap"),
    /** Piercer — disguised as a stalactite, drops on victim */
    PIERC        (7, " piercer"),
    /** Mimic trap — used only during level generation */
    MIMIC        (8, " mimic");

    /**
     * The trap's name with its correct indefinite article.
     *
     * <p>{@link #description} is stored as a suffix so that {@code "a" + desc}
     * reads correctly ("a bear trap", "an arrow trap"). Callers that added
     * their own space produced "a n arrow trap"; use this method instead.</p>
     */
    public String withArticle() { return "a" + description; }

    /** The trap's name with no article at all. */
    public String plainName() { return description.trim().replaceFirst("^n ", ""); }

    /** Original integer value */
    public final int value;
    /** Text suffix used in "You escape a&lt;desc&gt;" messages */
    public final String description;

    TrapType(int value, String description) {
        this.value       = value;
        this.description = description;
    }

    /** Total number of trap varieties (mirrors {@code TRAPNUM}). */
    public static final int TRAPNUM = 9;

    /**
     * Looks up a TrapType by its integer value.
     *
     * @param v integer from the original C constant
     * @return matching TrapType, or {@link #BEAR_TRAP} as fallback
     */
    public static TrapType fromValue(int v) {
        for (TrapType t : values()) {
            if (t.value == v) return t;
        }
        return BEAR_TRAP;
    }
}
