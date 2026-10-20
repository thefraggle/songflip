package de.goork.songflip.core.engine

/**
 * Pure-Kotlin RFC 8032 Ed25519 signature verifier for KMP (Android & iOS).
 * Allows offline verification of server-issued `sct_v2_` coupon tokens without
 * embedding any symmetric secret or private key in the client binary (#109).
 */
object Ed25519Verifier {

    private val D = LongArray(16)
    private val L = intArrayOf(
        0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58,
        0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10
    )

    init {
        // d = -121665 / 121666 mod p (TweetNaCl gf constant D)
        val dVals = longArrayOf(
            0x78a3, 0x1359, 0x4dca, 0x75eb, 0xd8ab, 0x4141, 0x0a4d, 0x0070,
            0xe898, 0x7779, 0x4079, 0x8cc7, 0xfe73, 0x2b6f, 0x6cee, 0x5203
        )
        for (i in 0 until 16) D[i] = dVals[i]
    }

    private val GF0 = LongArray(16)
    private val GF1 = LongArray(16).apply { this[0] = 1L }
    private val I_CONST = longArrayOf(
        0xa0b0, 0x4a0e, 0x1b27, 0xc4ee, 0xe478, 0xad2f, 0x1806, 0x2f43,
        0xd7a7, 0x3dfb, 0x0099, 0x2b4d, 0xdf0b, 0x4fc1, 0x2480, 0x2b83
    )

    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false

        // Reject non-canonical S >= L (RFC 8032 §5.1.7)
        if (!isCanonicalS(signature)) return false

        val p = Array(4) { LongArray(16) }
        val q = Array(4) { LongArray(16) }

        if (unpackNeg(q, publicKey) != 0) return false

        // h = SHA-512(R || A || M) mod L
        val hashInput = ByteArray(64 + message.size)
        signature.copyInto(hashInput, destinationOffset = 0, startIndex = 0, endIndex = 32)
        publicKey.copyInto(hashInput, destinationOffset = 32, startIndex = 0, endIndex = 32)
        message.copyInto(hashInput, destinationOffset = 64)
        val h = Sha512.digest(hashInput)
        reduceModL(h)

        // p = s * B + h * (-A)
        val sBytes = signature.copyOfRange(32, 64)
        scalarmultBase(p, sBytes)
        scalarmult(q, q, h)
        addPoints(p, q)

        val check = ByteArray(32)
        packPoint(check, p)

