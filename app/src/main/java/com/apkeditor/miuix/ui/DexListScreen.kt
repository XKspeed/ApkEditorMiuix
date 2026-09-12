package com.apkeditor.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.DexEntry
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.SectionCard
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.WindowInsets

@Composable
fun DexListScreen(
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenDex: (String) -> Unit,
) {
    var dexList by remember { mutableStateOf<List<DexEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        service.listDexFiles()
            .onSuccess { dexList = it }
            .onFailure { error = it.message ?: "无法读取 DEX 列表" }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "DEX 文件",
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
            error != null -> ErrorBox(error!!, onRetry = null)
            dexList == null -> LoadingBox("读取 DEX 列表…")
            dexList!!.isEmpty() -> ErrorBox("未找到 DEX 文件", onRetry = null)
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item {
                    Text(
                        "点击 DEX 反汇编为 Smali 代码进行编辑",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                items(dexList!!) { dex ->
                    ListItemRow(
                        title = dex.name,
                        subtitle = "点击反汇编",
                        trailing = "${dex.size}  ›",
                        onClick = { onOpenDex(dex.name) },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
