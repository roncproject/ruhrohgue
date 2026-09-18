package hack.engine;

import hack.model.*;
import hack.model.Dice;
import java.util.*;

/**
 * GameEngine.java – The central game loop and all player-action dispatch.
 *
 * Ports {@code domove()} and {@code pickup()} from {@code hack.c},
 * {@code nomul()} / {@code losehp()} / {@code losexp()} from {@code hack.c},
 * {@code find_ac()} / {@code losestr()} from {@code hack.worn.c},
 * and the main per-turn driver from {@code hack.main.c}.
 *
 * <p>Every public method receives a {@link GameState} as its first argument
 * and mutates it to reflect the result of the action.
 */
public final class GameEngine {

    // -----------------------------------------------------------------------
    // Direction table  (mirrors sdir / xdir / ydir from hack.cmd.c)
    // -----------------------------------------------------------------------

    /** Roguelike direction keys in order. */
    public static final String SDIR = "hykulnjb><";
    /** X delta for each direction. */
    public static final int[] XDIR = {-1,-1, 0, 1, 1, 1, 0,-1, 0, 0};
    /** Y delta for each direction. */
    public static final int[] YDIR = { 0,-1,-1,-1, 0, 1, 1, 1, 0, 0};
    /** Z delta (up=+1, down=-1). */
    public static final int[] ZDIR = { 0, 0, 0, 0, 0, 0, 0, 0, 1,-1};

    private GameEngine() {}

    // -----------------------------------------------------------------------
    // Main per-turn driver
    // -----------------------------------------------------------------------

    /**
     * Processes one player command and advances the game state by one turn.
     *
     * <p>This is called by the HTTP server on each POST /command request.
     * The command string may be a single character ('h', 'j', …) or a
     * named action ("pickup", "drop", "eat", etc.).
     *
     * @param state   current game state
     * @param command the player's command
     */
    public static void doCommand(GameState state, String command) {
        if (state.getPhase() != GameState.Phase.PLAYING) return;

        Player player = state.getPlayer();
        boolean moveTaken = false;

        // Handle multi-turn countdown
        if (state.getMulti() < 0) {
            state.setMulti(state.getMulti() + 1);
            if (state.getMulti() == 0) {
                String msg = state.getPendingMessage();
                state.pline(msg != null ? msg : "You can move again.");
                state.setPendingMessage(null);
            }
            // Monsters still move even while player is immobile
            MonsterEngine.movemon(state);
            perTurnEffects(state);
            return;
        }

        if (command == null || command.isEmpty()) return;

        // ----------------------------------------------------------------
        // Direction commands
        // ----------------------------------------------------------------
        char ch = command.charAt(0);
        int dirIdx = SDIR.indexOf(ch);
        if (dirIdx >= 0) {
            player.setDir(XDIR[dirIdx], YDIR[dirIdx], ZDIR[dirIdx]);
            if (ZDIR[dirIdx] != 0) {
                // Stairs
                moveTaken = doStairs(state);
            } else {
                moveTaken = doMove(state);
            }
        } else {
            // Named commands
            switch (command) {
                // pickup now handled in "," case above
                case "eat":   case "e":  moveTaken = doEat(state);                break;
                case "search": case "s": moveTaken = doSearch(state);             break;
                case "wait":   case ".": moveTaken = true;                        break;
                case "o":  case "O": moveTaken = doOpenDoor(state);                break;
                case "c":            moveTaken = doCloseDoor(state);               break;
                case "i":  case "I": moveTaken = doShowInventory(state);           break;
                case "P":            moveTaken = doPutRingMenu(state);             break;
                case "R":            moveTaken = doRemoveRingMenu(state);          break;
                case "d":            moveTaken = doDropMenu(state);                break;
                case ",":  case "pickup": moveTaken = doPickupEnhanced(state);     break;
                case "ring_nothing":
                    state.pline("Sadly, you have no ring."); return;
                case "noringworn":
                    state.pline("Sadly, you are not wearing a ring."); return;
                case "vault_no_credit":
                    state.pline("Not enough Credit."); return;
                case ":":
                    moveTaken = doLookHere(state); break;
                case "p":
                    moveTaken = ShopEngine.doShowShopTab(state); break;

                // ── Confirm new game ────────────────────────────
                case "confirm_new":
                    state.pline("Do you want to start a new game (yN)?");
                    return;
                case "quit_confirmed":
                    doQuit(state); return;
                case "confirm_quit":
                    state.pline("Do you want to quit the game (yN)?");
                    return;

                // ── Wield / Wear / Takeoff with chooser ─────────
                case "wield_nothing":
                    state.pline("Sadly, you have nothing to wield.");
                    return;
                case "wear_nothing":
                    state.pline("Sadly, you have nothing to wear.");
                    return;
                case "takeoff_nothing":
                    state.pline("Sadly, you are naked.");
                    return;
                case "eat_nothing":
                    state.pline("Sadly, you have nothing to eat.");
                    return;

                // ── Wield:slot ───────────────────────────────────
                default:
                    if (command.startsWith("wield:")) {
                        moveTaken = doWield(state, command.substring(6)); break;
                    }
                    if (command.startsWith("wear:")) {
                        moveTaken = doWear(state, command.substring(5)); break;
                    }
                    if (command.startsWith("takeoff:")) {
                        moveTaken = doTakeOff(state, command.substring(8)); break;
                    }
                    if (command.startsWith("eat:")) {
                        moveTaken = doEatBySlot(state, command.substring(4)); break;
                    }
                    if (command.startsWith("throw:")) {
                        moveTaken = doThrow(state, command.substring(6)); break;
                    }
                    if (command.startsWith("payshop:")) {
                        moveTaken = ShopEngine.doPayShop(state, command.substring(8)); break;
                    }
                    if (command.startsWith("throwgold:")) {
                        moveTaken = doThrowGold(state, command.substring(10)); break;
                    }
                    if (command.equals("throw_menu")) {
                        state.pline("[throw_menu]"); return;
                    }
                    if (command.startsWith("putring:")) {
                        moveTaken = doPutRing(state, command.substring(8)); break;
                    }
                    if (command.startsWith("removering:")) {
                        moveTaken = doRemoveRing(state, command.substring(11)); break;
                    }
                    if (command.startsWith("drop:")) {
                        moveTaken = doDropItem(state, command.substring(5)); break;
                    }
                    if (command.startsWith("dropgold:")) {
                        moveTaken = doDropGold(state, command.substring(9)); break;
                    }
                    if (command.startsWith("pickupvault:")) {
                        moveTaken = doPickupVaultGold(state, command.substring(12)); break;
                    }
                    state.pline("Unknown command '" + command + "'.");
                    return;
            }
        }

        if (!moveTaken) return;

        // ----------------------------------------------------------------
        // Per-turn world update
        // ----------------------------------------------------------------
        MonsterEngine.movemon(state);
        perTurnEffects(state);
        // Keep the tab honest and keep the shopkeeper on station: it holds the
        // doorway while money is owed and steps back inside once it is paid.
        ShopEngine.reconcileTab(state);
        ShopEngine.updateShopkeeper(state);
        state.incrementMoves();
        VisibilityEngine.setSee(state);
    }

    // -----------------------------------------------------------------------
    // Per-turn passive effects
    // -----------------------------------------------------------------------

