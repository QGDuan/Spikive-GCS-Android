package com.example.spikivegcs.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class NetworkKind(val transportType: Int) {
    USB_ETHERNET(NetworkCapabilities.TRANSPORT_ETHERNET),
    WIFI(NetworkCapabilities.TRANSPORT_WIFI),
}

internal data class NetworkCandidate(
    val kind: NetworkKind,
    val network: Network,
    val interfaceName: String?,
    val addresses: List<String>,
    val routes: List<String>,
) {
    val networkHandle: Long
        get() = network.networkHandle

    val displayAddress: String
        get() = (addresses.firstOrNull { it.contains('.') } ?: addresses.firstOrNull().orEmpty())
            .substringBefore('/')
}

internal data class NetworkDiscoveryState(
    val ethernet: NetworkCandidate? = null,
    val wifi: NetworkCandidate? = null,
    val selected: NetworkCandidate? = null,
    val bindingError: String? = null,
) {
    fun candidate(kind: NetworkKind): NetworkCandidate? = when (kind) {
        NetworkKind.USB_ETHERNET -> ethernet
        NetworkKind.WIFI -> wifi
    }
}

internal class NetworkMonitor(context: Context) : Closeable {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val candidates = ConcurrentHashMap<Network, NetworkCandidate>()
    private val mutableState = MutableStateFlow(NetworkDiscoveryState())
    private var selectedNetwork: Network? = null
    private var started = false

    val state: StateFlow<NetworkDiscoveryState> = mutableState.asStateFlow()

    private val callbacks = NetworkKind.entries.associateWith(::createCallback)

    @Synchronized
    fun start() {
        if (started) {
            return
        }
        started = true
        try {
            callbacks.forEach { (kind, callback) ->
                val request = NetworkRequest.Builder()
                    .addTransportType(kind.transportType)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build()
                connectivityManager.registerNetworkCallback(request, callback)
            }
        } catch (error: RuntimeException) {
            started = false
            callbacks.values.forEach { callback ->
                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
            }
            Log.e(TAG, "Unable to register network callback", error)
            mutableState.value = NetworkDiscoveryState(bindingError = "无法监听 Android 网络")
        }
    }

    @Synchronized
    fun refresh(kind: NetworkKind): NetworkCandidate? {
        if (!started) {
            start()
        }
        val availableNetworks = connectivityManager.allNetworks.toSet()
        candidates.keys.removeAll { it !in availableNetworks }
        availableNetworks.forEach { network -> updateCandidate(network) }
        publishState()
        return mutableState.value.candidate(kind)
    }

    @Synchronized
    fun select(candidate: NetworkCandidate): Boolean {
        val available = candidates[candidate.network]
            ?.takeIf { it.kind == candidate.kind && it.addresses.isNotEmpty() }
            ?: return false
        return try {
            if (!connectivityManager.bindProcessToNetwork(available.network)) {
                mutableState.value = currentState(
                    selected = null,
                    bindingError = "无法绑定到${available.kind.label()}网络",
                )
                selectedNetwork = null
                return false
            }
            selectedNetwork = available.network
            mutableState.value = currentState(selected = available, bindingError = null)
            Log.i(TAG, "Bound process to ${available.kind.name}: ${available.interfaceName}")
            true
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to bind process to ${available.kind.name}", error)
            selectedNetwork = null
            mutableState.value = currentState(
                selected = null,
                bindingError = "无法绑定到${available.kind.label()}网络",
            )
            false
        }
    }

    @Synchronized
    fun clearSelection() {
        runCatching { connectivityManager.bindProcessToNetwork(null) }
            .onFailure { Log.w(TAG, "Unable to clear process network binding", it) }
        selectedNetwork = null
        mutableState.value = currentState(selected = null, bindingError = null)
    }

    @Synchronized
    private fun updateCandidate(
        network: Network,
        capabilities: NetworkCapabilities? = connectivityManager.getNetworkCapabilities(network),
        linkProperties: LinkProperties? = connectivityManager.getLinkProperties(network),
    ) {
        if (!started) {
            return
        }
        if (capabilities == null || linkProperties == null) {
            removeCandidate(network)
            return
        }
        val kind = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.USB_ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            else -> {
                candidates.remove(network)
                publishState()
                return
            }
        }
        candidates[network] = NetworkCandidate(
            kind = kind,
            network = network,
            interfaceName = linkProperties.interfaceName,
            addresses = linkProperties.linkAddresses
                .filter { it.address.isUsableForGroundStation() }
                .map { it.toString() },
            routes = linkProperties.routes.map { it.toString() },
        )
        publishState()
    }

    private fun createCallback(kind: NetworkKind) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateCandidate(network)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            updateCandidate(network, capabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            updateCandidate(network, linkProperties = linkProperties)
        }

        override fun onLost(network: Network) {
            removeCandidate(network, expectedKind = kind)
        }
    }

    @Synchronized
    private fun removeCandidate(network: Network, expectedKind: NetworkKind? = null) {
        val candidate = candidates[network]
        if (expectedKind != null && candidate?.kind != expectedKind) {
            return
        }
        candidates.remove(network)
        if (selectedNetwork == network) {
            clearSelection()
        } else {
            publishState()
        }
    }

    @Synchronized
    private fun publishState() {
        if (!started) {
            return
        }
        val selected = selectedNetwork
            ?.let(candidates::get)
            ?.takeIf { it.addresses.isNotEmpty() }
        if (selectedNetwork != null && selected == null) {
            clearSelection()
            return
        }
        mutableState.value = currentState(selected = selected, bindingError = mutableState.value.bindingError)
    }

    private fun currentState(
        selected: NetworkCandidate?,
        bindingError: String?,
    ): NetworkDiscoveryState = NetworkDiscoveryState(
        ethernet = bestCandidate(NetworkKind.USB_ETHERNET),
        wifi = bestCandidate(NetworkKind.WIFI),
        selected = selected,
        bindingError = bindingError,
    )

    private fun bestCandidate(kind: NetworkKind): NetworkCandidate? = candidates.values
        .asSequence()
        .filter { it.kind == kind }
        .sortedWith(compareBy<NetworkCandidate> { it.addresses.isEmpty() }.thenBy { it.interfaceName })
        .firstOrNull()

    @Synchronized
    override fun close() {
        if (!started) {
            return
        }
        started = false
        callbacks.values.forEach { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                .onFailure { Log.w(TAG, "Unable to unregister network callback", it) }
        }
        runCatching { connectivityManager.bindProcessToNetwork(null) }
            .onFailure { Log.w(TAG, "Unable to clear process network binding", it) }
        selectedNetwork = null
        candidates.clear()
        mutableState.value = NetworkDiscoveryState()
    }

    private fun NetworkKind.label(): String = when (this) {
        NetworkKind.USB_ETHERNET -> "USB 网口"
        NetworkKind.WIFI -> "Wi-Fi"
    }

    private companion object {
        const val TAG = "NetworkMonitor"
    }
}
