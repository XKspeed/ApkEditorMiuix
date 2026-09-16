package com.apkeditor.miuix.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.util.AdaptiveTopAppBar
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.LocalIsWideScreen
import com.apkeditor.miuix.ui.util.blurSource
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import com.apkeditor.miuix.ui.util.rememberBlurState
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
fun HomePage(onPickApk: (String) -> Unit) {
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var selectedApk by remember { mutableStateOf<File?>(null) }
    var needPermission by remember {
        mutableStateOf(!Environment.isExternalStorageManager())
    }

    if (needPermission) {
        MiuixDialog(
            title = "需要存储权限",
            onDismiss = { needPermission = false },
        ) {
            Text(
                "NP 管理器风格主页需要\"所有文件访问\"权限才能浏览手机存储。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            DialogActions(
                confirmText = "去设置",
                onConfirm = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    needPermission = false
                },
                cancelText = "跳过",
                onCancel = { needPermission = false },
            )
        }
        return
    }

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

    val files = remember(currentDir) {
        currentDir.listFiles()?.toList()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()
    }

    Scaffold(
        topBar = {
            val barColor = if (blurActive) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                if (collapsed) MiuixTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent
            }
            val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            BlurredBar(hazeState, blurActive) {
                AdaptiveTopAppBar(
                    title = currentDir.absolutePath,
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
                innerPadding,
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
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            currentDir.parentFile?.let { if (it.canRead()) currentDir = it }
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("..", fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                }
                items(files) { file ->
                    val isApk = file.extension.equals("apk", true)
                    ListItemRow(
                        title = (if (file.isDirectory) "\uD83D\uDCC1 " else "") + file.name,
                        subtitle = if (file.isDirectory) "${file.list()?.size ?: 0} 项"
                        else formatSize(file.length()),
                        trailing = "\u203A",
                        onClick = {
                            when {
                                file.isDirectory -> currentDir = file
                                isApk -> selectedApk = file
                            }
                        },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                }
            }
        }
    }

    selectedApk?.let { apk ->
        MiuixDialog(
            title = apk.name,
            onDismiss = { selectedApk = null },
        ) {
            Text("大小: ${formatSize(apk.length())}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(4.dp))
            Text("路径: ${apk.absolutePath}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            DialogActions(
                confirmText = "反编译",
                onConfirm = {
                    onPickApk(Uri.fromFile(apk).toString())
                    selectedApk = null
                },
                cancelText = "安装",
                onCancel = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    selectedApk = null
                },
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        bytes < 1024 -> "$bytes B"
        kb < 1024 -> String.format("%.1f KB", kb)
        else -> String.format("%.1f MB", kb / 1024.0)
    }
}