    /**
     * Applies special timed effects (leprechaun jumps, floating eye vision).
     */
    public static void applyTimedEffects(GameState state) {
        // ── Leprechaun jump effect ──────────────────────────────────
        if (state.getLeprechaunJumpsLeft() > 0) {
            int countdown = state.getLeprechaunJumpCountdown() - 1;
            state.setLeprechaunJumpCountdown(countdown);
            if (countdown <= 0) {
                // Teleport the player to a random floor cell
                DungeonLevel level = state.getLevel();
                Player p = state.getPlayer();
                if (level != null) {
                    java.util.List<int[]> cells = new java.util.ArrayList<>();
                    for (int x = 0; x < DungeonLevel.COLNO; x++)
                        for (int y = 0; y < DungeonLevel.ROWNO; y++)
                            if (level.cellAt(x,y).getType() == CellType.ROOM ||
                                level.cellAt(x,y).getType() == CellType.CORR)
                                cells.add(new int[]{x, y});
                    if (!cells.isEmpty()) {
                        int[] dest = cells.get(Dice.rn2(cells.size()));
                        MonsterEngine.restoreCell(state, p.getX(), p.getY());
                        p.setPos(dest[0], dest[1]);
                        level.cellAt(dest[0], dest[1]).setScrsym('@');
                        VisibilityEngine.setSee(state);
                        state.pline("The leprechaun magic jerks you sideways!");
                    }
                }
                int jumpsLeft = state.getLeprechaunJumpsLeft() - 1;
                state.setLeprechaunJumpsLeft(jumpsLeft);
                if (jumpsLeft > 0) {
                    state.setLeprechaunJumpCountdown(3 + Dice.rn2(5));
                }
            }
        }

        // ── Floating eye vision effect ──────────────────────────────
        if (state.getFloatingEyeVisionLeft() > 0) {
            state.setFloatingEyeVisionLeft(state.getFloatingEyeVisionLeft() - 1);
            if (state.getFloatingEyeVisionLeft() == 0) {
                state.pline("The floating eye vision fades.");
            }
        }

        // ── Bank interest ───────────────────────────────────────────────
        // Compounds every 100 turns as before, but the amount earned is now
        // tracked since the last deposit, withdrawal or Bank payment, and
        // reported to the player every 1000 steps.
        long moves = state.getMoves();
        if (moves > 0 && moves % 100 == 0) {
            long bankBal = state.getPlayer().getBank();
            if (bankBal > 0) {
                long interest = Math.max(1L, bankBal / 100L);
                state.getPlayer().setBank(bankBal + interest);
                state.addAccruedInterest(interest);
            }
        }
        if (moves > 0 && moves % 1000 == 0 && moves != state.getLastInterestReport()) {
            state.setLastInterestReport(moves);
            long earned = state.getAccruedInterest();
            if (earned > 0) {
                state.pline("You earned " + earned + " interest on your Bank account.");
            }
        }
    }

    /**
     * Applies all passive per-turn effects: HP regeneration, hunger,
     * property timeouts, etc.
     *
     * Mirrors the effects loop inside the main {@code for(;;)} in
     * {@code hack.main.c}.
     *
     * @param state game state
     */
    static void perTurnEffects(GameState state) {
        Player p = state.getPlayer();
        long moves = state.getMoves();

        // HP regeneration
        if (p.getHp() < p.getHpMax()) {
            if (p.getLevel() > 9) {
                if (p.hasRegeneration() || (moves % 3 == 0)) {
                    p.setHp(Math.min(p.getHpMax(),
                                     p.getHp() + Dice.rnd(p.getLevel() - 9)));
                }
            } else if (p.hasRegeneration() || (moves % (22 - p.getLevel() * 2) == 0)) {
                p.setHp(Math.min(p.getHpMax(), p.getHp() + 1));
            }
        }

        // Hunger
        doHunger(state);

        // Property timeouts
        p.tickProperties();

        // Banshee wail at low HP
        if (p.getHp() * 10 < p.getHpMax() && moves % 50 == 0) {
            state.pline(p.getHp() == 1
                ? "You hear the wailing of the Banshee..."
                : "You hear the howling of the CwnAnnwn...");
        }

        // Random teleport
        if (p.hasTeleportation() && Dice.rn2(85) == 0) {
            TrapEngine.tele(state);
        }

        // Auto-search
        if (p.hasSearching()) doSearch(state);

        // Timed effects (leprechaun jumps, floating eye vision)
        applyTimedEffects(state);

        // Dog companion movement
        moveDogs(state);

        // Death check
        if (p.getHp() < 1) {
            state.pline("You die...");
            killPlayer(state, state.getKiller().isEmpty() ? "exhaustion" : state.getKiller());
        }
    }

    // -----------------------------------------------------------------------
    // Movement
    // -----------------------------------------------------------------------

