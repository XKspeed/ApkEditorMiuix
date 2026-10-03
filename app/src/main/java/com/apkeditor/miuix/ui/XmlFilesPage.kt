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
import com.apkeditor.miuix.data.XmlFileInfo
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

@Composable
fun XmlFilesPage(
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    var files by remember { mutableStateOf<List<XmlFileInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        service.listXmlFiles()
            .onSuccess { files = it }
            .onFailure { error = it.message ?: "无法读取 XML 文件列表" }
    }

    val visible = files?.filter {
        filter.isBlank() || it.path.contains(filter, ignoreCase = true)
    } ?: emptyList()

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
                title = "XML / res 文件",
                onBack = onBack,
                onSearch = { showSearch = !showSearch },
                scrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop,
                scrollProgress = { scrollProgress },
            )
        }
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            when {
            error != null -> ErrorBox(error!!, onRetry = null)
            files == null -> LoadingBox("读取文件列表…")
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showSearch) {
                    TextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = "搜索 res 文件",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Text(
                    "共 ${files!!.size} 个 XML 文件，匹配 ${visible.size} 个",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    "点击解码为文本后编辑，保存时编码回二进制",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                    ),
                ) {
                    items(visible) { f ->
                        ListItemRow(
                            title = f.path.substringAfterLast("/"),
                            subtitle = f.path,
                            trailing = "${f.size}  ›",
                            onClick = { onOpenFile(f.path) },
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
