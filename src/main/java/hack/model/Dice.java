package hack.model;

import java.util.Random;

/**
 * Dice.java – Random number generator utilities.
 *
 * Ports {@code rnd.c} verbatim, plus related helpers used throughout the
 * original C source. The original implementation used {@code (rand()>>3) % x};
 * Java's {@link Random} already provides uniform distribution so the
 * {@code >>3} shift is not required, but the function signatures and
 * semantics are preserved exactly.
 *
 * <pre>
 *   C function   Java equivalent         Description
 *   ----------   ------------------      -----------
 *   rn1(x,y)     rn1(x,y)                random in [y, y+x)
 *   rn2(x)       rn2(x)                  random in [0, x)
 *   rnd(x)       rnd(x)                  random in [1, x]  (1-based)
 *   d(n,x)       d(n,x)                  sum of n dice each 1..x
 * </pre>
 *
 * All methods are static so callers need not hold an instance.
 */
public final class Dice {

    /** The shared Random instance used by all dice functions. */
    private static final Random RNG = new Random();

    private Dice() {}

    // -----------------------------------------------------------------------
    // Seeding
    // -----------------------------------------------------------------------

    /**
     * Re-seeds the RNG.  Called once at game startup to ensure each
     * game is different.
     *
     * @param seed random seed (use {@link System#currentTimeMillis()} for
     *             non-reproducible games)
     */
    public static void seed(long seed) {
        RNG.setSeed(seed);
        gameSeed = seed;
        generationDepth = 0;
    }

    // -----------------------------------------------------------------------
    // Deterministic level generation  (per-floor derived seeds)
    // -----------------------------------------------------------------------

    /** The seed this game was started with — the basis for per-floor seeds. */
    private static long gameSeed;

    /** Guards against nested begin/endLevelGeneration pairs. */
    private static int generationDepth;

    /** Returns the seed the current game was started with. */
    public static long gameSeed() { return gameSeed; }

    /**
     * Derives the seed for one dungeon floor.
     *
     * <p>Mixing the floor number into the game seed with a 64-bit avalanche
     * (SplitMix64 finaliser) gives every floor its own well-separated,
     * repeatable seed.</p>
     *
     * @param dlevel dungeon floor number (1-based)
     * @return the seed to build that floor with
     */
    public static long floorSeed(int dlevel) {
        long z = gameSeed + 0x9E3779B97F4A7C15L * (dlevel + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Switches the RNG onto a private, floor-specific stream.
     *
     * <p>Level layout must not depend on how many random numbers the player's
     * fighting, searching and wandering happened to consume beforehand.
     * Without this, only floor 1 was reproducible from a seed: every deeper
     * floor was generated from whatever RNG state play had left behind, so the
     * same seed gave different floors 2, 3, 4 in different games.</p>
     *
     * <p>Always pair with {@link #endLevelGeneration()}.</p>
     *
     * @param dlevel dungeon floor being generated
     */
    public static void beginLevelGeneration(int dlevel) {
        if (generationDepth++ > 0) return;      // already inside a generation
        // Capture a continuation point for the main stream, then hand the RNG
        // over to the floor-specific seed.
        savedState = RNG.nextLong();
        RNG.setSeed(floorSeed(dlevel));
    }

    /** Continuation value used to resume the main stream after generation. */
    private static long savedState;

    /**
     * Restores the main RNG stream after a floor has been generated.
     *
     * <p>The stream resumes from the continuation value captured by
     * {@link #beginLevelGeneration(int)}, so gameplay randomness stays
     * independent of how many draws level generation consumed.</p>
     */
    public static void endLevelGeneration() {
        if (--generationDepth > 0) return;
        if (generationDepth < 0) generationDepth = 0;
        RNG.setSeed(savedState);
    }

    // -----------------------------------------------------------------------
    // Core functions  (match rnd.c)
    // -----------------------------------------------------------------------

    /**
     * Returns a random integer in the half-open range {@code [y, y+x)}.
     *
     * <pre>
     *   C:  rn1(x, y)  → (rand()>>3) % x + y
     * </pre>
     *
     * @param x range size (must be &gt; 0)
     * @param y lower bound (inclusive)
     * @return random value
     */
    public static int rn1(int x, int y) {
        if (x <= 0) return y;
        return RNG.nextInt(x) + y;
    }

    /**
     * Returns a random integer in the half-open range {@code [0, x)}.
     *
     * <pre>
     *   C:  rn2(x)  → (rand()>>3) % x
     * </pre>
     *
     * @param x upper bound (exclusive); must be &gt; 0
     * @return random value
     */
    public static int rn2(int x) {
        if (x <= 0) return 0;
        return RNG.nextInt(x);
    }

    /**
     * Returns a random integer in the closed range {@code [1, x]}.
     *
     * <pre>
     *   C:  rnd(x)  → (rand()>>3) % x + 1
     * </pre>
     *
     * @param x maximum value (inclusive); must be &gt; 0
     * @return random value
     */
    public static int rnd(int x) {
        if (x <= 0) return 1;
        return RNG.nextInt(x) + 1;
    }

    /**
     * Rolls {@code n} dice each with {@code x} faces and returns the sum.
     *
     * <pre>
     *   C:  d(n,x)  → sum of n * ((rand()>>3) % x + 1)
     * </pre>
     *
     * @param n number of dice (must be &gt; 0)
     * @param x faces per die  (must be &gt; 0)
     * @return sum of rolls
     */
    public static int d(int n, int x) {
        if (n <= 0 || x <= 0) return 0;
        int total = 0;
        for (int i = 0; i < n; i++) total += rnd(x);
        return total;
    }

    // -----------------------------------------------------------------------
    // Additional helpers used in the original source
    // -----------------------------------------------------------------------

    /**
     * Returns true with probability {@code 1/n}.
     *
     * @param n denominator (must be &gt; 0)
     * @return true with probability 1/n
     */
    public static boolean oneIn(int n) {
        return n > 0 && rn2(n) == 0;
    }

    /**
     * Returns 2 raised to the power {@code n}, clamped to avoid overflow.
     * Mirrors the {@code pow2()} function in the original C source.
     *
     * @param n exponent (clamped to [0, 62])
     * @return 2^n as a long
     */
    public static long pow2(int n) {
        if (n < 0)  n = 0;
        if (n > 62) n = 62;
        return 1L << n;
    }
}
