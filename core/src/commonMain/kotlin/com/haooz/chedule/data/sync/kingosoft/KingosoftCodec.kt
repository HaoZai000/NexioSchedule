package com.haooz.chedule.data.sync.kingosoft

/** Wire format required by the Kingosoft WAP endpoint; not a password hash. */
object KingosoftCodec {
    private val shifts = intArrayOf(7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21)
    private val constants = longArrayOf(
        0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee, 0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
        0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be, 0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
        0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa, 0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
        0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed, 0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
        0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c, 0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
        0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05, 0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
        0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039, 0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
        0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1, 0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391,
    )

    fun md5Hex(input: String): String {
        val bytes = input.encodeToByteArray()
        val padded = bytes.copyOf((bytes.size + 8) / 64 * 64 + 64)
        padded[bytes.size] = 0x80.toByte()
        val bitLength = bytes.size * 8L
        for (i in 0..7) padded[padded.size - 8 + i] = (bitLength ushr (8 * i)).toByte()

        val state = intArrayOf(0x67452301, 0xefcdab89.toInt(), 0x98badcfe.toInt(), 0x10325476)
        for (offset in padded.indices step 64) {
            val words = IntArray(16) { word ->
                var value = 0
                for (i in 0..3) value = value or ((padded[offset + word * 4 + i].toInt() and 255) shl (8 * i))
                value
            }
            var (a, b, c, d) = state
            for (i in 0 until 64) {
                val f = when (i / 16) {
                    0 -> (b and c) or (b.inv() and d)
                    1 -> (b and d) or (c and d.inv())
                    2 -> b xor c xor d
                    else -> c xor (b or d.inv())
                }
                val g = when (i / 16) {
                    0 -> i
                    1 -> (5 * i + 1) % 16
                    2 -> (3 * i + 5) % 16
                    else -> 7 * i % 16
                }
                val next = b + (a + f + constants[i].toInt() + words[g]).rotateLeft(shifts[i / 16 * 4 + i % 4])
                a = d
                d = c
                c = b
                b = next
            }
            state[0] += a
            state[1] += b
            state[2] += c
            state[3] += d
        }
        return buildString(32) {
            for (word in state) for (i in 0..3) append(((word ushr (i * 8)) and 255).toString(16).padStart(2, '0'))
        }
    }

    fun encodeParam(plain: String, key: String): String {
        require(key.isNotEmpty()) { "Missing Kingosoft encryption key" }
        // n2 = ceil(plain.length / 3). Indexing the key cyclically is the same as visiting n rows of klen.
        val shift = ((plain.length + 2L) / 3 * 6 % key.length).toInt()
        val digits = buildString {
            plain.forEachIndexed { i, ch ->
                append(((ch.code + key[i % key.length].code + shift) % 1000).toString().padStart(3, '0'))
            }
        }
        return digits.chunked(9).joinToString("") { it.toLong().toString(36).padStart(6, '0').takeLast(6) }
    }

    fun encodeParam2(plain: String): String = md5Hex(md5Hex(plain).filterIndexed { i, _ ->
        i != 2 && i != 9 && i != 16 && i != 24
    })

    fun buildFormBody(plain: String, key: String): String = "param=${encodeParam(plain, key)}&param2=${encodeParam2(plain)}"
}
