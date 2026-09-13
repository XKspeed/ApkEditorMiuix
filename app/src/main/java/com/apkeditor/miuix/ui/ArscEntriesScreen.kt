package com.apkeditor.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.ResourceEntryInfo
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.InfoRow
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.TextField
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * NP 管理器风格资源条目列表：
 * 顶部搜索栏，下面是资源列表（名称+值），点条目弹出编辑对话框。
 */
@Composable
fun ArscEntriesScreen(
    type: String,
    service: ApkDataService,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ResourceEntryInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ResourceEntryInfo?>(null) }
    var editValue by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        service.listResources(type)
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "读取失败" }
    }

    LaunchedEffect(type) { reload() }

    val visible = entries?.filter {
        filter.isBlank() || it.name.contains(filter, ignoreCase = true)
    } ?: emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "资源 · $type",
                navigationIcon = {
                    Text("返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TextField(
                value = filter,
                onValueChange = { filter = it },
                label = "搜索资源名",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "共 ${visible.size} 项",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when {
                error != null -> ErrorBox(error!!, onRetry = {
                    scope.launch { error = null; reload() }
                })
                entries == null -> LoadingBox("读取资源条目…")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visible) { e ->
                        // color 类型显示色块
                        val showColorBlock = type == "color" && e.value.startsWith("#")
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                editing = e
                                editValue = e.value
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (showColorBlock) {
                                val color = runCatching {
                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(e.value))
                                }.getOrNull()
                                if (color != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Spacer(
                                        Modifier.width(18.dp).height(18.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(color)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MiuixTheme.textStyles.main)
                                Spacer(Modifier.height(2.dp))
                                // array 类型列表里不显示长值，弹窗里看
                                val displayValue = if (type.contains("array", true)) "<数组>"
                                else e.value.ifEmpty { "<空>" }
                                Text(
                                    displayValue,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.subtitle,
                                )
                            }
                        }
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    editing?.let { entry ->
        MiuixDialog(
            title = entry.name,
            onDismiss = { editing = null },
        ) {
            InfoRow("资源 ID", entry.hexId)
            Spacer(Modifier.height(8.dp))
            Text("值",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(6.dp))
            TextField(
                value = editValue,
                onValueChange = { editValue = it },
                label = "值",
                modifier = Modifier.fillMaxWidth(),
            )
            DialogActions(
                confirmText = "保存",
                onConfirm = {
                    saving = true
                    scope.launch {
                        service.saveResourceValue(entry.id, null, editValue)
                            .onSuccess { tip = "已保存" }
                            .onFailure { tip = "保存失败：${it.message}" }
                        saving = false
                        editing = null
                        reload()
                    }
                },
                onCancel = { editing = null },
            )
        }
    }

    if (tip != null) {
        MiuixDialog(title = "结果", onDismiss = { tip = null }) {
            Text(tip!!, color = MiuixTheme.colorScheme.onSurface)
            DialogActions(
                confirmText = "好的",
                onConfirm = { tip = null },
                cancelText = "",
                onCancel = { tip = null },
            )
        }
    }
}
