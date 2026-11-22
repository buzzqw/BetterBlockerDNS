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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestTest {

    private fun parse(head: String): HttpRequest? {
        val bytes = head.toByteArray(Charsets.ISO_8859_1)
        return HttpRequest.parse(bytes, bytes.size)
    }

    @Test
    fun `parses method, target and host`() {
        val request = parse(
            "GET /path?q=1 HTTP/1.1\r\nHost: Example.com:8080\r\nUser-Agent: test\r\n\r\n",
        )!!
        assertEquals("GET", request.method)
        assertEquals("/path?q=1", request.target)
        assertEquals("HTTP/1.1", request.version)
        assertEquals("Example.com:8080", request.host)
        assertEquals("/path?q=1", request.path())
    }

    @Test
    fun `header lookup is case insensitive`() {
        val request = parse("GET / HTTP/1.1\r\nHOST: example.com\r\n\r\n")!!
        assertEquals("example.com", request.header("host"))
        assertEquals("example.com", request.header("Host"))
        assertNull(request.header("missing"))
    }

    @Test
    fun `replay drops hop-by-hop headers and forces a close`() {
        val request = parse(
            "GET /page HTTP/1.1\r\nHost: example.com\r\nConnection: keep-alive\r\n" +
                "Proxy-Connection: keep-alive\r\nAccept: text/html\r\n\r\n",
        )!!
        val wire = String(request.toWire(), Charsets.ISO_8859_1)
        assertTrue(wire.startsWith("GET /page HTTP/1.1\r\n"))
        assertTrue(wire.contains("Host: example.com\r\n"))
        assertTrue(wire.contains("Accept: text/html\r\n"))
        assertTrue(wire.contains("Connection: close\r\n"))
        assertTrue(!wire.contains("keep-alive"))
        assertTrue(wire.endsWith("\r\n\r\n"))
    }

    @Test
    fun `an absolute target is reduced to its path`() {
        val request = parse("GET http://example.com/a/b?c=d HTTP/1.1\r\nHost: example.com\r\n\r\n")!!
        assertEquals("/a/b?c=d", request.path())
    }

    @Test
    fun `malformed heads are rejected`() {
        assertNull(parse("GET / HTTP/1.1\r\nHost: example.com\r\n")) // no blank line
        assertNull(parse("BOGUS / HTTP/1.1\r\nHost: example.com\r\n\r\n"))
        assertNull(parse("GET / FTP/1.1\r\nHost: example.com\r\n\r\n"))
        assertNull(parse("GET / HTTP/1.1\r\nNoColon\r\n\r\n"))
        assertNull(parse("GET / HTTP/1.1\r\nHost: a\r\n folded\r\n\r\n"))
        assertNull(HttpRequest.parse(ByteArray(0), 0))
    }

    @Test
    fun `control characters in the target are rejected`() {
        assertNull(parse("GET /a\rb HTTP/1.1\r\nHost: example.com\r\n\r\n"))
    }
}
