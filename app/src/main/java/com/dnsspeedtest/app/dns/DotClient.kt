package com.dnsspeedtest.app.dns

import android.os.Build
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class DotClient {
    suspend fun query(
        server: DnsServer,
        domain: String,
        type: RecordType,
        timeoutMs: Int,
    ): ProbeResult = withContext(Dispatchers.IO) {
        val recorder = QueryRecorder()
        recorder.mark("开始", "DoT ${server.name} ${server.host}:${server.port}")
        val payload = DnsCodec.encodeQuery(domain, type)
        recorder.mark("编码查询", "wire ${payload.size} bytes, type=${type.label}")

        var dnsLookupMs: Long? = null
        var connectMs: Long? = null
        var tlsMs: Long? = null
        var firstByteMs: Long? = null
        var tlsProtocol: String? = null
        var tlsCipher: String? = null
        var remoteAddress: String? = null
        val timeout = timeoutMs.coerceAtLeast(1_000)

        try {
            val dnsStarted = recorder.elapsedMs()
            recorder.mark("解析引导地址", server.bootstrapIps.joinToString().ifEmpty { server.host })
            val address = resolveAddress(server)
            dnsLookupMs = recorder.elapsedMs() - dnsStarted
            remoteAddress = address.hostAddress
            recorder.mark("引导地址就绪", remoteAddress.orEmpty())
            ensureActive()

            val connectStarted = recorder.elapsedMs()
            recorder.mark("TCP 连接", "${address.hostAddress}:${server.port}")
            val plain = Socket()
            try {
                plain.connect(InetSocketAddress(address, server.port), timeout)
                plain.soTimeout = timeout
                connectMs = recorder.elapsedMs() - connectStarted
                recorder.mark("TCP 完成", "local=${plain.localPort}")
                ensureActive()

                val tlsStarted = recorder.elapsedMs()
                recorder.mark("TLS 握手", "SNI=${server.sni}")
                val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(
                    plain,
                    server.sni,
                    server.port,
                    true,
                ) as SSLSocket
                sslSocket.apply {
                    soTimeout = timeout
                    val params = sslParameters
                    params.serverNames = listOf(SNIHostName(server.sni))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        params.applicationProtocols = arrayOf("dot")
                    }
                    sslParameters = params
                    startHandshake()
                }
                try {
                    tlsMs = recorder.elapsedMs() - tlsStarted
                    tlsProtocol = sslSocket.session.protocol
                    tlsCipher = sslSocket.session.cipherSuite
                    recorder.mark("TLS 完成", listOfNotNull(tlsProtocol, tlsCipher).joinToString(" / "))
                    ensureActive()

                    val output = BufferedOutputStream(sslSocket.outputStream)
                    val input = BufferedInputStream(sslSocket.inputStream)
                    recorder.mark("发送 DNS 查询", "${payload.size} bytes")
                    output.write((payload.size ushr 8) and 0xFF)
                    output.write(payload.size and 0xFF)
                    output.write(payload)
                    output.flush()

                    firstByteMs = recorder.elapsedMs()
                    val lenHigh = input.read()
                    val lenLow = input.read()
                    if (lenHigh < 0 || lenLow < 0) error("连接已关闭")
                    val length = (lenHigh shl 8) or lenLow
                    require(length in 1..4096) { "异常响应长度 $length" }
                    recorder.mark("收到响应长度", "$length bytes")
                    val bytes = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(bytes, read, length - read)
                        if (n < 0) error("响应截断")
                        read += n
                    }
                    recorder.mark("读取响应体", "${bytes.size} bytes")
                    val message = DnsCodec.decodeMessage(bytes)
                    recorder.mark("解析完成", "${message.rcode}, answers=${message.answers.size}")
                    ProbeResult(
                        success = true,
                        error = null,
                        httpStatus = null,
                        tlsProtocol = tlsProtocol,
                        tlsCipher = tlsCipher,
                        httpProtocol = "DoT",
                        remoteAddress = remoteAddress,
                        timings = QueryTimings(
                            dnsLookupMs = dnsLookupMs,
                            connectMs = connectMs,
                            tlsMs = tlsMs,
                            firstByteMs = firstByteMs,
                            totalMs = recorder.elapsedMs(),
                        ),
                        events = recorder.snapshot(),
                        message = message,
                        startedAtMs = recorder.startedAtMs,
                    )
                } finally {
                    sslSocket.close()
                }
            } finally {
                plain.close()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            recorder.mark("失败", error.message ?: error.javaClass.simpleName)
            ProbeResult(
                success = false,
                error = error.message ?: error.javaClass.simpleName,
                httpStatus = null,
                tlsProtocol = tlsProtocol,
                tlsCipher = tlsCipher,
                httpProtocol = null,
                remoteAddress = remoteAddress,
                timings = QueryTimings(
                    dnsLookupMs = dnsLookupMs,
                    connectMs = connectMs,
                    tlsMs = tlsMs,
                    firstByteMs = firstByteMs,
                    totalMs = recorder.elapsedMs(),
                ),
                events = recorder.snapshot(),
                message = null,
                startedAtMs = recorder.startedAtMs,
            )
        }
    }

    private fun resolveAddress(server: DnsServer): InetAddress {
        val candidates = server.bootstrapIps.ifEmpty { listOf(server.host) }
        var last: Exception? = null
        for (candidate in candidates) {
            try {
                return InetAddress.getByName(candidate)
            } catch (error: Exception) {
                last = error
            }
        }
        throw last ?: IllegalStateException("无法解析 ${server.host}")
    }
}
