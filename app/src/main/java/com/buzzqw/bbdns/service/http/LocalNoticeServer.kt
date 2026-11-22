/*
 * Copyright (C) 2026 buzzqw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.buzzqw.bbdns.service.http

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Answers a blocked page with an explanation instead of a silent failure.
 *
 * DNS filtering usually ends in the browser's own error page, which tells the
 * user nothing: they cannot tell a broken site from a blocked one, and "why
 * was this blocked?" only exists inside the app. This server is the other
 * half of the local notice page: the tunnel answers a blocked A query with a
 * private address, the native stack connects to this loopback server instead
 * of the site, and the page explains the block.
 *
 * Nothing is decrypted. The tunnel only redirects plain HTTP (port 80); an
 * HTTPS visit to a blocked site fails at the connection, exactly as it did
 * before, because a page for TLS would need a certificate the app does not
 * have and must not install.
 *
 * The page can offer "continue" for a few minutes. That choice is signed with
 * a per-process key: any app on the phone can reach a loopback port, so an
 * unsigned link would let one app lift another app's blocks.
 *
 * Plain Java/Kotlin with no Android classes: the whole server runs in unit
 * tests against a loopback upstream.
 */
class LocalNoticeServer(
    private val policy: NoticePolicy,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Port used upstream. 80 in production; tests point it at a fake site. */
    private val upstreamPort: Int = 80,
    /** How long "continue" lasts. */
    private val allowMinutes: Int = 10,
    private val log: (String) -> Unit = {},
    private val connectTimeoutMillis: Int = 4_000,
    private val readTimeoutMillis: Int = 10_000,
    private val maxConnections: Int = 16,
    /** Opens the upstream connection. Replaced in tests. */
    private val connector: (String, Int) -> Socket = { host, port -> connect(host, port, connectTimeoutMillis) },
    /** SSRF guard. Replaced in tests, which use loopback upstreams. */
    private val addressAllowed: (InetAddress) -> Boolean = { isPublicAddress(it) },
    /** Resolves the site before relaying. Replaced in tests. */
    private val resolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
) {

    /** What the page needs to know about the filter. Implemented by the VPN. */
    interface NoticePolicy {
        /** True while the site is still blocked by a list or a custom rule. */
        fun isBlocked(host: String): Boolean

        /** Name of the list that blocks it, when the index knows it. */
        fun sourceOf(host: String): String?

        /** The user pressed "continue": let the site through for a while. */
        fun allowTemporarily(host: String, minutes: Int)

        /** True while a previous "continue" is still valid. */
        fun isTemporarilyAllowed(host: String): Boolean
    }

    private val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val threads = ThreadPoolExecutor(
        0,
        maxConnections,
        30L,
        TimeUnit.SECONDS,
        java.util.concurrent.SynchronousQueue(),
    ) { runnable ->
        Thread(runnable, "bbdns-notice-${threadCount.incrementAndGet()}").apply { isDaemon = true }
    }

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    @Volatile
    private var running = false

    /** Port the server is listening on, or 0 before [start]. */
    @Volatile
    var port: Int = 0
        private set

    /**
     * Binds the loopback address and starts accepting connections.
     * Returns the port, or 0 when the server could not start.
     */
    @Synchronized
    fun start(requestedPort: Int = 0): Int {
        if (running) return port
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), maxConnections)
            serverSocket = socket
            port = socket.localPort
            running = true
            acceptThread = Thread({ acceptLoop(socket) }, "bbdns-notice-accept").apply {
                isDaemon = true
                start()
            }
            log("notice page listening on 127.0.0.1:$port")
            port
        } catch (e: IOException) {
            log("notice page could not start: ${e.message}")
            closeQuietly(serverSocket)
            serverSocket = null
            port = 0
            0
        }
    }

    /**
     * Stops accepting and ends the worker pool. A stopped server is not
     * restartable: [VpnConnectionServer] creates a new one when the setting is
     * turned on again.
     */
    @Synchronized
    fun stop() {
        running = false
        closeQuietly(serverSocket)
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        threads.shutdownNow()
        port = 0
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                if (running) log("notice page accept failed: ${e.message}")
                break
            }
            try {
                threads.execute { handle(client) }
            } catch (e: Exception) {
                // Pool saturated (or shutting down): drop the connection
                // instead of queueing work the user is waiting on.
                closeQuietly(client)
            }
        }
    }

    private fun handle(client: Socket) {
        try {
            client.use { socket ->
                socket.soTimeout = readTimeoutMillis
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val head = readHead(input) ?: run {
                    respond(socket, output, "400 Bad Request", "text/plain; charset=utf-8", "bad request".toByteArray())
                    return
                }

                if (head.method == "CONNECT") {
                    respond(socket, output, "405 Method Not Allowed", "text/plain; charset=utf-8", "not supported".toByteArray())
                    return
                }
                // Absolute-form targets mean somebody is using this as a proxy.
                if (head.target.startsWith("http://") || head.target.startsWith("https://")) {
                    respond(socket, output, "400 Bad Request", "text/plain; charset=utf-8", "proxy use is not allowed".toByteArray())
                    return
                }

                val path = head.path().substringBefore('?')
                when (path) {
                    ALLOW_PATH -> handleAllow(socket, output, head)
                    CLOSE_PATH -> respond(
                        socket, output, "200 OK", "text/html; charset=utf-8",
                        closedPage().toByteArray(Charsets.UTF_8),
                    )
                    else -> handleSite(socket, output, head)
                }
            }
        } catch (e: SocketTimeoutException) {
            // A client that stops talking is not an error worth logging.
        } catch (e: IOException) {
            log("notice page connection failed: ${e.message}")
        } catch (e: Exception) {
            log("notice page error: ${e.message}")
        }
    }

    private fun handleSite(socket: Socket, output: OutputStream, head: HttpRequest) {
        val host = normalizeHost(head.host)
        if (host == null) {
            respond(socket, output, "400 Bad Request", "text/plain; charset=utf-8", "missing host".toByteArray())
            return
        }

        val allowed = policy.isTemporarilyAllowed(host)
        if (allowed || !policy.isBlocked(host)) {
            forward(socket, output, head, host)
            return
        }

        val html = noticePage(host, policy.sourceOf(host), head.path())
        respond(
            socket, output, "200 OK", "text/html; charset=utf-8",
            html.toByteArray(Charsets.UTF_8),
            head = head,
        )
    }

    private fun handleAllow(socket: Socket, output: OutputStream, head: HttpRequest) {
        val query = parseQuery(head.path())
        val host = normalizeHost(query["host"])
        val expires = query["exp"]?.toLongOrNull()
        val signature = query["sig"]
        if (host == null || expires == null || signature == null ||
            expires < clock() || !verify(host, expires, signature)
        ) {
            respond(
                socket, output, "403 Forbidden", "text/plain; charset=utf-8",
                "This link expired. Reload the blocked site to get a new one.".toByteArray(),
            )
            return
        }

        policy.allowTemporarily(host, allowMinutes)
        log("notice page: $host allowed for $allowMinutes minutes")
        val next = sanitizeNext(query["next"]) ?: "/"
        val location = "http://$host$next"
        respond(
            socket, output, "302 Found", "text/plain; charset=utf-8",
            "redirecting".toByteArray(),
            extraHeaders = "Location: $location\r\n",
        )
    }

    /**
     * Relays one plain HTTP request to the real site. Used only after the user
     * chose "continue": the browser still has the private address cached, so
     * the request arrives here until the DNS answer expires.
     */
    private fun forward(client: Socket, output: OutputStream, head: HttpRequest, host: String) {
        val addresses = try {
            resolver(host)
        } catch (e: Exception) {
            respond(client, output, "502 Bad Gateway", "text/plain; charset=utf-8", "cannot resolve $host".toByteArray())
            return
        }
        // Never relay to the phone itself or to the local network: a blocklist
        // entry pointing at 127.0.0.1 or 192.168.1.1 must not turn this server
        // into a way around the device's own access rules.
        if (addresses.isEmpty() || addresses.any { !addressAllowed(it) }) {
            respond(client, output, "403 Forbidden", "text/plain; charset=utf-8", "address not allowed".toByteArray())
            return
        }

        val upstream = try {
            connector(host, upstreamPort)
        } catch (e: Exception) {
            respond(client, output, "502 Bad Gateway", "text/plain; charset=utf-8", "site unreachable".toByteArray())
            return
        }

        upstream.use { remote ->
            remote.soTimeout = readTimeoutMillis
            val toRemote = remote.getOutputStream()
            toRemote.write(head.toWire(connectionClose = true))
            toRemote.flush()

            val clientToRemote = Thread({
                try {
                    copy(client.getInputStream(), toRemote)
                } catch (e: IOException) {
                    // the client went away; closing the sockets ends the relay
                } finally {
                    closeQuietly(remote)
                }
            }, "bbdns-notice-relay").apply { isDaemon = true; start() }

            try {
                copy(remote.getInputStream(), output)
            } catch (e: IOException) {
                // Upstream closed or timed out: nothing to add.
            } finally {
                clientToRemote.interrupt()
            }
        }
    }

    // ------------------------------------------------------------------ page

    private fun noticePage(host: String, source: String?, path: String): String {
        val safeHost = escapeHtml(host)
        val reason = if (source.isNullOrBlank()) {
            "a blocklist you enabled"
        } else {
            "the list <strong>${escapeHtml(source)}</strong>"
        }
        val expires = clock() + ALLOW_LINK_MINUTES * 60_000L
        val signature = sign(host, expires)
        val allowUrl = "$ALLOW_PATH?host=${urlEncode(host)}&exp=$expires&sig=${urlEncode(signature)}&next=${urlEncode(path)}"
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Site blocked</title>
<style>
  :root { color-scheme: light dark; --bg:#f6f7f4; --fg:#16211c; --muted:#5b6b62; --card:#ffffff; --line:#dfe4e0; --accent:#1b7f5b; --danger:#b3261e; }
  @media (prefers-color-scheme: dark) { :root { --bg:#0f1512; --fg:#e8efea; --muted:#9fb0a7; --card:#161f1b; --line:#26332c; --accent:#4fca9b; --danger:#ff8a80; } }
  * { box-sizing: border-box; }
  body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center; padding:24px;
         background:var(--bg); color:var(--fg); font:16px/1.5 -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { width:100%; max-width:520px; background:var(--card); border:1px solid var(--line); border-radius:20px;
         padding:28px 24px; box-shadow:0 8px 30px rgba(0,0,0,.06); }
  .badge { display:inline-flex; align-items:center; gap:8px; font-size:13px; font-weight:600; letter-spacing:.02em;
           color:var(--danger); text-transform:uppercase; }
  h1 { margin:12px 0 6px; font-size:22px; line-height:1.25; }
  p { margin:10px 0; color:var(--muted); }
  code { font:14px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; color:var(--fg); background:transparent;
         word-break:break-all; }
  .host { display:block; margin:14px 0 4px; padding:12px 14px; border:1px solid var(--line); border-radius:12px;
          background:transparent; }
  .actions { display:flex; flex-direction:column; gap:10px; margin-top:22px; }
  a.button { display:block; text-align:center; text-decoration:none; padding:13px 16px; border-radius:12px;
             font-weight:600; border:1px solid var(--line); color:var(--fg); }
  a.primary { background:var(--accent); border-color:var(--accent); color:#06231a; }
  footer { margin-top:18px; font-size:13px; color:var(--muted); }
</style>
</head>
<body>
<main>
  <div class="badge">Blocked by BBDNS</div>
  <h1>This site is on a blocklist</h1>
  <code class="host">$safeHost</code>
  <p>It was stopped by $reason on this device. No page content was downloaded and nothing was sent to the site.</p>
  <p>If you need it, you can let it through for $allowMinutes minutes. The choice is remembered only on this phone and only until protection restarts.</p>
  <div class="actions">
    <a class="button primary" href="$allowUrl">Continue to the site for $allowMinutes minutes</a>
    <a class="button" href="$CLOSE_PATH">Close</a>
  </div>
  <footer>HTTPS sites cannot show this page, because that would need to decrypt the connection. Turn the notice page off in BBDNS to stop redirecting blocked sites at all.</footer>
</main>
</body>
</html>
""".trimIndent()
    }

    private fun closedPage(): String = """
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Closed</title>
<style>
  :root { color-scheme: light dark; }
  body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center; padding:24px;
         font:16px/1.5 -apple-system, "Segoe UI", Roboto, sans-serif; }
  p { opacity:.75; text-align:center; }
</style></head>
<body><p>You can close this tab.</p></body></html>
""".trimIndent()

    // ----------------------------------------------------------------- plumbing

    private fun readHead(input: InputStream): HttpRequest? {
        val buffer = ByteArray(HttpRequest.MAX_HEAD)
        var length = 0
        while (length < buffer.size) {
            val read = input.read()
            if (read == -1) return null
            // An HTTPS connection also lands here (port 443 is relayed too, so
            // the browser gets an answer instead of a hang) and starts with a
            // TLS handshake, not a method. Drop it on the first byte.
            if (length == 0 && read !in 'A'.code..'Z'.code) return null
            buffer[length++] = read.toByte()
            // Rolling four-byte window: CRLF CRLF ends the head.
            if (length >= 4) {
                val prev = buffer[length - 4].toInt()
                val prev2 = buffer[length - 3].toInt()
                val prev3 = buffer[length - 2].toInt()
                if (prev == '\r'.code && prev2 == '\n'.code && prev3 == '\r'.code && read == '\n'.code) {
                    return HttpRequest.parse(buffer, length)
                }
            }
        }
        return null
    }

    private fun respond(
        socket: Socket,
        output: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        head: HttpRequest? = null,
        extraHeaders: String = "",
    ) {
        val builder = StringBuilder()
        builder.append("HTTP/1.1 ").append(status).append("\r\n")
        builder.append("Content-Type: ").append(contentType).append("\r\n")
        builder.append("Content-Length: ").append(body.size).append("\r\n")
        builder.append("Cache-Control: no-store\r\n")
        builder.append("X-Content-Type-Options: nosniff\r\n")
        builder.append("Referrer-Policy: no-referrer\r\n")
        builder.append("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'none'\r\n")
        builder.append("Connection: close\r\n")
        builder.append(extraHeaders)
        builder.append("\r\n")
        output.write(builder.toString().toByteArray(Charsets.ISO_8859_1))
        if (head?.method != "HEAD") output.write(body)
        output.flush()
        socket.shutdownOutputQuietly()
    }

    /**
     * Signs an allow link: loopback is reachable by every app on the phone.
     * Hex, not Base64: `java.util.Base64` needs API 26 and the app supports
     * Android 7, while `android.util.Base64` would keep this class out of the
     * JVM tests.
     */
    private fun sign(host: String, expires: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val payload = "$host|$expires".toByteArray(Charsets.UTF_8)
        return mac.doFinal(payload).toHex()
    }

    private fun verify(host: String, expires: Long, signature: String): Boolean {
        val provided = signature.hexToBytes() ?: return false
        val expected = sign(host, expires).hexToBytes() ?: return false
        return MessageDigest.isEqual(expected, provided)
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(digits[value ushr 4]).append(digits[value and 0x0F])
        }
        return out.toString()
    }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0 || length > 512) return null
        val out = ByteArray(length / 2)
        for (index in out.indices) {
            val high = Character.digit(this[index * 2], 16)
            val low = Character.digit(this[index * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    private fun sanitizeNext(next: String?): String? {
        if (next == null) return null
        if (!next.startsWith("/")) return null
        if (next.startsWith("//")) return null
        if (next.any { it == '\r' || it == '\n' || it == ' ' }) return null
        return next
    }

    private fun parseQuery(path: String): Map<String, String> {
        val raw = path.substringAfter('?', "")
        if (raw.isEmpty()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (name.isEmpty()) return@mapNotNull null
            urlDecode(name) to urlDecode(value)
        }.toMap()
    }

    private fun normalizeHost(value: String?): String? {
        if (value == null) return null
        var host = value.trim().lowercase()
        if (host.startsWith("[")) {
            // IPv6 literals are not used by the notice page.
            return null
        }
        host = host.substringBefore(':')
        host = host.trimEnd('.')
        if (host.isEmpty() || host.length > 253) return null
        if (!host.matches(HOST_PATTERN)) return null
        if (!host.contains('.')) return null
        return host
    }

    private fun escapeHtml(value: String): String = buildString(value.length) {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun urlDecode(value: String): String = try {
        java.net.URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        value
    }

    private fun copy(from: InputStream, to: OutputStream) {
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = from.read(buffer)
            if (read == -1) break
            to.write(buffer, 0, read)
            to.flush()
        }
    }

    private fun Socket.shutdownOutputQuietly() {
        try {
            shutdownOutput()
        } catch (e: IOException) {
            // already closed
        }
    }

    private fun closeQuietly(closeable: java.io.Closeable?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            // nothing to do
        }
    }

    companion object {
        const val ALLOW_PATH = "/__bbdns/allow"
        const val CLOSE_PATH = "/__bbdns/close"

        /** The link must survive a page reload, not much more. */
        private const val ALLOW_LINK_MINUTES = 5L

        private val HOST_PATTERN = Regex("^[a-z0-9]([a-z0-9_-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9_-]*[a-z0-9])?)+$")
        private val threadCount = AtomicInteger(0)

        private fun connect(host: String, port: Int, timeoutMillis: Int): Socket =
            Socket().apply {
                connect(InetSocketAddress(host, port), timeoutMillis)
                soTimeout = timeoutMillis
                tcpNoDelay = true
            }

        /**
         * True for addresses the relay may contact. The phone itself
         * (loopback), the local network and link-local ranges are excluded so
         * a blocklist entry cannot make the notice page reach device services.
         */
        fun isPublicAddress(address: InetAddress): Boolean =
            !address.isLoopbackAddress &&
                !address.isSiteLocalAddress &&
                !address.isLinkLocalAddress &&
                !address.isAnyLocalAddress &&
                !address.isMulticastAddress
    }
}
