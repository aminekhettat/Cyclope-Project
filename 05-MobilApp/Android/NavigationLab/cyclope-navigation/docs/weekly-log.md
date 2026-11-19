# Weekly log — navigation brick (Taoyu)

Plan agreed on 13/11/2026: build each block until Christmas, integrate after the holidays, final product early February.
At least one commit + push per week on this branch.

## Week of 16/10/2026
- Started studying the phone sensor / location functions (in Java); team decision to switch to Kotlin.
- Task received: compare the levels of position data available on a phone (raw / filtered / fused, GNSS, inertial, magnetometer).

## Week of 13/11/2026
- Reported 5 position sources tested with a test app on the phone (code was not pushed yet).
- Assigned: path tracking + localisation. Asked: weekly push, write the 5 sources down.

## Week of 14/11/2026 → 20/11/2026
- Test app to record **5 position sources at the same time** into one CSV (`PositionRecorder.kt`): GNSS, NETWORK, FUSED, GNSS_STATUS (satellites + C/N0), PDR (own fusion: steps + compass + GNSS).
- Navigation core in pure Kotlin, with 25 unit tests (run offline with hand-made API fixtures):
  Nominatim client, OSRM foot client, address choice with glasses buttons, French instructions, route follower (50 m / 12 m announcements, arrival, off-route, weak GPS).
- Interfaces with the other blocks written (`NavigationInterfaces.kt`) — to validate with the team.
- Analysis script `tools/analyze_positions.py` (CEP50 / CEP95, update rate, availability, plot).
- Docs: `01-positioning-study.md`, `02-navigation-architecture.md`.

**Next week**
- Field tests A / B / C on campus, commit the CSVs and fill the results table.
- Choose the position source for navigation from the measurements.
- Agree button mapping + `VoiceOutput` interface with Shana and the firmware teammate.
- Start: list zebra crossings along the route from OpenStreetMap (Overpass API).
