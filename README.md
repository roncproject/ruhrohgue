# 🕹️ RuhRohgue - Cloud Edition

**RuhRohgue** is a vibe-coded java port of the **Hack 1.0.2** roguelike
(CWI Amsterdam, 1985)

🚀 **Live Demo:** [https://ruhrohgue.dev](https://ruhrohgue.dev)

## Features
* **Game Engine variable:** Enter seed in start menu to get a deterministic dungeons, or omit for random.

---

## Quick start — local

```bash
# Build
mvn clean package

# Run — opens on http://localhost:8080
java -jar target/ruhrohgue.jar

# Or with Maven (same port)
mvn spring-boot:run
```

Open **http://localhost:8080** in your browser.

> **Port note:** the default port is **8080** — the standard Spring Boot
> convention.  You do NOT need to pass any flag locally.
> AWS Elastic Beanstalk overrides it to 5000 automatically via the Procfile.

---

## Reproducible dungeons — the Seed field

The new-game screen has an optional **Seed** field. Enter any whole number and
RuhRohgue will build exactly the same dungeon every time: the same rooms and
corridors, the same stairs, the same items and gold in the same places, and the
same monsters in the same starting positions.

Leave the field blank for a different dungeon each game.

A seed can also be fixed for the whole server:

```bash
java -jar target/ruhrohgue.jar --seed 42
```

Precedence is: **Seed field** → **`--seed` flag** → random (current time).
When a seed is in force, the message log opens with `Dungeon seed: N.` so you
can confirm which value was used.

---

## Override the port if needed

```bash
# Use a different port (e.g. 9090):
java -jar target/ruhrohgue.jar --server.port=9090

# Or via environment variable:
SERVER_PORT=9090 java -jar target/ruhrohgue.jar  # Linux / macOS
set SERVER_PORT=9090 && java -jar target/ruhrohgue.jar  # Windows CMD
$env:SERVER_PORT=9090; java -jar target/ruhrohgue.jar  # PowerShell
```

---

## Player records (high-score table)

Results are appended to a **JSON Lines** file — one JSON object per line:

```
{"name":"T01","class":"Fighter","floor":3,"score":1250,"time":"2026-09-15 14:32"}
```

**Where it lives.** By default `<java.io.tmpdir>/ruhrohgue-scores.json`:

| OS | Default path |
|---|---|
| Linux / macOS | `/tmp/ruhrohgue-scores.json` |
| Windows | `%TEMP%\ruhrohgue-scores.json` (e.g. `C:\Users\you\AppData\Local\Temp\`) |

The exact path in use is printed to the console at startup:

```
[RuhRohgue] Player records file: /tmp/ruhrohgue-scores.json
```

**Put it somewhere permanent.** `/tmp` is cleared on reboot, so for a table you
want to keep, point it at a real file:

```bash
java -jar target/ruhrohgue.jar -Druhrohgue.scores.file=./scores.json
```

**Editing it by hand.** Stop the server, open the file in any text editor, and
keep to these rules:

- one complete JSON object per line, no line breaks inside an object
- no enclosing `[ ]` array and no trailing commas
- `floor` and `score` are numbers; `name`, `class` and `time` are quoted strings
- delete a line to remove an entry; delete the whole file to reset the table

Entries are re-sorted by score when loaded, so lines need not be in order. Only
the top 100 survive the next save.

---

## Architecture

```
Browser (Vanilla HTML/CSS/JS + Service Worker)
  │  POST /new       → start game  (name, role, optional seed)
  │  POST /command   → send keypress
  │  GET  /state     → read state
  ▼
HackController  (@RestController)
  ▼
GameSession  (@SessionScope — one GameState per browser tab)
  ▼
GameState / GameEngine / ...  (original Hack 1.0.2 logic)
```

---

## PWA Features

| Feature | Detail |
|---|---|
| Installable | `manifest.json` + `beforeinstallprompt` handler |
| Offline shell | Service worker caches `index.html`, icons, manifest |
| Offline queue | Keypresses buffered; replayed when network returns |
| Touch d-pad | Shown on `pointer:coarse` devices |
| Health check | `GET /actuator/health` → `{"status":"UP"}` |

---

## AWS Elastic Beanstalk deployment

```bash
mvn clean package -DskipTests
cp target/ruhrohgue.jar .
zip -r ruhrohgue-deploy.zip ruhrohgue.jar Procfile .ebextensions/

aws elasticbeanstalk create-application-version \
  --application-name "ruhrohgue" \
  --version-label    "v2.0.1" \
  --source-bundle     S3Bucket=YOUR_BUCKET,S3Key=ruhrohgue-deploy.zip

aws elasticbeanstalk update-environment \
  --environment-name "ruhrohgue-env" \
  --version-label    "v2.0.1"
```

The Procfile (`web: java -jar ruhrohgue.jar --server.port=5000`) overrides the
port to 5000, which EB's reverse proxy maps to public port 80.

---

## Project structure

```
ruhrohgue/
├── src/main/java/hack/
│   ├── HackApplication.java          ← Spring Boot entry point
│   ├── engine/                       ← Game engine
│   ├── model/                        ← Game model
│   └── web/
│       ├── HackController.java       ← REST: POST /new /command GET /state
│       ├── GameSession.java          ← @SessionScope state per browser
│       └── GameStateSerializer.java  ← GameState → Map → Jackson JSON
├── src/main/resources/
│   ├── application.properties        ← server.port=8080 (local default)
│   ├── templates/index.html          ← Thymeleaf shell
│   └── static/
│       ├── js/hack.js                ← Vanilla JS PWA frontend
│       ├── manifest.json             ← PWA manifest
│       ├── sw.js                     ← Service worker
│       └── icons/
├── Dockerfile
├── Procfile                          ← EB: --server.port=5000
└── .github/workflows/deploy.yml
```

> **Note on the `hack` Java package.** The package name, the `HackApplication` /
> `HackController` class names and the `hack.*` property prefixes deliberately
> keep the original Hack lineage. They are internal identifiers and are never
> shown to players — everything player-facing reads **RuhRohgue**.

---

## Licence, attribution and AI disclosure

RuhRohgue is a Java adaptation of **Hack 1.0.2**, originally
`Copyright (c) Stichting Mathematisch Centrum, Amsterdam, 1985`, released for
free modification and redistribution.

- **Licence:** MIT — see [`LICENSE`](LICENSE)
- **Attribution and provenance:** see [`NOTICE.md`](NOTICE.md)
- **AI-assisted development:** this project was built with AI assistance
  ("vibe coding"). Large parts of the Java port, the front end, the tests and
  the documentation were generated or substantially revised by an AI coding
  assistant working from the original C sources and human-authored
  requirements. This is disclosed in full in [`NOTICE.md`](NOTICE.md), along
  with what it means for how the code should be reviewed.

---

## Player records — storage

Elastic Beanstalk containers are **ephemeral**. Anything written inside the
container is destroyed on scale-in, scale-out and every deployment, and two
instances behind the load balancer keep two divergent tables.

| Setting | Durability | Use |
|---|---|---|
| `store=file`, `dir=<EFS mount>` | Durable, shared across instances | Production (Option A) |
| `store=s3`, `s3.bucket=<name>` | Durable, no volume to mount | Production (Option B) |
| `store=file`, `dir=<tmpdir>` | **Wiped on redeploy** | Local development only |

```bash
# Local development (default) — records in the temp directory
java -jar target/ruhrohgue.jar

# Durable: mounted EFS volume
SCORES_DIR=/mnt/efs/ruhrohgue java -jar target/ruhrohgue.jar

# Durable: S3 bucket, credentials from the EC2 instance profile
SCORES_STORE=s3 SCORES_S3_BUCKET=my-bucket java -jar target/ruhrohgue.jar
```

The resolved location is logged at startup, with a warning when it points at
ephemeral container storage.

**Never commit or bake a records file into the image.** `.gitignore` excludes
`*-scores.json` for that reason.

---

## Security posture

| Vector | Control |
|---|---|
| Stored XSS / JSON injection | Player name validated at the controller boundary against `^[a-zA-Z0-9]{3}$`; character class checked against a closed allow-list |
| Concurrent write corruption | `ReentrantReadWriteLock` around the in-memory table; atomic temp-file-plus-move in the store |
| Path traversal | `SafePaths` resolves and normalises before confirming the result is still inside the permitted directory |
| Denial of service | Per-client token-bucket rate limiting (120 requests/minute by default); request size limits; health check exempt |
| Information disclosure | Stack traces, exception types and binding errors suppressed in error responses |

---

## Local performance (Windows)

Two settings govern how responsive the game feels when run locally. Both were
tuned in v6b.

### Request rate limit

Every keypress is a `POST /command`. The limiter therefore sits directly in the
input path:

```properties
ruhrohgue.ratelimit.capacity=600        # was 120
ruhrohgue.ratelimit.window-seconds=60
```

600 requests per minute is 10 per second — comfortably above a human roguelike
movement loop. At the previous 120 (2 per second) a player moving briskly hit
the limit within seconds and received HTTP 429, which felt exactly like the
keyboard lagging or locking up.

Override per environment without rebuilding:

```powershell
$env:RATELIMIT_CAPACITY = "900"
java -jar target\ruhrohgue.jar
```

Static assets, `/robots.txt`, `/sitemap.xml` and `/actuator/health` no longer
consume tokens at all.

### Windows Defender exclusion

The score store writes to a temporary file and then moves it into place with
`StandardCopyOption.ATOMIC_MOVE`. Real-time antivirus scanning opens each new
file as it appears, which can briefly lock it and stall the move — and that
stall happens on a request thread.

Add the project directory to Real-Time Scan exclusions:

**Settings → Privacy & security → Windows Security → Virus & threat protection
→ Manage settings → Add or remove exclusions → Add an exclusion → Folder**

Select the project folder, for example `C:\dev\project\webhack\ruhrohgue_v6`.

Or from an **elevated** PowerShell:

```powershell
Add-MpPreference -ExclusionPath "C:\dev\project\webhack\ruhrohgue_v6"
```

Verify:

```powershell
(Get-MpPreference).ExclusionPath
```

This is a development convenience on a trusted directory. It is not needed on
the Linux container used in production, and excluding a folder you do not
control is not advisable.
