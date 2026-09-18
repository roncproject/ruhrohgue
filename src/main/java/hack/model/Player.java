package hack.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Player.java – All state belonging to the player character.
 *
 * Ports {@code struct you} from {@code hack.h} and the global equipment
 * pointers ({@code uwep}, {@code uarm}, etc.) from {@code hack.Decl.c}.
 *
 * <p>The C {@code uprops[LAST_RING+9]} array (28 property slots) is
 * replaced by a named {@link Property} inner class and explicit field
 * accessors, making the intent of each property clear.
 *
 * @see Property
 * @see Monster
 */
public class Player {

    // -----------------------------------------------------------------------
    // Position
    // -----------------------------------------------------------------------

    /** Current column. */
    private int x;
    /** Current row. */
    private int y;
    /** Last known displayed column. */
    private int dispX;
    /** Last known displayed row. */
    private int dispY;
    /** Move direction X (-1/0/1). */
    private int dx;
    /** Move direction Y (-1/0/1). */
    private int dy;
    /** Move direction Z (-1/0/1) for up/down. */
    private int dz;

    // -----------------------------------------------------------------------
    // Identity
    // -----------------------------------------------------------------------

    /** Player's name (plname in C). */
    private String name;
    /** Character class string, e.g. "Fighter" (pl_character in C). */
    private String characterClass;
    /** Display symbol — always '@' unless swallowed or mimicking. */
    private char symbol = '@';
    /** True if the player is female (affects some messages). */
    private boolean female;

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    /** Current hit points. */
    private int hp;
    /** Maximum hit points. */
    private int hpMax;
    /** Experience level (1–14). */
    private int level;
    /** Accumulated experience points. */
    private long experience;
    /** Role-specific experience (for scoring). */
    private long roleExperience;
    /** Gold carried (ugold). */
    private long gold;
    /** Gold at start of level (ugold0, for scoring). */
    private long goldAtLevelStart;
    /** Current strength (ustr, max 118). */
    private int strength;
    /** Maximum strength achievable (ustrmax). */
    private int strengthMax;
    /** Damage increment from intrinsics (udaminc). */
    private int damageBonus;
    /** Effective armour class (uac, calculated by find_ac). */
    private int ac;
    /** Luck value (uluck, -5 to +5 roughly). */
    private int luck;

    // -----------------------------------------------------------------------
    // Hunger
    // -----------------------------------------------------------------------

    /** Raw hunger counter (uhunger, starts at 900). */
    private int hunger;
    /** Current hunger state. */
    private HungerState hungerState;

    // -----------------------------------------------------------------------
    // Trap state
    // -----------------------------------------------------------------------

    /** Turns remaining trapped (utrap). */
    private int trapTurns;
    /** Type of trap the player is stuck in. */
    private int trapType; // 0=bear, 1=pit  (TT_BEARTRAP / TT_PIT in C)

    // -----------------------------------------------------------------------
    // Swallow state
    // -----------------------------------------------------------------------

    /** True if the player has been swallowed by a monster. */
    private boolean swallowed;
    /** Turns the player has been swallowed (uswldtim). */
    private int swallowTime;

    // -----------------------------------------------------------------------
    // Misc flags
    // -----------------------------------------------------------------------

    /** True if the player's hands glow blue (confuse-monster attack). */
    private boolean confusingHands;
    /** Which room's shop the player is currently in (0 = none). */
    private int inShop;
    /** Amount of gold deposited in vault (uinvault). */
    private int inVaultGold;
    /** Bank balance — gold deposited in a vault, visible on the status panel. */
    private long bank = 0;

    /** Items picked up from a shop that have not yet been paid for. */
    private final java.util.List<hack.model.Item> unpaidItems = new java.util.ArrayList<>();
    public java.util.List<hack.model.Item> getUnpaidItems() { return unpaidItems; }
    public boolean hasUnpaidItems() { return !unpaidItems.isEmpty(); }

    // -----------------------------------------------------------------------
    // Kill count
    // -----------------------------------------------------------------------

    /** Kills per monster species index (matches MonsterPrototype.ALL indices). */
    private final int[] killCount = new int[MonsterPrototype.CMNUM + 2];

