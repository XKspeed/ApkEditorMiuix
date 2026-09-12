package com.apkeditor.miuix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * 全局主题模式状态（参考 miuix 官方示例 ui/Theme.kt 的实现方式）。
 * 0 = 跟随系统，1 = 浅色，2 = 深色。
 * 设置页修改此状态，MainActivity 据此重建 ThemeController。
 */
object ThemeState {
    var mode by mutableIntStateOf(0)

    const val MODE_SYSTEM = 0
    const val MODE_LIGHT = 1
    const val MODE_DARK = 2
}
