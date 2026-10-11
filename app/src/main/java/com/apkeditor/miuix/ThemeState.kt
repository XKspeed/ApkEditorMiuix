package com.apkeditor.miuix

import android.content.Context
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

    // ---- 持久化：设置类状态各自独立成文件，重启自动恢复用户选择 ----
    // 主题类 → shared_prefs/theme_config.xml（模糊/悬浮底栏在 ui_config，输出/签名在 output_config）
    private const val PREFS = "theme_config"
    private const val KEY_MODE = "mode"
    private const val KEY_SEED = "seed_index"
    private const val KEY_PALETTE = "palette_style"
    private const val KEY_SPEC = "color_spec"

    /** 启动时读取用户上次的主题选择（MainActivity.onCreate 中、setContent 之前调用）。 */
    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = p.getInt(KEY_MODE, MODE_SYSTEM)
        seedIndex = p.getInt(KEY_SEED, 0)
        paletteStyle = p.getInt(KEY_PALETTE, 0)
        colorSpec = p.getInt(KEY_SPEC, 0)
    }

    /** 每次改动后落盘；写的是 SharedPreferences 文件（独立于 ui_config / output_config）。 */
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MODE, mode)
            .putInt(KEY_SEED, seedIndex)
            .putInt(KEY_PALETTE, paletteStyle)
            .putInt(KEY_SPEC, colorSpec)
            .apply()
    }
}
