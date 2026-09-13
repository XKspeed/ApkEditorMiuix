package com.apkeditor.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * MIUI X UI 修改设置页：
 * 用 miuix 官方 SwitchPreference 组件
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit) {
    // 返回键处理
    androidx.activity.compose.BackHandler { onBack() }

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
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchPreference(
                        title = "Enable Blur Effect",
                        checked = UiSettingsState.enableBlur,
                        onCheckedChange = { UiSettingsState.enableBlur = it },
                    )
                    // 液态玻璃（RuntimeShader 支持时才显示）
                    if (isRuntimeShaderSupported()) {
                        SwitchPreference(
                            title = "Enable Squircle Shapes",
                            checked = UiSettingsState.enableSquircle,
                            onCheckedChange = { UiSettingsState.enableSquircle = it },
                        )
                    }
                }
            }
            item { SmallTitle("导航栏") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchPreference(
                        title = "Use FloatingNavigationBar",
                        summary = "悬浮底栏",
                        checked = UiSettingsState.useFloatingNavigationBar,
                        onCheckedChange = { UiSettingsState.useFloatingNavigationBar = it },
                    )
                    SwitchPreference(
                        title = "Show NavigationBar",
                        checked = UiSettingsState.showNavigationBar,
                        onCheckedChange = { UiSettingsState.showNavigationBar = it },
                    )
                }
            }
        }
    }
}

/** 全局 UI 设置状态（参考 miuix 官方 AppState） */
object UiSettingsState {
    var enableBlur: Boolean
        get() = _enableBlur
        set(value) { _enableBlur = value }

    var enableSquircle: Boolean
        get() = _enableSquircle
        set(value) { _enableSquircle = value }

    var useFloatingNavigationBar: Boolean
        get() = _useFloatingNavigationBar
        set(value) { _useFloatingNavigationBar = value }

    var showNavigationBar: Boolean
        get() = _showNavigationBar
        set(value) { _showNavigationBar = value }

    private var _enableBlur: Boolean = true        // 模糊效果，默认开
    private var _enableSquircle: Boolean = true   // 液态玻璃，默认开
    private var _useFloatingNavigationBar: Boolean = false  // 悬浮底栏，默认关
    private var _showNavigationBar: Boolean = true         // 显示导航栏，默认开
}
