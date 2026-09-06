# Cyclope

**A street-crossing assistant for blind and visually impaired people.**

The white cane tells you where to put your foot. GPS tells you where to go.
At the kerb, nothing speaks. Cyclope addresses that exact moment: recognising a
crosswalk, reading the state of a pedestrian traffic light, and guiding by voice
to a spoken address.

Developed by **[Blind Systems](https://www.blindsystems.org)** together with
successive cohorts of embedded-systems engineering students.

---

## Table of contents

- [About the project](#about-the-project)
- [Three generations](#three-generations)
- [Generation 3 architecture](#generation-3-architecture)
- [Repository layout](#repository-layout)
- [Getting started](#getting-started)
- [The four work packages](#the-four-work-packages)
- [Contributing](#contributing)
- [License and contact](#license-and-contact)

---

## About the project

Existing assistive devices — OrCam MyEye, Envision Glasses, Aira, Be My Eyes —
read text, describe scenes and recognise objects. None of them answers the two
questions that come up at every street corner: **where exactly is the crosswalk,
and is the light green for me?**

Cyclope deliberately limits itself to that problem. It is narrow enough to be
solved properly, rather than half-solved inside a device that claims to do
everything.

The project has three goals:

1. **On-device detection** of crosswalks and pedestrian traffic lights, with
   immediate voice feedback.
2. **Pedestrian navigation** guided by voice, from a spoken address.
3. **Map contribution**: report unmapped crosswalks to OpenStreetMap, after the
   user confirms by voice.

Cyclope is a research and development project. **It is not a product for sale,
and it does not replace the white cane.**

---

## Three generations

| | Period | Worn hardware | Where the intelligence runs | Limitation hit |
|---|---|---|---|---|
| **GEN 1** | 2024-2025 | 3D-printed glasses + ESP32-CAM; Raspberry Pi and battery in a backpack | Raspberry Pi, locally | Bulk: a computer carried on the back, a cable running down the face |
| **GEN 2** | 2025-2026 | 3D-printed glasses, board and battery in a central compartment | Remote AI service, reached through the phone over 4G | Dependence on a paid API, several seconds of latency |
| **GEN 3** | in progress | Two small enclosures clipped onto the temples of ordinary glasses | The user's own phone, offline | — |

### Generation 1 — the computer in the backpack

![Cartoon illustration of a blind man in profile holding a white cane. He wears bulky grey 3D-printed glasses with a large box on the forehead housing a camera. A black cable runs from the box down past his ear into an open backpack containing a green Raspberry Pi board and a battery.](00-Documentation/images/gen1.png)

An ESP32-CAM mounted on 3D-printed glasses streamed images over wifi to a
Raspberry Pi running the detection model. It worked — but the user had to carry a
computer and its battery on his back, with a cable hanging along his face.

### Generation 2 — the intelligence moved to the cloud

![Cartoon illustration of the same man. The backpack and cables are gone. A single 3D-printed box sits at the centre of the glasses frame, in front of the eyes. Blue wifi waves link the glasses to a smartphone; orange signal bars and a dotted line link the phone to a cloud symbol representing a remote AI service. A speech waveform shows voice interaction.](00-Documentation/images/gen2.png)

The backpack disappeared: board and battery moved into a compartment built into
the glasses, and image analysis moved to a remote AI service reached through the
user's phone. Lighter to wear — but recognition now depended on a paid API and on
the mobile network, with several seconds of latency. Good enough to describe a
scene, not to warn in time.

### Generation 3 — computation moves back into the pocket

![Cartoon illustration of the same man wearing his own ordinary thin-framed glasses. A small industrial-looking module is clipped to each temple, joined by a thin cable running behind his head. Blue wifi waves link the module to a smartphone showing a processor chip icon; orange signal bars link the phone to a cloud carrying only a map pin.](00-Documentation/images/gen3.png)

The current version inverts the logic. The smartphone already in the user's
pocket becomes the brain: the detection model runs on it locally, offline and at
no cost. The network is only used for maps and addresses. On the hardware side we
stopped making glasses altogether — Cyclope is now a small module that clips onto
the user's own frames.

The common thread: **computation moves closer to the user, and cost tends
towards zero.**

Two lessons carried over from the previous generations:

- **We no longer build glasses.** Three runs of 3D-printed frames gave
  disappointing results in size, comfort and hinge robustness. GEN 3 clips onto
  the pair the person already chose.
- **We no longer depend on a remote AI for real-time detection.** Network latency
  is incompatible with an alert at the edge of a pavement.

---

## Generation 3 architecture

```
   Glasses module                  Android phone                   Internet
   ──────────────                  ─────────────                   ────────
   ESP32-S3 + camera   ── wifi ──▶  YOLO detection     ── 4G/5G ──▶  Nominatim
   2 buttons                        offline                          OSRM
   battery (2nd enclosure)          Voice feedback                   OpenStreetMap
                                    Navigation
```

- The **phone acts as the wifi access point**; the camera module connects to it
  as a client. No network setup is left to the user.
- **Visual detection runs offline**, on the phone.
- **Guidance requires an active data connection**: without internet, no address
  lookup and no route computation.
- **Voice feedback** is produced by the phone, which routes it to its own audio
  output (module speaker, wired or Bluetooth earphones).

GEN 3 hardware constraints: two enclosures joined by a USB-C cable running behind
the head, one holding the camera, the board and the buttons, the other the
battery, for balance. Mass target: **40 to 50 g**, with the OrCam MyEye 3 Pro
(22.5 g) as the market reference to keep in sight.

---

## Repository layout

This repository holds the whole project. It was assembled by merging the
historical per-topic repositories, **with full history and all branches
preserved**.

| Folder | Contents | Source repository |
|---|---|---|
| `00-Documentation/` | Cross-cutting documentation: meeting notes, specifications, reports | — |
| `01-Raspberry/` | GEN 1 server: stream reception, detection, recording | `Cyclope_Raspberry_Pi` |
| `02-Embedded/01-Software/` | ESP32 camera module firmware | `Cyclope_ESP32CAM` |
| `02-Embedded/02-Hardware/` | Schematic and PCB layout of the camera board | `Cyclope_ESP32CAM` |
| `03-Vision/` | YOLO model training, data augmentation, tests | `Cyclope_Vision` |
| `04-3D_Design/` | SolidWorks parts and STL files for the enclosures | `Cyclope-3D` |
| `05-MobilApp/` | Mobile applications: vision, navigation, voice feedback. `Android/` holds the current app, `iOS/` is reserved for a future port | `Cyclope_Androis` |

Folder numbering follows the chronological order of the project and fixes the
display order; it does not imply any dependency between work packages.

---

## Getting started

### Prerequisites per work package

| Package | Tools |
|---|---|
| `02-Embedded/01-Software` | ESP-IDF or Arduino IDE, ESP32-S3 board |
| `02-Embedded/02-Hardware` | **Altium Designer** (mandatory), parts verified as available at JLCPCB |
| `03-Vision` | Python 3.10+, Ultralytics, PyTorch |
| `04-3D_Design` | **SolidWorks** (mandatory) |
| `05-MobilApp/Android` | Android Studio, Kotlin, LiteRT |
| `05-MobilApp/iOS` | Xcode, Swift, Core ML - planned, not started |

### Clone

```bash
git clone https://github.com/ACCOUNT/Cyclope-Project.git
cd Cyclope-Project
```

### Train a detection model

```bash
cd 03-Vision
pip install -r requirements.txt      # create it if missing
python trainv11.py
```

### Large files: Git LFS

Model weights and CAD files are stored with **[Git LFS](https://git-lfs.com)**.
Install it once before cloning, otherwise those files arrive as small text
pointers instead of real content:

```bash
git lfs install
git clone https://github.com/ACCOUNT/Cyclope-Project.git
```

If you cloned before installing LFS, run `git lfs pull` to fetch the real files.

The following patterns are tracked, as recorded in the root `.gitattributes`:

```
*.pt  *.pth  *.onnx  *.tflite        model weights
*.STL  *.SLDPRT  *.SLDASM  *.SLDDRW  CAD parts and assemblies
```

Everything else — source code, documentation, images — stays in plain Git.

---

## The four work packages

GEN 3 is split into four independent subjects, each sized for **100 hours of one
student**. Every package can start immediately, without waiting for the others.

### 1. Electronics and enclosure — `02-Embedded/02-Hardware`, `04-3D_Design`

Adapt the existing GEN 2 schematic and redo the layout: replace the LDO with a
buck-boost converter, add at least two buttons, verify that every part is
available at JLCPCB. Design the two clip-on enclosures in SolidWorks. Assembly is
outsourced to JLCPCB: no soldering for the student. The ESP32-S3 is used as an
integrated module (antenna, SRAM, flash), which is out of scope for this project.

### 2. Embedded firmware — `02-Embedded/01-Software`

Image capture and compression, audio recording of voice commands (without
interpreting them), button handling, wifi link monitoring with a voice alert if
the connection drops.

### 3. Vision on Android — `05-MobilApp/Android`, `03-Vision`

Stream reception, on-device model inference, voice feedback. The student starts
with the already working DFRobot module from GEN 2, without waiting for the new
board.

### 4. Pedestrian navigation — `05-MobilApp/Android`

Spoken address input, geocoding through Nominatim, route computation, voice
guidance through TalkBack, positioning through the Fused Location and Fused
Orientation Providers. Includes the OpenStreetMap contribution feature. The
M5Stick C Plus serves as the button interface until the final module is ready.

---

## Contributing

### Branches

`main` is the reference branch and must always be in working order.

Working branches are prefixed by their domain:

```
embedded/…      raspberry/…      vision/…      android/…      design3d/…
```

Branches inherited from the original repositories keep that prefix
(`embedded/AMA_Work`, `vision/text_to_speech`, `raspberry/feat_YOLO_Serveur`, …).
The `<domain>/main` branches are a snapshot of each repository at merge time and
are kept as a safety net.

### Commit messages

One imperative summary line, prefixed by the domain:

```
vision: add brightness-variation augmentation
embedded: fix wifi reconnection after access point loss
```

### Before pushing

- Never commit SolidWorks lock files (`~$…`), IDE settings (`.vs/`, `.idea/`) or
  build artefacts. The root `.gitignore` already excludes them.
- Large binary files must go through Git LFS. If you add a new binary type,
  register it first with `git lfs track "*.ext"` and commit the updated
  `.gitattributes` in the same change.

---

## License and contact

This project is released under the **Apache 2.0** license. See the `LICENSE` file.

- Website: [www.blindsystems.org](https://www.blindsystems.org)
- Contact: [contact@blindsystems.org](mailto:contact@blindsystems.org)

Field feedback — especially from blind and visually impaired users, occupational
therapists and orientation and mobility instructors — is what moves this project
forward the most.
