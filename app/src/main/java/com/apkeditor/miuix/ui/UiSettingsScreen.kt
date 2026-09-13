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
 * MIUI X UI 配置（参考官方示例 AppState 模式）
 */
data class UiConfig(
    val enableBlur: Boolean = true,          // 模糊效果
    val enableSquircle: Boolean = true,      // 液态玻璃
    val showNavigationBar: Boolean = true,   // 显示导航栏
    val useFloatingNavigationBar: Boolean = false, // 悬浮底栏
)

/**
 * MIUI X UI 修改设置页（参考官方示例 SettingsPage）
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit) {
    // 返回键处理
    androidx.activity.compose.BackHandler { onBack() }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ui_config", Context.MODE_PRIVATE) }

    // 用 mutableStateOf 包装整个配置，修改时 copy 新配置触发重组
    var config by remember {
        mutableStateOf(
            UiConfig(
                enableBlur = prefs.getBoolean("enable_blur", true),
                enableSquircle = prefs.getBoolean("enable_squircle", true),
                showNavigationBar = prefs.getBoolean("show_navbar", true),
                useFloatingNavigationBar = prefs.getBoolean("use_floating_navbar", false),
            )
        )
    }

    fun update(transform: (UiConfig) -> UiConfig) {
        config = transform(config)
        // 持久化
        prefs.edit().apply {
            putBoolean("enable_blur", config.enableBlur)
            putBoolean("enable_squircle", config.enableSquircle)
            putBoolean("show_navbar", config.showNavigationBar)
            putBoolean("use_floating_navbar", config.useFloatingNavigationBar)
        }.apply()
    }

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
                        checked = config.enableBlur,
                        onCheckedChange = { update { c -> c.copy(enableBlur = it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 液态玻璃（RuntimeShader 支持时才显示）
                    if (isRuntimeShaderSupported()) {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = "启用 Squircle 液态玻璃效果",
                            checked = config.enableSquircle,
                            onCheckedChange = { update { c -> c.copy(enableSquircle = it) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item { SmallTitle("导航栏") }
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchPreference(
                        title = "显示导航栏",
                        summary = "显示或隐藏底部导航栏",
                        checked = config.showNavigationBar,
                        onCheckedChange = { update { c -> c.copy(showNavigationBar = it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "使用悬浮式导航底栏",
                        checked = config.useFloatingNavigationBar,
                        onCheckedChange = { update { c -> c.copy(useFloatingNavigationBar = it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
