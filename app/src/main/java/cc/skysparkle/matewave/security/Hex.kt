package cc.skysparkle.matewave.security

private const val HEX_DIGITS = "0123456789abcdef"

/** Lowercase hex of the first [count] bytes. */
internal fun ByteArray.toHex(count: Int = size): String {
    val n = minOf(count, size)
    val out = CharArray(n * 2)
    for (i in 0 until n) {
        val v = this[i].toInt() and 0xFF
        out[i * 2] = HEX_DIGITS[v ushr 4]
        out[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
    }
    return String(out)
}
