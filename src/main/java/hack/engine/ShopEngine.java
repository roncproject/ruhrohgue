package hack.engine;

import hack.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ShopEngine.java — shop tab, payment and shopkeeper behaviour.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>A shopkeeper is <b>never hostile</b>. Taking goods opens a tab; it does
 *       not provoke an attack.</li>
 *   <li>While the player owes money the shopkeeper stands in the shop doorway
 *       and does not move, so the player cannot leave without settling up.</li>
 *   <li>With no outstanding tab the shopkeeper steps aside, exactly like the
 *       bank manager, so the player can walk in and out freely.</li>
 *   <li>The tab is always derived from items the player is actually carrying.
 *       Anything eaten, dropped or otherwise gone is struck from the tab, so
 *       stale entries can never re-appear on a later visit.</li>
 * </ul>
 */
public final class ShopEngine {

    private ShopEngine() { }

    // ── Room helpers ────────────────────────────────────────────────────────

    /** Returns the shop room containing (x,y), or {@code null}. */
    public static Room shopRoomAt(GameState state, int x, int y) {
        DungeonLevel level = state.getLevel();
        if (level == null) return null;
        for (Room r : level.getRooms()) {
            if (!r.type.isShop()) continue;
            if (x >= r.lx && x <= r.hx && y >= r.ly && y <= r.hy) return r;
        }
        return null;
    }

    /** Returns the level's shop room, or {@code null} when the floor has none. */
    public static Room shopRoom(GameState state) {
        DungeonLevel level = state.getLevel();
        if (level == null) return null;
        for (Room r : level.getRooms()) if (r.type.isShop()) return r;
        return null;
    }

    /** Returns the level's vault (Bank) room, or {@code null}. */
    public static Room vaultRoom(GameState state) {
        DungeonLevel level = state.getLevel();
        if (level == null) return null;
        for (Room r : level.getRooms()) if (r.type == RoomType.VAULT) return r;
        return null;
    }

    /** True when the player currently stands inside a shop. */
    public static boolean isPlayerInShop(GameState state) {
        Player p = state.getPlayer();
        return shopRoomAt(state, p.getX(), p.getY()) != null;
    }

