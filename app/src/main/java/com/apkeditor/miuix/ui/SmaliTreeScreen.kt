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
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.SmaliTreeNode
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
 * NP 管理器风格 Smali 目录树：
 * 未搜索：树形包结构（▸/▾ 折叠）
 * 搜索时：自动切扁平匹配文件列表
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
    var progressText by remember { mutableStateOf("") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dexNames) {
        error = null
        service.listSmaliTree(dexNames) { done, total ->
            progressText = "正在反编译… ($done/$total)"
        }
            .onSuccess { perDex ->
                // 合并所有 DEX 的包名文件夹（NP 风格：相同包名合并）
                val allChildren = perDex.values.flatMap { roots ->
                    roots.flatMap { it.children }
                }
                val merged = mergeTrees(allChildren)
                topNodes = listOf(
                    SmaliTreeNode(
                        name = "smali",
                        path = "merged",
                        isDir = true,
                        dex = "",
                        children = merged,
                    )
                )
                expanded["merged"] = true
            }
            .onFailure { error = it.message ?: "反汇编失败" }
    }

    val isSearching = filter.isNotBlank()
    val flatResults = if (isSearching) {
        topNodes?.let { searchFiles(it, filter) } ?: emptyList()
    } else emptyList()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "Smali",
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
                                    val errs = mutableListOf<String>()
                                    dexNames.forEach { dex ->
                                        service.assembleDex(dex)
                                            .onFailure { errs.add("$dex: ${it.message}") }
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
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            TextField(
                value = filter,
                onValueChange = { filter = it },
                label = "搜索类名",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                if (isSearching) "找到 ${flatResults.size} 个匹配"
                else "共 ${countFiles(topNodes ?: emptyList())} 个类",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            when {
                error != null -> ErrorBox(error!!, onRetry = null)
                topNodes == null -> LoadingBox(progressText.ifBlank { "正在反编译 DEX…（首次打开需要几分钟）" })
                isSearching -> {
                    // 搜索模式：扁平列表
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(flatResults) { (dex, path) ->
                            val className = path.removePrefix("smali/")
                                .substringBeforeLast(".smali").replace("/", ".")
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
                else -> {
                    // 树形模式
                    LazyColumn(Modifier.fillMaxSize()) {
                        topNodes!!.forEach { dexNode ->
                            item(key = dexNode.path) {
                                DexHeaderRow(dexNode, expanded[dexNode.path] == true) {
                                    toggle(expanded, dexNode.path)
                                }
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
                        item { Spacer(Modifier.height(24.dp)) }
                    }
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

private fun toggle(expanded: MutableMap<String, Boolean>, key: String) {
    expanded[key] = (expanded[key] != true)
}

/** 合并多个树的顶层节点，同名文件夹递归合并 */
private fun mergeTrees(nodes: List<SmaliTreeNode>): List<SmaliTreeNode> {
    val map = LinkedHashMap<String, SmaliTreeNode>()
    nodes.forEach { node ->
        val existing = map[node.name]
        if (existing == null) {
            map[node.name] = node
        } else if (existing.isDir && node.isDir) {
            // 同名文件夹：递归合并子节点
            val mergedChildren = mergeTrees(existing.children + node.children)
            map[node.name] = existing.copy(children = mergedChildren)
        }
        // 文件同名的话保留两个（不同 DEX 可能有同名类）
    }
    return map.values.toList()
}

private fun keyOf(node: SmaliTreeNode): String {
    val dex = node.dex.ifEmpty { "smali" }
    return "$dex/${node.path}"
}

private fun countFiles(nodes: List<SmaliTreeNode>): Int = nodes.sumOf { n ->
    if (n.isDir) countFiles(n.children) else 1
}

private fun searchFiles(nodes: List<SmaliTreeNode>, kw: String): List<Pair<String, String>> {
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
        Text(node.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
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
                modifier = Modifier.weight(1f),
            )
            Text("›",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
    }
}
