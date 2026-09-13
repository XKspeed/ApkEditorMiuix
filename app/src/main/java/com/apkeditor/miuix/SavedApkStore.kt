package com.apkeditor.miuix

import android.content.Context

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
