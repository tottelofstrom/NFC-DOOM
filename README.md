# NFC-DOOM

A native Android app (Kotlin + Jetpack Compose) that turns an **NFC implant into a
tiny "Doom cartridge"**. The phone runs a small Doom/Wolfenstein-style first‑person
raycaster; the implant is used **only as passive cartridge storage**.

> **Concept:** This is a *Doom‑like game loaded from an NFC implant cartridge.*
> Doom does **not** run on the implant. The NFC implant is not a computer — it is a
> ~1 KB tag that stores a compact binary "cartridge" describing one level. When the
> phone scans the implant, the app reads the cartridge and starts the game.

---

## What it does

* Reads NFC **NDEF** records from an implant and looks for a custom MIME record:
  * **MIME type:** `application/vnd.implantdoom.cartridge`
* Parses a compact binary **cartridge format (v1)** that fits under ~1000 bytes.
* Launches a simple first‑person **raycaster** using the cartridge data.
* Ships with a built‑in **demo cartridge** so it works with no NFC at all
  (emulator‑friendly).
* Includes a **cartridge builder/writer** that generates a cartridge and writes it
  to a writable NDEF tag.
* Includes a read‑only **NFC/Type‑5 diagnostics** screen (UID, tech list, NDEF
  availability/size, records, MIME detection, and ISO 15693 / NFC‑V details).
* Keeps **all data local** — no network, no vendor app/API/server/SDK/cloud.

## What it is **not**

* It contains **no id Software Doom assets**: no original IWAD data, title screens,
  sprites, textures, music, sound effects or maps, and not the trademarked DOOM logo.
  The first‑person artwork is from **Freedoom** — a free, BSD‑licensed Doom‑compatible
  art set (attribution in `app/src/main/assets/doomgfx/`), decoded into PNGs by a small
  WAD parser written for this project. The engine is an original Doom‑like raycaster.
* It does **not** use Dsruptive's app, APIs, servers, SDKs or cloud.
* It does **not** request the `INTERNET` permission.

---

## The tag

* Expected tag type: **NFC Forum Type 5 / ISO 15693 / NFC‑V** (e.g. an NXP ICODE
  DNA style implant).
* The reference implant exposed **256 blocks × 4 bytes = 1024 bytes** of raw memory,
  leaving roughly **1000 usable NDEF bytes**.
* ISO 15693 UIDs start with `E0` and are 8 bytes; tools differ on byte order — some
  show them MSB‑first (`E0 04 …`), others reversed (`… 04 E0`). The diagnostics screen
  prints both orders for whatever tag you scan. (No specific UID is committed here.)

### Capability Container note

For Android home‑screen NDEF reading to work on the reference implant, **block 00
(the Type‑5 Capability Container) had to be `E1 40 80 09` (hex `E1408009`)**. An
earlier broken value `E140FF09` advertised too much memory and stopped Android / NXP
apps from detecting NDEF correctly.

**This app never writes block 00**, lock bits, AFI, DSFID or passwords. The CC value
is shown in the app for reference only.

---

## Cartridge format v1

All integers are **little‑endian** unless noted. Total layout:

```
[ Header 20 B ][ Map 128 B ][ Entities 4·N ][ Items 3·M ][ CRC32 4 B ]
```

### Header (20 bytes)

| Offset | Size | Field            | Notes                                            |
|-------:|-----:|------------------|--------------------------------------------------|
| 0      | 5    | magic            | ASCII `IDOOM`                                     |
| 5      | 1    | version          | `0x01`                                            |
| 6      | 1    | flags            |                                                  |
| 7      | 1    | mapWidth         | 16                                               |
| 8      | 1    | mapHeight        | 16                                               |
| 9      | 1    | playerStartX     | 0–15                                             |
| 10     | 1    | playerStartY     | 0–15                                             |
| 11     | 1    | playerStartAngle | 0=E, 64=S, 128=W, 192=N                           |
| 12     | 4    | seed             | uint32                                           |
| 16     | 1    | textureThemeId   |                                                  |
| 17     | 1    | entityCount      |                                                  |
| 18     | 1    | itemCount        |                                                  |
| 19     | 1    | reserved         | must be 0 for now                                |

### Map (128 bytes for 16×16)

256 cells × 4 bits, row‑major, **two cells per byte**: low nibble = first
(even‑index) cell, high nibble = second (odd‑index) cell.

Tile IDs: `0` empty, `1` wall, `2` door, `3` exit, `4` hazard, `5–15` reserved.

### Entities (4 bytes each)

`x, y, type, flags` — types: `1` basic enemy, `2` turret, `3` boss placeholder.

### Items (3 bytes each)

`x, y, type` — types: `1` health, `2` key, `3` ammo placeholder.

### Footer (4 bytes)

`CRC32` (standard, little‑endian) of **all preceding bytes** (everything except the
trailing 4‑byte CRC itself).

### Size

The default demo cartridge is **208 bytes**:

```
Header 20 + Map 128 + Entities 8·4=32 + Items 8·3=24 + CRC 4 = 208
```

