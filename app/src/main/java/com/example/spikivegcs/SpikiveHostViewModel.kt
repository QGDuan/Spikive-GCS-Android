package com.example.spikivegcs

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spikivegcs.network.NetworkCandidate
import com.example.spikivegcs.network.NetworkDiscoveryState
import com.example.spikivegcs.network.NetworkKind
import com.example.spikivegcs.network.NetworkMonitor
import com.example.spikivegcs.server.AssetWebServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface ServerState {
    data object Stopped : ServerState
    data object Starting : ServerState
    data class Ready(val url: String) : ServerState
    data class Failed(val message: String) : ServerState
}

internal enum class SearchPhase {
    IDLE,
    NOT_FOUND,
    FOUND,
}

internal data class NetworkOptionState(
    val phase: SearchPhase = SearchPhase.IDLE,
    val candidate: NetworkCandidate? = null,
) {
    val ipAddress: String?
        get() = candidate?.displayAddress?.takeIf(String::isNotBlank)
}

internal data class HostUiState(
    val usb: NetworkOptionState = NetworkOptionState(),
    val wifi: NetworkOptionState = NetworkOptionState(),
    val selected: NetworkCandidate? = null,
    val serverState: ServerState = ServerState.Stopped,
    val message: String? = null,
) {
    val showWebView: Boolean
        get() = selected != null && serverState is ServerState.Ready

    fun option(kind: NetworkKind): NetworkOptionState = when (kind) {
        NetworkKind.USB_ETHERNET -> usb
        NetworkKind.WIFI -> wifi
    }
}

internal class SpikiveHostViewModel(application: Application) : AndroidViewModel(application) {
    private val applicationAssets = application.assets
    private val networkMonitor = NetworkMonitor(application)
    private val mutableUiState = MutableStateFlow(HostUiState())
    private var serverGeneration = 0L
    private var serverStartJob: Job? = null
    private var webServer: AssetWebServer? = null

    val uiState: StateFlow<HostUiState> = mutableUiState.asStateFlow()

    init {
        networkMonitor.start()
        viewModelScope.launch {
            networkMonitor.state.collectLatest(::onNetworksChanged)
        }
    }

    fun onNetworkButton(kind: NetworkKind) {
        val option = mutableUiState.value.option(kind)
        if (option.phase == SearchPhase.FOUND && option.candidate != null) {
            enterWith(kind)
        } else {
            search(kind)
        }
    }

    fun leaveWebView() {
        val current = mutableUiState.value
        val selectedKind = current.selected?.kind
        serverGeneration += 1
        stopServer()
        networkMonitor.clearSelection()
        mutableUiState.value = current.copy(
            usb = if (selectedKind == NetworkKind.USB_ETHERNET) NetworkOptionState() else current.usb,
            wifi = if (selectedKind == NetworkKind.WIFI) NetworkOptionState() else current.wifi,
            selected = null,
            serverState = ServerState.Stopped,
            message = "已退出地面站",
        )
    }

    fun retryServer() {
        val selected = mutableUiState.value.selected ?: return
        serverGeneration += 1
        stopServer()
        mutableUiState.value = mutableUiState.value.copy(serverState = ServerState.Starting)
        startServer(selected, serverGeneration)
    }

    private fun search(kind: NetworkKind) {
        val candidate = networkMonitor.refresh(kind)?.takeIf { it.addresses.isNotEmpty() }
        val option = if (candidate == null) {
            NetworkOptionState(phase = SearchPhase.NOT_FOUND)
        } else {
            NetworkOptionState(phase = SearchPhase.FOUND, candidate = candidate)
        }
        mutableUiState.value = updateOption(
            state = mutableUiState.value,
            kind = kind,
            option = option,
            message = if (candidate == null) "未找到${kind.label()} IP，请检查连接后重试" else null,
        )
    }

    private fun enterWith(kind: NetworkKind) {
        val expected = mutableUiState.value.option(kind).candidate ?: return
        val current = networkMonitor.refresh(kind)
        if (current?.networkHandle != expected.networkHandle || current.addresses.isEmpty()) {
            search(kind)
            return
        }
        if (!networkMonitor.select(current)) {
            mutableUiState.value = mutableUiState.value.copy(
                message = networkMonitor.state.value.bindingError ?: "无法使用${kind.label()}网络",
            )
            return
        }
        val selected = networkMonitor.state.value.selected ?: return
        mutableUiState.value = mutableUiState.value.copy(
            selected = selected,
            serverState = ServerState.Starting,
            message = null,
        )
        serverGeneration += 1
        startServer(selected, serverGeneration)
    }

    private fun startServer(selected: NetworkCandidate, generation: Long) {
        val server = AssetWebServer(applicationAssets)
        webServer = server
        serverStartJob = viewModelScope.launch {
            val serverState = try {
                withContext(Dispatchers.IO) { ServerState.Ready(server.start()) }
            } catch (_: CancellationException) {
                server.close()
                return@launch
            } catch (error: Exception) {
                Log.e(TAG, "Unable to start embedded web server", error)
                ServerState.Failed(error.message ?: "无法启动本地网页服务")
            }
            val current = mutableUiState.value
            if (
                serverGeneration == generation &&
                current.selected?.networkHandle == selected.networkHandle &&
                webServer === server
            ) {
                mutableUiState.value = current.copy(serverState = serverState)
            } else {
                server.close()
            }
        }
    }

    private fun stopServer() {
        serverStartJob?.cancel()
        serverStartJob = null
        webServer?.close()
        webServer = null
    }

    private fun onNetworksChanged(networks: NetworkDiscoveryState) {
        val current = mutableUiState.value
        if (current.selected != null && networks.selected == null) {
            serverGeneration += 1
            stopServer()
            mutableUiState.value = current.copy(
                selected = null,
                serverState = ServerState.Stopped,
                message = "所选网络已断开，已关闭地面站连接",
                usb = refreshFoundOption(current.usb, networks.ethernet),
                wifi = refreshFoundOption(current.wifi, networks.wifi),
            )
            return
        }
        mutableUiState.value = current.copy(
            selected = networks.selected,
            usb = refreshFoundOption(current.usb, networks.ethernet),
            wifi = refreshFoundOption(current.wifi, networks.wifi),
            message = networks.bindingError ?: current.message,
        )
    }

    private fun refreshFoundOption(
        option: NetworkOptionState,
        available: NetworkCandidate?,
    ): NetworkOptionState {
        if (option.phase != SearchPhase.FOUND) {
            return option
        }
        return if (
            available != null &&
            available.networkHandle == option.candidate?.networkHandle &&
            available.addresses.isNotEmpty()
        ) {
            option.copy(candidate = available)
        } else {
            NetworkOptionState(phase = SearchPhase.NOT_FOUND)
        }
    }

    private fun updateOption(
        state: HostUiState,
        kind: NetworkKind,
        option: NetworkOptionState,
        message: String?,
    ): HostUiState = when (kind) {
        NetworkKind.USB_ETHERNET -> state.copy(usb = option, message = message)
        NetworkKind.WIFI -> state.copy(wifi = option, message = message)
    }

    override fun onCleared() {
        serverGeneration += 1
        stopServer()
        networkMonitor.close()
        super.onCleared()
    }

    private fun NetworkKind.label(): String = when (this) {
        NetworkKind.USB_ETHERNET -> "USB 网口"
        NetworkKind.WIFI -> "Wi-Fi"
    }

    private companion object {
        const val TAG = "SpikiveHostViewModel"
    }
}
