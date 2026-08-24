package com.dnsspeedtest.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dnsspeedtest.app.dns.DnsQueryResult
import com.dnsspeedtest.app.dns.HistorySession
import com.dnsspeedtest.app.dns.RecordType
import com.dnsspeedtest.app.dns.answerGroups
import com.dnsspeedtest.app.dns.answerSummary
import com.dnsspeedtest.app.dns.fastestSuccessful
import com.dnsspeedtest.app.network.privateDnsLabel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.WorldClock
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun DnsApp(viewModel: DnsAppViewModel = viewModel()) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val mode = when (ui.colorSchemeMode) {
        "Light" -> ColorSchemeMode.Light
        "Dark" -> ColorSchemeMode.Dark
        "Monet" -> ColorSchemeMode.MonetSystem
        else -> ColorSchemeMode.System
    }
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(controller = controller) {
        DnsAppContent(ui = ui, viewModel = viewModel)
    }
}

@Composable
private fun DnsAppContent(
    ui: DnsUiState,
    viewModel: DnsAppViewModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val inServerEditor = ui.addingServer || ui.editingServerId != null
    val inServerFlow = ui.showServerManager || inServerEditor
    val showingDetail = ui.selectedResult != null || ui.selectedSession != null || inServerFlow
    LaunchedEffect(ui.message) {
        val message = ui.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }
    BackHandler(enabled = showingDetail) {
        when {
            inServerEditor -> viewModel.closeServerEditor()
            ui.showServerManager -> viewModel.closeServerManager()
            ui.selectedResult != null -> viewModel.closeResult()
            else -> viewModel.closeSession()
        }
    }
    val title = when {
        ui.addingServer -> "添加服务器"
        ui.editingServerId != null -> "编辑服务器"
        ui.showServerManager -> "服务器管理"
        ui.selectedResult != null -> "查询详情"
        ui.selectedSession != null -> "会话详情"
        ui.selectedTab == 1 -> "历史"
        ui.selectedTab == 2 -> "设置"
        else -> "DNS 测速"
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    if (showingDetail) {
                        IconButton(onClick = {
                            when {
                                inServerEditor -> viewModel.closeServerEditor()
                                ui.showServerManager -> viewModel.closeServerManager()
                                ui.selectedResult != null -> viewModel.closeResult()
                                else -> viewModel.closeSession()
                            }
                        }) {
                            Icon(MiuixIcons.Back, contentDescription = "返回")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!showingDetail) {
                NavigationBar {
                    NavigationBarItem(
                        selected = ui.selectedTab == 0,
                        onClick = { viewModel.setTab(0) },
                        icon = MiuixIcons.WorldClock,
                        label = "测试",
                    )
                    NavigationBarItem(
                        selected = ui.selectedTab == 1,
                        onClick = { viewModel.setTab(1) },
                        icon = MiuixIcons.Recent,
                        label = "历史",
                    )
                    NavigationBarItem(
                        selected = ui.selectedTab == 2,
                        onClick = { viewModel.setTab(2) },
                        icon = MiuixIcons.Settings,
                        label = "设置",
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            inServerEditor -> ServerEditorScreen(
                server = viewModel.editingServer(),
                adding = ui.addingServer,
                visibleOnTest = ui.editingServerId?.let { it !in ui.hiddenBuiltinServerIds } ?: true,
                padding = padding,
                onSaveCustom = { server ->
                    if (ui.addingServer) viewModel.addCustomServer(server) else viewModel.updateCustomServer(server)
                },
                onDeleteCustom = viewModel::removeCustomServer,
                onVisibilityChange = viewModel::setBuiltinServerVisible,
            )
            ui.showServerManager -> ServerManagerScreen(
                ui = ui,
                padding = padding,
                onOpenAdd = viewModel::openAddServer,
                onOpenEdit = viewModel::openEditServer,
            )
            ui.selectedResult != null -> ResultScreen(
                result = ui.selectedResult,
                padding = padding,
                onCopied = { viewModel.showMessage("已复制查询结果") },
            )
            ui.selectedSession != null -> SessionScreen(
                session = ui.selectedSession,
                padding = padding,
                onOpenResult = viewModel::openResult,
            )
            ui.selectedTab == 1 -> HistoryScreen(ui = ui, padding = padding, onOpenSession = viewModel::openSession)
            ui.selectedTab == 2 -> SettingsScreen(ui = ui, padding = padding, viewModel = viewModel)
            else -> TestScreen(ui = ui, padding = padding, viewModel = viewModel)
        }
    }
}

@Composable
private fun TestScreen(
    ui: DnsUiState,
    padding: PaddingValues,
    viewModel: DnsAppViewModel,
) {
    val recordTypes = RecordType.entries
    val recordIndex = recordTypes.indexOfFirst { it.name == ui.recordType }.coerceAtLeast(0)
    val protocolIndex = when (ui.protocolFilter) {
        "DOH" -> 1
        "DOT" -> 2
        else -> 0
    }
    val roundOptions = listOf(1, 3, 5)
    val timeoutOptions = listOf(3_000, 5_000, 8_000, 12_000, 20_000)
    val visibleServers = viewModel.visibleServers()
    val fastest = fastestSuccessful(ui.results)
    val groups = answerGroups(ui.results)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SmallTitle(text = "当前网络")
            Card(
                modifier = Modifier.sectionCard(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(ui.network.type, style = MiuixTheme.textStyles.title4)
                Text(
                    text = ui.network.transports.joinToString(" / ").ifEmpty { "无传输类型" },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = ui.network.privateDnsLabel(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = "计费网络：${if (ui.network.isMetered) "是" else "否"}  · 已验证：${if (ui.network.isValidated) "是" else "否"}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
        item {
            SmallTitle(text = "查询")
            Card(modifier = Modifier.sectionCard()) {
                TextField(
                    value = ui.domain,
                    onValueChange = viewModel::setDomain,
                    label = "查询域名",
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    enabled = !ui.running,
                )
                OverlayDropdownPreference(
                    title = "记录类型",
                    items = recordTypes.map { it.label },
                    selectedIndex = recordIndex,
                    onSelectedIndexChange = { viewModel.setRecordType(recordTypes[it]) },
                    enabled = !ui.running,
                )
                OverlayDropdownPreference(
                    title = "轮次",
                    summary = "用于观察抖动与稳定性",
                    items = roundOptions.map { "$it 次" },
                    selectedIndex = roundOptions.indexOf(ui.rounds).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setRounds(roundOptions[it]) },
                    enabled = !ui.running,
                )
            }
        }
        item {
            SmallTitle(text = "协议")
            TabRow(
                tabs = listOf("全部", "DoH", "DoT"),
                selectedTabIndex = protocolIndex,
                onTabSelected = { index ->
                    viewModel.setProtocolFilter(listOf("ALL", "DOH", "DOT")[index])
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
            )
        }
        item { SmallTitle(text = "DNS 服务器") }
        item {
            Card(modifier = Modifier.sectionCard()) {
                visibleServers.forEach { server ->
                    CheckboxPreference(
                        title = "${server.name} · ${server.protocol.label()}",
                        summary = server.endpointLabel(),
                        checked = server.id in ui.selectedServerIds,
                        onCheckedChange = { viewModel.toggleServer(server.id) },
                        enabled = !ui.running,
                    )
                }
            }
        }
        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
                onClick = { if (ui.running) viewModel.cancelTest() else viewModel.startTest() },
            ) {
                Text(if (ui.running) "停止测试" else "开始测试")
            }
        }
        if (ui.running || ui.progressText.isNotBlank()) {
            item {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    if (ui.running) {
                        LinearProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = ui.progressText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
        if (ui.results.isNotEmpty()) {
            item {
                SmallTitle(text = "对比")
                Card(
                    modifier = Modifier.sectionCard(),
                    insideMargin = PaddingValues(16.dp),
                ) {
                    Text(
                        text = fastest?.let { "最快：${it.server.name} ${it.server.protocol.label()}  ${it.timings.totalMs.toMsLabel()}" }
                            ?: "本轮没有成功结果",
                        style = MiuixTheme.textStyles.title4,
                    )
                    groups.forEach { (answers, servers) ->
                        Spacer(Modifier.height(8.dp))
                        Text(answers, style = MiuixTheme.textStyles.body2)
                        Text(
                            text = servers.joinToString(),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                }
            }
            item { SmallTitle(text = "结果") }
            items(ui.results, key = { it.id }) { result ->
                ResultCard(result = result, onClick = { viewModel.openResult(result) })
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    ui: DnsUiState,
    padding: PaddingValues,
    onOpenSession: (HistorySession) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        if (ui.history.isEmpty()) {
            item {
                Text(
                    text = "还没有历史记录。完成一次测试后会保存在这里。",
                    modifier = Modifier.padding(24.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            items(ui.history, key = { it.id }) { session ->
                val fastest = fastestSuccessful(session.results)
                Card(
                    modifier = Modifier.sectionCard(),
                    insideMargin = PaddingValues(16.dp),
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = { onOpenSession(session) },
                ) {
                    Text("${session.domain}  ${session.recordType}", style = MiuixTheme.textStyles.title4)
                    Text(
                        text = "${session.startedAtMs.toTimeLabel()}  ·  ${session.networkLabel}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                    Text(
                        text = fastest?.let { "最快 ${it.server.name} ${it.timings.totalMs.toMsLabel()}" }
                            ?: "全部失败",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionScreen(
    session: HistorySession,
    padding: PaddingValues,
    onOpenResult: (DnsQueryResult) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SmallTitle(text = "${session.domain} · ${session.recordType}")
            Card(
                modifier = Modifier.sectionCard(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(session.startedAtMs.toTimeLabel(), style = MiuixTheme.textStyles.body2)
                Text(
                    session.networkLabel,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
        items(session.results, key = { it.id }) { result ->
            ResultCard(result = result, onClick = { onOpenResult(result) })
        }
    }
}

@Composable
private fun SettingsScreen(
    ui: DnsUiState,
    padding: PaddingValues,
    viewModel: DnsAppViewModel,
) {
    val timeoutOptions = listOf(3_000, 5_000, 8_000, 12_000, 20_000)
    val themeOptions = listOf("System", "Light", "Dark", "Monet")
    val themeLabels = listOf("跟随系统", "浅色", "深色", "动态色")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SmallTitle(text = "外观")
            Card(modifier = Modifier.sectionCard()) {
                OverlayDropdownPreference(
                    title = "主题",
                    items = themeLabels,
                    selectedIndex = themeOptions.indexOf(ui.colorSchemeMode).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setColorSchemeMode(themeOptions[it]) },
                )
            }
        }
        item {
            SmallTitle(text = "查询")
            Card(modifier = Modifier.sectionCard()) {
                OverlayDropdownPreference(
                    title = "超时",
                    items = timeoutOptions.map { "${it / 1000} 秒" },
                    selectedIndex = timeoutOptions.indexOf(ui.timeoutMs).coerceAtLeast(0),
                    onSelectedIndexChange = { viewModel.setTimeout(timeoutOptions[it]) },
                )
                SwitchPreference(
                    title = "复用连接",
                    summary = "关闭时可让每次查询都重新完成 TCP/TLS 握手",
                    checked = ui.reuseConnections,
                    onCheckedChange = viewModel::setReuseConnections,
                )
                ArrowPreference(
                    title = "恢复默认服务器",
                    summary = "恢复预置服务器的显示，并选中 Cloudflare / Google / AliDNS",
                    onClick = viewModel::restoreDefaultServers,
                )
                ArrowPreference(
                    title = "服务器管理",
                    summary = "隐藏预置服务器，或添加、编辑自定义 DoH / DoT",
                    onClick = viewModel::openServerManager,
                )
            }
        }
        item {
            SmallTitle(text = "数据")
            Card(modifier = Modifier.sectionCard()) {
                ArrowPreference(
                    title = "清空历史",
                    summary = "删除本机保存的测试会话",
                    onClick = viewModel::clearHistory,
                )
            }
        }
        item {
            SmallTitle(text = "关于")
            Card(
                modifier = Modifier.sectionCard(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text(
                    text = "主动发起 RFC 8484 DoH 与 RFC 7858 DoT 查询，记录 TCP、TLS、首字节和解析结果，便于对比不同网络下的延迟与解析差异。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun ResultScreen(
    result: DnsQueryResult,
    padding: PaddingValues,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SmallTitle(text = "概览")
            Card(
                modifier = Modifier.sectionCard(),
                insideMargin = PaddingValues(16.dp),
            ) {
                Text("${result.server.name} · ${result.server.protocol.label()}", style = MiuixTheme.textStyles.title3)
                Text(
                    "${result.domain}  ${result.recordType}  ·  第 ${result.round} 轮",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = if (result.success) result.timings.totalMs.toMsLabel() else (result.error ?: "失败"),
                    color = if (result.success) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.headline1,
                )
                Text("TCP ${result.timings.connectMs?.toMsLabel() ?: "—"}  ·  TLS ${result.timings.tlsMs?.toMsLabel() ?: "—"}  ·  首字节 ${result.timings.firstByteMs?.toMsLabel() ?: "—"}")
                Text(
                    listOfNotNull(
                        result.httpProtocol,
                        result.tlsProtocol,
                        result.tlsCipher,
                        result.remoteAddress,
                        result.httpStatus?.let { "HTTP $it" },
                    ).joinToString("  ·  ").ifEmpty { "无额外协议信息" },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
                Text(
                    "${result.network.type}  ·  ${result.network.privateDnsLabel()}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
        item {
            SmallTitle(text = "解析结果")
            Card(
                modifier = Modifier.sectionCard(),
                insideMargin = PaddingValues(16.dp),
            ) {
                val message = result.message
                if (message == null) {
                    Text(result.error ?: "无报文", color = MiuixTheme.colorScheme.error)
                } else {
                    Text("RCODE ${message.rcode}  ·  ${message.flags.joinToString(" ")}")
                    message.questions.forEach { Text(it, style = MiuixTheme.textStyles.body2) }
                    Spacer(Modifier.height(8.dp))
                    if (message.answers.isEmpty()) {
                        Text("无 Answer 记录", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    } else {
                        message.answers.forEach { record ->
                            Text("${record.type}  ${record.data}", style = MiuixTheme.textStyles.body2)
                            Text(
                                "${record.name}  TTL ${record.ttl}",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                    }
                }
            }
        }
        item { SmallTitle(text = "完整过程") }
        items(result.events.size) { index ->
            val event = result.events[index]
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MiuixTheme.colorScheme.primary, CircleShape),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${event.elapsedMs.toMsLabel()}  ${event.stage}", style = MiuixTheme.textStyles.body2)
                    if (event.detail.isNotBlank()) {
                        Text(
                            event.detail,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp, bottom = 8.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
                onClick = {
                    copyText(context, result.toCopyText())
                    onCopied()
                },
            ) {
                Text("复制结果")
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("DNS 查询结果", text))
}

@Composable
private fun ResultCard(result: DnsQueryResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier.sectionCard(),
        insideMargin = PaddingValues(16.dp),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${result.server.name} · ${result.server.protocol.label()}", style = MiuixTheme.textStyles.title4)
                Text(
                    result.answerSummary(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            Text(
                text = if (result.success) result.timings.totalMs.toMsLabel() else "失败",
                color = if (result.success) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                style = MiuixTheme.textStyles.title4,
            )
        }
    }
}
