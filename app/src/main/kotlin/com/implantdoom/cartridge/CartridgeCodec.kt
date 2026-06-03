package com.implantdoom.cartridge

import java.util.zip.CRC32

/**
 * Encoder/decoder for the ImplantDoom cartridge binary format **v1**.
 *
 * The format is intentionally tiny so an entire level fits in the ~1000 usable
 * NDEF bytes of a small NFC Forum Type 5 / ISO 15693 implant.
 *
 * Byte layout (all integers little-endian):
 *
 * ```
 * Header (20 bytes)
 *   0..4   ASCII magic "IDOOM"
 *   5      version (0x01)
 *   6      flags
 *   7      mapWidth   (default 16)
 *   8      mapHeight  (default 16)
 *   9      playerStartX (0..mapWidth-1)
 *   10     playerStartY (0..mapHeight-1)
 *   11     playerStartAngle (0=E, 64=S, 128=W, 192=N)
 *   12..15 seed (uint32)
 *   16     textureThemeId
 *   17     entityCount
 *   18     itemCount
 *   19     reserved (must be 0 for now)
 *
 * Map (ceil(mapWidth*mapHeight / 2) bytes; 128 for 16x16)
 *   256 cells x 4 bits, row-major, two cells per byte:
 *   low nibble  = first (even-index) cell
 *   high nibble = second (odd-index) cell
 *
 * Entities (entityCount x 4 bytes)
 *   x, y, type, flags
 *
 * Items (itemCount x 3 bytes)
 *   x, y, type
 *
 * Footer (4 bytes)
 *   CRC32 (uint32, little-endian) of every preceding byte (header..items),
 *   i.e. of bytes[0 until size-4]. Standard CRC32 (java.util.zip.CRC32).
 * ```
 *
 * For the default 16x16 demo cartridge with 8 entities and 8 items the total is
 * `20 + 128 + 32 + 24 + 4 = 208` bytes.
 */
object CartridgeCodec {

    /** ASCII magic that every cartridge starts with. */
    const val MAGIC = "IDOOM"
    val MAGIC_BYTES: ByteArray = MAGIC.toByteArray(Charsets.US_ASCII) // 5 bytes

    /** Current (and only) supported format version. */
    const val VERSION = 0x01

    const val HEADER_SIZE = 20
    const val CRC_SIZE = 4
    const val ENTITY_SIZE = 4
    const val ITEM_SIZE = 3

    const val DEFAULT_MAP_WIDTH = 16
    const val DEFAULT_MAP_HEIGHT = 16

    /**
     * Hard ceiling for a serialised cartridge. The observed implant exposes
     * ~1000 usable NDEF bytes, so we refuse to build/accept anything larger.
     */
    const val MAX_SIZE_BYTES = 1000

    /** Largest value any single byte field (counts, coordinates, angle) can hold. */
    private const val MAX_U8 = 0xFF

    // ---------------------------------------------------------------------
    // Public size helpers
    // ---------------------------------------------------------------------

    /** Number of bytes the packed 4-bit map occupies for a [width] x [height] grid. */
    fun mapByteCount(width: Int, height: Int): Int = (width * height + 1) / 2

    /** Total serialised size (header + map + entities + items + CRC) for [c]. */
    fun encodedSize(c: Cartridge): Int =
        HEADER_SIZE +
            mapByteCount(c.mapWidth, c.mapHeight) +
            c.entities.size * ENTITY_SIZE +
            c.items.size * ITEM_SIZE +
            CRC_SIZE

    /**
     * Maximum number of entities that still fits under [MAX_SIZE_BYTES] given a
     * fixed [itemCount] and map size. Useful for the builder UI.
     */
    fun maxEntities(itemCount: Int, width: Int = DEFAULT_MAP_WIDTH, height: Int = DEFAULT_MAP_HEIGHT): Int {
        val fixed = HEADER_SIZE + mapByteCount(width, height) + itemCount * ITEM_SIZE + CRC_SIZE
        return ((MAX_SIZE_BYTES - fixed) / ENTITY_SIZE).coerceAtLeast(0)
    }

    // ---------------------------------------------------------------------
    // Encode
    // ---------------------------------------------------------------------

