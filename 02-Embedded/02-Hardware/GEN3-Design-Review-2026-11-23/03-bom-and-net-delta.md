# BOM stages and traceable changes

## 1. What the 46-component target means

Ali's unchanged Eagle source has **68 physical purchasable components** (139 part objects include symbols). Audio removal subtracts 15; original illumination removal subtracts 13; old 3.3 V LDO U2 and C8 subtract 2; proposed TPS63802 stage adds 7; missing direct charger-input decoupling adds 1:

```text
68 - 15 - 13 - 2 + 7 + 1 = 46 physical placements
```

[Baseline CSV](bom-camera-baseline-target46.csv): 27 grouped rows, 46 placements. It is a review baseline closer to architecture 1, **not** the final architecture-2 camera BOM. Its unresolved gauge, D4, charge LED and connector entries remain unresolved. Earlier Basic/Extended accounting was 26/12 with eight unresolved placements; fresh catalogue status takes precedence for purchasing. Boot/reset switches are two placements, separate from the new user buttons.

Audio removed: U1, IC4, J2, FB1/FB2, C11/C13/C16/C17/C18/C19, R19/R20/R21/R22. Original illumination removed: LED1–4, R9/R13/R14/R15/R16/R18, Q2, D1/D2. The later **optional IR** circuit is a new module-dependent proposal, not the old four visible LEDs silently retained.

## 2. Net-based camera delta for architecture 2

| Designators | Verified Eagle net/function | Architecture-2 verdict | Reason |
|---|---|---|---|
| U3 | VDD=VBUS, VBAT=VBAT, PROG=N$4, STAT=N$3 | MOVE FUNCTION; existing chip optional on battery prototype | Battery is no longer local. Baseline smart battery instead uses a new BQ24074 power-path charger. |
| R4 | 5.1 kΩ, N$4(PROG)–GND | MOVE with MCP73831 or REMOVE if replaced | Sets roughly 196 mA charge, not USB-C Rd. No PROG capacitor. |
| CHG, R2 | Charge indication via N$1/N$3 to U3 STAT | MOVE/REPLACE on battery | Not user illumination. R2 is not a CC pull-down. |
| IC1 | LC709203F on VBAT and shared SDA/SCL | MOVE GAUGE FUNCTION; replace part with new MAX17048 | No raw cell accessible at camera in architecture 2; firmware/footprint change. |
| X1, C3 | Cell connector and 10 µF VBAT–GND | REMOVE from camera; retain battery-side connection/decoupling function | No camera-local battery. Source X1 remains present until CAD is changed. |
| Q3, R12 | Q3 G=VBUS, D=VBAT, S=VHI; R12 VBUS–GND | REMOVE after single-input redesign | USB/cell selector no longer needed at camera. Do not conflate with Q1. |
| D4 | Schottky, anode VBUS / cathode VHI | REMOVE in revised single-5-V topology | Not a Zener; selector isolation redundant. New USB protection still required. |
| C_USB_IN | Proposed capacitor at U3 VDD/VBUS, absent in original | REMOVE at old charger location | Charge function moves; retain proper buck-input decoupling at C6/new layout. |
| C6, R7 | C6 VHI–GND; R7 VHI–EN | KEEP/REWIRE with redesigned input/enable | Input decoupling and enable function still needed. No automatic pin-compatible replacement. |
| Q1, R11, R66, IC3, C14/C15 | Camera supply chain, not VBUS/VBAT selection | KEEP conditionally, module rails to qualify | IC3 output 2.8 V; not the proposed core 1.5 V. |
| R5 / R8 | 5.1 kΩ to X3 CC2 / CC1 respectively | KEEP two separate Rd | The only two CC pull-downs in the extracted source. |
| R1 | 5.1 kΩ, 3.3 V–RESET (ESP EN) | KEEP | Not a CC resistor. |
| C5/R17 | IO47_DBLTAP reserve to GND, 1 µF/1 MΩ | KEEP as explicit reserve pending PO/Sean review | No proof of a fitted accelerometer; optional removal not counted. |
| R3 | 10 kΩ ×4 resistor array, includes SCCB/I2C pull-ups | KEEP function, qualify voltage/pull-ups | One physical placement; camera DOVDD may differ from 3.3 V. |
| X3 | USB D−→GPIO19; D+→GPIO20 | KEEP sink/native USB role; candidate connector footprint change | Flash/development remains available after unplugging battery. |
| J1 | Original TE 24-position camera connector | REPLACE only after module drawings | Existing part is stock-zero; pitch alone does not qualify a substitute. |

The eleven old-camera removals are U3, IC1, CHG, R2, R4, Q3, R12, X1, C3, C_USB_IN, D4. Their *functions* may move, but the smart-battery implementation is not automatically a relocation of every existing physical component.

