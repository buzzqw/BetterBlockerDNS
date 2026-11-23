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

package com.buzzqw.bbdns.service.vpn.network.transport.dns

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.nio.ByteBuffer

class DNSExtensionsTest {

    private fun name(vararg labels: String): ByteArray = buildList<Byte> {
        labels.forEach { label ->
            add(label.length.toByte())
            label.forEach { add(it.code.toByte()) }
        }
        add(0)
    }.toByteArray()

    @Test
    fun `a plain name is read and the cursor moves past it`() {
        val bytes = name("www", "example", "com")
        val buffer = ByteBuffer.wrap(bytes)
        assertEquals("www.example.com", buffer.readDomainName())
        assertEquals(bytes.size, buffer.position())
    }

    @Test
    fun `a compressed name follows the pointer`() {
        val target = name("example", "com")
        val wire = ByteArray(target.size + 6)
        System.arraycopy(target, 0, wire, 0, target.size)
        wire[target.size] = 3
        wire[target.size + 1] = 'w'.code.toByte()
        wire[target.size + 2] = 'w'.code.toByte()
        wire[target.size + 3] = 'w'.code.toByte()
        wire[target.size + 4] = 0xC0.toByte()
        wire[target.size + 5] = 0x00

        val buffer = ByteBuffer.wrap(wire)
        buffer.position(target.size)
        assertEquals("www.example.com", buffer.readDomainName())
        assertEquals(wire.size, buffer.position())
    }

    @Test(expected = IOException::class)
    fun `a pointer loop is rejected`() {
        ByteBuffer.wrap(byteArrayOf(0xC0.toByte(), 0x00)).readDomainName()
    }

    @Test(expected = IOException::class)
    fun `a truncated label is rejected`() {
        ByteBuffer.wrap(byteArrayOf(3, 'a'.code.toByte())).readDomainName()
    }

    @Test(expected = IOException::class)
    fun `an invalid label length is rejected`() {
        ByteBuffer.wrap(byteArrayOf(0x40, 0x00)).readDomainName()
    }

    @Test(expected = IOException::class)
    fun `a pointer without its low byte is rejected`() {
        ByteBuffer.wrap(byteArrayOf(0xC0.toByte())).readDomainName()
    }

    @Test
    fun `a query is parsed from its wire form`() {
        val question = name("metrics", "example", "com")
        val wire = ByteBuffer.allocate(12 + question.size + 4)
        wire.putShort(0x1234)
        wire.putShort(0x0100)
        wire.putShort(1) // questions
        wire.putShort(0)
        wire.putShort(0)
        wire.putShort(0)
        wire.put(question)
        wire.putShort(1) // A
        wire.putShort(1) // IN

        val model = ByteBuffer.wrap(wire.array()).toDNSModel()
        assertEquals(0x1234, model.transactionId)
        assertEquals("metrics.example.com", model.domainName)
        assertEquals(1, model.queryType)
        assertEquals(1, model.queryClass)
    }

    @Test(expected = IOException::class)
    fun `a short header is rejected`() {
        ByteBuffer.wrap(ByteArray(11)).toDNSModel()
    }

    @Test(expected = IOException::class)
    fun `a question without type and class is rejected`() {
        val wire = ByteBuffer.allocate(12 + name("example", "com").size)
        repeat(6) { wire.putShort(0) }
        wire.put(name("example", "com"))
        ByteBuffer.wrap(wire.array()).toDNSModel()
    }
}
