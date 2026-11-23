# GEN 3 hardware design review — 23 November 2026

**For Sean Haughey, hardware and mechanical integration lead.** Product decisions: Amine Khettat. Camera sourcing/qualification to coordinate with Viet-An.

This is a documentary design review of Ali Makki's existing Eagle design, not a revised schematic, approved production BOM, purchase order or measured prototype. Existing Altium files may represent a different revision. No existing CAD, workbook or firmware is changed by this directory.

## Decisions and direct answers

- **Two temple-mounted enclosures remain the product layout.** Both electronic architectures are documented: one active camera PCB plus a protected passive battery pack; or separate camera and smart-battery PCBs.
- **Amine currently prefers two USB-C connectors on the smart battery**, one charge input and one power/data output, because they are simpler to manage. One-port IP5310 DRP remains an alternative requiring qualification, not the selected baseline.
- **Battery 3.0–4.2 V to 5 V needs a boost, not a buck-boost.** In the one-active-board architecture, the camera's raw-cell-to-3.3 V regulator instead needs buck-boost operation. These are different stages.
- **Existing versus new:** U3 MCP73831 is already in Eagle. MAX17048, IP5310 and the proposed 5 V boost are new relative to Eagle. BQ24074 is an optional new replacement for U3 when proper power-path/NTC is wanted. TPS63802 is already proposed in the 46-part review, but is not fitted in the original Eagle design.
- **Phone performs YOLO**, not ESP32. Aim approximately 5 frames/s at the model's input size, typically 640 × 640; phone is the nearby Wi-Fi access point. No retained local audio amplifier/microphone or original four-LED illumination block.
- **OV3660 DVP, NoIR required; not yet qualified.** Supplier listings do not establish a filter-free delivered revision or exact flex compatibility. Connector purchase follows module qualification.
- **Low-battery voice feedback needs telemetry.** Preferred production direction: MAX17048 plus battery-side USB-host MCU, ESP32-S3 USB device, then Wi-Fi to the phone. Raw UART on USB D+/D− is only a qualified, isolated proprietary prototype option.
- **Three-hour runtime is not established with a 1000–1500 mAh thin cell.** No examined thin-cell offer is qualified for the requested ≥2 A continuous rating; see the explicit power and mass trade-off.

## Reading order

1. [Architectures and cable interface](01-architectures.md): both architectures, one/two-port variants, Mermaid plus ASCII, telemetry and power budgets.
2. [Battery chips: existing versus new](02-battery-chips.md): why particular parts are retained, moved, replaced or rejected.
3. [BOM accounting and net-based decisions](03-bom-and-net-delta.md): original 68 → target 46, then the separate 56-placement camera proposal and 65-position battery budget.
4. [OV3660 modules and conditional connectors](04-camera-modules.md).
5. [Buttons and mechanical integration](05-buttons-and-mechanics.md).
6. [Discrepancies found in Analysis of BOM.xlsx](06-workbook-discrepancies.md): factual observations, not edits to Sean's workbook.
7. [Remaining decisions and owners](07-remaining-work.md).
8. [Fresh JLCPCB reference catalogue](08-jlcpcb-reference-catalogue.md), with manufacturer, exact MPN, stock/quota, Basic/Extended, indicative USD price, role and datasheet link for every cited C-code.

## Machine-readable files

- [46-placement baseline](bom-camera-baseline-target46.csv).
- [56-placement camera, architecture 2, day-only proposal](bom-camera-arch2-rev2.csv).
- [65-position smart-battery budget, architecture 2, two ports](bom-battery-arch2-two-port.csv).
- [JLCPCB reference catalogue](jlcpcb-reference-catalogue.csv).
- [Source audit](source-audit.json).

The three BOMs are **alternatives/stages, not quantities to add together**. A quantity is a physical placement; a resistor array is one placement. Empty purchase references and conditional footprints remain deliberately open. Extended parts, assembly fees, effective ceramic capacitance, thermal performance and enclosure clearances still need engineering review.

## Location and branch

The root README's contributing table assigns schematics, PCB, power and buttons to `CYCLOPE_GEN3_HW`, under `02-Embedded/02-Hardware/`. Its governance commit is `2a386bfb4b065d00b9fc5a9dd63239f78151ab5d`. The repository also has `00-Documentation/02-Embedded/` for general meeting notes/specifications; this self-contained, hardware-specific review is placed beside its CAD sources in a new subdirectory to keep its BOM and net traceability together. Markdown and CSV are not LFS file types under the existing `.gitattributes`.

All stock observations are dated snapshots, not reservations. A catalogue match or stock quantity is not electrical, mechanical or procurement qualification. No measurements, charging tests, thermal tests, optical tests or USB compliance tests were performed.
