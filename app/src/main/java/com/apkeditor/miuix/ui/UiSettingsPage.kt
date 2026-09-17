package com.apkeditor.miuix.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ui.component.BackNavigationIcon
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val FLOATING_NAV_STYLE_OPTIONS = listOf("默认", "iOS 风格")

/**
 * 全局 UI 配置（类似 ThemeState）
 */
object UiConfigState {
    var enableBlur by mutableStateOf(true)
    var useFloatingNavigationBar by mutableStateOf(false)
    var blurRadius by mutableStateOf(15f)
    var floatingNavStyle by mutableStateOf(1) // 0=Default, 1=iOS-like

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        enableBlur = prefs.getBoolean("enable_blur", true)
        useFloatingNavigationBar = prefs.getBoolean("use_floating_navbar", false)
        blurRadius = prefs.getFloat("blur_radius", 15f)
        floatingNavStyle = prefs.getInt("floating_nav_style", 1)
    }

    fun save(context: Context) {
        context.getSharedPreferences("ui_config", Context.MODE_PRIVATE).edit().apply {
            putBoolean("enable_blur", enableBlur)
            putBoolean("use_floating_navbar", useFloatingNavigationBar)
            putFloat("blur_radius", blurRadius)
            putInt("floating_nav_style", floatingNavStyle)
        }.apply()
    }
}

/**
 * MIUI X UI 修改设置页
 */
@Composable
fun UiSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current

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
            val titleColor = MiuixTheme.colorScheme.onSurface.copy(
                alpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f),
            )
            BlurredBar(backdrop, blurActive) {
                SmallTopAppBar(
                    title = "UI 修改",
                    navigationIcon = {
                        BackNavigationIcon(onClick = onBack)
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    color = barColor,
                    titleColor = titleColor,
                    defaultWindowInsetsPadding = false,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.layerBackdrop(backdrop)) {
            val scrollPadding = pageContentPadding(
                innerPadding,
                innerPadding,
                false,
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        SwitchPreference(
                            title = "底栏模糊",
                            summary = "开启底部导航栏模糊效果",
                            checked = UiConfigState.enableBlur,
                            onCheckedChange = {
                                UiConfigState.enableBlur = it
                                UiConfigState.save(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // 当启用底栏模糊时，展开模糊半径 slider
                        if (UiConfigState.enableBlur) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "模糊半径",
                                        style = MiuixTheme.textStyles.main,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "%.1f".format(UiConfigState.blurRadius) + "px",
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        style = MiuixTheme.textStyles.subtitle,
                                    )
                                }
                                Slider(
                                    value = UiConfigState.blurRadius,
                                    onValueChange = {
                                        UiConfigState.blurRadius = it
                                        UiConfigState.save(context)
                                    },
                                    valueRange = 0f..30f,
                                )
                            }
                        }

                        SwitchPreference(
                            title = "使用悬浮导航栏",
                            checked = UiConfigState.useFloatingNavigationBar,
                            onCheckedChange = {
                                UiConfigState.useFloatingNavigationBar = it
                                UiConfigState.save(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // 当启用悬浮导航栏时，展开悬浮导航栏风格
                        if (UiConfigState.useFloatingNavigationBar) {
                            OverlayDropdownPreference(
                                title = "悬浮导航栏风格",
                                items = FLOATING_NAV_STYLE_OPTIONS,
                                selectedIndex = UiConfigState.floatingNavStyle,
                                onSelectedIndexChange = {
                                    UiConfigState.floatingNavStyle = it
                                    UiConfigState.save(context)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
