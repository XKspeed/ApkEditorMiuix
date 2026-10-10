package com.apkeditor.miuix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.apkeditor.miuix.ui.App
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        // 崩溃不再闪退：写日志后弹出「错误摘要 + 分享」页（见 CrashHandler/CrashReportActivity）
        CrashHandler.install(this)
        enableEdgeToEdge()
        SavedApkStore.init(this)
        // 主题选择持久化：先读用户上次的选择，再进 setContent(否则重启永远回到跟随系统)
        ThemeState.load(this)
        com.apkeditor.miuix.data.OutputConfig.init(this)
        setContent {
            // 主题模式（0 跟随系统 / 1 浅色 / 2 深色 / 3 莫奈跟随系统 / 4 莫奈浅色 / 5 莫奈深色）
            val controller = remember(ThemeState.mode, ThemeState.seedIndex, ThemeState.paletteStyle, ThemeState.colorSpec) {
                val keyColor = keyColorFor(ThemeState.seedIndex)
                val spec = ThemeColorSpec.entries.getOrNull(ThemeState.colorSpec) ?: ThemeColorSpec.Spec2021
                val style = ThemePaletteStyle.entries.getOrNull(ThemeState.paletteStyle) ?: ThemePaletteStyle.Content
                when (ThemeState.mode) {
                    ThemeState.MODE_LIGHT -> ThemeController(ColorSchemeMode.Light)
                    ThemeState.MODE_DARK -> ThemeController(ColorSchemeMode.Dark)
                    ThemeState.MODE_MONET_SYSTEM -> ThemeController(ColorSchemeMode.MonetSystem, keyColor = keyColor, colorSpec = spec, paletteStyle = style)
                    ThemeState.MODE_MONET_LIGHT -> ThemeController(ColorSchemeMode.MonetLight, keyColor = keyColor, colorSpec = spec, paletteStyle = style)
                    ThemeState.MODE_MONET_DARK -> ThemeController(ColorSchemeMode.MonetDark, keyColor = keyColor, colorSpec = spec, paletteStyle = style)
                    else -> ThemeController(ColorSchemeMode.System)
                }
            }
            MiuixTheme(controller = controller) {
                // 键盘避让：edge-to-edge / 部分 ROM 下 adjustResize 不缩窗时，
                // 根内容按 IME 高度垫底，保证最底部（状态行/列表末尾）可观测；
                // 窗口真的被 adjustResize 缩过时 ime inset 为 0，不会双重避让。
                Box(Modifier.fillMaxSize().imePadding()) {
                    App()
                }
            }
        }
    }
}

/* 崩溃处理已迁移至 CrashHandler.kt（不再闪退，改为错误页 + 分享） */
