# OV3660 modules — sourcing evidence and connector traps

## Selection status

OV3660, 3 MP (2048 × 1536), parallel DVP, ESP32-S3 capture is the requirement. The phone runs inference. **No examined offer has a delivered NoIR revision, removable IR-cut filter, near-IR performance and exact flex interface all qualified.** “OV3660”, lens field of view or seller RGB language does not establish NoIR. No examined strict-OV3660 offer documents integrated 850/940 nm LEDs. All prices/availability below are website observations on 23 November, not purchase/stock reservations.

## Five purchasable leads

| Seller / model / link | Price and observed availability | Sensor / interface | NoIR / IR LEDs | Flex, contact face, size and pinout certainty |
|---|---|---|---|---|
| [Waveshare ESP32-S3-CAM-OV3660, SKU 33700](https://www.waveshare.com/esp32-s3-cam-ov5640.htm) — choose **With OV3660 camera** | Page range USD 16.99–21.99 across kit variants; OV3660 variant flagged saleable; exact variant quantity/price not exposed | OV3660, 3 MP, DVP, documented ESP32-S3 host | NoIR/filter removal not stated; no module IR LEDs documented | Host schematic has 24-pin connector and documented host pinout; flex pitch/thickness/contact face not qualified from delivered module drawing. Kit includes board/camera/speaker; separate camera-only sale not established. Obtain module outline including lens and flex bend envelope. |
| [ESP32s.com upgraded CM16413](https://esp32s.com/product/upgraded-ov3660-camera-module-for-esp32-cam-esp32-s3-development-board-68-120-160-degrees-anti-interference-rgb-hd-3mp-dvp-24pin/) | USD 7.80–10.10; listed variants purchasable | OV3660, 3 MP; DVP **24-pin** explicit | Neither NoIR nor filter removal/IR LEDs documented | Camera-only offer; lens options roughly 66/120/160 degrees. No usable exact pitch, flex thickness, contact-side drawing, dimensions/pinout guarantee published in collected text. |
| [ESP32s.com standard CM16502](https://esp32s.com/product/ov3660-camera-module-3-million-pixels-65-80-120-160-degree-dvp-interface-yuv-output-supports-esp32-single-chip-camera/) | USD 6.50–8.65; observed variant stock about 193–198 | OV3660, 3 MP; DVP explicit | NoIR and LEDs not documented | Several fields of view; 24-pin not independently proven for this specific listing. Pitch, thickness, contact face, outline and complete pinout unknown. |
| [ESP32s.com 75 mm CM16517](https://esp32s.com/product/75mm-ov3660-camera-module-for-esp32-cam-esp32-s3-development-board-68-80-120-160-degrees-yuv-rgb-hd-3mp-dvp-24pin-interface/) | USD 16.90–23.90; in-stock flags, but absurd machine stock figures (10^11–10^15) are **not credible quantities** | OV3660, 3 MP, 24-pin DVP explicit | NoIR and LEDs not documented | 75 mm advertised length; complete outline, pitch/thickness/contact side and rails not qualified. Long flex can increase DVP signal-integrity risk. |
| [ESP32s.com adjustable CM34828](https://esp32s.com/product/ov3660-camera-module-3mp-adjustable-lens-66-160-fov-80mm-length/) | USD 6.90–8.20; in-stock flag | OV3660, 3 MP; exact DVP pinout not independently documented for this article | NoIR and LEDs not documented | 80 mm advertised length, adjustable lens 66/160 degrees; remaining mechanical/connector data unknown. |

Four offers are from the same seller, **not four independently qualified supply chains**. Shipping, invoice, module marking/revision and sample conformity are not verified.

### Top two qualification priorities, not purchase approval

1. **Waveshare OV3660 kit SKU33700** for its official host schematic and a known development context; ask for the exact camera-only SKU/revision and NoIR capability. Conditional connector is C202112 if bottom-contact geometry is proven, C506794 if top-contact geometry is proven. No exact pairing is certified.
2. **ESP32s CM16413** for its explicit camera-only, 24-pin DVP offer. Obtain the signed pinout/flex mechanical drawing and filter configuration first; same conditional connector choices, with C49166984 as an alternative only after drawing/sample review.

## Other brands and rejected ambiguity

- Current [DFRobot DFR1154 product 2899](https://www.dfrobot.com/product-2899.html) lists **GC2145 2 MP**, with a 940 nm night feature, around USD 18.90. Older OV3660 references/images do not establish the shipped revision. It is not a qualified OV3660 selection.
- M5Stack **UnitV2 U078-D uses GC2145**, and **UnitCamS3 U174 uses OV2640** in the checked official specifications. Do not infer OV3660 from the product family name. A camera-only OV3660 purchase route was not established.
- Arducam/UCTRONICS searches did not yield a verifiable suitable OV3660 purchase offer in this review. That is not a claim that none can ever be supplied.
- AI-Thinker/marketplace and Aideepen/UICPAL leads lacked the required stable delivered revision, price/stock or mechanical/NoIR documentation. They are not qualified alternatives.

## FPC candidates — contact side is a gating criterion

| Code / manufacturer / exact MPN | Documented connector geometry | Status |
|---|---|---|
| **C202112 / Hirose / FH12-24S-0.5SH(55)** | 24 positions, 0.5 mm pitch, **bottom contact**, FPC 0.30 ±0.05 mm, rotary ZIF/flip-lock, 2.0 mm height | Conditional default only after module contact face/pin1/rails match. |
| **C506794 / Hirose / FH12A-24S-0.5SH(55)** | 24 positions, 0.5 mm pitch, **top contact**, FPC 0.30 ±0.05 mm, ZIF/flip-lock, 2.0 mm height | Top-contact alternative, not an automatic footprint/pinout replacement. |
| **C49166984 / HongCheng / HC-FPC-0.5-24P-FHH10** | Catalogue advertises top/bottom dual contacts; drawing closed height **0.80 ±0.05 mm** (catalogue class 1 mm), FPC 0.30 ±0.03 mm, 24 positions, 0.5 mm | Drawing/catalogue actuator wording differs; verify actual dual contact, retention and pad mapping. Lower cost/height does not prove compatibility. |
| **C3169263 / TE / 2-2328702-4** | Existing Eagle connector | Exact purchasing code remains stock-zero in the reviewed snapshots; replacement requires a new footprint/module interface review. |

Fresh stock, Basic/Extended, price, exact partdetail and datasheet links for each appear in the reference catalogue. These are separate new footprint candidates, not drop-in replacements for the original TE footprint. A photo showing exposed copper without a defined viewing convention is insufficient: request the mating-face view, pin1 marking, copper side relative to lens/PCB and stiffener thickness. Never turn a lack of contact-side documentation into compatibility.


### Fresh connector snapshot

| Code | Stock / quota | Classification | Indicative USD |
|---|---:|---|---:|
| C202112 | 2388 / 2163 | Extended | 0.5155 |
| C506794 | 1393 / 947 | Extended | 0.8264 |
| C49166984 | 2399 / 2379 | Extended | 0.129 |
| C3169263 | 0 / 0 | Extended | 0.4036 |

## Documented Waveshare host pinout — warning, not a universal module contract

Official [board documentation](https://docs.waveshare.com/ESP32-S3-CAM-OVxxxx) and [Rev1.1 schematic](https://files.waveshare.com/wiki/ESP32-S3-CAM-OVxxxx/ESP32-S3-CAM-OVxxxx_Rev1.1.pdf) identify the host connector:

| Pin(s) | Waveshare host function |
|---|---|
| 1 / 2 | NC / AGND |
| 3 / 4 / 5 | SCCB SDA / AVDD 2.8 V via filter / SCCB SCL |
| 6 / 7 / 8 / 9 | RESET / VSYNC / PWDN / HREF |
| **10 / 11** | **DVDD 1.5 V / DOVDD 2.8 V** |
| 12 / 13 / 14 / 15 | Y9=D7 / XCLK / Y8=D6 / DGND |
| 16 / 17 / 18 | Y7=D5 / PCLK / Y6=D4 |
| 19 / 20 / 21 / 22 | Y2=D0 / Y5=D3 / Y3=D1 / Y4=D2 |
| 23 / 24 | Shared-board Y1/AF_VDD and Y0/AF_GND paths; exact OV3660 delivered module use needs confirmation |

**Critical mismatch:** Cyclope's old connector pin10 is ground and pin11 is 3.3 V. They are not the above 1.5/2.8 V assignments. No direct insertion is approved. Also qualify reset pull-up voltage, 3.3 V ESP32 XCLK/SCCB compatibility with actual DOVDD, PWDN, camera core current and whether the module integrates regulators. Do not merely rename nets.

Existing 8-bit DVP allocation to preserve where feasible: D0–D7 = GPIO5,6,17,15,7,16,18,38; PCLK8; XCLK39; HREF14; VSYNC21; SCCB SDA4/SCL3. GPIO3 is a strapping pin and external bias/boot effects need review; GPIO35–37 are not available with the specified N16R8 Octal-PSRAM module. The source uses eight captured data bits, not ten despite the sensor's wider options.

## Qualification request for Viet-An and Sean

Obtain exact seller SKU, module PCB/flex revision and OV3660 marking; written **no IR-cut filter** statement; lens spectral transmission at chosen 850/940 nm; minimum focus/FOV; sample low-light images; complete pinout including all power rails and I/O limits; flex 24-pin count/pitch/thickness/copper face/stiffener/pin1 and full dimensions. Sean then selects connector/footprint and power rails. An IR driver is optional only after optical qualification; see the BOM note. No vendor was contacted and no module purchased during this documentary review.
