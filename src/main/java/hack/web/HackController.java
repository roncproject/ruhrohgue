package hack.web;

import hack.engine.*;
import hack.model.*;
import hack.web.dto.NewGameRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HackController.java — Spring Boot controller for Hack 1.0.2 AWS Web Edition v2.
 *
 * <h2>Rendering strategy</h2>
 * <p>{@code GET /} is now handled by a {@code @Controller} method that returns the
 * Thymeleaf view {@code index}.  Thymeleaf renders the template at request time and
 * injects SEO model attributes into the HTML before it is sent to the client.
 * The complete HTML — including all meta tags, JSON-LD, and the semantic content
 * block — is present in the first HTTP byte, so search engines and AI crawlers
 * (Google, Bing, GPTBot, ClaudeBot, PerplexityBot) can index the page content
 * without executing any JavaScript.</p>
 *
 * <h2>API (unchanged from v1)</h2>
 * <pre>
 *   GET  /              → Thymeleaf SSR HTML shell (index.html template)
 *   POST /new           → JSON game state (starts new game)
 *   POST /command       → JSON game state (executes one command)
 *   GET  /state         → JSON game state (read-only poll)
 *   GET  /robots.txt    → crawl directives + sitemap pointer
 *   GET  /sitemap.xml   → XML sitemap with canonical URL
 * </pre>
 *
 * <h2>Test-framework compatibility (unchanged)</h2>
 * <p>All {@code data-*} attributes, {@code id} values, ARIA roles, and DOM structure
 * are identical to v11.  Playwright, Cypress and Selenium selectors continue to work
 * without modification.</p>
 */
@Controller
@CrossOrigin(origins = "*")
public class HackController {

    private final GameSession      session;
    private final HighScoreService highScoreService;

    /** Canonical base URL — override with environment variable CANONICAL_URL in prod. */
    private static final String BASE_URL =
            System.getenv("CANONICAL_URL") != null
            ? System.getenv("CANONICAL_URL")
            : "https://ruhrohgue.dev";

    public HackController(GameSession session, HighScoreService highScoreService) {
        this.session          = session;
        this.highScoreService = highScoreService;
    }

    // ── SSR landing page ──────────────────────────────────────────────────────

    /**
     * Serves the Thymeleaf-rendered HTML shell.
     *
     * <p>The model attributes injected here appear inside the {@code <head>} and the
     * hidden {@code #seo-content} block.  They are present in the raw HTML before
     * JavaScript runs, making the page fully indexable by crawlers.</p>
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("pageTitle",
                "RuhRohgue, Cloud version of Hack 1.0.2. Copyright © Stichting Mathematisch Centrum, Amsterdam, 1985");
        model.addAttribute("pageDesc",
                "RuhRohgue, Cloud version of Hack 1.0.2. Copyright © Stichting Mathematisch Centrum, Amsterdam, 1985");
        model.addAttribute("h1Title",
                "RuhRohgue, Cloud version of Hack 1.0.2. Copyright © Stichting Mathematisch Centrum, Amsterdam, 1985");
        model.addAttribute("introText",
                "RuhRohgue, Cloud version of Hack 1.0.2. Copyright © Stichting Mathematisch Centrum, Amsterdam, 1985");
        model.addAttribute("canonicalUrl", BASE_URL + "/");
        model.addAttribute("appVersion", "2.0.3");
        model.addAttribute("buildInfo",
                "Spring Boot 3.2.5 · Java 17 · AWS Elastic Beanstalk");
        model.addAttribute("jsonLd", buildJsonLd());
        return "index";
    }

    // ── Game API (unchanged from v1) ──────────────────────────────────────────

    /**
     * Starts a new game.
     *
     * <p><b>Input validation.</b> The player name is validated at this
     * boundary against {@code ^[a-zA-Z0-9]{3}$} before it reaches any storage
     * or rendering layer. The name is persisted to the score store and later
     * rendered on the score board, so an unrestricted three-character field is
     * a stored-XSS and JSON-injection vector, not a harmless nickname box.
     * An invalid name is rejected with {@code 400} and a message the client
     * can display — it is never silently rewritten, because silently accepting
     * a name the player did not choose is its own kind of surprise.</p>
     *
     * <p>The character class is checked against a fixed allow-list rather than
     * a pattern: the set of valid classes is known and closed, so an allow-list
     * is both stricter and clearer than any regular expression.</p>
     */
    @PostMapping(value = "/new", consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> newGame(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "Fighter") String role,
            @RequestParam(required = false) String seed) {

        String candidate = (name == null) ? "" : name.trim();
        if (!NewGameRequest.isValidName(candidate)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "invalid_name");
            error.put("message",
                "Name must be exactly 3 letters or digits (A-Z, a-z, 0-9).");
            return ResponseEntity.badRequest().body(error);
        }
        String safeName = candidate;
        String safeRole = normaliseRole(role);

