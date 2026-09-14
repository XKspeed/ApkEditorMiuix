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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.TextField
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MT/NP 同款代码编辑器（sora-editor）：
 * 语法高亮、行号、搜索替换、跳转行。
 */
@Composable
fun TextEditorPage(
    title: String,
    subtitle: String,
    load: suspend () -> Result<String>,
    save: suspend (String) -> Result<Unit>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<CodeEditor?>(null) }

    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savedTip by remember { mutableStateOf<String?>(null) }

    var showFind by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showGoto by remember { mutableStateOf(false) }
    var gotoLine by remember { mutableStateOf("") }

    LaunchedEffect(editor) {
        if (editor == null) return@LaunchedEffect
        load().onSuccess { content ->
            editor?.setText(content)
            dirty = false
            loaded = true
        }.onFailure { error = it.message ?: "读取失败" }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = {
                    BackNavigationIcon(onClick = onBack)
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
                                    editor?.text?.toString()?.let { content ->
                                        save(content).onSuccess {
                                            dirty = false
                                            savedTip = "已保存"
                                        }.onFailure { savedTip = "保存失败：${it.message}" }
                                    }
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
                            TextField(value = findQuery, onValueChange = { findQuery = it },
                                label = "查找", modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(6.dp))
                            TextField(value = replaceQuery, onValueChange = { replaceQuery = it },
                                label = "替换为", modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = { /* sora-editor 搜索后续加 */ },
                                    minWidth = 64.dp) { Text("查找") }
                                Spacer(Modifier.width(6.dp))
                                Button(onClick = { dirty = true }, minWidth = 64.dp) { Text("替换") }
                                Spacer(Modifier.width(6.dp))
                                Button(onClick = { dirty = true }, minWidth = 72.dp) { Text("全部替换") }
                            }
                            Spacer(Modifier.height(4.dp))
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                        }
                    }

                    // sora-editor 代码编辑器（MT/NP 同款）
                    AndroidView(
                        factory = { ctx ->
                            CodeEditor(ctx).apply {
                                setEditorLanguage(JavaLanguage())
                                isWordwrap = false
                                typefaceText = android.graphics.Typeface.MONOSPACE
                                editor = this
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )

                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("行 ${editor?.cursor?.leftLine?.plus(1) ?: 1}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(savedTip ?: if (dirty) "有未保存修改" else "已保存",
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
                onConfirm = {
                    val line = gotoLine.toIntOrNull() ?: return@DialogActions
                    editor?.jumpToLine(line - 1)
                    showGoto = false
                },
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
