package com.apkeditor.miuix.data

import kotlinx.coroutines.delay

/**
 * UI 预览版使用的示例数据服务。
 * 所有操作返回演示数据，便于先验收界面与交互流程。
 */
class MockApkDataService : ApkDataService {

    private suspend fun mockDelay() = delay(300)

    override suspend fun loadApk(uri: String): Result<ApkInfo> {
        mockDelay()
        val fileName = uri.substringAfterLast("/", "示例应用.apk")
        return Result.success(
            ApkInfo(
                fileName = fileName,
                label = "示例应用 (演示数据)",
                packageName = "com.example.demoapp",
                versionName = "1.2.3",
                versionCode = 123,
                minSdk = 26,
                targetSdk = 35,
                permissions = listOf(
                    "android.permission.INTERNET",
                    "android.permission.ACCESS_NETWORK_STATE",
                    "android.permission.WRITE_EXTERNAL_STORAGE",
                    "android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.CAMERA",
                ),
                mainActivity = "com.example.demoapp.MainActivity",
                dexNames = listOf("classes.dex", "classes2.dex", "classes3.dex"),
                fileSize = "18.6 MB",
                resourceCount = 1264,
            )
        )
    }

    override suspend fun listDexFiles(): Result<List<DexEntry>> {
        mockDelay()
        return Result.success(
            listOf(
                DexEntry("classes.dex", "6.2 MB"),
                DexEntry("classes2.dex", "3.1 MB"),
                DexEntry("classes3.dex", "1.4 MB"),
            )
        )
    }

    override suspend fun listSmaliFiles(dexName: String): Result<List<String>> {
        mockDelay()
        val root = "smali"
        return Result.success(
            listOf(
                "$root/com/example/demoapp/MainActivity.smali",
                "$root/com/example/demoapp/MainApplication.smali",
                "$root/com/example/demoapp/data/UserManager.smali",
                "$root/com/example/demoapp/data/ApiClient.smali",
                "$root/com/example/demoapp/ui/HomeFragment.smali",
                "$root/com/example/demoapp/ui/SettingsActivity.smali",
                "$root/com/example/demoapp/utils/TimeUtils.smali",                "$root/com/example/demoapp/utils/AppPrefs.smali",
                "$root/com/example/demoapp/BuildConfig.smali",
                "$root/android/support/v4/content/ContextCompat.smali",
                "$root/android/support/v4/app/Fragment.smali",
            )
        )
    }

    override suspend fun listSmaliTree(dexNames: List<String>): Result<Map<String, List<SmaliTreeNode>>> {
        mockDelay()
        val map = LinkedHashMap<String, List<SmaliTreeNode>>()
        dexNames.forEach { dex ->
            val files = listSmaliFiles(dex).getOrThrow()
            map[dex] = listOf(
                SmaliTreeNode(name = "smali", path = dex, isDir = true, dex = dex,
                    children = files.map { f ->
                        SmaliTreeNode(name = f.substringAfterLast("/"), path = f, isDir = false, dex = dex)
                    }
                )
            )
        }
        return Result.success(map)
    }

    override suspend fun searchResources(type: String?, keyword: String, maxResults: Int): Result<List<ResourceEntryInfo>> {
        mockDelay()
        return Result.success(emptyList())
    }

    override suspend fun readSmaliFile(dexName: String, filePath: String): Result<String> {
        mockDelay()
        val className = filePath.substringAfterLast("/").removeSuffix(".smali")
        return Result.success(
            """
            .class public L$className;
            .super Ljava/lang/Object;
            .source "Demo.kt"

            # annotations
            .annotation runtime Lkotlin/Metadata;
                d1 = {
                    0x1
                }
            .end annotation

            .field public static final INSTANCE:L$className;

            .method public static final fun hello()Ljava/lang/String;
                .locals 1

                const-string v0, "Hello from $className"

                return-object v0
            .end method

            .method public constructor <init>()V
                .locals 0

                invoke-direct {p0}, Ljava/lang/Object;-><init>()V

                return-void
            .end method
            """.trimIndent()
        )
    }

    override suspend fun saveSmaliFile(dexName: String, filePath: String, content: String): Result<Unit> {
        mockDelay()
        return Result.success(Unit)
    }

    override suspend fun renameSmaliFile(dexName: String, filePath: String, newClassName: String): Result<Unit> {
        mockDelay()
        return Result.success(Unit)
    }

    override suspend fun deleteSmaliFile(dexName: String, filePath: String): Result<Unit> {
        mockDelay()
        return Result.success(Unit)
    }

