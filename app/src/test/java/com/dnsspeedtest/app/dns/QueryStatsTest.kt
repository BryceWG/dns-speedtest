package com.dnsspeedtest.app.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryStatsTest {
    @Test
    fun aggregateByServerComputesLatencyStats() {
        val server = DnsServerCatalog.all.first { it.protocol == DnsProtocol.DOH }
        val results = listOf(
            sampleResult(server, round = 1, success = true, totalMs = 20),
            sampleResult(server, round = 2, success = true, totalMs = 10),
            sampleResult(server, round = 3, success = false, totalMs = 0),
        )
        val group = aggregateByServer(results).single()
        assertEquals(3, group.totalCount)
        assertEquals(2, group.successCount)
        assertEquals(10L, group.minMs)
        assertEquals(20L, group.maxMs)
        assertEquals(15L, group.avgMs)
        assertEquals(10L, group.jitterMs)
        assertTrue(group.answersStable)
    }

    @Test
    fun aggregateDetectsInconsistentAnswers() {
        val server = DnsServerCatalog.all.first { it.protocol == DnsProtocol.DOT }
        val results = listOf(
            sampleResult(server, round = 1, success = true, totalMs = 12, answer = "1.1.1.1"),
            sampleResult(server, round = 2, success = true, totalMs = 14, answer = "1.0.0.1"),
        )
        val group = aggregateByServer(results).single()
        assertFalse(group.answersStable)
        assertEquals("解析结果不一致", group.answerSummary)
    }

    @Test
    fun sortServerGroupsByFastestThenSuccessRate() {
        val first = DnsServerCatalog.all.first { it.protocol == DnsProtocol.DOH }
        val second = DnsServerCatalog.all.first { it.protocol == DnsProtocol.DOT }
        val groups = aggregateByServer(
            listOf(
                sampleResult(first, round = 1, success = true, totalMs = 30),
                sampleResult(second, round = 1, success = true, totalMs = 10),
                sampleResult(second, round = 2, success = false, totalMs = 0),
            ),
        )
        val byFastest = sortServerGroups(groups, ResultSortKey.FASTEST, ascending = true)
        assertEquals(second.id, byFastest.first().server.id)
        val bySuccess = sortServerGroups(groups, ResultSortKey.SUCCESS_RATE, ascending = false)
        assertEquals(first.id, bySuccess.first().server.id)
        val faster = byFastest.first()
        val slower = byFastest.last()
        assertTrue(
            rankingBarFraction(byFastest, ResultSortKey.FASTEST, ascending = true, faster) >
                rankingBarFraction(byFastest, ResultSortKey.FASTEST, ascending = true, slower),
        )
    }

    private fun sampleResult(
        server: DnsServer,
        round: Int,
        success: Boolean,
        totalMs: Long,
        answer: String = "93.184.216.34",
    ): DnsQueryResult {
        return DnsQueryResult(
            id = "${server.id}-$round",
            server = server,
            domain = "example.com",
            recordType = "A",
            success = success,
            error = if (success) null else "timeout",
            timings = QueryTimings(totalMs = totalMs),
            events = emptyList(),
            message = if (success) {
                DnsMessageView(
                    id = round,
                    rcode = "NOERROR",
                    flags = listOf("QR"),
                    questions = listOf("example.com A"),
                    answers = listOf(DnsRecord("example.com", "A", 60, answer)),
                    authorities = emptyList(),
                    additionals = emptyList(),
                )
            } else {
                null
            },
            network = NetworkSnapshot(
                type = "Wi-Fi",
                transports = listOf("Wi-Fi"),
                hasInternet = true,
                isValidated = true,
                isMetered = false,
                downstreamKbps = null,
                upstreamKbps = null,
                privateDnsMode = "off",
                privateDnsSpecifier = null,
                capturedAtMs = 0L,
            ),
            startedAtMs = round * 1_000L,
            round = round,
        )
    }
}
