package cc.skysparkle.matewave.share

import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import kotlin.concurrent.thread

class ApkShareServer(private val apk: File, private val appName: String) {
    private val fileName = "$appName.apk"

    @Volatile
    private var server: ServerSocket? = null

    fun start(): Int {
        val socket = ServerSocket(0)
        server = socket
        thread(name = "apk-share-accept", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                thread(name = "apk-share-client", isDaemon = true) { serve(client) }
            }
        }
        return socket.localPort
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun serve(client: Socket) {
        try {
            client.use { s ->
                s.soTimeout = 15_000
                val input = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                val requestLine = input.readLine() ?: return

                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val parts = requestLine.split(' ')
                val method = parts.getOrNull(0).orEmpty()
                val path = parts.getOrNull(1).orEmpty().substringBefore('?')
                val out = BufferedOutputStream(s.getOutputStream())
                val withBody = method == "GET"

                when {
                    method != "GET" && method != "HEAD" ->
                        sendText(out, 405, "Method Not Allowed", "Method Not Allowed", withBody)
                    path == "/" || path == "/index.html" -> {
                        val page = landingPage()
                        writeHeader(out, 200, "OK", "text/html; charset=utf-8", page.size.toLong())
                        if (withBody) out.write(page)
                    }
                    path == "/$fileName" -> {
                        writeHeader(
                            out, 200, "OK", "application/vnd.android.package-archive", apk.length(),
                            "Content-Disposition: attachment; filename=\"$fileName\"\r\n"
                        )
                        if (withBody) apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                    }
                    else -> sendText(out, 404, "Not Found", "Not Found", withBody)
                }
                out.flush()
            }
        } catch (e: IOException) {
        }
    }

    private fun sendText(out: OutputStream, code: Int, reason: String, text: String, withBody: Boolean) {
        val body = text.toByteArray(Charsets.UTF_8)
        writeHeader(out, code, reason, "text/plain; charset=utf-8", body.size.toLong())
        if (withBody) out.write(body)
    }

    private fun writeHeader(
        out: OutputStream, code: Int, reason: String, type: String, length: Long, extra: String = ""
    ) {
        val header = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: $length\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n" +
            extra +
            "\r\n"
        out.write(header.toByteArray(Charsets.ISO_8859_1))
    }

    private fun landingPage(): ByteArray {
        val size = String.format(Locale.US, "%.1f MB", apk.length() / 1_048_576.0)
        val html = "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>$appName</title><style>" +
            "body{font-family:sans-serif;background:#12181A;color:#fff;text-align:center;padding:48px 20px}" +
            "a.b{display:inline-block;margin-top:28px;padding:16px 28px;border-radius:12px;" +
            "background:#3E7C4A;color:#fff;font-size:18px;text-decoration:none}" +
            "p{color:#b8c2c0}</style></head><body><h1>$appName</h1>" +
            "<p>Android app \u00B7 APK $size</p>" +
            "<a class=\"b\" href=\"/$fileName\">Download APK</a>" +
            "<p>After downloading, open the file and allow installation from this source if asked.</p>" +
            "</body></html>"
        return html.toByteArray(Charsets.UTF_8)
    }
}

object LocalNetwork {
    private val WIFI_PREFIXES = listOf("wlan", "swlan", "ap", "softap", "eth")

    fun wifiIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { nif -> nif.isUp && !nif.isLoopback && WIFI_PREFIXES.any { nif.name.startsWith(it) } }
            .flatMap { nif -> nif.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
