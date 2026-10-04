package com.apkeditor.miuix

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * "保存的 APK"记录存储：记录用户打包输出的 APK（SAF Uri + 文件名 + 时间）。
 * 用 SharedPreferences 持久化，真实引擎接入后仍可复用。
 */
object SavedApkStore {

    @Volatile
    private var appContext: Context? = null

    private const val PREFS = "saved_apks"
    private const val KEY = "records"
    private const val SEP = "\u0001"

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun prefs(): android.content.SharedPreferences {
        val ctx = appContext ?: error("SavedApkStore.init() 必须先调用")
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun add(uri: String, name: String, size: Long = 0L) {
        val records = list().toMutableList()
        records.add(0, SavedApkInfo(uri = uri, name = name, size = size, time = System.currentTimeMillis()))
        val data = records.joinToString("\n") { "${it.uri}$SEP${it.name}$SEP${it.size}$SEP${it.time}" }
        prefs().edit().putString(KEY, data).apply()
    }

    fun list(): List<SavedApkInfo> {
        val raw = prefs().getString(KEY, "") ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.mapNotNull { line ->
            val p = line.split(SEP)
            if (p.size < 4) return@mapNotNull null
            SavedApkInfo(
                uri = p[0],
                name = p[1],
                size = p[2].toLongOrNull() ?: 0L,
                time = p[3].toLongOrNull() ?: 0L,
            )
        }
    }

    /**
     * 清理指向已不存在文件的记录（文件被其他应用删除时），返回清理后的最新列表。
     * 包含磁盘 IO，请在后台线程调用。
     */
    fun pruneMissing(): List<SavedApkInfo> {
        val ctx = appContext ?: error("SavedApkStore.init() 必须先调用")
        val all = list()
        if (all.isEmpty()) return all
        val alive = all.filter { recordExists(ctx, it) }
        if (alive.size != all.size) {
            val data = alive.joinToString("\n") { "${it.uri}$SEP${it.name}$SEP${it.size}$SEP${it.time}" }
            prefs().edit().putString(KEY, data).apply()
        }
        return alive
    }

    /**
     * 记录指向的文件是否仍存在。
     * - 本地路径（公共目录 / 私有目录）：直接查 [File.exists]；
     * - content://（SAF）：查 ContentResolver，查不到（0 行）或抛 FileNotFoundException 才判失效；
     *   其他异常（如瞬时权限错误）保守保留，避免误删。
     */
    private fun recordExists(ctx: Context, r: SavedApkInfo): Boolean {
        if (r.uri.startsWith("content://")) {
            return runCatching {
                ctx.contentResolver.query(Uri.parse(r.uri), null, null, null, null)?.use { c ->
                    c.count > 0
                } ?: false
            }.getOrElse { e -> e is java.io.FileNotFoundException }
        }
        val path = if (r.uri.startsWith("file:")) Uri.parse(r.uri).path else r.uri
        return !path.isNullOrBlank() && File(path).exists()
    }

    fun remove(uri: String) {
        val records = list().filterNot { it.uri == uri }
        val data = records.joinToString("\n") { "${it.uri}$SEP${it.name}$SEP${it.size}$SEP${it.time}" }
        prefs().edit().putString(KEY, data).apply()
    }
}

data class SavedApkInfo(
    val uri: String,
    val name: String,
    val size: Long = 0L,
    val time: Long = 0L,
)
