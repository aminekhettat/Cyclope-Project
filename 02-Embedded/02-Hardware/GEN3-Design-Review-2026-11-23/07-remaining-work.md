# Remaining work, decisions and owners

This is a design hand-off, not release readiness. Resolve the following before CAD freeze or purchasing.

| Work item | Primary owner | Required evidence / decision |
|---|---|---|
| Electronic architecture | **Amine**, with Sean's feasibility advice | Choose one active board with a dedicated raw-cell interconnect, or two active boards with standard USB-C 5 V. Resolve the passive-pack/standard-USB conflict explicitly. |
| Smart-battery port count | **Amine + Sean** | Current preference is **two ports**. Keep IP5310 one-port as an option only after variant/current/CV/DRP qualification. |
| Camera sourcing and NoIR | **Viet-An**, coordinated with Amine and Sean | Exact OV3660 SKU/revision, no IR-cut proof, lens transmission, dimensions, delivered sample and full flex/pinout documentation. No purchase/contact has been made by this review. |
| Camera connector and rails | **Sean**, after Viet-An's module evidence | Top/bottom mating contact proof; pin1; footprint; DVDD/DOVDD; data bit order; reset/XCLK/SCCB levels; pins23/24. |
| Cell and 3 h autonomy | **Amine requirements; Sean electrical/mechanical validation** | No examined 1000–1500 mAh pack meets ≥2 A continuous. Select documented cell/PCM rating; allow ≥3 A if full 5 V/1.5 A source is advertised. Measure average/peaks and weigh the complete assembly; arbitrate larger pack vs shorter runtime/current reduction. |
| Charger choice and thermal | **Sean** | Reuse MCP73831 only with its charge-only limitations; otherwise qualify new power-path charger, cell CV/current/NTC window, timer/termination and enclosure temperature. |
| Camera regulator | **Sean**, trade-off confirmed by Amine | Keep TPS63802 to minimise change or use Extended TPS562201 after cable-drop validation; consider lower-input-voltage buck if 4.5 V margin is inadequate. No suitable Basic drop-in was verified. |
| Battery telemetry | **Sean hardware + ESP32 firmware owner + Android team** | Prefer MAX17048 + native USB host MCU; prove host/device enumeration, safe flash access, protocol and stale/fault behaviour. UART requires isolation/mux and proprietary-mode qualification. |
| User-button number and actions | **Amine**; Sean mechanical/electrical implementation | Choose 2, 3 or 4; GPIO9–12, KMR232 plus tactile caps. Agree user functions/debounce with firmware owner. |
| Optional IR | **Amine need; Viet-An optical; Sean electrical/mechanical** | True NoIR, wavelength/beam/power, eye-proximity optical safety and thermal; add the driver only after evidence. |
| CAD baseline reconciliation | **Sean** | Reconcile Eagle vs current Altium, D3/JP1/X1 and workbook discrepancies. Issue revised schematic/PCB, BOM/CPL and enclosure drawings. |
| Protection and USB | **Sean** | Source attach/disable timing, current limit, inrush/discharge, reverse blocking, overvoltage/short faults, ESD, cell protection and NTC. Qualify actual C-to-C cable both orientations. |
| Tests before manufacture | **Sean and integration owners** | Power/efficiency/thermal across cell range; charging unloaded/loaded; brownout and reconnect; USB roles/flash; camera low-light/IR/focus; radio and power at 5 fps; button accessibility and worn mass. |

No Gerbers, CPL, firmware, production order, supplier communication or release claim is included. All stock must be checked again when the engineering BOM is frozen. Basic/Extended classification and an in-stock flag are procurement observations, not proof of the chosen circuit's correctness.
