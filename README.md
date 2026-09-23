# 🕹️ RuhRohgue - Cloud Edition

**RuhRohgue** is a vibe-coded java port of the **Hack 1.0.2** roguelike
(CWI Amsterdam, 1985)

🚀 **Live Demo:** [https://ruhrohgue.dev](https://ruhrohgue.dev) *Momentarily down for maintenance* 

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

