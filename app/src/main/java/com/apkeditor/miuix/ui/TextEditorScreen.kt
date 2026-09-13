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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.TextField
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用代码/文本编辑器：Smali 与 XML 编辑共用。
 * 支持查找 / 替换 / 跳转行 / 保存。
 */
@Composable
fun TextEditorScreen(
    title: String,
    subtitle: String,
    load: suspend () -> Result<String>,
    save: suspend (String) -> Result<Unit>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val editorState = remember { TextFieldState() }

    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savedTip by remember { mutableStateOf<String?>(null) }

    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var findPos by remember { mutableIntStateOf(0) }
    var findCount by remember { mutableIntStateOf(0) }

    var showGoto by remember { mutableStateOf(false) }
    var gotoLine by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        load().onSuccess {
            editorState.edit { replace(0, length, it) }
            dirty = false
            loaded = true
        }.onFailure { error = it.message ?: "读取失败" }
    }

    LaunchedEffect(editorState) {
        snapshotFlow { editorState.text.toString() }
            .collect { if (loaded && it.isNotEmpty()) dirty = true }
    }

    fun refreshFind() {
        val text = editorState.text.toString()
        if (findQuery.isEmpty()) {
            findCount = 0
            findPos = 0
            return
        }
        findCount = text.windowed(findQuery.length).count { it == findQuery }
        findPos = 0
    }

    fun findNext() {
        val text = editorState.text.toString()
        if (findQuery.isEmpty()) return
        val start = text.indexOf(findQuery, findPos)
        var idx = if (start >= 0) start else text.indexOf(findQuery)
        if (idx < 0) idx = 0
        findPos = idx
        editorState.edit { selection = TextRange(idx, idx + findQuery.length) }
    }

    fun findPrev() {
        val text = editorState.text.toString()
        if (findQuery.isEmpty()) return
        val base = findPos - 1
        val idx = text.lastIndexOf(findQuery, base)
        if (idx >= 0) {
            findPos = idx
            editorState.edit { selection = TextRange(idx, idx + findQuery.length) }
        }
    }

    fun replaceAll() {
        if (findQuery.isEmpty()) return
        val text = editorState.text.toString()
        val newText = text.replace(findQuery, replaceQuery)
        editorState.edit { replace(0, length, newText) }
        dirty = true
        refreshFind()
    }

    fun jumpToLine() {
        val line = gotoLine.toIntOrNull() ?: return
        val text = editorState.text.toString()
        val lines = text.split("\n")
        if (line in 1..lines.size) {
            var offset = 0
            for (i in 0 until line - 1) offset += lines[i].length + 1
            editorState.edit { selection = TextRange(offset) }
        }
        showGoto = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = {
                    Text("返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
                actions = {
                    Text("查找",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            .clickable { showFind = !showFind },
                    )
                    Text("跳转",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            .clickable { showGoto = true },
                    )
                    Text(if (dirty) "保存*" else "保存",
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = if (dirty) androidx.compose.ui.text.font.FontWeight.Bold else null,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(enabled = loaded && !saving) {
                                saving = true
                                scope.launch {
                                    save(editorState.text.toString())
                                        .onSuccess {
                                            dirty = false
                                            savedTip = "已保存"
                                        }
                                        .onFailure { savedTip = "保存失败：${it.message}" }
                                    saving = false
                                }
                            },
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                error != null -> ErrorBox(error!!, onRetry = null)
                !loaded -> LoadingBox("加载中…")
                else -> {
                    if (showFind) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            TextField(
                                value = findQuery,
                                onValueChange = { findQuery = it; refreshFind() },
                                label = "查找",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(6.dp))
                            TextField(
                                value = replaceQuery,
                                onValueChange = { replaceQuery = it },
                                label = "替换为",
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (findCount == 0) "未找到匹配" else "匹配 $findCount 处",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.footnote2,
                                    modifier = Modifier.weight(1f),
                                )
                                Button(onClick = { findPrev() }, minWidth = 64.dp) { Text("上一个") }
                                Spacer(Modifier.width(6.dp))
                                Button(onClick = { findNext() }, minWidth = 64.dp) { Text("下一个") }
                                Spacer(Modifier.width(6.dp))
                                Button(onClick = { replaceAll() }, minWidth = 72.dp) { Text("全部替换") }
                            }
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        }
                    }

                    MiuixTextField(
                        state = editorState,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 4.dp),
                        label = "",
                        useLabelAsPlaceholder = false,
                        textStyle = MiuixTheme.textStyles.main.copy(fontFamily = FontFamily.Monospace),
                    )

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "行 ${editorState.text.toString().count { it == '\n' } + 1}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            savedTip ?: if (dirty) "有未保存修改" else "已保存",
                            color = if (dirty) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    }
                }
            }
        }
    }

    if (showGoto) {
        MiuixDialog(title = "跳转到行", onDismiss = { showGoto = false }) {
            TextField(value = gotoLine, onValueChange = { gotoLine = it },
                label = "行号", modifier = Modifier.fillMaxWidth())
            DialogActions(
                confirmText = "跳转",
                onConfirm = { jumpToLine() },
                onCancel = { showGoto = false },
            )
        }
    }

    if (savedTip != null) {
        MiuixDialog(title = "保存", onDismiss = { savedTip = null }) {
            Text(savedTip!!, color = MiuixTheme.colorScheme.onSurface)
            DialogActions(confirmText = "好的",
                onConfirm = { savedTip = null }, cancelText = "", onCancel = { savedTip = null })
        }
    }
}
