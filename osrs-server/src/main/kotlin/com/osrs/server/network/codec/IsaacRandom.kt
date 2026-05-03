package com.osrs.server.network.codec

class IsaacRandom(seed: IntArray) {

    private val results = IntArray(256)
    private val mem = IntArray(256)
    private var count = 0
    private var a = 0
    private var b = 0
    private var c = 0

    init {
        require(seed.size == 4) { "OSRS ISAAC seed must be exactly 4 ints" }
        for (i in seed.indices) results[i] = seed[i]
        initState()
    }

    fun nextInt(): Int {
        if (count == 0) {
            generate()
            count = 255
        } else {
            count--
        }
        return results[count]
    }

    private fun initState() {
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
            c = c xor (d shl 8); f += c; d += e
            d = d xor (e ushr 16); g += d; e += f
            e = e xor (f shl 10); h += e; f += g
            f = f xor (g ushr 4); a += f; g += h
            g = g xor (h shl 8); b += g; h += a
            h = h xor (a ushr 9); c += h; a += b
        }

        var i = 0
        while (i < 256) {
            a += results[i]; b += results[i + 1]
            c += results[i + 2]; d += results[i + 3]
            e += results[i + 4]; f += results[i + 5]
            g += results[i + 6]; h += results[i + 7]

            a = a xor (b shl 11); d += a; b += c
            b = b xor (c ushr 2); e += b; c += d
            c = c xor (d shl 8); f += c; d += e
            d = d xor (e ushr 16); g += d; e += f
            e = e xor (f shl 10); h += e; f += g
            f = f xor (g ushr 4); a += f; g += h
            g = g xor (h shl 8); b += g; h += a
            h = h xor (a ushr 9); c += h; a += b

            mem[i] = a; mem[i + 1] = b; mem[i + 2] = c; mem[i + 3] = d
            mem[i + 4] = e; mem[i + 5] = f; mem[i + 6] = g; mem[i + 7] = h
            i += 8
        }
        generate()
    }

    private fun generate() {
        c++
        b += c
        for (i in 0 until 256) {
            val x = mem[i]
            a = a xor (a shl 13)
            a += mem[(i + 128) and 255]
            var y = mem[(x ushr 2) and 255] + a + b
            mem[i] = y
            b = mem[(y ushr 10) and 255] + x
            results[i] = b
        }
    }
}

class IsaacPair(keys: IntArray) {
    val decoder = IsaacRandom(keys)
    val encoder = IsaacRandom(IntArray(4) { keys[it] + 50 })
}
