package com.apkeditor.miuix.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.apkeditor.miuix.ui.component.BackNavigationIcon
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.widget.Toast

/**
 * 通用文本编辑器壳（miuix 风格）。
 * AXML / DEX(smali) / ARSC 等所有需要“文本显示 + 高亮 + 保存”的页面复用此组件。
 *
 * 顶栏：
 *  - 左上角：返回（BackNavigationIcon，统一格式）
 *  - 右上角：Search 图标（切换底部搜索条） + More 图标（下拉菜单：搜索/保存/退出）
 * 底栏：默认不显示，点搜索后浮出搜索条。
 * 正文：sora-editor（语法高亮）。
 */
@Composable
fun TextEditorScaffold(
    title: String,
    subtitle: String,
    language: Language?,
    load: suspend () -> Result<String>,
    save: suspend (String) -> Result<Unit>,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    var showSearchBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    fun doSave() {
        if (saving || !loaded) return
        saving = true
        scope.launch {
            val text = editorRef?.text?.toString() ?: ""
            save(text)
                .onSuccess {
                    Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(ctx, "保存失败：${it.message}", Toast.LENGTH_SHORT).show() }
            saving = false
        }
    }

    // 用 Unit 作 key，触发一次加载；editor 在 AndroidView.factory 里创建后
    // 通过 editorRef 引用写入文本（避免依赖 editor 状态导致永久 loading）。
    LaunchedEffect(Unit) {
        load()
            .onSuccess { content ->
                editorRef?.setText(content)
                loaded = true
            }
            .onFailure { error = it.message ?: "读取失败" }
    }

    val moreEntry = DropdownEntry(
        items = listOf(
            DropdownItem(text = "搜索", onClick = { showSearchBar = true }),
            DropdownItem(text = "保存", onClick = { doSave() }),
            DropdownItem(text = "退出", onClick = onBack),
        ),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = "搜索",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    OverlayIconDropdownMenu(entry = moreEntry) {
                        Icon(
                            imageVector = MiuixIcons.More,
                            contentDescription = "更多",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (showSearchBar) {
                EditorSearchBar(
                    findQuery = findQuery,
                    onFindChange = { findQuery = it },
                    replaceQuery = replaceQuery,
                    onReplaceChange = { replaceQuery = it },
                    onClose = { showSearchBar = false },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                error != null -> Text(
                    text = "读取失败：${error!!}",
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
                else -> AndroidView(
                    factory = { c ->
                        CodeEditor(c).apply {
                            if (language != null) setEditorLanguage(language)
                            isWordwrap = false
                            typefaceText = android.graphics.Typeface.MONOSPACE
                            editorRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 底部浮出的搜索条（默认隐藏） */
@Composable
private fun EditorSearchBar(
    findQuery: String,
    onFindChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        color = MiuixTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "搜索",
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "关闭",
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable(onClick = onClose),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = findQuery,
                onValueChange = onFindChange,
                label = "查找",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            TextField(
                value = replaceQuery,
                onValueChange = onReplaceChange,
                label = "替换为",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "（搜索/替换逻辑待接 sora-editor 搜索 API）",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
            )
        }
    }
}
