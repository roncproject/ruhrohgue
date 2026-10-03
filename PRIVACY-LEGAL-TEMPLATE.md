# Privacy & Legal Notice — Template

Reference document for the eventual `/privacy-legal.html` page. Not
implemented in the app on purpose — this is a skeleton to fill in once
there's real content, not something to ship with placeholder facts
presented as real.

---

## 1. Privacy Statement

### 1.1 Cookies

This part is true regardless of anything else and can be written as final
copy now: no cookies are set by this application. No tracking, analytics,
or advertising scripts run. The only browser storage used is a single
`localStorage` flag remembering that the visitor dismissed the in-page
notice — nothing identifying, nothing sent to any server.

### 1.2 Server & edge logs

When you play, your request passes through:

- **Cloudflare** (edge/CDN in front of the origin) — processes your IP
  address to route the request and apply rate limiting / bot protection.
- **The application's own rate limiter** (`RateLimitFilter`) — keys a
  short-lived in-memory counter off your IP (or the `CF-Connecting-IP`
  header, when behind Cloudflare) to prevent abuse. Not persisted, not
  logged per-player.
- **nginx access logs** on the hosting instance — standard web server
  access logs (IP, request line, user-agent, timestamp), used for
  operational troubleshooting.

**[FILL IN WHEN REAL]** — the legal basis you're relying on (typically GDPR
Art. 6(1)(f), legitimate interest, for security/abuse-prevention logging),
how long these logs are retained, and who can access them.

### 1.3 Game data

Completing a game stores: the 3-character name you chose, your character
class, final score, and dungeon depth reached — written to the high-score
file. No account, no email, no IP tied to the score entry itself.

### 1.4 Data subject rights

**[FILL IN WHEN REAL]** — how someone actually exercises access/erasure
rights given there's no account system to look entries up by (e.g.
"contact us with the name string and approximate date, we'll locate and
remove the entry").

---

## 2. Legal Notice / Imprint

**[FILL IN WHEN REAL — none of this should ever ship as placeholder text
presented as fact]**

- **Operator:** your real name or entity, if any
- **Contact:** a real, monitored email address
- **Jurisdiction / location:** wherever you're actually established, if
  this is meant to be a formal imprint
- **Liability notice:** standard "provided as-is" language is fine to
  write once the above is real — not legal advice, worth having an actual
  lawyer glance at this before publishing if it's ever a real product with
  real users

---

## Implementation notes, for when this becomes real

- Goes at `src/main/resources/static/privacy-legal.html` — a plain static
  file is fine, no Thymeleaf needed, it has no dynamic content.
- Link to it from `#privacy-banner` in `index.html` once it exists — the
  current banner deliberately links nowhere, since there's nothing real to
  link to yet.
- Match the site's actual theme (pure black/white terminal — the `--bg`,
  `--fg`, `--border` variables already in `hack.css`) rather than
  inventing a separate color palette.
