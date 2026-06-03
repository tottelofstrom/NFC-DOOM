# ImplantDoom cartridge format — v1

A self-contained reference for the on-tag binary format. The authoritative
implementation is [`CartridgeCodec`](../app/src/main/kotlin/com/implantdoom/cartridge/CartridgeCodec.kt);
this document mirrors it.

* Endianness: **little-endian** for all multi-byte integers.
* Container: stored as the payload of a single MIME NDEF record with type
  `application/vnd.implantdoom.cartridge`.
* Hard size limit: **1000 bytes** (the codec rejects anything larger).

## Overall layout

```
offset 0                                                         size-1
+--------+-----------+--------------+-----------+----------------------+
| Header |    Map    |   Entities   |   Items   |   CRC32 (footer)     |
| 20 B   |  128 B*   |  4·N bytes   | 3·M bytes |        4 B           |
+--------+-----------+--------------+-----------+----------------------+
```

`* Map size = ceil(mapWidth·mapHeight / 2)` — 128 bytes for the standard 16×16 map.

## Header (20 bytes)

| Off | Len | Field            | Notes                                   |
|----:|----:|------------------|-----------------------------------------|
|  0  |  5  | magic            | ASCII `IDOOM` (`49 44 4F 4F 4D`)        |
|  5  |  1  | version          | `0x01`                                  |
|  6  |  1  | flags            | reserved bit flags (0 today)            |
|  7  |  1  | mapWidth         | 16                                      |
|  8  |  1  | mapHeight        | 16                                      |
|  9  |  1  | playerStartX     | 0–15                                    |
| 10  |  1  | playerStartY     | 0–15                                    |
| 11  |  1  | playerStartAngle | 0=E, 64=S, 128=W, 192=N (256 = 360°)    |
| 12  |  4  | seed             | uint32, little-endian                   |
| 16  |  1  | textureThemeId   | 0=Tech, 1=Inferno, 2=Cavern, 3=Cryo     |
| 17  |  1  | entityCount      | N                                       |
| 18  |  1  | itemCount        | M                                       |
| 19  |  1  | reserved         | must be 0                               |

## Map

`mapWidth·mapHeight` cells, **4 bits each**, row-major. Two cells per byte:

```
byte = (cell[2k] & 0x0F) | ((cell[2k+1] & 0x0F) << 4)
        \___ low nibble ___/   \____ high nibble ____/
        first (even) cell        second (odd) cell
```

Tile IDs: `0` empty · `1` wall · `2` door · `3` exit · `4` hazard · `5–15` reserved.

## Entities — 4 bytes each (×N)

| Off | Field | Notes                                   |
|----:|-------|-----------------------------------------|
|  0  | x     | tile column                             |
|  1  | y     | tile row                                |
|  2  | type  | 1=basic enemy, 2=turret, 3=boss         |
|  3  | flags | per-entity flags (0 today)              |

## Items — 3 bytes each (×M)

| Off | Field | Notes                          |
|----:|-------|--------------------------------|
|  0  | x     | tile column                    |
|  1  | y     | tile row                       |
|  2  | type  | 1=health, 2=key, 3=ammo        |

## Footer — CRC32 (4 bytes)

Standard CRC32 (`java.util.zip.CRC32`, polynomial 0xEDB88320) of **every byte
before the footer** — i.e. `bytes[0 .. size-5]`. Stored little-endian.

Decoding validates, in order: minimum length → magic → version → that the total
length matches the header's declared counts → CRC32.

## Worked size example (default demo cartridge)

```
Header           20
Map (16×16)     128
Entities (8×4)   32
Items   (8×3)    24
CRC32             4
---------------------
Total           208 bytes   (< 1000-byte budget)
```

## Max entities for a given item count

```
maxEntities = floor((1000 − 20 − 128 − 3·itemCount − 4) / 4)
```
