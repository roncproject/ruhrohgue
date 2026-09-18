package hack.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Monster.java – A live monster instance on the current dungeon level.
 *
 * Ports {@code struct monst} from {@code def.monst.h}.  The C bitfields
 * ({@code Bitfield(msleep,1)}, etc.) are replaced by plain boolean fields.
 * The flexible tail allocation ({@code mextra[1]}) is replaced by
 * specialised optional fields.
 *
 * <p>Each Monster holds a reference to its immutable
 * {@link MonsterPrototype} (the {@code *data} pointer in C) rather than
 * duplicating species data.
 *
 * @see MonsterPrototype
 */
public class Monster {

    // -----------------------------------------------------------------------
    // Identity and position
    // -----------------------------------------------------------------------

    /** Unique instance identifier (mirrors {@code m_id}). */
    private final int id;
    /** Species data (mirrors {@code struct permonst *data}). */
    private MonsterPrototype data;
    /** Current column position. */
    private int x;
    /** Current row position. */
    private int y;
    /** Last displayed column (valid when {@code mdispl} is true). */
    private int displayX;
    /** Last displayed row. */
    private int displayY;
    /** Whether {@code displayX/Y} are valid. */
    private boolean mdispl;

    // -----------------------------------------------------------------------
    // Recent movement track (for pathfinding, mirrors mtrack[MTSZ=4])
    // -----------------------------------------------------------------------

    /** Ring buffer of the last 4 positions visited. */
    private final List<Coord> track = new ArrayList<>(4);

    // -----------------------------------------------------------------------
    // Hit points
    // -----------------------------------------------------------------------

    /** Current HP. */
    private int hp;
    /** Maximum HP for this instance. */
    private int hpMax;

    // -----------------------------------------------------------------------
    // Flags (each C bitfield becomes a boolean)
    // -----------------------------------------------------------------------

    /** Mimic in disguise (not yet revealed). */
    private boolean mimic;
    /** Invisible to the player. */
    private boolean invisible;
    /** Shape-changer / chameleon. */
    private boolean shapechanger;
    /** Hides under floor objects. */
    private boolean hiding;
    /** Currently undetected in its hiding place. */
    private boolean undetected;
    /** Speed modifier: 0=normal, MSLOW=1, MFAST=2. */
    private int speedMod;
    /** Currently asleep. */
    private boolean sleeping;
    /** Frozen / paralysed. */
    private boolean frozen;
    /** Confused. */
    private boolean confused;
    /** Currently fleeing. */
    private boolean fleeing;
    /** Turns remaining in flee mode. */
    private int fleeTurns;
    /** Cancelled (magic-cancelled, reducing special attacks). */
    private boolean cancelled;
    /** Tame (player's pet). */
    private boolean tame;
    /** Peaceful (will not attack unless provoked). */
    private boolean peaceful;
    /** Is a shopkeeper. */
    private boolean shopkeeper;

    /**
     * Home room bounding box for confined monsters (shopkeepers, vault guards).
     * When set, the monster AI will not move outside these bounds.
     * Values: {lx, ly, hx, hy} or null if unconfined.
     */
    private int[] homeRoom = null;
    /** Is a vault guard. */
    private boolean guard;
    /** Can currently see. */
    private boolean canSee;
    /** Turns of temporary blindness remaining. */
    private int blindedTurns;
    /** Caught in a trap. */
    private boolean trapped;
    /** Bitmask of traps already encountered (avoidance). */
    private int trapsSeen;

    // -----------------------------------------------------------------------
    // Timing
    // -----------------------------------------------------------------------

    /** Move number when this monster last took a turn. */
    private long lastMoveTurn;

    // -----------------------------------------------------------------------
    // Inventory / gold
    // -----------------------------------------------------------------------

    /** Gold carried (dropped on death). */
    private long gold;
    /** Items held (simplified from C linked list). */
    private final List<Item> inventory = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Appearance override for mimics
    // -----------------------------------------------------------------------

    /** Displayed character when disguised (used by mimics). */
    private char disguiseSymbol;

    // -----------------------------------------------------------------------
    // Optional name (mnamelth > 0 in C)
    // -----------------------------------------------------------------------

