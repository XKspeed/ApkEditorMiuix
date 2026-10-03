package com.apkeditor.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.SmaliClassEntry
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixTopBar
import com.apkeditor.miuix.ui.components.TextField
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * “所有类”列表页：跨 DEX 合并的扁平类列表 + 搜索。
 * 入口：类详情页顶栏的指南针图标。
 * 点类 → 替换栈顶的类详情页（直接进入该类的方法列表）。
 */
@Composable
fun SmaliClassListPage(
    dexNames: List<String>,
    service: ApkDataService,
    onBack: () -> Unit,
    onSelectClass: (dexName: String, filePath: String) -> Unit,
) {
    var entries by remember { mutableStateOf<List<SmaliClassEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(dexNames) {
        error = null
        entries = null
        service.listSmaliClasses(dexNames)
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "加载类列表失败" }
    }

    val all = entries
    val kw = filter.trim()
    val shown = if (all == null) emptyList()
    else if (kw.isEmpty()) all
    else all.filter {
        it.className.contains(kw, true) ||
            it.className.substringAfterLast('.').contains(kw, true)
    }

    // 主页同款 progressive 顶栏：滚动缩小 + 折叠后模糊
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf { if (lazyListState.firstVisibleItemIndex > 0) 1f else 0f }
    }
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MiuixTopBar(
                title = "所有类",
                subtitle = if (all != null) shown.size.toString() + " 个" else "",
                onBack = onBack,
                onSearch = { showSearch = !showSearch },
                scrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop,
                scrollProgress = { scrollProgress },
            )
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (showSearch) {
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = "搜索类名",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                error != null -> ErrorBox(error!!, onRetry = null)
                all == null -> LoadingBox("加载类列表…")
                else -> {
                    if (kw.isNotEmpty()) {
                        Text(
                            text = "找到 " + shown.size + " 个匹配",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        )
                    }
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize().pageScrollModifiers(
                            showTopAppBar = true,
                            topAppBarScrollBehavior = topAppBarScrollBehavior,
                        ),
                    ) {
                        items(shown, key = { it.dex + "/" + it.filePath }) { e ->
                            ListItemRow(
                                title = e.className.substringAfterLast('.'),
                                subtitle = e.dex + " · " + e.className,
                                trailing = "›",
                                onClick = { onSelectClass(e.dex, e.filePath) },
                            )
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
        }
    }
}
