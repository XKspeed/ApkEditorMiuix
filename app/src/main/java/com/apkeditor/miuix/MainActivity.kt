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
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * miuix 0.9.3 的 MiuixPopupHost 内部使用 NavigationBackHandler，
 * 要求宿主通过 LocalNavigationEventDispatcherOwner 提供 NavigationEventDispatcher。
 * 本 App 不引入 Navigation Compose（自实现返回栈），故在此手动提供，
 * 否则展开任何 Overlay 弹窗（如 OverlayDropdownPreference）都会闪退。
 */
private class AppNavigationEventDispatcherOwner : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 安装崩溃日志记录器
        installCrashLogger()
        // Android 15 强制 edge-to-edge：状态栏/导航栏透明，图标颜色由系统按主题自动适配
        enableEdgeToEdge()
        SavedApkStore.init(this)
        com.apkeditor.miuix.data.OutputConfig.init(this)
        setContent {
            // 主题模式（0 跟随系统 / 1 浅色 / 2 深色 / 3 莫奈跟随系统 / 4 莫奈浅色 / 5 莫奈深色），设置页可切换
            val controller = remember(ThemeState.mode) {
                when (ThemeState.mode) {
                    ThemeState.MODE_LIGHT -> ThemeController(ColorSchemeMode.Light)
                    ThemeState.MODE_DARK -> ThemeController(ColorSchemeMode.Dark)
                    ThemeState.MODE_MONET_SYSTEM -> ThemeController(ColorSchemeMode.MonetSystem)
                    ThemeState.MODE_MONET_LIGHT -> ThemeController(ColorSchemeMode.MonetLight)
                    ThemeState.MODE_MONET_DARK -> ThemeController(ColorSchemeMode.MonetDark)
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

/** 提供 miuix popup 所需的 NavigationEventDispatcher（防止 Overlay 弹窗展开闪退） */
@Composable
private fun ProvideNavigationEventDispatcher(content: @Composable () -> Unit) {
    val owner = remember { AppNavigationEventDispatcherOwner() }
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides owner,
        content = content,
    )
}

/** 崩溃日志记录：把未捕获异常写到 files/crash_log.txt */
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