## 3. 56-placement camera proposal, day-only

[Separate architecture-2 CSV](bom-camera-arch2-rev2.csv), 36 rows, 56 physical placements:

```text
46 - 11 old local battery/charge/selector parts
   -  7 proposed TPS63802-stage parts
   +  7 TPS562201-stage parts
   +  8 four user buttons plus four pull-ups
   +  3 candidate ESD networks
   +  3 optional VBUS divider/filter reservations
   +  7 conditional camera core/reset/DOVDD decoupling parts
 = 56
```

Use two user buttons instead of four: 52 day placements. Removing C5/R17, if agreed, subtracts two further; not assumed here. A native-USB telemetry design may omit the optional VBUS ADC reservation after fault/monitoring review. GPIO1 ADC cannot simultaneously serve UART fallback TX.

Candidate buck TPS562201 C90061 has seven placements: IC, 4.7 µH inductor, bootstrap 100 nF, two 22 µF output capacitors, 33 kΩ and 10 kΩ divider; reuses the input-capacitor function. 0.768 × (1+33/10) ≈ 3.302 V. Proposed TPS63802 stage has one Basic plus six Extended placements; new buck stage has five Basic plus two Extended in the snapshot. This reduces Extended placements, **but the buck IC remains Extended**. Inrush, effective capacitor values, ripple and the 4.5 V minimum input are qualification items. Keeping TPS63802 in architecture 2 is electrically viable and avoids a further topology change, at higher indicated part cost.

The budget currently assigns a conditional 1.5 V core rail plus reset/decoupling based on a documented host-camera pinout, not a received qualified module. Module-integrated regulators could change this count. It is **not** proof the original J1 nets are compatible.

### Optional new infrared illumination

For a qualified NoIR module, propose two or four independent branches from 3.3 V: 39 Ω 1206 0.25 W resistor → IR LED → low-side AO3400A C20917, with GPIO13 through 100 Ω gate resistor and 100 kΩ gate pull-down. Candidates: 850 nm VSMY1850ITX01 C7389210 or 940 nm VSMB2948SL C6604342, resistor C22198; gate resistor C22775 and pull-down C25803. Estimate about 42–50 mA per branch, 85–100 mA for two or 170–200 mA for four. LED forward-voltage spread and regulator tolerance determine actual current. Two LEDs add seven placements; four add eleven (56→67 with four buttons). Optical output, beam coverage, thermal and near-eye photobiological safety are **not qualified**; no wearable IR drive setting is approved here.

## 4. Two-port battery budget

[Battery CSV](bom-battery-arch2-two-port.csv): 38 grouped rows, **65 reserved positions**, comprising **62 PCB positions plus three external reservations** (cell NTC, independent pack protection/fuse, maintained power switch). The cell, cable and enclosures are outside that count. Nine positions remain without purchasing codes. This is an indicative circuit budget, not a placement-ready BOM.

The seven IC blocks are new BQ24074 charger/power-path, TPS61023 boost, TUSB320LA source CC, TPS2553 output switch, CH32V203 USB host, MAX17048 gauge and AP2112 3.3 V MCU supply. TPS61023 feedback 100 kΩ/11 kΩ targets approximately 5.045 V. Proposed Sunltech SLO0415S1R0MTT C325988 is 1 µH, Irms 4 A, Isat 6 A, 4.3 × 4 × 1.5 mm; its maximum 50 mΩ DCR can dissipate 0.45 W at 3 A. Do not call that thermally free. The 150 µF Ymin C51949781 output capacitor is 7.3 × 4.3 × 1.9 mm, subject to inrush/discharge review.

Open resistor reservations include input limit, termination, timer, USB series values and source-switch current limit; select after the schematic/power budget is frozen. The NTC network/charging-temperature window, PCM threshold and reverse-blocking circuit may add further components. One-port IP5310 and one-active-board architecture 1 have **different** BOMs; no unqualified exact total is claimed for them.

## 5. Source traceability

Source schematic, relative to the repository root:

`02-Embedded/02-Hardware/Eagle_Version/MAKKI_ISMAIL_ESPS3_CAM/MAKKI_ISMAIL_ESPS3_CAM/MAKKI_ISMAIL_ESPS3_CAM.sch`

Read the source audit for the commit/hash. The workbook observations refer to Sean's supplied `Analysis of BOM.xlsx`, preserved in the review records; it is not added here. Names such as `BB_*`, `CAM_*`, `B_*`, `BTN_*`, `C_USB_IN` are **documentary proposed designators**, not present-day CAD/CPL entries. Do not upload these CSVs as a production order without new schematics, footprints, assembly BOM and CPL reconciliation.
