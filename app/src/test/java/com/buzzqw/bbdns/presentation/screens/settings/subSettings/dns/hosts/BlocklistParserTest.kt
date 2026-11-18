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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistParserTest {

    @Test
    fun `parses hosts file entries`() {
        assertEquals(
            BlocklistRule.PlainDomain("ads.example.com"),
            BlocklistParser.parseLine("0.0.0.0 ads.example.com")
        )
        assertEquals(
            BlocklistRule.PlainDomain("tracker.example.com"),
            BlocklistParser.parseLine("127.0.0.1 tracker.example.com # comment")
        )
        assertEquals(
            BlocklistRule.PlainDomain("example.com"),
            BlocklistParser.parseLine("example.com")
        )
    }

    @Test
    fun `parses ABP block and allow rules`() {
        assertEquals(
            BlocklistRule.PlainDomain("ads.example.com"),
            BlocklistParser.parseLine("||ads.example.com^")
        )
        assertEquals(
            BlocklistRule.WhitelistDomain("example.com"),
            BlocklistParser.parseLine("@@||example.com^")
        )
    }

    @Test
    fun `keeps ABP wildcards as wildcards`() {
        val rule = BlocklistParser.parseLine("||youtube.*^")
        assertTrue(rule is BlocklistRule.WildcardDomain)
        rule as BlocklistRule.WildcardDomain
        assertTrue(rule.regex.matches("youtube.com"))
        assertTrue(rule.regex.matches("youtube.co.uk"))
        assertNull(BlocklistParser.parseLine("||youtube^"))
    }

    @Test
    fun `skips browser-only cosmetic and scriptlet rules`() {
        listOf(
            "example.com##.ad-banner",
            "example.com#@#.ad-banner",
            "example.com#?#.ad-banner",
            "example.com#%#//scriptlet('abort-on-property-read')",
            "example.com##+js(abort-on-property-read)",
            "example.com\$\$script[data-ad]"
        ).forEach { line ->
            assertNull(line, BlocklistParser.parseLine(line))
        }
    }

    @Test
    fun `ignores comments and blanks`() {
        assertNull(BlocklistParser.parseLine(""))
        assertNull(BlocklistParser.parseLine("# a comment"))
        assertNull(BlocklistParser.parseLine("! EasyList header"))
        assertNull(BlocklistParser.parseLine("   "))
    }

    @Test
    fun `parses DNSmasq and wildcard formats`() {
        assertEquals(
            BlocklistRule.PlainDomain("ads.example.com"),
            BlocklistParser.parseLine("address=/ads.example.com/0.0.0.0")
        )
        val wildcard = BlocklistParser.parseLine("*.doubleclick.net")
        assertTrue(wildcard is BlocklistRule.WildcardDomain)
        assertTrue((wildcard as BlocklistRule.WildcardDomain).regex.matches("ads.doubleclick.net"))
        assertTrue(!wildcard.regex.matches("doubleclick.net"))
    }

    @Test
    fun `parses slash and pihole regular expressions`() {
        val slash = BlocklistParser.parseLine("/^ads?\\..*/")
        assertNotNull(slash)
        assertTrue(slash is BlocklistRule.RegexPattern)
        assertTrue((slash as BlocklistRule.RegexPattern).regex.matches("ads.example.com"))

        val pihole = BlocklistParser.parseLine("(^|\\.)ads?[0-9]*\\.")
        assertNotNull(pihole)
        assertTrue(pihole is BlocklistRule.RegexPattern)
        assertTrue((pihole as BlocklistRule.RegexPattern).regex.containsMatchIn("ads12.example.com"))
    }

    @Test
    fun `rejects invalid domains`() {
        assertNull(BlocklistParser.parseLine("http://example.com/path"))
        assertNull(BlocklistParser.parseLine("not_a_domain"))
        assertNull(BlocklistParser.parseLine("bad..example.com"))
    }
}