    /**
     * Serialise [c] to the v1 byte format, appending a valid CRC32 footer.
     *
     * @throws CartridgeException if the contents are out of range or the result
     *   would exceed [MAX_SIZE_BYTES].
     */
    fun encode(c: Cartridge): ByteArray {
        val w = c.mapWidth
        val h = c.mapHeight
        if (w !in 1..MAX_U8 || h !in 1..MAX_U8) {
            throw CartridgeException("map size out of range: ${w}x$h")
        }
        if (c.tiles.size != w * h) {
            throw CartridgeException("tiles length ${c.tiles.size} != ${w * h} (mapWidth*mapHeight)")
        }
        if (c.entities.size > MAX_U8) throw CartridgeException("too many entities: ${c.entities.size}")
        if (c.items.size > MAX_U8) throw CartridgeException("too many items: ${c.items.size}")

        val total = encodedSize(c)
        if (total > MAX_SIZE_BYTES) {
            throw CartridgeException("cartridge is $total bytes, exceeds max $MAX_SIZE_BYTES")
        }

        val out = ByteArray(total)
        var p = 0

        // --- Header ---
        MAGIC_BYTES.copyInto(out, 0); p += MAGIC_BYTES.size // 0..4
        out[p++] = VERSION.toByte()                          // 5
        out[p++] = c.flags.toByte()                          // 6
        out[p++] = w.toByte()                                // 7
        out[p++] = h.toByte()                                // 8
        out[p++] = c.playerStartX.toByte()                   // 9
        out[p++] = c.playerStartY.toByte()                   // 10
        out[p++] = c.playerStartAngle.toByte()               // 11
        writeUInt32LE(out, p, c.seed); p += 4                // 12..15
        out[p++] = c.textureThemeId.toByte()                 // 16
        out[p++] = c.entities.size.toByte()                  // 17
        out[p++] = c.items.size.toByte()                     // 18
        out[p++] = 0                                          // 19 reserved

        // --- Map ---
        val map = packMap(c.tiles, w, h)
        map.copyInto(out, p); p += map.size

        // --- Entities ---
        for (e in c.entities) {
            out[p++] = e.x.toByte()
            out[p++] = e.y.toByte()
            out[p++] = e.type.toByte()
            out[p++] = e.flags.toByte()
        }

        // --- Items ---
        for (it in c.items) {
            out[p++] = it.x.toByte()
            out[p++] = it.y.toByte()
            out[p++] = it.type.toByte()
        }

        // --- CRC32 footer over everything written so far ---
        val crc = crc32(out, 0, p)
        writeUInt32LE(out, p, crc)
        // p + 4 == total

        return out
    }

    // ---------------------------------------------------------------------
    // Decode
    // ---------------------------------------------------------------------

