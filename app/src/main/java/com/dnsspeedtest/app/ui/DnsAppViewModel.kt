package com.dnsspeedtest.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dnsspeedtest.app.data.HistoryRepository
import com.dnsspeedtest.app.data.SettingsRepository
import com.dnsspeedtest.app.dns.DnsProtocol
import com.dnsspeedtest.app.dns.DnsQueryEngine
import com.dnsspeedtest.app.dns.ResultSortKey
import com.dnsspeedtest.app.dns.DnsQueryResult
import com.dnsspeedtest.app.dns.DnsServer
import com.dnsspeedtest.app.dns.DnsServerCatalog
import com.dnsspeedtest.app.dns.isCustom
import com.dnsspeedtest.app.dns.HistorySession
import com.dnsspeedtest.app.dns.NetworkSnapshot
import com.dnsspeedtest.app.dns.RecordType
import com.dnsspeedtest.app.dns.UserSettings
import com.dnsspeedtest.app.network.NetworkMonitor
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DnsUiState(
    val domain: String = "www.example.com",
    val recordType: String = RecordType.A.name,
    val protocolFilter: String = "ALL",
    val rounds: Int = 1,
    val timeoutMs: Int = 8_000,
    val reuseConnections: Boolean = false,
    val selectedServerIds: Set<String> = DnsServerCatalog.defaultSelectedIds,
    val customServers: List<DnsServer> = emptyList(),
    val hiddenBuiltinServerIds: Set<String> = emptySet(),
    val colorSchemeMode: String = "System",
    val network: NetworkSnapshot = emptyNetwork(),
    val running: Boolean = false,
    val progressText: String = "",
    val results: List<DnsQueryResult> = emptyList(),
    val history: List<HistorySession> = emptyList(),
    val selectedTab: Int = 0,
    val selectedSession: HistorySession? = null,
    val selectedResult: DnsQueryResult? = null,
    val showServerManager: Boolean = false,
    val editingServerId: String? = null,
    val addingServer: Boolean = false,
    val resultSortKey: ResultSortKey = ResultSortKey.FASTEST,
    val resultSortAscending: Boolean = true,
    val recentDomains: List<String> = emptyList(),
    val message: String? = null,
)

fun emptyNetwork(): NetworkSnapshot = NetworkSnapshot(
    type = "未知",
    transports = emptyList(),
    hasInternet = false,
    isValidated = false,
    isMetered = false,
    downstreamKbps = null,
    upstreamKbps = null,
    privateDnsMode = null,
    privateDnsSpecifier = null,
    capturedAtMs = 0L,
)

class DnsAppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val historyRepository = HistoryRepository(application)
    private val networkMonitor = NetworkMonitor(application)
    private val engine = DnsQueryEngine()
    private val _uiState = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = _uiState.asStateFlow()
    private var queryJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        domain = settings.lastDomain,
                        recordType = settings.recordType,
                        protocolFilter = settings.protocolFilter,
                        rounds = settings.rounds,
                        timeoutMs = settings.timeoutMs,
                        reuseConnections = settings.reuseConnections,
                        selectedServerIds = settings.selectedServerIds,
                        customServers = settings.customServers,
                        hiddenBuiltinServerIds = settings.hiddenBuiltinServerIds,
                        colorSchemeMode = settings.colorSchemeMode,
                        resultSortKey = ResultSortKey.entries
                            .firstOrNull { it.name == settings.resultSortKey }
                            ?: ResultSortKey.FASTEST,
                        resultSortAscending = settings.resultSortAscending,
                        recentDomains = settings.recentDomains,
                    )
                }
            }
        }
        viewModelScope.launch {
            historyRepository.sessions.collect { sessions ->
                _uiState.update { it.copy(history = sessions) }
            }
        }
        viewModelScope.launch {
            networkMonitor.observe().collect { snapshot ->
                _uiState.update { it.copy(network = snapshot) }
            }
        }
    }

    fun allServers(): List<DnsServer> = DnsServerCatalog.all + _uiState.value.customServers

    fun visibleServers(): List<DnsServer> {
        val state = _uiState.value
        return allServers().filter { server ->
            val shownOnTestPage = server.isCustom() || server.id !in state.hiddenBuiltinServerIds
            val protocolMatches = when (state.protocolFilter) {
                "DOH" -> server.protocol == DnsProtocol.DOH
                "DOT" -> server.protocol == DnsProtocol.DOT
                else -> true
            }
            shownOnTestPage && protocolMatches
        }
    }

    fun editingServer(): DnsServer? = serverById(_uiState.value.editingServerId)

    fun serverById(id: String?): DnsServer? = id?.let { serverId ->
        allServers().firstOrNull { it.id == serverId }
    }

    fun selectedServers(): List<DnsServer> {
        val ids = _uiState.value.selectedServerIds
        return visibleServers().filter { it.id in ids }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index, selectedSession = null, selectedResult = null) }
    }

    fun setDomain(value: String) {
        _uiState.update { it.copy(domain = value) }
    }

    fun setRecordType(type: RecordType) {
        _uiState.update { it.copy(recordType = type.name) }
        persist { it.copy(recordType = type.name) }
    }

    fun setProtocolFilter(filter: String) {
        _uiState.update { it.copy(protocolFilter = filter) }
        persist { it.copy(protocolFilter = filter) }
    }

    fun setRounds(rounds: Int) {
        _uiState.update { it.copy(rounds = rounds) }
        persist { it.copy(rounds = rounds) }
    }

    fun setTimeout(timeoutMs: Int) {
        _uiState.update { it.copy(timeoutMs = timeoutMs) }
        persist { it.copy(timeoutMs = timeoutMs) }
    }

    fun setReuseConnections(value: Boolean) {
        _uiState.update { it.copy(reuseConnections = value) }
        persist { it.copy(reuseConnections = value) }
    }

    fun setResultSortKey(key: ResultSortKey) {
        _uiState.update { it.copy(resultSortKey = key) }
        persist { it.copy(resultSortKey = key.name) }
    }

    fun setResultSortAscending(ascending: Boolean) {
        _uiState.update { it.copy(resultSortAscending = ascending) }
        persist { it.copy(resultSortAscending = ascending) }
    }

    fun setColorSchemeMode(mode: String) {
        _uiState.update { it.copy(colorSchemeMode = mode) }
        persist { it.copy(colorSchemeMode = mode) }
    }

    fun toggleServer(id: String) {
        val next = _uiState.value.selectedServerIds.toMutableSet()
        if (!next.add(id)) next.remove(id)
        _uiState.update { it.copy(selectedServerIds = next) }
        persist { it.copy(selectedServerIds = next) }
    }

    fun restoreDefaultServers() {
        val customIds = _uiState.value.customServers.map { it.id }.toSet()
        val ids = DnsServerCatalog.defaultSelectedIds + customIds
        _uiState.update {
            it.copy(selectedServerIds = ids, hiddenBuiltinServerIds = emptySet())
        }
        persist {
            it.copy(selectedServerIds = ids, hiddenBuiltinServerIds = emptySet())
        }
    }

    fun addCustomServer(server: DnsServer) {
        val next = _uiState.value.customServers + server
        val selected = _uiState.value.selectedServerIds + server.id
        _uiState.update {
            it.copy(
                customServers = next,
                selectedServerIds = selected,
                addingServer = false,
                editingServerId = null,
            )
        }
        persist { it.copy(customServers = next, selectedServerIds = selected) }
    }

    fun updateCustomServer(server: DnsServer) {
        val next = _uiState.value.customServers.map { current ->
            if (current.id == server.id) server else current
        }
        _uiState.update { it.copy(customServers = next, editingServerId = null) }
        persist { it.copy(customServers = next) }
    }

    fun removeCustomServer(id: String) {
        val next = _uiState.value.customServers.filterNot { it.id == id }
        val selected = _uiState.value.selectedServerIds - id
        _uiState.update {
            it.copy(
                customServers = next,
                selectedServerIds = selected,
                editingServerId = null,
                addingServer = false,
            )
        }
        persist { it.copy(customServers = next, selectedServerIds = selected) }
    }

    fun setBuiltinServerVisible(id: String, visible: Boolean) {
        val hidden = _uiState.value.hiddenBuiltinServerIds.toMutableSet()
        val selected = _uiState.value.selectedServerIds.toMutableSet()
        if (visible) {
            hidden.remove(id)
        } else {
            hidden.add(id)
            selected.remove(id)
        }
        _uiState.update { it.copy(hiddenBuiltinServerIds = hidden, selectedServerIds = selected) }
        persist { it.copy(hiddenBuiltinServerIds = hidden, selectedServerIds = selected) }
    }

    fun openServerManager() {
        _uiState.update {
            it.copy(
                showServerManager = true,
                addingServer = false,
                editingServerId = null,
                selectedTab = 2,
            )
        }
    }

    fun closeServerManager() {
        _uiState.update {
            it.copy(showServerManager = false, addingServer = false, editingServerId = null)
        }
    }

    fun openAddServer() {
        _uiState.update {
            it.copy(showServerManager = true, addingServer = true, editingServerId = null)
        }
    }

    fun openEditServer(id: String) {
        _uiState.update {
            it.copy(showServerManager = true, addingServer = false, editingServerId = id)
        }
    }

    fun closeServerEditor() {
        _uiState.update { it.copy(addingServer = false, editingServerId = null) }
    }

    fun startTest() {
        val domain = _uiState.value.domain.trim().trimEnd('.')
        if (domain.isBlank() || domain.contains(' ')) {
            _uiState.update { it.copy(message = "请输入有效域名") }
            return
        }
        val servers = selectedServers()
        if (servers.isEmpty()) {
            _uiState.update { it.copy(message = "请至少选择一个 DNS 服务器") }
            return
        }
        val type = RecordType.entries.find { it.name == _uiState.value.recordType } ?: RecordType.A
        val recentDomains = (
            listOf(domain) +
                _uiState.value.recentDomains.filter { !it.equals(domain, ignoreCase = true) }
            ).take(MAX_RECENT_DOMAINS)
        _uiState.update { it.copy(recentDomains = recentDomains) }
        persist {
            it.copy(
                lastDomain = domain,
                recentDomains = recentDomains,
            )
        }
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    running = true,
                    results = emptyList(),
                    progressText = "准备查询…",
                    message = null,
                    selectedResult = null,
                    selectedSession = null,
                )
            }
            val collected = mutableListOf<DnsQueryResult>()
            val network = _uiState.value.network
            val timeoutMs = _uiState.value.timeoutMs
            val rounds = _uiState.value.rounds.coerceIn(1, 5)
            try {
                repeat(rounds) { index ->
                    if (!isActive) return@repeat
                    val round = index + 1
                    _uiState.update { it.copy(progressText = "第 $round / $rounds 轮，并行查询 ${servers.size} 台服务器") }
                    val roundResults = engine.queryAll(
                        servers = servers,
                        domain = domain,
                        type = type,
                        timeoutMs = timeoutMs,
                        round = round,
                        network = network,
                    )
                    collected += roundResults
                    _uiState.update { it.copy(results = collected.toList()) }
                }
                val session = HistorySession(
                    id = UUID.randomUUID().toString(),
                    startedAtMs = System.currentTimeMillis(),
                    domain = domain,
                    recordType = type.label,
                    networkLabel = network.type,
                    results = collected.toList(),
                )
                historyRepository.add(session)
                _uiState.update { it.copy(progressText = "完成 ${collected.size} 次查询") }
            } catch (cancelled: CancellationException) {
                _uiState.update { it.copy(progressText = "已取消") }
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(message = error.message ?: "查询失败") }
            } finally {
                _uiState.update { it.copy(running = false) }
            }
        }
    }

    fun cancelTest() {
        queryJob?.cancel()
        _uiState.update { it.copy(running = false, progressText = "已取消") }
    }

    fun openResult(result: DnsQueryResult) {
        _uiState.update { it.copy(selectedResult = result) }
    }

    fun openSession(session: HistorySession) {
        _uiState.update { it.copy(selectedSession = session, selectedResult = null, selectedTab = 1) }
    }

    fun closeResult() {
        _uiState.update { it.copy(selectedResult = null) }
    }

    fun closeSession() {
        _uiState.update { it.copy(selectedSession = null, selectedResult = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun showMessage(text: String) {
        _uiState.update { it.copy(message = text) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clear() }
    }

    private fun persist(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    companion object {
        private const val MAX_RECENT_DOMAINS = 20
    }
}
