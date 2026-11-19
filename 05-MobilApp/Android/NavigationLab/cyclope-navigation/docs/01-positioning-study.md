# Positioning study — which position source for pedestrian navigation?

Owner: Taoyu (navigation brick: localisation + path tracking)
Status: **tool ready, field measurements in progress** (results table below is filled from real logs only)

## 1. Why this study

The navigation brick says "in 50 m, turn left" and "now, turn left". If the position is 15 m wrong,
the user turns at the wrong corner. So before choosing a source we need **numbers**: how precise,
how often, how fast, and how it behaves near buildings — on our own phones, not from a datasheet.

Requested in the follow-up meeting of 16/10/2026: compare the *levels* of position data a phone gives
(raw, filtered, fused; GNSS without sensors; inertial sensors; magnetometer).

## 2. The 5 sources compared

All 5 are recorded **at the same time** by the test app (`PositionRecorder.kt`), so they see exactly
the same sky, buildings and walk.

| # | Name in CSV | Android API | Level | What it is | Open source? |
|---|---|---|---|---|---|
| 1 | `GNSS` | `LocationManager.GPS_PROVIDER` | filtered | Position computed by the phone's GNSS chip from satellites only (GPS, Galileo, GLONASS, BeiDou), already smoothed by the chip. No Wi-Fi, no sensors. | Standard Android API |
| 2 | `NETWORK` | `LocationManager.NETWORK_PROVIDER` | filtered | Position from Wi-Fi access points and cell towers. Works indoors, fast first fix, but coarse. | API is standard; the service behind it is usually Google Play services |
| 3 | `FUSED` | `LocationManager.FUSED_PROVIDER` (Android 12+) | fused | Android's own fusion of GNSS + network (+ sensors on some phones). Same idea as Google's Fused Location Provider but reached through the standard `LocationManager`, **without adding the Google Play services library**. | Standard API; implementation depends on the phone |
| 4 | `GNSS_STATUS` | `GnssStatus.Callback` | raw | Not a position: per-satellite data — number of satellites used / visible and **C/N0** (signal strength, dB-Hz). Explains *why* GNSS is good or bad at a place. (Next level: `GnssMeasurement` raw pseudoranges — out of scope for now.) | Standard API |
| 5 | `PDR` | ours: `PdrFusion.kt` | custom fusion | **Pedestrian Dead Reckoning**: between GNSS fixes, move one step length (0.7 m) in the compass direction (`TYPE_STEP_DETECTOR` + `TYPE_ROTATION_VECTOR` = accelerometer + gyroscope + **magnetometer**). Each GNSS fix corrects the estimate with a Kalman-style weight `k = σ_pdr² / (σ_pdr² + accuracy_gnss²)`. | Our code |

Expected trade-offs (to be confirmed by measurements, not assumed):

- GNSS: good in open sky, degraded near tall buildings (reflections = "multipath"), slow first fix.
- NETWORK: available everywhere including indoors, but tens of metres: probably only useful for a quick first position.
- FUSED: probably the best ready-made compromise; we don't control its algorithm.
- PDR: can smooth GNSS jumps and bridge short GNSS gaps, but the compass heading depends on how the
  phone is carried (pocket vs hand) and is disturbed by metal (cars, street furniture).

## 3. Test protocol

Phone, Android version and date are written in the CSV file name / test notes.
App: Cyclope Nav Lab → "Start log" (screen stays on; keep the app in the foreground).

| Test | How | What it measures |
|---|---|---|
| A. Static, open sky | Stand still 3 min on a point you can find on openstreetmap.org (right-click → "Show address" gives lat/lon) | accuracy (error vs truth), precision (spread), time to first fix |
| B. Static, near buildings | Same, 3 min, next to a tall building | degradation in "urban canyon" |
| C. Walk with marks | Walk a 300–500 m loop on campus; at each corner press **Mark point** (M1, M2…). Write the true lat/lon of each corner in `marks_reference.csv` | error while walking, behaviour at turns, update rate |

Analysis: `python tools/analyze_positions.py <csv> --ref lat,lon` (tests A, B) or `--marks marks_reference.csv` (test C).

Metrics (definitions in the script header): availability %, update rate, time to first fix,
reported accuracy (median / 95 %), **real error CEP50 / CEP95** vs reference, spread, and
"honest %" = how often the real error is inside the accuracy the phone claims (ideal ≈ 68 %).

## 4. Decision criteria (proposal, to validate with M. Khettat)

For turn-by-turn guidance of a pedestrian we propose:

- real error **CEP95 ≤ 10 m outdoors** (otherwise "now turn left" may be said at the wrong corner),
- **≥ 1 fix per second**,
- reported accuracy must be **honest** (used by `RouteFollower` to ignore bad fixes and to avoid false "off-route" alarms),
- prefer standard Android APIs / open source (project rule: free and open software only).

## 5. Results

Fill only from real logs (CSV files are committed in `docs/measurements/`).

| Test | Phone | GNSS CEP95 | NETWORK CEP95 | FUSED CEP95 | PDR CEP95 | Notes |
|---|---|---|---|---|---|---|
| A. open sky | _to do_ | | | | | |
| B. buildings | _to do_ | | | | | |
| C. walk (error at marks, median) | _to do_ | | | | | |

## 6. Current choice (temporary)

Until the measurements are done, the navigation demo uses **FUSED if the phone has it (Android 12+),
otherwise GNSS** (see `MainActivity.navSource()`). This choice will be replaced by the measured best source.
