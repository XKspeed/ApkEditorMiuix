package com.apkeditor.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.TextField
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * NP 管理器风格 Smali 类列表：
 * 顶部搜索栏，下面是扁平类列表（完整包名），点类直接打开编辑器。
 */
@Composable
fun SmaliTreeScreen(
    dexNames: List<String>,
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    var allFiles by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var assembling by remember { mutableStateOf(false) }
    var assembleResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dexNames) {
        error = null
        service.listSmaliFiles(dexNames.first())
            .onSuccess { files ->
                allFiles = files.map { dexNames.first() to it }
            }
            .onFailure { error = it.message ?: "反汇编失败" }
    }

    val visible = allFiles?.filter {
        filter.isBlank() || it.second.contains(filter, ignoreCase = true)
    } ?: emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "Smali · ${dexNames.first()}",
                navigationIcon = {
                    Text("返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
                actions = {
                    Text("汇编",
                        color = if (assembling) MiuixTheme.colorScheme.onSurfaceVariantActions
                        else MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(enabled = !assembling) {
                                assembling = true
                                scope.launch {
                                    service.assembleDex(dexNames.first())
                                        .onSuccess { assembleResult = "汇编完成" }
                                        .onFailure { assembleResult = "汇编失败：${it.message}" }
                                    assembling = false
                                }
                            },
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            // 搜索栏
            TextField(
                value = filter,
                onValueChange = { filter = it },
                label = "搜索类名",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "共 ${visible.size} 个类",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when {
                error != null -> ErrorBox(error!!, onRetry = null)
                allFiles == null -> LoadingBox("正在反汇编…")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visible) { (dex, path) ->
                        // NP 风格：显示完整类名（去掉 smali/ 前缀，把 / 换成 .）
                        val className = path.removePrefix("smali/").substringBeforeLast(".smali").replace("/", ".")
                        ListItemRow(
                            title = className,
                            subtitle = path,
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
        top.yukonga.miuix.kmp.basic.Text(
            assembleResult!!,
            modifier = Modifier.padding(16.dp),
        )
    }
}
