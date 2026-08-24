package com.dnsspeedtest.app.dns

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

class DohClient(
    private val sharedClient: OkHttpClient? = null,
) {
    suspend fun query(
        server: DnsServer,
        domain: String,
        type: RecordType,
        timeoutMs: Int,
    ): ProbeResult = withContext(Dispatchers.IO) {
        val recorder = QueryRecorder()
        recorder.mark("开始", "DoH ${server.name} ${server.host}${server.path}")
        val payload = DnsCodec.encodeQuery(domain, type)
        recorder.mark("编码查询", "wire ${payload.size} bytes, type=${type.label}")

        var dnsLookupMs: Long? = null
        var connectMs: Long? = null
        var tlsMs: Long? = null
        var firstByteMs: Long? = null
        var tlsProtocol: String? = null
        var tlsCipher: String? = null
        var remoteAddress: String? = null
        var dnsStarted = 0L
        var connectStarted = 0L
        var tlsStarted = 0L

        val listener = object : EventListener() {
            override fun dnsStart(call: Call, domainName: String) {
                dnsStarted = recorder.elapsedMs()
                recorder.mark("解析引导地址", domainName)
            }

            override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
                dnsLookupMs = recorder.elapsedMs() - dnsStarted
                recorder.mark(
                    "引导地址就绪",
                    inetAddressList.joinToString { it.hostAddress ?: it.hostName },
                )
            }

            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
                connectStarted = recorder.elapsedMs()
                recorder.mark("TCP 连接", inetSocketAddress.toString())
            }

            override fun connectEnd(
                call: Call,
                inetSocketAddress: InetSocketAddress,
                proxy: Proxy,
                protocol: Protocol?,
            ) {
                connectMs = recorder.elapsedMs() - connectStarted
                remoteAddress = inetSocketAddress.address?.hostAddress
                recorder.mark("TCP 完成", protocol?.toString().orEmpty())
            }

            override fun secureConnectStart(call: Call) {
                tlsStarted = recorder.elapsedMs()
                recorder.mark("TLS 握手", "SNI=${server.sni}")
            }

            override fun secureConnectEnd(call: Call, handshake: Handshake?) {
                tlsMs = recorder.elapsedMs() - tlsStarted
                tlsProtocol = handshake?.tlsVersion?.javaName
                tlsCipher = handshake?.cipherSuite?.javaName
                recorder.mark(
                    "TLS 完成",
                    listOfNotNull(tlsProtocol, tlsCipher).joinToString(" / "),
                )
            }

            override fun requestHeadersStart(call: Call) {
                recorder.mark("发送 HTTP 请求", "POST application/dns-message")
            }

            override fun responseHeadersStart(call: Call) {
                firstByteMs = recorder.elapsedMs()
                recorder.mark("收到响应头", "")
            }
        }

        val client = (sharedClient ?: createClient(timeoutMs, server, listener)).newBuilder()
            .eventListener(listener)
            .build()

        val url = "https://${server.host}:${server.port}${server.path}"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/dns-message")
            .header("Content-Type", "application/dns-message")
            .post(payload.toRequestBody(DNS_MESSAGE))
            .build()

        recorder.mark("发起请求", url)

        try {
            val response = client.newCall(request).await()
            response.use { resp ->
                val bytes = resp.body.bytes()
                recorder.mark("读取响应体", "${bytes.size} bytes, HTTP ${resp.code}, ${resp.protocol}")
                val message = if (bytes.isNotEmpty()) {
                    runCatching { DnsCodec.decodeMessage(bytes) }
                        .onFailure { recorder.mark("解析失败", it.message ?: "decode") }
                        .getOrNull()
                } else {
                    null
                }
                if (message != null) {
                    recorder.mark("解析完成", "${message.rcode}, answers=${message.answers.size}")
                }
                ProbeResult(
                    success = resp.isSuccessful && message != null,
                    error = if (resp.isSuccessful) {
                        if (message == null) "空响应或无法解析" else null
                    } else {
                        "HTTP ${resp.code} ${resp.message}"
                    },
                    httpStatus = resp.code,
                    tlsProtocol = tlsProtocol,
                    tlsCipher = tlsCipher,
                    httpProtocol = resp.protocol.toString(),
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

    private fun createClient(
        timeoutMs: Int,
        server: DnsServer,
        listener: EventListener,
    ): OkHttpClient {
        val timeout = timeoutMs.toLong().coerceAtLeast(1_000L)
        return OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(timeout, TimeUnit.MILLISECONDS)
            .callTimeout(timeout, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
            .dns(BootstrapDns(server))
            .eventListener(listener)
            .build()
    }

    private class BootstrapDns(private val server: DnsServer) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val ips = server.bootstrapIps.ifEmpty { listOf(server.host) }
            return ips.map { InetAddress.getByName(it) }
        }
    }

    companion object {
        private val DNS_MESSAGE = "application/dns-message".toMediaType()
    }
}

data class ProbeResult(
    val success: Boolean,
    val error: String?,
    val httpStatus: Int?,
    val tlsProtocol: String?,
    val tlsCipher: String?,
    val httpProtocol: String?,
    val remoteAddress: String?,
    val timings: QueryTimings,
    val events: List<QueryEvent>,
    val message: DnsMessageView?,
    val startedAtMs: Long,
)

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        },
    )
    continuation.invokeOnCancellation { cancel() }
}
