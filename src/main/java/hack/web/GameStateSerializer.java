package hack.web;

import hack.engine.VisibilityEngine;
import hack.model.*;

import java.util.*;

/**
 * GameStateSerializer.java — converts {@link GameState} to a {@code Map} that
 * Spring's Jackson auto-configuration serialises to JSON.
 *
 * <p>Produces the same JSON structure as the original hand-rolled
 * {@code GameController.toJson()} so that the frontend JavaScript requires no
 * structural changes.</p>
 *
 * <h2>JSON schema</h2>
 * <pre>
 * {
 *   "phase":     "PLAYING" | "DEAD" | "ESCAPED" | "QUIT",
 *   "player":    { x, y, hp, hpMax, level, exp, gold, str, ac,
 *                  name, class, hunger, blind, confused, levitating, invisible },
 *   "messages":  ["newest", "older", ...],
 *   "topMsg":    "string",
 *   "map":       [[col_0_cells], [col_1_cells], ...],  // [x][y], 80×22
 *   "monsters":  [{ x, y, sym, name }, ...],
 *   "items":     [{ x, y, sym, name }, ...],
 *   "gold":      [{ x, y, amount }, ...],
 *   "traps":     [{ x, y, desc }, ...],
 *   "inventory": [{ slot, name, sym, worn }, ...],
 *   "dlevel":    1,
 *   "moves":     0,
 *   "score":     0,
 *   "killer":    ""
 * }
 * </pre>
 */
public final class GameStateSerializer {

    private GameStateSerializer() {}

    /**
     * Converts the full game state to a nested {@link Map} / {@link List}
     * structure that Spring's Jackson serialiser will turn into JSON.
     *
     * @param gs game state to convert
     * @return serialisable map
     */
    public static Map<String, Object> toMap(GameState gs) {
        return toMap(gs, java.util.Collections.emptyList());
    }

    public static Map<String, Object> toMap(GameState gs,
            java.util.List<hack.model.HighScoreEntry> highScores) {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("phase",     gs.getPhase().name());
        root.put("player",    playerMap(gs));
        root.put("messages",  messagesList(gs));
        root.put("topMsg",    gs.getTopMessage());
        // One-shot UI control string (look overlay, choosers, shop tab).
        // Reading it clears it, so it can never be replayed by a later poll.
        root.put("signal",    gs.consumeSignal());
        root.put("seed",      gs.getGameSeed());
        root.put("seedFixed", gs.isSeedExplicit());
        root.put("map",       mapGrid(gs));
        root.put("monsters",  monstersList(gs));
        root.put("items",     itemsList(gs));
        root.put("gold",      goldList(gs));
        root.put("traps",     trapsList(gs));
        root.put("inventory", inventoryList(gs));
        root.put("dlevel",    gs.getDungeonLevel());
        root.put("moves",     gs.getMoves());
        root.put("score",     gs.getFinalScore());
        root.put("killer",    gs.getKiller() == null ? "" : gs.getKiller());
        root.put("maxFloor",  gs.getMaxDungeonLevel());

        // High scores — only included when provided (i.e. after game over)
        if (!highScores.isEmpty()) {
            java.util.List<java.util.Map<String,Object>> hs = new java.util.ArrayList<>();
            for (hack.model.HighScoreEntry e : highScores) {
                java.util.Map<String,Object> row = new java.util.LinkedHashMap<>();
                row.put("name",  e.getName());
                row.put("class", e.getCharacterClass());
                row.put("floor", e.getMaxFloor());
                row.put("score", e.getScore());
                row.put("time",  e.getDateTime());
                hs.add(row);
            }
            root.put("highScores", hs);
        }

        return root;
    }

    // -------------------------------------------------------------------------
    // Sub-serialisers
    // -------------------------------------------------------------------------

    private static Map<String, Object> playerMap(GameState gs) {
        Player p = gs.getPlayer();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x",          p.getX());
        m.put("y",          p.getY());
        m.put("hp",         p.getHp());
        m.put("hpMax",      p.getHpMax());
        m.put("level",      p.getLevel());
        m.put("exp",        p.getExperience());
        m.put("gold",       p.getGold());
        m.put("str",        p.getStrength());
        m.put("ac",         p.getAc());
        m.put("name",           p.getName());
        m.put("characterClass", p.getCharacterClass());
        m.put("class",      p.getCharacterClass());
        m.put("hunger",     p.getHungerState().label.trim());
        m.put("blind",      p.isBlind());
        m.put("confused",   p.isConfused());
        m.put("levitating", p.isLevitating());
        m.put("invisible",  p.isInvisible());
        m.put("bank",        p.getBank());
        m.put("hasUnpaidItems", p.hasUnpaidItems());
        m.put("unpaidCount",  p.getUnpaidItems().size());
        return m;
    }