        // ── Seed resolution ────────────────────────────────────────────────
        // Priority:
        //   1. the "seed" form field typed on the new-game screen
        //   2. the --seed N command-line flag (HackApplication.CONFIGURED_SEED)
        //   3. a fresh System.currentTimeMillis() value (non-reproducible)
        //
        // The chosen value is applied to Dice immediately before any world
        // building happens, and nothing downstream may re-seed: an identical
        // seed therefore reproduces the identical dungeon, the identical
        // item placement and the identical monster spawns.
        long gameSeed;
        boolean explicit = false;
        Long typed = parseSeed(seed);
        if (typed != null) {
            gameSeed = typed;
            explicit = true;
        } else if (hack.HackApplication.CONFIGURED_SEED >= 0) {
            gameSeed = hack.HackApplication.CONFIGURED_SEED;
            explicit = true;
        } else {
            gameSeed = System.currentTimeMillis();
        }
        hack.model.Dice.seed(gameSeed);

        GameState gs = new GameState();
        gs.setSeedInfo(gameSeed, explicit);
        gs.startNewGame(safeName, safeRole);
        LevelGenerator.makeLevel(gs, 1);
        // makeLevel() spawns the floor's monsters inside its deterministic window.
        VisibilityEngine.setSee(gs);
        GameEngine.findAc(gs);

        // Spawn the little dog companion on level 1
        LevelGenerator.spawnDogNearPlayer(gs);
        gs.pline("Little dog says: Ruh Roh.");
        if (explicit) gs.pline("Dungeon seed: " + gameSeed + ".");

