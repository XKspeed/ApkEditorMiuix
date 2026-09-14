package com.apkeditor.miuix.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.RealApkDataService
import com.apkeditor.miuix.ui.component.liquid.IosLiquidGlassNavigationBar
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
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
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/** 底部导航 tab */
private enum class BottomTab(val title: String) {
    HOME("主页"),
    SAVED("保存的APK"),
    SETTINGS("设置"),
    ABOUT("关于"),
}

@Composable
fun App(service: ApkDataService? = null) {
    val ctx = LocalContext.current
    val svc: ApkDataService = if (service != null) {
        service
    } else {
        remember { RealApkDataService(ctx) }
    }

    LaunchedEffect(Unit) { UiConfigState.load(ctx) }

    CompositionLocalProvider(
        LocalSquircleEnabled provides true,
    ) {
        // 全局 NavDisplay：第一个是主页面，其他是二级页面
        val backStack = rememberNavBackStack<Route>(Route.Main)

        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
            transition = NavTransitions.MiuixDefault,
            effects = NavDisplayEffects(
                enableCornerClip = true,
                cornerClipRadius = 32.dp,
                cornerClipMode = NavCornerClipMode.All,
                dimAmount = 0.5f,
                blockInputDuringTransition = false,
                backdropColor = MiuixTheme.colorScheme.surface,
            ),
        ) {
            // 主页面：包含 Pager 和底栏
            entry<Route.Main> {
                MainPage(
                    service = svc,
                    navigate = { backStack.add(it) },
                )
            }

            // 二级页面：全屏显示，自动隐藏底栏
            entry<Route.ApkInfo> { route ->
                ApkInfoPage(
                    uri = route.apkPath,
                    service = svc,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenManifest = { backStack.add(Route.XmlEdit("AndroidManifest.xml")) },
                    onOpenDex = { name -> backStack.add(Route.SmaliTree(route.apkPath, listOf(name))) },
                    onOpenAllDex = { names -> backStack.add(Route.SmaliTree(route.apkPath, names)) },
                    onOpenArsc = { backStack.add(Route.ArscTypes(route.apkPath)) },
                    onOpenRes = { backStack.add(Route.XmlFiles) },
                )
            }
            entry<Route.SmaliTree> { route ->
                SmaliTreePage(
                    dexNames = route.dexNames,
                    service = svc,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenFile = { dex, path -> backStack.add(Route.SmaliEdit(route.apkPath, dex, path)) },
                )
            }
            entry<Route.SmaliEdit> { route ->
                TextEditorPage(
                    title = route.filePath.substringAfterLast("/"),
                    subtitle = route.filePath,
                    load = { svc.readSmaliFile(route.dexName, route.filePath) },
                    save = { text -> svc.saveSmaliFile(route.dexName, route.filePath, text) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Route.ArscTypes> { route ->
                ArscTypesPage(
                    service = svc,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenType = { type -> backStack.add(Route.ArscEntries(route.apkPath, type)) },
                )
            }
            entry<Route.ArscEntries> { route ->
                ArscEntriesPage(
                    type = route.type,
                    service = svc,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Route.XmlFiles> {
                XmlFilesPage(
                    service = svc,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenFile = { path -> backStack.add(Route.XmlEdit(path)) },
                )
            }
            entry<Route.XmlEdit> { route ->
                TextEditorPage(
                    title = route.path.substringAfterLast("/"),
                    subtitle = route.path,
                    load = { svc.readXmlFile(route.path) },
                    save = { text -> svc.saveXmlFile(route.path, text) },
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<Route.UiSettings> {
                UiSettingsPage(onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.ThirdPartyLicenses> {
                ThirdPartyLicensesPage(onBack = { backStack.removeLastOrNull() })
            }
        }
    }
}

/** 主页面：包含 Pager 和底栏 */
@Composable
private fun MainPage(
    service: ApkDataService,
    navigate: (Route) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    var selectedIndex by remember { mutableIntStateOf(0) }
    var isNavigating by remember { mutableStateOf(false) }
    var navJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    val items = listOf(
        BottomTab.HOME.title,
        BottomTab.SAVED.title,
        BottomTab.SETTINGS.title,
        BottomTab.ABOUT.title,
    )
    val icons = listOf(
        MiuixIcons.Home,
        MiuixIcons.Download,
        MiuixIcons.Settings,
        MiuixIcons.Info,
    )

    LaunchedEffect(pagerState.currentPage) {
        if (!isNavigating && selectedIndex != pagerState.currentPage) {
            selectedIndex = pagerState.currentPage
        }
    }

    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val blurActive = UiConfigState.enableBlur && isRuntimeShaderSupported()

    // 底栏模式：0=普通，1=悬浮，2=液态玻璃
    val navBarMode = when {
        !UiConfigState.useFloatingNavigationBar -> 0
        UiConfigState.floatingNavStyle == 1 -> 2  // iOS-like = 液态玻璃
        else -> 1
    }

    val onItemSelected: (Int) -> Unit = select@{ index ->
        if (index == selectedIndex) return@select
        navJob?.cancel()
        selectedIndex = index
        isNavigating = true
        navJob = scope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val distance = abs(index - pagerState.currentPage).coerceAtLeast(2)
                    val duration = 100 * distance + 100
                    val layoutInfo = pagerState.layoutInfo
                    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                    val currentDistanceInPages = index - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val scrollPixels = currentDistanceInPages * pageSize
                    var previousValue = 0f
                    androidx.compose.animation.core.animate(
                        initialValue = 0f,
                        targetValue = scrollPixels,
                        animationSpec = androidx.compose.animation.core.tween(
                            easing = androidx.compose.animation.core.EaseInOut,
                            durationMillis = duration,
                        ),
                    ) { currentValue, _ ->
                        previousValue += scrollBy(currentValue - previousValue)
                    }
                }
                if (pagerState.currentPage != index) {
                    pagerState.scrollToPage(index)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != index) {
                        selectedIndex = pagerState.currentPage
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                BottomNavigationBar(
                    mode = navBarMode,
                    items = items,
                    icons = icons,
                    selectedIndex = selectedIndex,
                    backdrop = backdrop,
                    blurActive = blurActive,
                    onItemSelected = onItemSelected,
                )
            }
        },
    ) { globalPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(Modifier.layerBackdrop(backdrop))
                .background(surfaceColor),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> HomePage(
                        onPickApk = { uri -> navigate(Route.ApkInfo(uri)) },
                    )
                    1 -> SavedApksPage()
                    2 -> SettingsPage(
                        onOpenUiSettings = { navigate(Route.UiSettings) },
                    )
                    3 -> AboutPage(
                        onBack = {},
                        onOpenThirdPartyLicenses = { navigate(Route.ThirdPartyLicenses) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(
    mode: Int,
    items: List<String>,
    icons: List<androidx.compose.ui.graphics.vector.ImageVector>,
    selectedIndex: Int,
    backdrop: LayerBackdrop?,
    blurActive: Boolean,
    onItemSelected: (Int) -> Unit,
) {
    when (mode) {
        2 -> {
            // 液态玻璃底栏
            val navigationItems = remember(items, icons) {
                List(items.size) { i -> NavigationItem(items[i], icons[i]) }
            }
            val liquidModifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = 440.dp)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                IosLiquidGlassNavigationBar(
                    modifier = liquidModifier,
                    items = navigationItems,
                    selectedIndex = selectedIndex,
                    onItemClick = { index ->
                        onItemSelected(index)
                    },
                    backdrop = backdrop,
                    isBlurActive = blurActive,
                )
            }
        }
        1 -> {
            // 悬浮底栏
            val floatingBarColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer
            val floatingBarShape = RoundedCornerShape(28.dp)
            FloatingNavigationBar(
                modifier = if (blurActive && backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = floatingBarShape,
                        blurRadius = UiConfigState.blurRadius,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(
                                BlendColorEntry(color = MiuixTheme.colorScheme.surfaceContainer.copy(0.4f)),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
                color = floatingBarColor,
            ) {
                items.forEachIndexed { index, label ->
                    FloatingNavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onItemSelected(index) },
                        icon = icons[index],
                        label = label,
                        enabled = true,
                    )
                }
            }
        }
        else -> {
            // 普通底栏
            val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .then(
                        if (blurActive && backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = RectangleShape,
                                blurRadius = UiConfigState.blurRadius,
                                colors = BlurDefaults.blurColors(
                                    blendColors = listOf(
                                        BlendColorEntry(color = MiuixTheme.colorScheme.surface.copy(0.5f)),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        }
                    )
                    .background(barColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                NavigationBar(
                    color = barColor,
                ) {
                    items.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { onItemSelected(index) },
                            icon = icons[index],
                            label = label,
                            enabled = true,
                        )
                    }
                }
            }
        }
    }
}
