package com.apkeditor.miuix.ui.components

import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.styling.color.ResolvableColor
import io.github.rosemoe.sora.lang.styling.line.LineBackground
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import androidx.compose.ui.graphics.toArgb
import com.apkeditor.miuix.ui.EditorLanguages
import com.apkeditor.miuix.ui.component.MethodNavIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop

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

    /** 行标记（如 .method/.end method 红底）的内容签名与已标记行，用于增量维护 */
    var markSig: Long = -1L
    var markedLines: IntArray = IntArray(0)
}

/** 行标记背景色：40% 红（0x66FF0000），整行绘制在文字之下 */
private const val LINE_MARK_BG = 0x66FF0000

/**
 * 把 Miuix 当前配色覆盖到 TextMate 颜色方案的基础键上。
 *
 * - 深浅主题（app-light / app-dark）由 [EditorLanguages.applyTheme] 按 Miuix 解析出的
 *   深浅色选择，语法 token 高亮随之切换；
 * - 底色 / 正文 / 行号 / 分隔线 / 当前行 / 补全窗等基础键直接取 [colors]——
 *   Monet 模式下这就是动态生成的色板，编辑器与 app 主题同源取色。
 *
 * [ed] 为 null（编辑器尚未创建）时只切主题，编辑器创建时会再执行一遍。
 */
private fun applyMiuixColors(ed: CodeEditor?, dark: Boolean, colors: Colors) {
    EditorLanguages.applyTheme(dark)
    if (ed == null) return
    val scheme = EditorLanguages.colorScheme() ?: return
    runCatching {
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, colors.background.toArgb())
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, colors.onBackground.toArgb())
        scheme.setColor(EditorColorScheme.LINE_NUMBER, colors.onSurfaceVariantSummary.toArgb())
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, colors.onSurface.toArgb())
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, colors.background.toArgb())
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, colors.dividerLine.toArgb())
        scheme.setColor(EditorColorScheme.CURRENT_LINE, colors.surface.toArgb())
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, colors.primary.toArgb())
        scheme.setColor(EditorColorScheme.SELECTION_HANDLE, colors.primary.toArgb())
        scheme.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, colors.surface.toArgb())
        scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, colors.onSurface.toArgb())
        scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, colors.onSurfaceVariantSummary.toArgb())
    }.onFailure { it.printStackTrace() }
    ed.colorScheme = scheme
    ed.invalidate()
}

/**
 * 维护 [matcher] 匹配行的整行背景（LineBackground 行样式）。
 *
 * 触发时机：轮询（200ms）驱动，两种情况会真正重建——
 *  1. 文本内容签名变化（编辑器内容变了，行号随之变化）；
 *  2. 签名没变但首行标记丢失（TextMate 分析器在异步分析完成后
 *     整体替换了 Styles 对象，把行样式冲掉了）。
 *
 * 重建方式：快照现有 LineStyles → 擦除全部 LineBackground → 按当前
 * 内容重扫并逐行添加 → finishBuilding() 重新排序（渲染层用二分查找，
 * 乱序会查不到行样式）→ invalidate()。
 *
 * 颜色与匹配都通过 runCatching 兜底：行样式 API 在不同 sora 版本上
 * 签名若有出入，最坏结果是不显示红底，不影响编辑与保存。
 */