    private static List<String> messagesList(GameState gs) {
        List<String> out = new ArrayList<>();
        int count = 0;
        for (String msg : gs.getMessages()) {
            if (count++ >= 20) break;
            out.add(msg);
        }
        return out;
    }

    /**
     * Builds the 80×22 map grid as a list of columns (each column is a list of
     * 22 single-character strings).  Visibility rules:
     *
     * <ul>
     *   <li><b>Corridors (#)</b> – shown only if the player has physically
     *       visited the cell (seen flag set by doMove) and the scrsym is '#'.
     *       They are NOT revealed by room expansion, so unvisited branches
     *       remain dark until the player walks through them.</li>
     *   <li><b>Room tiles (., -, |, +, &lt;, &gt;)</b> – shown either when the
     *       player is currently in the same lit room (canSee returns true) OR
     *       after the player has visited the room (seen=true). This means a
     *       room stays on the map after the player leaves, but a completely
     *       unvisited room stays dark even if it is lit.</li>
     *   <li><b>Monsters</b> – handled separately by monstersList; never
     *       leaked through the map grid.</li>
     * </ul>
     */
    private static List<List<String>> mapGrid(GameState gs) {
        DungeonLevel level = gs.getLevel();
        List<List<String>> columns = new ArrayList<>(DungeonLevel.COLNO);
        for (int x = 0; x < DungeonLevel.COLNO; x++) {
            List<String> col = new ArrayList<>(DungeonLevel.ROWNO);
            for (int y = 0; y < DungeonLevel.ROWNO; y++) {
                if (level == null) { col.add(" "); continue; }
                Cell c = level.cellAt(x, y);
                char sym = c.getScrsym();

                boolean show;
                if (c.getType() == CellType.CORR || c.getType() == CellType.SCORR) {
                    show = c.isSeen() && sym == '#';
                } else {
                    show = VisibilityEngine.canSee(gs, x, y) || c.isSeen();
                }

                // ── Staircases are rendered from the level's own coordinates ──
                // scrsym is a scratch field: the player, the shopkeeper and the
                // bank manager all render as '@' and overwrite it, and monster
                // letters land on it too. Once that happened the later
                // "unknown symbol" fallback replaced it with
                // CellType.STAIRS.defaultSymbol, which is '<' — so a down
                // staircase silently turned into a second up staircase and the
                // floor had no visible exit. The level records both stair
                // positions explicitly, so use those as the single source of
                // truth and never infer a stair symbol from scrsym.
                // Doors render from their recorded state, for the same reason
                // staircases do: scrsym is scratch, overwritten by whatever
                // stands on the cell.
                if (c.getType() == CellType.DOOR || c.getType() == CellType.LDOOR) {
                    col.add(show ? (c.isDoorOpen() ? " " : "+") : " ");
                    continue;
                }

                if (c.getType() == CellType.STAIRS) {
                    if (x == level.getXDnStair() && y == level.getYDnStair())      sym = '>';
                    else if (x == level.getXUpStair() && y == level.getYUpStair()) sym = '<';
                    col.add(show ? String.valueOf(sym) : " ");
                    continue;
                }

                // Never leak the player '@' through the map grid.
                if (sym == '@') sym = c.getType().defaultSymbol;

                // Never leak monster letters through the map grid when the monster
                // is no longer visible. If scrsym is a monster letter but no live
                // monster is at this cell right now, fall back to the cell's type symbol.
                // This prevents saved monster scrsyms from showing after level-return.
                // If a food marker (negative GoldPile) is on this cell,
                // always render '%' regardless of scrsym (gold overlay uses '$').
                if (show && gs.getLevel() != null) {
                    GoldPile _fp = gs.getLevel().goldAt(x, y);
                    if (_fp != null && _fp.getAmount() < 0) sym = '%';
                }

                // For corridors: only show '#' — never show stale monster letters.
                // A monster in a corridor is handled exclusively by monstersList().
                if (c.getType() == CellType.CORR || c.getType() == CellType.SCORR) {
                    if (sym != '#') sym = '#'; // reset any stale monster sym
                } else if (show && sym != ' ' && sym != '#' && sym != '.'
                        && sym != '+' && sym != ' ' && sym != '-' && sym != '|'
                        && sym != '<' && sym != '>' && sym != '}'
                        && sym != '$' && sym != '^' && sym != '%'
                        && sym != '\'') {
                    // Looks like a monster letter — only show it if a live monster
                    // is actually here and currently visible to the player
                    Monster mon = gs.monsterAt(x, y);
                    boolean monVis = (mon != null)
                            && VisibilityEngine.canSeeMonster(gs, mon);
                    if (!monVis) {
                        // Show underlying cell type instead
                        sym = c.getType().defaultSymbol;
                    }
                }

                col.add(show ? String.valueOf(sym) : " ");
            }
            columns.add(col);
        }
        return columns;
    }