    /**
     * Attempts to move the player in the current direction.
     * Mirrors {@code domove()} from {@code hack.c}.
     *
     * @param state game state
     * @return true if a turn was consumed
     */
    static boolean doMove(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();

        if (level == null) return false;

        // Check carry weight
        if (inventoryWeight(state) > 0) {
            state.pline("You collapse under your load.");
            nomul(state, 0);
            return false;
        }

        int nx = p.getX() + p.getDx();
        int ny = p.getY() + p.getDy();

        if (!DungeonLevel.isOk(nx, ny)) { nomul(state, 0); return false; }

        // Confused player moves randomly
        if (p.isConfused()) {
            do {
                int ri = Dice.rn2(8);
                p.setDir(XDIR[ri], YDIR[ri], 0);
                nx = p.getX() + p.getDx();
                ny = p.getY() + p.getDy();
            } while (!DungeonLevel.isOk(nx, ny) || level.cellAt(nx, ny).isRock());
        }

        // Stuck in a monster
        if (state.getStuckMonster() != null && !p.isSwallowed()) {
            Monster stuck = state.getStuckMonster();
            if (nx != stuck.getX() || ny != stuck.getY()) {
                int distSq = new Coord(stuck.getX(), stuck.getY()).dist2(p.getX(), p.getY());
                if (distSq > 2) {
                    state.setStuckMonster(null);  // it fled
                } else {
                    if (p.isBlind()) state.pline("You cannot escape from it!");
                    else state.pline("You cannot escape from " + stuck.getData().name + "!");
                    nomul(state, 0);
                    return false;
                }
            }
        }

        // ── Dog displacement — MUST run before the monster-attack check ─────
        {
            Monster dogAtDest = state.monsterAt(nx, ny);
            if (dogAtDest != null && dogAtDest.getData() == MonsterPrototype.LITTLE_DOG) {
                boolean displaced = false;
                int playerFromX = p.getX(), playerFromY = p.getY();
                boolean inCorridor = (level.cellAt(nx, ny).getType() == CellType.CORR
                                   || level.cellAt(p.getX(), p.getY()).getType() == CellType.CORR);

                if (inCorridor) {
                    if (DungeonLevel.isOk(playerFromX, playerFromY)
                            && level.cellAt(playerFromX, playerFromY).getType() != CellType.EMPTY
                            && !level.cellAt(playerFromX, playerFromY).isRock()
                            && state.monsterAt(playerFromX, playerFromY) == null) {
                        MonsterEngine.restoreCell(state, nx, ny);
                        dogAtDest.setPos(playerFromX, playerFromY);
                        level.cellAt(playerFromX, playerFromY)
                            .setScrsym(MonsterPrototype.LITTLE_DOG.letter);
                        displaced = true;
                    }
                }

                if (!displaced) {
                    int[][] dogDirs = {
                        {-p.getDx(), -p.getDy()},
                        {0,1},{1,0},{0,-1},{-1,0},
                        {1,1},{1,-1},{-1,1},{-1,-1}
                    };
                    for (int[] dd : dogDirs) {
                        int dnx = nx + dd[0], dny = ny + dd[1];
                        if (!DungeonLevel.isOk(dnx, dny)) continue;
                        Cell dc = level.cellAt(dnx, dny);
                        if (dc.getType() == CellType.EMPTY || dc.isRock()) continue;
                        if (state.monsterAt(dnx, dny) != null) continue;
                        if (dnx == nx && dny == ny) continue;
                        MonsterEngine.restoreCell(state, nx, ny);
                        dogAtDest.setPos(dnx, dny);
                        level.cellAt(dnx, dny).setScrsym(MonsterPrototype.LITTLE_DOG.letter);
                        displaced = true;
                        break;
                    }
                }

                if (displaced) {
                    if (Dice.oneIn(25)) state.pline("You displaced your little dog.");
                } else {
                    MonsterEngine.restoreCell(state, nx, ny);
                    dogAtDest.setPos(p.getX(), p.getY());
                    level.cellAt(p.getX(), p.getY())
                        .setScrsym(MonsterPrototype.LITTLE_DOG.letter);
                }
            }
        }

        // ── Bank manager and shopkeeper displacement ─────────────────────────
        // Both step aside to let the player pass. The single exception is a
        // shopkeeper with an open tab: it holds the doorway until the bill is
        // settled. It still never attacks — it simply will not move.
        {
            Monster mgr = state.monsterAt(nx, ny);
            boolean isKeeper = mgr != null && mgr.getData() == MonsterPrototype.SHOPKEEPER;
            boolean isManager = mgr != null && mgr.getData() == MonsterPrototype.VAULTGUARD;

            if (isKeeper && state.getPlayer().hasUnpaidItems()) {
                state.pline("The shopkeeper blocks your way: \"Please pay first, Sir.\"");
                state.pline("Press 'p' to open the Shop Tab.");
                nomul(state, 0);
                return false;
            }

            if (isKeeper || isManager) {
                char letter = mgr.getData().letter;
                boolean displaced = false;
                int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
                for (int[] dd : dirs) {
                    int dnx = nx+dd[0], dny = ny+dd[1];
                    if (!DungeonLevel.isOk(dnx,dny)) continue;
                    Cell dc = level.cellAt(dnx,dny);
                    if (dc.getType()!=CellType.ROOM && dc.getType()!=CellType.CORR) continue;
                    if (state.monsterAt(dnx,dny)!=null) continue;
                    if (dnx==p.getX()&&dny==p.getY()) continue;
                    // Keep staff inside (or on the threshold of) their own room
                    int[] hr = mgr.getHomeRoom();
                    if (mgr.isConfined()
                            && (dnx < hr[0]-1 || dnx > hr[2]+1
                             || dny < hr[1]-1 || dny > hr[3]+1)) continue;
                    MonsterEngine.restoreCell(state, nx, ny);
                    mgr.setPos(dnx, dny);
                    level.cellAt(dnx,dny).setScrsym(letter);
                    displaced = true;
                    break;
                }
                if (!displaced) {
                    state.pline(isKeeper
                        ? "The shopkeeper cannot step aside just now."
                        : "The bank manager is blocking the entrance.");
                    nomul(state, 0);
                    return false;
                }
            }
        }

        // Monster at destination → attack
        Monster mtmp = state.monsterAt(nx, ny);
        if (mtmp != null || p.isSwallowed()) {
            nomul(state, 0);
            doHunger(state);
            if (state.getMulti() < 0) return false;
            return CombatEngine.playerAttacks(state, p.isSwallowed() ? state.getStuckMonster() : mtmp);
        }

        // Still trapped
        if (p.getTrapTurns() > 0) {
            if (p.getTrapType() == 1) { // TT_PIT
                state.pline("You are still in a pit.");
                p.setTrapTurns(p.getTrapTurns() - 1);
            } else {
                state.pline("You are caught in a beartrap.");
                if ((p.getDx() != 0 && p.getDy() != 0) || Dice.oneIn(5))
                    p.setTrapTurns(p.getTrapTurns() - 1);
            }
            return false;
        }

        // Check destination cell
        Cell destCell = level.cellAt(nx, ny);

        if (destCell.getType() == CellType.EMPTY
                || destCell.isRock()
                || (p.getDx() != 0 && p.getDy() != 0
                    && (destCell.getType() == CellType.DOOR
                        || level.cellAt(p.getX(), p.getY()).getType() == CellType.DOOR))) {
            nomul(state, 0);
            return false;
        }

        // Falling into pool
        if (destCell.getType() == CellType.POOL && !p.isLevitating()) {
            state.pline("You fall into a pool!");
            state.pline("You can't swim!");
            state.pline("You drown...");
            killPlayer(state, "pool of water");
            return true;
        }

        // Actual move
        MonsterEngine.restoreCell(state, p.getX(), p.getY());
        p.setPos(nx, ny);
        level.cellAt(nx, ny).setSeen(true);

        if (destCell.getType() == CellType.DOOR) {
            if (destCell.getScrsym() == '+') {
                destCell.setScrsym(' ');
                destCell.markDirty();
                            }
            announceSpecialRoom(state, nx, ny);
        }
        if (destCell.getType() == CellType.ROOM) {
            announceSpecialRoom(state, nx, ny);
        }

        // Stop running at stairs or doors
        if (destCell.getType() == CellType.DOOR || destCell.getType() == CellType.STAIRS) {
            nomul(state, 0);
        }

        // Visibility update
        if (!p.isBlind()) VisibilityEngine.setSee(state);

        // Trap check
        Trap trap = level.trapAt(nx, ny);
        if (trap != null) TrapEngine.dotrap(state, trap);

        // Report what is lying on the tile the player just stepped onto.
        // Runs last so the trap message (if any) is not pushed off the top.
        autoDescribeFloor(state);

        return true;
    }

    private static void announceSpecialRoom(GameState state, int nx, int ny) {
        DungeonLevel level = state.getLevel();
        for (hack.model.Room r : level.getRooms()) {
            if (r.type != hack.model.RoomType.VAULT
                    && r.type != hack.model.RoomType.SHOPBASE
                    && r.type != hack.model.RoomType.GENERAL) continue;
            if (nx < r.lx || nx > r.hx || ny < r.ly || ny > r.hy) continue;

            // Only announce once per level visit
            String key = r.lx + "," + r.ly + "@" + state.getDungeonLevel();
            if (state.getAnnouncedRooms().contains(key)) return;
            state.getAnnouncedRooms().add(key);

            int dlevel = state.getDungeonLevel();
            if (r.type == hack.model.RoomType.SHOPBASE ||
                r.type == hack.model.RoomType.GENERAL) {
                state.pline("Welcome to the shop of floor " + dlevel + "!");
            } else if (r.type == hack.model.RoomType.VAULT) {
                state.pline("Bank of level " + dlevel + ", NO TRESPASSERS!");
            }
            return;
        }
    }

    // -----------------------------------------------------------------------
    // Stairs
    // -----------------------------------------------------------------------

    static boolean doStairs(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        int dz = p.getDz();

        if (dz > 0) { // '>' pressed — descend to next level
            if (p.getX() != level.getXDnStair() || p.getY() != level.getYDnStair()) {
                state.pline("You can't go down here.");
                return false;
            }
            if (state.getStuckMonster() != null) {
                state.pline("You are being held, and cannot go down.");
                return true;
            }
            if (p.isLevitating()) {
                state.pline("You're floating high above the stairs.");
                return false;
            }
            gotoLevel(state, state.getDungeonLevel() + 1);
        } else { // '<' pressed — ascend toward surface
            if (p.getX() != level.getXUpStair() || p.getY() != level.getYUpStair()) {
                state.pline("You can't go up here.");
                return false;
            }
            if (state.getStuckMonster() != null) {
                state.pline("You are being held, and cannot go up.");
                return true;
            }
            if (inventoryWeight(state) + 5 > 0) {
                state.pline("Your load is too heavy to climb the stairs.");
                return true;
            }
            if (state.getDungeonLevel() <= 1) {
                state.pline("You escaped from the dungeon!");
                state.setPhase(GameState.Phase.ESCAPED);
                return true;
            }
            gotoLevel(state, state.getDungeonLevel() - 1);
        }
        return true;
    }

