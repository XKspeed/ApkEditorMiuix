package com.apkeditor.miuix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * MIUI X UI 修改设置页：
 * - 底栏模糊（默认开启）
 * - 悬浮底栏（默认关闭）
 * - 液态玻璃（默认开启）
 * - 滑块调节参数
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit) {
    // 开关状态
    var bottomBarBlur by remember { mutableStateOf(UiSettingsState.bottomBarBlur) }
    var floatingBar by remember { mutableStateOf(UiSettingsState.floatingBar) }
    var liquidGlass by remember { mutableStateOf(UiSettingsState.liquidGlass) }

    // 滑块参数
    var blurRadius by remember { mutableFloatStateOf(UiSettingsState.blurRadius) }
    var floatingElevation by remember { mutableFloatStateOf(UiSettingsState.floatingElevation) }
    var glassIntensity by remember { mutableFloatStateOf(UiSettingsState.glassIntensity) }

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
            item {
                Text(
                    "开关",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SwitchRow(
                        title = "底栏模糊",
                        subtitle = "底部导航栏背景毛玻璃效果",
                        checked = bottomBarBlur,
                        onCheckedChange = {
                            bottomBarBlur = it
                            UiSettingsState.bottomBarBlur = it
                        },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    SwitchRow(
                        title = "悬浮底栏",
                        subtitle = "底部导航栏悬浮在内容上方（MIUI X 自带）",
                        checked = floatingBar,
                        onCheckedChange = {
                            floatingBar = it
                            UiSettingsState.floatingBar = it
                        },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    SwitchRow(
                        title = "液态玻璃",
                        subtitle = "RuntimeShader 液态玻璃动态效果（MIUI X 自带）",
                        checked = liquidGlass,
                        onCheckedChange = {
                            liquidGlass = it
                            UiSettingsState.liquidGlass = it
                        },
                    )
                }
            }
            item {
                Text(
                    "参数调节",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    SliderRow(
                        title = "模糊半径",
                        value = blurRadius,
                        valueRange = 0f..30f,
                        unit = "px",
                        onValueChange = {
                            blurRadius = it
                            UiSettingsState.blurRadius = it
                        },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    SliderRow(
                        title = "悬浮高度",
                        value = floatingElevation,
                        valueRange = 0f..24f,
                        unit = "dp",
                        onValueChange = {
                            floatingElevation = it
                            UiSettingsState.floatingElevation = it
                        },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    SliderRow(
                        title = "玻璃强度",
                        value = glassIntensity,
                        valueRange = 0f..100f,
                        unit = "%",
                        onValueChange = {
                            glassIntensity = it
                            UiSettingsState.glassIntensity = it
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.main)
            Text(
                subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MiuixTheme.textStyles.main, modifier = Modifier.weight(1f))
            Text(
                "${"%.1f".format(value)}$unit",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

/** 全局 UI 设置状态（类似 ThemeState） */
object UiSettingsState {
    var bottomBarBlur: Boolean
        get() = _bottomBarBlur
        set(value) { _bottomBarBlur = value }

    var floatingBar: Boolean
        get() = _floatingBar
        set(value) { _floatingBar = value }

    var liquidGlass: Boolean
        get() = _liquidGlass
        set(value) { _liquidGlass = value }

    var blurRadius: Float
        get() = _blurRadius
        set(value) { _blurRadius = value }

    var floatingElevation: Float
        get() = _floatingElevation
        set(value) { _floatingElevation = value }

    var glassIntensity: Float
        get() = _glassIntensity
        set(value) { _glassIntensity = value }

    private var _bottomBarBlur: Boolean = true      // 底栏模糊，默认开
    private var _floatingBar: Boolean = false      // 悬浮底栏，默认关
    private var _liquidGlass: Boolean = true       // 液态玻璃，默认开
    private var _blurRadius: Float = 10f           // 模糊半径，默认 10px
    private var _floatingElevation: Float = 8f     // 悬浮高度，默认 8dp
    private var _glassIntensity: Float = 50f      // 玻璃强度，默认 50%
}
