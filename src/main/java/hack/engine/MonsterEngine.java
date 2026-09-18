package hack.engine;

import hack.model.*;
import hack.model.Dice;
import java.util.*;

/**
 * MonsterEngine.java – Monster movement, AI, death, and spawning.
 *
 * Ports {@code hack.mon.c} (movemon, dochug, m_move, killed, mondead)
 * and {@code hack.makemon.c} (makemon).
 */
public class MonsterEngine {

    // -----------------------------------------------------------------------
    // Monster spawning
    // -----------------------------------------------------------------------

    /**
     * Attempts to create a monster of the given prototype near (x, y).
     * Mirrors {@code makemon()} in {@code hack.makemon.c}.
     *
     * @param state  game state
     * @param proto  species to create (null = random)
     * @param x      preferred column
     * @param y      preferred row
     * @return the created monster, or null if placement failed
     */
    public static Monster makemon(GameState state, MonsterPrototype proto,
                                   int x, int y) {
        if (proto == null) {
            proto = randomMonster(state.getDungeonLevel());
        }

        // Find a free position near the given coordinate
        Coord pos = findFree(state, x, y);
        if (pos == null) return null;

        Monster m = state.createMonster(proto, pos.x, pos.y);
        if (m == null) return null;

        // Sleeping probability based on dlevel distance from player
        m.setSleeping(Dice.rn2(2) == 0);
        return m;
    }

    /**
     * Returns a random monster prototype appropriate for the given
     * dungeon level. Mirrors the level-based selection in {@code makemon()}.
     *
     * @param dlevel dungeon depth
     * @return randomly chosen prototype
     */
    static MonsterPrototype randomMonster(int dlevel) {
        int maxIdx = Math.min(MonsterPrototype.CMNUM + 1,
                              dlevel + 8 + Dice.rn2(6));
        if (maxIdx < 1) maxIdx = 1;

        // Cap difficulty by depth. The index range alone did not bound monster
        // strength, so a level-3 piercer hitting for 2d6 — or a fog cloud —
        // could appear on floor 1 against a level-1 character with 28 HP.
        // Allow monsters up to the floor number plus one, with an occasional
        // one-level surprise, and never a shopkeeper or bank manager.
        int cap = dlevel + 1 + (Dice.oneIn(8) ? 1 : 0);
        for (int attempt = 0; attempt < 40; attempt++) {
            MonsterPrototype candidate = MonsterPrototype.ALL[Dice.rn2(maxIdx)];
            if (candidate == MonsterPrototype.SHOPKEEPER
                    || candidate == MonsterPrototype.VAULTGUARD) continue;
            if (candidate.level <= cap) return candidate;
        }
        // Nothing weak enough turned up — fall back to the weakest monster
        // in range rather than handing the player something overwhelming.
        MonsterPrototype weakest = null;
        for (int i = 0; i < maxIdx; i++) {
            MonsterPrototype c = MonsterPrototype.ALL[i];
            if (c == MonsterPrototype.SHOPKEEPER || c == MonsterPrototype.VAULTGUARD) continue;
            if (weakest == null || c.level < weakest.level) weakest = c;
        }
        return (weakest != null) ? weakest : MonsterPrototype.ALL[0];
    }