private fun ensureLineMarks(ed: CodeEditor, refs: EditorRefs, matcher: (String) -> Boolean) {
    val styles = ed.styles ?: return
    val text = ed.text
    val sig = text.length.toLong() * 1_000_003L + text.lineCount

    val intact = sig == refs.markSig && runCatching {
        refs.markedLines.isEmpty() || styles.lineStyles?.any { ls ->
            ls.line == refs.markedLines[0] && ls.findOne(LineBackground::class.java) != null
        } == true
    }.getOrDefault(false)
    if (intact) return

    runCatching {
        styles.lineStyles?.toList()?.forEach { ls ->
            styles.eraseLineStyle(ls.line, LineBackground::class.java)
        }
    }
    val lineCount = text.lineCount
    val marked = ArrayList<Int>()
    for (i in 0 until lineCount) {
        val line = text.getLine(i)
        if (runCatching { matcher(line.toString()) }.getOrDefault(false)) {
            runCatching {
                styles.addLineStyle(LineBackground(i, ResolvableColor { LINE_MARK_BG }))
            }
            marked.add(i)
        }
    }
    refs.markSig = sig
    refs.markedLines = marked.toIntArray()
    runCatching { styles.finishBuilding() }
    ed.invalidate()
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
    /**
     * 行标记匹配器：匹配整行文本的行加整行背景（红色）。
     * smali 编辑传入 ".method/.end method" 判定，其它编辑器传 null。
     * 标记由 [ensureLineMarks] 在装载与每次内容变化后重建，
     * 文本分析器整体替换 Styles 后也会自动补刷（轮询校验）。
     */
    lineMarks: ((String) -> Boolean)? = null,
    /** method 导航：非空时顶栏返回右侧显示方法列表图标 + More 菜单入口（smali 整文件编辑用） */
    onOpenMethodNav: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val refs = remember { EditorRefs() }

    // 深浅色直接问 Miuix：colorSchemeMode 由 ThemeController 解析（含 Monet 六种模式），
    // 只有 System/MonetSystem 才回退到系统深浅色，绝不自己另推一套。
    val schemeMode = MiuixTheme.colorSchemeMode ?: ColorSchemeMode.System
    val dark = when (schemeMode) {
        ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        else -> isSystemInDarkTheme()
    }
    // 当前解析后的 Miuix 配色；Monet 模式下是动态生成的色板（取色跟随壁纸/种子色）
    val miuixColors = MiuixTheme.colorScheme

    LaunchedEffect(dark, miuixColors.background, miuixColors.onBackground, miuixColors.surface) {
        EditorLanguages.init(ctx)
        applyMiuixColors(refs.editor, dark, miuixColors)
    }

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
                // .method/.end method 整行红底：内容变化或分析器覆盖 Styles 后重建
                val lm = lineMarks
                if (lm != null) runCatching { ensureLineMarks(ed, refs, lm) }
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

    // 主页同款 progressive 顶栏：编辑器内部是 View 滚动，由滚动监听驱动进度与顶栏折叠
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    var editorScrollY by mutableIntStateOf(0)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 与列表页共用同一个顶栏组件，避免各页各自拼 TopAppBar 导致样式漂移
            MiuixTopBar(
                title = if (dirty) "$title *" else title,
                subtitle = subtitle,
                onBack = onBack,
                onSearch = { showSearchBar = !showSearchBar },
                navigationExtras = if (onOpenMethodNav != null) {
                    {
                        IconButton(onClick = onOpenMethodNav) {
                            MethodNavIcon(tint = MiuixTheme.colorScheme.onBackground)
                        }
                    }
                } else {
                    null
                },
                menuItems = buildList {
                    if (onOpenMethodNav != null) {
                        add(DropdownItem(text = "方法列表", onClick = onOpenMethodNav))
                    }
                    add(DropdownItem(text = "查找替换", onClick = { showSearchBar = true }))
                    add(DropdownItem(text = "跳转到行", onClick = { showGoto = true }))
                    add(DropdownItem(text = "撤销", onClick = { runCatching { refs.editor?.undo() } }))
                    add(DropdownItem(text = "重做", onClick = { runCatching { refs.editor?.redo() } }))
                    add(DropdownItem(text = "保存", onClick = { doSave() }))
                    add(DropdownItem(text = "退出", onClick = onBack))
                },
                scrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop,
                scrollProgress = { if (editorScrollY > 0) 1f else 0f },
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
        Box(Modifier.fillMaxSize().padding(innerPadding).then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)) {
            when {
                error != null -> Text(
                    text = "读取失败：${error!!}",
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
                else -> AndroidView(
                    factory = { c ->
                        // 保证语法/主题已装载；颜色方案必须在 setEditorLanguage 之前挂上
                        EditorLanguages.init(c)
                        CodeEditor(c).apply {
                            applyMiuixColors(this, dark, miuixColors)
                            if (language != null) setEditorLanguage(language)
                            isWordwrap = false
                            typefaceText = Typeface.MONOSPACE
                            setLineNumberEnabled(true)
                            refs.editor = this
                            // View 滚动 → 驱动 Compose 顶栏：上滑(sy 增大)收起、回滑展开，
                            // 语义与主页 nestedScroll 手势一致（onPreScroll 收起 / onPostScroll 展开）
                            setOnScrollChangeListener { _, _, sy, _, _ ->
                                val dy = editorScrollY - sy
                                editorScrollY = sy
                                val conn = topAppBarScrollBehavior.nestedScrollConnection
                                when {
                                    dy < 0 -> conn.onPreScroll(Offset(0f, dy.toFloat()), NestedScrollSource.UserInput)
                                    dy > 0 -> conn.onPostScroll(Offset.Zero, Offset(0f, dy.toFloat()), NestedScrollSource.UserInput)
                                }
                            }
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
