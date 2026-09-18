package hack.model;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Item — lastOwner tracking and core fields.
 *
 * (Baeldung §3 — Naming, §5 — Simple, §6 — Appropriate Assertions,
 *               §7 — Specific Unit Tests)
 */
@DisplayName("Item")
class ItemTest {

    private Item foodItem;

    @BeforeEach
    void setUp() {
        // Use FOOD_RATION (index 2) from ItemTable as a representative food item
        ItemClass fc = ItemTable.OBJECTS[ItemTable.FOOD_RATION];
        foodItem = new Item(1, ItemTable.FOOD_RATION, fc);
    }

    // ── lastOwner ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getLastOwner_newItem_returnsNull")
    void getLastOwner_newItem_returnsNull() {
        // §4 — expected (null) vs actual
        assertNull(foodItem.getLastOwner(),
            "A new item should have no recorded owner");
    }

    @Test
    @DisplayName("setLastOwner_playerName_ownerRecorded")
    void setLastOwner_playerName_ownerRecorded() {
        foodItem.setLastOwner("Ron");
        assertEquals("Ron", foodItem.getLastOwner(),
            "lastOwner should reflect who last held the item");
    }

    @Test
    @DisplayName("setLastOwner_null_clearsPreviousOwner")
    void setLastOwner_null_clearsPreviousOwner() {
        foodItem.setLastOwner("Ron");
        foodItem.setLastOwner(null);
        assertNull(foodItem.getLastOwner(),
            "Setting lastOwner to null should clear previous ownership");
    }

    // ── Symbol ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getSymbol_foodItem_returnsFoodSymbol")
    void getSymbol_foodItem_returnsFoodSymbol() {
        assertEquals(ItemTable.FOOD_SYM, foodItem.getSymbol(),
            "Food item symbol should be FOOD_SYM ('%')");
    }

    // ── Quantity ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getQuantity_newItem_returnsOne")
    void getQuantity_newItem_returnsOne() {
        assertEquals(1, foodItem.getQuantity(),
            "New items should have quantity 1 by default");
    }

    @Test
    @DisplayName("setQuantity_positiveValue_quantityUpdated")
    void setQuantity_positiveValue_quantityUpdated() {
        foodItem.setQuantity(5);
        assertEquals(5, foodItem.getQuantity(),
            "Quantity should reflect the set value");
    }

    // ── doname ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("doname_foodItem_returnsNonEmptyString")
    void doname_foodItem_returnsNonEmptyString() {
        // §6 — assertFalse / assertNotNull; we don't care about exact string
        String name = foodItem.doname();
        assertNotNull(name, "doname() should never return null");
        assertFalse(name.isBlank(), "doname() should return a non-blank description");
    }
}
