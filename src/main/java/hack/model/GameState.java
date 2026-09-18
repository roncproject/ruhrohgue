package hack.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GameState.java – Complete mutable state of one running game.
 *
 * This class is the Java equivalent of all the global variables in
 * {@code hack.Decl.c} plus the game-flow variables in
 * {@code hack.main.c}.  It is the single source of truth passed into
 * every engine method.
 *
 * <p>The original C used dozens of global variables.  Collecting them
 * here makes threading safe (a single {@code synchronized} lock on this
 * object is sufficient for the HTTP server).
 */
public class GameState {

    // -----------------------------------------------------------------------
    // Game-phase enum
    // -----------------------------------------------------------------------

    /**
     * Phase of the current game.
     */
    public enum Phase {
        /** Character setup screen has not been submitted yet. */
        SETUP,
        /** Active play. */
        PLAYING,
        /** The player has died. */
        DEAD,
        /** The player escaped with the Amulet. */
        ESCAPED,
        /** The player quit. */
        QUIT
    }

    // -----------------------------------------------------------------------
    // Core state
    // -----------------------------------------------------------------------

    /** Current game phase. */
    private Phase phase = Phase.SETUP;

    /** The player character. */
    private Player player;

    /** Current dungeon depth (dlevel, 1 = top, 29 = deepest). */
    private int dungeonLevel = 1;

    /** Deepest level reached so far (maxdlevel). */
    private int maxDungeonLevel = 1;

    /** The current dungeon level map and content. */
    private DungeonLevel level;

    /**
     * Cache of previously visited dungeon levels.
     * Key = dungeon floor number (1, 2, 3, …).
     * When the player revisits a floor the saved DungeonLevel is restored,
     * preserving explored cells, items, and gold.
     */
    private final java.util.Map<Integer, DungeonLevel> levelMemory =
            new java.util.HashMap<>();

    // ── Timed effect counters ──────────────────────────────────────────────
    /** Special rooms already announced this level visit (prevents repeat messages). */
    private final java.util.Set<String> announcedRooms = new java.util.HashSet<>();
    /** Remaining random floor-jumps from eating a dead leprechaun. */
    private int leprechaunJumpsLeft    = 0;
    /** Turns between each leprechaun jump. */
    private int leprechaunJumpPause    = 0;
    /** Countdown until next leprechaun jump fires. */
    private int leprechaunJumpCountdown = 0;
    /** Remaining turns of floating-eye all-monster vision. */
    /** Remaining turns of floating-eye all-monster vision. Max 50. */
    private int floatingEyeVisionLeft  = 0;
    public static final int FLOATING_EYE_MAX_STEPS = 50;

    /** Turn counter (moves in C). */
    private long moves = 1;

    /**
     * Multi-turn action counter (multi in C).
     * Positive = extra moves; negative = immobilised.
     */
    private int multi = 0;

    // -----------------------------------------------------------------------
    // Object registry
    // -----------------------------------------------------------------------

    /**
     * All item instances in the game, keyed by ID.
     * Includes both floor items and inventory items.
     */
    private final Map<Integer, Item> items = new java.util.LinkedHashMap<>();
    /** Auto-incrementing item ID counter. */
    private int nextItemId = 1;

    /** Items currently on the dungeon floor. */
    private final List<Item> floorItems = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Monster registry
    // -----------------------------------------------------------------------

    /** All live monsters on the current level, keyed by ID. */
    private final Map<Integer, Monster> monsters = new java.util.LinkedHashMap<>();
    /** Auto-incrementing monster ID counter. */
    private int nextMonsterId = 1;

    /** The monster the player is stuck in (ustuck). */
    private Monster stuckMonster;

    /** Monsters that have fallen to the level below (fallen_down). */
    private final List<Monster> fallenDown = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Messages
    // -----------------------------------------------------------------------

    /**
     * Message queue: newest message is first.
     * The HTML frontend displays these as the game log.
     */
    private final Deque<String> messages = new ArrayDeque<>();

    /** The topmost message line currently displayed. */
    private String topMessage = "";

    /**
     * One-shot UI signal for the browser client (look overlay, shop tab,
     * chooser dialogs, …).
     *
     * <p>Signals are bracketed control strings such as {@code [look:a|b]}.
     * They are deliberately <b>not</b> stored in {@link #messages} and never
     * become the {@link #topMessage}: a signal describes a transient UI action
     * that must fire exactly once.  {@link #consumeSignal()} returns the
     * pending signal and clears it, so a later {@code GET /state} poll or a
     * subsequent command that produces no message of its own cannot replay
     * the same overlay.  This is the fix for the "look overlay keeps
     * reappearing on every step" defect.</p>
     */
    private String uiSignal;

