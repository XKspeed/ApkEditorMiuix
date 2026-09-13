package com.apkeditor.miuix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.RealApkDataService
import com.apkeditor.miuix.ui.component.liquid.IosLiquidGlassNavigationBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
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
import top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/** 主页 tab 内的页面路由 */
sealed interface Screen {
    data object Home : Screen
    data class ApkInfo(val uri: String) : Screen
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
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = stack.last()

    LaunchedEffect(Unit) { UiConfigState.load(ctx) }

    CompositionLocalProvider(
        LocalSquircleEnabled provides true,
    ) {
        MainPage(
            stack = stack,
            current = current,
            goBack = { if (stack.size > 1) stack.removeLast() },
            navigate = { stack.add(it) },
            service = svc,
        )

        BackHandler(enabled = stack.size > 1) {
            stack.removeLast()
        }
    }
}

@Composable
private fun MainPage(
    stack: MutableList<Screen>,
    current: Screen,
    goBack: () -> Unit,
    navigate: (Screen) -> Unit,
    service: ApkDataService,
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
                    0 -> HomeContent(current, goBack, navigate, service)
                    1 -> SavedApksPage()
                    2 -> SettingsContent(current, goBack, navigate)
                    3 -> AboutPage(onBack = {})
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

/** 主页 tab 内容 */
@Composable
private fun HomeContent(
    current: Screen,
    goBack: () -> Unit,
    navigate: (Screen) -> Unit,
    service: ApkDataService,
) {
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            // 判断是进入二级页面还是返回
            val isEntering = initialState is Screen.Home && targetState !is Screen.Home
            val isReturning = initialState !is Screen.Home && targetState is Screen.Home

            if (isEntering) {
                // 进入二级页面：新页面从右滑入，旧页面向左移出
                slideInHorizontally(
                    initialOffsetX = { fullWidth: Int -> fullWidth },
                    animationSpec = tween(300),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                    animationSpec = tween(300),
                )
            } else if (isReturning) {
                // 返回：新页面从左滑入，旧页面向右滑出
                slideInHorizontally(
                    initialOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                    animationSpec = tween(300),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth: Int -> fullWidth },
                    animationSpec = tween(300),
                )
            } else {
                // 其他情况（比如从一个二级页面到另一个二级页面）
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }
        },
        label = "homeContentTransition",
    ) { target ->
        when (target) {
            is Screen.Home -> HomePage(
                onPickApk = { uri -> navigate(Screen.ApkInfo(uri)) },
            )
            is Screen.ApkInfo -> ApkInfoPage(
                uri = target.uri,
                service = service,
                onBack = goBack,
                onOpenManifest = { navigate(Screen.XmlEdit("AndroidManifest.xml")) },
                onOpenDex = { name -> navigate(Screen.SmaliTree(listOf(name))) },
                onOpenAllDex = { names -> navigate(Screen.SmaliTree(names)) },
                onOpenArsc = { navigate(Screen.ArscTypes) },
                onOpenRes = { navigate(Screen.XmlFiles) },
            )
            is Screen.SmaliTree -> SmaliTreePage(
                dexNames = target.dexNames,
                service = service,
                onBack = goBack,
                onOpenFile = { dex, path -> navigate(Screen.SmaliEdit(dex, path)) },
            )
            is Screen.SmaliEdit -> TextEditorPage(
                title = target.filePath.substringAfterLast("/"),
                subtitle = target.filePath,
                load = { service.readSmaliFile(target.dexName, target.filePath) },
                save = { text -> service.saveSmaliFile(target.dexName, target.filePath, text) },
                onBack = goBack,
            )
            is Screen.ArscTypes -> ArscTypesPage(
                service = service,
                onBack = goBack,
                onOpenType = { type -> navigate(Screen.ArscEntries(type)) },
            )
            is Screen.ArscEntries -> ArscEntriesPage(
                type = target.type,
                service = service,
                onBack = goBack,
            )
            is Screen.XmlFiles -> XmlFilesPage(
                service = service,
                onBack = goBack,
                onOpenFile = { path -> navigate(Screen.XmlEdit(path)) },
            )
            is Screen.XmlEdit -> TextEditorPage(
                title = target.path.substringAfterLast("/"),
                subtitle = target.path,
                load = { service.readXmlFile(target.path) },
                save = { text -> service.saveXmlFile(target.path, text) },
                onBack = goBack,
            )
            else -> {}
        }
    }
}

/** 设置 tab 内容 */
@Composable
private fun SettingsContent(
    current: Screen,
    goBack: () -> Unit,
    navigate: (Screen) -> Unit,
) {
    AnimatedContent(
        targetState = current,
        transitionSpec = {
            // 判断是进入二级页面还是返回
            val isRootPage = targetState !is Screen.UiSettings && targetState !is Screen.About
            val isEntering = initialState.let { it !is Screen.UiSettings && it !is Screen.About } && !isRootPage
            val isReturning = initialState.let { it is Screen.UiSettings || it is Screen.About } && isRootPage

            if (isEntering) {
                // 进入二级页面：新页面从右滑入，旧页面向左移出
                slideInHorizontally(
                    initialOffsetX = { fullWidth: Int -> fullWidth },
                    animationSpec = tween(300),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                    animationSpec = tween(300),
                )
            } else if (isReturning) {
                // 返回：新页面从左滑入，旧页面向右滑出
                slideInHorizontally(
                    initialOffsetX = { fullWidth: Int -> -fullWidth / 4 },
                    animationSpec = tween(300),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { fullWidth: Int -> fullWidth },
                    animationSpec = tween(300),
                )
            } else {
                // 其他情况
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }
        },
        label = "settingsContentTransition",
    ) { target ->
        when (target) {
            is Screen.UiSettings -> UiSettingsPage(onBack = goBack)
            is Screen.About -> AboutPage(onBack = goBack)
            else -> SettingsPage(
                onOpenUiSettings = { navigate(Screen.UiSettings) },
                onOpenAbout = { navigate(Screen.About) },
            )
        }
    }
}
