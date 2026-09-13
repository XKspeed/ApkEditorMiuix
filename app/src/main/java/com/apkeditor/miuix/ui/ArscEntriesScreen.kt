package com.apkeditor.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.ResourceEntryInfo
import com.apkeditor.miuix.data.ResourceVariant
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
 * ARSC 资源条目（NP 风格）：
 * - 顶部搜索框模糊过滤
 * - 每条资源展开显示全部配置变体
 * - 点变体独立编辑该变体的值
 * - color 显示色块，dimen 显示单位
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
    var editing by remember { mutableStateOf<Pair<ResourceEntryInfo, ResourceVariant>?>(null) }
    var editValue by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        service.searchResources(type, filter.ifBlank { "" })
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "读取失败" }
    }

    LaunchedEffect(type) { reload() }
    LaunchedEffect(filter) {
        if (filter.isBlank()) reload()
        else service.searchResources(type, filter)
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "搜索失败" }
    }

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
        when {
            error != null -> ErrorBox(error!!, onRetry = {
                scope.launch { error = null; reload() }
            })
            entries == null -> LoadingBox("读取资源条目…")
            else -> Column(
                Modifier.fillMaxSize().padding(innerPadding)
            ) {
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = "搜索资源名",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "共 ${entries!!.size} 项",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries!!) { e ->
                        // 资源名行
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(e.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(e.hexId,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.subtitle,
                            )
                        }
                        // 每个变体一行
                        e.variants.forEach { v ->
                            VariantRow(
                                variant = v,
                                resourceType = type,
                                onClick = {
                                    editing = e to v
                                    editValue = v.displayValue
                                }
                            )
                        }
                        if (e.variants.isEmpty()) {
                            Text("（无变体）",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.subtitle,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                            )
                        }
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // 变体编辑弹窗
    editing?.let { (entry, variant) ->
        MiuixDialog(
            title = "${entry.name} · ${variant.qualifiers.ifBlank { "default" }}",
            onDismiss = { editing = null },
        ) {
            InfoRow("资源 ID", entry.hexId)
            InfoRow("配置", variant.qualifiers.ifBlank { "default" })
            InfoRow("类型", variant.valueType)
            Spacer(Modifier.height(8.dp))
            Text("值",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(6.dp))
            TextField(
                value = editValue,
                onValueChange = { editValue = it },
                label = "值（#FFRRGGBB / -330dp / @type/name 等自动识别）",
                modifier = Modifier.fillMaxWidth(),
            )
            DialogActions(
                confirmText = "保存",
                onConfirm = {
                    saving = true
                    scope.launch {
                        runCatching {
                            service.saveResourceValue(entry.id, variant.qualifiers.ifBlank { null }, editValue)
                        }.onSuccess {
                            tip = "已保存"
                            reload()
                        }.onFailure {
                            tip = "保存失败：${it.message}"
                        }
                        saving = false
                        editing = null
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

@Composable
private fun VariantRow(
    variant: ResourceVariant,
    resourceType: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // color 类型显示色块
        if (resourceType == "color" && variant.displayValue.startsWith("#")) {
            val color = runCatching { Color(android.graphics.Color.parseColor(variant.displayValue)) }.getOrNull()
            if (color != null) {
                Spacer(Modifier.width(4.dp))
                Spacer(
                    Modifier.width(18.dp).height(18.dp).clip(CircleShape).background(color)
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(
            variant.qualifiers.ifBlank { "default" },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.subtitle,
            modifier = Modifier.width(80.dp),
        )
        Text(
            variant.displayValue.ifEmpty { "<空>" },
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text("›",
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.subtitle,
        )
    }
}
