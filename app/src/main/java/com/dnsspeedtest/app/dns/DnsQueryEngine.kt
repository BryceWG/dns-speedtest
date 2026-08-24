package com.dnsspeedtest.app.dns

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class DnsQueryEngine(
    private val dohClient: DohClient = DohClient(),
    private val dotClient: DotClient = DotClient(),
) {
    suspend fun query(
        server: DnsServer,
        domain: String,
        type: RecordType,
        timeoutMs: Int,
        round: Int,
        network: NetworkSnapshot,
    ): DnsQueryResult = withContext(Dispatchers.IO) {
        val probe = when (server.protocol) {
            DnsProtocol.DOH -> dohClient.query(server, domain, type, timeoutMs)
            DnsProtocol.DOT -> dotClient.query(server, domain, type, timeoutMs)
        }
        DnsQueryResult(
            id = UUID.randomUUID().toString(),
            server = server,
            domain = domain,
            recordType = type.label,
            success = probe.success,
            error = probe.error,
            httpStatus = probe.httpStatus,
            tlsProtocol = probe.tlsProtocol,
            tlsCipher = probe.tlsCipher,
            httpProtocol = probe.httpProtocol,
            remoteAddress = probe.remoteAddress,
            timings = probe.timings,
            events = probe.events,
            message = probe.message,
            network = network,
            startedAtMs = probe.startedAtMs,
            round = round,
        )
    }

    suspend fun queryAll(
        servers: List<DnsServer>,
        domain: String,
        type: RecordType,
        timeoutMs: Int,
        round: Int,
        network: NetworkSnapshot,
        parallelism: Int = 4,
    ): List<DnsQueryResult> = coroutineScope {
        val semaphore = Semaphore(parallelism.coerceAtLeast(1))
        servers.map { server ->
            async {
                semaphore.withPermit {
                    query(server, domain, type, timeoutMs, round, network)
                }
            }
        }.map { it.await() }
    }
}

fun DnsQueryResult.answerSummary(): String {
    val answers = message?.answers.orEmpty()
    if (answers.isNotEmpty()) {
        return answers.joinToString { "${it.type} ${it.data}" }
    }
    return error ?: message?.rcode ?: "无记录"
}

fun fastestSuccessful(results: List<DnsQueryResult>): DnsQueryResult? =
    results.filter { it.success }.minByOrNull { it.timings.totalMs }

fun answerGroups(results: List<DnsQueryResult>): Map<String, List<String>> {
    return results
        .filter { it.success }
        .groupBy { result ->
            result.message?.answers
                ?.map { "${it.type} ${it.data}" }
                ?.sorted()
                ?.joinToString(" | ")
                .orEmpty()
                .ifEmpty { "(空应答)" }
        }
        .mapValues { (_, group) -> group.map { it.server.name + " " + it.server.protocol.name } }
}
