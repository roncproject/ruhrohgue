# Product Requirements Document — RuhRohgue Cloud Edition

| | |
|---|---|
| **Status** | Living document, in development ("no guarantees", see the README) |
| **Applies to** | v3.2.x |
| **Related** | [technical-requirements.md](technical-requirements.md) · [architecture-flow.md](architecture-flow.md) · [backend-schema.md](backend-schema.md) |

## 1. Objective

RuhRohgue is a Java port of **Hack 1.0.2** (Stichting Mathematisch Centrum,
Amsterdam, 1985), a turn-based ASCII roguelike, delivered as a web game that
runs in any modern browser and installs as a progressive web app (PWA).

The product exists to answer one question: *can a 1985 game be kept faithful to
its rules and still be pleasant to open on a phone, with nothing to install and
no account to create?*

## 2. Problem statement

- The original game needs a Unix-era toolchain to build and run. Most people who
  would enjoy it will never get it running.
- Modern roguelikes are large, require accounts or downloads, and track their
  players.
- A casual player wants to open a link, type a three-letter name and be in a
  dungeon within seconds.

## 3. User personas

These are working assumptions, not research findings.

| Persona | Wants | Implication |
|---|---|---|
| **Curious visitor** | Try it in seconds, on a phone or desktop, with no sign-up | Zero-friction start, on-screen touch controls, no account |
| **Roguelike veteran** | The original rules, reproducible runs, a readable score table | Faithful mechanics, an optional dungeon **seed**, a persistent "Magnificent 7" |
| **Privacy-minded player** | No tracking, no cookie wall | No analytics, no third-party scripts, privacy notice ([WISH-04](https://github.com/roncproject/ruhrohgue/issues/15)) |
| **Maintainer** (the project owner) | Cheap to run, safe to change, easy to deploy | One small instance, automated tests, a single version source (`pom.xml`) |

## 4. Scope and core features (MoSCoW)

### Must have

- Play a complete game in the browser: new game, dungeon exploration, combat,
  items, shops, death.
- Three-character player name and a choice of role (Fighter, Cave-man, Tourist,
  Wizard, Knight, Speleologist).
- Server-side game state: the browser renders what the server sends.
- A persistent high-score table ("Magnificent 7") that survives redeploys.
- Each death is recorded exactly once ([TRK-013](https://github.com/roncproject/ruhrohgue/issues/20)).
- Abuse limits: request-rate and request-size caps.

### Should have

- Installable PWA with an offline shell and an offline banner.
- Optional dungeon seed for reproducible games.
- Touch controls for phones.
- A zero-cookie privacy notice.
- Automated tests at three levels: unit, API and browser end-to-end.

### Could have

- A legal / imprint page ([WISH-04](https://github.com/roncproject/ruhrohgue/issues/15)),
  using [`PRIVACY-LEGAL-TEMPLATE.md`](../PRIVACY-LEGAL-TEMPLATE.md) as a starting
  point. The template is a reference only, not legal text.
- Content-Security-Policy and related headers ([WISH-01](https://github.com/roncproject/ruhrohgue/issues/9)).
- Push-button deployment from GitHub ([WISH-02](https://github.com/roncproject/ruhrohgue/issues/10)).

### Won't have (for now)

- Player accounts, logins or profiles.
- Saving a game and resuming it on another device.
- Multiplayer.
- Analytics or advertising.

## 5. User stories

- As a **visitor**, I want to enter three letters and start playing so that I can
  try the game without signing up.
- As a **phone player**, I want on-screen movement controls so that I can play
  without a keyboard.
- As a **returning player**, I want my score to stay on the table after I die so
  that I can compare runs over time.
- As a **veteran**, I want to enter a seed so that I can replay the same dungeon.
- As a **privacy-minded player**, I want to know what the site stores about me so
  that I can decide whether to play.
- As a **player on a bad connection**, I want to be told when I am offline so
  that I do not think the game froze.
- As the **maintainer**, I want one version number and repeatable tests so that a
  release is a small, reviewable change.

## 6. Success metrics

This is a hobby project, so the targets below are practical rather than
commercial. They are proposals to be confirmed by the maintainer.

| Metric | How it is checked | Target |
|---|---|---|
| Regression safety | Unit, API and e2e suites on every change | All green before merge |
| Score integrity | One table row per death; scores survive a redeploy | No duplicates, no loss |
| Availability | `/actuator/health` and a manual playtest after each deploy | Health `UP` after every deploy |
| Running cost | AWS Billing and Cost Management | Roughly one small instance plus negligible storage |
| Privacy | No cookies set for tracking, no third-party requests | Stays true |

## 7. Known limitations and open work

Tracked as GitHub issues; see the issue list for the current state.

- Starvation death is dead code ([TRK-011](https://github.com/roncproject/ruhrohgue/issues/17)).
- Cloudflare can block `manifest.json` and POST requests in some configurations
  ([TRK-010](https://github.com/roncproject/ruhrohgue/issues/14)).
- The game session lives in server memory. A deploy or restart ends games in
  progress. Scores are not affected.
- The score table is shared by every player; there is no per-player history.
- The public demo runs with a fixed dungeon seed (20), so every new game starts in
  the same dungeon. This is deliberate. Players can type their own seed to get a
  different one.
- Only a death is recorded. A game ended by quitting, or by climbing out of the
  dungeon on floor 1 ("You Escaped"), shows its game-over screen but adds no
  entry. Whether an escape should count is a product decision that has not been
  made.

## 8. Attribution

RuhRohgue is an adaptation of Hack 1.0.2 and keeps its original copyright notice.
It was built with AI assistance. See [`NOTICE.md`](../NOTICE.md) and the README
for the licence, provenance and the AI disclosure.