    /**
     * Finds the doorway of a room: the wall cell that was turned into a door.
     *
     * @return {x,y} of the door, or {@code null} when the room has none
     */
    public static int[] doorOf(GameState state, Room r) {
        DungeonLevel level = state.getLevel();
        if (level == null || r == null) return null;
        for (int x = r.lx - 1; x <= r.hx + 1; x++) {
            for (int y = r.ly - 1; y <= r.hy + 1; y++) {
                if (!DungeonLevel.isOk(x, y)) continue;
                // Only the surrounding wall ring can hold the door
                boolean onRing = (x == r.lx - 1 || x == r.hx + 1
                               || y == r.ly - 1 || y == r.hy + 1);
                if (!onRing) continue;
                CellType t = level.cellAt(x, y).getType();
                if (t == CellType.DOOR || t == CellType.LDOOR || t == CellType.SDOOR) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    /**
     * The cell just inside the shop next to the doorway — where the shopkeeper
     * stands to block a departing debtor.
     */
    public static int[] guardPost(GameState state, Room r) {
        int[] door = doorOf(state, r);
        if (door == null) return null;
        int dx = door[0], dy = door[1];
        int gx = Math.min(Math.max(dx, r.lx), r.hx);
        int gy = Math.min(Math.max(dy, r.ly), r.hy);
        return new int[]{gx, gy};
    }

    // ── Tab bookkeeping ─────────────────────────────────────────────────────

    /** The price of one item, never less than 1 gold. */
    public static long priceOf(Item it) {
        if (it == null) return 1;
        ItemClass ic = it.getItemClass();
        return (ic != null) ? Math.max(1, ic.cost) : 1;
    }

    /**
     * Records a debt for an item that has been consumed or destroyed.
     *
     * <p>Eating an unpaid ration used to wipe it from the tab, so the shop's
     * stock could be eaten for free. The charge now survives the item: it is
     * added to a per-floor debt that the shopkeeper remembers even if the
     * player leaves the dungeon floor and comes back.</p>
     */
    public static void chargeConsumed(GameState state, Item it) {
        if (it == null) return;
        long price = priceOf(it);
        state.getPlayer().getUnpaidItems().remove(it);
        it.setUnpaid(false);
        state.addShopDebt(state.getDungeonLevel(), price);
        state.pline("The shopkeeper notes " + price + " gold for the "
                + plainName(it) + " you consumed.");
    }

    /** Display name without any [unpaid] decoration. */
    private static String plainName(Item it) {
        String dn = it.getDisplayName();
        if (dn != null) return dn;
        return (it.getItemClass() != null) ? it.getItemClass().name : "item";
    }

    /** Total the player owes this floor's shopkeeper, tab plus consumed debt. */
    public static long amountOwed(GameState state) {
        return tabTotal(state) + state.getShopDebt(state.getDungeonLevel());
    }

    /** True when the player owes this floor's shopkeeper anything at all. */
    public static boolean owesMoney(GameState state) {
        reconcileTab(state);
        return state.getPlayer().hasUnpaidItems()
            || state.getShopDebt(state.getDungeonLevel()) > 0;
    }

    /**
     * Drops tab entries the player no longer carries.
     *
     * <p>Eating, dropping or throwing an unpaid item removed it from the
     * inventory but left it on the tab, so the Shop Tab accumulated ghost
     * entries that re-appeared on every later visit and made the total
     * meaningless. The tab is now always a subset of the inventory.</p>
     *
     * @return the number of stale entries removed
     */
    public static int reconcileTab(GameState state) {
        Player p = state.getPlayer();
        List<Item> inv = p.getInventory();
        List<Item> tab = p.getUnpaidItems();
        int removed = 0;
        for (int i = tab.size() - 1; i >= 0; i--) {
            Item it = tab.get(i);
            if (it == null || !inv.contains(it)) { tab.remove(i); removed++; }
        }
        return removed;
    }

    /** Adds an item to the tab, marking it unpaid. Ignores duplicates. */
    public static void addToTab(GameState state, Item it) {
        if (it == null) return;
        Player p = state.getPlayer();
        if (!p.getUnpaidItems().contains(it)) p.getUnpaidItems().add(it);
    }

    /** Total owed across every item currently on the tab. */
    public static long tabTotal(GameState state) {
        reconcileTab(state);
        long total = 0;
        for (Item it : state.getPlayer().getUnpaidItems()) total += priceOf(it);
        return total;
    }

    // ── 'p' command: open the Shop Tab ──────────────────────────────────────

    /**
     * Builds the Shop Tab signal for the browser.
     *
     * <p>Emits {@code [shoptab:name:price|name:price|…]} as a one-shot UI
     * signal. The dialog stays open while the player types item letters and
     * closes only on Cash, Bank or Escape.</p>
     *
     * @return false — opening the tab does not consume a turn
     */
    public static boolean doShowShopTab(GameState state) {
        Player p = state.getPlayer();
        reconcileTab(state);
        if (!owesMoney(state)) {
            state.pline("You have no unpaid items.");
            return false;
        }
        StringBuilder sb = new StringBuilder("[shoptab:");
        List<Item> tab = p.getUnpaidItems();
        for (int i = 0; i < tab.size(); i++) {
            if (i > 0) sb.append("|");
            // '|' and ':' are field separators — strip them from the name
            String name = tab.get(i).doname().replace("|", " ").replace(":", " ");
            sb.append(name).append(":").append(priceOf(tab.get(i)));
        }
        // Anything already eaten or destroyed appears as a single running line
        long debt = state.getShopDebt(state.getDungeonLevel());
        if (debt > 0) {
            if (tab.size() > 0) sb.append("|");
            sb.append("consumed goods").append(":").append(debt);
        }
        sb.append("]");
        state.pline(sb.toString());
        return false;
    }

    // ── Payment ─────────────────────────────────────────────────────────────

    /**
     * Settles selected tab entries.
     *
     * @param args {@code "cash:0,1,2"} or {@code "bank:0,2"}; an empty index
     *             list means "pay for everything on the tab"
     * @return true when gold changed hands (the turn is consumed)
     */
    public static boolean doPayShop(GameState state, String args) {
        Player p = state.getPlayer();
        reconcileTab(state);
        List<Item> tab = p.getUnpaidItems();
        long debt = state.getShopDebt(state.getDungeonLevel());
        if (tab.isEmpty() && debt <= 0) { state.pline("Nothing to pay for."); return false; }

        String[] parts = args.split(":", 2);
        if (parts.length < 1 || parts[0].isEmpty()) return false;
        String method = parts[0].trim();
        if (!"cash".equals(method) && !"bank".equals(method)) return false;

        // Collect the selected indices, de-duplicated and in range.
        List<Integer> idxList = new ArrayList<>();
        boolean payDebt = false;
        int debtIndex = tab.size();      // the "consumed goods" line, if present
        if (parts.length == 2 && !parts[1].isBlank()) {
            for (String s : parts[1].split(",")) {
                try {
                    int idx = Integer.parseInt(s.trim());
                    if (idx == debtIndex && debt > 0) { payDebt = true; continue; }
                    if (idx >= 0 && idx < tab.size() && !idxList.contains(idx)) idxList.add(idx);
                } catch (NumberFormatException ignored) { /* skip junk */ }
            }
        } else {
            for (int i = 0; i < tab.size(); i++) idxList.add(i);
            payDebt = debt > 0;
        }
        if (idxList.isEmpty() && !payDebt) { state.pline("No items selected."); return false; }

        long total = 0;
        for (int idx : idxList) total += priceOf(tab.get(idx));
        if (payDebt) total += debt;

        if ("cash".equals(method)) {
            if (p.getGold() < total) {
                state.pline("You cannot afford that! (" + total + " gold needed, you carry "
                        + p.getGold() + ".)");
                return false;
            }
            p.setGold(p.getGold() - total);
        } else {
            if (p.getBank() < total) {
                state.pline("Not enough Credit. (" + total + " needed, balance "
                        + p.getBank() + ".)");
                return false;
            }
            p.setBank(p.getBank() - total);
            state.resetAccruedInterest();   // interest is counted since this payment
        }

        // Remove paid entries back-to-front so earlier indices stay valid.
        idxList.sort(Collections.reverseOrder());
        for (int idx : idxList) {
            Item it = tab.remove(idx);
            if (it != null) it.setUnpaid(false);
        }
        if (payDebt) state.clearShopDebt(state.getDungeonLevel());

        state.pline("You pay " + total + " gold "
                + ("cash".equals(method) ? "in cash." : "from your Bank account."));
        state.pline("Thank you for your business Sir.");
        return true;
    }

    // ── Shopkeeper behaviour ────────────────────────────────────────────────

    /**
     * Keeps the shopkeeper where it belongs, once per turn.
     *
     * <p>With an open tab the shopkeeper walks to the doorway and stays put.
     * With a clear tab it wanders back inside so it is not permanently
     * standing in the exit.</p>
     */
    public static void updateShopkeeper(GameState state) {
        Room shop = shopRoom(state);
        if (shop == null) return;
        Monster keeper = findKeeper(state, shop);
        if (keeper == null) return;

        keeper.setPeaceful(true);          // shopkeepers never turn hostile
        keeper.setSleeping(false);

        reconcileTab(state);
        boolean owes = owesMoney(state);
        int[] post = guardPost(state, shop);
        if (post == null) return;

        if (owes) {
            // Move to the doorway and hold it.
            if (keeper.getX() != post[0] || keeper.getY() != post[1]) {
                if (state.monsterAt(post[0], post[1]) == null
                        && !(state.getPlayer().getX() == post[0]
                          && state.getPlayer().getY() == post[1])) {
                    moveKeeper(state, keeper, post[0], post[1]);
                }
            }
        } else if (keeper.getX() == post[0] && keeper.getY() == post[1]) {
            // Tab settled — step back inside so the doorway is clear.
            for (int x = shop.lx; x <= shop.hx; x++) {
                for (int y = shop.ly; y <= shop.hy; y++) {
                    if (x == post[0] && y == post[1]) continue;
                    if (state.getLevel().cellAt(x, y).getType() != CellType.ROOM) continue;
                    if (state.monsterAt(x, y) != null) continue;
                    if (state.getPlayer().getX() == x && state.getPlayer().getY() == y) continue;
                    moveKeeper(state, keeper, x, y);
                    return;
                }
            }
        }
    }

    /** Relocates the shopkeeper, keeping the map symbols consistent. */
    private static void moveKeeper(GameState state, Monster keeper, int x, int y) {
        int ox = keeper.getX(), oy = keeper.getY();
        keeper.setPos(x, y);
        MonsterEngine.restoreCell(state, ox, oy);
        if (DungeonLevel.isOk(x, y)) {
            state.getLevel().cellAt(x, y).setScrsym(MonsterPrototype.SHOPKEEPER.letter);
        }
    }

    /** Finds the shopkeeper belonging to the given shop room. */
    public static Monster findKeeper(GameState state, Room shop) {
        for (Monster m : state.getLiveMonsters()) {
            if (m.getData() != MonsterPrototype.SHOPKEEPER) continue;
            if (m.getX() >= shop.lx - 1 && m.getX() <= shop.hx + 1
                    && m.getY() >= shop.ly - 1 && m.getY() <= shop.hy + 1) return m;
        }
        return null;
    }

    /** Finds the bank manager belonging to the given vault room. */
    public static Monster findManager(GameState state, Room vault) {
        for (Monster m : state.getLiveMonsters()) {
            if (m.getData() != MonsterPrototype.VAULTGUARD) continue;
            if (m.getX() >= vault.lx - 1 && m.getX() <= vault.hx + 1
                    && m.getY() >= vault.ly - 1 && m.getY() <= vault.hy + 1) return m;
        }
        return null;
    }

    // ── Level re-entry ──────────────────────────────────────────────────────

    /**
     * Re-creates the shopkeeper and the bank manager after a level is restored.
     *
     * <p>{@code gotoLevel()} clears every live monster before swapping in a
     * remembered level, and only random wandering monsters were re-spawned.
     * The shopkeeper and the bank manager therefore vanished the moment the
     * player revisited a floor, leaving the shop and the Bank unattended.</p>
     */
    public static void restoreStaff(GameState state) {
        Room shop = shopRoom(state);
        if (shop != null && findKeeper(state, shop) == null) {
            int[] pos = freeCell(state, shop);
            if (pos != null) {
                Monster m = state.createMonster(MonsterPrototype.SHOPKEEPER, pos[0], pos[1]);
                if (m != null) {
                    m.setPeaceful(true);
                    m.setSleeping(false);
                    m.setShopkeeper(true);
                    m.setHomeRoom(shop.lx, shop.ly, shop.hx, shop.hy);
                }
            }
        }
        Room vault = vaultRoom(state);
        if (vault != null && findManager(state, vault) == null) {
            int[] pos = freeCell(state, vault);
            if (pos != null) {
                Monster m = state.createMonster(MonsterPrototype.VAULTGUARD, pos[0], pos[1]);
                if (m != null) {
                    m.setPeaceful(true);
                    m.setSleeping(false);
                    m.setShopkeeper(false);
                    m.setHomeRoom(vault.lx, vault.ly, vault.hx, vault.hy);
                }
            }
        }
    }

    /** First free floor cell in the room that is not occupied by the player. */
    private static int[] freeCell(GameState state, Room r) {
        DungeonLevel level = state.getLevel();
        if (level == null) return null;
        Player p = state.getPlayer();
        for (int x = r.lx; x <= r.hx; x++) {
            for (int y = r.ly; y <= r.hy; y++) {
                if (level.cellAt(x, y).getType() != CellType.ROOM) continue;
                if (state.monsterAt(x, y) != null) continue;
                if (p != null && p.getX() == x && p.getY() == y) continue;
                return new int[]{x, y};
            }
        }
        return null;
    }
}
