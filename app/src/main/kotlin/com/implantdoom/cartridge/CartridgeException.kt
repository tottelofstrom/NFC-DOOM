package com.implantdoom.cartridge

/**
 * Thrown by [CartridgeCodec.decode] when a byte payload is not a valid
 * ImplantDoom cartridge: bad magic, unsupported version, truncated/over-long
 * payload, or a CRC32 mismatch.
 */
class CartridgeException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
