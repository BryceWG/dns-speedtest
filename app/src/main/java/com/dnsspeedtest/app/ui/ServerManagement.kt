package com.dnsspeedtest.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dnsspeedtest.app.dns.CustomServerParser
import com.dnsspeedtest.app.dns.DnsProtocol
import com.dnsspeedtest.app.dns.DnsServer
import com.dnsspeedtest.app.dns.DnsServerCatalog
import com.dnsspeedtest.app.dns.isCustom
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ServerManagerScreen(
    ui: DnsUiState,
    padding: PaddingValues,
    onOpenAdd: () -> Unit,
    onOpenEdit: (String) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val hidden = ui.hiddenBuiltinServerIds
    val mutedTitle = BasicComponentDefaults.titleColor(
        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.30f),
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SmallTitle(text = "预置服务器")
            Card(modifier = Modifier.sectionCard()) {
                DnsServerCatalog.all.forEach { server ->
                    val visibleOnTest = server.id !in hidden
                    ArrowPreference(
                        title = "${server.name} · ${server.protocol.label()}",
                        titleColor = if (visibleOnTest) {
                            BasicComponentDefaults.titleColor()
                        } else {
                            mutedTitle
                        },
                        summary = if (visibleOnTest) {
                            "显示在测试页 · ${server.endpointLabel()}"
                        } else {
                            "已隐藏 · ${server.endpointLabel()}"
                        },
                        onClick = { onOpenEdit(server.id) },
                    )
                }
            }
        }
        item {
            SmallTitle(text = "自定义服务器")
            Card(modifier = Modifier.sectionCard()) {
                if (ui.customServers.isEmpty()) {
                    ArrowPreference(
                        title = "添加自定义服务器",
                        summary = "DoH 填写 HTTPS URL，DoT 填写主机或 IP",
                        onClick = onOpenAdd,
                    )
                } else {
                    ui.customServers.forEach { server ->
                        ArrowPreference(
                            title = "${server.name} · ${server.protocol.label()}",
                            summary = server.endpointLabel(),
                            onClick = { onOpenEdit(server.id) },
                        )
                    }
                    ArrowPreference(
                        title = "添加自定义服务器",
                        summary = "点击已有条目可进入编辑页",
                        onClick = onOpenAdd,
                    )
                }
            }
        }
    }
}

@Composable
fun ServerEditorScreen(
    server: DnsServer?,
    adding: Boolean,
    visibleOnTest: Boolean,
    padding: PaddingValues,
    onSaveCustom: (DnsServer) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onVisibilityChange: (String, Boolean) -> Unit,
) {
    val isCustom = server?.isCustom() == true || adding
    var protocolIndex by remember { mutableIntStateOf(if (server?.protocol == DnsProtocol.DOT) 1 else 0) }
    var name by remember { mutableStateOf(server?.name.orEmpty()) }
    var address by remember { mutableStateOf(server?.let(CustomServerParser::formatAddress).orEmpty()) }
    var bootstrap by remember { mutableStateOf(server?.let(CustomServerParser::formatBootstrap).orEmpty()) }
    var sni by remember { mutableStateOf(server?.sni.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(server?.id, adding) {
        protocolIndex = if (server?.protocol == DnsProtocol.DOT) 1 else 0
        name = server?.name.orEmpty()
        address = server?.let(CustomServerParser::formatAddress).orEmpty()
        bootstrap = server?.let(CustomServerParser::formatBootstrap).orEmpty()
        sni = server?.sni.orEmpty()
        error = null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        if (!isCustom && server != null) {
            item {
                SmallTitle(text = "测试页")
                Card(modifier = Modifier.sectionCard()) {
                    SwitchPreference(
                        title = "显示在测试页",
                        summary = "关闭后不会出现在测试页的服务器列表中",
                        checked = visibleOnTest,
                        onCheckedChange = { onVisibilityChange(server.id, it) },
                    )
                }
            }
        }
        item {
            SmallTitle(text = if (adding) "服务器信息" else "编辑信息")
            Card(modifier = Modifier.sectionCard()) {
                OverlayDropdownPreference(
                    title = "协议",
                    items = listOf("DoH", "DoT"),
                    selectedIndex = protocolIndex,
                    onSelectedIndexChange = { protocolIndex = it },
                    enabled = isCustom,
                )
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "名称",
                    enabled = isCustom,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
                )
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    label = if (protocolIndex == 0) {
                        "地址，如 https://dns.google/dns-query"
                    } else {
                        "主机或 IP，如 1.1.1.1:853"
                    },
                    enabled = isCustom,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
                )
                TextField(
                    value = bootstrap,
                    onValueChange = { bootstrap = it },
                    label = "引导 IP（可选，逗号分隔）",
                    enabled = isCustom,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp),
                )
                TextField(
                    value = sni,
                    onValueChange = { sni = it },
                    label = "SNI（可选，默认同主机名）",
                    enabled = isCustom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp, bottom = 12.dp),
                )
            }
        }
        if (isCustom && server != null && !adding) {
            item {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    onClick = { onDeleteCustom(server.id) },
                ) {
                    Text("删除服务器", color = MiuixTheme.colorScheme.error)
                }
            }
        }
        if (isCustom) {
            item {
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                    )
                }
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    onClick = {
                        val protocol = if (protocolIndex == 0) DnsProtocol.DOH else DnsProtocol.DOT
                        CustomServerParser.parse(
                            name = name,
                            protocol = protocol,
                            address = address,
                            bootstrapText = bootstrap,
                            sniText = sni,
                            existingId = server?.id,
                        ).onSuccess(onSaveCustom)
                            .onFailure { error = it.message ?: "无法解析服务器地址" }
                    },
                ) {
                    Text(if (adding) "添加" else "保存")
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

fun DnsServer.endpointLabel(): String {
    val endpoint = CustomServerParser.formatAddress(this)
    val extra = buildList {
        if (bootstrapIps.isNotEmpty()) add("引导 ${bootstrapIps.joinToString("/")}")
        if (sni.isNotBlank() && sni != host) add("SNI $sni")
        if (isCustom()) add("自定义")
    }
    return if (extra.isEmpty()) endpoint else "$endpoint  ·  ${extra.joinToString("  ·  ")}"
}

fun Modifier.sectionCard(): Modifier =
    fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)
