package hack.model;

/**
 * WornMask.java – Bitmask constants for equipment slots.
 *
 * Ports the {@code W_*} defines from {@code def.obj.h}.
 * Used in {@link Item#getWornMask()} and {@link Player#getWornItem(long)}.
 *
 * <pre>
 *  Original C        Value (octal)   Java constant
 *  ---------         -------------   -------------
 *  #define W_ARM     01L         →   ARM
 *  #define W_ARM2    02L         →   ARM2
 *  #define W_ARMH    04L         →   ARMH
 *  #define W_ARMS    010L        →   ARMS
 *  #define W_ARMG    020L        →   ARMG
 *  #define W_ARMOR   W_ARM|…     →   ARMOR (combined)
 *  #define W_RINGL   010000L     →   RING_LEFT
 *  #define W_RINGR   020000L     →   RING_RIGHT
 *  #define W_RING    W_RINGL|…   →   RING (combined)
 *  #define W_WEP     01000L      →   WEAPON
 *  #define W_BALL    02000L      →   BALL
 *  #define W_CHAIN   04000L      →   CHAIN
 * </pre>
 */
public final class WornMask {

    private WornMask() { /* constants only */ }

    /** Main armour body slot */
    public static final long ARM       = 0x001L;
    /** Secondary armour (cloak / elven cloak) */
    public static final long ARM2      = 0x002L;
    /** Helmet */
    public static final long ARMH      = 0x004L;
    /** Shield */
    public static final long ARMS      = 0x008L;
    /** Gloves */
    public static final long ARMG      = 0x010L;
    /** All armour slots combined */
    public static final long ARMOR     = ARM | ARM2 | ARMH | ARMS | ARMG;
    /** Left ring finger */
    public static final long RING_LEFT = 0x1000L;
    /** Right ring finger */
    public static final long RING_RIGHT= 0x2000L;
    /** Both ring slots */
    public static final long RING      = RING_LEFT | RING_RIGHT;
    /** Wielded weapon */
    public static final long WEAPON    = 0x100L;
    /** Ball (punishment) */
    public static final long BALL      = 0x200L;
    /** Chain (punishment) */
    public static final long CHAIN     = 0x400L;
}