    override suspend fun assembleDex(dexName: String): Result<Unit> {
        mockDelay()
        return Result.success(Unit)
    }

    override suspend fun listResourceTypes(): Result<List<ResourceTypeInfo>> {
        mockDelay()
        return Result.success(
            listOf(
                ResourceTypeInfo("string", 96),
                ResourceTypeInfo("style", 54),
                ResourceTypeInfo("color", 42),
                ResourceTypeInfo("dimen", 38),
                ResourceTypeInfo("layout", 31),
                ResourceTypeInfo("drawable", 27),
                ResourceTypeInfo("anim", 8),
                ResourceTypeInfo("attr", 120),
                ResourceTypeInfo("id", 76),
                ResourceTypeInfo("bool", 6),
                ResourceTypeInfo("integer", 9),
            )
        )
    }

    override suspend fun listResources(type: String): Result<List<ResourceEntryInfo>> {
        mockDelay()
        val samples = mapOf(
            "string" to listOf(
                ResourceEntryInfo(0x7f010001, "0x7f010001", "app_name", "string", "示例应用", listOf("default")),
                ResourceEntryInfo(0x7f010002, "0x7f010002", "welcome", "string", "欢迎使用", listOf("default", "zh-rCN")),
                ResourceEntryInfo(0x7f010003, "0x7f010003", "cancel", "string", "取消", listOf("default")),
                ResourceEntryInfo(0x7f010004, "0x7f010004", "confirm", "string", "确定", listOf("default")),
                ResourceEntryInfo(0x7f010005, "0x7f010005", "network_error", "string", "网络连接失败", listOf("default", "zh-rCN", "en")),
            ),
        )
        val entries = samples[type] ?: (1..12).map { i ->
            ResourceEntryInfo(
                0x7f010000 + i,
                "0x7f010000" + (i + 9).toString(16),
                "${type}_item_$i",
                type,
                "示例值 $i",
                listOf("default"),
            )
        }
        mockDelay()
        return Result.success(entries)
    }

    override suspend fun saveResourceValue(id: Int, qualifiers: String?, newValue: String): Result<Unit> {
        mockDelay()
        return Result.success(Unit)
    }

    override suspend fun renameResource(id: Int, newName: String): Result<Unit> {
        mockDelay()
        return Result.success(Unit)
    }

    override suspend fun listApkContents(): Result<List<ApkEntry>> {
        mockDelay()
        val list = listOf(
            "AndroidManifest.xml", "classes.dex", "classes2.dex", "resources.arsc",
            "res/layout/a.xml", "res/values/strings.xml", "assets/x.txt",
        ).map { ApkEntry(it, it.length.toLong()) }
        return Result.success(list)
    }

    override suspend fun listXmlFiles(): Result<List<XmlFileInfo>> {
        mockDelay()
        return Result.success(
            listOf(
                XmlFileInfo("AndroidManifest.xml", "3.2 KB"),
                XmlFileInfo("res/layout/activity_main.xml", "1.8 KB"),
                XmlFileInfo("res/layout/fragment_home.xml", "2.4 KB"),
                XmlFileInfo("res/layout/item_list.xml", "1.1 KB"),
                XmlFileInfo("res/layout/dialog_settings.xml", "1.5 KB"),
                XmlFileInfo("res/drawable/ic_launcher_background.xml", "640 B"),
                XmlFileInfo("res/drawable/btn_round.xml", "420 B"),
                XmlFileInfo("res/menu/menu_main.xml", "380 B"),
                XmlFileInfo("res/xml/backup_rules.xml", "260 B"),
            )
        )
    }

    override suspend fun readXmlFile(path: String): Result<String> {
        mockDelay()
        return Result.success(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.demoapp">

                <uses-permission android:name="android.permission.INTERNET" />

                <application
                    android:label="@string/app_name"
                    android:icon="@mipmap/ic_launcher"
                    android:theme="@style/Theme.Demo">
                    <activity android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """.trimIndent()
        )
    }

    override suspend fun saveXmlFile(path: String, content: String): Result<Unit> {
        mockDelay()
        return Result.success(Unit)
    }

    override suspend fun buildAndSign(): Result<BuildResult> {
        mockDelay()
        val name = "output.apk"
        // 记录到"保存的 APK"列表（真实引擎接入后同样在此记录）
        com.apkeditor.miuix.SavedApkStore.add(uri = "output", name = name, size = 6_700_000L)
        return Result.success(BuildResult(name, signed = true, outputPath = "output"))
    }
}
