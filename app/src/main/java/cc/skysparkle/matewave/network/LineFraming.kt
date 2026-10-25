package cc.skysparkle.matewave.network

import java.io.Reader

/** Result of reading one newline-delimited message from a socket. */
sealed class LineResult {
    data class Line(val text: String) : LineResult()
    object TooLong : LineResult()
    object EndOfStream : LineResult()
}

/** Reads one line of at most [maxChars] characters; longer lines are reported, not buffered. */
fun readBoundedLine(reader: Reader, maxChars: Int): LineResult {
    val builder = StringBuilder()
    while (true) {
        val ch = reader.read()
        if (ch == -1) {
            return if (builder.isEmpty()) LineResult.EndOfStream
            else LineResult.Line(builder.toString())
        }
        if (ch == '\n'.code) return LineResult.Line(builder.toString())
        if (ch == '\r'.code) continue
        if (builder.length >= maxChars) return LineResult.TooLong
        builder.append(ch.toChar())
    }
}
