# Technical Requirements — RuhRohgue Cloud Edition

| | |
|---|---|
| **Applies to** | v3.2.x |
| **Related** | [prd.md](prd.md) · [architecture-flow.md](architecture-flow.md) · [backend-schema.md](backend-schema.md) · [`AGENTS.md`](../AGENTS.md) |

This document bridges the product goals in [prd.md](prd.md) to what is actually
built and deployed. Where something is a target rather than a fact, it says so.

## 1. Tech stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 (embedded Tomcat, Thymeleaf for the HTML shell) |
| Build | Maven; `mvn clean package` produces one executable JAR (`ruhrohgue.jar`) |
| Frontend | Vanilla JavaScript, no build step, served from `classpath:/static/` |
| PWA | `sw.js` service worker and a Web App Manifest |
| Container | Docker image built by the separate **Multideplo** deployment repository: Amazon Corretto 17 (Alpine) with nginx and the application JAR. This repository's own `Dockerfile` is for standalone use (see §2.4) |
| Persistence | A single score file, stored on local disk or in Amazon S3 (see [backend-schema.md](backend-schema.md)) |
| Observability | Spring Boot Actuator, `health` and `info` only |
| Tests | JUnit 5 + Mockito (unit), Postman/newman (API), Playwright + Cucumber (browser e2e) |
| External services | AWS (Elastic Beanstalk, S3, IAM), Cloudflare (DNS, proxy, WAF) |

The application talks to AWS through the AWS SDK for Java v2 (S3 client only).

## 2. Infrastructure requirements

### 2.1 Hosting

| Component | Choice |
|---|---|
| Compute | AWS Elastic Beanstalk, **Docker** platform on Amazon Linux 2023 |
| Environment type | **Single instance**, no load balancer |
| Proxies | nginx on the instance (the Elastic Beanstalk default), then a second nginx inside the container |
| Ports | The container listens on **80** (nginx). The game runs on **8081** inside it and nginx forwards `/`, `/api/` and `/app1/` to it. Locally the app defaults to 8080 |
| Packaging | One container can hold more than one application. The image has room for a second JAR (`app2.jar`, routed under `/app2/`), but only the game is started today |
| Region | An EU region |
| Instance size | A small general-purpose instance, sized with headroom for a second small app. Resize later from the environment's capacity settings |
| Score storage | One S3 bucket, one object |
| Public front door | Cloudflare, proxying `ruhrohgue.dev` |

A single instance keeps the monthly cost low and is deliberate. It has two
consequences that the rest of this document accounts for:

1. Game sessions live in the instance's memory and end on restart or deploy.
2. The score table is held in memory and loaded once at startup, so running
   **more than one instance would make each keep its own copy** and the last
   writer to S3 would win. Do not scale out without first changing this
   (see [backend-schema.md](backend-schema.md), "Concurrency").

### 2.2 Configuration (environment properties)

Configuration is injected as Elastic Beanstalk environment properties. None of
them is a secret except the admin token.

| Property | Purpose | Default |
|---|---|---|
| `SCORES_STORE` | `file` or `s3` | `file` |
| `SCORES_S3_BUCKET` | Bucket name, required when the store is `s3` | empty |
| `SCORES_S3_KEY` | Object key | `ruhrohgue/scores.json` |
| `SCORES_S3_REGION` | Region of the bucket | provider default |
| `SCORES_DIR`, `SCORES_FILE` | Directory and file name for the file store | temp dir, `ruhrohgue-scores.json` |
| `ADMIN_TOKEN` | Shared secret for `DELETE /dev/scores`. **Unset means the route is locked for everyone** | empty |
| `RATELIMIT_ENABLED`, `RATELIMIT_CAPACITY`, `RATELIMIT_WINDOW` | Rate limiter | `true`, `600`, `60` |
| `REQUEST_MAX_BODY_BYTES` | Request body cap | `1024` |
| `CANONICAL_URL` | Base URL for the sitemap and metadata | `https://ruhrohgue.dev` |

If `SCORES_STORE=s3` is set without a bucket, the app logs a loud warning and
falls back to the file store rather than refusing to start.

### 2.3 AWS identity and access

- The application has **no access keys**. It uses the default AWS credential
  chain, which on Elastic Beanstalk means the EC2 instance profile.
- The instance profile needs only what the score store uses: read, write and
  delete on the one object, plus `s3:ListBucket` on the bucket. `ListBucket` is
  required, because without it S3 answers 403 instead of 404 for a missing
  object and the first run fails instead of starting an empty table.
- Do not grant wildcard bucket access.

### 2.4 Deployment (CI/CD)

**Two repositories, two responsibilities:**

| Repository | Owns |
|---|---|
| This repository (`ruhrohgue`) | The application: source, tests, the version in `pom.xml`, and the JAR that `mvn clean package` produces |
| Multideplo | How it is shipped: the production `Dockerfile`, `nginx.conf`, `entrypoint.sh`, `Dockerrun.aws.json`, and the GitHub Actions that place the JAR in the bundle as `app1.jar` |

**Multideplo leads for deployment.** What runs in production is defined there, not by this repository's `Dockerfile` or `Procfile`. Those two files are for running the game standalone (for example `docker run` on a laptop) and do not affect production.

Things the two sides must agree on:

