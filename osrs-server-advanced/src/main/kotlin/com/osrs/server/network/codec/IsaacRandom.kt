package com.osrs.server.network.codec

/**
 * ISAAC (Indirection, Shift, Accumulate, Add, Count) CSPRNG.
 *
 * Used by OSRS to XOR-scramble packet opcodes in both directions.
 * The client uses one instance for encoding outbound opcodes and a
 * second (seeded with keys + 50) for decoding inbound opcodes.
 * The server mirrors this: decode with [keys], encode with [keys + 50].
 *
 * Reference: Bob Jenkins' ISAAC algorithm (1996).
 * OSRS-specific detail: the seed array is 4 ints derived from the
 * XTEA block inside the login RSA payload.
 */
class IsaacRandom(seed: IntArray) {

    private val results = IntArray(256)
    private val mem     = IntArray(256)
    private var count   = 0
    private var a = 0
    private var b = 0
    private var c = 0

    init {
        require(seed.size == 4) { "OSRS ISAAC seed must be exactly 4 ints" }
        // Zero-extend seed into the full 256-int results array
        for (i in seed.indices) results[i] = seed[i]
        init()
    }

    /** Returns the next scramble value (0..255). */
    fun nextInt(): Int {
        if (count == 0) {
            generate()
            count = 255
        } else {
            count--
        }
        return results[count]
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun init() {
        var a = -1640531527
        var b = -1640531527
        var c = -1640531527
        var d = -1640531527
        var e = -1640531527
        var f = -1640531527
        var g = -1640531527
        var h = -1640531527

        repeat(4) {
            a = a xor (b shl 11); d += a; b += c
            b = b xor (c ushr 2); e += b; c += d
            c = c xor (d shl 8);  f += c; d += e
            d = d xor (e ushr 16); g += d; e += f
            e = e xor (f shl 10); h += e; f += g
            f = f xor (g ushr 4); a += f; g += h
            g = g xor (h shl 8);  b += g; h += a
            h = h xor (a ushr 9); c += h; a += b
        }

        var i = 0
        while (i < 256) {
            a += results[i];   b += results[i + 1]
            c += results[i + 2]; d += results[i + 3]
            e += results[i + 4]; f += results[i + 5]
            g += results[i + 6]; h += results[i + 7]

            a = a xor (b shl 11); d += a; b += c
            b = b xor (c ushr 2); e += b; c += d
            c = c xor (d shl 8);  f += c; d += e
            d = d xor (e ushr 16); g += d; e += f
            e = e xor (f shl 10); h += e; f += g
            f = f xor (g ushr 4); a += f; g += h
            g = g xor (h shl 8);  b += g; h += a
            h = h xor (a ushr 9); c += h; a += b

            mem[i]     = a; mem[i + 1] = b; mem[i + 2] = c; mem[i + 3] = d
            mem[i + 4] = e; mem[i + 5] = f; mem[i + 6] = g; mem[i + 7] = h
            i += 8
        }
        generate()
    }

    private fun generate() {
        c++
        b += c
        var i = 0
        while (i < 256) {
            val x = mem[i]
            a = a xor (a shl 13)
            a += mem[(i + 128) and 255]
            var y = mem[(x ushr 2) and 255] + a + b
            mem[i] = y
            b = mem[(y ushr 10) and 255] + x
            results[i] = b
            i++

            val x2 = mem[i]
            a = a xor (a ushr 6)
            a += mem[(i + 128) and 255]
            var y2 = mem[(x2 ushr 2) and 255] + a + b
            mem[i] = y2
            b = mem[(y2 ushr 10) and 255] + x2
            results[i] = b
            i++

            val x3 = mem[i]
            a = a xor (a shl 2)
            a += mem[(i + 128) and 255]
            var y3 = mem[(x3 ushr 2) and 255] + a + b
            mem[i] = y3
            b = mem[(y3 ushr 10) and 255] + x3
            results[i] = b
            i++

            val x4 = mem[i]
            a = a xor (a ushr 16)
            a += mem[(i + 128) and 255]
            var y4 = mem[(x4 ushr 2) and 255] + a + b
            mem[i] = y4
            b = mem[(y4 ushr 10) and 255] + x4
            results[i] = b
            i++
        }
    }
}

/**
 * Pair of ISAAC instances for the game session.
 *
 * [decoder] is seeded from the raw XTEA keys.
 * [encoder] is seeded from keys + 50 per the OSRS convention.
 */
class IsaacPair(keys: IntArray) {
    val decoder = IsaacRandom(keys)
    val encoder = IsaacRandom(IntArray(4) { keys[it] + 50 })
}
