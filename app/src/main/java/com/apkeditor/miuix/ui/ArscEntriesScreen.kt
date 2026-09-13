package com.apkeditor.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.ResourceEntryInfo
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.InfoRow
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.SectionCard
import com.apkeditor.miuix.ui.components.TextField
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.WindowInsets

@Composable
fun ArscEntriesScreen(
    type: String,
    service: ApkDataService,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ResourceEntryInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ResourceEntryInfo?>(null) }
    var editValue by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        service.listResources(type)
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "读取失败" }
    }

    LaunchedEffect(type) { reload() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "资源 · $type",
                navigationIcon = {
                    Text(
                        "返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
            )
        }
    ) { innerPadding ->
        when {
            error != null -> ErrorBox(error!!, onRetry = { scope.launch { error = null; reload() } })
            entries == null -> LoadingBox("读取资源条目…")
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item {
                    Text(
                        "共 ${entries!!.size} 项 · 点击编辑",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                items(entries!!) { e ->
                    ListItemRow(
                        title = e.name,
                        subtitle = e.hexId,
                        trailing = e.value.ifEmpty { "<空>" },
                        onClick = {
                            selected = e
                            editValue = e.value
                            editName = e.name
                        },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // 编辑弹窗
    selected?.let { entry ->
        MiuixDialog(
            title = "${entry.type} · ${entry.name}",
            onDismiss = { selected = null },
        ) {
            InfoRow("资源 ID", entry.hexId)
            if (entry.configs.isNotEmpty()) {
                InfoRow("配置", entry.configs.joinToString())
            }
            Spacer(Modifier.height(8.dp))
            Text("字符串值", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.subtitle)
            Spacer(Modifier.height(6.dp))
            TextField(
                value = editValue,
                onValueChange = { editValue = it },
                label = "值",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("资源名称", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.subtitle)
            Spacer(Modifier.height(6.dp))
            TextField(
                value = editName,
                onValueChange = { editName = it },
                label = "名称",
                modifier = Modifier.fillMaxWidth(),
            )
            DialogActions(
                confirmText = "保存",
                onConfirm = {
                    saving = true
                    scope.launch {
                        runCatching {
                            if (editName != entry.name) {
                                service.renameResource(entry.id, editName)
                            }
                            service.saveResourceValue(entry.id, entry.configs.firstOrNull(), editValue)
                        }
                            .onSuccess { tip = "已保存：${entry.hexId}" }
                            .onFailure { tip = "保存失败：${it.message}" }
                        saving = false
                        selected = null
                        reload()
                    }
                },
                onCancel = { selected = null },
            )
        }
    }

    if (tip != null) {
        MiuixDialog(
            title = "结果",
            onDismiss = { tip = null },
        ) {
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
