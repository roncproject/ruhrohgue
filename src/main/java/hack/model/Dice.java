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

    private Dice() {}

    /**
     * Per-session RNG state (TRK-02A / TRK-02B).
     *
     * <p>Dice used to hold this as plain static fields: one {@link Random},
     * one game seed, one generation-depth counter, shared by every player
     * on the server. A {@code /new} in one browser session reseeded the
     * exact same {@code Random} instance every other in-progress session
     * was reading from (TRK-02A), and two sessions generating a floor at
     * the same time raced on the same non-atomic {@code generationDepth}
     * and {@code savedState} fields, each capable of corrupting the
     * other's stream (TRK-02B).</p>
     *
     * <p>Each field that used to be {@code static} now lives in one of
     * these, one per {@link hack.web.GameSession} — so two sessions no
     * longer share any mutable RNG state at all, regardless of timing.</p>
     */
    public static final class State {
        final Random rng = new Random();
        long gameSeed;
        int  generationDepth;
        long savedState;
    }

    /**
     * Binds the calling thread to one session's {@link State} for the
     * duration of a single request. Spring MVC handles one HTTP request
     * per thread from a pool, so this is set at the top of every
     * controller method that touches game logic and must be cleared in a
     * {@code finally} block — otherwise a reused pool thread would leak
     * one session's Dice state into whichever session's request it
     * handles next, recreating TRK-02A by a different route.
     */
    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(State::new);

    /** Binds {@code state} to the current thread. Pair with {@link #unbind()}. */
    public static void bind(State state) { CURRENT.set(state); }

    /**
     * Clears the current thread's binding. Falling back to a fresh,
     * unshared {@link State} (rather than throwing) if Dice is ever used
     * without an explicit {@link #bind}, e.g. {@code HackApplication}'s
     * startup seed call, or a unit test calling {@code Dice.rnd(...)}
     * directly — both get their own isolated stream instead of a crash.
     */
    public static void unbind() { CURRENT.remove(); }

    private static State current() { return CURRENT.get(); }

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
        State s = current();
        s.rng.setSeed(seed);
        s.gameSeed = seed;
        s.generationDepth = 0;
    }

    // -----------------------------------------------------------------------
    // Deterministic level generation  (per-floor derived seeds)
    // -----------------------------------------------------------------------

    /** Returns the seed the current game was started with. */
    public static long gameSeed() { return current().gameSeed; }

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
        long z = current().gameSeed + 0x9E3779B97F4A7C15L * (dlevel + 1L);
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
        State s = current();
        if (s.generationDepth++ > 0) return;      // already inside a generation
        // Capture a continuation point for the main stream, then hand the RNG
        // over to the floor-specific seed.
        s.savedState = s.rng.nextLong();
        s.rng.setSeed(floorSeed(dlevel));
    }

    /**
     * Restores the main RNG stream after a floor has been generated.
     *
     * <p>The stream resumes from the continuation value captured by
     * {@link #beginLevelGeneration(int)}, so gameplay randomness stays
     * independent of how many draws level generation consumed.</p>
     */
    public static void endLevelGeneration() {
        State s = current();
        if (--s.generationDepth > 0) return;
        if (s.generationDepth < 0) s.generationDepth = 0;
        s.rng.setSeed(s.savedState);
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
        return current().rng.nextInt(x) + y;
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
        return current().rng.nextInt(x);
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
        return current().rng.nextInt(x) + 1;
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
