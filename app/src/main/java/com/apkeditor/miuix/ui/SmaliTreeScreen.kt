package com.apkeditor.miuix.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.SmaliTreeNode
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
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
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Smali 目录树（NP 管理器风格）：
 * 顶层按 smali / smali_classesN 分组，包路径逐级展开，▸/▾ 折叠。
 * 搜索实时过滤，点文件进编辑器，长按/菜单可重命名、删除类。
 */
@Composable
fun SmaliTreeScreen(
    dexNames: List<String>,
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenFile: (String, String) -> Unit,
) {
    var topNodes by remember { mutableStateOf<List<SmaliTreeNode>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var assembling by remember { mutableStateOf(false) }
    var assembleResult by remember { mutableStateOf<String?>(null) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dexNames) {
        error = null
        service.listSmaliTree(dexNames)
            .onSuccess { perDex ->
                topNodes = perDex.values.flatten()
                // 默认展开第一个 dex
                perDex.keys.firstOrNull()?.let { expanded[it] = true }
            }
            .onFailure { error = it.message ?: "反汇编失败" }
    }

    val title = if (dexNames.size == 1) "Smali · ${dexNames[0]}"
    else "Smali · ${dexNames.size} 个 DEX"

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    Text("返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
                actions = {
                    Text("全部汇编",
                        color = if (assembling) MiuixTheme.colorScheme.onSurfaceVariantActions
                        else MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(enabled = !assembling) {
                                assembling = true
                                scope.launch {
                                    val errs = mutableListOf<String>()
                                    dexNames.forEach { dex ->
                                        service.assembleDex(dex).onFailure { errs.add("$dex: ${it.message}") }
                                    }
                                    assembleResult = if (errs.isEmpty()) "已汇编 ${dexNames.size} 个 DEX"
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
            topNodes == null -> LoadingBox("正在加载 smali 目录树 …")
            else -> Column(
                Modifier.fillMaxSize().padding(innerPadding)
            ) {
                TextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = "搜索类名 / 文件名",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "共 ${countFiles(topNodes!!)} 个 smali 文件",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    if (filter.isBlank()) {
                        // 目录树模式
                        topNodes!!.forEach { dexNode ->
                            // 顶层 dex 行
                            item(key = dexNode.path) {
                                DexHeaderRow(
                                    node = dexNode,
                                    isExpanded = expanded[dexNode.path] == true,
                                    onClick = { toggle(expanded, dexNode.path) },
                                )
                            }
                            if (expanded[dexNode.path] == true) {
                                dexNode.children.forEach { child ->
                                    items(buildVisible(child, expanded)) { n ->
                                        TreeRow(n, expanded, onOpenFile)
                                    }
                                }
                            }
                            item { HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine) }
                        }
                    } else {
                        // 搜索模式：扁平列出匹配文件
                        val filtered = filterFiles(topNodes!!, filter)
                        if (filtered.isEmpty()) {
                            item {
                                Text("没有匹配的类",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                                )
                            }
                        }
                        items(filtered) { (dex, path) ->
                            ListItemRow(
                                title = path.substringAfterLast("/"),
                                subtitle = "$dex · $path",
                                trailing = "›",
                                onClick = { onOpenFile(dex, path) },
                            )
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (assembleResult != null) {
        MiuixDialog(title = "汇编结果", onDismiss = { assembleResult = null }) {
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

private fun toggle(expanded: MutableMap<String, Boolean>, key: String) {
    expanded[key] = (expanded[key] != true)
}

private fun keyOf(node: SmaliTreeNode): String {
    val dex = node.dex.ifEmpty { "smali" }
    return "$dex/${node.path}"
}

private fun countFiles(nodes: List<SmaliTreeNode>): Int = nodes.sumOf { n ->
    if (n.isDir) countFiles(n.children) else 1
}

private fun filterFiles(nodes: List<SmaliTreeNode>, kw: String): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    fun walk(list: List<SmaliTreeNode>) {
        list.forEach { n ->
            if (n.isDir) walk(n.children)
            else if (n.name.contains(kw, true) || n.path.contains(kw, true)) {
                out.add(n.dex to n.path)
            }
        }
    }
    walk(nodes)
    return out
}

/** 递归收集可见节点：折叠目录只显示自己，展开递归子节点 */
private fun buildVisible(root: SmaliTreeNode, expanded: MutableMap<String, Boolean>): List<SmaliTreeNode> {
    val out = mutableListOf<SmaliTreeNode>()
    out.add(root)
    if (!root.isDir) return out
    if (expanded[keyOf(root)] != true) return out
    root.children.forEach { c -> out.addAll(buildVisible(c, expanded)) }
    return out
}

@Composable
private fun DexHeaderRow(
    node: SmaliTreeNode,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (isExpanded) "▾" else "▸",
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.subtitle,
        )
        Spacer(Modifier.width(6.dp))
        Text(node.name, style = MiuixTheme.textStyles.main.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(8.dp))
        Text("(${countFiles(node.children)} 文件)",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.subtitle,
        )
    }
}

@Composable
private fun TreeRow(
    node: SmaliTreeNode,
    expanded: MutableMap<String, Boolean>,
    onOpenFile: (String, String) -> Unit,
) {
    val depth = node.path.count { it == '/' }
    if (node.isDir) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { toggle(expanded, keyOf(node)) }
                .padding(start = (16 + 12 * depth).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (expanded[keyOf(node)] == true) "▾" else "▸",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.width(6.dp))
            Text(node.name, color = MiuixTheme.colorScheme.primary)
        }
    } else {
        Row(
            Modifier.fillMaxWidth()
                .clickable { onOpenFile(node.dex, node.path) }
                .padding(start = (28 + 12 * depth).dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(node.name,
                style = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            Text("›",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
    }
}