This is intentionally small and leaves room for future features. The codec refuses
to build/accept anything larger than **1000 bytes**.

---

## NDEF serialisation

The cartridge is stored as a **single MIME NDEF record** with type
`application/vnd.implantdoom.cartridge` and the raw cartridge bytes as the payload.
It is **not** stored as Text or URI. (A debug‑only URI helper exists in
`NdefCartridge` but is never the primary format.)

A custom MIME type lets Android auto‑launch the app from a written implant via the
`NDEF_DISCOVERED` intent filter.

---

## Project structure

```
app/src/main/kotlin/com/implantdoom/
├── MainActivity.kt            NFC adapter + foreground dispatch + intent routing
├── cartridge/                 Cartridge model + binary codec (pure Kotlin)
│   ├── Cartridge.kt           Data model, tile/entity/item types
│   ├── CartridgeCodec.kt      Encode/decode, nibble packing, CRC32
│   ├── CartridgeException.kt
│   └── DemoCartridge.kt       Built-in demo + seeded MapGenerator
├── nfc/                       NFC layer
│   ├── NdefCartridge.kt       MIME record (de)serialisation
│   ├── NfcReader.kt           Tag -> diagnostics + parsed cartridge (read-only)
│   ├── NfcWriter.kt           NDEF write / format (never locks)
│   └── NfcVDiagnostics.kt     Read-only ISO 15693 system-info + block reads
├── game/                      Raycaster engine (pure Kotlin, no Compose)
│   ├── GameLevel.kt           Mutable level state from a cartridge
│   ├── Raycaster.kt           Grid DDA raycaster + line-of-sight
│   ├── GameState.kt           Player, vitals, update loop, shooting
│   └── Textures.kt            Procedural colours (no imported assets)
└── ui/                        Jetpack Compose UI
    ├── NFC-DOOMApp.kt      NavHost over the 7 screens
    ├── AppViewModel.kt        Single source of truth + NFC orchestration
    ├── Components.kt          MapPreview, HexDump, InfoRow, HoldButton…
    └── screens/               Home, Scan, Details, Play, Builder, Diagnostics, About
```

Unit tests live in `app/src/test/kotlin/com/implantdoom/cartridge/`.

---

## Build & run

### Requirements

* JDK 17
* Android SDK with **platform 35** and **build‑tools 35** (and an NFC‑capable device
  for the NFC features; the demo + builder work on the emulator).

The repo includes a Gradle wrapper (Gradle 8.9, AGP 8.7.2, Kotlin 2.0.21).

### From the command line

```bash
# Build the debug APK
./gradlew :app:assembleDebug          # Windows: gradlew.bat :app:assembleDebug

# Run the unit tests
./gradlew :app:testDebugUnitTest

# Install on a connected device/emulator
./gradlew :app:installDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

> `local.properties` must point `sdk.dir` at your Android SDK. Android Studio writes
> this automatically; it is git‑ignored.

### From Android Studio

Open the project folder and let it sync, then Run ▶ the `app` configuration. Any
recent Android Studio (Koala / Ladybug or newer) works.

---

## How to write a demo cartridge

1. Open **Build / write a cartridge** from the home screen.
2. (Optional) tap **Load the built‑in demo cartridge**, or tweak seed / entities /
   items / theme to generate your own. The screen shows a live map preview, the byte
   count, the size‑limit check and the CRC status.
3. Tap **Arm write — then tap implant**.
4. Hold your implant to the phone's NFC antenna. The app writes a single MIME NDEF
   record and reports success/failure. If the tag is blank but `NdefFormatable`, it
   is formatted first (never locked / never made read‑only).

## How to scan and play

* **With an implant:** open **Scan implant cartridge** and hold the implant to the
  phone. If it carries an NFC-DOOM cartridge, the app loads it and offers
  **Details** / **Play**. A written implant will also cold‑launch the app straight
  into the cartridge.
* **Without NFC:** tap **Play built‑in demo cartridge** on the home screen, or load
  the demo from the Scan / Builder screens.

### Controls

On‑screen buttons: rotate left/right, move forward/back, and a fire button. Walk into
the green **exit** tile to finish the level. Hazard tiles and enemies hurt you;
health/ammo/key pickups help.

---

## Safety & privacy

* No `INTERNET` permission; the app cannot reach the network.
* Tag data never leaves the device.
* Writing only ever writes an NDEF message. The app does **not** lock tags, set
  passwords, write‑protect, or modify AFI / DSFID / lock bits / CC / security.
* The raw ISO 15693 block reads on the diagnostics screen are **read‑only** and gated
  behind a clearly labelled developer toggle.

## License / content

This project implements an original Doom‑like engine. The bundled first‑person art is
from **Freedoom**, used under its BSD licence (see the files under
`app/src/main/assets/doomgfx/`). It does **not** include or redistribute id Software's
copyrighted Doom data or the trademarked DOOM logo. "Doom" is referenced only
descriptively; this is a Doom‑*like* project, not Doom.
