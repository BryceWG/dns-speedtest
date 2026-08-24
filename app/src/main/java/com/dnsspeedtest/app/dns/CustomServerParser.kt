package com.dnsspeedtest.app.dns

import java.net.URI
import java.util.UUID

object CustomServerParser {
    fun parse(
        name: String,
        protocol: DnsProtocol,
        address: String,
        bootstrapText: String = "",
        sniText: String = "",
        existingId: String? = null,
    ): Result<DnsServer> = runCatching {
        val trimmedAddress = address.trim()
        require(trimmedAddress.isNotEmpty()) { "请输入服务器地址" }
        val bootstrap = parseBootstrap(bootstrapText)
        val id = existingId?.takeIf { it.startsWith("custom-") } ?: "custom-${UUID.randomUUID()}"
        when (protocol) {
            DnsProtocol.DOH -> parseDoh(name, trimmedAddress, bootstrap, sniText, id)
            DnsProtocol.DOT -> parseDot(name, trimmedAddress, bootstrap, sniText, id)
        }
    }

    fun formatAddress(server: DnsServer): String = when (server.protocol) {
        DnsProtocol.DOH -> {
            val port = if (server.port == 443) "" else ":${server.port}"
            "https://${server.host}$port${server.path}"
        }
        DnsProtocol.DOT -> if (server.port == 853) server.host else "${server.host}:${server.port}"
    }

    fun formatBootstrap(server: DnsServer): String = server.bootstrapIps.joinToString(", ")

    fun parseBootstrap(text: String): List<String> =
        text.split(',', ';', ' ', '\n', '\t', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun splitHostPort(value: String, defaultPort: Int): Pair<String, Int> {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "请输入主机地址" }
        if (trimmed.startsWith("[")) {
            val end = trimmed.indexOf(']')
            require(end > 0) { "IPv6 地址格式无效" }
            val host = trimmed.substring(1, end)
            require(host.isNotEmpty()) { "IPv6 地址格式无效" }
            val rest = trimmed.substring(end + 1)
            val port = if (rest.startsWith(":")) {
                rest.drop(1).toIntOrNull() ?: error("端口无效")
            } else {
                defaultPort
            }
            require(port in 1..65535) { "端口无效" }
            return host to port
        }
        val firstColon = trimmed.indexOf(':')
        val lastColon = trimmed.lastIndexOf(':')
        if (lastColon > 0 && firstColon == lastColon) {
            val host = trimmed.substring(0, lastColon)
            val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: error("端口无效")
            require(host.isNotEmpty()) { "主机名无效" }
            require(port in 1..65535) { "端口无效" }
            return host to port
        }
        return trimmed to defaultPort
    }

    private fun parseDoh(
        name: String,
        address: String,
        bootstrap: List<String>,
        sniText: String,
        id: String,
    ): DnsServer {
        val uri = if (address.contains("://")) {
            require(address.startsWith("https://", ignoreCase = true)) { "DoH 仅支持 HTTPS" }
            URI(address)
        } else {
            URI("https://$address")
        }
        val host = uri.host?.trim().orEmpty()
        require(host.isNotEmpty()) { "无法解析 DoH 主机名" }
        val port = if (uri.port == -1) 443 else uri.port
        require(port in 1..65535) { "端口无效" }
        val rawPath = uri.rawPath.orEmpty()
        val path = when {
            rawPath.isBlank() || rawPath == "/" -> "/dns-query"
            rawPath.startsWith("/") -> rawPath
            else -> "/$rawPath"
        }
        val sni = sniText.trim().ifEmpty { host }
        return DnsServer(
            id = id,
            name = name.trim().ifEmpty { host },
            protocol = DnsProtocol.DOH,
            host = host,
            port = port,
            path = path,
            bootstrapIps = bootstrap,
            sni = sni,
        )
    }

    private fun parseDot(
        name: String,
        address: String,
        bootstrap: List<String>,
        sniText: String,
        id: String,
    ): DnsServer {
        require(!address.contains("://")) { "DoT 请填写主机或 IP，不要使用 URL" }
        val (host, port) = splitHostPort(address, defaultPort = 853)
        val sni = sniText.trim().ifEmpty { host }
        return DnsServer(
            id = id,
            name = name.trim().ifEmpty { host },
            protocol = DnsProtocol.DOT,
            host = host,
            port = port,
            path = "/",
            bootstrapIps = bootstrap,
            sni = sni,
        )
    }
}

fun DnsServer.isCustom(): Boolean = id.startsWith("custom-")
