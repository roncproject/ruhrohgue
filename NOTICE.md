# NOTICE — Attribution and Provenance

## Original work

RuhRohgue is a Java adaptation of **Hack 1.0.2**, a roguelike dungeon crawler
written at the Centrum voor Wiskunde en Informatica (CWI) in Amsterdam and
released in 1985.

The original C sources carry this notice, which is reproduced here and applies
to every part of RuhRohgue derived from them:

```
/* Copyright (c) Stichting Mathematisch Centrum, Amsterdam, 1985. */
```

Hack 1.0.2 was released for free modification and redistribution. RuhRohgue is
a non-commercial port that preserves that spirit: it is free to play, free to
read and free to fork.

### Lineage

| | |
|---|---|
| **Rogue** (1980) | Michael Toy, Glenn Wichman, Ken Arnold |
| **Hack** (1982–1985) | Jay Fenlason, Andries Brouwer, and others at CWI Amsterdam |
| **Hack 1.0.2** (1985) | Stichting Mathematisch Centrum, Amsterdam |
| **RuhRohgue** (2026) | This Java / Spring Boot port |

## What is derived, and what is not

**Derived from the original C sources.** The dungeon generation algorithm, the
monster and object tables, the combat and hunger models, the trap behaviours,
the command key bindings and the screen symbol vocabulary. The Java package is
named `hack`, and classes are named after their C counterparts
(`mklev.c` → `LevelGenerator`, `monmove.c` → `MonsterEngine`), so the
correspondence stays legible to anyone reading both.

**Original to this project.** The Spring Boot web layer, the browser front end
and its Progressive Web App shell, the seeded-reproducibility system, the shop
and banking economy, the Rotterdam street-food item set, the food decay model,
and all deployment and infrastructure code.

## AI-assisted development disclosure

**This project was developed with AI assistance** — the practice sometimes
called *vibe coding*. Large parts of the Java port, the browser front end, the
test suite and the documentation were generated or substantially revised by an
AI coding assistant, working from the original C sources and from
human-authored requirements and bug reports.

This is disclosed for three reasons:

1. **Honesty about provenance.** Anyone reading, forking or auditing this code
   is entitled to know how it was produced.
2. **It changes how the code should be reviewed.** AI-generated code tends to
   look confident and idiomatic while missing exactly the things a compiler
   cannot catch — concurrency, perimeter security, resource lifecycle, and the
   difference between a plausible constant and a correct one. Several real
   defects in this project's history were of precisely that kind: an array
   sized 215 against a declared 237, a wall-clock re-seed that silently
   defeated the seed feature, and a file writer with no atomicity.
3. **Testing is not optional here.** The test suite exists because generated
   code needs verification more than hand-written code does, not less.

Bugs found in this project should be reported normally. The AI provenance is
context, not an excuse.

## Third-party components

| Component | Licence | Used for |
|---|---|---|
| Spring Boot 3.2.x | Apache-2.0 | Web framework, actuator |
| AWS SDK for Java v2 (S3) | Apache-2.0 | Durable player records |
| Eclipse Temurin JRE 17 | GPL-2.0 with Classpath Exception | Runtime |

## Licence

RuhRohgue is published under the **MIT License** — see `LICENSE`. MIT was
chosen because it is permissive, short enough to actually be read, and
consistent with the freely redistributable terms under which Hack 1.0.2 was
released.
