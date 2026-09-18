package hack.engine;

import hack.model.*;

/**
 * FoodEngine.java — nutrition, corpse decay and over-eating rules.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li><b>Corpse decay.</b> A corpse rots over {@link #ROT_STEPS} turns,
 *       whether it lies on the floor or is carried. Eating a rotten one costs
 *       health on a sliding scale — about 1% of maximum at the moment of
 *       death, rising to 50% at full rot — and gives no nutrition at all.
 *       Corpses inside a shop are refrigerated stock and never age.</li>
 *   <li><b>Street food.</b> Rotterdam delicacies satisfy twice the hunger of
 *       an ordinary item, but one time in ten a greasy parcel slips out of
 *       the player's hands.</li>
 *   <li><b>Gluttony.</b> Eating past twice the Satiated threshold is a
 *       struggle; past three times it is fatal.</li>
 * </ul>
 */
public final class FoodEngine {

    private FoodEngine() { }

    /** Turns a corpse takes to rot completely. */
    public static final int ROT_STEPS = 500;
    /** Health cost of a just-dead corpse, as a percentage of maximum. */
    public static final int MIN_ROT_DAMAGE_PCT = 1;
    /** Health cost of a fully rotten corpse, as a percentage of maximum. */
    public static final int MAX_ROT_DAMAGE_PCT = 50;
    /** Hunger value at which the player counts as Satiated. */
    public static final int SATIATED = 1000;
    /** Eating beyond this is a struggle. */
    public static final int STUFFED  = SATIATED * 2;
    /** Eating beyond this kills. */
    public static final int FATAL    = SATIATED * 3;
    /** One pickup or meal in this many drops a street-food parcel. */
    public static final int SLIP_CHANCE = 10;

    // ── Classification ──────────────────────────────────────────────────────

    /** True when the item is a Rotterdam street-food delicacy. */
    public static boolean isStreetFood(Item it) {
        if (it == null) return false;
        int t = it.getTypeIndex();
        return t >= ItemTable.BARA && t < ItemTable.NROFOBJECTS;
    }

    /** True when the item is a monster corpse (and so subject to rot). */
    public static boolean isCorpse(Item it) {
        if (it == null) return false;
        int t = it.getTypeIndex();
        if (t >= ItemTable.DEAD_HUMAN && t <= ItemTable.LAST_CORPSE) return true;
        String dn = it.getDisplayName();
        return dn != null && dn.startsWith("dead ");
    }

    // ── Corpse decay ────────────────────────────────────────────────────────

    /**
     * How far a corpse has rotted, 0 (fresh) to {@link #ROT_STEPS} (ruined).
     *
     * <p>Corpses lying in a shop do not age: shop stock is kept fresh, so a
     * customer is never sold a rotten corpse.</p>
     *
     * @param state game state, used for the current turn and shop lookup
     * @param it    the corpse
     * @param inShop true when the corpse is shop stock on the shop floor
     */
    public static int rotAge(GameState state, Item it, boolean inShop) {
        if (!isCorpse(it) || inShop) return 0;
        long age = state.getMoves() - it.getAge();
        if (age < 0) age = 0;
        return (int) Math.min(age, ROT_STEPS);
    }

    /** Convenience overload that works out the shop status itself. */
    public static int rotAge(GameState state, Item it) {
        boolean inShop = false;
        if (state.getFloorItems().contains(it)) {
            inShop = ShopEngine.shopRoomAt(state, it.getX(), it.getY()) != null;
        }
        return rotAge(state, it, inShop);
    }

    /** Percentage of maximum health a corpse of this age costs to eat. */
    public static int rotDamagePercent(int age) {
        if (age <= 0) return MIN_ROT_DAMAGE_PCT;
        int span = MAX_ROT_DAMAGE_PCT - MIN_ROT_DAMAGE_PCT;
        return MIN_ROT_DAMAGE_PCT + (int) Math.round(span * (age / (double) ROT_STEPS));
    }

    /** A short word describing how far gone a corpse is, for the menus. */
    public static String rotLabel(int age) {
        if (age <= 0)                    return null;
        if (age < ROT_STEPS / 4)         return "fresh";
        if (age < ROT_STEPS / 2)         return "going off";
        if (age < (ROT_STEPS * 3) / 4)   return "rotting";
        if (age < ROT_STEPS)             return "foul";
        return "rotten";
    }

    // ── Eating ──────────────────────────────────────────────────────────────

    /**
     * Applies the nutrition and consequences of eating one item.
     *
     * @return true when the player died from over-eating
     */
    public static boolean consume(GameState state, Item it) {
        Player p = state.getPlayer();
        ItemClass fc = it.getItemClass();
        int nutrition = (fc != null) ? fc.multiValue : 0;

        // ── Corpse rot ──────────────────────────────────────────────────────
        if (isCorpse(it)) {
            int age = rotAge(state, it, false);   // in inventory: always ages
            int pct = rotDamagePercent(age);
            int dmg = Math.max(1, (p.getHpMax() * pct) / 100);
            if (age >= ROT_STEPS) {
                state.pline("That corpse was completely rotten!");
                nutrition = 0;                     // no goodness left at all
            } else {
                String label = rotLabel(age);
                if (label != null && age >= ROT_STEPS / 4) {
                    state.pline("That " + label + " corpse tasted terrible.");
                }
                // Rotting flesh gives progressively less nourishment
                nutrition = (int) (nutrition * (1.0 - age / (double) ROT_STEPS));
            }
            state.pline("You feel sick. (-" + dmg + " HP)");
            p.setHp(p.getHp() - dmg);
            if (p.getHp() <= 0) {
                p.setHp(0);
                state.pline("The rotten meat was too much for you.");
                state.setPhase(GameState.Phase.DEAD);
                state.setKiller("food poisoning");
                return true;
            }
        }

        // ── Street food is twice as filling ─────────────────────────────────
        if (isStreetFood(it)) nutrition *= 2;

        int newHunger = p.getHunger() + Math.max(0, nutrition);

        // ── Gluttony ────────────────────────────────────────────────────────
        if (newHunger >= FATAL) {
            p.setHunger(FATAL);
            state.pline("You choked on your food and die.");
            p.setHp(0);
            state.setPhase(GameState.Phase.DEAD);
            state.setKiller("Gluttony");
            return true;
        }
        if (newHunger >= STUFFED) {
            state.pline("You are really full already and have difficulties eating.");
        }

        p.setHunger(Math.min(FATAL - 1, newHunger));
        p.setHungerState(HungerState.forHunger(p.getHunger()));
        return false;
    }

    /**
     * Rolls the street-food fumble.
     *
     * <p>Greasy parcels are awkward: one time in ten the item slips and lands
     * on the floor instead of reaching the player's hands or mouth.</p>
     *
     * @return true when the item slipped and should be dropped
     */
    public static boolean slips(GameState state, Item it) {
        if (!isStreetFood(it)) return false;
        if (!Dice.oneIn(SLIP_CHANCE)) return false;
        String name = it.getDisplayName() != null ? it.getDisplayName()
                    : (it.getItemClass() != null ? it.getItemClass().name : "food");
        state.pline("The " + name + " is slippery and falls on the floor.");
        return true;
    }
}
