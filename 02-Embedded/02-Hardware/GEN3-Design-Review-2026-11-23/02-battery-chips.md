# Battery and regulator chips — what exists, what is new?

“Existing” below means present in **Ali's Eagle schematic** at the source commit, not necessarily present in every Altium revision, assembled board or earlier review proposal. Inventory and exact fresh stock/prices are in the [reference catalogue](08-jlcpcb-reference-catalogue.md); each C-code links to its exact partdetail and datasheet there.

| Chip / exact purchasing code | Eagle status | Architecture 1 | Architecture 2 | Reason / qualification |
|---|---|---|---|---|
| MCP73831T-2ACI/OT C424093 | **EXISTING U3** | Can remain, charge-only | Can move to battery for constrained prototype; not the power-path baseline | Original VDD=VBUS, VBAT=cell, PROG=R4. No USB-C DRP, no 5 V boost, no NTC, no true system power-path. |
| LC709203F/MH | **EXISTING IC1** | Existing gauge function retained/replaced | Gauge function moves with cell | “LC0709203F” is a workbook typo. Eagle does not specify the full orderable suffix. C184366 is a separately checked LC709203FXE-01MH family candidate, **not proof of an exact suffix match**; see fresh stock. |
| MAX17048G+T10 C2682616 | **NEW relative to Eagle** | Proposed gauge replacement, camera-local | New battery-local gauge | Replaces the unavailable LC709203F candidate, but footprint, decoupling, I2C address (0x36 vs old 0x0B), firmware and SOC calibration change. Not drop-in. |
| BQ24074RGTR C54313 | **NEW** | Optional new charger redesign | Two-port power-path baseline | New replacement for MCP73831, not merely moving the same chip. Adds system power-path and temperature-input capability; current, NTC window, timer and thermal design still open. |
| IP5310 C191176 | **NEW** | Not needed | Conditional one-port charge/boost/DRP alternative | Native CC1/CC2 DRP. Qualify supplied variant, cell CV/charge current, advertised source current and source MOSFET implementation. |
| IP5310_I2C C20616661 | **NEW, separate variant** | Not needed | Availability/variant comparison only | Do not assume standard IP5310 exposes the same I2C configuration; custom variant stock must be checked separately. |
| IP5306 C181692 / IP5306-I2C C488349 | **NEW** | Not needed | Potential non-DRP prototype power-bank blocks | Charge + boost, but **no native USB-C CC/DRP**. Standard and I2C variants differ. Reviewed auto-off roughly <45 mA for 32 s; our normal 300–900 mA load is above that, sleep modes may not be. |
| IP2312U_VSET C605432 | **NEW** | Optional charger only | Not a one-chip power bank | Buck charger only; no required discharge boost or USB-C DRP. Do not substitute a 4.35 V cell variant for a 4.2 V LiPo. |
| IP5328P C188809 | **NEW** | Not needed | Non-selected comparison | Multi-protocol power-bank IC, but zero-stock observation and no qualified one-port DRP implementation here. |
| TPS61023DRLR C919459 | **NEW** | Not needed | Proposed small synchronous 5 V boost | Tiny 1.2 × 1.6 mm package; 1.5 A output must be validated at minimum cell voltage, with power-path, inductor, thermal and current-limit margins. |
| TPS61088RHLR C87357 | **NEW** | Not needed | Higher-margin boost alternative | Switch rating is 10 A, **not 10 A USB output**. More area/passives/compensation than TPS61023. |
| SY7208ABC C80514 / MT3608 C84817 | **NEW** | Not needed | Not accepted for guaranteed 5 V/1.5 A from 3 V | “2 A” refers to switch current; output can be around/below 1 A in this corner before ripple/temperature allowance. |
| TPS63802DLAR C2845237 | **NEW vs Eagle; already in proposed target46** | Retain buck-boost concept for raw cell→3.3 V | Can operate in buck mode at fixed 5 V | Datasheet supports 2 A at 3.3 V for Vin ≥2.3 V; 1.5 A at 5→3.3 V is within electrical capability subject to layout/thermal. Input max 5.5 V; USB fault tolerance still needed. |
| TPS562201DDCR C90061 | **NEW** | Not suitable across 3.0–4.2 V raw cell | Lower-cost buck proposal | 2 A, Vin 4.5–17 V; **Extended**, not Basic. Replaces the proposed TPS63802 stage, not original U3. Cable drop must retain Vin ≥4.5 V. |
| SY8089A1AAC C479074 | **NEW** | Not selected | Buck alternative | 2 A, Vin 2.5–5.5 V; Extended; better low-input margin but limited high-end headroom. |
| MT2492 C89358 | **NEW** | Not selected | Buck comparison | 2 A, minimum Vin 4.5 V; Extended. |
| ME3116AM6G C97643 | **NEW** | Not selected | Rejected for ≥1.5 A requirement | 1 A and minimum Vin about 4.75 V. |
| TP5400 C2891434 | **NEW** | Not selected | Not a 5→3.3 V buck replacement | Charger/boost function, different use. |
| TPS5430DDAR C9864 / XL1509-5.0E1 C61063 | **NEW** | Not selected | Basic parts examined, not suitable substitutes | TPS5430 minimum Vin 5.5 V; cited XL1509 variant fixed 5 V output. Do not trade correctness for a Basic label. |
| TUSB320LAIRWBR C132554 / TPS2553DBVR C55266 | **NEW** | Not needed for raw battery link | Source CC attach control and current-limited VBUS switch | Configure source-only; qualify enable, current limit, reverse isolation and discharge circuit. Not the charger. |
| CH32V203F6P6 C3040880 / AP2112K-3.3TRG1 C51118 | **NEW** | No battery MCU needed | Battery USB host plus local 3.3 V LDO | CH32V203 is Extended. MCU/LDO consume quiescent energy; sleep and role handling require firmware. |
| CH32V003F4P6 C5187096 / TS3USB221ARSER C128396 | **NEW** | Not needed | Proprietary UART fallback only | Extended MCU; USB/UART mux and safe mode detection are necessary. This is not transparent standard USB. |
| TLV75515PDBVR C2867152 | **NEW** | Conditional module core rail | Conditional 1.5 V/500 mA rail | Module-dependent; use only after actual camera power tree is confirmed. Existing IC3 is 2.8 V, not 1.5 V. |