    public static void gotoLevel(GameState state, int newLevel) {
        int prevLevel = state.getDungeonLevel();
        boolean goingDown = newLevel > prevLevel;

        // Save the CURRENT level to memory before leaving
        if (state.getLevel() != null) {
            state.getLevelMemory().put(prevLevel, state.getLevel());
        }

        // Check if the little dog is adjacent to the player — if so, it follows
        Player curP = state.getPlayer();
        Monster followingDog = null;
        for (Monster m : state.getLiveMonsters()) {
            if (m.getData() != MonsterPrototype.LITTLE_DOG) continue;
            int dist = Math.abs(m.getX() - curP.getX()) + Math.abs(m.getY() - curP.getY());
            if (dist <= 1) { followingDog = m; break; }
        }

        // Clear live monsters
        state.getMonsters().clear();
        state.getFloorItems().clear();

        state.setDungeonLevel(newLevel);
        state.clearAnnouncedRooms();

        // Restore a previously visited level, or generate a new one
        DungeonLevel savedLevel = state.getLevelMemory().get(newLevel);
        if (savedLevel != null) {
            state.setLevel(savedLevel);
            // Re-populate a remembered floor with fresh wanderers.
            for (hack.model.Room room : savedLevel.getRooms()) {
                if (room.type == hack.model.RoomType.VAULT || room.type.isShop()) continue;
                if (Dice.oneIn(3)) {
                    MonsterEngine.makemon(state, null, room.randomX(), room.randomY());
                }
            }
        } else {
            // makeLevel() now spawns the floor's starting monsters itself,
            // inside the deterministic per-floor RNG window.
            LevelGenerator.makeLevel(state, newLevel);
        }

        DungeonLevel lvl = state.getLevel();
        Player p = state.getPlayer();
        if (goingDown) {
            p.setPos(lvl.getXUpStair(), lvl.getYUpStair());
        } else {
            p.setPos(lvl.getXDnStair(), lvl.getYDnStair());
        }

        // gotoLevel() clears every live monster before swapping in a remembered
        // level, and only random wanderers were re-spawned — so the shopkeeper
        // and the bank manager vanished whenever the player revisited a floor.
        // Put them back on duty.
        ShopEngine.restoreStaff(state);

        // A tab belongs to the shop that opened it. Leaving the floor closes it,
        // so an old tab can never re-appear in a different shop.
        state.getPlayer().getUnpaidItems().clear();

        // Respawn the dog near the player on the new level if it followed
        if (followingDog != null) {
            LevelGenerator.spawnDogNearPlayer(state);
            state.pline("Your little dog follows you.");
        }

        VisibilityEngine.setSee(state);
        state.resetDescribed();
        state.pline("You arrive at dungeon level " + newLevel + ".");
        autoDescribeFloor(state);
    }

    // -----------------------------------------------------------------------
    // Pickup
    // -----------------------------------------------------------------------

    public static boolean doPickup(GameState state) {
        Player p = state.getPlayer();
        if (p.isLevitating()) return false;

        DungeonLevel level = state.getLevel();
        boolean pickedUp = false;

        // Gold first
        GoldPile gold = level.goldAt(p.getX(), p.getY());
        if (gold != null) {
            long amt = gold.getAmount();
            if (amt < 0) {
                int typeIdx = (int)(-amt);
                if (typeIdx >= 0 && typeIdx < ItemTable.NROFOBJECTS) {
                    Item foodItem = (Item) state.createItem(typeIdx);
                    if (foodItem != null) {
                        level.getGold().remove(gold);
                        MonsterEngine.restoreCell(state, p.getX(), p.getY());
                        foodItem.setLastOwner(p.getName());
                        p.getInventory().add(foodItem);
                        if (!p.isBlind()) foodItem.setDescriptionKnown(true);
                        if (isPlayerInShop(state)) {
                            foodItem.setUnpaid(true);
                            ShopEngine.addToTab(state, foodItem);
                            state.pline(foodItem.doname() + " ["
                                + ShopEngine.priceOf(foodItem) + " gold, unpaid].");
                        } else {
                            state.pline(foodItem.doname() + ".");
                            pickedUp = true;
                            triggerGuardIfNeeded(state, p.getX(), p.getY());
                        }
                    }
                }
            } else {
                state.pline(amt + " gold piece" + (amt == 1 ? "" : "s") + ".");
                p.setGold(p.getGold() + amt);
                level.getGold().remove(gold);
                MonsterEngine.restoreCell(state, p.getX(), p.getY());
                pickedUp = true;
                triggerGuardIfNeeded(state, p.getX(), p.getY());
            }
        }

        // Items
        List<Item> toPickup = new ArrayList<>();
        for (Item it : state.getFloorItems()) {
            if (it.getX() == p.getX() && it.getY() == p.getY()) {
                toPickup.add(it);
            }
        }
        for (Item it : toPickup) {
            if (p.getInventory().size() >= 52) {
                state.pline("Your knapsack cannot accommodate anymore items.");
                break;
            }
            if (inventoryWeight(state) + it.getWeight() > 0 && !p.getInventory().isEmpty()) {
                state.pline("There is " + it.doname() + " here, but you cannot carry anymore.");
                continue;
            }
            if (FoodEngine.slips(state, it)) { pickedUp = true; continue; }
            state.removeFromFloor(it);
            it.setLastOwner(p.getName());
            p.getInventory().add(it);
            if (!p.isBlind()) it.setDescriptionKnown(true);
            if (isPlayerInShop(state)) {
                // Taking goods in a shop opens a tab instead of stealing them.
                it.setUnpaid(true);
                ShopEngine.addToTab(state, it);
                state.pline(it.doname() + " [" + ShopEngine.priceOf(it) + " gold, unpaid].");
            } else {
                state.pline(it.doname() + ".");
                pickedUp = true;
            }
        }
        if (pickedUp) triggerGuardIfNeeded(state, p.getX(), p.getY());
        return pickedUp;
    }

