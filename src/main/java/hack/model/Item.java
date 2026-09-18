package hack.model;

/**
 * Item.java – A single game-object instance (weapon, food, potion, etc.).
 *
 * Ports {@code struct obj} from {@code def.obj.h}.  The flexible tail
 * ({@code oextra[1]}) that held either a name string or a gold amount is
 * replaced by dedicated fields: {@link #customName} and {@link #goldAmount}.
 *
 * <p>Items also hold a reference to their {@link ItemClass} (the
 * {@code objclass} prototype), which never changes.
 *
 * @see ItemClass
 * @see ItemTable
 */
public class Item {

    // -----------------------------------------------------------------------
    // Identity
    // -----------------------------------------------------------------------

    /** Unique object ID (mirrors {@code o_id}). */
    private final int id;
    /** Object type index into {@link ItemTable#OBJECTS} (mirrors {@code otyp}). */
    private int typeIndex;
    /** The class/prototype data. */
    private ItemClass itemClass;
    /** Optional override for the display name (used for named corpses). */
    private String displayName;
    // /** Optional override for the display name (used for named corpses). */
    // private String displayName;

    // -----------------------------------------------------------------------
    // Position
    // -----------------------------------------------------------------------

    /** Column where the object rests (0 if carried). */
    private int x;
    /** Row where the object rests (0 if carried). */
    private int y;
    /** Last display column (valid when {@code displayed} is true). */
    private int dispX;
    /** Last display row. */
    private int dispY;

    // -----------------------------------------------------------------------
    // Quantity and quality
    // -----------------------------------------------------------------------

    /** Stack count (mirrors {@code quan}). */
    private int quantity;
    /** Enchantment / charges / special (mirrors {@code spe}). */
    private int enchantment;
    /** Weight in units (mirrors {@code owt}). */
    private int weight;

    // -----------------------------------------------------------------------
    // Display
    // -----------------------------------------------------------------------

    /** The single-character symbol (mirrors {@code olet}). */
    private char symbol;

    // -----------------------------------------------------------------------
    // Flags
    // -----------------------------------------------------------------------

    /** Player knows the exact nature of this item. */
    private boolean known;
    /** Player has at least seen the colour/text (dknown). */
    private boolean descriptionKnown;
    /** Currently cursed. */
    private boolean cursed;
    /** On a shopkeeper's unpaid bill. */
    private boolean unpaid;
    /** Rust-free (no effect from rust monster). */
    private boolean rustFree;
    /** Item is displayed on the map. */
    private boolean displayed;

    // -----------------------------------------------------------------------
    // Worn bitmask
    // -----------------------------------------------------------------------

    /**
     * Which equipment slot(s) this item occupies.
     * Mirrors {@code owornmask} and the W_* constants from {@code def.obj.h}.
     * Uses {@link WornMask} constants.
     */
    private long wornMask;

    // -----------------------------------------------------------------------
    // Extra data
    // -----------------------------------------------------------------------

    /** Player-assigned custom name (ONAME in C). */
    private String customName;
    /** Gold amount for pseudo-gold-objects (OGOLD in C). */
    private long goldAmount;
    /** Creation turn (mirrors {@code age}). */
    private long age;

    /** Last player or monster name that possessed this item. The dog eats floor
     *  items only if their lastOwner equals the player's name. */
    private String lastOwner = null;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Creates a new item of the given type.
     *
     * @param id        unique item ID
     * @param typeIndex index into {@link ItemTable#OBJECTS}
     * @param itemClass prototype data
     */
    public Item(int id, int typeIndex, ItemClass itemClass) {
        this.id        = id;
        this.typeIndex = typeIndex;
        this.itemClass = itemClass;
        this.symbol    = itemClass.symbol;
        this.quantity  = 1;
        this.weight    = itemClass.weight;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    public int       getId()          { return id; }
    public int       getTypeIndex()   { return typeIndex; }
    public void      setTypeIndex(int t) { typeIndex=t; }
    public ItemClass getItemClass()   { return itemClass; }
    public void      setItemClass(ItemClass c) { itemClass=c; symbol=c.symbol; }

    public int  getX()           { return x; }
    public int  getY()           { return y; }
    public void setX(int x)      { this.x=x; }
    public void setY(int y)      { this.y=y; }
    public void setPos(int x, int y) { this.x=x; this.y=y; }
    public int  getDispX()       { return dispX; }
    public int  getDispY()       { return dispY; }
    public void setDispPos(int x, int y) { dispX=x; dispY=y; }

    public int  getQuantity()    { return quantity; }
    public void setQuantity(int q) { quantity=q; }
    public int  getEnchantment() { return enchantment; }
    public void setEnchantment(int e) { enchantment=e; }
    public int  getWeight()      { return weight; }
    public void setWeight(int w) { weight=w; }

    public char getSymbol()      { return symbol; }
    public void setSymbol(char s){ symbol=s; }

    public boolean isKnown()     { return known; }
    public void    setKnown(boolean b)   { known=b; }
    public boolean isDescriptionKnown()  { return descriptionKnown; }
    public void    setDescriptionKnown(boolean b){ descriptionKnown=b; }
    public boolean isCursed()    { return cursed; }
    public void    setCursed(boolean b)  { cursed=b; }
    public boolean isUnpaid()    { return unpaid; }
    public void    setUnpaid(boolean b)  { unpaid=b; }
    public boolean isRustFree()  { return rustFree; }
    public void    setRustFree(boolean b){ rustFree=b; }
    public boolean isDisplayed() { return displayed; }
    public void    setDisplayed(boolean b){ displayed=b; }

    public long getWornMask()    { return wornMask; }
    public void setWornMask(long m) { wornMask=m; }
    public boolean isWorn()      { return wornMask != 0; }
    public boolean isWorn(long slot) { return (wornMask & slot) != 0; }

    public String  getCustomName()       { return customName; }
    public void    setCustomName(String n){ customName=n; }
    public long    getGoldAmount()       { return goldAmount; }
    public void    setGoldAmount(long g) { goldAmount=g; }
    public long    getAge()              { return age; }
    public void    setAge(long a)        { age=a; }
    public String  getLastOwner()        { return lastOwner; }
    public void    setLastOwner(String s){ this.lastOwner = s; }

    // -----------------------------------------------------------------------
    // Convenience helpers
    // -----------------------------------------------------------------------

    /**
     * Returns a display name for this item, respecting identification state.
     * If known or the class name is always known, returns the real name.
     * Otherwise returns the description or "something".
     *
     * @return display name string
     */
    public String displayName() {
        if (customName != null) return itemClass.name + " called " + customName;
        if (known || itemClass.nameAlwaysKnown) return itemClass.name;
        if (itemClass.description != null)      return itemClass.description;
        return "something";
    }

    /**
     * Returns a full description including enchantment, curses, and count.
     * Mirrors the spirit of {@code doname()} in {@code hack.objnam.c}.
     *
     * @return verbose item description
     */
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String n) { this.displayName = n; }

    public String doname() {
        if (displayName != null) return displayName;
        StringBuilder sb = new StringBuilder();
        if (quantity > 1) sb.append(quantity).append(' ');
        sb.append(displayName());
        if (known && enchantment != 0) {
            sb.append(enchantment > 0 ? " (+" : " (").append(enchantment).append(')');
        }
        if (cursed) sb.append(" {cursed}");
        return sb.toString();
    }
}
