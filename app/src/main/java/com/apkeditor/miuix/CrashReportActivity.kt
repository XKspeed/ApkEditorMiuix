package com.apkeditor.miuix

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃汇报页：进程将死前被拉起，显示错误摘要 + 分享/复制/关闭。
 *
 * 为什么不用 Compose：本页要在「主进程崩溃后」的残局里启动，必须尽量少依赖
 * 崩溃前初始化的东西（Compose/Miuix 主题/缓存的单例都可能处于坏状态）。
 * 传统 View 最稳。
 */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = readReport()
        val summary = firstLines(report, 6)

        val pad = (resources.displayMetrics.density * 16).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1B1B1F.toInt())
            setPadding(pad, pad * 2, pad, pad)
        }

        val title = TextView(this).apply {
            text = "应用遇到了问题"
            setTextColor(0xFFBC3F3C.toInt())
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)

        val hint = TextView(this).apply {
            text = "\n错误摘要：\n" + summary + "\n\n完整报告可用于排查，请通过下方按钮分享。"
            setTextColor(0xFFD6D9DE.toInt())
            textSize = 14f
        }
        root.addView(hint)

        val scroll = ScrollView(this).apply {
            addView(TextView(this@CrashReportActivity).apply {
                text = report
                setTextColor(0xFF9AA0A6.toInt())
                textSize = 11f
                typeface = Typeface.MONOSPACE
            })
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
        ))

        fun button(label: String, action: (CrashReportActivity) -> Unit): Button =
            Button(this).apply { text = label }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val share = button("分享报告") { act -> shareReport(act) }
        val copy = button("复制") { act -> copyReport(act) }
        val close = button("关闭") { act -> act.finishAffinity() }
        listOf(share, copy, close).forEach { b ->
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (resources.displayMetrics.density * 6).toInt()
            })
        }
        root.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = pad })

        setContentView(root)
    }

    private fun readReport(): String {
        // 优先读独立报告文件；没有就用崩溃处理器内存里留下的
        val f = File(cacheDir, "crash_report.txt")
        if (f.exists() && f.length() > 0) return f.readText()
        val fallback = File(filesDir, "crash_log.txt")
        if (fallback.exists()) {
            val text = fallback.readText()
            // 只展示最后一段（最近的崩溃）
            val idx = text.lastIndexOf("\n[")
            return if (idx > 0) text.substring(idx) else text
        }
        return "(无报告内容)"
    }

    private fun firstLines(text: String, n: Int): String =
        text.lineSequence().filter { it.isNotBlank() }.take(n).joinToString("\n")

    private fun shareReport(act: CrashReportActivity) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val out = File(act.cacheDir, "log/crash-report-" + stamp + ".txt")
            out.parentFile?.mkdirs()
            out.writeText(readReport())
            val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", out)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "ApkEditorMiuix 崩溃报告")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            act.startActivity(Intent.createChooser(intent, "分享崩溃报告"))
        }.onFailure {
            Toast.makeText(act, "分享失败: " + it.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyReport(act: CrashReportActivity) {
        runCatching {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ApkEditorMiuix 崩溃报告", readReport()))
            Toast.makeText(act, "已复制", Toast.LENGTH_SHORT).show()
        }
    }
}