    /**
     * Parse [bytes] into a [Cartridge], validating magic, version, structural
     * length and CRC32.
     *
     * @throws CartridgeException on any of: too-short payload, wrong magic,
     *   unsupported version, length not matching the header's declared counts
     *   (truncated/over-long), or CRC mismatch.
     */
    fun decode(bytes: ByteArray): Cartridge {
        if (bytes.size < HEADER_SIZE + CRC_SIZE) {
            throw CartridgeException("payload too short: ${bytes.size} bytes (min ${HEADER_SIZE + CRC_SIZE})")
        }

        // Magic
        for (i in MAGIC_BYTES.indices) {
            if (bytes[i] != MAGIC_BYTES[i]) {
                throw CartridgeException("invalid magic (expected ASCII \"$MAGIC\")")
            }
        }

        val version = bytes.u8(5)
        if (version != VERSION) {
            throw CartridgeException("unsupported version $version (this build supports $VERSION)")
        }

        val flags = bytes.u8(6)
        val w = bytes.u8(7)
        val h = bytes.u8(8)
        if (w == 0 || h == 0) throw CartridgeException("invalid map size ${w}x$h")

        val playerStartX = bytes.u8(9)
        val playerStartY = bytes.u8(10)
        val playerStartAngle = bytes.u8(11)
        val seed = readUInt32LE(bytes, 12)
        val themeId = bytes.u8(16)
        val entityCount = bytes.u8(17)
        val itemCount = bytes.u8(18)
        // byte 19 (reserved) is ignored on read for forward compatibility.

        val mapBytes = mapByteCount(w, h)
        val expected = HEADER_SIZE + mapBytes + entityCount * ENTITY_SIZE + itemCount * ITEM_SIZE + CRC_SIZE
        if (bytes.size != expected) {
            throw CartridgeException(
                "length mismatch: header implies $expected bytes but payload is ${bytes.size} " +
                    "(truncated, padded or corrupt counts)",
            )
        }

        // CRC32 over everything except the trailing 4-byte CRC.
        val storedCrc = readUInt32LE(bytes, bytes.size - CRC_SIZE)
        val calcCrc = crc32(bytes, 0, bytes.size - CRC_SIZE)
        if (storedCrc != calcCrc) {
            throw CartridgeException(
                "CRC32 mismatch: stored=0x%08X computed=0x%08X".format(storedCrc, calcCrc),
            )
        }

        // Map
        val tiles = unpackMap(bytes, HEADER_SIZE, w, h)

        // Entities
        var p = HEADER_SIZE + mapBytes
        val entities = ArrayList<Entity>(entityCount)
        repeat(entityCount) {
            entities.add(Entity(bytes.u8(p), bytes.u8(p + 1), bytes.u8(p + 2), bytes.u8(p + 3)))
            p += ENTITY_SIZE
        }

        // Items
        val items = ArrayList<Item>(itemCount)
        repeat(itemCount) {
            items.add(Item(bytes.u8(p), bytes.u8(p + 1), bytes.u8(p + 2)))
            p += ITEM_SIZE
        }

        return Cartridge(
            version = version,
            flags = flags,
            mapWidth = w,
            mapHeight = h,
            playerStartX = playerStartX,
            playerStartY = playerStartY,
            playerStartAngle = playerStartAngle,
            seed = seed,
            textureThemeId = themeId,
            tiles = tiles,
            entities = entities,
            items = items,
        )
    }

    /** Convenience: true if [bytes] decodes cleanly. */
    fun isValid(bytes: ByteArray): Boolean =
        try {
            decode(bytes); true
        } catch (_: CartridgeException) {
            false
        }

    // ---------------------------------------------------------------------
    // Map nibble packing (exposed for unit tests)
    // ---------------------------------------------------------------------

    /**
     * Pack a row-major [tiles] grid (`width*height` cells, each 0..15) into
     * `ceil(width*height/2)` bytes: low nibble = even cell, high nibble = odd cell.
     */
    fun packMap(tiles: IntArray, width: Int, height: Int): ByteArray {
        val n = width * height
        require(tiles.size == n) { "tiles length ${tiles.size} != $n" }
        val out = ByteArray((n + 1) / 2)
        for (i in 0 until n) {
            val nib = tiles[i] and 0x0F
            val bi = i / 2
            out[bi] = if (i % 2 == 0) {
                ((out[bi].toInt() and 0xF0) or nib).toByte()
            } else {
                ((out[bi].toInt() and 0x0F) or (nib shl 4)).toByte()
            }
        }
        return out
    }

    /** Inverse of [packMap], reading [width]*[height] nibbles starting at [offset]. */
    fun unpackMap(src: ByteArray, offset: Int, width: Int, height: Int): IntArray {
        val n = width * height
        val tiles = IntArray(n)
        for (i in 0 until n) {
            val b = src[offset + i / 2].toInt() and 0xFF
            tiles[i] = if (i % 2 == 0) b and 0x0F else (b shr 4) and 0x0F
        }
        return tiles
    }

    // ---------------------------------------------------------------------
    // Low-level helpers
    // ---------------------------------------------------------------------

    /** Standard CRC32 over `data[offset until offset+length]`, returned as a 0..2^32-1 Long. */
    fun crc32(data: ByteArray, offset: Int, length: Int): Long {
        val crc = CRC32()
        crc.update(data, offset, length)
        return crc.value
    }

    private fun writeUInt32LE(dst: ByteArray, offset: Int, value: Long) {
        dst[offset] = (value and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun readUInt32LE(src: ByteArray, offset: Int): Long =
        (src.u8(offset).toLong()) or
            (src.u8(offset + 1).toLong() shl 8) or
            (src.u8(offset + 2).toLong() shl 16) or
            (src.u8(offset + 3).toLong() shl 24)

    /** Read one unsigned byte (0..255) from a [ByteArray]. */
    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
