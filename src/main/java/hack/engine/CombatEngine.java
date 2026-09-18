package hack.engine;

import hack.model.*;
import hack.model.Dice;

/**
 * CombatEngine.java – All combat resolution logic.
 *
 * Ports {@code hack.fight.c} (player attacks monsters),
 * {@code hack.mhitu.c} (monsters attack the player), and
 * related helpers from {@code hack.mon.c}.
 *
 * <p>All methods take a {@link GameState} as their first argument so they
 * can emit messages and update game-wide state without relying on globals.
 */
public class CombatEngine {

    // -----------------------------------------------------------------------
    // Player attacks monster
    // -----------------------------------------------------------------------

    /**
     * Resolves a player attack against the monster at position (nx, ny).
     *
     * Mirrors the {@code attack()} function in {@code hack.fight.c}.
     *
     * @param state  current game state
     * @param target the monster being attacked
     * @return true if a move was consumed (always true for an attack)
     */
    public static boolean playerAttacks(GameState state, Monster target) {
        Player player = state.getPlayer();
        MonsterPrototype mdat = target.getData();

        // Mimic reveals itself
        if (target.isMimic()) {
            target.setMimic(false);
            switch (state.getLevel().cellAt(player.getX() + player.getDx(),
                                            player.getY() + player.getDy()).getScrsym()) {
                case '+': state.pline("The door actually was a Mimic."); break;
                case '$': state.pline("The chest was a Mimic!"); break;
                default:  state.pline("Wait! That's a Mimic!"); break;
            }
            wakeup(target);
            return true;
        }

        wakeup(target);

        // Hidden monster reveals itself
        if (target.isHiding() && target.isUndetected()) {
            target.setUndetected(false);
            Item under = state.itemAt(target.getX(), target.getY());
            if (under != null && !player.isBlind()) {
                state.pline("Wait! There's a " + mdat.name +
                            " hiding under " + under.doname() + "!");
            }
            return true;
        }

        // To-hit calculation (mirrors the C formula exactly)
        int tmp = player.getLuck()
                + player.getLevel()
                + mdat.ac
                + player.attackBonus();

        Item wep = player.getWeapon();
        if (wep != null) {
            if (wep.getSymbol() == ItemTable.WEAPON_SYM || wep.getTypeIndex() == ItemTable.PICK_AXE) {
                tmp += wep.getEnchantment();
            }
            if (wep.getTypeIndex() == ItemTable.TWO_HANDED_SWORD) tmp -= 1;
            else if (wep.getTypeIndex() == ItemTable.DAGGER)        tmp += 2;
            else if (wep.getTypeIndex() == ItemTable.CRYSKNIFE)     tmp += 3;
        }
        if (target.isSleeping())  { target.setSleeping(false); tmp += 2; }
        if (target.isFrozen())    { tmp += 4; if (Dice.oneIn(10)) target.setFrozen(false); }
        if (target.isFleeing())   { tmp += 2; }
        if (player.getTrapTurns() > 0) tmp -= 3;
        tmp -= (player.inventoryWeight() + 40) / 20;

        if (tmp <= Dice.rnd(20) && !player.isSwallowed()) {
            // Miss
            if (player.isBlind())  state.pline("You miss it.");
            else                   state.pline("You miss " + monName(target) + ".");
        } else {
            // Hit
            boolean monAlive = hmon(state, target, wep, false);
            if (!monAlive) return true;

            // Floating eye gaze paralysis
            if (mdat.letter == 'E' && !target.isCancelled() && Dice.rn2(3) == 0) {
                if (target.canSee()) {
                    state.pline("You are frozen by the floating eye's gaze!");
                    GameEngine.nomul(state, Dice.rn1(20, -21));
                } else {
                    state.pline("The blinded floating eye cannot defend itself.");
                }
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Weapon vs. monster damage  (hmon from hack.fight.c)
    // -----------------------------------------------------------------------

    /**
     * Applies weapon damage to a monster and handles death.
     * Mirrors {@code hmon()} in {@code hack.fight.c}.
     *
     * @param state   game state
     * @param mon     monster taking damage
     * @param obj     weapon used (may be null for bare-hands)
     * @param thrown  true if the weapon was thrown
     * @return true if the monster is still alive after the hit
     */
    public static boolean hmon(GameState state, Monster mon, Item obj, boolean thrown) {
        Player player = state.getPlayer();
        MonsterPrototype pd = mon.getData();
        int tmp;

        if (obj == null) {
            // Bare-hands
            tmp = Dice.rnd(2);
            if (pd.letter == 'c' && player.getGloves() == null) {
                state.pline("You hit the cockatrice with your bare hands.");
                state.pline("You turn to stone ...");
                GameEngine.killPlayer(state, "cockatrice");
                return false;
            }
        } else if (obj.getSymbol() == ItemTable.WEAPON_SYM ||
                   obj.getTypeIndex() == ItemTable.PICK_AXE) {
            ItemClass cls = obj.getItemClass();
            // Large vs. small monster distinction (mlarge in C)
            boolean large = "bCDdegIlmnoPSsTUwY',&".indexOf(pd.letter) >= 0;
            if (large) {
                tmp = Dice.rnd(cls.oc1);
                if (obj.getTypeIndex() == ItemTable.TWO_HANDED_SWORD) tmp += Dice.d(2, 6);
                else if (obj.getTypeIndex() == ItemTable.FLAIL)        tmp += Dice.rnd(4);
            } else {
                tmp = Dice.rnd(cls.oc2);
            }
            tmp += obj.getEnchantment();
        } else {
            // Non-weapon objects
            switch (obj.getTypeIndex()) {
                case ItemTable.HEAVY_IRON_BALL: tmp = Dice.rnd(25); break;
                default:
                    tmp = Math.max(1, Math.min(6, Dice.rnd(obj.getWeight() / 10 + 1)));
                    break;
            }
        }

        tmp += player.getDamageBonus() + player.damBonus();
        if (player.isSwallowed()) tmp -= player.getSwallowTime();
        tmp = Math.max(1, tmp);

        if (!player.isBlind() && !thrown) {
            state.pline("You hit " + monName(mon) + exclam(tmp));
        } else if (!thrown) {
            state.pline("You hit it.");
        }

        mon.setHp(mon.getHp() - tmp);
        if (mon.getHp() < 1) {
            MonsterEngine.killMonster(state, mon);
            return false;
        }

        // Confused hands transfer
        if (player.isConfusingHands()) {
            if (!player.isBlind()) {
                state.pline("Your hands stop glowing blue.");
                if (!mon.isFrozen() && !mon.isSleeping())
                    state.pline(capFirst(monName(mon)) + " appears confused.");
            }
            mon.setConfused(true);
            player.setConfusingHands(false);
        }

        // Fleeing after being badly hurt
        if (Dice.rn2(25) == 0 && mon.getHp() < mon.getHpMax() / 2) {
            mon.setFleeing(true);
            if (Dice.oneIn(3)) mon.setFleeTurns(Dice.rnd(100));
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Monster attacks player  (mhitu from hack.mhitu.c)
    // -----------------------------------------------------------------------

    /**
     * Resolves a monster attacking the player.
     * Mirrors {@code mhitu()} from {@code hack.mhitu.c}.
     *
     * @param state  game state
     * @param mtmp   the attacking monster
     * @return true if the monster died during the attack (e.g. yellow light)
     */
    public static boolean monsterHitsPlayer(GameState state, Monster mtmp) {
        Player player = state.getPlayer();
        MonsterPrototype mdat = mtmp.getData();

        // Swallowed case
        if (player.isSwallowed()) {
            if (mtmp != state.getStuckMonster()) {
                if (mdat.letter == 'c' && Dice.rn2(13) == 0) {
                    state.pline("Outside, you hear " + monName(mtmp) + "'s hissing!");
                    state.pline(capFirst(monName(state.getStuckMonster())) + " gets turned to stone!");
                    state.pline("And the same fate befalls you.");
                    GameEngine.killPlayer(state, "cockatrice");
                }
                return false;
            }
        }

        // Eels reveal themselves when they attack
        if (mdat.letter == ';' && mtmp.isInvisible()
                && state.getLevel() != null
                && VisibilityEngine.canSee(state, mtmp.getX(), mtmp.getY())) {
            mtmp.setInvisible(false);
        }

        // Base hit
        int tmp = 0;
        if ("1&DuxynNF".indexOf(mdat.letter) < 0) {
            tmp = hitu(state, mtmp, Dice.d(mdat.attackDice, mdat.attackDie));
        }

        // Undead get extra attack at midnight
        boolean midnight = (state.getMoves() % 1440 < 10);
        if (MonsterPrototype.ZOMBIE.isUndead() && "ZVW ".indexOf(mdat.letter) >= 0 && midnight) {
            tmp += hitu(state, mtmp, Dice.d(mdat.attackDice, mdat.attackDie));
        }

        // Special per-monster effects
        boolean ctmp = tmp > 0 && !mtmp.isCancelled();
        applyMonsterSpecials(state, mtmp, mdat, tmp, ctmp);

        if (player.getHp() < 1) {
            GameEngine.killPlayer(state, mdat.name);
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Base "hitu" roll
    // -----------------------------------------------------------------------

    /**
     * Resolves one monster attack roll against the player.
     * Mirrors {@code hitu()} from {@code hack.mhitu.c}.
     *
     * @param state  game state
     * @param mtmp   attacking monster
     * @param dam    damage to deal if the attack hits
     * @return 1 if hit, 0 if miss
     */
    static int hitu(GameState state, Monster mtmp, int dam) {
        Player player = state.getPlayer();
        if (player.isSwallowed()) return 0;

        int tmp = player.getAc();
        if (tmp < 0) {
            dam = Math.max(1, dam + tmp);
            tmp  = -Dice.rn2(-tmp);
        }
        tmp += mtmp.getData().level;
        if (state.getMulti() < 0) tmp += 4;
        if (player.isInvisible() || !mtmp.canSee()) tmp -= 2;
        if (mtmp.isTrapped()) tmp -= 2;

        if (tmp <= Dice.rnd(20)) {
            if (player.isBlind())  state.pline("It misses.");
            else                   state.pline(capFirst(monName(mtmp)) + " misses.");
            return 0;
        } else {
            if (player.isBlind())  state.pline("It hits!");
            else                   state.pline(capFirst(monName(mtmp)) + " hits!");
            // Reduce monster damage to 25% of the original value (minimum 1)
            int reducedDam = Math.max(1, dam / 4);
            GameEngine.loseHp(state, reducedDam, mtmp.getData().name);
            return 1;
        }
    }

    // -----------------------------------------------------------------------
    // Monster special attack effects
    // -----------------------------------------------------------------------

    /**
     * Applies the letter-specific post-hit effect for a monster.
     * Mirrors the big switch in {@code mhitu()}.
     *
     * @param state  game state
     * @param mtmp   attacking monster
     * @param mdat   monster prototype
     * @param tmp    1 if the base hit landed, 0 otherwise
     * @param ctmp   true if the special effect should apply
     */
    private static void applyMonsterSpecials(GameState state,
            Monster mtmp, MonsterPrototype mdat, int tmp, boolean ctmp) {
        Player player = state.getPlayer();

        switch (mdat.letter) {
            case 'A': // Giant ant — strength drain
                if (ctmp && Dice.rn2(2) == 0) {
                    state.pline("You feel weaker!");
                    player.setStrength(player.getStrength() - 1);
                }
                break;
            case 'c': // Cockatrice — petrification risk
                if (!Dice.oneIn(5)) break;
                state.pline("You hear " + monName(mtmp) + "'s hissing!");
                if (ctmp || Dice.oneIn(20)) {
                    state.pline("You get turned to stone!");
                    GameEngine.killPlayer(state, "cockatrice");
                }
                break;
            case 'D': // Dragon — breath weapon
                if (Dice.rn2(6) == 0 && !mtmp.isCancelled()) {
                    state.pline("The dragon breathes fire!");
                    GameEngine.loseHp(state, Math.max(1, Dice.d(4, 6) / 4), "dragon fire");
                } else {
                    hitu(state, mtmp, Dice.d(3, 10));
                    hitu(state, mtmp, Dice.rnd(8));
                    hitu(state, mtmp, Dice.rnd(8));
                }
                break;
            case 'L': // Leprechaun — steals gold
                if (tmp > 0) MonsterEngine.stealGold(state, mtmp);
                break;
            case 'N': // Nymph — steals item
                if (!mtmp.isCancelled()) MonsterEngine.stealItem(state, mtmp);
                break;
            case 'n': // Nurse — heals if unequipped
                if (player.getWeapon() == null && player.getArmor() == null
                        && player.getHelmet() == null) {
                    state.pline(capFirst(monName(mtmp)) + " hits! (I hope you don't mind)");
                    int heal = Dice.rnd(7);
                    player.setHp(Math.min(player.getHpMax(), player.getHp() + heal));
                    return;
                }
                hitu(state, mtmp, Dice.d(2, 6));
                hitu(state, mtmp, Dice.d(2, 6));
                break;
            case 'R': // Rust monster — corrodes armour
                if (tmp > 0 && player.getHelmet() != null && !player.getHelmet().isRustFree()) {
                    state.pline("Your helmet rusts!");
                    player.getHelmet().setEnchantment(player.getHelmet().getEnchantment() - 1);
                } else if (ctmp && player.getArmor() != null && !player.getArmor().isRustFree()) {
                    state.pline("Your armor rusts!");
                    player.getArmor().setEnchantment(player.getArmor().getEnchantment() - 1);
                }
                break;
            case 'V': // Vampire — drains experience level
                if (tmp > 0) { player.setHp(player.getHp() - 4); }
                if (ctmp) GameEngine.loseExperienceLevel(state);
                break;
            case 'W': // Wraith — drains experience level
                if (ctmp) GameEngine.loseExperienceLevel(state);
                break;
            case 'y': // Yellow light — blinds on explosion
                if (!mtmp.isCancelled()) {
                    state.pline("You are blinded by a blast of light!");
                    player.prop(Player.PROP_BLIND).timeout += Dice.d(4, 12);
                    VisibilityEngine.seeOff(state);
                    MonsterEngine.monsterDies(state, mtmp);
                }
                break;
            default:
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Monster-vs-monster combat  (hitmm from hack.fight.c)
    // -----------------------------------------------------------------------

    /**
     * Resolves a monster-vs-monster attack.
     * Mirrors {@code hitmm()} in {@code hack.fight.c}.
     *
     * @param state  game state
     * @param aggressor attacking monster
     * @param defender  defending monster
     * @return 0=miss, 1=hit, 2=kill
     */
    public static int monsterVsMonster(GameState state,
                                        Monster aggressor, Monster defender) {
        MonsterPrototype pa = aggressor.getData();
        MonsterPrototype pd = defender.getData();

        if ("Eauy".indexOf(pa.letter) >= 0) return 0;
        if (aggressor.isFrozen()) return 0;

        int tmp = pd.ac + pa.level;
        if (defender.isConfused() || defender.isFrozen() || defender.isSleeping()) {
            tmp += 4;
            if (defender.isSleeping()) defender.setSleeping(false);
        }

        boolean hit = tmp > Dice.rnd(20);
        if (hit) defender.setSleeping(false);

        boolean vis = VisibilityEngine.canSee(state, aggressor.getX(), aggressor.getY())
                   && VisibilityEngine.canSee(state, defender.getX(), defender.getY());

        if (vis) {
            state.pline(capFirst(monName(aggressor)) + (hit ? " hits " : " misses ") + monName(defender) + ".");
        }

        if (!hit) return 0;

        // Cockatrice petrification
        if (pa.letter == 'c' && !aggressor.isShapechanger()) {
            if (vis) state.pline(capFirst(monName(defender)) + " is turned to stone!");
            MonsterEngine.monsterDies(state, defender);
            return 2;
        }

        int dmg = Dice.d(pa.attackDice, pa.attackDie);
        defender.setHp(defender.getHp() - dmg);
        if (defender.getHp() < 1) {
            aggressor.setHpMax(aggressor.getHpMax() + 1 + Dice.rn2(pd.level + 1));
            if (vis) state.pline(capFirst(monName(defender)) + " is killed!");
            MonsterEngine.monsterDies(state, defender);
            return 2;
        }
        return 1;
    }

    // -----------------------------------------------------------------------
    // Wake up a monster
    // -----------------------------------------------------------------------

    /**
     * Wakes a sleeping monster. Mirrors {@code wakeup()} in C.
     *
     * @param m monster to wake
     */
    public static void wakeup(Monster m) {
        m.setSleeping(false);
        m.setMimic(false);
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Returns the lowercase name for a monster.
     *
     * @param m monster
     * @return name string
     */
    static String monName(Monster m) {
        if (m.getPetName() != null) return m.getData().name + " named " + m.getPetName();
        return m.getData().name;
    }

    /** Returns "!" with bonus exclamation marks for large damage. */
    private static String exclam(int dmg) {
        if (dmg < 5)  return "!";
        if (dmg < 10) return "!!";
        return "!!!";
    }

    /** Capitalises the first character of a string. */
    static String capFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
