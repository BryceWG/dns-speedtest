package com.dnsspeedtest.app.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomServerParserTest {
    @Test
    fun parseDohHttpsUrl() {
        val server = CustomServerParser.parse(
            name = "My DoH",
            protocol = DnsProtocol.DOH,
            address = "https://dns.example.com/dns-query",
        ).getOrThrow()
        assertEquals("My DoH", server.name)
        assertEquals(DnsProtocol.DOH, server.protocol)
        assertEquals("dns.example.com", server.host)
        assertEquals(443, server.port)
        assertEquals("/dns-query", server.path)
        assertEquals("dns.example.com", server.sni)
        assertTrue(server.isCustom())
    }

    @Test
    fun parseDohHostUsesDefaultPath() {
        val server = CustomServerParser.parse(
            name = "",
            protocol = DnsProtocol.DOH,
            address = "dns.google",
            bootstrapText = "8.8.8.8, 8.8.4.4",
        ).getOrThrow()
        assertEquals("dns.google", server.name)
        assertEquals("/dns-query", server.path)
        assertEquals(listOf("8.8.8.8", "8.8.4.4"), server.bootstrapIps)
    }

    @Test
    fun parseDotHostAndPort() {
        val server = CustomServerParser.parse(
            name = "Custom DoT",
            protocol = DnsProtocol.DOT,
            address = "1.1.1.1:853",
            sniText = "cloudflare-dns.com",
        ).getOrThrow()
        assertEquals(DnsProtocol.DOT, server.protocol)
        assertEquals("1.1.1.1", server.host)
        assertEquals(853, server.port)
        assertEquals("cloudflare-dns.com", server.sni)
    }

    @Test
    fun parseKeepsExistingCustomId() {
        val original = CustomServerParser.parse(
            name = "Old",
            protocol = DnsProtocol.DOH,
            address = "https://dns.example.com/dns-query",
        ).getOrThrow()
        val updated = CustomServerParser.parse(
            name = "New",
            protocol = DnsProtocol.DOH,
            address = "https://dns.example.com/dns-query",
            existingId = original.id,
        ).getOrThrow()
        assertEquals(original.id, updated.id)
        assertEquals("New", updated.name)
    }

    @Test
    fun rejectCleartextDoh() {
        val result = CustomServerParser.parse(
            name = "",
            protocol = DnsProtocol.DOH,
            address = "http://dns.example.com/dns-query",
        )
        assertTrue(result.isFailure)
    }
}
