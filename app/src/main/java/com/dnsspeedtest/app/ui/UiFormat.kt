package com.dnsspeedtest.app.ui

import com.dnsspeedtest.app.dns.CustomServerParser
import com.dnsspeedtest.app.dns.DnsProtocol
import com.dnsspeedtest.app.dns.DnsQueryResult
import com.dnsspeedtest.app.network.privateDnsLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toMsLabel(): String = "$this ms"

fun Long.toTimeLabel(): String {
    val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(this))
}

fun DnsProtocol.label(): String = when (this) {
    DnsProtocol.DOH -> "DoH"
    DnsProtocol.DOT -> "DoT"
}

fun privateDnsModeLabel(mode: String?): String = when (mode) {
    "off" -> "关闭"
    "opportunistic" -> "自动"
    "hostname" -> "指定主机"
    else -> mode ?: "未知"
}

fun DnsQueryResult.toCopyText(): String = buildString {
    appendLine("DNS 查询结果")
    appendLine("服务器：${server.name} ${server.protocol.label()}")
    appendLine("地址：${CustomServerParser.formatAddress(server)}")
    if (server.sni.isNotBlank() && server.sni != server.host) {
        appendLine("SNI：${server.sni}")
    }
    if (server.bootstrapIps.isNotEmpty()) {
        appendLine("引导 IP：${server.bootstrapIps.joinToString(", ")}")
    }
    appendLine("查询：${domain} ${recordType}  ·  第 ${round} 轮")
    appendLine("网络：${network.type}  ·  ${network.privateDnsLabel()}")
    appendLine("结果：${if (success) "成功 ${timings.totalMs.toMsLabel()}" else (error ?: "失败")}")
    appendLine(
        "时延：DNS ${timings.dnsLookupMs?.toMsLabel() ?: "—"}  ·  TCP ${timings.connectMs?.toMsLabel() ?: "—"}  ·  TLS ${timings.tlsMs?.toMsLabel() ?: "—"}  ·  首字节 ${timings.firstByteMs?.toMsLabel() ?: "—"}",
    )
    listOfNotNull(httpProtocol, tlsProtocol, tlsCipher, remoteAddress, httpStatus?.let { "HTTP $it" })
        .takeIf { it.isNotEmpty() }
        ?.let { appendLine("协议：${it.joinToString("  ·  ")}") }
    val message = message
    if (message != null) {
        appendLine("RCODE：${message.rcode}  ${message.flags.joinToString(" ")}")
        if (message.questions.isNotEmpty()) {
            appendLine("问题：${message.questions.joinToString("；")}")
        }
        if (message.answers.isEmpty()) {
            appendLine("应答：无 Answer 记录")
        } else {
            appendLine("应答：")
            message.answers.forEach { record ->
                appendLine("- ${record.type} ${record.data}  (${record.name}, TTL ${record.ttl})")
            }
        }
    }
    if (events.isNotEmpty()) {
        appendLine("过程：")
        events.forEach { event ->
            val detail = if (event.detail.isBlank()) "" else "  ${event.detail}"
            appendLine("- ${event.elapsedMs.toMsLabel()}  ${event.stage}$detail")
        }
    }
}
