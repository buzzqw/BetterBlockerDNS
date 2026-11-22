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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalNoticeServerTest {

    private class FakePolicy : LocalNoticeServer.NoticePolicy {
        val blocked = ConcurrentHashMap.newKeySet<String>()
        val allowed = ConcurrentHashMap.newKeySet<String>()
        var lastMinutes = 0

        override fun isBlocked(host: String) = host in blocked
        override fun sourceOf(host: String) = if (host in blocked) "HaGeZi PRO" else null
        override fun allowTemporarily(host: String, minutes: Int) {
            lastMinutes = minutes
            allowed += host
        }

        override fun isTemporarilyAllowed(host: String) = host in allowed
    }

    private lateinit var policy: FakePolicy
    private lateinit var server: LocalNoticeServer
    private lateinit var upstream: ServerSocket
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        policy = FakePolicy()
        policy.blocked += "ads.example.com"

        // A fake site: answers anything with a fixed page and closes.
        upstream = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        val accepted = CountDownLatch(1)
        Thread({
            while (!upstream.isClosed) {
                val socket = try {
                    upstream.accept()
                } catch (e: Exception) {
                    break
                }
                accepted.countDown()
                socket.use {
                    it.getInputStream().read(ByteArray(4096))
                    val body = "hello from the real site"
                    val response = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n" +
                        "Content-Type: text/plain\r\nConnection: close\r\n\r\n$body"
                    it.getOutputStream().write(response.toByteArray())
                    it.getOutputStream().flush()
                }
            }
        }, "fake-site").apply { isDaemon = true; start() }

        server = LocalNoticeServer(
            policy = policy,
            clock = { now },
            upstreamPort = upstream.localPort,
            connector = { _, port -> Socket(InetAddress.getLoopbackAddress(), port) },
            addressAllowed = { true },
            resolver = { arrayOf(InetAddress.getLoopbackAddress()) },
        )
        assertEquals(true, server.start() > 0)
    }

    @After
    fun tearDown() {
        server.stop()
        upstream.close()
    }

    @Test
    fun `a blocked site gets an explanation page instead of a connection error`() {
        val response = get("/", host = "ads.example.com")
        assertTrue(response.status.startsWith("HTTP/1.1 200"))
        assertTrue(response.body.contains("ads.example.com"))
        assertTrue(response.body.contains("HaGeZi PRO"))
        assertTrue(response.headers.contains("Cache-Control: no-store"))
        assertTrue(response.headers.contains("Content-Security-Policy"))
    }

    @Test
    fun `the page escapes what the browser put in the host header`() {
        policy.blocked += "x.example.com"
        val response = get("/", host = "x.example.com")
        assertFalse(response.body.contains("<script"))
    }

    @Test
    fun `head requests get the headers and no body`() {
        val response = get("/", host = "ads.example.com", method = "HEAD")
        assertTrue(response.status.startsWith("HTTP/1.1 200"))
        assertTrue(response.body.isEmpty())
    }

    @Test
    fun `a request without a usable host is refused`() {
        assertEquals(true, get("/", host = null).status.contains("400"))
        assertEquals(true, get("/", host = "localhost").status.contains("400"))
    }

    @Test
    fun `absolute targets are not proxied`() {
        val response = get("http://ads.example.com/", host = "ads.example.com")
        assertTrue(response.status.contains("400"))
    }

    @Test
    fun `continue is allowed through a signed link and then forwards the site`() {
        val page = get("/promo?id=1", host = "ads.example.com").body
        val link = Regex("""href="(/__bbdns/allow\?[^"]+)"""").find(page)?.groupValues?.get(1)
        assertTrue(link != null)

        val allow = get(link!!, host = "ads.example.com")
        assertTrue(allow.status.contains("302"))
        assertTrue(allow.headers.contains("Location: http://ads.example.com/promo?id=1"))
        assertEquals(10, policy.lastMinutes)

        // The browser still has the private address cached and asks again:
        // the request is now relayed to the real site.
        val forwarded = get("/promo?id=1", host = "ads.example.com")
        assertTrue(forwarded.status.startsWith("HTTP/1.1 200"))
        assertTrue(forwarded.body.contains("hello from the real site"))
    }

    @Test
    fun `a forged or expired allow link is refused`() {
        val page = get("/", host = "ads.example.com").body
        val link = Regex("""href="(/__bbdns/allow\?[^"]+)"""").find(page)!!.groupValues[1]

        assertEquals(true, get(link.replace("sig=", "sig=x"), host = "ads.example.com").status.contains("403"))

        now += 10 * 60_000
        assertEquals(true, get(link, host = "ads.example.com").status.contains("403"))
        assertTrue(policy.allowed.isEmpty())
    }

    @Test
    fun `a site that is no longer blocked is simply relayed`() {
        policy.blocked.clear()
        val response = get("/", host = "news.example.com")
        assertTrue(response.body.contains("hello from the real site"))
    }

    @Test
    fun `the relay refuses loopback and private addresses in production mode`() {
        server.stop()
        server = LocalNoticeServer(
            policy = policy,
            upstreamPort = upstream.localPort,
            connector = { _, port -> Socket(InetAddress.getLoopbackAddress(), port) },
            resolver = { arrayOf(InetAddress.getLoopbackAddress()) },
        ).also { it.start() }
        policy.allowed += "ads.example.com"

        val response = get("/", host = "ads.example.com")
        assertTrue(response.status.contains("403"))
    }

    // ------------------------------------------------------------------ helper

    private data class Response(val status: String, val headers: String, val body: String)

    private fun get(path: String, host: String?, method: String = "GET"): Response {
        Socket(InetAddress.getLoopbackAddress(), server.port).use { socket ->
            socket.soTimeout = 5_000
            val request = buildString {
                append("$method $path HTTP/1.1\r\n")
                if (host != null) append("Host: $host\r\n")
                append("User-Agent: test\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().write(request.toByteArray())
            socket.getOutputStream().flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val status = reader.readLine() ?: ""
            val headers = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                headers.append(line).append('\n')
            }
            val body = reader.readText()
            return Response(status, headers.toString(), body)
        }
    }
}
