package com.apkeditor.miuix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 全局主题状态（对齐官方 example/ui/Theme.kt 的 AppTheme 参数体系）。
 * 0 = 跟随系统，1 = 浅色，2 = 深色，3 = 莫奈跟随系统，4 = 莫奈浅色，5 = 莫奈深色。
 */
object ThemeState {
    var mode by mutableIntStateOf(0)
    var seedIndex by mutableIntStateOf(0)
    var paletteStyle by mutableIntStateOf(0)
    var colorSpec by mutableIntStateOf(0)

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2
    const val MODE_MONET_SYSTEM = 3
    const val MODE_MONET_LIGHT = 4
    const val MODE_MONET_DARK = 5
}
