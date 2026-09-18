package hack.engine;

import hack.model.*;
import hack.model.Dice;

/**
 * TrapEngine.java – Trap triggering and teleportation.
 *
 * Ports {@code dotrap()}, {@code tele()}, {@code vtele()},
 * {@code teleds()}, and {@code level_tele()} from {@code hack.trap.c}.
 */
public final class TrapEngine {

    private TrapEngine() {}

    // -----------------------------------------------------------------------
    // Trap activation
    // -----------------------------------------------------------------------

    /**
     * Activates a trap that the player stepped on.
     * Mirrors {@code dotrap()} from {@code hack.trap.c}.
     *
     * @param state game state
     * @param trap  the trap being triggered
     */
    public static void dotrap(GameState state, Trap trap) {
        Player p = state.getPlayer();
        TrapType type = trap.getType();

        // Seen trap: 20% chance to escape
        if (trap.isSeen() && Dice.rn2(5) != 0 && type != TrapType.PIT) {
            state.pline("You escape a" + type.description + ".");
            return;
        }

        trap.setSeen(true);

        switch (type) {
            case SLP_GAS_TRAP:
                state.pline("A cloud of gas puts you to sleep!");
                GameEngine.nomul(state, -Dice.rnd(25));
                break;

            case BEAR_TRAP:
                if (p.isLevitating()) {
                    state.pline("You float over a bear trap.");
                    break;
                }
                p.setTrapTurns(4 + Dice.rn2(4));
                p.setTrapType(0); // TT_BEARTRAP
                state.pline("A bear trap closes on your foot!");
                break;

            case ARROW_TRAP:
                state.pline("An arrow shoots out at you!");
                if (Dice.rnd(20) > p.getAc() + 8) {
                    GameEngine.loseHp(state, Math.max(1, (Dice.rnd(6)) / 4), "arrow");
                } else {
                    state.pline("It narrowly misses you.");
                    // Place the arrow on the floor
                    Item arrow = state.createItem(ItemTable.ARROW);
                    state.placeItem(arrow, p.getX(), p.getY());
                }
                break;

            case DART_TRAP:
                state.pline("A little dart shoots out at you!");
                if (Dice.rnd(20) > p.getAc() + 7) {
                    GameEngine.loseHp(state, Math.max(1, (Dice.rnd(3)) / 4), "little dart");
                    if (Dice.rn2(6) == 0) {
                        state.pline("The dart was poisoned!");
                        if (!p.hasPoisonRes()) {
                            p.setStrength(p.getStrength() - Dice.rnd(3));
                        }
                    }
                }
                break;

            case TRAPDOOR:
                if (Dice.rnd(20) > p.getAc()) {
                    int newLevel = state.getDungeonLevel() + 1;
                    while (Dice.oneIn(4) && newLevel < 29) newLevel++;
                    state.pline("A trap door opens up under you!");
                    if (p.isLevitating() || state.getStuckMonster() != null) {
                        state.pline("For some reason you don't fall in.");
                        break;
                    }
                    GameEngine.gotoLevel(state, newLevel);
                }
                break;

            case TELEP_TRAP:
                if (trap.isOnce()) {
                    state.getLevel().getTraps().remove(trap);
                    vtele(state);
                } else {
                    tele(state);
                }
                break;

            case PIT:
                if (p.isLevitating()) {
                    state.pline("A pit opens up under you!");
                    state.pline("You don't fall in!");
                    break;
                }
                state.pline("You fall into a pit!");
                p.setTrapTurns(Dice.rn1(6, 2));
                p.setTrapType(1); // TT_PIT
                GameEngine.loseHp(state, Math.max(1, (Dice.rnd(6)) / 4), "fall into a pit");
                break;

            case PIERC:
                state.getLevel().getTraps().remove(trap);
                MonsterEngine.makemon(state, MonsterPrototype.PIERCER, p.getX(), p.getY());
                state.pline("A piercer suddenly drops from the ceiling!");
                if (p.getHelmet() != null) {
                    state.pline("Its blow glances off your helmet.");
                } else {
                    GameEngine.loseHp(state, Dice.d(4, 6), "falling piercer");
                }
                break;

            default:
                state.pline("You hit a strange trap.");
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Teleportation
    // -----------------------------------------------------------------------

    /**
     * Teleports the player to a random valid position on the current level.
     * Mirrors {@code tele()} from {@code hack.trap.c}.
     *
     * @param state game state
     */
    public static void tele(GameState state) {
        DungeonLevel level = state.getLevel();
        if (level == null) return;

        int tries = 0;
        int nux, nuy;
        do {
            nux = Dice.rnd(DungeonLevel.COLNO - 1);
            nuy = Dice.rn2(DungeonLevel.ROWNO);
            tries++;
        } while (tries < 400 && !teleok(state, nux, nuy));

        if (tries < 400) {
            teleds(state, nux, nuy);
        }
    }

    /**
     * Teleports the player into a vault room if one exists.
     * Mirrors {@code vtele()} from {@code hack.trap.c}.
     *
     * @param state game state
     */
    static void vtele(GameState state) {
        for (Room r : state.getLevel().getRooms()) {
            if (r.type == RoomType.VAULT) {
                int x = Dice.rn2(2) == 0 ? r.lx : r.hx;
                int y = Dice.rn2(2) == 0 ? r.ly : r.hy;
                if (teleok(state, x, y)) {
                    teleds(state, x, y);
                    return;
                }
            }
        }
        tele(state);
    }

    /**
     * Moves the player to a specific teleport destination.
     * Mirrors {@code teleds()} from {@code hack.trap.c}.
     *
     * @param state game state
     * @param nux   destination column
     * @param nuy   destination row
     */
    static void teleds(GameState state, int nux, int nuy) {
        Player p = state.getPlayer();
        VisibilityEngine.unSee(state);
        p.setTrapTurns(0);
        state.setStuckMonster(null);

        MonsterEngine.restoreCell(state, p.getX(), p.getY());
        p.setPos(nux, nuy);
        state.getLevel().cellAt(nux, nuy).setScrsym('@');

        VisibilityEngine.setSee(state);
        if (p.isSwallowed()) {
            p.setSwallowed(false);
            p.setSwallowTime(0);
        }
        GameEngine.nomul(state, 0);
        GameEngine.doPickup(state);
    }

    /**
     * Returns true if (x, y) is a valid teleport destination.
     * Mirrors {@code teleok()} from {@code hack.trap.c}.
     *
     * @param state game state
     * @param x     column
     * @param y     row
     * @return true if the player can be teleported here
     */
    static boolean teleok(GameState state, int x, int y) {
        if (!DungeonLevel.isOk(x, y)) return false;
        DungeonLevel level = state.getLevel();
        Cell c = level.cellAt(x, y);
        // Block teleport into empty void or structural walls
        if (c.getType() == CellType.EMPTY) return false;
        if (c.isRock()) return false;
        if (state.monsterAt(x, y) != null) return false;
        if (state.specificItemAt(ItemTable.ENORMOUS_ROCK, x, y) != null) return false;
        if (level.trapAt(x, y) != null) return false;
        return true;
    }
}
