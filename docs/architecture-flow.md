# App Flow and Design Brief — RuhRohgue Cloud Edition

| | |
|---|---|
| **Applies to** | v3.2.x |
| **Related** | [prd.md](prd.md) · [technical-requirements.md](technical-requirements.md) · [backend-schema.md](backend-schema.md) |

There are no Figma or Miro boards for this project. The diagrams below are the
design record. They are Mermaid, which GitHub renders natively, so they stay in
step with the code in review.

## 1. User journey

The whole game is one page. There is no routing, no login and no state in the
URL.

```mermaid
flowchart TD
    A([Open ruhrohgue.dev]) --> B{Service worker<br/>cached shell?}
    B -- first visit --> C[Load page from server]
    B -- repeat visit --> D[Load shell from cache]
    C --> E
    D --> E[New-game screen]
    E --> F[Enter 3-character name,<br/>pick role, optional seed]
    F --> G{Name valid?}
    G -- no --> E
    G -- yes --> H[Game screen:<br/>map, status panel, messages, inventory]
    H --> I{Player action}
    I -- move / fight / use item / shop / bank --> H
    I -- HP reaches 0 --> J[Game-over screen<br/>score, class, floor, killer, moves]
    I -- Q then y, quit --> Q["Game-over screen: You Quit<br/>nothing recorded"]
    I -- climb the stairs on floor 1 --> X["Game-over screen: You Escaped<br/>nothing recorded"]
    J --> K[Magnificent 7 table]
    K --> L{Play again?}
    Q --> L
    X --> L
    L -- yes --> E
    L -- no --> M([Leave])
```

Banners appear on top of the game and sit above the modal layer so they stay
clickable:

- **Install banner**, offered by the browser when the PWA is installable.
- **Privacy banner**, shown once, dismissal remembered in local storage.
- **Offline banner**, shown while the browser reports no connection.

## 2. State management

### 2.1 Server-side game phases

The server holds the single source of truth. The client is stateless: it renders
what it receives and posts commands.

```mermaid
stateDiagram-v2
    [*] --> SETUP: GameState created
    SETUP --> PLAYING: startNewGame
    PLAYING --> PLAYING: command accepted
    PLAYING --> DEAD: HP reaches 0
    PLAYING --> QUIT: quit confirmed (Q, then y)
    PLAYING --> ESCAPED: climb the stairs on floor 1
    DEAD --> DEAD: further commands ignored,<br/>table returned, nothing recorded
    DEAD --> [*]: new game replaces the state
    QUIT --> [*]: new game replaces the state
    ESCAPED --> [*]: new game replaces the state
```

The score is recorded **once**, on the first response after the game becomes
`DEAD`. Only `DEAD` is recorded today: a game that ends in `QUIT` or `ESCAPED`
shows its game-over screen but adds nothing to the table. Commands that were already in flight at the moment of death still reach
the server; they receive the table but do not add to it
([TRK-013](https://github.com/roncproject/ruhrohgue/issues/20)).

### 2.2 Client states

| Condition | What the client does |
|---|---|
| No game yet | Shows the new-game screen |
| `phase = PLAYING` | Sends a command per keypress, redraws from the response |
| `phase = DEAD`, `ESCAPED` or `QUIT` | Stops sending keypresses and shows the game-over screen (`highScores` accompany `DEAD`) |
| Network failure | Offline banner; keypresses are queued and replayed when the connection returns |
| Tab hidden then revealed | Calls `GET /api/v1/state` to resynchronise |
| Rate limited (`429`) | Retries once after the interval in `Retry-After`, then drops the keypress quietly |

Session scope: one browser session is one game. Two tabs in the same browser
share it; two different browsers play two independent games, each with its own
random-number stream.

## 3. System architecture

### 3.1 Components

```mermaid
flowchart LR
    subgraph Browser
        UI[hack.js<br/>single-page client]
        SW[Service worker<br/>cache + offline]
    end
    CF[Cloudflare<br/>TLS, WAF, bot rules]
    subgraph AWS["AWS, Elastic Beanstalk (single instance)"]
        NG["nginx on the instance,<br/>then nginx in the container (port 80)<br/>forwarding to the game on 8081"]
        subgraph DC["Docker container"]
            F1[RequestSizeLimitFilter]
            F2[RateLimitFilter]
            C[HackController]
            E[Game engine]
            S[GameSession<br/>in-memory state]
            H[HighScoreService]
        end
    end
    S3[(Amazon S3<br/>one score object)]

    UI <--> SW
    SW --> CF
    CF --> NG --> F1 --> F2 --> C
    C --> E --> S
    C --> H --> S3
```

### 3.2 Request and response loop

Every action is a small JSON request. All endpoints are under `/api/v1`.

```mermaid
sequenceDiagram
    autonumber
    participant B as Browser
    participant CF as Cloudflare
    participant API as Spring Boot
    participant HS as HighScoreService
    participant S3 as Amazon S3

    B->>CF: POST /api/v1/command {"cmd":"l"}
    CF->>API: forwarded (CF-Connecting-IP set)
    API->>API: size limit, then rate limit
    API->>API: bind this session's dice, run the command
    alt game still playing
        API-->>B: 200 game state
    else the game has just ended
        API->>HS: record the death (first time only)
        HS->>S3: PUT score object
        HS-->>API: top 7
        API-->>B: 200 game state + highScores
    else already dead
        API->>HS: read top 7
        API-->>B: 200 game state + highScores
    end
```

### 3.3 Start-up

On start, `HighScoreService` reads the score object once from S3 into memory. A
missing object is treated as an empty table. A read error is logged and the
table starts empty, so a storage problem cannot stop the game from booting.

### 3.4 Failure behaviour

| Failure | Result |
|---|---|
| S3 unreachable at start | Logged; the table starts empty and the next save will try again |
| S3 write fails | Logged; the game continues, the entry remains in memory until restart |
| Instance restarts | Games in progress are lost; the score table is reloaded from S3 |
| Rate limit exceeded | `429` with `Retry-After` |
| Oversized body | `413` |
| Invalid player name | `400` with `error: invalid_name` |

## 4. Design principles

- **Server authoritative.** The browser never decides an outcome.
- **One-shot UI signals.** Overlays such as shops and the look command are driven
  by a transient `signal` field that is cleared when read, so a state refresh
  never replays an old overlay.
- **Reproducible.** The same seed produces the same dungeon, items and monsters.
- **Small, boring operations.** One instance, one object in storage, one JAR.
- **Fail soft.** A storage hiccup should degrade the score table, not the game.