    // -----------------------------------------------------------------------
    // Properties (intrinsics + ring effects)  — mirrors uprops[LAST_RING+9]
    // -----------------------------------------------------------------------

    /** Number of ring property slots. Mirrors LAST_RING = 19. */
    public static final int LAST_RING  = 19;
    /** Total number of property slots. Mirrors LAST_RING+9 = 28. */
    public static final int NPROPS     = 28;

    /** Property slot indices (mirrors the #defines in hack.h). */
    public static final int PROP_TELEPAT      = LAST_RING;     // 19
    public static final int PROP_FAST         = LAST_RING + 1; // 20
    public static final int PROP_CONFUSION    = LAST_RING + 2; // 21
    public static final int PROP_INVIS        = LAST_RING + 3; // 22
    public static final int PROP_GLIB         = LAST_RING + 4; // 23
    public static final int PROP_PUNISHED     = LAST_RING + 5; // 24
    public static final int PROP_SICK         = LAST_RING + 6; // 25
    public static final int PROP_BLIND        = LAST_RING + 7; // 26
    public static final int PROP_WOUNDED_LEGS = LAST_RING + 8; // 27

    /** The 28 property slots. */
    private final Property[] props = new Property[NPROPS];

    // -----------------------------------------------------------------------
    // Equipment slots
    // -----------------------------------------------------------------------

    /** Wielded weapon (uwep). Null if none. */
    private Item weapon;
    /** Body armour (uarm). */
    private Item armor;
    /** Second body armour / cloak (uarm2). */
    private Item armor2;
    /** Helmet (uarmh). */
    private Item helmet;
    /** Shield (uarms). */
    private Item shield;
    /** Gloves (uarmg). */
    private Item gloves;
    /** Left ring (uleft). */
    private Item ringLeft;
    /** Right ring (uright). */
    private Item ringRight;
    /** Ball (when punished). */
    private Item ball;
    /** Chain (when punished). */
    private Item chain;

    // -----------------------------------------------------------------------
    // Inventory
    // -----------------------------------------------------------------------

    /** All items carried by the player. */
    private final List<Item> inventory = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Sick cause
    // -----------------------------------------------------------------------

    /** Description of what made the player sick (usick_cause). */
    private String sickCause;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a new player with the given name and character class.
     *
     * @param name           player name
     * @param characterClass class string, e.g. "Fighter"
     */
    public Player(String name, String characterClass) {
        this.name           = name;
        this.characterClass = characterClass;
        for (int i = 0; i < NPROPS; i++) props[i] = new Property();
        hunger      = 900;
        hungerState = HungerState.NOT_HUNGRY;
        level       = 1;
    }

    // -----------------------------------------------------------------------
    // Property inner class
    // -----------------------------------------------------------------------

    /**
     * Property.java – One row in the player's intrinsic-property table.
     *
     * Mirrors {@code struct prop} from {@code hack.h}:
     * <pre>
     *   struct prop {
     *       long p_flgs;         // combined timeout + ring-slot flags
     *       int (*p_tofn)();     // callback on timeout
     *   };
     * </pre>
     *
     * In Java the timeout and ring-source flags are split into separate
     * fields for clarity.
     */
    public static final class Property {
        /** Turn countdown. When &gt; 0 the property is active via timeout. */
        public long timeout;
        /** True when the left ring grants this property. */
        public boolean fromLeftRing;
        /** True when the right ring grants this property. */
        public boolean fromRightRing;
        /** True when the property is an intrinsic (permanent). */
        public boolean intrinsic;

        /** Returns true if this property is currently active from any source. */
        public boolean isActive() {
            return timeout > 0 || fromLeftRing || fromRightRing || intrinsic;
        }

        /** Sets the intrinsic flag (mirrors {@code FOO |= INTRINSIC}). */
        public void grantIntrinsic() { intrinsic = true; }

        /** Decrements the timeout by 1 if positive. */
        public void tick() { if (timeout > 0) timeout--; }
    }

    // -----------------------------------------------------------------------
    // Property accessors
    // -----------------------------------------------------------------------

    /** Returns the property slot at index {@code i}. */
    public Property prop(int i) { return props[i]; }

