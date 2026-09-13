package com.apkeditor.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.RealApkDataService
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
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
    data object About : Screen
    data object UiSettings : Screen
}

/** 底部导航 tab */
private enum class BottomTab(val title: String) {
    HOME("主页"),
    SAVED("保存的APK"),
    SETTINGS("设置"),
}

@Composable
fun App(service: ApkDataService? = null) {
    val ctx = LocalContext.current
    val svc: ApkDataService = if (service != null) {
        service
    } else {
        remember { RealApkDataService(ctx) }
    }
    var tab by remember { mutableIntStateOf(0) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = stack.last()

    LaunchedEffect(Unit) { UiConfigState.load(ctx) }

    // 完全按照官方示例的 backdrop 写法
    val surfaceColor = MiuixTheme.colorScheme.surface
    val blurActive = UiConfigState.enableBlur && isRuntimeShaderSupported()
    val backdrop: LayerBackdrop? = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val goBack = { if (stack.size > 1) stack.removeLast() }
    val navigate: (Screen) -> Unit = { stack.add(it) }

    BackHandler {
        when {
            tab != 0 -> tab = 0
            stack.size > 1 -> goBack()
        }
    }

    // 导航项列表
    val navigationItems = remember {
        listOf(
            NavigationItem(BottomTab.HOME.title, MiuixIcons.Home),
            NavigationItem(BottomTab.SAVED.title, MiuixIcons.Download),
            NavigationItem(BottomTab.SETTINGS.title, MiuixIcons.Settings),
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        bottomBar = {
            AppNavigationBar(
                navigationItems = navigationItems,
                selectedTab = tab,
                onTabSelected = { tab = it },
                backdrop = backdrop,
                blurActive = blurActive,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurActive && backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .padding(innerPadding),
        ) {
            when (tab) {
                0 -> HomeContent(current, goBack, navigate, svc)
                1 -> SavedApksScreen()
                2 -> SettingsContent(current, goBack, navigate)
            }
        }
    }
}

/** 完全按照官方示例的底栏写法 */
@Composable
private fun AppNavigationBar(
    navigationItems: List<NavigationItem>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    blurActive: Boolean,
) {
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
    val floatingBarColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        AnimatedVisibility(
            visible = !UiConfigState.useFloatingNavigationBar,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (blurActive && backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = androidx.compose.ui.graphics.RectangleShape,
                                blurRadius = UiConfigState.blurRadius,
                                colors = BlurDefaults.blurColors(
                                    blendColors = listOf(
                                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.8f)),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .drawBehind { drawRect(barColor) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                NavigationBar(
                    color = barColor,
                    mode = NavigationBarDisplayMode.IconAndText,
                ) {
                    navigationItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
            }
        }

        if (UiConfigState.useFloatingNavigationBar) {
            Box {
                FloatingNavigationBar(
                    modifier = if (blurActive && backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(28.dp),
                            blurRadius = UiConfigState.blurRadius,
                            colors = BlurDefaults.blurColors(
                                blendColors = listOf(
                                    BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.6f)),
                                ),
                            ),
                        )
                    } else {
                        Modifier
                    },
                    color = floatingBarColor,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    navigationItems.forEachIndexed { index, item ->
                        FloatingNavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
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

        is Screen.About -> AboutScreen(onBack = goBack)
        is Screen.UiSettings -> UiSettingsScreen(onBack = goBack)
    }
}

/** 设置 tab 内容（含子页面导航栈） */
@Composable
private fun SettingsContent(
    current: Screen,
    goBack: () -> Unit,
    navigate: (Screen) -> Unit,
) {
    when (current) {
        is Screen.Home -> SettingsScreen(
            onOpenAbout = { navigate(Screen.About) },
            onOpenUiSettings = { navigate(Screen.UiSettings) },
        )
        is Screen.About -> AboutScreen(onBack = goBack)
        is Screen.UiSettings -> UiSettingsScreen(onBack = goBack)
        else -> SettingsScreen(
            onOpenAbout = { navigate(Screen.About) },
            onOpenUiSettings = { navigate(Screen.UiSettings) },
        )
    }
}
