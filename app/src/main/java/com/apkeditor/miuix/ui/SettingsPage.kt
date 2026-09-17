package com.apkeditor.miuix.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ThemeState
import com.apkeditor.miuix.data.ApkCacheManager
import com.apkeditor.miuix.data.OutputConfig
import com.apkeditor.miuix.ui.util.AdaptiveTopAppBar
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.LocalIsWideScreen
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val THEME_OPTIONS = listOf("跟随系统", "浅色", "深色", "莫奈跟随系统", "莫奈浅色", "莫奈深色")

/** 底部导航：设置 */
@Composable
fun SettingsPage(
    padding: PaddingValues,
    onOpenUiSettings: () -> Unit = {},
) {
    val isWideScreen = LocalIsWideScreen.current
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

    val backdrop = rememberBlurBackdrop()
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) { derivedStateOf { backdrop != null && scrollProgress == 1f } }

    Scaffold(
        topBar = {
            val barColor = if (blurActive) {
                Color.Transparent
            } else {
                if (collapsed) MiuixTheme.colorScheme.surface else Color.Transparent
            }
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    title = "设置",
                    showTopAppBar = true,
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box {
            val scrollPadding = pageContentPadding(
                innerPadding,
                padding,
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
                    bottom = innerPadding.calculateBottomPadding(),
                ),
            ) {
                item {
                    SmallTitle("外观")
                }
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        OverlayDropdownPreference(
                            title = "主题",
                            entries = listOf(
                                DropdownEntry(
                                    items = listOf("跟随系统", "浅色", "深色").mapIndexed { index, text ->
                                        DropdownItem(
                                            text = text,
                                            selected = ThemeState.mode == index,
                                            onClick = { ThemeState.mode = index },
                                        )
                                    },
                                ),
                                DropdownEntry(
                                    items = listOf("莫奈跟随系统", "莫奈浅色", "莫奈深色").mapIndexed { index, text ->
                                        DropdownItem(
                                            text = text,
                                            selected = ThemeState.mode == (index + 3),
                                            onClick = { ThemeState.mode = index + 3 },
                                        )
                                    },
                                ),
                            ),
                            collapseOnSelection = true,
                        )
                        ArrowPreference(
                            title = "UI 修改",
                            summary = "底栏模糊 · 悬浮底栏 · 液态玻璃",
                            onClick = onOpenUiSettings,
                        )
                    }
                }
                item {
                    SmallTitle("存储")
                }
                item {
                    CachePreference()
                }
                item {
                    SmallTitle("输出")
                }
                item {
                    OutputDirectoryPreference()
                }
                item {
                    SmallTitle("其他")
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun CachePreference() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheMgr = remember { ApkCacheManager(context) }
    var sizeText by remember { mutableStateOf("—") }
    var clearing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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

@Composable
private fun OutputDirectoryPreference() {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasAccess by remember { mutableStateOf(OutputConfig.hasAllFilesAccess(context)) }
    var dirName by remember { mutableStateOf(OutputConfig.displayName(context)) }

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
            }
            Text(
                "选择输出目录（SAF）",
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier
                    .clickable { picker.launch(null) }
                    .padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