        session.setState(gs);
        return ResponseEntity.ok(GameStateSerializer.toMap(gs));
    }

    @PostMapping(value = "/command", consumes = "application/x-www-form-urlencoded")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> command(
            @RequestParam(defaultValue = "") String cmd) {

        GameState gs = session.getState();
        if (gs == null) {
            return ResponseEntity.ok(Map.of("error", "no game"));
        }
        GameEngine.doCommand(gs, cmd);

        // If the game just ended in death, record and return the high score table
        if (gs.getPhase() == hack.model.GameState.Phase.DEAD) {
            hack.model.Player p = gs.getPlayer();
            hack.model.HighScoreEntry entry = new hack.model.HighScoreEntry(
                p.getName(), p.getCharacterClass(),
                gs.getMaxDungeonLevel(), gs.getFinalScore());
            java.util.List<hack.model.HighScoreEntry> top7 =
                highScoreService.addAndGet(entry);
            return ResponseEntity.ok(GameStateSerializer.toMap(gs, top7));
        }
        return ResponseEntity.ok(GameStateSerializer.toMap(gs));
    }

    @GetMapping("/state")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> state() {
        GameState gs = session.getState();
        if (gs == null) {
            return ResponseEntity.ok(Map.of("error", "no game"));
        }
        return ResponseEntity.ok(GameStateSerializer.toMap(gs));
    }

    // ── Developer: view / clear the Magnificent 7 high-score file ──────────
    //
    // GET  /dev/scores         → returns the current high-score list as JSON
    // DELETE /dev/scores       → clears the list (wipes the persisted file)
    //
    // These endpoints are intentionally simple and unauthenticated — the game
    // is a single-server installation and the developer runs it locally or on
    // a private AWS instance. Use Spring Security if public exposure is needed.

    @GetMapping("/dev/scores")
    @ResponseBody
    public ResponseEntity<java.util.List<hack.model.HighScoreEntry>> devGetScores() {
        return ResponseEntity.ok(highScoreService.topN());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/dev/scores")
    @ResponseBody
    public ResponseEntity<String> devClearScores() {
        highScoreService.clearAll();
        return ResponseEntity.ok("High score list cleared.");
    }

    // ── robots.txt ────────────────────────────────────────────────────────────

    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String robotsTxt() {
        return "User-agent: *\n"
             + "Allow: /\n"
             + "\n"
             + "Sitemap: " + BASE_URL + "/sitemap.xml\n";
    }

    // ── sitemap.xml ───────────────────────────────────────────────────────────

    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    @ResponseBody
    public String sitemapXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
             + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n"
             + "  <url>\n"
             + "    <loc>" + BASE_URL + "/</loc>\n"
             + "    <changefreq>weekly</changefreq>\n"
             + "    <priority>1.0</priority>\n"
             + "  </url>\n"
             + "</urlset>\n";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses the optional seed field from the new-game form.
     *
     * <p>Accepts any signed 64-bit integer.  A blank field, whitespace, or a
     * non-numeric value means "no seed given" and yields {@code null} rather
     * than an error, so a typo simply falls back to a random game.</p>
     *
     * @param raw the raw form value (may be null)
     * @return the parsed seed, or {@code null} when none was supplied
     */
    /** The character classes the game actually implements. */
    private static final List<String> VALID_ROLES = List.of(
        "Fighter", "Cave-man", "Tourist", "Wizard", "Knight", "Speleologist");

    /**
     * Maps a submitted role onto the allow-list.
     *
     * <p>An allow-list rather than a pattern: the set is closed and known, so
     * anything outside it is a mistake or an attack, and either way the safe
     * response is the default class rather than passing the value onward.</p>
     *
     * @param role submitted character class
     * @return a role guaranteed to be in {@link #VALID_ROLES}
     */
    static String normaliseRole(String role) {
        if (role == null || role.isBlank()) return "Fighter";
        String trimmed = role.trim();
        for (String valid : VALID_ROLES) {
            if (valid.equalsIgnoreCase(trimmed)) return valid;
        }
        return "Fighter";
    }

    static Long parseSeed(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }


    /** Builds the JSON-LD VideoGame structured-data block as a Java string. */
    private String buildJsonLd() {
        return "{\n"
             + "  \"@context\": \"https://schema.org\",\n"
             + "  \"@type\": \"VideoGame\",\n"
             + "  \"name\": \"RuhRohgue\",\n"
             + "  \"alternateName\": [\"RuhRohgue, Cloud version of Hack 1.0.2. Copyright © Stichting Mathematisch Centrum, Amsterdam, 1985\", \"Hack 1.0.2\"],\n"
             + "  \"description\": \"RuhRohgue, Cloud version of Hack 1.0.2. Copyright © Stichting "
             + "Mathematisch Centrum, Amsterdam, 1985\",\n"
             + "  \"url\": \"" + BASE_URL + "/\",\n"
             + "  \"genre\": [\"Roguelike\", \"Dungeon crawler\", \"Turn-based\"],\n"
             + "  \"playMode\": \"SinglePlayer\",\n"
             + "  \"operatingSystem\": \"Web Browser\",\n"
             + "  \"applicationCategory\": \"Game\",\n"
             + "  \"inLanguage\": \"en\",\n"
             + "  \"datePublished\": \"1985\",\n"
             + "  \"publisher\": { \"@type\": \"Organization\", \"name\": \"CWI Amsterdam\" },\n"
             + "  \"offers\": { \"@type\": \"Offer\", \"price\": \"0\", "
             + "\"priceCurrency\": \"USD\", \"availability\": \"https://schema.org/InStock\" }\n"
             + "}\n";
    }
}
