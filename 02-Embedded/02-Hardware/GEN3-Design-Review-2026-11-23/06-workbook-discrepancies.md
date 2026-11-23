# Discrepancies found in Analysis of BOM.xlsx

Thank you to Sean for the functional BOM analysis. The points below reconcile the supplied workbook with Ali's Eagle source; some are version differences rather than mistakes. **The workbook and all existing CAD are unchanged.** “Current Eagle” refers to the source commit listed in the audit, not every Altium file.

| Workbook reference | Observed difference | Suggested resolution |
|---|---|---|
| A12 `LC0709203F` | Eagle IC1 is `LC709203F/MH`; the extra zero is a naming typo. Full orderable suffix is not given in Eagle. | Correct the name in the next controlled revision; compare the new MAX17048 proposal separately, not as an existing Eagle chip. |
| A4/C4 `MBR540`, linked C272410 | The link is an axial **DO-201AD** part; Eagle D4 footprint is **SOD-123**. D4 is a Schottky between VBUS and VHI, not a Zener. | Match actual MPN/package/rating if retained in architecture 1; remove function in the revised single-5-V-input camera topology of architecture 2. Do not substitute the axial part into SOD-123 pads. |
| F40 R20/R21 `4.7K` | Eagle R20/R21 are **47 kΩ**, not 4.7 kΩ. | Record the difference for traceability; both are removed with the microphone under Amine's audio-removal decision, so no new sourcing is required for GEN 3. |
| IC4 / J2 procurement | Exact microphone ICS-43434 C5656610 and speaker connector B2B-XH-A C19272845 were stock-zero in the prior review; the fresh catalogue now states the current snapshot. | These are no longer blockers because the entire audio chain was removed by PO decision. Do not replace a removed block just to solve its stock issue. |
| A6/E6 `JST PH` / `dont need it anymore` | X1 is still present on VBAT/GND in Eagle schematic and board. | Architecture 1 still needs a battery connection; architecture 2 moves it to the battery enclosure. Apply removal only in the chosen revised CAD, not in a purchase sheet alone. |
| `D3 LED RED 0603` | D3 is Altium-only; it is absent from the 68-component Eagle inventory. Existing Eagle `CHG` is a different component. | Reconcile Altium and Eagle baselines with Sean before setting the final count; do not subtract D3 again from Eagle. |
| JP1 / USB-to-serial section | JP1 exists in Altium but not current Eagle; X3's USB data already goes to ESP32 GPIO19/20. No separate CH340/CP210x appears in this Eagle BOM. | Confirm the CAD baseline. Preserve native USB flashing; do not infer an extra converter from the section title. |
| R1/R2/R4/R5/R8 all labelled 5.1 kΩ in source | Equal value does not imply equal function. Only R5/R8 are CC Rd. R1 biases ESP reset, R2 is charge indication, R4 sets charge current. | Retain the two CC pull-downs on the 5 V sink. Move/remove the charger-specific parts only with the charger topology. |

Exact fresh C-code metadata is in the [catalogue](08-jlcpcb-reference-catalogue.md). The functional decisions supersede old stock workarounds. The review does not modify Sean's cells, formatting, comments or formulas.
