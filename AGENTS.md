# AGENTS.md — RuhRohgue Cloud Edition

## Project identity
**RuhRohgue** is a vibe-coded port of Hack 1.0.2 (CWI Amsterdam, 1985) running as a
Spring Boot 3 progressive web app deployed on AWS Elastic Beanstalk via Docker.
The game is a turn-based ASCII roguelike served as a single-page app over HTTP.

## Technology stack
| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 (embedded Tomcat, Thymeleaf SSR shell) |
| Build | Maven 3 (`mvn clean package` → fat JAR) |
| Container | Docker (single-stage, `eclipse-temurin:17-jre`) |
| Deployment | AWS Elastic Beanstalk — Docker platform, `Procfile` sets port 5000 |
| Frontend | Vanilla JS (ES5-compatible), no build step, served from `classpath:/static/` |
| PWA | `sw.js` service worker, Web App Manifest |
| Persistence | JSON-Lines file on disk (`/tmp/ruhrohgue-scores.json` default, configurable) |
| Session | Spring HTTP session (4-hour timeout, single-server) |

## Project layout
```
src/main/java/hack/
  HackApplication.java        — @SpringBootApplication entry point; --seed flag
  engine/
    GameEngine.java           — doCommand() dispatcher; all game commands
    CombatEngine.java         — player/monster attack formulas
    LevelGenerator.java       — procedural dungeon, shop, bank, food placement
    MonsterEngine.java        — monster AI, movement, death, restoreCell
    TrapEngine.java           — trap effects
    VisibilityEngine.java     — canSee(), setSee(), field-of-view
  model/
    Dice.java                 — central RNG (java.util.Random); seed() for reproducibility
                              NOTE: seeded exactly once per game, by HackController
    GameState.java            — full game state including Player, DungeonLevel
    Player.java               — stats, inventory, unpaidItems, bank
    Item.java / ItemClass.java / ItemTable.java — item definitions and cost
    MonsterPrototype.java     — monster data table
    DungeonLevel.java / Cell.java / Room.java — map representation
    HighScoreEntry.java       — score record (JSON-serializable)
    GoldPile.java             — gold + shop food markers (negative amount = food)
  web/
    HackController.java       — REST endpoints: /new /command /state /dev/scores
    GameSession.java          — @SessionScope state holder
    GameStateSerializer.java  — GameState → JSON for the JS client
    HighScoreService.java     — persist/load Magnificent 7; clearAll()

src/main/resources/
  application.properties      — port, session, logging, score file path
  templates/index.html        — Thymeleaf shell (one-page app)
  static/js/hack.js           — game client (overlays, renderMap, applyState)
  static/sw.js                — service worker

src/test/java/hack/           — JUnit 5 tests (Baeldung conventions)
Dockerfile                    — Docker build for EB
Procfile                      — EB: web: java -jar ... --server.port=5000
AGENTS.md                     — this file
CLAUDE.md                     — AI agent instructions (references this file)
```

## Key architectural decisions
- **Server-side state**: The entire game state lives in an HTTP session on the server.
  The JS client is stateless — it renders what the server sends and posts commands.
- **Negative GoldPile amounts = shop food markers**: `amount < 0` encodes a food
  typeIndex; these are excluded from `goldList()` in the serializer and rendered via
  cell `scrsym='%'` from `mapGrid()`.
- **Dice.seed()**: All randomness goes through `Dice`. A seed can be supplied per
  game in the **Seed** field on the new-game screen (`POST /new` form field `seed`),
  or globally with `--seed N` at startup. Seed precedence is: form field → `--seed`
  → `System.currentTimeMillis()`. The RNG is seeded once in `HackController.newGame()`
  immediately before world building; **nothing downstream may re-seed `Dice`**, or
  reproducibility breaks silently. `GameState.startNewGame()` used to re-seed from
  the wall clock — that bug is fixed and must not return.
- **UI signals are one-shot**: bracketed control strings (`[look:…]`, `[shoptab:…]`,
  `[bank:pickup:…]`, chooser prompts) are routed by `GameState.pline()` into a
  transient `uiSignal` field instead of the message log, and serialised as
  `"signal"` via `consumeSignal()`, which clears it on read. Never put a bracketed
  string into the visible message log — it would stick at `messages[0]` and replay
  its overlay on every later state refresh.
- **Shop tab**: Items picked up in shops go to `Player.unpaidItems`. The `p` command
  opens the shop tab overlay. Shopkeeper blocks exit until tab is paid.
- **Bank interest**: Silent, every 100 turns, `max(1, bank/100)` gold added.

## Running locally
```bash
mvn clean package -DskipTests
java -jar target/ruhrohgue.jar                  # random seed
java -jar target/ruhrohgue.jar --seed 42        # reproducible seed
java -jar target/ruhrohgue.jar -Druhrohgue.scores.file=./scores.json  # editable scores
```
Open http://localhost:8080

## Score file
Default: `<java.io.tmpdir>/ruhrohgue-scores.json` (Windows: `%TEMP%\ruhrohgue-scores.json`).
Plain JSON-Lines format — one record per line. Edit with any text editor.
Clear via: `curl -X DELETE http://localhost:8080/dev/scores`
Override path: `java -jar ruhrohgue.jar -Druhrohgue.scores.file=/path/to/scores.json`

## Logging
```bash
java -jar ruhrohgue.jar --logging.level.hack=DEBUG         # game engine
java -jar ruhrohgue.jar --logging.level.root=DEBUG         # full Spring trace
java -jar ruhrohgue.jar > game.log 2>&1                    # save to file
```

## Common pitfalls for AI agents
1. **Do not use `localStorage`** in JS artifacts — not supported in this environment.
2. **`Dice.seed()`** must be called before Spring context boots (done in `main()`).
3. **`shopTabEl`** and all shop tab JS vars must be declared before the capture-phase
   keydown listener — missing declarations cause silent `ReferenceError` that freezes
   all keyboard input.
4. **`itemClass.cost`** is a computed field in `ItemClass` — derived from `multiValue`
   and `symbol`, not stored in `ItemTable.set()` parameters.
5. **Open doors use `scrsym=' '`** (space), not `':'`. The `':'` key is reserved for
   the look-here command.
6. **Serializer slot letters** always match the raw inventory index (`'a'` = `inv[0]`).
   Strange objects (ILLOBJ_SYM `'\'`) get `strange:true` flag; JS filters them from
   display but the slot letter must remain correct for `findBySlot()` on the server.
7. **Session timeout** is 4 hours. Tab-freeze after idle is handled by a
   `visibilitychange` listener that calls `GET /state` on tab reveal.