    /** True if the given property is active from any source. */
    public boolean hasProp(int i) { return i >= 0 && i < NPROPS && props[i].isActive(); }

    // Convenient named accessors (mirror the original C macros)
    public boolean isFast()          { return hasProp(PROP_FAST); }
    public boolean isConfused()       { return hasProp(PROP_CONFUSION) || confusingHands; }
    public boolean isInvisible()      { return hasProp(PROP_INVIS); }
    public boolean isBlind()          { return hasProp(PROP_BLIND); }
    public boolean hasTelepathy()     { return hasProp(PROP_TELEPAT); }
    public boolean isPunished()       { return hasProp(PROP_PUNISHED); }
    public boolean isSick()           { return hasProp(PROP_SICK); }
    public boolean isGlib()           { return hasProp(PROP_GLIB); }
    public boolean hasWoundedLegs()   { return hasProp(PROP_WOUNDED_LEGS); }

    // Ring-granted properties (index 0–18 mirrors RIN_ADORNMENT–RIN_TELEPORT_CONTROL)
    public boolean isLevitating()     { return hasProp(6); }   // RIN_LEVITATION idx
    public boolean hasPoisonRes()     { return hasProp(7); }
    public boolean hasFireRes()       { return hasProp(10); }
    public boolean hasColdRes()       { return hasProp(11); }
    public boolean hasProtFromSC()    { return hasProp(12); }
    public boolean hasSeeInvisible()  { return hasProp(4); }
    public boolean hasStealth()       { return hasProp(5); }
    public boolean hasRegeneration()  { return hasProp(2); }
    public boolean hasSearching()     { return hasProp(3); }
    public boolean hasTeleportCtrl()  { return hasProp(18); }
    public boolean hasTeleportation() { return hasProp(1); }
    public boolean hasAggravateMonster(){ return hasProp(8); }
    public boolean hasHungerRing()    { return hasProp(9); }
    public boolean hasConflict()      { return hasProp(13); }

    /** Ticks all property timeouts down by one. */
    public void tickProperties() {
        for (Property p : props) p.tick();
    }

    // -----------------------------------------------------------------------
    // Standard getters / setters
    // -----------------------------------------------------------------------

    public int    getX()           { return x; }
    public int    getY()           { return y; }
    public void   setX(int x)      { this.x = x; }
    public void   setY(int y)      { this.y = y; }
    public void   setPos(int x, int y) { this.x=x; this.y=y; }
    public int    getDispX()       { return dispX; }
    public int    getDispY()       { return dispY; }
    public void   setDispPos(int x, int y) { dispX=x; dispY=y; }
    public int    getDx()          { return dx; }
    public int    getDy()          { return dy; }
    public int    getDz()          { return dz; }
    public void   setDir(int dx, int dy, int dz) { this.dx=dx; this.dy=dy; this.dz=dz; }

    public String getName()            { return name; }
    public String getCharacterClass()  { return characterClass; }
    public char   getSymbol()          { return symbol; }
    public void   setSymbol(char s)    { symbol=s; }
    public boolean isFemale()          { return female; }
    public void   setFemale(boolean f) { female=f; }

    public int  getHp()            { return hp; }
    public void setHp(int hp)      { this.hp=hp; }
    public int  getHpMax()         { return hpMax; }
    public void setHpMax(int m)    { hpMax=m; }
    public int  getLevel()         { return level; }
    public void setLevel(int l)    { level=l; }
    public long getExperience()    { return experience; }
    public void setExperience(long e){ experience=e; }
    public long getRoleExperience(){ return roleExperience; }
    public void setRoleExperience(long e){ roleExperience=e; }
    public long getGold()          { return gold; }
    public void setGold(long g)    { gold=g; }
    public long getGoldAtLevelStart(){ return goldAtLevelStart; }
    public void setGoldAtLevelStart(long g){ goldAtLevelStart=g; }
    public int  getStrength()      { return strength; }
    public void setStrength(int s) { strength=Math.max(0,Math.min(118,s)); }
    public int  getStrengthMax()   { return strengthMax; }
    public void setStrengthMax(int s){ strengthMax=s; }
    public int  getDamageBonus()   { return damageBonus; }
    public void setDamageBonus(int d){ damageBonus=d; }
    public int  getAc()            { return ac; }
    public void setAc(int a)       { ac=a; }
    public int  getLuck()          { return luck; }
    public void setLuck(int l)     { luck=l; }
    public void adjustLuck(int d)  { luck+=d; }

