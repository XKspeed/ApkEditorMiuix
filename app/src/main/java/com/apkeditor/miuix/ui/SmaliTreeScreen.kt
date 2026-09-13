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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.TextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.WindowInsets

/**
 * Smali 文件树：支持同时反编译多个 DEX（并行 baksmali）。
 * 文件列表项带 dex 前缀，点击进入编辑。
 */
@Composable
fun SmaliTreeScreen(
    dexNames: List<String>,
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenFile: (String, String) -> Unit, // dexName, filePath
) {
    var files by remember { mutableStateOf<List<Pair<String, String>>?>(null) } // (dexName, path)
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var assembling by remember { mutableStateOf(false) }
    var assembleResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dexNames) {
        error = null
        files = null
        // 并行反编译所有 dex
        val results = coroutineScope {
            dexNames.map { dex ->
                async(Dispatchers.IO) { dex to service.listSmaliFiles(dex) }
            }.awaitAll()
        }
        val merged = mutableListOf<Pair<String, String>>()
        var firstErr: String? = null
        results.forEach { (dex, r) ->
            r.onSuccess { list -> list.forEach { merged.add(dex to it) } }
                .onFailure { e -> if (firstErr == null) firstErr = "${e.message ?: "反汇编失败"}（$dex）" }
        }
        if (merged.isEmpty() && firstErr != null) error = firstErr
        else files = merged
    }

    val title = if (dexNames.size == 1) "Smali · ${dexNames[0]}"
    else "Smali · ${dexNames.size} 个 DEX"

    val visible = files?.filter {
        filter.isBlank() ||
            it.second.contains(filter, ignoreCase = true) ||
            it.first.contains(filter, ignoreCase = true)
    } ?: emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    Text(
                        "返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
                actions = {
                    // 全部 DEX 一并汇编
                    Text(
                        "全部汇编",
                        color = if (assembling) MiuixTheme.colorScheme.onSurfaceVariantActions
                        else MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(enabled = !assembling) {
                                assembling = true
                                scope.launch {
                                    val errs = mutableListOf<String>()
                                    dexNames.forEach { dex ->
                                        service.assembleDex(dex)
                                            .onFailure { errs.add("$dex: ${it.message}") }
                                    }
                                    assembleResult = if (errs.isEmpty()) "已汇编 ${dexNames.size} 个 DEX 并替换回 APK"
                                    else "部分失败：${errs.joinToString("；")}"
                                    assembling = false
                                }
                            },
                    )
                },
            )
        }
    ) { innerPadding ->
        when {
            error != null -> ErrorBox(error!!, onRetry = null)
            files == null -> LoadingBox("正在并行反汇编 ${dexNames.size} 个 DEX …")
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = "搜索类名 / 文件名",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "共 ${files!!.size} 个 smali 文件",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visible) { (dex, path) ->
                        ListItemRow(
                            title = path.substringAfterLast("/"),
                            subtitle = if (dexNames.size > 1) "$dex · $path" else path,
                            trailing = "›",
                            onClick = { onOpenFile(dex, path) },
                        )
                        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (assembleResult != null) {
        MiuixDialog(
            title = "汇编结果",
            onDismiss = { assembleResult = null },
        ) {
            Text(assembleResult!!, color = MiuixTheme.colorScheme.onSurface)
            DialogActions(
                confirmText = "好的",
                onConfirm = { assembleResult = null },
                cancelText = "",
                onCancel = { assembleResult = null },
            )
        }
    }
}
