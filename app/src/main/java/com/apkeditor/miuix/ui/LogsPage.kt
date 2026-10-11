package com.apkeditor.miuix.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.apkeditor.miuix.AppLog
import com.apkeditor.miuix.ui.component.BackNavigationIcon
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.LocalIsWideScreen
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行日志页：查看、分享、清空应用日志。
 *
 * debug 与 release 都可用 —— 线上用户遇到问题时，让他把日志发过来即可定位。
 */
@Composable
fun LogsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isWideScreen = LocalIsWideScreen.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()

    var level by remember { mutableStateOf(AppLog.currentLevel()) }
    var logText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        loading = true
        scope.launch {
            logText = withContext(Dispatchers.IO) { AppLog.readAll() }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            BlurredBar(backdrop, backdrop != null) {
                SmallTopAppBar(
                    title = "运行日志",
                    navigationIcon = { BackNavigationIcon(onClick = onBack) },
                    scrollBehavior = topAppBarScrollBehavior,
                    color = MiuixTheme.colorScheme.surface,
                    defaultWindowInsetsPadding = false,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val scrollPadding = pageContentPadding(
                innerPadding,
                PaddingValues(0.dp),
                isWideScreen,
                extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
                extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = scrollPadding.calculateTopPadding(),
                    start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = scrollPadding.calculateBottomPadding(),
                ),
            ) {
                // ---------- 日志级别 ----------
                item { SmallTitle("日志级别") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        OverlayDropdownPreference(
                            title = "记录级别",
                            summary = when (level) {
                                AppLog.Level.VERBOSE -> "记录每一步操作与参数，最利于排查（日志增长快）"
                                AppLog.Level.BRIEF -> "只记关键节点与全部错误（推荐）"
                                AppLog.Level.OFF -> "不记录任何日志，出问题将无从排查"
                            },
                            items = AppLog.Level.entries.map { it.label },
                            selectedIndex = level.id,
                            modifier = Modifier.fillMaxWidth(),
                            onSelectedIndexChange = { idx ->
                                val nl = AppLog.Level.fromId(idx)
                                level = nl
                                AppLog.setLevel(nl)
                            },
                        )
                    }
                }

                // ---------- 快捷分享 ----------
                item { SmallTitle("分享日志") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "遇到问题时把日志发给我，能直接定位原因。",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.subtitle,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { shareLog(context, "txt", scope) { reload() } },
                                    modifier = Modifier.weight(1f),
                                ) { Text("分享文件") }

                                Button(
                                    onClick = { shareLogText(context, logText) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("分享文本") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { copyLog(context, logText) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("复制全部") }

                                Button(
                                    onClick = {
                                        AppLog.clear()
                                        Toast.makeText(context, "已清空日志", Toast.LENGTH_SHORT).show()
                                        reload()
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("清空") }
                            }
                        }
                    }
                }

                // ---------- 其他操作 ----------
                item { SmallTitle("文件") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            ArrowPreference(
                                title = "刷新",
                                summary = "重新读取当前日志内容",
                                onClick = { reload() },
                            )
                            ArrowPreference(
                                title = "日志文件位置",
                                summary = AppLog.logFile().absolutePath,
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        AppLog.logFile().absolutePath,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                },
                            )
                        }
                    }
                }

                // ---------- 日志预览 ----------
                item { SmallTitle("日志内容（" + (if (loading) "读取中…" else "${logText.length} 字符") + "）") }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = logText.ifBlank { "(暂无日志)" },
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.subtitle,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ---------------- 分享 / 复制 ----------------

/** 把日志写成文件后走系统分享（可发给任意 App）。 */
private fun shareLog(
    context: Context,
    ext: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onDone: () -> Unit,
) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val out = File(context.cacheDir, "log/ApkEditorMiuix-log-" + stamp + "." + ext)
                AppLog.exportTo(out)
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    out,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "ApkEditorMiuix 运行日志")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享日志"))
                true
            }.getOrElse { e ->
                AppLog.e("LogsPage", "分享日志失败", e)
                false
            }
        }
        if (!ok) Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
        onDone()
    }
}

/** 直接把日志文本塞进分享意图（部分接收方对纯文本更友好）。 */
private fun shareLogText(context: Context, text: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ApkEditorMiuix 运行日志")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "分享日志"))
    }.onFailure {
        AppLog.e("LogsPage", "分享文本失败", it)
        Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
    }
}

private fun copyLog(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("ApkEditorMiuix 日志", text))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }.onFailure {
        AppLog.e("LogsPage", "复制日志失败", it)
        Toast.makeText(context, "复制失败", Toast.LENGTH_SHORT).show()
    }
}
