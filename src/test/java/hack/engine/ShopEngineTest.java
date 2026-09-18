package hack.engine;

import hack.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the shop tab, payment and shopkeeper behaviour.
 *
 * Root causes covered:
 *   1. Eating or dropping an unpaid item removed it from the inventory but
 *      left it on the tab, so ghost entries re-appeared on every later visit
 *      and the total was meaningless. reconcileTab() now keeps the tab a
 *      subset of the inventory.
 *   2. gotoLevel() cleared every live monster when restoring a remembered
 *      level and only re-spawned random wanderers, so the shopkeeper and the
 *      bank manager vanished on revisit. restoreStaff() puts them back.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("ShopEngine")
class ShopEngineTest {

    private GameState state;
    private Player    player;

    private static GameState floor(long seed, int dlevel) {
        Dice.seed(seed);
        GameState gs = new GameState();
        gs.setSeedInfo(seed, true);
        gs.startNewGame("T01", "Fighter");
        LevelGenerator.makeLevel(gs, dlevel);
        return gs;
    }

    @BeforeEach
    void setUp() {
        state  = floor(12L, 2);
        player = state.getPlayer();
        player.setGold(500);
        player.setBank(500);
    }

    // ── Tab reconciliation ────────────────────────────────────────────────

    @Test
    @DisplayName("reconcileTab_itemNoLongerCarried_isRemovedFromTab")
    void reconcileTab_itemNoLongerCarried_isRemovedFromTab() {
        Item ration = state.createItem(ItemTable.FOOD_RATION);
        player.getInventory().add(ration);
        ShopEngine.addToTab(state, ration);

        player.getInventory().remove(ration);   // eaten
        ShopEngine.reconcileTab(state);

        assertTrue(player.getUnpaidItems().isEmpty(),
            "An item the player no longer carries must not stay on the tab");
    }

    @Test
    @DisplayName("addToTab_sameItemTwice_doesNotDuplicate")
    void addToTab_sameItemTwice_doesNotDuplicate() {
        Item ration = state.createItem(ItemTable.FOOD_RATION);
        player.getInventory().add(ration);
        ShopEngine.addToTab(state, ration);
        ShopEngine.addToTab(state, ration);

        assertEquals(1, player.getUnpaidItems().size(),
            "The same item must appear on the tab only once");
    }

    // ── Payment ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("doPayShop_cashWithEnoughGold_deductsExactPrice")
    void doPayShop_cashWithEnoughGold_deductsExactPrice() {
        Item ration = state.createItem(ItemTable.FOOD_RATION);
        player.getInventory().add(ration);
        ShopEngine.addToTab(state, ration);
        long price  = ShopEngine.priceOf(ration);
        long before = player.getGold();

        assertTrue(ShopEngine.doPayShop(state, "cash:0"), "Payment should succeed");
        assertEquals(before - price, player.getGold(),
            "Cash payment must deduct exactly the item price");
        assertTrue(player.getUnpaidItems().isEmpty(), "The paid item must leave the tab");
    }

    @Test
    @DisplayName("doPayShop_bankMethod_deductsFromBankBalance")
    void doPayShop_bankMethod_deductsFromBankBalance() {
        Item ration = state.createItem(ItemTable.FOOD_RATION);
        player.getInventory().add(ration);
        ShopEngine.addToTab(state, ration);
        long before = player.getBank();

        ShopEngine.doPayShop(state, "bank:0");

        assertEquals(before - ShopEngine.priceOf(ration), player.getBank(),
            "Bank payment must deduct from the Bank balance, not from carried gold");
    }

    @Test
    @DisplayName("doPayShop_insufficientGold_refusesAndKeepsTab")
    void doPayShop_insufficientGold_refusesAndKeepsTab() {
        Item ration = state.createItem(ItemTable.FOOD_RATION);
        player.getInventory().add(ration);
        ShopEngine.addToTab(state, ration);
        player.setGold(0);

        assertFalse(ShopEngine.doPayShop(state, "cash:0"),
            "Payment must be refused when the player cannot afford it");
        assertEquals(1, player.getUnpaidItems().size(),
            "A refused payment must leave the tab untouched");
    }

    @Test
    @DisplayName("doPayShop_emptySelection_settlesWholeTab")
    void doPayShop_emptySelection_settlesWholeTab() {
        Item a = state.createItem(ItemTable.FOOD_RATION);
        Item b = state.createItem(ItemTable.FOOD_RATION + 1);
        player.getInventory().add(a);
        player.getInventory().add(b);
        ShopEngine.addToTab(state, a);
        ShopEngine.addToTab(state, b);

        ShopEngine.doPayShop(state, "cash:");

        assertTrue(player.getUnpaidItems().isEmpty(),
            "Pressing Cash with nothing ticked must settle the entire tab");
    }

    @Test
    @DisplayName("doPayShop_afterPaying_shopkeeperThanksCustomer")
    void doPayShop_afterPaying_shopkeeperThanksCustomer() {
        Item ration = state.createItem(ItemTable.FOOD_RATION);
        player.getInventory().add(ration);
        ShopEngine.addToTab(state, ration);

        ShopEngine.doPayShop(state, "cash:0");

        boolean thanked = state.getMessages().stream()
                .anyMatch(m -> m.contains("Thank you for your business Sir"));
        assertTrue(thanked, "A settled tab must be acknowledged by the shopkeeper");
    }

    // ── Staff persistence ─────────────────────────────────────────────────

    @Test
    @DisplayName("restoreStaff_afterMonsterWipe_shopkeeperIsBack")
    void restoreStaff_afterMonsterWipe_shopkeeperIsBack() {
        Room shop = ShopEngine.shopRoom(state);
        assertNotNull(shop, "An even floor must have a shop");

        state.getMonsters().clear();        // what gotoLevel() does
        assertNull(ShopEngine.findKeeper(state, shop),
            "Precondition: the wipe removes the shopkeeper");

        ShopEngine.restoreStaff(state);

        assertNotNull(ShopEngine.findKeeper(state, shop),
            "Revisiting a floor must not leave the shop unattended");
    }

    @Test
    @DisplayName("restoreStaff_afterMonsterWipe_bankManagerIsBack")
    void restoreStaff_afterMonsterWipe_bankManagerIsBack() {
        GameState odd = floor(11L, 1);
        Room vault = ShopEngine.vaultRoom(odd);
        assertNotNull(vault, "An odd floor must have a Bank");

        odd.getMonsters().clear();
        ShopEngine.restoreStaff(odd);

        assertNotNull(ShopEngine.findManager(odd, vault),
            "Revisiting a floor must not leave the Bank unattended");
    }

    @Test
    @DisplayName("movemon_shopkeeperMarkedHostile_isForcedBackToPeaceful")
    void movemon_shopkeeperMarkedHostile_isForcedBackToPeaceful() {
        Room shop = ShopEngine.shopRoom(state);
        Monster keeper = ShopEngine.findKeeper(state, shop);
        assertNotNull(keeper, "Precondition: the shop has a shopkeeper");

        keeper.setPeaceful(false);
        MonsterEngine.movemon(state);

        assertTrue(keeper.isPeaceful(),
            "A shopkeeper must never fight — it enforces payment by blocking the door");
    }
}
