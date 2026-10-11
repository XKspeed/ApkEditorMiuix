package com.apkeditor.miuix

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * 应用内日志系统：**debug 与 release 一律可用**，用于线上排障。
 *
 * 设计要点：
 *  - 默认开启（[Level.BRIEF]），保证任何用户装完即有日志可查，不需要事先配置。
 *  - 三档：详细(VERBOSE) / 简略(BRIEF) / 关闭(OFF)，设置页可切换。
 *  - 写入**单文件循环**：超过 [MAX_BYTES] 自动截断前半，避免无限增长占满存储。
 *  - 写盘在单线程池串行执行，不阻塞调用方（UI/IO 线程都不会被拖慢）。
 *  - 同时转发到 logcat，接 adb 调试时行为不变。
 *
 * 用法：
 * ```kotlin
 * AppLog.init(context)              // Application/Activity 启动时一次
 * AppLog.i("QuickEdit", "开始处理 ${apk.name}")
 * AppLog.e("Arsc", "解析失败", throwable)
 * ```
 *
 * 重要：**不要记录密钥、密码、用户隐私内容**。日志会被用户分享出去。
 */
object AppLog {

    enum class Level(val id: Int, val label: String) {
        /** 详细：记录每一步操作与参数，排查复杂问题用 */
        VERBOSE(0, "详细"),

        /** 简略：只记关键节点与所有错误（默认） */
        BRIEF(1, "简略"),

        /** 关闭：不写任何日志 */
        OFF(2, "关闭"),
        ;

        companion object {
            fun fromId(id: Int): Level = entries.firstOrNull { it.id == id } ?: BRIEF
        }
    }

    private const val PREFS = "app_log"
    private const val KEY_LEVEL = "level"
    private const val TAG = "ApkEditorMiuix"

    /** 单个日志文件上限：超过后保留后半段（最近的才是有用的）。 */
    private const val MAX_BYTES = 2L * 1024 * 1024

    private const val FILE_NAME = "app.log"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var level: Level = Level.BRIEF

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "AppLog").apply { isDaemon = true }
    }

    /** 写盘计数（用于「日志」页显示） */
    private val written = AtomicLong(0)

    private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            level = Level.fromId(prefs().getInt(KEY_LEVEL, Level.BRIEF.id))
        }
    }

    private fun prefs() = (appContext ?: error("AppLog.init() 必须先调用"))
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentLevel(): Level = level

    fun setLevel(newLevel: Level) {
        level = newLevel
        runCatching { prefs().edit().putInt(KEY_LEVEL, newLevel.id).apply() }
        // 切换动作本身也记一笔，便于确认用户改过设置
        i("AppLog", "日志级别切换为 " + newLevel.label)
    }

    /** 日志文件 */
    fun logFile(): File =
        File((appContext ?: error("AppLog.init() 必须先调用")).filesDir, FILE_NAME)

    /** 已写入条数（约数，仅用于展示） */
    fun writtenCount(): Long = written.get()

    // ---------------- 对外 API ----------------

    /** 详细日志：仅 [Level.VERBOSE] 时记录 */
    fun v(tag: String, msg: String) {
        if (level == Level.VERBOSE) write("V", tag, msg, null)
    }

    /** 关键节点：VERBOSE 与 BRIEF 都记录 */
    fun i(tag: String, msg: String) {
        if (level != Level.OFF) write("I", tag, msg, null)
    }

    /** 错误：**任何非 OFF 级别都记录**（排障最需要的就是它） */
    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (level != Level.OFF) write("E", tag, msg, t)
    }

    /**
     * 包裹一段可能失败的操作，失败时自动记日志并把异常继续抛出。
     *
     * 专治"代码里到处 runCatching 静默吞异常、线上无从排查"的问题：
     * ```kotlin
     * AppLog.tryOrLog("QuickEdit") { ApkVersionService.quickEdit(...) }
     * ```
     */
    inline fun <T> tryOrLog(tag: String, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        e(tag, "操作失败: " + (t.message ?: t.javaClass.name), t)
        throw t
    }

    /**
     * `runCatching` 的替代：**行为完全一致**（同样返回 [Result]，同样捕获 Throwable），
     * 额外把失败写进日志。
     *
     * 用于替换那些"静默吞异常、线上无从排查"的 runCatching：
     * ```kotlin
     * // 之前：失败后什么都不留
     * override suspend fun readXmlFile(p: String): Result<String> = runCatching { ... }
     * // 之后：失败会进 app.log
     * override suspend fun readXmlFile(p: String): Result<String> = logResult("readXmlFile") { ... }
     * ```
     *
     * 关键：**返回类型仍是 Result，且 return@runCatching 之类的标签需要改成 return@logResult**。
     * 只在方法体首行替换时安全。
     */
    inline fun <T> logResult(tag: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (t: Throwable) {
        e(tag, "失败: " + (t.message ?: t.javaClass.name), t)
        Result.failure(t)
    }

    // ---------------- 读写 ----------------

    private fun write(levelChar: String, tag: String, msg: String, t: Throwable?) {
        val line = buildString {
            append(timeFmt.format(Date()))
            append(' ')
            append(levelChar)
            append('/')
            append(tag)
            append(": ")
            append(msg)
            if (t != null) {
                append('\n')
                append(Log.getStackTraceString(t))
            }
            append('\n')
        }
        // logcat 保持可用（adb 调试照旧）
        when (levelChar) {
            "E" -> Log.e(TAG, "[$tag] $msg", t)
            "V" -> Log.v(TAG, "[$tag] $msg")
            else -> Log.i(TAG, "[$tag] $msg")
        }
        executor.execute {
            runCatching {
                val f = logFile()
                if (f.length() > MAX_BYTES) trim(f)
                f.appendText(line)
                written.incrementAndGet()
            }
        }
    }

    /** 文件超限时保留后半段 */
    private fun trim(f: File) {
        runCatching {
            val keep = MAX_BYTES / 2
            val text = f.readText()
            val cut = text.length - keep.toInt()
            if (cut > 0) {
                f.writeText("…(前段日志已截断)…\n" + text.substring(cut))
            }
        }
    }

    /** 清空日志（设置页「清空」按钮） */
    fun clear() {
        executor.execute { runCatching { logFile().writeText("") } }
    }

    /** 同步读取全部日志文本（导出/分享用）。始终在后台线程调用。 */
    fun readAll(): String = runCatching {
        val f = logFile()
        if (f.exists()) f.readText() else "(暂无日志)"
    }.getOrElse { "(读取日志失败: " + it.message + ")" }

    /** 生成一次性导出文件并返回（分享用）。 */
    fun exportTo(dest: File): File {
        dest.parentFile?.mkdirs()
        dest.writeText(readAll())
        return dest
    }
}
