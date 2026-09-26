package com.apkeditor.miuix.ui.components

import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
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

/**
 * 编辑器内部可变引用集合。
 *
 * 刻意不用 Compose state：这些值会从 AndroidView 的 factory 回调和 sora 的事件回调里写入，
 * 若写成 state 会在组合过程中被赋值，触发 Compose 的「组合期间修改 state」问题。
 * 用普通对象承载引用，UI 真正需要重绘的少量状态（脏标记、光标行、匹配数）单独用 state。
 */
private class EditorRefs {
    var editor: CodeEditor? = null
    var textApplied = false

    /** 上一次真正下发的查找条件，用来判断「继续找下一个」还是「重新搜」。 */
    var lastQuery: String? = null
    var lastType = -1
    var lastCaseInsensitive = false
}

/**
 * 通用文本编辑器壳（miuix 风格 + sora-editor）。
 * smali / AXML 等所有需要「文本显示 + 高亮 + 查找替换 + 保存」的页面复用此组件。
 *
 * 顶栏：
 *  - 左上角：返回（BackNavigationIcon，统一格式）
 *  - 右上角：Search 图标（切换底部查找/替换条） + More 图标（下拉菜单）
 * 底栏：查找/替换条（默认隐藏），以及常驻状态行（光标行号 + 保存状态）。
 * 正文：sora-editor（语法高亮、行号、撤销重做）。
 *
 * 查找/替换直接对接 sora-editor 的 [EditorSearcher]，不再是占位实现。
 * 注意 EditorSearcher.search 是异步的（内部起线程），且
 * [EditorSearcher.getMatchedPositionCount] / gotoNext / replaceAll 内部都会调
 * checkState()，在没有查询时抛 IllegalStateException
 * —— 因此这些调用前必须先用 hasQuery() 判空，或包在 runCatching 里。
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
    val refs = remember { EditorRefs() }

    // 已加载的原始文本。用「它是否为 null」表达加载完成，省掉一个额外的 state。
    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    var showSearchBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var caseInsensitive by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }

    var showGoto by remember { mutableStateOf(false) }
    var gotoLineInput by remember { mutableStateOf("") }

    var cursorLine by remember { mutableStateOf(1) }
    var matchCount by remember { mutableStateOf(0) }
    var matchIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        load()
            .onSuccess { content = it }
            .onFailure { error = it.message ?: "读取失败" }
    }

    // 轮询循环：状态行的光标行号，以及 sora 异步查找的匹配数/当前序号。
    // 这两个值 sora 都不给回调，轮询是唯一不依赖其内部线程状态的做法。
    LaunchedEffect(Unit) {
        while (true) {
            val ed = refs.editor
            if (ed != null) {
                runCatching { cursorLine = ed.cursor.leftLine + 1 }
                val s = ed.searcher
                if (runCatching { s.hasQuery() }.getOrDefault(false)) {
                    runCatching { matchCount = s.matchedPositionCount }
                    runCatching { matchIndex = s.currentMatchedPositionIndex }
                } else {
                    matchCount = 0
                    matchIndex = -1
                }
            }
            delay(200)
        }
    }

    fun doSave() {
        val ed = refs.editor ?: return
        if (saving || content == null) return
        saving = true
        scope.launch {
            save(ed.text.toString())
                .onSuccess {
                    dirty = false
                    Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show()
                }
                .onFailure { Toast.makeText(ctx, "保存失败：${it.message}", Toast.LENGTH_SHORT).show() }
            saving = false
        }
    }

    /** 查找条件与上次相同时复用已有结果，不同则重新下发 search。 */
    fun withSearcher(onReady: (EditorSearcher) -> Unit) {
        val ed = refs.editor ?: return
        val q = findQuery
        if (q.isEmpty()) {
            Toast.makeText(ctx, "请输入查找内容", Toast.LENGTH_SHORT).show()
            return
        }
        val type = if (useRegex) SearchOptions.TYPE_REGULAR_EXPRESSION else SearchOptions.TYPE_NORMAL
        val s = ed.searcher
        runCatching {
            val reused = s.hasQuery() &&
                q == refs.lastQuery &&
                type == refs.lastType &&
                caseInsensitive == refs.lastCaseInsensitive
            if (reused) {
                onReady(s)
            } else {
                refs.lastQuery = q
                refs.lastType = type
                refs.lastCaseInsensitive = caseInsensitive
                s.search(q, SearchOptions(type, caseInsensitive))
                // search 异步执行，等结果就绪再跳转，否则 gotoNext 会因结果未生成而空转。
                scope.launch {
                    repeat(25) {
                        delay(60)
                        val c = runCatching { if (s.hasQuery()) s.matchedPositionCount else 0 }
                            .getOrDefault(0)
                        if (c > 0) {
                            runCatching { onReady(s) }
                            return@launch
                        }
                    }
                }
            }
        }.onFailure {
            Toast.makeText(ctx, "查找失败：${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun doReplaceOne() {
        val ed = refs.editor ?: return
        val s = ed.searcher
        if (!runCatching { s.hasQuery() }.getOrDefault(false)) {
            Toast.makeText(ctx, "请先查找", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { s.replaceCurrentMatch(replaceQuery) }
            .onSuccess { dirty = true }
            .onFailure { Toast.makeText(ctx, "替换失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    fun doReplaceAll() {
        val ed = refs.editor ?: return
        val s = ed.searcher
        if (!runCatching { s.hasQuery() }.getOrDefault(false)) {
            Toast.makeText(ctx, "请先查找", Toast.LENGTH_SHORT).show()
            return
        }
        // replaceAll 自带进度对话框与结果校验
        runCatching { s.replaceAll(replaceQuery) }
            .onSuccess { dirty = true }
            .onFailure { Toast.makeText(ctx, "全部替换失败：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    val moreEntry = DropdownEntry(
        items = listOf(
            DropdownItem(text = "查找替换", onClick = { showSearchBar = true }),
            DropdownItem(text = "跳转到行", onClick = { showGoto = true }),
            DropdownItem(text = "撤销", onClick = { runCatching { refs.editor?.undo() } }),
            DropdownItem(text = "重做", onClick = { runCatching { refs.editor?.redo() } }),
            DropdownItem(text = "保存", onClick = { doSave() }),
            DropdownItem(text = "退出", onClick = onBack),
        ),
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = if (dirty) "$title *" else title,
                subtitle = subtitle,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = "查找替换",
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
            Column {
                if (showSearchBar) {
                    EditorSearchBar(
                        findQuery = findQuery,
                        onFindChange = { findQuery = it },
                        replaceQuery = replaceQuery,
                        onReplaceChange = { replaceQuery = it },
                        caseInsensitive = caseInsensitive,
                        onToggleCase = { caseInsensitive = !caseInsensitive },
                        useRegex = useRegex,
                        onToggleRegex = { useRegex = !useRegex },
                        matchCount = matchCount,
                        matchIndex = matchIndex,
                        onFindPrev = { withSearcher { it.gotoPrevious() } },
                        onFindNext = { withSearcher { it.gotoNext() } },
                        onReplaceOne = { doReplaceOne() },
                        onReplaceAll = { doReplaceAll() },
                        onClose = { showSearchBar = false },
                    )
                }
                EditorStatusBar(
                    cursorLine = cursorLine,
                    dirty = dirty,
                    saving = saving,
                    loaded = content != null,
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
                            typefaceText = Typeface.MONOSPACE
                            setLineNumberEnabled(true)
                            refs.editor = this
                            subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                                // 装载初始内容也会触发该事件，那不算用户改动
                                if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) dirty = true
                            }
                        }
                    },
                    update = { ed ->
                        // 文本可能先于编辑器就绪（小文件），也可能后到（大文件），
                        // 放在 update 里两种时序都能覆盖。
                        val c = content
                        if (c != null && !refs.textApplied) {
                            ed.setText(c)
                            refs.textApplied = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showGoto) {
        MiuixDialog(title = "跳转到行", onDismiss = { showGoto = false }) {
            TextField(
                value = gotoLineInput,
                onValueChange = { gotoLineInput = it },
                label = "行号",
                modifier = Modifier.fillMaxWidth(),
            )
            DialogActions(
                confirmText = "跳转",
                onConfirm = {
                    val line = gotoLineInput.toIntOrNull()
                    if (line != null && line > 0) refs.editor?.jumpToLine(line - 1)
                    showGoto = false
                },
                onCancel = { showGoto = false },
            )
        }
    }
}

/** 底部查找/替换条（默认隐藏） */
@Composable
private fun EditorSearchBar(
    findQuery: String,
    onFindChange: (String) -> Unit,
    replaceQuery: String,
    onReplaceChange: (String) -> Unit,
    caseInsensitive: Boolean,
    onToggleCase: () -> Unit,
    useRegex: Boolean,
    onToggleRegex: () -> Unit,
    matchCount: Int,
    matchIndex: Int,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
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
                    text = "查找替换",
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (matchCount > 0 && matchIndex >= 0) {
                        "${matchIndex + 1}/$matchCount"
                    } else {
                        "$matchCount 处"
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote2,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "关闭",
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
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
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (caseInsensitive) "Aa 忽略大小写" else "Aa 区分大小写",
                    color = if (caseInsensitive) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.clickable(onClick = onToggleCase),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (useRegex) ".* 正则" else ".* 普通",
                    color = if (useRegex) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.clickable(onClick = onToggleRegex),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onFindPrev, minWidth = 56.dp) { Text("上一个") }
                Button(onClick = onFindNext, minWidth = 56.dp) { Text("下一个") }
                Button(onClick = onReplaceOne, minWidth = 56.dp) { Text("替换") }
                Button(onClick = onReplaceAll, minWidth = 72.dp) { Text("全部替换") }
            }
        }
    }
}

/** 常驻状态行：光标行号 + 保存状态 */
@Composable
private fun EditorStatusBar(
    cursorLine: Int,
    dirty: Boolean,
    saving: Boolean,
    loaded: Boolean,
) {
    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "行 $cursorLine",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote2,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = when {
                !loaded -> "加载中…"
                saving -> "保存中…"
                dirty -> "有未保存修改"
                else -> "已保存"
            },
            color = if (dirty) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}
