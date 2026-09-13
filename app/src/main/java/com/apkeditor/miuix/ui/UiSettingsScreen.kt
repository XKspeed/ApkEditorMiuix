package com.apkeditor.miuix.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUI X UI 修改设置页（全中文）：
 * - 底栏模糊（默认开启）
 * - 悬浮底栏（默认关闭）
 * - 液态玻璃（默认开启，不支持时自动隐藏）
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit) {
    // 返回键处理
    androidx.activity.compose.BackHandler { onBack() }

    val context = LocalContext.current
    val config = remember { UiConfigManager(context) }

    // 用 mutableStateOf 包装，修改后自动触发重组
    var bottomBarBlur by remember { mutableStateOf(config.bottomBarBlur) }
    var liquidGlass by remember { mutableStateOf(config.liquidGlass) }
    var floatingBar by remember { mutableStateOf(config.floatingBar) }
    var showNavBar by remember { mutableStateOf(config.showNavBar) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "UI 修改",
                navigationIcon = {
                    Text("返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            item { SmallTitle("模糊与玻璃效果") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchPreference(
                        title = "底栏模糊",
                        summary = "开启底部导航栏模糊效果",
                        checked = bottomBarBlur,
                        onCheckedChange = {
                            bottomBarBlur = it
                            config.bottomBarBlur = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 液态玻璃（RuntimeShader 支持时才显示）
                    if (isRuntimeShaderSupported()) {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = "启用 Squircle 液态玻璃效果",
                            checked = liquidGlass,
                            onCheckedChange = {
                                liquidGlass = it
                                config.liquidGlass = it
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item { SmallTitle("导航栏") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "使用悬浮式导航底栏",
                        checked = floatingBar,
                        onCheckedChange = {
                            floatingBar = it
                            config.floatingBar = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchPreference(
                        title = "显示导航栏",
                        summary = "显示或隐藏底部导航栏",
                        checked = showNavBar,
                        onCheckedChange = {
                            showNavBar = it
                            config.showNavBar = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 全局 UI 配置管理器（SharedPreferences 持久化） */
class UiConfigManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)

    var bottomBarBlur: Boolean
        get() = prefs.getBoolean(KEY_BLUR, true)
        set(value) = prefs.edit().putBoolean(KEY_BLUR, value).apply()

    var liquidGlass: Boolean
        get() = prefs.getBoolean(KEY_SQUIRCLE, true)
        set(value) = prefs.edit().putBoolean(KEY_SQUIRCLE, value).apply()

    var floatingBar: Boolean
        get() = prefs.getBoolean(KEY_FLOATING, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING, value).apply()

    var showNavBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NAVBAR, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NAVBAR, value).apply()

    companion object {
        private const val KEY_BLUR = "bottom_bar_blur"
        private const val KEY_SQUIRCLE = "liquid_glass"
        private const val KEY_FLOATING = "floating_bar"
        private const val KEY_SHOW_NAVBAR = "show_nav_bar"
    }
}
