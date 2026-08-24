package com.dnsspeedtest.app.dns

import kotlinx.serialization.Serializable

@Serializable
enum class DnsProtocol {
    DOH,
    DOT,
}

@Serializable
enum class RecordType(val code: Int, val label: String) {
    A(1, "A"),
    NS(2, "NS"),
    CNAME(5, "CNAME"),
    SOA(6, "SOA"),
    PTR(12, "PTR"),
    MX(15, "MX"),
    TXT(16, "TXT"),
    AAAA(28, "AAAA"),
    HTTPS(65, "HTTPS"),
    ;

    companion object {
        fun fromCode(code: Int): RecordType? = entries.firstOrNull { it.code == code }
    }
}

@Serializable
data class DnsServer(
    val id: String,
    val name: String,
    val protocol: DnsProtocol,
    val host: String,
    val port: Int,
    val path: String = "/dns-query",
    val bootstrapIps: List<String> = emptyList(),
    val sni: String = host,
)

@Serializable
data class QueryEvent(
    val elapsedMs: Long,
    val stage: String,
    val detail: String,
)

@Serializable
data class QueryTimings(
    val dnsLookupMs: Long? = null,
    val connectMs: Long? = null,
    val tlsMs: Long? = null,
    val firstByteMs: Long? = null,
    val totalMs: Long = 0,
)

@Serializable
data class DnsRecord(
    val name: String,
    val type: String,
    val ttl: Int,
    val data: String,
)

@Serializable
data class DnsMessageView(
    val id: Int,
    val rcode: String,
    val flags: List<String>,
    val questions: List<String>,
    val answers: List<DnsRecord>,
    val authorities: List<DnsRecord>,
    val additionals: List<DnsRecord>,
)

@Serializable
data class NetworkSnapshot(
    val type: String,
    val transports: List<String>,
    val hasInternet: Boolean,
    val isValidated: Boolean,
    val isMetered: Boolean,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
    val privateDnsMode: String?,
    val privateDnsSpecifier: String?,
    val capturedAtMs: Long,
)

@Serializable
data class DnsQueryResult(
    val id: String,
    val server: DnsServer,
    val domain: String,
    val recordType: String,
    val success: Boolean,
    val error: String? = null,
    val httpStatus: Int? = null,
    val tlsProtocol: String? = null,
    val tlsCipher: String? = null,
    val httpProtocol: String? = null,
    val remoteAddress: String? = null,
    val timings: QueryTimings,
    val events: List<QueryEvent>,
    val message: DnsMessageView? = null,
    val network: NetworkSnapshot,
    val startedAtMs: Long,
    val round: Int,
)

@Serializable
data class HistorySession(
    val id: String,
    val startedAtMs: Long,
    val domain: String,
    val recordType: String,
    val networkLabel: String,
    val results: List<DnsQueryResult>,
)

@Serializable
data class UserSettings(
    val timeoutMs: Int = 8_000,
    val rounds: Int = 1,
    val reuseConnections: Boolean = false,
    val selectedServerIds: Set<String> = DnsServerCatalog.defaultSelectedIds,
    val colorSchemeMode: String = "System",
    val protocolFilter: String = "ALL",
    val recordType: String = RecordType.A.name,
    val lastDomain: String = "www.example.com",
    val customServers: List<DnsServer> = emptyList(),
    val hiddenBuiltinServerIds: Set<String> = emptySet(),
    val resultSortKey: String = ResultSortKey.FASTEST.name,
    val resultSortAscending: Boolean = true,
    val recentDomains: List<String> = emptyList(),
)

fun rcodeName(code: Int): String = when (code) {
    0 -> "NOERROR"
    1 -> "FORMERR"
    2 -> "SERVFAIL"
    3 -> "NXDOMAIN"
    4 -> "NOTIMP"
    5 -> "REFUSED"
    6 -> "YXDOMAIN"
    7 -> "YXRRSET"
    8 -> "NXRRSET"
    9 -> "NOTAUTH"
    10 -> "NOTZONE"
    16 -> "BADVERS"
    else -> "RCODE_$code"
}
