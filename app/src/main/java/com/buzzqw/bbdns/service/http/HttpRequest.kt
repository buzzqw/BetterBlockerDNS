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

/**
 * The head of a plain HTTP/1.x request, parsed only as far as the local
 * notice page needs it: method, target, host and the bytes to replay.
 *
 * Deliberately not a general HTTP parser. It never reads past the blank line
 * that ends the head, so a request body stays untouched in the socket and can
 * be forwarded as it is.
 */
class HttpRequest private constructor(
    val method: String,
    val target: String,
    val version: String,
    private val headers: Map<String, String>,
    /** The headers in the order the browser sent them, for the replay. */
    private val rawHeaders: List<Pair<String, String>>,
    /** Bytes of the head including the blank line that ends it. */
    val length: Int,
) {

    val host: String? get() = headers["host"]

    fun header(name: String): String? = headers[name.lowercase()]

    /** Path and query of [target], without scheme or authority. */
    fun path(): String {
        val value = target
        if (value.startsWith("http://") || value.startsWith("https://")) {
            val afterScheme = value.substringAfter("://")
            val slash = afterScheme.indexOf('/')
            return if (slash == -1) "/" else afterScheme.substring(slash)
        }
        return if (value.startsWith("/")) value else "/"
    }

    /**
     * Serialises the request for the upstream server, with [connectionClose]
     * forcing one request per connection: the notice page relays a single
     * request and does not implement keep-alive or pipelining.
     */
    fun toWire(connectionClose: Boolean = true): ByteArray {
        val builder = StringBuilder()
        builder.append(method).append(' ').append(target).append(' ').append(version).append("\r\n")
        for ((name, value) in rawHeaders) {
            val lower = name.lowercase()
            if (connectionClose && (lower == "connection" || lower == "proxy-connection" || lower == "keep-alive")) continue
            builder.append(name).append(": ").append(value).append("\r\n")
        }
        if (connectionClose) builder.append("Connection: close\r\n")
        builder.append("\r\n")
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }

    companion object {
        const val MAX_HEAD = 16 * 1024

        private val METHODS = setOf(
            "GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "TRACE",
        )

        /** Parses a head already read from the socket, or null when malformed. */
        fun parse(bytes: ByteArray, length: Int): HttpRequest? {
            if (length <= 0 || length > bytes.size) return null
            val text = String(bytes, 0, length, Charsets.ISO_8859_1)
            if (!text.endsWith("\r\n\r\n") && !text.endsWith("\n\n")) return null
            val lines = text.split("\r\n", "\n")
            if (lines.isEmpty()) return null

            val requestLine = lines[0]
            val parts = requestLine.split(' ')
            if (parts.size != 3) return null
            val method = parts[0].uppercase()
            if (method !in METHODS) return null
            val target = parts[1]
            val version = parts[2]
            if (!version.startsWith("HTTP/1.")) return null
            if (target.isEmpty() || target.length > 8 * 1024) return null
            if (target.any { it == '\r' || it == '\n' || it == ' ' }) return null

            val raw = ArrayList<Pair<String, String>>()
            val byName = HashMap<String, String>()
            for (line in lines.drop(1)) {
                if (line.isEmpty()) continue
                if (line[0] == ' ' || line[0] == '\t') return null // obsolete line folding
                val colon = line.indexOf(':')
                if (colon <= 0) return null
                val name = line.substring(0, colon).trim()
                val value = line.substring(colon + 1).trim()
                if (name.isEmpty() || name.length > 256) return null
                if (value.any { it == '\r' || it == '\n' }) return null
                raw += name to value
                byName.putIfAbsent(name.lowercase(), value)
            }

            val request = HttpRequest(method, target, version, byName, raw, length)
            return request
        }
    }
}
