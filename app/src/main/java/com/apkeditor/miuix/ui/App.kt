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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.RealApkDataService
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 页面路由（miuix-nav） */
@Serializable
sealed interface Screen : NavKey {
    @Serializable data object Home : Screen
    @Serializable data class ApkInfo(val uri: String) : Screen
    @Serializable data object DexList : Screen
    @Serializable data class SmaliTree(val dexNames: List<String>) : Screen
    @Serializable data class SmaliEdit(val dexName: String, val filePath: String) : Screen
    @Serializable data object ArscTypes : Screen
    @Serializable data class ArscEntries(val type: String) : Screen
    @Serializable data object XmlFiles : Screen
    @Serializable data class XmlEdit(val path: String) : Screen
    @Serializable data object About : Screen
    @Serializable data object UiSettings : Screen
}

/** 底部导航 tab */
private enum class BottomTab(val title: String) {
    HOME("主页"),
    SAVED("保存的APK"),
    SETTINGS("设置"),
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator found!") }

@Composable
fun App(service: ApkDataService? = null) {
    val ctx = LocalContext.current
    val svc: ApkDataService = if (service != null) {
        service
    } else {
        remember { RealApkDataService(ctx) }
    }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { UiConfigState.load(ctx) }

    // miuix-nav 返回栈
    val backStack = rememberNavBackStack<Screen>(Screen.Home)
    val navigator = remember { Navigator(backStack) }

    CompositionLocalProvider(
        LocalSquircleEnabled provides UiConfigState.enableSquircle,
        LocalNavigator provides navigator,
    ) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        val blurActive = UiConfigState.enableBlur && isRuntimeShaderSupported()
        val backdrop: LayerBackdrop? = rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }

        BackHandler {
            when {
                tab != 0 -> tab = 0
                backStack.size > 1 -> navigator.pop()
            }
        }

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
            topBar = {
                AnimatedVisibility(visible = UiConfigState.showTopAppBar) {
                    TopAppBar(title = "ApkEditor Miuix")
                }
            },
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
                    0 -> HomeNavDisplay(backStack, svc)
                    1 -> SavedApksScreen()
                    2 -> SettingsNavDisplay(backStack)
                }
            }
        }
    }
}

/** 主页 tab 的 NavDisplay */
@Composable
private fun HomeNavDisplay(backStack: NavBackStack<Screen>, service: ApkDataService) {
    NavDisplay(
        backStack = backStack,
    ) { entry ->
        when (val screen = entry.key) {
            is Screen.Home -> HomeScreen(
                onPickApk = { uri -> backStack.push(Screen.ApkInfo(uri)) },
            )
            is Screen.ApkInfo -> ApkInfoScreen(
                uri = screen.uri,
                service = service,
                onBack = { backStack.pop() },
                onOpenManifest = { backStack.push(Screen.XmlEdit("AndroidManifest.xml")) },
                onOpenDex = { name -> backStack.push(Screen.SmaliTree(listOf(name))) },
                onOpenAllDex = { names -> backStack.push(Screen.SmaliTree(names)) },
                onOpenArsc = { backStack.push(Screen.ArscTypes) },
                onOpenRes = { backStack.push(Screen.XmlFiles) },
            )
            is Screen.DexList -> DexListScreen(
                service = service,
                onBack = { backStack.pop() },
                onOpenDex = { name -> backStack.push(Screen.SmaliTree(listOf(name))) },
            )
            is Screen.SmaliTree -> SmaliTreeScreen(
                dexNames = screen.dexNames,
                service = service,
                onBack = { backStack.pop() },
                onOpenFile = { dex, path -> backStack.push(Screen.SmaliEdit(dex, path)) },
            )
            is Screen.SmaliEdit -> TextEditorScreen(
                title = screen.filePath.substringAfterLast("/"),
                subtitle = screen.filePath,
                load = { service.readSmaliFile(screen.dexName, screen.filePath) },
                save = { text -> service.saveSmaliFile(screen.dexName, screen.filePath, text) },
                onBack = { backStack.pop() },
            )
            is Screen.ArscTypes -> ArscTypesScreen(
                service = service,
                onBack = { backStack.pop() },
                onOpenType = { type -> backStack.push(Screen.ArscEntries(type)) },
            )
            is Screen.ArscEntries -> ArscEntriesScreen(
                type = screen.type,
                service = service,
                onBack = { backStack.pop() },
            )
            is Screen.XmlFiles -> XmlFilesScreen(
                service = service,
                onBack = { backStack.pop() },
                onOpenFile = { path -> backStack.push(Screen.XmlEdit(path)) },
            )
            is Screen.XmlEdit -> TextEditorScreen(
                title = screen.path.substringAfterLast("/"),
                subtitle = screen.path,
                load = { service.readXmlFile(screen.path) },
                save = { text -> service.saveXmlFile(screen.path, text) },
                onBack = { backStack.pop() },
            )
            is Screen.About -> AboutScreen(onBack = { backStack.pop() })
            is Screen.UiSettings -> UiSettingsScreen(onBack = { backStack.pop() })
        }
    }
}

/** 设置 tab 的 NavDisplay */
@Composable
private fun SettingsNavDisplay(backStack: NavBackStack<Screen>) {
    NavDisplay(
        backStack = backStack,
    ) { entry ->
        when (val screen = entry.key) {
            is Screen.Home -> SettingsScreen(
                onOpenAbout = { backStack.push(Screen.About) },
                onOpenUiSettings = { backStack.push(Screen.UiSettings) },
            )
            is Screen.About -> AboutScreen(onBack = { backStack.pop() })
            is Screen.UiSettings -> UiSettingsScreen(onBack = { backStack.pop() })
            else -> SettingsScreen(
                onOpenAbout = { backStack.push(Screen.About) },
                onOpenUiSettings = { backStack.push(Screen.UiSettings) },
            )
        }
    }
}

/** 简单的 Navigator 包装 */
class Navigator(val backStack: NavBackStack<Screen>) {
    fun push(route: Screen) {
        backStack.push(route)
    }
    fun pop() {
        if (backStack.size > 1) backStack.pop()
    }
}

/** 完全照搬官方示例的底栏写法 */
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
    val floatingBarShape = RoundedCornerShape(28.dp)

    AnimatedVisibility(
        visible = UiConfigState.showNavigationBar,
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
                            shape = floatingBarShape,
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
