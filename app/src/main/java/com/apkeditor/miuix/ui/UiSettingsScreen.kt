package com.apkeditor.miuix.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BLUR_STYLE_OPTIONS = listOf("高斯模糊", "渐进模糊")
private val FLOATING_NAV_STYLE_OPTIONS = listOf("默认", "iOS 风格")

/**
 * 全局 UI 配置（类似 ThemeState）
 */
object UiConfigState {
    var enableBlur by remember { mutableStateOf(false) }
    var enableSquircle by remember { mutableStateOf(true) }
    var useFloatingNavigationBar by remember { mutableStateOf(false) }
    var blurRadius by remember { mutableStateOf(10f) }
    var floatingElevation by remember { mutableStateOf(8f) }

    // 复刻官方示例的 UI 配置
    var enableScrollEndHaptic by remember { mutableStateOf(true) }
    var enablePageUserScroll by remember { mutableStateOf(true) }
    var showTopAppBar by remember { mutableStateOf(true) }
    var showNavigationBar by remember { mutableStateOf(true) }
    var enableCornerClip by remember { mutableStateOf(true) }
    var enableDim by remember { mutableStateOf(true) }
    var blurStyle by remember { mutableStateOf(1) } // 0=Gaussian, 1=Progressive
    var floatingNavStyle by remember { mutableStateOf(1) } // 0=Default, 1=iOS-like

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        enableBlur = prefs.getBoolean("enable_blur", true)
        enableSquircle = prefs.getBoolean("enable_squircle", true)
        useFloatingNavigationBar = prefs.getBoolean("use_floating_navbar", false)
        blurRadius = prefs.getFloat("blur_radius", 10f)
        floatingElevation = prefs.getFloat("floating_elevation", 8f)
        enableScrollEndHaptic = prefs.getBoolean("enable_scroll_end_haptic", true)
        enablePageUserScroll = prefs.getBoolean("enable_page_user_scroll", true)
        showTopAppBar = prefs.getBoolean("show_top_app_bar", true)
        showNavigationBar = prefs.getBoolean("show_navigation_bar", true)
        enableCornerClip = prefs.getBoolean("enable_corner_clip", true)
        enableDim = prefs.getBoolean("enable_dim", true)
        blurStyle = prefs.getInt("blur_style", 1)
        floatingNavStyle = prefs.getInt("floating_nav_style", 1)
    }

    fun save(context: Context) {
        context.getSharedPreferences("ui_config", Context.MODE_PRIVATE).edit().apply {
            putBoolean("enable_blur", enableBlur)
            putBoolean("enable_squircle", enableSquircle)
            putBoolean("use_floating_navbar", useFloatingNavigationBar)
            putFloat("blur_radius", blurRadius)
            putFloat("floating_elevation", floatingElevation)
            putBoolean("enable_scroll_end_haptic", enableScrollEndHaptic)
            putBoolean("enable_page_user_scroll", enablePageUserScroll)
            putBoolean("show_top_app_bar", showTopAppBar)
            putBoolean("show_navigation_bar", showNavigationBar)
            putBoolean("enable_corner_clip", enableCornerClip)
            putBoolean("enable_dim", enableDim)
            putInt("blur_style", blurStyle)
            putInt("floating_nav_style", floatingNavStyle)
        }.apply()
    }
}

/**
 * MIUI X UI 修改设置页（完全照搬官方示例 SettingsPage 样式）
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "UI 设置",
                navigationIcon = {
                    Text(
                        "返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    SwitchPreference(
                        title = "启用液态玻璃",
                        checked = UiConfigState.enableSquircle,
                        onCheckedChange = {
                            UiConfigState.enableSquircle = it
                            UiConfigState.save(context)
                        },
                    )
                    SwitchPreference(
                        title = "启用模糊效果",
                        checked = UiConfigState.enableBlur,
                        onCheckedChange = {
                            UiConfigState.enableBlur = it
                            UiConfigState.save(context)
                        },
                    )
                    AnimatedVisibility(visible = UiConfigState.showTopAppBar && UiConfigState.enableBlur && isRuntimeShaderSupported()) {
                        OverlayDropdownPreference(
                            title = "顶栏模糊风格",
                            items = BLUR_STYLE_OPTIONS,
                            selectedIndex = UiConfigState.blurStyle,
                            onSelectedIndexChange = {
                                UiConfigState.blurStyle = it
                                UiConfigState.save(context)
                            },
                        )
                    }
                    SwitchPreference(
                        title = "滚动结束震动",
                        checked = UiConfigState.enableScrollEndHaptic,
                        onCheckedChange = {
                            UiConfigState.enableScrollEndHaptic = it
                            UiConfigState.save(context)
                        },
                    )
                    SwitchPreference(
                        title = "页面用户滚动",
                        checked = UiConfigState.enablePageUserScroll,
                        onCheckedChange = {
                            UiConfigState.enablePageUserScroll = it
                            UiConfigState.save(context)
                        },
                    )
                    SwitchPreference(
                        title = "显示顶栏",
                        checked = UiConfigState.showTopAppBar,
                        onCheckedChange = {
                            UiConfigState.showTopAppBar = it
                            UiConfigState.save(context)
                        },
                    )
                    SwitchPreference(
                        title = "显示导航栏",
                        checked = UiConfigState.showNavigationBar,
                        onCheckedChange = {
                            UiConfigState.showNavigationBar = it
                            UiConfigState.save(context)
                        },
                    )
                    AnimatedVisibility(visible = UiConfigState.showNavigationBar) {
                        SwitchPreference(
                            title = "使用悬浮导航栏",
                            checked = UiConfigState.useFloatingNavigationBar,
                            onCheckedChange = {
                                UiConfigState.useFloatingNavigationBar = it
                                UiConfigState.save(context)
                            },
                        )
                    }
                    AnimatedVisibility(visible = UiConfigState.showNavigationBar && UiConfigState.useFloatingNavigationBar) {
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
                    SwitchPreference(
                        title = "启用圆角裁剪",
                        checked = UiConfigState.enableCornerClip,
                        onCheckedChange = {
                            UiConfigState.enableCornerClip = it
                            UiConfigState.save(context)
                        },
                    )
                    SwitchPreference(
                        title = "启用变暗效果",
                        checked = UiConfigState.enableDim,
                        onCheckedChange = {
                            UiConfigState.enableDim = it
                            UiConfigState.save(context)
                        },
                    )
                }
            }
            item { SmallTitle("参数调节") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("模糊半径", style = MiuixTheme.textStyles.main, modifier = Modifier.weight(1f))
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
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("悬浮高度", style = MiuixTheme.textStyles.main, modifier = Modifier.weight(1f))
                            Text(
                                "%.1f".format(UiConfigState.floatingElevation) + "dp",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.subtitle,
                            )
                        }
                        Slider(
                            value = UiConfigState.floatingElevation,
                            onValueChange = {
                                UiConfigState.floatingElevation = it
                                UiConfigState.save(context)
                            },
                            valueRange = 0f..24f,
                        )
                    }
                }
            }
        }
    }
}
