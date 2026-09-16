package com.apkeditor.miuix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.apkeditor.miuix.ui.App
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class AppNavigationEventDispatcherOwner : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashLogger()
        enableEdgeToEdge()
        SavedApkStore.init(this)
        com.apkeditor.miuix.data.OutputConfig.init(this)
        setContent {
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
            ProvideNavigationEventDispatcher {
                MiuixTheme(controller = controller) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun ProvideNavigationEventDispatcher(content: @Composable () -> Unit) {
    val owner = remember { AppNavigationEventDispatcherOwner() }
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides owner,
        content = content,
    )
}

private fun ComponentActivity.installCrashLogger() {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val log = "[$time]\n$sw\n\n"
            val file = File(filesDir, "crash_log.txt")
            file.appendText(log)
        } catch (_: Exception) {
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
