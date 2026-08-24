package com.dnsspeedtest.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.provider.Settings
import com.dnsspeedtest.app.dns.NetworkSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun snapshot(): NetworkSnapshot {
        val network = connectivity.activeNetwork
        val caps = network?.let { connectivity.getNetworkCapabilities(it) }
        val transports = buildList {
            if (caps == null) return@buildList
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("Wi-Fi")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("蜂窝")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("以太网")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("蓝牙")
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) add("USB")
        }
        val type = when {
            caps == null -> "无网络"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi + VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝 + VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝网络"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "其他"
        }
        val privateDnsMode = Settings.Global.getString(appContext.contentResolver, "private_dns_mode")
        val privateDnsSpecifier = Settings.Global.getString(appContext.contentResolver, "private_dns_specifier")
        return NetworkSnapshot(
            type = type,
            transports = transports,
            hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            isMetered = connectivity.isActiveNetworkMetered,
            downstreamKbps = caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 },
            upstreamKbps = caps?.linkUpstreamBandwidthKbps?.takeIf { it > 0 },
            privateDnsMode = privateDnsMode,
            privateDnsSpecifier = privateDnsSpecifier,
            capturedAtMs = System.currentTimeMillis(),
        )
    }

    fun observe(): Flow<NetworkSnapshot> = callbackFlow {
        trySend(snapshot())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(snapshot())
            }

            override fun onLost(network: Network) {
                trySend(snapshot())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(snapshot())
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose {
            runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()
}

fun NetworkSnapshot.privateDnsLabel(): String = when (privateDnsMode) {
    "off" -> "系统私有 DNS：关闭"
    "opportunistic" -> "系统私有 DNS：自动"
    "hostname" -> "系统私有 DNS：${privateDnsSpecifier ?: "指定主机"}"
    else -> "系统私有 DNS：未知"
}
