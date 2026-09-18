package hack.engine;

import hack.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for corpse decay, street food and the over-eating rules.
 *
 * (Baeldung §3 — Naming, §4 — Expected first, §5 — Simple, §7 — Specific)
 */
@DisplayName("FoodEngine")
class FoodEngineTest {

    private static GameState floor(long seed, int dlevel) {
        Dice.seed(seed);
        GameState gs = new GameState();
        gs.setSeedInfo(seed, true);
        gs.startNewGame("T01", "Fighter");
        LevelGenerator.makeLevel(gs, dlevel);
        return gs;
    }

    @Test
    @DisplayName("rotDamagePercent_freshCorpse_costsOnePercent")
    void rotDamagePercent_freshCorpse_costsOnePercent() {
        assertEquals(1, FoodEngine.rotDamagePercent(0));
    }

    @Test
    @DisplayName("rotDamagePercent_fullyRotten_costsFiftyPercent")
    void rotDamagePercent_fullyRotten_costsFiftyPercent() {
        assertEquals(50, FoodEngine.rotDamagePercent(FoodEngine.ROT_STEPS));
    }

    @Test
    @DisplayName("rotDamagePercent_acrossLifetime_neverDecreases")
    void rotDamagePercent_acrossLifetime_neverDecreases() {
        int previous = -1;
        for (int t = 0; t <= FoodEngine.ROT_STEPS; t += 10) {
            int d = FoodEngine.rotDamagePercent(t);
            assertTrue(d >= previous, "Rot damage must not decrease at age " + t);
            previous = d;
        }
    }

    @Test
    @DisplayName("rotAge_corpseInShop_doesNotAge")
    void rotAge_corpseInShop_doesNotAge() {
        GameState gs = floor(6L, 1);
        Item corpse = gs.createItem(ItemTable.DEAD_HUMAN + 3);
        corpse.setDisplayName("dead kobold");
        corpse.setAge(0);
        gs.setMoves(600);

        assertEquals(0, FoodEngine.rotAge(gs, corpse, true),
            "Shop stock is kept fresh and must never rot");
    }

    @Test
    @DisplayName("rotAge_oldCorpse_capsAtRotSteps")
    void rotAge_oldCorpse_capsAtRotSteps() {
        GameState gs = floor(6L, 1);
        Item corpse = gs.createItem(ItemTable.DEAD_HUMAN + 3);
        corpse.setDisplayName("dead kobold");
        corpse.setAge(0);
        gs.setMoves(10_000);

        assertEquals(FoodEngine.ROT_STEPS, FoodEngine.rotAge(gs, corpse, false));
    }

    @Test
    @DisplayName("consume_streetFood_isTwiceAsFilling")
    void consume_streetFood_isTwiceAsFilling() {
        GameState gs = floor(7L, 1);
        Player p = gs.getPlayer();
        p.setHunger(0);

        Item street = gs.createItem(ItemTable.BARA);
        FoodEngine.consume(gs, street);

        assertEquals(2L * ItemTable.get(ItemTable.BARA).multiValue, p.getHunger(),
            "A Rotterdam delicacy must satisfy twice the hunger of its nutrition value");
    }

    @Test
    @DisplayName("consume_pastTwiceSatiated_warnsButDoesNotKill")
    void consume_pastTwiceSatiated_warnsButDoesNotKill() {
        GameState gs = floor(8L, 1);
        gs.getPlayer().setHunger(FoodEngine.STUFFED - 100);

        boolean died = FoodEngine.consume(gs, gs.createItem(ItemTable.FOOD_RATION));

        assertFalse(died, "Being over-full must not be fatal on its own");
        assertTrue(gs.getMessages().stream().anyMatch(m -> m.contains("really full already")),
            "The player must be told they are struggling to eat");
    }

    @Test
    @DisplayName("consume_pastThreeTimesSatiated_killsByGluttony")
    void consume_pastThreeTimesSatiated_killsByGluttony() {
        GameState gs = floor(8L, 1);
        gs.getPlayer().setHunger(FoodEngine.FATAL - 10);

        boolean died = FoodEngine.consume(gs, gs.createItem(ItemTable.FOOD_RATION));

        assertTrue(died, "Eating past three times Satiated must be fatal");
        assertEquals(GameState.Phase.DEAD, gs.getPhase());
        assertEquals("Gluttony", gs.getKiller(),
            "The score table must record Gluttony as the cause of death");
    }

    @Test
    @DisplayName("isStreetFood_ordinaryRation_returnsFalse")
    void isStreetFood_ordinaryRation_returnsFalse() {
        GameState gs = floor(9L, 1);
        assertFalse(FoodEngine.isStreetFood(gs.createItem(ItemTable.FOOD_RATION)));
        assertTrue(FoodEngine.isStreetFood(gs.createItem(ItemTable.BARA)));
    }
}