    // ── Automatic floor-tile description ───────────────────────────────────
    /** X of the tile whose contents were last auto-described (-1 = none). */
    private int lastDescribedX = -1;
    /** Y of the tile whose contents were last auto-described (-1 = none). */
    private int lastDescribedY = -1;
    /** Dungeon floor on which {@link #lastDescribedX}/Y were recorded. */
    private int lastDescribedLevel = -1;

    // ── Reproducible-seed bookkeeping ──────────────────────────────────────
    /** The RNG seed this game was started with (for display / reproduction). */
    private long gameSeed;
    /** True when the seed was explicitly supplied rather than time-derived. */
    private boolean seedExplicit;

    /**
     * Message to display when a multi-turn action finishes (nomovemsg).
     * Null if no message pending.
     */
    private String pendingMessage;

    // -----------------------------------------------------------------------
    // End-game data
    // -----------------------------------------------------------------------

    /** What killed the player (killer in C). */
    private String killer = "";

    /** Final score. */
    private long finalScore;

    // -----------------------------------------------------------------------
    // Moon phase
    // -----------------------------------------------------------------------

    /** Current moon phase (0 = NEW_MOON, 4 = FULL_MOON). */
    private int moonPhase;

    // -----------------------------------------------------------------------
    // Genocide tracking
    // -----------------------------------------------------------------------

    /** Set of monster letters that have been genocided. */
    private final java.util.Set<Character> genocided = new java.util.HashSet<>();

    // -----------------------------------------------------------------------
    // Identification state
    // -----------------------------------------------------------------------