    public int  getHunger()        { return hunger; }
    public void setHunger(int h)   { hunger=h; hungerState=HungerState.forHunger(h); }
    public HungerState getHungerState(){ return hungerState; }
    public void setHungerState(HungerState s){ hungerState=s; }

    public int  getTrapTurns()     { return trapTurns; }
    public void setTrapTurns(int t){ trapTurns=t; }
    public int  getTrapType()      { return trapType; }
    public void setTrapType(int t) { trapType=t; }

    public boolean isSwallowed()   { return swallowed; }
    public void    setSwallowed(boolean b){ swallowed=b; }
    public int     getSwallowTime(){ return swallowTime; }
    public void    setSwallowTime(int t){ swallowTime=t; }

    public boolean isConfusingHands(){ return confusingHands; }
    public void    setConfusingHands(boolean b){ confusingHands=b; }
    public int     getInShop()     { return inShop; }
    public void    setInShop(int r){ inShop=r; }
    public int     getInVaultGold(){ return inVaultGold; }
    public void    setInVaultGold(int g){ inVaultGold=g; }
    public long    getBank()       { return bank; }
    public void    setBank(long b) { bank = b; }

    public int[]   getKillCount()  { return killCount; }
    public void    recordKill(int monsterIndex) {
        if (monsterIndex >= 0 && monsterIndex < killCount.length)
            killCount[monsterIndex]++;
    }
    public int     getKills(int monsterIndex) {
        if (monsterIndex >= 0 && monsterIndex < killCount.length)
            return killCount[monsterIndex];
        return 0;
    }

    // Equipment
    public Item getWeapon()  { return weapon; }
    public void setWeapon(Item i) { weapon=i; }
    public Item getArmor()   { return armor; }
    public void setArmor(Item i)  { armor=i; }
    public Item getArmor2()  { return armor2; }
    public void setArmor2(Item i) { armor2=i; }
    public Item getHelmet()  { return helmet; }
    public void setHelmet(Item i) { helmet=i; }
    public Item getShield()  { return shield; }
    public void setShield(Item i) { shield=i; }
    public Item getGloves()  { return gloves; }
    public void setGloves(Item i) { gloves=i; }
    public Item getRingLeft(){ return ringLeft; }
    public void setRingLeft(Item i){ ringLeft=i; }
    public Item getRingRight(){ return ringRight; }
    public void setRingRight(Item i){ ringRight=i; }
    public Item getBall()    { return ball; }
    public void setBall(Item i)   { ball=i; }
    public Item getChain()   { return chain; }
    public void setChain(Item i)  { chain=i; }

    public List<Item> getInventory() { return inventory; }

    public String getSickCause()     { return sickCause; }
    public void   setSickCause(String s){ sickCause=s; }

    // -----------------------------------------------------------------------
    // Computed helpers
    // -----------------------------------------------------------------------

    /**
     * Computes the attack bonus from strength.
     * Mirrors {@code abon()} in {@code hack.worn.c}.
     *
     * @return attack bonus
     */
    public int attackBonus() {
        if (strength == 118)  return 3;
        if (strength >= 17)   return 2;
        if (strength >= 16)   return 1;
        if (strength >= 8)    return 0;
        if (strength >= 6)    return -1;
        if (strength >= 4)    return -2;
        return -3;
    }

    /**
     * Computes the damage bonus from strength.
     * Mirrors {@code dbon()} in {@code hack.worn.c}.
     *
     * @return damage bonus
     */
    public int damBonus() {
        if (strength == 118) return 6;
        if (strength >= 17)  return 1;
        if (strength >= 7)   return 0;
        return -1;
    }

    /**
     * Returns the total carry weight of all inventory items.
     * Used in encumbrance checks.  Mirrors {@code inv_weight()}.
     *
     * @return total weight
     */
    public int inventoryWeight() {
        int w = 0;
        for (Item it : inventory) w += it.getWeight() * it.getQuantity();
        return w;
    }
}
