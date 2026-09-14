package com.apkeditor.miuix.ui
import com.apkeditor.miuix.ui.component.BackNavigationIcon

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
import com.apkeditor.miuix.data.XmlFileInfo
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.SectionCard
import com.apkeditor.miuix.ui.components.TextField
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.WindowInsets

@Composable
fun XmlFilesPage(
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenFile: (String) -> Unit,
) {
    var files by remember { mutableStateOf<List<XmlFileInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        service.listXmlFiles()
            .onSuccess { files = it }
            .onFailure { error = it.message ?: "无法读取 XML 文件列表" }
    }

    val visible = files?.filter {
        filter.isBlank() || it.path.contains(filter, ignoreCase = true)
    } ?: emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "XML / res 文件",
                navigationIcon = {
                    BackNavigationIcon(onClick = onBack)
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
            files == null -> LoadingBox("读取文件列表…")
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // res 搜索框
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = "搜索 res 文件",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
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
                LazyColumn(Modifier.fillMaxSize()) {
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