    /**
     * Finds an accessible, unoccupied cell adjacent to (x, y).
     *
     * @param state game state
     * @param x     preferred column
     * @param y     preferred row
     * @return free coordinate or null
     */
    static Coord findFree(GameState state, int x, int y) {
        DungeonLevel level = state.getLevel();
        if (level == null) return null;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = x + dx, ny = y + dy;
                if (!DungeonLevel.isOk(nx, ny)) continue;
                if (!level.cellAt(nx, ny).getType().isAccessible()) continue;
                if (state.monsterAt(nx, ny) != null) continue;
                if (nx == state.getPlayer().getX() && ny == state.getPlayer().getY()) continue;
                return new Coord(nx, ny);
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Monster movement AI
    // -----------------------------------------------------------------------

    /**
     * Moves all monsters on the current level.
     * Mirrors {@code movemon()} in {@code hack.mon.c}.
     *
     * @param state game state
     */
    public static void movemon(GameState state) {
        long moves = state.getMoves();
        // Collect IDs to iterate safely (monsters may die during movement)
        List<Integer> ids = new ArrayList<>(state.getMonsters().keySet());

        for (int id : ids) {
            Monster m = state.getMonsters().get(id);
            if (m == null) continue;
            if (m.getLastMoveTurn() >= moves) continue;
            m.setLastMoveTurn(moves);

            tickMonster(state, m, moves);
        }
    }

    /**
     * Handles per-turn updates for one monster and possibly moves it.
     *
     * @param state  game state
     * @param m      monster to update
     * @param moves  current turn count
     */
    private static void tickMonster(GameState state, Monster m, long moves) {
        DungeonLevel level = state.getLevel();
        MonsterPrototype mdat = m.getData();

        // The shopkeeper is never an aggressor. It is repositioned once per
        // turn by ShopEngine.updateShopkeeper() — holding the doorway while a
        // tab is open, stepping back inside once it is settled — so it takes
        // no part in the ordinary monster AI and never attacks the player.
        if (mdat == MonsterPrototype.SHOPKEEPER) {
            m.setPeaceful(true);
            return;
        }
        boolean inPool = level.cellAt(m.getX(), m.getY()).getType() == CellType.POOL;
        boolean isEel  = mdat.letter == ';';

        // Non-eel monsters drown in pools
        if (inPool && !isEel) {
            if (VisibilityEngine.canSee(state, m.getX(), m.getY())) {
                state.pline(CombatEngine.capFirst(mdat.name) + " drowns.");
            }
            monsterDies(state, m);
            return;
        }
        // Eels die out of water
        if (isEel && !inPool) {
            m.setHp(m.getHp() - 1);
            m.setFleeing(true);
            m.setFleeTurns(m.getFleeTurns() + 2);
        }

        // Tick timers
        if (m.getBlinderTurns() > 0) {
            m.setBlinderTurns(m.getBlinderTurns() - 1);
            if (m.getBlinderTurns() == 0) m.setCanSee(true);
        }
        if (m.getFleeTurns() > 0) {
            m.setFleeTurns(m.getFleeTurns() - 1);
            if (m.getFleeTurns() == 0) m.setFleeing(false);
        }
        if (m.isMimic()) return;

        // Regenerate HP
        if ((moves % 20 == 0 || mdat.regenerates()) && m.getHp() < m.getHpMax()) {
            m.setHp(m.getHp() + 1);
        }

        if (m.isFrozen()) return;

        // Wake up logic
        if (m.isSleeping()) {
            boolean aggravate = state.getPlayer().hasAggravateMonster();
            boolean nearPlayer = m.getX() != 0 &&
                new Coord(m.getX(), m.getY()).dist2(state.getPlayer().getX(),
                                                     state.getPlayer().getY()) < 36;
            if (aggravate || nearPlayer || Dice.oneIn(7)) {
                m.setSleeping(false);
            } else {
                return;
            }
        }

        // Un-confuse occasionally
        if (m.isConfused() && Dice.oneIn(50)) m.setConfused(false);

        // Normal speed monsters: move every turn; slow monsters: every 2 turns
        boolean slow = m.getSpeedMod() == 1;
        if (!slow || moves % 2 == 0) {
            boolean died = dochug(state, m);
            if (died) return;
        }
        // Fast monsters get an extra move
        if (m.getSpeedMod() == 2) dochug(state, m);
    }

    /**
     * Handles one monster's action for one turn.
     * Mirrors {@code dochug()} in {@code hack.mon.c}.
     *
     * @param state game state
     * @param m     monster to act
     * @return true if the monster died
     */
    static boolean dochug(GameState state, Monster m) {
        Player player = state.getPlayer();
        int distSq = new Coord(m.getX(), m.getY())
                         .dist2(player.getX(), player.getY());

        // ── Check if little dog is adjacent — monsters attack it ────────────
        {
            Monster dog = findAdjacentDog(state, m.getX(), m.getY());
            if (dog != null && !m.isPeaceful()) {
                int fullDmg = Dice.rn2(Math.max(1, m.getData().level + 1)) + 1;
                int dogDmg  = Math.max(1, fullDmg / 10);   // 1/10 of monster damage
                boolean wasBelowHalf = dog.getHp() <= dog.getHpMax() / 2;
                boolean dogDied = dog.takeDamage(dogDmg);
                if (VisibilityEngine.canSee(state, dog.getX(), dog.getY())) {
                    state.pline(CombatEngine.capFirst(m.getData().name) + " attacks the little dog!");
                    if (!dogDied && !wasBelowHalf && dog.getHp() <= dog.getHpMax() / 2) {
                        state.pline("The little dog yelps.");
                    }
                }
                if (dogDied) {
                    // Dog death: player loses 1/4 HP
                    Player playerRef = state.getPlayer();
                    int hpLoss = Math.max(1, playerRef.getHp() / 4);
                    playerRef.setHp(playerRef.getHp() - hpLoss);
                    state.pline("You feel terrible.");
                    if (VisibilityEngine.canSee(state, dog.getX(), dog.getY())) {
                        state.pline("The little dog is killed!");
                    }
                    monsterDies(state, dog);
                }
            }
        }

        // Adjacent to player and not peaceful: attack
        if (distSq <= 2 && !m.isPeaceful() && player.getHp() > 0) {
            // Check Elbereth engraving / scare scroll at player's position
            if (hasScareMechanism(state)) {
                m.setFleeing(true);
                m.setFleeTurns(10);
            } else {
                boolean monDied = CombatEngine.monsterHitsPlayer(state, m);
                return monDied;
            }
        }

        // Movement
        return moveMonster(state, m) == 2;
    }

    /**
     * Checks whether a scare mechanism (Elbereth / scroll) is present at
     * the player's current position.
     *
     * @param state game state
     * @return true if the player's tile scares monsters
     */
    private static boolean hasScareMechanism(GameState state) {
        Player p = state.getPlayer();
        return state.specificItemAt(ItemTable.SCR_SCARE_MONSTER, p.getX(), p.getY()) != null;
    }

    /**
     * Moves a monster one step towards or away from the player.
     * Mirrors {@code m_move()} in {@code hack.mon.c}.
     *
     * @param state game state
     * @param m     monster to move
     * @return 0=didn't move, 1=moved, 2=died
     */
    static int moveMonster(GameState state, Monster m) {
        MonsterPrototype mdat = m.getData();
        if (mdat.moveRate < Dice.rnd(6)) return 0;

        // Peaceful monsters (shopkeeper, vaultguard) do not chase the player.
        // They stay in their room and do not approach.
        if (m.isPeaceful()) return 0;

        Player player = state.getPlayer();
        int omx = m.getX(), omy = m.getY();

        // Determine approach vs. flee
        int appr = m.isFleeing() ? -1 : (m.isConfused() ? 0 : 1);

        // Target position
        int gx = player.getX(), gy = player.getY();

        // Find best adjacent cell
        int bestX = omx, bestY = omy;
        int bestDist = Integer.MAX_VALUE;
        boolean found = false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = omx + dx, ny = omy + dy;
                if (!DungeonLevel.isOk(nx, ny)) continue;

                Cell c = state.getLevel().cellAt(nx, ny);
                if (!c.getType().isAccessible()) continue;

                // Eels stay in pools
                if (mdat.letter == ';' && c.getType() != CellType.POOL) continue;

                // No diagonal through doors
                if (dx != 0 && dy != 0) {
                    CellType curType = state.getLevel().cellAt(omx, omy).getType();
                    if (curType == CellType.DOOR || c.getType() == CellType.DOOR) continue;
                }

                // Can't enter player's cell (attack handled above)
                if (nx == player.getX() && ny == player.getY()) continue;

                // Can't enter cell occupied by another monster
                if (state.monsterAt(nx, ny) != null) continue;

                // Avoid garlic
                if ("VZW ".indexOf(mdat.letter) >= 0
                        && state.specificItemAt(ItemTable.CLOVE_OF_GARLIC, nx, ny) != null) continue;

                int dist = new Coord(nx, ny).dist2(gx, gy);
                if (appr == 0) dist = Dice.rn2(1000);  // random walk when confused
                else if (appr < 0) dist = -dist;       // flee: maximise distance

                if (dist < bestDist) {
                    bestDist = dist;
                    bestX    = nx;
                    bestY    = ny;
                    found    = true;
                }
            }
        }

