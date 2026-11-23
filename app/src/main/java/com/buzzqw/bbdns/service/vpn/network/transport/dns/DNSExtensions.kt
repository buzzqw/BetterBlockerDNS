/*
 * Copyright (C) 2025-2026 Vexzure
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

import java.io.IOException
import java.nio.ByteBuffer

/** A chain longer than this is either malformed or a pointer loop. */
private const val MAX_NAME_POINTERS = 10

/**
 * Reads a DNS name at the current position, following RFC 1035 compression
 * pointers. Every offset is bounds checked: a malformed packet must be
 * rejected instead of throwing a raw buffer underflow further down the path.
 */
fun ByteBuffer.readDomainName(): String {
    val parts = mutableListOf<String>()
    val limit = limit()
    var position = position()
    var resumePosition = -1
    var jumps = 0

    while (true) {
        if (position >= limit) throw IOException("Truncated DNS name")
        val length = get(position).toInt() and 0xFF
        when {
            length and 0xC0 == 0xC0 -> {
                if (position + 1 >= limit) throw IOException("Truncated DNS name pointer")
                val pointer = ((length and 0x3F) shl 8) or (get(position + 1).toInt() and 0xFF)
                // Remember where the name ends on the wire, so the caller
                // resumes after the pointer rather than inside the chain it
                // pointed at.
                if (resumePosition < 0) resumePosition = position + 2
                if (++jumps > MAX_NAME_POINTERS) throw IOException("DNS name pointer loop")
                position = pointer
            }
            length and 0xC0 != 0 -> throw IOException("Invalid DNS label length")
            length == 0 -> {
                if (resumePosition >= 0) position(resumePosition) else position(position + 1)
                return parts.joinToString(".")
            }
            else -> {
                val start = position + 1
                val end = start + length
                if (end > limit) throw IOException("Truncated DNS label")
                parts.add(ByteArray(length) { i -> get(start + i) }.toString(Charsets.UTF_8))
                position = end
            }
        }
    }
}

fun ByteBuffer.toDNSModel(): DNSModel {
    if (remaining() < 12) throw IOException("Truncated DNS header")
    val transactionId = this.short.toInt()
    val flags = this.short.toInt()
    val questionCount = this.short.toInt()
    val answerCount = this.short.toInt()
    val authorityCount = this.short.toInt()
    val additionalCount = this.short.toInt()
    val domainName = this.readDomainName()
    if (remaining() < 4) throw IOException("Truncated DNS question")
    val queryType = this.short.toInt()
    val queryClass = this.short.toInt()

    return DNSModel(
        transactionId = transactionId,
        flags = flags,
        questionCount = questionCount,
        answerCount = answerCount,
        authorityCount = authorityCount,
        additionalCount = additionalCount,
        domainName = domainName,
        queryType = queryType,
        queryClass = queryClass
    )
}
