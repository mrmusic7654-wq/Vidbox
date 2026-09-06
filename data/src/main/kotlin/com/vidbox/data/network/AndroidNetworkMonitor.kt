package com.vidbox.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vidbox.domain.model.NetworkStatus
import com.vidbox.domain.repository.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNetworkMonitor @Inject constructor(@ApplicationContext context: Context) : NetworkMonitor {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var blocked = false
    private val mutableStatus = MutableStateFlow(read())
    override val status = mutableStatus.asStateFlow()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { blocked = false; refresh() }
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            this@AndroidNetworkMonitor.blocked = blocked
            refresh()
        }
    }
    init { manager.registerDefaultNetworkCallback(callback) }
    private fun refresh() { mutableStatus.value = read() }
    private fun read(): NetworkStatus {
        val network = manager.activeNetwork ?: return NetworkStatus()
        val caps = manager.getNetworkCapabilities(network) ?: return NetworkStatus()
        return NetworkStatus(
            connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
            cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            blocked = blocked,
        )
    }
}
