package com.apkeditor.miuix.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ThemeState
import com.apkeditor.miuix.data.ApkCacheManager
import com.apkeditor.miuix.data.OutputConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val THEME_OPTIONS = listOf("跟随系统", "浅色", "深色")

/** 底部导航：设置 */
@Composable
fun SettingsScreen(
    blurEnabled: Boolean = true,
    onBlurChange: (Boolean) -> Unit = {},
    floatingNavigationBar: Boolean = false,
    onFloatingNavigationBarChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = "设置") },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item {
                Text(
                    "外观",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    // 主题切换
                    OverlayDropdownPreference(
                        title = "主题",
                        items = THEME_OPTIONS,
                        selectedIndex = ThemeState.mode,
                        onSelectedIndexChange = { ThemeState.mode = it },
                    )
                    // 模糊效果开关
                    SwitchPreference(
                        title = "启用模糊效果",
                        checked = blurEnabled,
                        onCheckedChange = onBlurChange,
                    )
                    // 悬浮底栏开关
                    SwitchPreference(
                        title = "悬浮底栏",
                        checked = floatingNavigationBar,
                        onCheckedChange = onFloatingNavigationBarChange,
                    )
                }
            }
            item {
                Text(
                    "存储",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            item {
                CachePreference()
            }
            item {
                Text(
                    "输出",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            item {
                OutputDirectoryPreference()
            }
            item {
                Text(
                    "关于",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("ApkEditor·Miuix", style = MiuixTheme.textStyles.main)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "版本 0.1（可靠重打包版）\n仿 MT 管理器的 APK 编辑工具\nUI：Miuix（HyperOS 风格）\n最低系统：Android 15",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 缓存管理：显示缓存占用 + 清除缓存（磁盘反编译产物 + 内存树） */
@Composable
private fun CachePreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheMgr = remember { ApkCacheManager(context) }
    var sizeText by remember { mutableStateOf("—") }
    var clearing by remember { mutableStateOf(false) }

    // 进入设置页时异步算一次缓存大小
    androidx.compose.runtime.LaunchedEffect(Unit) {
        sizeText = withContext(Dispatchers.IO) { cacheMgr.totalSizeText() }
    }

    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("清除缓存", style = MiuixTheme.textStyles.main, modifier = Modifier.padding(vertical = 10.dp))
            Text(
                "当前缓存占用：$sizeText\n清除反编译产生的 smali / 解码产物，下次打开同一 APK 会重新反编译。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (clearing) "正在清除…" else "立即清除缓存",
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier
                    .clickable(enabled = !clearing) {
                        clearing = true
                        scope.launch {
                            withContext(Dispatchers.IO) { cacheMgr.clearAll() }
                            sizeText = withContext(Dispatchers.IO) { cacheMgr.totalSizeText() }
                            clearing = false
                        }
                    }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** 输出目录配置项：全部文件权限 / SAF 选择 / 恢复默认 */
@Composable
private fun OutputDirectoryPreference() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasAccess by remember { mutableStateOf(OutputConfig.hasAllFilesAccess(context)) }
    var dirName by remember { mutableStateOf(OutputConfig.displayName(context)) }

    // 从系统设置页返回时刷新
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAccess = OutputConfig.hasAllFilesAccess(context)
                dirName = OutputConfig.displayName(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            OutputConfig.setTreeUri(uri.toString(), "已选择目录（SAF）")
            dirName = OutputConfig.displayName(context)
        }
    }

    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("输出目录", style = MiuixTheme.textStyles.main, modifier = Modifier.padding(vertical = 10.dp))
            Text(
                "回编译后的 APK 输出到：$dirName",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(8.dp))

            if (!hasAccess) {
                Text(
                    "授予所有文件访问权限",
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.main,
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                                context.startActivity(intent)
                            }
                        }
                        .padding(vertical = 8.dp),
                )
                HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            }
            Text(
                "选择目录（SAF，备选）",
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier
                    .clickable { picker.launch(null) }
                    .padding(vertical = 8.dp),
            )
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            Text(
                "恢复默认",
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier
                    .clickable {
                        OutputConfig.setTreeUri(null, null)
                        dirName = OutputConfig.displayName(context)
                    }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
