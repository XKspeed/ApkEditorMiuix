package com.apkeditor.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.RealApkDataService
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings

/** 主页 tab 内的页面路由 */
sealed interface Screen {
    data object Home : Screen
    data class ApkInfo(val uri: String) : Screen
    data object DexList : Screen
    data class SmaliTree(val dexNames: List<String>) : Screen
    data class SmaliEdit(val dexName: String, val filePath: String) : Screen
    data object ArscTypes : Screen
    data class ArscEntries(val type: String) : Screen
    data object XmlFiles : Screen
    data class XmlEdit(val path: String) : Screen
}

/** 底部导航 tab */
private enum class BottomTab(val title: String) {
    HOME("主页"),
    SAVED("保存的APK"),
    SETTINGS("设置"),
}

@Composable
fun App(service: ApkDataService? = null) {
    // 默认使用真实反编译引擎（ARSCLib + smali/baksmali + apksig）
    val ctx = LocalContext.current
    val svc: ApkDataService = if (service != null) {
        service
    } else {
        remember { RealApkDataService(ctx) }
    }
    var tab by remember { mutableIntStateOf(0) }
    // 主页 tab 的页面栈（子页面导航）
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = stack.last()

    // 加载 UI 配置
    LaunchedEffect(Unit) { UiConfigState.load(ctx) }

    // 模糊背景层（最外层创建，只实例化一次）
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val blurActive = UiConfigState.enableBlur && isRuntimeShaderSupported()

    val goBack = { if (stack.size > 1) stack.removeLast() }
    val navigate: (Screen) -> Unit = { stack.add(it) }

    BackHandler {
        when {
            tab != 0 -> tab = 0
            stack.size > 1 -> goBack()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (UiConfigState.useFloatingNavigationBar) {
                // 悬浮底栏（放 Scaffold bottomBar 里，官方推荐）
                FloatingNavigationBar(
                    color = if (blurActive) androidx.compose.ui.graphics.Color.Transparent
                            else MiuixTheme.colorScheme.surfaceContainer,
                    modifier = if (blurActive) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                            blurRadius = UiConfigState.blurRadius,
                            colors = top.yukonga.miuix.kmp.blur.BlurDefaults.blurColors(
                                blendColors = listOf(
                                    top.yukonga.miuix.kmp.blur.BlendColorEntry(
                                        color = MiuixTheme.colorScheme.surface.copy(0.6f)
                                    ),
                                ),
                            ),
                        )
                    } else Modifier,
                ) {
                    BottomTab.entries.forEachIndexed { index, item ->
                        FloatingNavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = when (item) {
                                BottomTab.HOME -> MiuixIcons.Home
                                BottomTab.SAVED -> MiuixIcons.Download
                                BottomTab.SETTINGS -> MiuixIcons.Settings
                            },
                            label = item.title,
                        )
                    }
                }
            } else {
                // 普通底栏
                NavigationBar(
                    color = if (blurActive) androidx.compose.ui.graphics.Color.Transparent
                            else MiuixTheme.colorScheme.surface,
                    modifier = if (blurActive) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = androidx.compose.ui.graphics.RectangleShape,
                            blurRadius = UiConfigState.blurRadius,
                            colors = top.yukonga.miuix.kmp.blur.BlurDefaults.blurColors(
                                blendColors = listOf(
                                    top.yukonga.miuix.kmp.blur.BlendColorEntry(
                                        color = MiuixTheme.colorScheme.surface.copy(0.8f)
                                    ),
                                ),
                            ),
                        )
                    } else Modifier,
                ) {
                    BottomTab.entries.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = when (item) {
                                BottomTab.HOME -> MiuixIcons.Home
                                BottomTab.SAVED -> MiuixIcons.Download
                                BottomTab.SETTINGS -> MiuixIcons.Settings
                            },
                            label = item.title,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(if (blurActive) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            when (tab) {
                0 -> HomeContent(current, goBack, navigate, svc)
                1 -> SavedApksScreen()
                2 -> SettingsScreen()
            }
        }
    }
}

/** 主页 tab 内容（含子页面导航栈） */
@Composable
private fun HomeContent(
    current: Screen,
    goBack: () -> Unit,
    navigate: (Screen) -> Unit,
    service: ApkDataService,
) {
    when (current) {
        is Screen.Home -> HomeScreen(
            onPickApk = { uri -> navigate(Screen.ApkInfo(uri)) },
        )

        is Screen.ApkInfo -> ApkInfoScreen(
            uri = current.uri,
            service = service,
            onBack = goBack,
            onOpenManifest = { navigate(Screen.XmlEdit("AndroidManifest.xml")) },
            onOpenDex = { name -> navigate(Screen.SmaliTree(listOf(name))) },
            onOpenAllDex = { names -> navigate(Screen.SmaliTree(names)) },
            onOpenArsc = { navigate(Screen.ArscTypes) },
            onOpenRes = { navigate(Screen.XmlFiles) },
        )

        is Screen.DexList -> DexListScreen(
            service = service,
            onBack = goBack,
            onOpenDex = { name -> navigate(Screen.SmaliTree(listOf(name))) },
        )

        is Screen.SmaliTree -> SmaliTreeScreen(
            dexNames = current.dexNames,
            service = service,
            onBack = goBack,
            onOpenFile = { dex, path -> navigate(Screen.SmaliEdit(dex, path)) },
        )

        is Screen.SmaliEdit -> TextEditorScreen(
            title = current.filePath.substringAfterLast("/"),
            subtitle = current.filePath,
            load = { service.readSmaliFile(current.dexName, current.filePath) },
            save = { text -> service.saveSmaliFile(current.dexName, current.filePath, text) },
            onBack = goBack,
        )

        is Screen.ArscTypes -> ArscTypesScreen(
            service = service,
            onBack = goBack,
            onOpenType = { type -> navigate(Screen.ArscEntries(type)) },
        )

        is Screen.ArscEntries -> ArscEntriesScreen(
            type = current.type,
            service = service,
            onBack = goBack,
        )

        is Screen.XmlFiles -> XmlFilesScreen(
            service = service,
            onBack = goBack,
            onOpenFile = { path -> navigate(Screen.XmlEdit(path)) },
        )

        is Screen.XmlEdit -> TextEditorScreen(
            title = current.path.substringAfterLast("/"),
            subtitle = current.path,
            load = { service.readXmlFile(current.path) },
            save = { text -> service.saveXmlFile(current.path, text) },
            onBack = goBack,
        )
    }
}
