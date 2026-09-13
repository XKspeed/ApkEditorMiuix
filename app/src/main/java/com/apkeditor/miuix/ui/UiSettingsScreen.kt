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
 * 全局 UI 配置（类似 ThemeState）
 */
object UiConfigState {
    var enableBlur by mutableStateOf(true)
    var enableSquircle by mutableStateOf(true)
    var showNavigationBar by mutableStateOf(true)
    var useFloatingNavigationBar by mutableStateOf(false)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences("ui_config", Context.MODE_PRIVATE)
        enableBlur = prefs.getBoolean("enable_blur", true)
        enableSquircle = prefs.getBoolean("enable_squircle", true)
        showNavigationBar = prefs.getBoolean("show_navbar", true)
        useFloatingNavigationBar = prefs.getBoolean("use_floating_navbar", false)
    }

    fun save(context: Context) {
        context.getSharedPreferences("ui_config", Context.MODE_PRIVATE).edit().apply {
            putBoolean("enable_blur", enableBlur)
            putBoolean("enable_squircle", enableSquircle)
            putBoolean("show_navbar", showNavigationBar)
            putBoolean("use_floating_navbar", useFloatingNavigationBar)
        }.apply()
    }
}

/**
 * MIUI X UI 修改设置页
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit) {
    // 返回键处理
    androidx.activity.compose.BackHandler { onBack() }

    val context = LocalContext.current

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
                        checked = UiConfigState.enableBlur,
                        onCheckedChange = {
                            UiConfigState.enableBlur = it
                            UiConfigState.save(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (isRuntimeShaderSupported()) {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = "启用 Squircle 液态玻璃效果",
                            checked = UiConfigState.enableSquircle,
                            onCheckedChange = {
                                UiConfigState.enableSquircle = it
                                UiConfigState.save(context)
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
                        title = "显示导航栏",
                        summary = "显示或隐藏底部导航栏",
                        checked = UiConfigState.showNavigationBar,
                        onCheckedChange = {
                            UiConfigState.showNavigationBar = it
                            UiConfigState.save(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "使用悬浮式导航底栏",
                        checked = UiConfigState.useFloatingNavigationBar,
                        onCheckedChange = {
                            UiConfigState.useFloatingNavigationBar = it
                            UiConfigState.save(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