    private static List<Map<String, Object>> monstersList(GameState gs) {
        List<Map<String, Object>> out = new ArrayList<>();
        // Floating eye vision: reveal ALL monsters on this floor for the duration
        boolean eyeVision = gs.getFloatingEyeVisionLeft() > 0;
        for (Monster m : gs.getLiveMonsters()) {
            boolean vis = VisibilityEngine.canSeeMonster(gs, m)
                        || (gs.getPlayer().hasTelepathy() && !m.isMimic())
                        || eyeVision;
            if (!vis) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x",    m.getX());
            entry.put("y",    m.getY());
            entry.put("sym",  String.valueOf(m.getDisplayChar()));
            entry.put("name", m.getData().name);
            out.add(entry);
        }
        return out;
    }

    private static List<Map<String, Object>> itemsList(GameState gs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (gs.getLevel() == null) return out;
        for (Item it : gs.getFloorItems()) {
            if (!VisibilityEngine.canSee(gs, it.getX(), it.getY())) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x",    it.getX());
            entry.put("y",    it.getY());
            entry.put("sym",  String.valueOf(it.getSymbol()));
            entry.put("name", it.displayName());
            out.add(entry);
        }
        return out;
    }

    private static List<Map<String, Object>> goldList(GameState gs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (gs.getLevel() == null) return out;
        for (GoldPile g : gs.getLevel().getGold()) {
            if (!VisibilityEngine.canSee(gs, g.getX(), g.getY())) continue;
            // Negative amounts are food-marker piles — rendered via mapGrid cell scrsym ('%)
            // not as gold piles ('$'). Skip them here to prevent '$' overwriting '%'.
            if (g.getAmount() <= 0) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x",      g.getX());
            entry.put("y",      g.getY());
            entry.put("amount", g.getAmount());
            out.add(entry);
        }
        return out;
    }

    private static List<Map<String, Object>> trapsList(GameState gs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (gs.getLevel() == null) return out;
        for (Trap t : gs.getLevel().getTraps()) {
            if (!t.isSeen()) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x",    t.getX());
            entry.put("y",    t.getY());
            entry.put("desc", t.getType().description);
            out.add(entry);
        }
        return out;
    }

    private static List<Map<String, Object>> inventoryList(GameState gs) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Item> inv = gs.getPlayer().getInventory();
        for (int i = 0; i < inv.size(); i++) {
            Item it = inv.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            // Always use the ACTUAL inventory index as slot letter.
            // The JS chooser filters display but must send back the true index.
            entry.put("slot", String.valueOf((char) ('a' + i)));
            // Flag strange objects so the JS can hide them from display only.
            entry.put("strange", it.getSymbol() == hack.model.ItemTable.ILLOBJ_SYM);
            entry.put("name", it.doname());
            // Flag items that are in the player's unpaid shop tab
            boolean isUnpaid = gs.getPlayer().getUnpaidItems().contains(it);
            if (isUnpaid) entry.put("unpaid", true);
            // Corpse freshness, so the eat menu can warn before it is too late
            if (hack.engine.FoodEngine.isCorpse(it)) {
                String rot = hack.engine.FoodEngine.rotLabel(
                        hack.engine.FoodEngine.rotAge(gs, it, false));
                if (rot != null) entry.put("rot", rot);
            }
            entry.put("sym",  String.valueOf(it.getSymbol()));
            entry.put("worn", wornLabel(gs.getPlayer(), it));
            out.add(entry);
        }
        return out;
    }

    private static String wornLabel(Player p, Item it) {
        if (it == p.getWeapon())    return "(wielded)";
        if (it == p.getArmor())     return "(worn)";
        if (it == p.getArmor2())    return "(worn)";
        if (it == p.getHelmet())    return "(helmet)";
        if (it == p.getShield())    return "(shield)";
        if (it == p.getGloves())    return "(gloves)";
        if (it == p.getRingLeft())  return "(left ring)";
        if (it == p.getRingRight()) return "(right ring)";
        return "";
    }
}
