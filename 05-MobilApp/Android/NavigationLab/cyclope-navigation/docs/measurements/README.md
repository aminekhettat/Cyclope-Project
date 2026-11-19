# Measurements

Put the CSV logs of the field tests here (they are small, a few hundred kB), with the results produced by
`tools/analyze_positions.py` (`*_results.md`, `*_tracks.png`).

File naming: `poslog_<date>_<time>_<phone>.csv` (automatic) — add the test letter in the commit message
(A = static open sky, B = static near buildings, C = walk with marks).

For test C, copy `marks_reference_template.csv`, and write the true coordinates of each corner
(openstreetmap.org → right-click → "Show address" shows lat/lon).
