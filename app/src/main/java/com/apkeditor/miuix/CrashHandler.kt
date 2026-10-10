package com.apkeditor.miuix

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃处理：不闪退，改为「提示 + 分享」。
 *
 * 之前的链路：未捕获异常 → 写 crash_log.txt → 进程退出（用户只看到闪退，什么都做不了）。
 * 现在的链路：未捕获异常 → 写日志 → 弹出全屏错误界面（显示错误摘要 + 复制/分享按钮）。
 *
 * 用户选择分享时跳转系统分享面板；选择关闭则走正常退出流程。
 *
 * 注意：崩溃处理器内部绝不能再抛异常（否则会二次崩溃且无任何日志）。
 */
object CrashHandler {

    private const val TAG = "Crash"

    /** 崩溃瞬间必须同步落盘的文件（进程马上要死了，异步的 AppLog 可能来不及写盘） */
    private var pendingReport: File? = null

    fun install(activity: android.app.Activity) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val log = "[$time]\n$sw\n\n"

                // 1) 同步落盘（两处：原 crash_log.txt 保留 + 本次崩溃的独立报告文件）
                try {
                    val file = File(activity.filesDir, "crash_log.txt")
                    file.appendText(log)
                } catch (_: Exception) {
                }
                try {
                    val report = File(activity.cacheDir, "crash_report.txt")
                    report.writeText(log)
                    pendingReport = report
                } catch (_: Exception) {
                }
                // 2) 尽力写一份进 app.log（可能来不及落盘，但试了不亏）
                try {
                    AppLog.e(TAG, "未捕获异常: " + (throwable.message ?: throwable.javaClass.name), throwable)
                } catch (_: Exception) {
                }

                // 3) 不再交给系统默认处理（默认=直接杀进程=闪退）。
                //    改为自杀前启动错误汇报页（独立进程级的透明 Activity）。
                try {
                    val intent = Intent(activity, CrashReportActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    activity.startActivity(intent)
                } catch (_: Exception) {
                    // 实在弹不出来（Activity 已死透），退回系统行为（闪退）
                    previous?.uncaughtException(thread, throwable)
                    return@setDefaultUncaughtExceptionHandler
                }

                // 4) 给错误页一点启动时间，然后结束当前进程
                android.os.Handler(activity.mainLooper).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }, 600)
            } catch (handlerCrash: Throwable) {
                // 处理器自身出错：回退系统默认行为，保证至少能退出
                try {
                    previous?.uncaughtException(thread, throwable)
                } catch (_: Exception) {
                }
            }
        }
    }
}