    /**
     * Indexed by ItemTable type index.  True = the player has identified
     * this object class (mirrors {@code oc_name_known} in C).
     */
    private final boolean[] identified = new boolean[ItemTable.NROFOBJECTS];

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a blank game state in the SETUP phase.
     * Call {@link #startNewGame(String, String)} to initialise fully.
     */
    public GameState() {}

    // -----------------------------------------------------------------------
    // Game initialisation
    // -----------------------------------------------------------------------

    /**
     * Initialises all fields for a fresh game and transitions to PLAYING.
     *
     * @param playerName     the player's chosen name
     * @param characterClass the character class string
     */
    public void startNewGame(String playerName, String characterClass) {
        // NOTE: the RNG is seeded by the caller (HackController.newGame) *before*
        // this method runs.  Re-seeding here with System.currentTimeMillis()
        // silently discarded the requested seed and was the root cause of the
        // "same seed, different dungeon" defect.  Do not re-seed here.
        player = new Player(playerName, characterClass);
        initPlayerStats(characterClass);
        phase = Phase.PLAYING;
        moves = 1;
        multi = 0;
        dungeonLevel = 1;
        maxDungeonLevel = 1;
        moonPhase = computeMoonPhase();
        items.clear();
        floorItems.clear();
        monsters.clear();
        messages.clear();
        uiSignal = null;
        lastDescribedX = lastDescribedY = lastDescribedLevel = -1;
        topMessage = "Hello " + playerName + ", welcome to RuhRohgue!";
    }

    /**
     * Sets starting statistics based on the character class.
     * Mirrors {@code u_init()} in {@code hack.u_init.c}.
     *
     * @param characterClass character class string
     */
    private void initPlayerStats(String characterClass) {
        Player p = player;
        char pc = characterClass.isEmpty() ? 'F' : Character.toUpperCase(characterClass.charAt(0));
        // Starting stats are doubled to balance against reduced (25%) monster damage.
        // Strength is capped at 118 (max allowed by Player.setStrength).
        switch (pc) {
            case 'C': p.setHp(32); p.setHpMax(32); p.setStrength(36); p.setStrengthMax(36); break;
            case 'T': p.setHp(20); p.setHpMax(20); p.setStrength(16); p.setStrengthMax(16); break;
            case 'W': p.setHp(30); p.setHpMax(30); p.setStrength(32); p.setStrengthMax(32); break;
            case 'S': p.setHp(24); p.setHpMax(24); p.setStrength(20); p.setStrengthMax(20);
                      p.prop(Player.PROP_FAST).intrinsic  = true;
                      p.prop(5).intrinsic                 = true;  // Stealth
                      break;
            case 'K': p.setHp(24); p.setHpMax(24); p.setStrength(20); p.setStrengthMax(20); break;
            case 'F': p.setHp(28); p.setHpMax(28); p.setStrength(34); p.setStrengthMax(34); break;
            default:  p.setHp(24); p.setHpMax(24); p.setStrength(32); p.setStrengthMax(32); break;
        }
        p.setHunger(900);

        // Give starting weapon based on character class
        // This makes early-game combat survivable and gives weapon hit bonus.
        int startWeapon = -1;
        switch (pc) {
            case 'F': startWeapon = hack.model.ItemTable.MACE;   break; // Fighter: mace
            case 'K': startWeapon = hack.model.ItemTable.MACE;   break; // Knight: mace
            case 'C': startWeapon = hack.model.ItemTable.DAGGER;  break; // Cavewoman: dagger
            case 'T': startWeapon = hack.model.ItemTable.DAGGER;  break; // Thief: dagger
            case 'S': startWeapon = hack.model.ItemTable.SPEAR;   break; // Samurai: spear
            case 'W': startWeapon = hack.model.ItemTable.DAGGER;  break; // Wizard: dagger
            default:  startWeapon = hack.model.ItemTable.DAGGER;  break;
        }
        if (startWeapon >= 0) {
            hack.model.Item wep = createItem(startWeapon);
            if (wep != null) {
                wep.setKnown(true);
                p.getInventory().add(wep);
                p.setWeapon(wep);
            }
        }
    }

    /**
     * Computes a rough moon phase (0-7).
     *
     * <p>When the game runs with an explicit seed the phase is derived from
     * that seed instead of the wall clock, so two runs of the same seed are
     * identical even across days.</p>
     */
    private int computeMoonPhase() {
        long days = seedExplicit
                ? Math.floorMod(gameSeed, 28L)
                : (System.currentTimeMillis() / 86_400_000L);
        return (int)((days % 28) / 4);
    }

    // -----------------------------------------------------------------------
    // Item management
    // -----------------------------------------------------------------------

    /**
     * Creates a new item, registers it, and returns it.
     *
     * @param typeIndex index into {@link ItemTable#OBJECTS}
     * @return the new item
     */
    public Item createItem(int typeIndex) {
        ItemClass cls = ItemTable.get(typeIndex);
        if (cls == null) cls = ItemTable.get(0);
        Item it = new Item(nextItemId++, typeIndex, cls);
        it.setAge(moves);
        items.put(it.getId(), it);
        return it;
    }

    /**
     * Adds an item to the dungeon floor at position (x, y).
     *
     * @param it the item to place
     * @param x  column
     * @param y  row
     */
    public void placeItem(Item it, int x, int y) {
        it.setPos(x, y);
        floorItems.add(it);
        // Update the map symbol so the item is visible
        if (level != null && DungeonLevel.isOk(x, y)) {
            Cell c = level.cellAt(x, y);
            if (!c.getType().isWall()) {
                c.setScrsym(it.getSymbol());
                c.markDirty();
            }
        }
    }

    /**
     * Removes an item from the floor (picked up or destroyed).
     * Does NOT remove it from {@link #items}.
     *
     * @param it item to remove from floor
     */
    public void removeFromFloor(Item it) {
        floorItems.remove(it);
    }

    /**
     * Returns the first floor item at (x, y), or null.
     * Mirrors {@code o_at(x, y)}.
     *
     * @param x column
     * @param y row
     * @return floor item or null
     */
    public Item itemAt(int x, int y) {
        for (Item it : floorItems) {
            if (it.getX() == x && it.getY() == y) return it;
        }
        return null;
    }

    /**
     * Returns the first floor item of the given type at (x, y), or null.
     * Mirrors {@code sobj_at(typeIndex, x, y)}.
     *
     * @param typeIndex item type to search for
     * @param x         column
     * @param y         row
     * @return matching item or null
     */
    public Item specificItemAt(int typeIndex, int x, int y) {
        for (Item it : floorItems) {
            if (it.getX() == x && it.getY() == y && it.getTypeIndex() == typeIndex) return it;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Monster management
    // -----------------------------------------------------------------------

    /**
     * Creates and registers a new monster of the given prototype at (x, y).
     *
     * @param proto monster prototype
     * @param x     column
     * @param y     row
     * @return the new monster, or null if the spot is occupied
     */
    public Monster createMonster(MonsterPrototype proto, int x, int y) {
        if (monsterAt(x, y) != null) return null;
        Monster m = new Monster(nextMonsterId++, proto, x, y);
        monsters.put(m.getId(), m);
        // Mark the cell with the monster's letter
        if (level != null && DungeonLevel.isOk(x, y)) {
            level.cellAt(x, y).setScrsym(proto.letter);
        }
        return m;
    }

    /**
     * Removes a monster from the registry (after death or level change).
     *
     * @param m monster to remove
     */
    public void removeMonster(Monster m) {
        monsters.remove(m.getId());
        if (stuckMonster == m) stuckMonster = null;
    }

    /**
     * Returns the monster at (x, y), or null.
     * Mirrors {@code m_at(x, y)}.
     *
     * @param x column
     * @param y row
     * @return monster at that position or null
     */
    public Monster monsterAt(int x, int y) {
        for (Monster m : monsters.values()) {
            if (m.getX() == x && m.getY() == y) return m;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Messaging
    // -----------------------------------------------------------------------

    /**
     * Pushes a message to the top of the message log.
     * Mirrors {@code pline(msg)}.
     *
     * @param msg the message text
     */
    public void pline(String msg) {
        if (msg == null) return;
        // Bracketed control strings are UI signals, not game messages.  They
        // must never enter the log or the top message bar — otherwise they
        // stay at messages[0] and the client replays the overlay on every
        // subsequent state refresh.
        if (isUiSignal(msg)) { uiSignal = msg; return; }
        topMessage = msg;
        messages.addFirst(msg);
        if (messages.size() > 100) messages.removeLast();
    }

    /** True when {@code msg} is a bracketed one-shot control string. */
    private static boolean isUiSignal(String msg) {
        return msg.length() > 1 && msg.charAt(0) == '[' && msg.endsWith("]");
    }

    /**
     * Returns the pending one-shot UI signal and clears it.
     *
     * @return the signal, or {@code null} when none is pending
     */
    public String consumeSignal() {
        String s = uiSignal;
        uiSignal = null;
        return s;
    }

    /** Discards any pending UI signal without delivering it. */
    public void clearSignal() { uiSignal = null; }

    /** Returns the most recently displayed message. */
    public String getTopMessage() { return topMessage; }

    /** Returns the full message log (newest first). */
    public Deque<String> getMessages() { return messages; }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    public Phase        getPhase()          { return phase; }
    public void         setPhase(Phase p)   { phase=p; }

    public Player       getPlayer()         { return player; }

    public int          getDungeonLevel()   { return dungeonLevel; }
    public void         setDungeonLevel(int l){ dungeonLevel=l; if(l>maxDungeonLevel) maxDungeonLevel=l; }
    public int          getMaxDungeonLevel(){ return maxDungeonLevel; }

    public DungeonLevel getLevel()          { return level; }
    public void         setLevel(DungeonLevel l){ level=l; }

    // ── Announced rooms ────────────────────────────────────────────────────
    public java.util.Set<String> getAnnouncedRooms() { return announcedRooms; }
    public void clearAnnouncedRooms() { announcedRooms.clear(); }

    // ── Timed effect accessors ──────────────────────────────────────────────
    public int getLeprechaunJumpsLeft()     { return leprechaunJumpsLeft; }
    public void setLeprechaunJumpsLeft(int v){ leprechaunJumpsLeft = v; }
    public int getLeprechaunJumpPause()     { return leprechaunJumpPause; }
    public void setLeprechaunJumpPause(int v){ leprechaunJumpPause = v; }
    public int getLeprechaunJumpCountdown() { return leprechaunJumpCountdown; }
    public void setLeprechaunJumpCountdown(int v){ leprechaunJumpCountdown = v; }
    public int getFloatingEyeVisionLeft()   { return floatingEyeVisionLeft; }
    public void setFloatingEyeVisionLeft(int v){ floatingEyeVisionLeft = v; }

    /** Returns the level memory cache (floor number → saved DungeonLevel). */
    public java.util.Map<Integer, DungeonLevel> getLevelMemory() { return levelMemory; }

    public long         getMoves()          { return moves; }
    public void         setMoves(long m)    { moves=m; }
    public void         incrementMoves()    { moves++; }

    public int          getMulti()          { return multi; }
    public void         setMulti(int m)     { multi=m; }

    public Map<Integer,Item>    getItems()    { return items; }
    public List<Item>           getFloorItems(){ return floorItems; }

    public Map<Integer,Monster> getMonsters() { return monsters; }
    public java.util.Collection<Monster> getLiveMonsters(){ return monsters.values(); }

    public Monster      getStuckMonster()   { return stuckMonster; }
    public void         setStuckMonster(Monster m){ stuckMonster=m; }

    public List<Monster> getFallenDown()    { return fallenDown; }

    public String       getKiller()         { return killer; }
    public void         setKiller(String k) { killer=k; }

    public long         getFinalScore()     { return finalScore; }
    public void         setFinalScore(long s){ finalScore=s; }

    public int          getMoonPhase()      { return moonPhase; }

    public java.util.Set<Character> getGenocided(){ return genocided; }

    public boolean      isIdentified(int typeIdx){
        return typeIdx >= 0 && typeIdx < identified.length && identified[typeIdx];
    }
    public void         identify(int typeIdx){
        if (typeIdx >= 0 && typeIdx < identified.length) identified[typeIdx]=true;
    }

    // ── Shop debt (remembered per floor) ───────────────────────────────────

    /**
     * Gold owed to each floor's shopkeeper for goods already consumed.
     *
     * <p>Held on the game state rather than on the shopkeeper monster, because
     * monsters are destroyed and re-created whenever a level is left and
     * revisited. Keeping the debt here is what lets the shopkeeper remember a
     * theft after the player has been away.</p>
     */
    private final Map<Integer, Long> shopDebt = new java.util.LinkedHashMap<>();

    /** Adds to what the player owes the shopkeeper on the given floor. */
    public void addShopDebt(int floor, long amount) {
        if (amount <= 0) return;
        shopDebt.merge(floor, amount, Long::sum);
    }

    /** Gold owed for consumed goods on the given floor. */
    public long getShopDebt(int floor) {
        return shopDebt.getOrDefault(floor, 0L);
    }

    /** Settles the consumed-goods debt for the given floor. */
    public void clearShopDebt(int floor) { shopDebt.remove(floor); }

    /** True when any floor's shopkeeper is still owed money. */
    public boolean hasAnyShopDebt() {
        for (long v : shopDebt.values()) if (v > 0) return true;
        return false;
    }

    public String       getPendingMessage() { return pendingMessage; }
    public void         setPendingMessage(String m){ pendingMessage=m; }

    // ── Bank interest ──────────────────────────────────────────────────────

    /** Interest accrued since the last deposit, withdrawal or payment. */
    private long accruedInterest;
    /** Turn on which interest was last reported to the player. */
    private long lastInterestReport;

    public long getAccruedInterest()          { return accruedInterest; }
    public void addAccruedInterest(long i)    { accruedInterest += i; }
    /** Called on any deposit, withdrawal or Bank payment. */
    public void resetAccruedInterest()        { accruedInterest = 0; }
    public long getLastInterestReport()       { return lastInterestReport; }
    public void setLastInterestReport(long t) { lastInterestReport = t; }

    // ── Seed bookkeeping ───────────────────────────────────────────────────

    /**
     * Records the RNG seed this game was started with.
     * Must be called <em>before</em> {@link #startNewGame(String, String)}
     * so the moon phase can be derived from it.
     *
     * @param seed     the seed passed to {@link Dice#seed(long)}
     * @param explicit true when the player (or --seed) chose the value
     */
    public void setSeedInfo(long seed, boolean explicit) {
        this.gameSeed     = seed;
        this.seedExplicit = explicit;
    }

    public long    getGameSeed()     { return gameSeed; }
    public boolean isSeedExplicit()  { return seedExplicit; }

    // ── Automatic floor-tile description ───────────────────────────────────

    /**
     * Returns true when the given tile has not yet been auto-described during
     * the current stay on it, and records it as described.
     *
     * <p>This keeps the "you see here" line to one per arrival: standing
     * still, searching, or fighting on the same tile does not repeat it.</p>
     */
    public boolean markDescribed(int x, int y) {
        if (x == lastDescribedX && y == lastDescribedY
                && dungeonLevel == lastDescribedLevel) {
            return false;
        }
        lastDescribedX     = x;
        lastDescribedY     = y;
        lastDescribedLevel = dungeonLevel;
        return true;
    }

    /** Forgets the last auto-described tile (used on level change). */
    public void resetDescribed() {
        lastDescribedX = lastDescribedY = lastDescribedLevel = -1;
    }
}