    /**
     * Wakes the Bank guard when gold is taken from the vault.
     *
     * <p>Shopkeepers are deliberately excluded: taking goods from a shop opens
     * a tab rather than provoking an attack. The shopkeeper enforces payment
     * by standing in the doorway (see {@link ShopEngine#updateShopkeeper}),
     * never by fighting.</p>
     */
    private static void triggerGuardIfNeeded(GameState state, int x, int y) {
        for (hack.model.Monster m : state.getLiveMonsters()) {
            if (!m.isConfined()) continue;
            if (m.getData() == MonsterPrototype.SHOPKEEPER) continue; // never hostile
            int[] hr = m.getHomeRoom();
            if (x >= hr[0] && x <= hr[2] && y >= hr[1] && y <= hr[3]) {
                if (m.isPeaceful()) {
                    m.setPeaceful(false);
                    state.pline("The bank manager shouts: Thief!");
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Eating
    // -----------------------------------------------------------------------

    static boolean doEat(GameState state) {
        Player p = state.getPlayer();
        List<Item> inv = p.getInventory();
        Item food = null;
        for (Item it : inv) {
            if (it.getSymbol() == ItemTable.FOOD_SYM) { food = it; break; }
        }
        if (food == null) {
            state.pline("Sadly, you have nothing to eat.");
            return false;
        }
        return doEatBySlot(state, String.valueOf((char)('a' + inv.indexOf(food))));
    }

    // -----------------------------------------------------------------------
    // Searching
    // -----------------------------------------------------------------------

    static boolean doSearch(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null) return true;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = p.getX() + dx, ny = p.getY() + dy;
                if (!DungeonLevel.isOk(nx, ny)) continue;
                Cell c = level.cellAt(nx, ny);
                if (c.getType() == CellType.SDOOR && Dice.rn2(7) == 0) {
                    c.setType(CellType.DOOR); c.setDoorOpen(true);
                    c.setScrsym('+');
                    c.setSeen(true);
                    state.pline("You find a secret door.");
                }
                if (c.getType() == CellType.SCORR && Dice.rn2(7) == 0) {
                    c.setType(CellType.CORR);
                    c.setScrsym('#');
                    c.setSeen(true);
                    state.pline("You find a secret corridor.");
                }
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Little dog companion
    // -----------------------------------------------------------------------

    public static void spawnLittleDog(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null) return;
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d : dirs) {
            int nx = p.getX() + d[0], ny = p.getY() + d[1];
            if (!DungeonLevel.isOk(nx, ny)) continue;
            CellType t = level.cellAt(nx, ny).getType();
            if (t == CellType.ROOM || t == CellType.CORR) {
                if (state.monsterAt(nx, ny) == null) {
                    Monster dog = state.createMonster(MonsterPrototype.LITTLE_DOG, nx, ny);
                    if (dog != null) {
                        dog.setPeaceful(true);
                        dog.setSleeping(false);
                    }
                    return;
                }
            }
        }
    }

    public static void moveDogs(GameState state) {
        Player p = state.getPlayer();
        DungeonLevel level = state.getLevel();
        if (level == null) return;
        int px = p.getX(), py = p.getY();

        for (Monster m : new java.util.ArrayList<>(state.getLiveMonsters())) {
            if (m.getData() != MonsterPrototype.LITTLE_DOG) continue;
            int mx = m.getX(), my = m.getY();
            int dist = Math.abs(mx - px) + Math.abs(my - py);

            if (dist <= 5) {
                boolean inRoom = level.cellAt(mx, my).getType() == CellType.ROOM;
                boolean wander = inRoom && dist <= 3 && Dice.oneIn(3);

                int bestX = mx, bestY = my;
                int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

                if (wander && dist > 1) {
                    java.util.List<int[]> candidates = new java.util.ArrayList<>();
                    for (int[] d : dirs) {
                        int nx = mx+d[0], ny = my+d[1];
                        if (!DungeonLevel.isOk(nx,ny)) continue;
                        if (level.cellAt(nx,ny).getType() != CellType.ROOM) continue;
                        if (state.monsterAt(nx,ny) != null) continue;
                        if (nx==px && ny==py) continue;
                        int wanderDist = Math.abs(nx-px)+Math.abs(ny-py);
                        if (wanderDist >= 2 && wanderDist <= 5) candidates.add(new int[]{nx,ny});
                    }
                    if (!candidates.isEmpty()) {
                        int[] pick = candidates.get(Dice.rn2(candidates.size()));
                        bestX = pick[0]; bestY = pick[1];
                    }
                } else if (!wander && dist > 1) {
                    int bestDist = dist;
                    for (int[] d : dirs) {
                        int nx = mx+d[0], ny = my+d[1];
                        if (!DungeonLevel.isOk(nx,ny)) continue;
                        if (level.cellAt(nx,ny).getType()==CellType.EMPTY) continue;
                        if (level.cellAt(nx,ny).isRock()) continue;
                        if (state.monsterAt(nx,ny)!=null) continue;
                        if (nx==px && ny==py) continue;
                        int d2 = Math.abs(nx-px)+Math.abs(ny-py);
                        if (d2 < bestDist) { bestDist=d2; bestX=nx; bestY=ny; }
                    }
                }
                if (bestX != mx || bestY != my) {
                    MonsterEngine.restoreCell(state, mx, my);
                    m.setPos(bestX, bestY);
                    level.cellAt(bestX, bestY).setScrsym(MonsterPrototype.LITTLE_DOG.letter);
                }
            } else {
                int[][] dirs2 = {{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] d : dirs2) {
                    int nx=px+d[0], ny=py+d[1];
                    if (!DungeonLevel.isOk(nx,ny)) continue;
                    if (level.cellAt(nx,ny).getType()==CellType.EMPTY) continue;
                    if (level.cellAt(nx,ny).isRock()) continue;
                    if (state.monsterAt(nx,ny)!=null) continue;
                    MonsterEngine.restoreCell(state, mx, my);
                    m.setPos(nx,ny);
                    level.cellAt(nx,ny).setScrsym(MonsterPrototype.LITTLE_DOG.letter);
                    break;
                }
            }

            if (dist <= 2) {
                for (Monster enemy : new java.util.ArrayList<>(state.getLiveMonsters())) {
                    if (enemy.getData() == MonsterPrototype.LITTLE_DOG) continue;
                    boolean isShopOrGuard = (enemy.getData() == MonsterPrototype.SHOPKEEPER
                                          || enemy.getData() == MonsterPrototype.VAULTGUARD);
                    if (isShopOrGuard && enemy.isPeaceful()) continue;
                    int ex = enemy.getX(), ey = enemy.getY();
                    int toPlayer = Math.abs(ex-px)+Math.abs(ey-py);
                    if (toPlayer > 2) continue;
                    int playerDmg = p.getLevel() + Dice.rnd(4);
                    int dogAtk    = Math.max(1, playerDmg / 4);
                    boolean killed = enemy.takeDamage(dogAtk);
                    if (VisibilityEngine.canSeeMonster(state, enemy)) {
                        state.pline("The little dog attacks the " + enemy.getData().name + "!");
                    }
                    if (killed) MonsterEngine.monsterDies(state, enemy);
                    break;
                }
            }

            {
                int dmx = m.getX(), dmy = m.getY();
                for (Item fi : new java.util.ArrayList<>(state.getFloorItems())) {
                    if (fi.getX() != dmx || fi.getY() != dmy) continue;
                    if (fi.getSymbol() != ItemTable.FOOD_SYM) continue;
                    String owner = fi.getLastOwner();
                    if (owner == null || !owner.equals(p.getName())) continue;
                    state.removeFromFloor(fi);
                    if (VisibilityEngine.canSeeMonster(state, m)) {
                        state.pline("The little dog eats the " + fi.doname() + ".");
                    }
                    break;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Hunger
    // -----------------------------------------------------------------------

    static void doHunger(GameState state) {
        Player p = state.getPlayer();
        long moves = state.getMoves();

        p.setHunger(p.getHunger() - 1);
        if ((p.hasRegeneration() || p.hasHungerRing()) && moves % 2 == 0)
            p.setHunger(p.getHunger() - 1);

        HungerState newHs = HungerState.forHunger(p.getHunger());
        if (newHs != p.getHungerState()) {
            switch (newHs) {
                case HUNGRY:    state.pline("You are beginning to feel hungry."); break;
                case WEAK:      state.pline("You are beginning to feel weak.");   break;
                case FAINTING:
                    if (p.getHungerState().ordinalIndex > HungerState.FAINTING.ordinalIndex) {
                        state.pline("You faint from lack of food.");
                        nomul(state, -10 + p.getHunger() / 10);
                        state.setPendingMessage("You regain consciousness.");
                    }
                    break;
                case STARVED:
                    state.pline("You die from starvation.");
                    killPlayer(state, "starvation");
                    return;
                default: break;
            }
        }
        p.setHungerState(newHs);
    }

    // -----------------------------------------------------------------------
    // Quit
    // -----------------------------------------------------------------------

    static void doQuit(GameState state) {
        state.pline("You quit the game.");
        state.setPhase(GameState.Phase.QUIT);
    }

    // -----------------------------------------------------------------------
    // nomul
    // -----------------------------------------------------------------------

    public static void nomul(GameState state, int nval) {
        if (state.getMulti() < 0) return;
        state.setMulti(nval);
    }

    // -----------------------------------------------------------------------
    // Damage and death
    // -----------------------------------------------------------------------

    public static void loseHp(GameState state, int n, String killer) {
        Player p = state.getPlayer();
        p.setHp(p.getHp() - n);
        if (p.getHp() > p.getHpMax()) p.setHpMax(p.getHp());
        if (p.getHp() < 1) {
            state.setKiller(killer);
            killPlayer(state, killer);
        }
    }

    public static void loseHpByMonster(GameState state, int n, Monster mtmp) {
        loseHp(state, n, mtmp.getData().name);
    }

    public static void loseExperienceLevel(GameState state) {
        Player p = state.getPlayer();
        if (p.getLevel() > 1) {
            state.pline("Goodbye level " + p.getLevel() + ".");
            p.setLevel(p.getLevel() - 1);
        } else {
            p.setHp(-1);
        }
        int num = Dice.rnd(10);
        p.setHp(p.getHp() - num);
        p.setHpMax(p.getHpMax() - num);
        p.setExperience((long)(10 * Dice.pow2(p.getLevel() - 1)));
    }

    public static void killPlayer(GameState state, String killer) {
        Player p = state.getPlayer();
        p.setHp(0);
        state.setKiller(killer);
        state.setPhase(GameState.Phase.DEAD);

        long score = p.getRoleExperience()
                   + Math.max(0, p.getGold() - p.getGoldAtLevelStart())
                   + 50L * state.getMaxDungeonLevel();
        if (state.getMaxDungeonLevel() > 20) {
            score += 1000L * Math.min(10, state.getMaxDungeonLevel() - 20);
        }
        state.setFinalScore(score);

        state.pline("You die...");
        state.pline("Killed by " + killer + ".");
        state.pline("Final score: " + score);
    }

    // -----------------------------------------------------------------------
    // Armour class
    // -----------------------------------------------------------------------

    public static void findAc(GameState state) {
        Player p = state.getPlayer();
        int ac = 10;
        if (p.getArmor()  != null) ac -= p.getArmor().getItemClass().oc1  + p.getArmor().getEnchantment();
        if (p.getArmor2() != null) ac -= 1;
        if (p.getHelmet() != null) ac -= 1 + p.getHelmet().getEnchantment();
        if (p.getShield() != null) ac -= 1 + p.getShield().getEnchantment();
        if (p.getGloves() != null) ac -= 1 + p.getGloves().getEnchantment();
        if (p.getRingLeft()  != null && p.getRingLeft().getItemClass().oc1  > 0)
            ac -= p.getRingLeft().getEnchantment();
        if (p.getRingRight() != null && p.getRingRight().getItemClass().oc1 > 0)
            ac -= p.getRingRight().getEnchantment();
        p.setAc(ac);
    }

    // -----------------------------------------------------------------------
    // Wield by slot
    // -----------------------------------------------------------------------

    static boolean doWield(GameState state, String slot) {
        Player p = state.getPlayer();
        Item it = findBySlot(p, slot);
        if (it == null) { state.pline("Nothing to wield."); return false; }
        p.setWeapon(it);
        state.pline("You are now wielding a " + it.getItemClass().name + ".");
        return true;
    }

    // -----------------------------------------------------------------------
    // Wear by slot
    // -----------------------------------------------------------------------

    static boolean doWear(GameState state, String slot) {
        Player p = state.getPlayer();
        Item it = findBySlot(p, slot);
        if (it == null) { state.pline("Nothing to wear."); return false; }
        if (it.getSymbol() == '[' || it.getSymbol() == '(') {
            if (p.getArmor() == null) p.setArmor(it);
            else if (p.getHelmet() == null) p.setHelmet(it);
            else if (p.getShield() == null) p.setShield(it);
            else if (p.getGloves() == null) p.setGloves(it);
            else { state.pline("You are already wearing too much."); return false; }
        }
        findAc(state);
        state.pline("You are now wearing a " + it.getItemClass().name + ".");
        return true;
    }

    // -----------------------------------------------------------------------
    // Take off by slot
    // -----------------------------------------------------------------------

    static boolean doTakeOff(GameState state, String slot) {
        Player p = state.getPlayer();
        Item it = findBySlot(p, slot);
        if (it == null) { state.pline("You are not wearing that."); return false; }
        if (it.equals(p.getArmor()))  p.setArmor(null);
        else if (it.equals(p.getArmor2()))  p.setArmor2(null);
        else if (it.equals(p.getHelmet())) p.setHelmet(null);
        else if (it.equals(p.getShield())) p.setShield(null);
        else if (it.equals(p.getGloves())) p.setGloves(null);
        else { state.pline("You can't take that off."); return false; }
        findAc(state);
        state.pline("You have taken off a " + it.getItemClass().name + ".");
        return true;
    }

    // -----------------------------------------------------------------------
    // Eat by slot
    // -----------------------------------------------------------------------

    static boolean doEatBySlot(GameState state, String slot) {
        Player p = state.getPlayer();
        Item it = findBySlot(p, slot);
        if (it == null || it.getSymbol() != ItemTable.FOOD_SYM) {
            state.pline("That is not edible."); return false;
        }
        String foodName = it.getDisplayName() != null ? it.getDisplayName()
                        : it.getItemClass().name;

        // Greasy street food can slip out of your hands on the way to your mouth.
        if (FoodEngine.slips(state, it)) {
            p.getInventory().remove(it);
            state.placeItem(it, p.getX(), p.getY());
            return true;
        }

        p.getInventory().remove(it);
        // Eating an unpaid item does NOT cancel the debt — you consumed the
        // shopkeeper's stock, so it stays on the tab as a consumed charge.
        if (it.isUnpaid()) {
            ShopEngine.chargeConsumed(state, it);
        }
        state.pline("You eat the " + foodName + ".");
        ItemClass fc = it.getItemClass();
        if (FoodEngine.consume(state, it)) return true;   // died from over-eating

        String dn = it.getDisplayName();
        if (dn != null && dn.contains("little dog")) {
            state.pline("You get a terrible feeling and your HP drops to 1!");
            p.setHp(1);
            return true;
        }

        if (dn != null) {
            if (dn.contains("leprechaun")) {
                int jumps = 5 + Dice.rn2(6); int pause = 3 + Dice.rn2(5);
                state.pline("The leprechaun essence makes you feel dizzy...");
                state.setLeprechaunJumpsLeft(jumps);
                state.setLeprechaunJumpPause(pause);
                state.setLeprechaunJumpCountdown(pause);
            } else if (dn.contains("floating eye")) {
                // Floating eye vision: 10-50 steps, cumulative
                int dur = 10 + Dice.rn2(41); // 10..50 per corpse
                int newTotal = Math.min(
                    state.getFloatingEyeVisionLeft() + dur,
                    hack.model.GameState.FLOATING_EYE_MAX_STEPS);
                state.setFloatingEyeVisionLeft(newTotal);
                state.pline("You see the dungeon through the floating eye's perception! ("
                    + newTotal + " steps remaining).");
            }
        }
        nomul(state, -fc.useDelay);
        return true;
    }

    // -----------------------------------------------------------------------
    // Throw item in last movement direction
    // -----------------------------------------------------------------------

    static boolean doThrow(GameState state, String dirArg) {
        Player p = state.getPlayer();
        String[] parts = dirArg.split(":");
        if (parts.length < 3) { state.pline("No throw direction."); return false; }
        String slot = parts[0];
        int tdx, tdy;
        try { tdx=Integer.parseInt(parts[1]); tdy=Integer.parseInt(parts[2]); }
        catch (NumberFormatException ex) { return false; }
        if (tdx==0 && tdy==0) { state.pline("Move first to set a throw direction."); return false; }

        Item thrown = findBySlot(p, slot);
        if (thrown == null) { state.pline("You don't have that."); return false; }
        p.getInventory().remove(thrown);
        thrown.setLastOwner(p.getName());
        return executeThrow(state, thrown.doname(), thrown, null, p.getX(), p.getY(), tdx, tdy);
    }

    static boolean doThrowGold(GameState state, String args) {
        Player p = state.getPlayer();
        String[] parts = args.split(":");
        if (parts.length < 3) return false;
        long amt; int tdx, tdy;
        try { amt=Long.parseLong(parts[0]); tdx=Integer.parseInt(parts[1]); tdy=Integer.parseInt(parts[2]); }
        catch (NumberFormatException ex) { return false; }
        if (amt<=0 || amt>p.getGold()) { state.pline("You don't have that much gold."); return false; }
        p.setGold(p.getGold()-amt);
        return executeThrow(state, amt+" gold piece"+(amt==1?"":"s"), null,
                            new GoldPile(p.getX(), p.getY(), amt), p.getX(), p.getY(), tdx, tdy);
    }

    private static boolean executeThrow(GameState state, String label, Item itemObj,
                                        GoldPile goldObj, int cx, int cy, int tdx, int tdy) {
        DungeonLevel level = state.getLevel();
        int range = 2 + Dice.rn2(4);
        int landX = cx, landY = cy;
        for (int step = 1; step <= range; step++) {
            int nx = cx + tdx*step, ny = cy + tdy*step;
            if (!DungeonLevel.isOk(nx,ny)) break;
            Cell c = level.cellAt(nx,ny);
            if (c.getType()==CellType.EMPTY || c.isRock()) break;
            if (c.getType()==CellType.DOOR && !c.isDoorOpen()) break;
            Monster target = state.monsterAt(nx,ny);
            if (target != null) {
                int dmg = Dice.rnd(4) + (itemObj!=null ? 1 : 0);
                target.takeDamage(dmg);
                if (VisibilityEngine.canSeeMonster(state,target))
                    state.pline("The "+label+" hits "+target.getData().name+"!");
                if (itemObj!=null) { itemObj.setX(nx); itemObj.setY(ny); state.placeItem(itemObj,nx,ny); }
                else if (goldObj!=null) { level.getGold().add(new GoldPile(nx, ny, goldObj.getAmount())); level.cellAt(nx,ny).setScrsym('$'); }
                state.pline("You throw the "+label+" away.");
                return true;
            }
            landX=nx; landY=ny;
        }
        if (itemObj!=null) { state.placeItem(itemObj,landX,landY); }
        else if (goldObj!=null) { level.getGold().add(new GoldPile(landX, landY, goldObj.getAmount())); level.cellAt(landX,landY).setScrsym('$'); }
        state.pline("You throw the "+label+" away.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Find inventory item by slot letter
    // -----------------------------------------------------------------------

    private static Item findBySlot(Player p, String slot) {
        if (slot == null || slot.isEmpty()) return null;
        char slotChar = slot.charAt(0);
        List<Item> inv = p.getInventory();
        int idx = slotChar - 'a';
        if (idx >= 0 && idx < inv.size()) return inv.get(idx);
        return null;
    }

    // -----------------------------------------------------------------------
    // Open / Close door
    // -----------------------------------------------------------------------

    static boolean doOpenDoor(GameState state) {
        Player p = state.getPlayer(); DungeonLevel level = state.getLevel();
        if (level == null) return false;
        for (int[] d : new int[][]{{0,-1},{0,1},{-1,0},{1,0}}) {
            int nx=p.getX()+d[0], ny=p.getY()+d[1];
            if (!DungeonLevel.isOk(nx,ny)) continue;
            Cell c = level.cellAt(nx,ny);
            if (c.getType()==CellType.DOOR && !c.isDoorOpen()) {
                c.setDoorOpen(true);
                return true;
            }
        }
        state.pline("There is no closed door adjacent to you."); return true;
    }

    static boolean doCloseDoor(GameState state) {
        Player p = state.getPlayer(); DungeonLevel level = state.getLevel();
        if (level == null) return false;
        for (int[] d : new int[][]{{0,-1},{0,1},{-1,0},{1,0}}) {
            int nx=p.getX()+d[0], ny=p.getY()+d[1];
            if (!DungeonLevel.isOk(nx,ny)) continue;
            Cell c = level.cellAt(nx,ny);
            if (c.getType()==CellType.DOOR && c.isDoorOpen()) {
                if (state.monsterAt(nx,ny)!=null) { state.pline("There is a monster in the doorway!"); return true; }
                c.setDoorOpen(false);
                state.pline("You close the door."); return true;
            }
        }
        state.pline("There is no open door adjacent to you."); return true;
    }

    static boolean doShowInventory(GameState state) { return false; }

    // -----------------------------------------------------------------------
    // Ring: Put on (P) / Remove (R)
    // -----------------------------------------------------------------------

    static boolean doPutRingMenu(GameState state) {
        Player p = state.getPlayer();
        boolean hasRing = p.getInventory().stream().anyMatch(it->it.getSymbol()==ItemTable.RING_SYM);
        if (!hasRing) { state.pline("Sadly, you have no ring."); return false; }
        state.pline("[Choose a ring to put on]"); return false;
    }

    static boolean doPutRing(GameState state, String slot) {
        Player p = state.getPlayer();
        Item ring = findBySlot(p, slot);
        if (ring==null||ring.getSymbol()!=ItemTable.RING_SYM) { state.pline("That is not a ring."); return false; }
        if (p.getRingLeft()==null) { p.setRingLeft(ring); state.pline("You put on the "+ring.getItemClass().name+" (left hand)."); }
        else if (p.getRingRight()==null) { p.setRingRight(ring); state.pline("You put on the "+ring.getItemClass().name+" (right hand)."); }
        else { state.pline("You are already wearing two rings."); return false; }
        findAc(state); return true;
    }

    static boolean doRemoveRingMenu(GameState state) {
        Player p = state.getPlayer();
        if (p.getRingLeft()==null && p.getRingRight()==null) { state.pline("Sadly, you are not wearing a ring."); return false; }
        state.pline("[Choose which ring to remove]"); return false;
    }

    static boolean doRemoveRing(GameState state, String slot) {
        Player p = state.getPlayer();
        Item ring = findBySlot(p, slot);
        if (ring==null) { state.pline("You are not wearing that."); return false; }
        if (ring.equals(p.getRingLeft()))  p.setRingLeft(null);
        else if (ring.equals(p.getRingRight())) p.setRingRight(null);
        else { state.pline("You are not wearing that ring."); return false; }
        state.pline("You remove the "+ring.getItemClass().name+"."); findAc(state); return true;
    }

    // -----------------------------------------------------------------------
    // Drop (d) and Vault-gold pickup
    // -----------------------------------------------------------------------

    static boolean doDropMenu(GameState state) { state.pline("[What do you want to drop?]"); return false; }

    static boolean doDropItem(GameState state, String slot) {
        Player p = state.getPlayer();
        Item it = findBySlot(p, slot);
        if (it==null) { state.pline("You don't have that."); return false; }
        p.getInventory().remove(it);
        state.placeItem(it, p.getX(), p.getY());
        state.pline("You drop the "+it.doname()+"."); return true;
    }

    static boolean doDropGold(GameState state, String amtStr) {
        Player p = state.getPlayer(); DungeonLevel level = state.getLevel();
        long amt; try { amt=Long.parseLong(amtStr.trim()); } catch(NumberFormatException e){ return false; }
        if (amt<=0||amt>p.getGold()) { state.pline("You don't have that much gold."); return false; }
        p.setGold(p.getGold()-amt);
        level.getGold().add(new GoldPile(p.getX(),p.getY(),amt));
        level.cellAt(p.getX(),p.getY()).setScrsym('$');
        if (isPlayerInVault(state)) {
            p.setBank(p.getBank()+amt);
            state.resetAccruedInterest();   // interest is counted since this deposit
            state.pline("Thank you dear customer.");
        }
        else state.pline("You drop "+amt+" gold piece"+(amt==1?"":"s")+".");
        return true;
    }

    public static boolean doPickupEnhanced(GameState state) {
        Player p = state.getPlayer(); DungeonLevel level = state.getLevel();
        if (level==null) return false;
        GoldPile gold = level.goldAt(p.getX(),p.getY());
        boolean hasFloorItems = state.getFloorItems().stream()
            .anyMatch(it->it.getX()==p.getX()&&it.getY()==p.getY());
        if (gold==null && !hasFloorItems) { state.pline("There is nothing here."); return true; }
        if (isPlayerInVault(state) && gold!=null && gold.getAmount()>0) {
            state.pline("[bank:pickup:"+gold.getAmount()+":"+p.getBank()+"]"); return false;
        }
        boolean r = doPickup(state);
        // After taking something, report whatever is still lying here (if
        // anything). Nothing is printed when the tile is now empty.
        state.resetDescribed();
        autoDescribeFloor(state);
        return r;
    }

    static boolean doPickupVaultGold(GameState state, String amtStr) {
        Player p = state.getPlayer(); DungeonLevel level = state.getLevel();
        long amt; try { amt=Long.parseLong(amtStr.trim()); } catch(NumberFormatException e){ return false; }
        GoldPile gold = level.goldAt(p.getX(),p.getY());
        if (gold==null) { state.pline("No gold here."); return false; }
        long maxAmt = Math.min(gold.getAmount(), p.getBank());
        if (amt<=0||amt>maxAmt) { state.pline("Invalid amount."); return false; }
        p.setGold(p.getGold()+amt); p.setBank(p.getBank()-amt);
        state.resetAccruedInterest();       // interest is counted since this withdrawal
        gold.setAmount(gold.getAmount()-amt);
        if (gold.getAmount()==0) { level.getGold().remove(gold); MonsterEngine.restoreCell(state,p.getX(),p.getY()); }
        state.pline("You pick up "+amt+" gold piece"+(amt==1?"":"s")+" from the Bank."); return true;
    }

    // -----------------------------------------------------------------------
    // Shop: detect if player is in a shop room
    // -----------------------------------------------------------------------

    public static boolean isPlayerInShop(GameState state) {
        return ShopEngine.isPlayerInShop(state);
    }

    // -----------------------------------------------------------------------
    // Shop: show tab of unpaid items ("p" command)
    // -----------------------------------------------------------------------

    // Shop tab and payment now live in ShopEngine.

    // -----------------------------------------------------------------------
    // Look here (':') — describe contents of current floor tile
    // -----------------------------------------------------------------------

    /**
     * Collects everything lying on the tile at {@code (x,y)}: floor items,
     * gold, shop-food markers and a discovered trap.
     *
     * @return the descriptions, newest-first ordering irrelevant; never null
     */
    static java.util.List<String> tileContents(GameState state, int x, int y) {
        java.util.List<String> found = new java.util.ArrayList<>();
        DungeonLevel level = state.getLevel();
        for (Item it : state.getFloorItems())
            if (it.getX() == x && it.getY() == y) found.add(it.doname());
        if (level == null) return found;
        GoldPile gp = level.goldAt(x, y);
        if (gp != null) {
            if (gp.getAmount() > 0) {
                found.add(gp.getAmount() + " gold piece" + (gp.getAmount() == 1 ? "" : "s"));
            } else {
                // Negative amount = shop food marker: show the food name
                int typeIdx = (int)(-gp.getAmount());
                if (typeIdx >= 0 && typeIdx < ItemTable.NROFOBJECTS) {
                    ItemClass fc = ItemTable.get(typeIdx);
                    if (fc != null) found.add(fc.name);
                }
            }
        }
        hack.model.Trap trap = level.trapAt(x, y);
        // TrapType.description is a suffix designed for "a" + desc, e.g.
        // "n arrow trap" -> "an arrow trap". Prefixing "a " produced "a n arrow trap".
        if (trap != null) found.add(trap.getType().withArticle());
        return found;
    }

    /**
     * Auto-describes floor-tile contents when the player arrives on a cell.
     *
     * <p>Emits one ordinary message line — no {@code [autolook:]} control
     * string, no overlay, no prefix decoration — so the player is informed
     * purely as a matter of record.  Nothing is printed for an empty tile,
     * and the line is not repeated while the player stays on the same
     * tile.</p>
     */
    static void autoDescribeFloor(GameState state) {
        Player p = state.getPlayer();
        if (state.getLevel() == null) return;
        int x = p.getX(), y = p.getY();
        java.util.List<String> found = tileContents(state, x, y);
        if (found.isEmpty()) {
            // Leaving an item behind and stepping back should re-announce it.
            state.resetDescribed();
            return;
        }
        if (!state.markDescribed(x, y)) return;   // already announced this stay
        state.pline("You see here: " + String.join(", ", found) + ".");
    }

    /**
     * The ':' (look here) command — opens the floor-tile overlay.
     *
     * <p>The result travels as a one-shot UI signal (see
     * {@link GameState#consumeSignal()}), so the overlay fires exactly once
     * instead of re-opening on every following turn.</p>
     */
    static boolean doLookHere(GameState state) {
        Player p = state.getPlayer();
        java.util.List<String> found = tileContents(state, p.getX(), p.getY());
        if (found.isEmpty()) state.pline("There is nothing here.");
        else state.pline("[look:" + String.join("|", found) + "]");
        return false;
    }

    public static boolean isPlayerInVault(GameState state) {
        Player p = state.getPlayer(); DungeonLevel level = state.getLevel();
        if (level==null) return false;
        for (hack.model.Room r : level.getRooms()) {
            if (r.type!=hack.model.RoomType.VAULT) continue;
            if (p.getX()>=r.lx&&p.getX()<=r.hx&&p.getY()>=r.ly&&p.getY()<=r.hy) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Inventory weight

    public static int inventoryWeight(GameState state) {
        Player p = state.getPlayer();
        int wt = (int)((p.getGold() + 500) / 1000);
        for (Item it : p.getInventory()) {
            wt += it.getWeight() * it.getQuantity();
        }
        int str = Math.min(18, p.getStrength());
        int carrcap = Math.min(120, 5 * (str + p.getLevel()));
        if (p.hasWoundedLegs()) carrcap -= 20;
        return wt - carrcap;
    }
}
