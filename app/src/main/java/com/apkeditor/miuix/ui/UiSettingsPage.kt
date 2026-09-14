package com.apkeditor.miuix.ui

import android.content.Context
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "UI 修改",
                navigationIcon = {
                    Text(
                        text = "返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
                scrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior(),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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