        // Confined monsters (shopkeeper, vault guard) cannot leave their home room
        if (found && m.isConfined()) {
            int[] hr = m.getHomeRoom();
            if (bestX < hr[0] || bestX > hr[2] || bestY < hr[1] || bestY > hr[3]) {
                // Destination is outside home room — don't move
                return 0;
            }
        }

        if (!found) return 0;

        // Record old position in track
        m.recordTrack(omx, omy);

        // Restore old cell symbol
        restoreCell(state, omx, omy);

        // Move
        m.setPos(bestX, bestY);
        if (DungeonLevel.isOk(bestX, bestY)) {
            state.getLevel().cellAt(bestX, bestY).setScrsym(mdat.letter);
        }

        return 1;
    }

    /**
     * Restores a cell's symbol after a monster has vacated it.
     *
     * @param state game state
     * @param x     column
     * @param y     row
     */
    static void restoreCell(GameState state, int x, int y) {
        if (!DungeonLevel.isOk(x, y)) return;
        DungeonLevel level = state.getLevel();
        Cell c = level.cellAt(x, y);

        // Check for gold or item first
        GoldPile gold = level.goldAt(x, y);
        if (gold != null) { c.setScrsym('$'); return; }
        Item it = state.itemAt(x, y);
        if (it != null) { c.setScrsym(it.getSymbol()); return; }

        // Default cell symbol.
        // Also clears any stale '@' that may have been written into scrsym
        // when the player occupied this cell (belt-and-suspenders guard).
        switch (c.getType()) {
            case ROOM:   c.setScrsym('.'); break;
            case CORR:   c.setScrsym('#'); break;
            case STAIRS:
                // Restore from the level's recorded stair coordinates, never
                // from scrsym: a shopkeeper, bank manager or the player having
                // stood here leaves '@' behind, which later renders as '<'.
                if (x == level.getXDnStair() && y == level.getYDnStair())      c.setScrsym('>');
                else if (x == level.getXUpStair() && y == level.getYUpStair()) c.setScrsym('<');
                break;
            case DOOR:
            case LDOOR:
                // Restore the door's ACTUAL state. This used to hard-code '+',
                // so every open door a monster walked through was silently
                // shown as closed afterwards.
                c.setScrsym(c.isDoorOpen() ? ' ' : '+');
                break;
            default:
                // Clear any residual player symbol on walls/empty cells
                if (c.getScrsym() == '@') c.setScrsym(c.getType().defaultSymbol);
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Monster death
    // -----------------------------------------------------------------------

    /**
     * Handles monster death: releases items and gold, updates the display,
     * and removes the monster from the game.
     *
     * Mirrors {@code mondead()} in {@code hack.mon.c}.
     *
     * @param state game state
     * @param m     monster that died
     */
    public static void monsterDies(GameState state, Monster m) {
        int x = m.getX(), y = m.getY();

        // Drop gold
        if (m.getGold() > 0) {
            GoldPile existing = state.getLevel().goldAt(x, y);
            if (existing != null) {
                existing.add(m.getGold());
            } else {
                state.getLevel().getGold().add(new GoldPile(x, y, m.getGold()));
                if (DungeonLevel.isOk(x, y)) state.getLevel().cellAt(x, y).setScrsym('$');
            }
        }

        // Drop inventory
        for (Item it : m.getInventory()) {
            state.placeItem(it, x, y);
        }

        // Restore cell symbol
        restoreCell(state, x, y);

        // Remove from game
        state.removeMonster(m);
    }

    /**
     * Handles a monster being killed by the player, awarding experience.
     * Mirrors {@code killed()} in {@code hack.mon.c}.
     *
     * @param state game state
     * @param m     killed monster
     */
    public static void killMonster(GameState state, Monster m) {
        Player player = state.getPlayer();
        MonsterPrototype mdat = m.getData();

        if (player.isBlind()) {
            state.pline("You destroy it!");
        } else {
            state.pline("You destroy " +
                (m.isTame() ? "your poor " + mdat.name : mdat.name) + "!");
        }

        // Experience formula (mirrors NEW_SCORING in hack.mon.c)
        int xp = 1 + mdat.level * mdat.level;
        if (mdat.ac < 3) xp += 2 * (7 - mdat.ac);
        if (mdat.level > 6) xp += 50;
        if (mdat.letter == ';') xp += 1000;

        player.setExperience(player.getExperience() + xp);
        player.setRoleExperience(player.getRoleExperience() + 4 * xp);

        // Record kill
        for (int i = 0; i < MonsterPrototype.ALL.length; i++) {
            if (MonsterPrototype.ALL[i] == mdat) {
                player.recordKill(i);
                break;
            }
        }

        // Level up
        while (player.getLevel() < 14 &&
               player.getExperience() >= 10 * Dice.pow2(player.getLevel() - 1)) {
            player.setLevel(player.getLevel() + 1);
            int hpGain = Dice.rnd(10);
            if (hpGain < 3) hpGain = Dice.rnd(10);
            player.setHpMax(player.getHpMax() + hpGain);
            player.setHp(player.getHp() + hpGain);
            state.pline("Welcome to XP level " + player.getLevel()
                    + "!  (total XP: " + player.getExperience() + ")");
        }

        // Drop a corpse / item at the death site
        dropCorpse(state, m, mdat);

        monsterDies(state, m);
    }

    /**
     * Maybe drops a corpse or special item where the monster died.
     *
     * @param state game state
     * @param m     killed monster
     * @param mdat  monster prototype
     */
    /**
     * Drops a named corpse item for every killed monster.
     * The corpse is always left at the monster's death position and is
     * always edible by the player (FOOD_SYM '%').
     *
     * Special drops:
     *   Minotaur ('m') → wand of digging
     *   Long worm ('w') → worm tooth
     *   All others      → a named "%  dead <monster>" corpse item
     *
     * The corpse index maps to ItemTable indices 18+ where each entry is
     * named after the corresponding monster in MonsterPrototype.ALL.
     * We find the monster's ALL[] index to get the right corpse name.
     */
    private static void dropCorpse(GameState state, Monster m, MonsterPrototype mdat) {
        char let = mdat.letter;
        int  x   = m.getX(), y = m.getY();

        // Little dog always drops "dead little dog" food corpse (%)
        if (mdat == MonsterPrototype.LITTLE_DOG) {
            Item corpse = state.createItem(ItemTable.FOOD_RATION); // use food ration as base
            if (corpse != null) {
                corpse.setDisplayName("dead little dog");
                state.placeItem(corpse, x, y);
            }
            return;
        }

        // Minotaur always drops a wand of digging
        if (let == 'm') {
            Item wand = state.createItem(ItemTable.WAN_DIGGING);
            state.placeItem(wand, x, y);
            return;
        }
        // Long worm always drops a worm tooth
        if (let == 'w') {
            Item tooth = state.createItem(ItemTable.WORM_TOOTH);
            state.placeItem(tooth, x, y);
            return;
        }

        // Always drop a corpse for any monster with a letter.
        // Find the monster's index in MonsterPrototype.ALL to get
        // the matching corpse entry (ItemTable index = DEAD_HUMAN + monsterAllIdx).
        if (!Character.isLetter(let) && let != '&' && let != ';') return;

        int monIdx = -1;
        for (int i = 0; i < MonsterPrototype.ALL.length; i++) {
            if (MonsterPrototype.ALL[i] == mdat) { monIdx = i; break; }
        }
        if (monIdx < 0) return; // special monster with no corpse table entry

        // Corpse entries occupy ItemTable indices 18-70 only.  MonsterPrototype.ALL
        // is longer than that range, so higher monster indices used to map onto
        // the weapon block (purple worm -> "arrow", shopkeeper -> "dart").
        // Clamp into the corpse range and rely on setDisplayName() below for the
        // correct name, so every corpse is a '%' food item.
        int corpseIdx = ItemTable.DEAD_HUMAN + monIdx;
        if (corpseIdx > ItemTable.LAST_CORPSE) corpseIdx = ItemTable.LAST_CORPSE;
        if (corpseIdx < ItemTable.DEAD_HUMAN) return;

        Item corpse = state.createItem(corpseIdx);
        if (corpse == null) return;
        corpse.setAge(state.getMoves());
        // Name the corpse after the actual monster, not just the index
        corpse.setDisplayName("dead " + mdat.name);
        state.placeItem(corpse, x, y);
    }

    // -----------------------------------------------------------------------
    // Theft helpers
    // -----------------------------------------------------------------------

    /**
     * Leprechaun steals gold from the player.
     * Mirrors {@code stealgold()} in {@code hack.steal.c}.
     *
     * @param state game state
     * @param thief the leprechaun
     */
    static void stealGold(GameState state, Monster thief) {
        Player p = state.getPlayer();
        // A leprechaun can only take gold that actually exists. The old test
        // was "== 0", so a purse already at or below zero was still robbed and
        // the player's gold went further negative.
        if (p.getGold() <= 0) return;
        long stolen = Math.max(1, p.getGold() / 4 + Dice.rn2(4));
        stolen = Math.min(stolen, p.getGold());   // never overdraw the purse
        p.setGold(p.getGold() - stolen);
        thief.setGold(thief.getGold() + stolen);
        state.pline("Your purse feels lighter!");
        thief.setFleeing(true);
    }

    /**
     * Nymph steals a random inventory item from the player.
     * Mirrors {@code steal()} in {@code hack.steal.c}.
     *
     * @param state game state
     * @param thief the nymph
     */
    static void stealItem(GameState state, Monster thief) {
        Player p = state.getPlayer();
        List<Item> inv = p.getInventory();
        if (inv.isEmpty()) return;
        Item stolen = inv.remove(Dice.rn2(inv.size()));
        state.pline(CombatEngine.capFirst(thief.getData().name) + " steals " + stolen.doname() + "!");
        thief.setFleeing(true);
    }

    /**
     * Returns a living LITTLE_DOG monster within 2 tiles of (x,y), or null.
     */
    private static Monster findAdjacentDog(GameState gs, int x, int y) {
        for (Monster candidate : gs.getLiveMonsters()) {
            if (candidate.getData() != MonsterPrototype.LITTLE_DOG) continue;
            int distSq = new Coord(x, y).dist2(candidate.getX(), candidate.getY());
            if (distSq <= 2) return candidate;
        }
        return null;
    }
}