## Fresh key-part availability snapshot

API unit prices are indicative USD; see the full catalogue for manufacturer, exact MPN, timestamps, partdetail and datasheet. Stock/quota are not reserved.

| Code / exact MPN | Classification | Stock / quota | USD |
|---|---|---:|---:|
| C424093 / MCP73831T-2ACI/OT | Extended | 12675 / 11730 | 0.7806 |
| C184366 / LC709203FXE-01MH | Extended | 0 / 0 | 0.9786 |
| C2682616 / MAX17048G+T10 | Extended | 24710 / 24007 | 1.944 |
| C191176 / IP5310 | Extended | 4932 / 4859 | 0.6677 |
| C20616661 / IP5310_I2C | Extended | 0 / 0 | 0.8771 |
| C181692 / IP5306 | Extended | 34043 / 33410 | 0.345 |
| C605432 / IP2312U_VSET | Extended | 16577 / 15702 | 0.3245 |
| C54313 / BQ24074RGTR | Extended | 2734 / 2468 | 2.1649 |
| C919459 / TPS61023DRLR | Extended | 137712 / 136088 | 0.2331 |
| C87357 / TPS61088RHLR | Extended | 3655 / 2791 | 1.44 |
| C80514 / SY7208ABC | Extended | 11187 / 10891 | 0.3168 |
| C2845237 / TPS63802DLAR | Extended | 15341 / 14929 | 0.9442 |
| C90061 / TPS562201DDCR | Extended | 457679 / 437797 | 0.0565 |

## Existing camera power chain is not the battery selector

- Existing **Q3** is DMG3415U C7202986: gate on VBUS, drain on VBAT, source on VHI. Together with D4 (anode VBUS, cathode VHI) it implements USB/cell selection.
- Existing **Q1**, same MPN/code but different instance, feeds **IC3 XC6206P282MR-G C2891263** from 3.3 V through net N$7; R11/R66 bias its gate. IC3 produces the camera 2.8 V rail. Do not remove Q1 merely because Q3 is removed in architecture 2.
- Existing **U2** is the old 3.3 V LDO family, replaced in the 46-part proposal; it is neither the battery charger nor the new TPS63802.

## Source references

- [MCP73831 manufacturer datasheet](https://ww1.microchip.com/downloads/en/DeviceDoc/20001984g.pdf).
- [MAX17048 manufacturer datasheet](https://www.analog.com/media/en/technical-documentation/data-sheets/MAX17048-MAX17049.pdf).
- [BQ24074 family datasheet](https://www.ti.com/lit/ds/symlink/bq24074.pdf).
- [TPS61023](https://www.ti.com/lit/ds/symlink/tps61023.pdf), [TPS61088](https://www.ti.com/lit/ds/symlink/tps61088.pdf), [TPS63802](https://www.ti.com/lit/ds/symlink/tps63802.pdf), [TPS562201](https://www.ti.com/lit/ds/symlink/tps562201.pdf).
- IP5310, IP5306, IP2312 and all purchasing variants: use the exact per-C-code datasheet in the catalogue, not an interchangeable family screenshot.
