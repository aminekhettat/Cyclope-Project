# Buttons and enclosure integration

Amine validated **2–4 tactile user buttons** plus printed caps; the BOM budget uses four. Candidate **C&K KMR232NGULCLFS, C221681**, 3 N actuation, ultra-low-current contact option, with printable caps that transfer force without loading the PCB sideways. Compare the exact manufacturer's mechanical drawing to the existing `BTN_KMR2_4.6X2.8` footprint: family name alone does not prove the footprint.

| Function reservation | ESP32 GPIO | Proposed input circuit |
|---|---:|---|
| User button 1 | 9 | Button to GND, 10 kΩ pull-up to 3.3 V |
| User button 2 | 10 | Same |
| Optional user button 3 | 11 | Same |
| Optional user button 4 | 12 | Same |

10 kΩ pull-up is C25804. Firmware debounce proposal 20–30 ms; tap/long-press functions are a PO/firmware decision. Keep all inputs accessible without relying on visual labels. Add tactile distinctions and text documentation. SW1/SW2 remain separate maintenance boot/reset switches and are not counted among the four user controls. GPIO9–12 are free in the extracted source; confirm the final revised design before routing.

Mechanical work for Sean: PCB outline, antenna keep-out and orientation, flex bend/strain relief, camera lens axis, button pitch/cap travel/stops, printed wall thickness, cable routing behind head and load transfer to glasses temples. The cell's **full pack** dimensions/PCM/tabs and swelling clearance are required, not bare-pouch dimensions. USB-C connector retention and ESD return paths affect both boards/enclosures. Do not place metal or battery over the ESP32 antenna keep-out. Align the decision between a raw-cell dedicated connector in architecture 1 and the standard USB-C 5 V interface in architecture 2 before freezing openings.

One or two battery ports changes the enclosure; two-port preference is current PO direction, not a reason to discard the documented one-port variant. Battery mass and charge temperature must be reviewed against wearing comfort and the total 40–50 g target.