        // Constant-time comparison of R (first 32 bytes of signature) with packed point
        var diff = 0
        for (i in 0 until 32) {
            diff = diff or ((signature[i].toInt() xor check[i].toInt()) and 0xff)
        }
        return diff == 0
    }

    private fun isCanonicalS(sig: ByteArray): Boolean {
        for (i in 31 downTo 0) {
            val sByte = sig[32 + i].toInt() and 0xff
            if (sByte < L[i]) return true
            if (sByte > L[i]) return false
        }
        return false
    }

    private fun set25519(r: LongArray, a: LongArray) {
        for (i in 0 until 16) r[i] = a[i]
    }

    private fun car25519(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += (1L shl 16)
            val c = o[i] shr 16
            o[(i + 1) * (if (i < 15) 1 else 0)] += c - 1L + 37L * (c - 1L) * (if (i == 15) 1L else 0L)
            o[i] -= c shl 16
        }
    }

    private fun sel25519(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).toLong().inv()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun pack25519(o: ByteArray, n: LongArray) {
        val m = LongArray(16)
        val t = LongArray(16)
        for (i in 0 until 16) t[i] = n[i]
        car25519(t)
        car25519(t)
        car25519(t)
        for (j in 0 until 2) {
            m[0] = t[0] - 0xffedL
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffffL - ((m[i - 1] shr 16) and 1L)
                m[i - 1] = m[i - 1] and 0xffffL
            }
            m[15] = t[15] - 0x7fffL - ((m[14] shr 16) and 1L)
            val b = ((m[15] shr 16) and 1L).toInt()
            m[14] = m[14] and 0xffffL
            sel25519(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            o[2 * i] = (t[i] and 0xffL).toByte()
            o[2 * i + 1] = (t[i] shr 8).toByte()
        }
    }

    private fun neq25519(a: LongArray, b: LongArray): Int {
        val c = ByteArray(32)
        val d = ByteArray(32)
        pack25519(c, a)
        pack25519(d, b)
        var diff = 0
        for (i in 0 until 32) {
            diff = diff or ((c[i].toInt() xor d[i].toInt()) and 0xff)
        }
        return if (diff != 0) 1 else 0
    }

    private fun par25519(a: LongArray): Byte {
        val d = ByteArray(32)
        pack25519(d, a)
        return (d[0].toInt() and 1).toByte()
    }

    private fun unpack25519(o: LongArray, n: ByteArray) {
        for (i in 0 until 16) {
            o[i] = ((n[2 * i].toInt() and 0xff) or ((n[2 * i + 1].toInt() and 0xff) shl 8)).toLong()
        }
        o[15] = o[15] and 0x7fffL
    }

    private fun A(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun Z(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun M(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) {
            for (j in 0 until 16) {
                t[i + j] += a[i] * b[j]
            }
        }
        for (i in 0 until 15) {
            t[i] += 38L * t[i + 16]
        }
        for (i in 0 until 16) o[i] = t[i]
        car25519(o)
        car25519(o)
    }

    private fun S(o: LongArray, a: LongArray) {
        M(o, a, a)
    }

    private fun inv25519(o: LongArray, i: LongArray) {
        val c = LongArray(16)
        for (a in 0 until 16) c[a] = i[a]
        for (a in 253 downTo 0) {
            S(c, c)
            if (a != 2 && a != 4) M(c, c, i)
        }
        for (a in 0 until 16) o[a] = c[a]
    }

    private fun pow2523(o: LongArray, i: LongArray) {
        val c = LongArray(16)
        for (a in 0 until 16) c[a] = i[a]
        for (a in 250 downTo 0) {
            S(c, c)
            if (a != 1) M(c, c, i)
        }
        for (a in 0 until 16) o[a] = c[a]
    }

    private fun unpackNeg(r: Array<LongArray>, p: ByteArray): Int {
        val t = LongArray(16)
        val chk = LongArray(16)
        val num = LongArray(16)
        val den = LongArray(16)
        val den2 = LongArray(16)
        val den4 = LongArray(16)
        val den6 = LongArray(16)

        set25519(r[2], GF1)
        unpack25519(r[1], p)
        S(num, r[1])
        M(den, num, D)
        Z(num, num, r[2])
        A(den, r[2], den)

        S(den2, den)
        S(den4, den2)
        M(den6, den4, den2)
        M(t, den6, num)
        M(t, t, den)

        pow2523(t, t)
        M(t, t, num)
        M(t, t, den)
        M(t, t, den)
        M(r[0], t, den)

        S(chk, r[0])
        M(chk, chk, den)
        if (neq25519(chk, num) != 0) M(r[0], r[0], I_CONST)

        S(chk, r[0])
        M(chk, chk, den)
        if (neq25519(chk, num) != 0) return -1

        if (par25519(r[0]) == ((p[31].toInt() shr 7) and 1).toByte()) {
            Z(r[0], GF0, r[0])
        }

        M(r[3], r[0], r[1])
        return 0
    }

    private fun packPoint(r: ByteArray, p: Array<LongArray>) {
        val tx = LongArray(16)
        val ty = LongArray(16)
        val zi = LongArray(16)
        inv25519(zi, p[2])
        M(tx, p[0], zi)
        M(ty, p[1], zi)
        pack25519(r, ty)
        r[31] = (r[31].toInt() xor ((par25519(tx).toInt() and 1) shl 7)).toByte()
    }

    private fun addPoints(p: Array<LongArray>, q: Array<LongArray>) {
        val a = LongArray(16)
        val b = LongArray(16)
        val c = LongArray(16)
        val d = LongArray(16)
        val t = LongArray(16)
        val e = LongArray(16)
        val f = LongArray(16)
        val g = LongArray(16)
        val h = LongArray(16)

        Z(a, p[1], p[0])
        Z(t, q[1], q[0])
        M(a, a, t)
        A(b, p[0], p[1])
        A(t, q[0], q[1])
        M(b, b, t)
        M(c, p[3], q[3])
        M(c, c, D)
        A(c, c, c)
        M(d, p[2], q[2])
        A(d, d, d)
        Z(e, b, a)
        Z(f, d, c)
        A(g, d, c)
        A(h, b, a)

        M(p[0], e, f)
        M(p[1], h, g)
        M(p[2], g, f)
        M(p[3], e, h)
    }

    private fun cswap(p: Array<LongArray>, q: Array<LongArray>, b: Int) {
        for (i in 0 until 4) {
            sel25519(p[i], q[i], b)
        }
    }

    private fun scalarmult(p: Array<LongArray>, q: Array<LongArray>, s: ByteArray) {
        val qq = Array(4) { i -> q[i].copyOf() }
        set25519(p[0], GF0)
        set25519(p[1], GF1)
        set25519(p[2], GF1)
        set25519(p[3], GF0)
        for (i in 255 downTo 0) {
            val b = ((s[i shr 3].toInt() shr (i and 7)) and 1)
            cswap(p, qq, b)
            addPoints(qq, p)
            addPoints(p, p)
            cswap(p, qq, b)
        }
    }

    private val BX = longArrayOf(
        0xd51a, 0x8f25, 0x2d60, 0xc956, 0xa7b2, 0x9525, 0xc760, 0x692c,
        0xdc5c, 0xfdd6, 0xe231, 0xc0a4, 0x53fe, 0xcd6e, 0x36d3, 0x2169
    )
    private val BY = longArrayOf(
        0x6658, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666,
        0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666
    )

    private fun scalarmultBase(p: Array<LongArray>, s: ByteArray) {
        val q = Array(4) { LongArray(16) }
        set25519(q[0], BX)
        set25519(q[1], BY)
        set25519(q[2], GF1)
        M(q[3], BX, BY)
        scalarmult(p, q, s)
    }

    private fun reduceModL(r: ByteArray) {
        val x = LongArray(64)
        for (i in 0 until 64) x[i] = (r[i].toInt() and 0xff).toLong()
        for (i in 0 until 64) r[i] = 0
        modL(r, x)
    }

    private fun modL(r: ByteArray, x: LongArray) {
        for (i in 63 downTo 32) {
            var carry = 0L
            var j = i - 32
            val k = i - 12
            while (j < k) {
                x[j] += carry - 16L * x[i] * L[j - (i - 32)].toLong()
                carry = (x[j] + 128L) shr 8
                x[j] -= carry shl 8
                j++
            }
            x[j] += carry
            x[i] = 0L
        }
        var carry = 0L
        for (j in 0 until 32) {
            x[j] += carry - (x[31] shr 4) * L[j].toLong()
            carry = x[j] shr 8
            x[j] = x[j] and 0xffL
        }
        for (j in 0 until 32) {
            x[j] -= carry * L[j].toLong()
        }
        for (i in 0 until 32) {
            x[i + 1] += x[i] shr 8
            r[i] = (x[i] and 0xffL).toByte()
        }
    }

    fun verify(message: ByteArray, signatureHex: String, publicKeyHex: String): Boolean {
        val sigBytes = hexToBytes(signatureHex)
        val pubBytes = hexToBytes(publicKeyHex)
        if (sigBytes.size != 64 || pubBytes.size != 32) return false
        return verify(message, sigBytes, pubBytes)
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        if (clean.isEmpty() || clean.length % 2 != 0) return ByteArray(0)
        return try {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }
}

/**
 * Pure-Kotlin SHA-512 implementation (FIPS 180-4) required by Ed25519.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object Sha512 {
    private val K = ulongArrayOf(
        0x428a2f98d728ae22uL, 0x7137449123ef65cduL, 0xb5c0fbcfec4d3b2fuL, 0xe9b5dba58189dbbcUL,
        0x3956c25bf348b538uL, 0x59f111f1b605d019uL, 0x923f82a4af194f9buL, 0xab1c5ed5da6d8118uL,
        0xd807aa98a3030242uL, 0x12835b0145706fbeuL, 0x243185be4ee4b28cuL, 0x550c7dc3d5ffb4e2uL,
        0x72be5d74f27b896fuL, 0x80deb1fe3b1696b1uL, 0x9bdc06a725c71235uL, 0xc19bf174cf692694uL,
        0xe49b69c19ef14ad2uL, 0xefbe4786384f25e3uL, 0x0fc19dc68b8cd5b5uL, 0x240ca1cc77ac9c65uL,
        0x2de92c6f592b0275uL, 0x4a7484aa6ea6e483uL, 0x5cb0a9dcbd41fbd4uL, 0x76f988da831153b5uL,
        0x983e5152ee66dfabuL, 0xa831c66d2db43210uL, 0xb00327c898fb213fuL, 0xbf597fc7beef0ee4uL,
        0xc6e00bf33da88fc2uL, 0xd5a79147930aa725uL, 0x06ca6351e003826fuL, 0x142929670a0e6e70uL,
        0x27b70a8546d22ffcuL, 0x2e1b21385c26c926uL, 0x4d2c6dfc5ac42aeduL, 0x53380d139d95b3dfuL,
        0x650a73548baf63deuL, 0x766a0abb3c77b2a8uL, 0x81c2c92e47edaee6uL, 0x92722c851482353buL,
        0xa2bfe8a14cf10364uL, 0xa81a664bbc423001uL, 0xc24b8b70d0f89791uL, 0xc76c51a30654be30uL,
        0xd192e819d6ef5218uL, 0xd69906245565a910uL, 0xf40e35855771202auL, 0x106aa07032bbd1b8uL,
        0x19a4c116b8d2d0c8uL, 0x1e376c085141ab53uL, 0x2748774cdf8eeb99uL, 0x34b0bcb5e19b48a8uL,
        0x391c0cb3c5c95a63uL, 0x4ed8aa4ae3418acbuL, 0x5b9cca4f7763e373uL, 0x682e6ff3d6b2b8a3uL,
        0x748f82ee5defb2fcuL, 0x78a5636f43172f60uL, 0x84c87814a1f0ab72uL, 0x8cc702081a6439ecuL,
        0x90befffa23631e28uL, 0xa4506cebde82bde9uL, 0xbef9a3f7b2c67915uL, 0xc67178f2e372532buL,
        0xca273eceea26619cuL, 0xd186b8c721c0c207uL, 0xeada7dd6cde0eb1euL, 0xf57d4f7fee6ed178uL,
        0x06f067aa72176fbauL, 0x0a637dc5a2c898a6uL, 0x113f9804bef90daeuL, 0x1b710b35131c471buL,
        0x28db77f523047d84uL, 0x32caab7b40c72493uL, 0x3c9ebe0a15c9bebcuL, 0x431d67c49c100d4cuL,
        0x4cc5d4becb3e42b6uL, 0x597f299cfc657e2auL, 0x5fcb6fab3ad6faecuL, 0x6c44198c4a475817uL
    )

    private val H0 = ulongArrayOf(
        0x6a09e667f3bcc908uL, 0xbb67ae8584caa73buL, 0x3c6ef372fe94f82buL, 0xa54ff53a5f1d36f1uL,
        0x510e527fade682d1uL, 0x9b05688c2b3e6c1fuL, 0x1f83d9abfb41bd6buL, 0x5be0cd19137e2179uL
    )

    fun digest(input: ByteArray): ByteArray {
        val h = H0.copyOf()
        val bitLen = input.size.toULong() * 8uL
        val totalLen = ((input.size + 17 + 127) / 128) * 128
        val padded = ByteArray(totalLen)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[totalLen - 1 - i] = ((bitLen shr (i * 8)) and 0xffuL).toByte()
        }

        val w = ULongArray(80)
        var offset = 0
        while (offset < totalLen) {
            for (i in 0 until 16) {
                var v = 0uL
                for (b in 0 until 8) {
                    v = (v shl 8) or (padded[offset + i * 8 + b].toInt() and 0xff).toULong()
                }
                w[i] = v
            }
            for (i in 16 until 80) {
                val s0 = rotr(w[i - 15], 1) xor rotr(w[i - 15], 8) xor (w[i - 15] shr 7)
                val s1 = rotr(w[i - 2], 19) xor rotr(w[i - 2], 61) xor (w[i - 2] shr 6)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]

            for (i in 0 until 80) {
                val S1 = rotr(e, 14) xor rotr(e, 18) xor rotr(e, 41)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = hh + S1 + ch + K[i] + w[i]
                val S0 = rotr(a, 28) xor rotr(a, 34) xor rotr(a, 39)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = S0 + maj

                hh = g; g = f; f = e; e = d + temp1
                d = c; c = b; b = a; a = temp1 + temp2
            }

            h[0] += a; h[1] += b; h[2] += c; h[3] += d
            h[4] += e; h[5] += f; h[6] += g; h[7] += hh
            offset += 128
        }

        val out = ByteArray(64)
        for (i in 0 until 8) {
            val v = h[i]
            for (b in 0 until 8) {
                out[i * 8 + b] = ((v shr ((7 - b) * 8)) and 0xffuL).toByte()
            }
        }
        return out
    }

    private fun rotr(x: ULong, n: Int): ULong = (x shr n) or (x shl (64 - n))
}