- The JAR (this repository produces it, Multideplo consumes it as `app1.jar`).
- The port the game listens on (production passes `-Dserver.port=8081`; the `Procfile`'s 5000 is not used).
- The environment properties in §2.2, which are set on the Elastic Beanstalk environment, not in either repository.
- The JVM arguments and game arguments in `entrypoint.sh`: heap capped at 512 MB, and the demo seed below.

**Demo seed.** Production starts the game with a fixed dungeon seed of 20, so every new game begins in the same dungeon. This is deliberate, because it makes a good demo. A player can still type their own seed on the new-game screen, and a typed seed wins over the fixed one. Removing `--SEED 20` from `entrypoint.sh` returns to a random dungeon per game.

- A deployment bundle is the Multideplo files plus the JAR. It does not contain this repository's source.
- Releases follow semantic versioning, with `pom.xml` as the **single source of
  truth** for the version. The JAR embeds it as build info; the page reads it
  from there. Do not hand-type a version anywhere else.
- Changes reach `main` through pull requests.
- A hosted deploy-from-GitHub flow is a wish, not a fact:
  [WISH-02](https://github.com/roncproject/ruhrohgue/issues/10).

### 2.5 Operations

- **Health check:** `GET /actuator/health` returns `UP`. This repository's
  standalone `Dockerfile` uses it for a container health check; the production
  image does not define one, and Elastic Beanstalk health reporting is used instead.
- **Restart:** Elastic Beanstalk → *Restart app server(s)* re-reads the score
  file from S3 and drops all in-memory sessions.
- **Resetting scores:** delete the score object in S3, **then** restart the app
  server. Order matters: the app keeps the table in memory and would otherwise
  write it back.
- **Logging:** Spring Boot to stdout, collected by Elastic Beanstalk. Default
  level `WARN` for dependencies, `INFO` for the `hack` package.

## 3. Performance targets

Targets, not measured guarantees.

| Area | Target |
|---|---|
| API latency | A command round trip well under 100 ms on the server (the Postman run observes single-digit milliseconds locally) |
| Throughput per client | 10 requests per second sustained, which comfortably exceeds a human playing, see §4 |
| Memory | One game session is small; in production the Java heap is capped at 512 MB (`-Xmx512m`) |
| Startup | The app is ready in a few seconds; the Docker health check allows a 20 s start period |
| Static assets | Served with the service worker's cache-first strategy after the first visit |

## 4. Security targets and controls

### 4.1 Edge: Cloudflare

Cloudflare proxies the domain and provides TLS, DDoS absorption and WAF/bot
rules. Design intent:

- **Only Cloudflare may reach the origin.** The instance's security group allows
  web traffic from Cloudflare's published address ranges (kept in an AWS managed
  prefix list) and not from the open internet. This is what makes the next point
  safe.
- **The rate limiter trusts `CF-Connecting-IP`** to identify the client. That
  header is only trustworthy because the origin accepts nothing but Cloudflare
  (see [TRK-06](https://github.com/roncproject/ruhrohgue/issues/7)). If the
  origin were ever opened to the world, the header could be forged and the
  limiter bypassed.
- **Bot challenges can break legitimate requests.** Cloudflare's challenge
  features can return `403` with a `cf-mitigated: challenge` header to a request
  that has no browser behind it, including `POST` API calls and `manifest.json`.
  See [TRK-010](https://github.com/roncproject/ruhrohgue/issues/14). The fix
  lives in the Cloudflare dashboard, not in this repository.

### 4.2 Application

| Control | Detail |
|---|---|
| Input validation | Player name must match `^[a-zA-Z0-9]{3}$`; role is checked against a fixed allow-list; an invalid name returns `400`, never a silently rewritten value |
| Rate limiting | `RateLimitFilter`: a token bucket per client, 600 requests per 60 s by default; HTTP `429` with `Retry-After` when empty; static assets are exempt; at most 10,000 tracked clients |
| Request size | `RequestSizeLimitFilter`, highest filter order: 1024-byte body cap, `413` on a declared oversize length, a counting wrapper otherwise |
| Error output | Messages, stack traces and exception names are not sent to the browser |
| Admin route | `DELETE /dev/scores` needs the `X-Admin-Token` header, compared in constant time; an unset token locks the route |
| Storage path safety | File store paths are resolved inside a declared directory, and S3 keys containing `..` are rejected |
| Session | HTTP session, 4-hour timeout, one game per session |
| Cookies | Only the session cookie the server needs; no tracking, no analytics |
| CORS | The controller currently allows any origin (`@CrossOrigin("*")`). It sends no credentials, but it is worth tightening if the API ever becomes more than a game back end |
| Not yet in place | Content-Security-Policy and related headers ([WISH-01](https://github.com/roncproject/ruhrohgue/issues/9)) |

### 4.3 Privacy

No analytics, advertising or third-party scripts. A zero-cookie notice is shown
once and remembered in the browser's local storage. A legal page is still a
draft ([WISH-04](https://github.com/roncproject/ruhrohgue/issues/15)); the repository
template is reference material only and is not legal advice.

## 5. Test strategy

| Level | Tool | Where | Notes |
|---|---|---|---|
| Unit and slice | JUnit 5, Mockito, Spring `@WebMvcTest` | `src/test/java` | Includes the S3 store with a mocked client |
| API | Postman collection run with newman | `tests/api/` | Needs the app on port 8081 |
| Browser e2e | Playwright (Java) with Cucumber, page-object model | `tests/e2e/` | Scenarios live in `features/*.feature` |
| All of it | `run-all-tests.bat` | repo root | Does not start the app; start it first |

The expectation is that **all** suites are green before a change is merged.

## 6. Constraints and assumptions

- One instance, one in-memory score table (see §2.1).
- The game keeps the original rules; changes to mechanics need care, because
  correctness is measured against the 1985 behaviour.
- All randomness goes through `Dice`, seeded exactly once per game, with one RNG
  state **per HTTP session**. Nothing downstream may re-seed it.
- The JavaScript is ES5-compatible and has no build step; keep it that way
  unless the cost is justified.
