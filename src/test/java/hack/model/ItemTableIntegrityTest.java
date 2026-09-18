package hack.model;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the ItemTable prototype array against silent truncation.
 *
 * Root cause of the original failure:
 *   OBJECTS was allocated with a hard-coded length of 215 while NROFOBJECTS
 *   was 237. set() silently ignores any index outside the array, so every
 *   Rotterdam street food from index 215 ("telo") upward was never
 *   registered. createItem() then fell back to index 0 and handed the player
 *   a "strange object" ('\\') instead of food.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("ItemTable integrity")
class ItemTableIntegrityTest {

    @Test
    @DisplayName("objectsArray_length_matchesNrOfObjects")
    void objectsArray_length_matchesNrOfObjects() {
        assertEquals(ItemTable.NROFOBJECTS, ItemTable.OBJECTS.length,
            "The prototype array must cover every declared object index");
    }

    @Test
    @DisplayName("streetFoods_everyIndex_isDefinedFoodItem")
    void streetFoods_everyIndex_isDefinedFoodItem() {
        for (int i = ItemTable.BARA; i < ItemTable.NROFOBJECTS; i++) {
            ItemClass c = ItemTable.get(i);
            assertNotNull(c, "Street food index " + i + " must be defined");
            assertNotNull(c.name, "Street food index " + i + " must have a name");
            assertEquals(ItemTable.FOOD_SYM, c.symbol,
                "Street food index " + i + " must render as '%'");
        }
    }

    @Test
    @DisplayName("placeFoodRange_noIndex_resolvesToStrangeObject")
    void placeFoodRange_noIndex_resolvesToStrangeObject() {
        // The exact range LevelGenerator.placeFood() draws from
        for (int i = ItemTable.BARA; i <= ItemTable.BARA + 24; i++) {
            ItemClass c = ItemTable.get(i);
            assertNotNull(c, "placeFood may never roll an undefined index (" + i + ")");
            assertNotEquals("strange object", c.name,
                "placeFood must never produce a strange object (index " + i + ")");
        }
    }

    @Test
    @DisplayName("corpseRange_everyMonster_mapsToFoodCorpse")
    void corpseRange_everyMonster_mapsToFoodCorpse() {
        for (int m = 0; m < MonsterPrototype.ALL.length; m++) {
            int idx = Math.min(ItemTable.DEAD_HUMAN + m, ItemTable.LAST_CORPSE);
            ItemClass c = ItemTable.get(idx);
            assertNotNull(c, "Corpse index " + idx + " must be defined");
            assertEquals(ItemTable.FOOD_SYM, c.symbol,
                "A corpse must be a '%' food item, not a weapon (monster " + m + ")");
        }
    }
}