    /** Pet name given by the player, or null. */
    private String petName;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a new monster of the given species at the specified position.
     *
     * <p>HP is randomised as {@code level * d(1,8)} unless level is 0,
     * in which case it defaults to 1.
     *
     * @param id   unique monster ID
     * @param data species prototype
     * @param x    starting column
     * @param y    starting row
     */
    public Monster(int id, MonsterPrototype data, int x, int y) {
        this.id   = id;
        this.data = data;
        this.x    = x;
        this.y    = y;

        int maxHp = Math.max(1, data.level * 4 + Dice.rnd(data.level * 4 + 1));
        this.hp    = maxHp;
        this.hpMax = maxHp;

        this.sleeping   = true;   // monsters start asleep
        this.canSee     = true;
        this.lastMoveTurn = 0;
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public int            getId()           { return id; }
    public MonsterPrototype getData()        { return data; }
    public void           setData(MonsterPrototype d) { this.data = d; }
    public int            getX()            { return x; }
    public int            getY()            { return y; }
    public void           setX(int x)       { this.x = x; }
    public void           setY(int y)       { this.y = y; }
    public void           setPos(int x, int y) { this.x = x; this.y = y; }
    public int            getDisplayX()     { return displayX; }
    public int            getDisplayY()     { return displayY; }
    public boolean        isMdispl()        { return mdispl; }
    public void           setMdispl(int x, int y) { displayX=x; displayY=y; mdispl=true; }
    public void           clearMdispl()     { mdispl=false; }
    public List<Coord>    getTrack()        { return track; }

    public int            getHp()           { return hp; }
    public void           setHp(int hp)     { this.hp = hp; }
    public int            getHpMax()        { return hpMax; }
    public void           setHpMax(int m)   { this.hpMax = m; }

    public boolean isMimic()       { return mimic; }
    public void    setMimic(boolean b)   { mimic = b; }
    public boolean isInvisible()   { return invisible; }
    public void    setInvisible(boolean b) { invisible = b; }
    public boolean isShapechanger(){ return shapechanger; }
    public void    setShapechanger(boolean b) { shapechanger=b; }
    public boolean isHiding()      { return hiding; }
    public void    setHiding(boolean b)  { hiding = b; }
    public boolean isUndetected()  { return undetected; }
    public void    setUndetected(boolean b){ undetected=b; }
    public int     getSpeedMod()   { return speedMod; }
    public void    setSpeedMod(int s) { speedMod = s; }
    public boolean isSleeping()    { return sleeping; }
    public void    setSleeping(boolean b){ sleeping=b; }
    public boolean isFrozen()      { return frozen; }
    public void    setFrozen(boolean b)  { frozen=b; }
    public boolean isConfused()    { return confused; }
    public void    setConfused(boolean b){ confused=b; }
    public boolean isFleeing()     { return fleeing; }
    public void    setFleeing(boolean b) { fleeing=b; }
    public int     getFleeTurns()  { return fleeTurns; }
    public void    setFleeTurns(int t)   { fleeTurns=t; }
    public boolean isCancelled()   { return cancelled; }
    public void    setCancelled(boolean b){ cancelled=b; }
    public boolean isTame()        { return tame; }
    public void    setTame(boolean b)    { tame=b; }
    public boolean isPeaceful()    { return peaceful; }
    public void    setPeaceful(boolean b){ peaceful=b; }
    public boolean isShopkeeper()  { return shopkeeper; }
    public void    setShopkeeper(boolean b){ shopkeeper=b; }

    /** Confines this monster to the given room bounds {lx, ly, hx, hy}. */
    public void   setHomeRoom(int lx, int ly, int hx, int hy) {
        this.homeRoom = new int[]{lx, ly, hx, hy};
    }
    /** Returns the home room bounds, or null if not confined. */
    public int[]  getHomeRoom() { return homeRoom; }
    /** True if this monster has a home room it cannot leave. */
    public boolean isConfined() { return homeRoom != null; }
    public boolean isGuard()       { return guard; }
    public void    setGuard(boolean b)   { guard=b; }
    public boolean canSee()        { return canSee; }
    public void    setCanSee(boolean b)  { canSee=b; }
    public int     getBlinderTurns(){ return blindedTurns; }
    public void    setBlinderTurns(int t){ blindedTurns=t; }
    public boolean isTrapped()     { return trapped; }
    public void    setTrapped(boolean b) { trapped=b; }
    public int     getTrapsSeen()  { return trapsSeen; }
    public void    setTrapsSeen(int t)   { trapsSeen=t; }
    public void    addTrapSeen(int bit)  { trapsSeen|=bit; }

    public long    getLastMoveTurn(){ return lastMoveTurn; }
    public void    setLastMoveTurn(long t){ lastMoveTurn=t; }

    public long           getGold()         { return gold; }
    public void           setGold(long g)   { this.gold = g; }
    public List<Item>     getInventory()     { return inventory; }

    public char    getDisguiseSymbol()       { return disguiseSymbol; }
    public void    setDisguiseSymbol(char c) { disguiseSymbol=c; }

    public String  getPetName()              { return petName; }
    public void    setPetName(String n)      { petName=n; }

    // -----------------------------------------------------------------------
    // Convenience helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the display character for this monster.
     * If it is a mimic, returns the disguise symbol.
     * Otherwise returns the species letter.
     *
     * @return character to display on the map
     */
    public char getDisplayChar() {
        return (mimic && disguiseSymbol != 0) ? disguiseSymbol : data.letter;
    }

    /**
     * Reduces HP by {@code damage}, clamping to a minimum of 0.
     *
     * @param damage amount of damage dealt
     * @return true if the monster is now dead (hp &le; 0)
     */
    public boolean takeDamage(int damage) {
        hp = Math.max(0, hp - damage);
        return hp <= 0;
    }

    /** True if this monster is dead (hp == 0). */
    public boolean isDead() { return hp <= 0; }

    /**
     * Pushes the current position onto the track ring (newest first).
     * Trims to 4 entries to mirror {@code mtrack[MTSZ]}.
     *
     * @param ox previous column
     * @param oy previous row
     */
    public void recordTrack(int ox, int oy) {
        if (track.size() >= 4) track.remove(track.size() - 1);
        track.add(0, new Coord(ox, oy));
    }
}
