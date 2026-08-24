package com.dnsspeedtest.app.dns

import kotlin.math.sqrt

data class ServerQueryGroup(
    val server: DnsServer,
    val results: List<DnsQueryResult>,
) {
    val totalCount: Int = results.size
    val successCount: Int = results.count { it.success }
    val successRate: Float = if (totalCount == 0) 0f else successCount.toFloat() / totalCount
    val latenciesMs: List<Long> = results.filter { it.success }.map { it.timings.totalMs }
    val minMs: Long? = latenciesMs.minOrNull()
    val maxMs: Long? = latenciesMs.maxOrNull()
    val avgMs: Long? = latenciesMs.takeIf { it.isNotEmpty() }?.average()?.toLong()
    val jitterMs: Long? = if (latenciesMs.size >= 2) maxMs!! - minMs!! else null
    val stdevMs: Long? = standardDeviation(latenciesMs)
    val answerSignatures: Set<String> = results.mapNotNull { result ->
        if (!result.success) {
            null
        } else {
            result.message?.answers
                ?.map { "${it.type} ${it.data}" }
                ?.sorted()
                ?.joinToString(" | ")
                ?.ifEmpty { "(空应答)" }
        }
    }.toSet()
    val answersStable: Boolean = answerSignatures.size <= 1
    val answerSummary: String = answerSignatures.singleOrNull()
        ?: if (answerSignatures.isEmpty()) "无成功解析" else "解析结果不一致"
}

enum class ResultSortKey(val label: String) {
    FASTEST("最快"),
    AVERAGE("平均"),
    SLOWEST("最慢"),
    JITTER("抖动"),
    SUCCESS_RATE("成功率"),
}

fun aggregateByServer(results: List<DnsQueryResult>): List<ServerQueryGroup> {
    return results
        .groupBy { it.server.id }
        .map { (_, group) ->
            val ordered = group.sortedWith(compareBy<DnsQueryResult> { it.round }.thenBy { it.startedAtMs })
            ServerQueryGroup(server = ordered.first().server, results = ordered)
        }
}

fun sortServerGroups(
    groups: List<ServerQueryGroup>,
    key: ResultSortKey,
    ascending: Boolean,
): List<ServerQueryGroup> {
    return groups.sortedWith(
        compareBy<ServerQueryGroup> { it.sortValue(key) == null }
            .thenComparator { left, right ->
                val leftValue = left.sortValue(key) ?: 0.0
                val rightValue = right.sortValue(key) ?: 0.0
                val comparison = leftValue.compareTo(rightValue)
                if (ascending) comparison else -comparison
            }
            .thenBy { it.server.name }
            .thenBy { it.server.protocol.name },
    )
}

fun ServerQueryGroup.sortValue(key: ResultSortKey): Double? = when (key) {
    ResultSortKey.FASTEST -> minMs?.toDouble()
    ResultSortKey.AVERAGE -> avgMs?.toDouble()
    ResultSortKey.SLOWEST -> maxMs?.toDouble()
    ResultSortKey.JITTER -> jitterMs?.toDouble()
    ResultSortKey.SUCCESS_RATE -> successRate.toDouble()
}

fun ServerQueryGroup.metricLabel(key: ResultSortKey): String = when (key) {
    ResultSortKey.FASTEST -> minMs?.let { "$it ms" } ?: "—"
    ResultSortKey.AVERAGE -> avgMs?.let { "$it ms" } ?: "—"
    ResultSortKey.SLOWEST -> maxMs?.let { "$it ms" } ?: "—"
    ResultSortKey.JITTER -> jitterMs?.let { "$it ms" } ?: "—"
    ResultSortKey.SUCCESS_RATE -> "${(successRate * 100).toInt()}%"
}

fun rankingBarFraction(
    groups: List<ServerQueryGroup>,
    key: ResultSortKey,
    ascending: Boolean,
    group: ServerQueryGroup,
): Float {
    val value = group.sortValue(key) ?: return 0.04f
    val values = groups.mapNotNull { it.sortValue(key) }
    val min = values.minOrNull() ?: return 0.04f
    val max = values.maxOrNull() ?: return 0.04f
    if (max == min) return 1f
    val normalized = ((value - min) / (max - min)).toFloat()
    val fraction = if (ascending) 1f - normalized else normalized
    return fraction.coerceIn(0.06f, 1f)
}

private fun standardDeviation(values: List<Long>): Long? {
    if (values.size < 2) return null
    val mean = values.average()
    val variance = values.map { value ->
        val delta = value - mean
        delta * delta
    }.average()
    return sqrt(variance).toLong()
}
