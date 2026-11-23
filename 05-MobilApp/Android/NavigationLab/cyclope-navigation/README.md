# CyclopeGEN 3 — Navigation brick (pedestrian navigation)

Branch of the navigation brick: **localisation + path tracking + voice guidance instructions**.
Kotlin, Android, open source services only (OpenStreetMap: Nominatim + OSRM).

| Document | Content |
|---|---|
| [docs/01-positioning-study.md](docs/01-positioning-study.md) | The 5 position sources compared, test protocol, results |
| [docs/02-navigation-architecture.md](docs/02-navigation-architecture.md) | Block diagram, user flow, interfaces with the other blocks, API vs our code |
| [docs/weekly-log.md](docs/weekly-log.md) | What was done each week |

## Status (20/11/2026)

- [x] Navigation core (pure Kotlin): Nominatim, OSRM foot, address choice with buttons, French instructions, route follower — **25 unit tests**
- [x] Test app: live view + CSV log of the 5 position sources; navigation demo (live GPS or simulated walk)
- [x] Analysis script for the logs
- [ ] Field measurements (tests A / B / C) and choice of the position source
- [ ] Interfaces frozen with voice / firmware / STT teammates
- [ ] Zebra crossings along the route from OpenStreetMap

## Build and run

1. Android Studio → *Open* → this folder. Let Gradle sync (first time downloads Gradle 8.9 / AGP 8.7.3).
2. Phone with *Developer options → USB debugging* → Run ▶ `app`.
3. Accept location + physical activity permissions.

Unit tests (no phone needed): `./gradlew :navigation:test` (or right-click `navigation/src/test` → *Run tests*).

## Using the test app

**1. Positioning study** — *Start log* records all sources to `poslog_<date>_<phone>.csv`.
Press *Mark point* at known places during a walk. *Share CSV* sends the file (mail, Drive…).
Then: `python tools/analyze_positions.py poslog_xxx.csv --ref <lat>,<lon>`.

**2. Navigation demo** — type an address (stand-in for speech-to-text) → *Search* →
the candidates are read aloud → *Short* = next, *Long* = choose, *Double* = none →
the route is computed from the current position → walk, or press *Simulate walk* to replay the route indoors.

Network services: `nominatim.openstreetmap.org` and `routing.openstreetmap.de/routed-foot`
(max 1 request/s, identifying User-Agent set in `MainActivity.USER_AGENT`). Map data © OpenStreetMap contributors (ODbL).

## Shared voice and accessibility (23/11/2026)

The app now hosts the byte-identical VisionLab `:voicekit` module. Navigation algorithms and interfaces are unchanged.
See [shared voicekit and TalkBack integration](docs/03-shared-voicekit-accessibility.md) for the module boundary,
copy provenance, test commands and a step-by-step TalkBack checklist for Shanna and Taoyu.
Speech-to-text is a reserved interface only; this integration adds no microphone permission.
