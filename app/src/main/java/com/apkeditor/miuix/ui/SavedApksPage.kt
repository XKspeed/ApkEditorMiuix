package com.apkeditor.miuix.ui

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.apkeditor.miuix.SavedApkInfo
import com.apkeditor.miuix.SavedApkStore
import com.apkeditor.miuix.ui.util.AdaptiveTopAppBar
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.LocalIsWideScreen
import com.apkeditor.miuix.ui.util.blurSource
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import com.apkeditor.miuix.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 底部导航：保存的 APK */
@Composable
fun SavedApksPage(padding: PaddingValues) {
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    var records by remember { mutableStateOf(SavedApkStore.list()) }

    LaunchedEffect(Unit) { records = SavedApkStore.list() }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> 0f
            }
        }
    }

    val hazeState = rememberBlurState()
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(hazeState) { derivedStateOf { hazeState != null && scrollProgress == 1f } }

    Scaffold(
        topBar = {
            val barColor = if (blurActive) {
                Color.Transparent
            } else {
                if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
            }
            BlurredBar(hazeState, blurActive) {
                AdaptiveTopAppBar(
                    title = "保存的 APK",
                    showTopAppBar = true,
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.blurSource(hazeState)) {
            val scrollPadding = pageContentPadding(
                innerPadding,
                padding,
                isWideScreen,
                extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
                extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
            )

            if (records.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "还没有保存的 APK",
                            style = MiuixTheme.textStyles.title2,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "在主页完成「打包并签名」后，产物会显示在这里",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
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
                    item {
                        Text(
                            "共 ${records.size} 个 APK",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                    items(records, key = { it.uri }) { record ->
                        Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Column {
                                SavedApkRow(record = record, onShare = {
                                    shareApk(context, record)
                                }, onDelete = {
                                    SavedApkStore.remove(record.uri)
                                    records = SavedApkStore.list()
                                })
                                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SavedApkRow(record: SavedApkInfo, onShare: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(record.name, style = MiuixTheme.textStyles.main)
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatSize(record.size)} · ${formatTime(record.time)}",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.subtitle,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onShare) { Text("分享") }
            Button(onClick = onDelete) { Text("删除") }
        }
    }
}

private fun formatSize(size: Long): String = when {
    size <= 0 -> "大小未知"
    size < 1024 * 1024 -> "%.1f KB".format(size / 1024f)
    else -> "%.1f MB".format(size / 1024f / 1024f)
}

private fun formatTime(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))

private fun shareApk(context: android.content.Context, record: SavedApkInfo) {
    try {
        val uri = when {
            record.uri.startsWith("file:") || record.uri.startsWith("/") -> {
                val f = java.io.File(android.net.Uri.parse(record.uri).path ?: return)
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
            }
            else -> android.net.Uri.parse(record.uri)
        }
        val type = context.contentResolver.getType(uri) ?: "application/vnd.android.package-archive"
        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 ${record.name}"))
    } catch (e: Exception) {
    }
}
