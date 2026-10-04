package com.apkeditor.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.apkeditor.miuix.data.DexEntry
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixTopBar
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DexListPage(
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
                title = "DEX 文件",
                onBack = onBack,
                scrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop,
                scrollProgress = { scrollProgress },
            )
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            when {
                error != null -> ErrorBox(error!!, onRetry = null)
                dexList == null -> LoadingBox("读取 DEX 列表…")
                dexList!!.isEmpty() -> ErrorBox("未找到 DEX 文件", onRetry = null)
                else -> LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .pageScrollModifiers(
                            showTopAppBar = true,
                            topAppBarScrollBehavior = topAppBarScrollBehavior,
                        )
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
}
