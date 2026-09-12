# ApkEditor·Miuix —— AI 开发交接文档（自包含版）

> 本文件是给**后续接手的 AI 开发者**（或人类开发者）使用的唯一权威交接文档。
> 阅读本文档即可了解项目全貌：需求基线、UI 层、功能层（真实引擎蓝图）、构建环境、版本锁定、已踩坑记录、交付规范。
> 本文档与对话上下文无关，可独立使用。
>
> 当前版本：v0.1（版本号固定不再改动）· 真实引擎已接入（ARSCLib + smali/baksmali + apksig）· 底栏三栏 + MT风格APK内容页
> 最后更新：2026-09-12

---

## 目录
0. 文档目的与读者
1. 项目定位与需求基线
2. 技术栈与版本锁定（勿随意升级）
3. 构建环境与常用命令
4. 项目结构（现状）
5. UI 层详解
6. 数据层详解
7. 功能层（真实反编译引擎）接入蓝图 —— 最重要的后续工作
8. 已踩坑记录（勿重踩）
9. 交付与文档规范（用户强制要求）
10. 修改记录

---

## 0. 文档目的与读者

- **目的**：让一个没有任何本对话上下文的 AI/开发者，仅凭本文档 + 源码即可继续开发本项目。
- **读者**：后续接手开发的 AI 代理（最优先）、人类开发者。
- **使用方式**：先通读第 1/2/3 节了解全局；开发 UI 前必读第 5 节；开发功能层前必读第 7 节；任何改动前必读第 8 节（避免重踩坑）。

---

## 1. 项目定位与需求基线

**一句话**：仿 MT 管理器的 APK 反编译编辑工具，UI 使用 miuix（小米 HyperOS 风格 Compose 组件库），**无文件管理器**，只做 APK 编辑能力。

### 需求历史（已定案，勿再讨论）
| 需求点 | 结论 |
| --- | --- |
| 类似 MT 管理器 | ✅ 是 |
| UI 使用 miuix | ✅ 是（compose-miuix-ui，HyperOS 风格） |
| 双排文件管理器 | ❌ 不要 |
| 功能范围 | 仅 APK 反编译编辑：DEX/Smali 修改、ARSC 资源修改、XML 修改、打包签名 |
| DEX 编辑深度 | 进阶版：MT 式 smali 反汇编 → 编辑 → 汇编 |
| 底层方案 | 接受成熟开源库；用户明确要求"自己开发，把文件打包发过来" |
| Android 版本 | **只适配 Android 15+（API 35），无需兼容旧设备**（minSdk=35） |
| 反编译引擎 | 用户指出"apktool 在安卓17 反编译 arsc 后回编译会炸" → **弃用 apktool+aapt2**，改用 **REAndroid/ARSCLib**（纯 Java 直改二进制资源，官方支持 API37）+ **smali/baksmali 2.5.2** + **apksig** |

### 当前阶段
- **v0.2.0 = UI 预览版**：界面完整可交互，数据层为 Mock 演示数据。
- 用户验收 UI 后，下一步接真实引擎（第 7 节）。

---

## 2. 技术栈与版本锁定（勿随意升级）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Kotlin | 2.4.20 | 编译语言 |
| AGP | 8.13.2 | Android Gradle 插件 |
| Gradle | 8.14 | 构建工具（wrapper 已生成） |
| Jetpack Compose | 1.11.x（由 miuix 传递） | UI 框架 |
| **miuix** | **0.9.3（锁定！）** | HyperOS 风格组件库，KMP 库（ui/preference/icons 三件套） |
| activity-compose | 1.11.0 | Activity + Compose 桥接 |
| kotlinx-coroutines-android | 1.11.0 | 异步 |
| ARSCLib | V1.4.0（jitpack，`com.github.REAndroid:ARSCLib`） | 功能层：资源表/二进制 XML 解析（**已预置依赖但未调用**） |
| baksmali / smali | 2.5.2 | 功能层：DEX ↔ Smali |
| apksig | 8.13.x（google maven，`com.android.tools.build:apksig`） | 功能层：APK 签名（**尚未加入依赖**） |

> ⚠️ **miuix 处于实验阶段，API 随版本变化大**。本项目锁定 0.9.3，升级前必须逐 API 核对（用 javap 验证，见 §8）。
>
> ⚠️ ARSCLib 的 V1.4.0 坐标来自 jitpack，官方 README 写 `V1.3.1` 也常见；构建时以能解析为准。

### 应用配置（app/build.gradle.kts）
- namespace = applicationId = `com.apkeditor.miuix`
- compileSdk = 37 / minSdk = **35** / targetSdk = 37
- versionName = `0.2.0`
- 已启用 compose，jvmTarget 17
- 依赖里已含：activity-compose、coroutines、miuix-ui/preference/icons 三件套

---

## 3. 构建环境与常用命令

### 3.1 本机（当前开发机）已搭好的环境

> ⚠️ **`/tmp` 目录会被系统周期性清空**（已发生 2 次），一切持久资料必须放 `/home/user` 下。

| 资源 | 路径 |
| --- | --- |
| JDK 17 | `/home/user/buildenv/jdk-17.0.15+6` |
| Gradle 8.14 | `/home/user/buildenv/gradle-8.14` |
| Android SDK | `/home/user/buildenv/android-sdk`（含 `platforms/android-37.0`、`build-tools/37.0.0`、`cmdline-tools/latest/bin`） |
| miuix 源码 | `/home/user/refs/miuix`（git 仓库，已 fetch `v0.9.3` tag） |
| miuix 0.9.3 AAR 解包 | `/home/user/refs/miuix093/classes.jar`（用于 javap 核对 API） |
| miuix-preference 0.9.3 | `/home/user/refs/miuixpref093/classes.jar` |
| miuix-icons 0.9.3 | `/home/user/refs/miuixicons093/classes.jar` |
| ARSCLib 源码 | `/home/user/refs/ARSCLib`（`src/main/java/com/reandroid/...`） |
| 项目根 | `/home/user/Doubao/chats/8153721457272066/ApkEditorMiuix/` |
| 交付目录 | `/home/user/Doubao/chats/8153721457272066/交付/` |

### 3.2 编译命令（本机）

```bash
export JAVA_HOME=/home/user/buildenv/jdk-17.0.15+6
export PATH=$JAVA_HOME/bin:$PATH
cd /home/user/Doubao/chats/8153721457272066/ApkEditorMiuix
/home/user/buildenv/gradle-8.14/bin/gradle :app:assembleDebug --no-daemon --console=plain
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 项目内已有 `./gradlew`（wrapper），小白环境用 `./gradlew assembleDebug` 即可。

### 3.3 SDK 平台软链坑（重要）
- SDK 平台包实际目录名是 `platforms;android-37.0`，而 AGP 查找 `android-37`。
- 已解决：建符号链接 `android-37 -> android-37.0`。若换机器或重装 SDK，需重新检查。

### 3.4 验证 APK 命令
```bash
AAPT=/home/user/buildenv/android-sdk/build-tools/37.0.0/aapt2
$AAPT dump badging app-debug.apk | grep -E "package:|minSdk|targetSdk"
# 预期：minSdkVersion:'35'  targetSdkVersion:'37'
```

### 3.5 小白环境（用户视角）
用户拿到的是完整 zip（源码 + docs + APK），只需安装 Android Studio，用 `./gradlew assembleDebug` 编译。详见 `docs/编译教程-小白版.md`。

---

## 4. 项目结构（现状）

```
ApkEditorMiuix/
├─ settings.gradle.kts          # 仓库：google()/mavenCentral()/jitpack
├─ build.gradle.kts             # 插件版本声明
├─ gradle.properties            # JVM 与 AndroidX 配置
├─ gradle/wrapper/              # Gradle Wrapper（8.14）
├─ docs/
│  ├─ 编译教程-小白版.md          # 零环境编译指南（每次功能更新必须同步）
│  ├─ 开发文档.md                # 人类开发者文档（每次功能更新必须同步）
│  └─ AI开发交接文档.md          # 本文档（面向 AI 的完整交接）
└─ app/
   ├─ build.gradle.kts          # 应用构建配置
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/                   # values/strings、values/themes、drawable、mipmap-anydpi-v26
      └─ java/com/apkeditor/miuix/
         ├─ MainActivity.kt     # 入口：enableEdgeToEdge + ThemeController(跟随 ThemeState) + MiuixTheme
         ├─ ThemeState.kt       # 全局主题模式状态（0 系统/1 浅色/2 深色）
         ├─ SavedApkStore.kt    # "保存的 APK"记录（SharedPreferences）+ SavedApkInfo 模型
         ├─ data/
         │  ├─ Models.kt        # 数据模型
         │  ├─ ApkDataService.kt# 数据服务接口（UI 只依赖接口）
         │  └─ MockApkDataService.kt # 演示数据实现
         └─ ui/
            ├─ App.kt           # 底部三栏导航 + 主页子页面返回栈
            ├─ HomeScreen.kt    # 首页：选择 APK（SAF 文件选择器）
            ├─ SavedApksScreen.kt # 保存的 APK 列表（分享/删除）
            ├─ SettingsScreen.kt  # 设置：主题切换 + 关于
            ├─ ApkInfoScreen.kt # APK 信息 + 功能入口 + 打包按钮
            ├─ DexListScreen.kt # dex 列表
            ├─ SmaliTreeScreen.kt # smali 文件树（搜索 + 汇编入口）
            ├─ TextEditorScreen.kt # 通用代码编辑器（查找/替换/跳行/保存）
            ├─ ArscTypesScreen.kt  # 资源类型列表
            ├─ ArscEntriesScreen.kt # 资源条目 + 编辑弹窗
            ├─ XmlFilesScreen.kt  # XML 文件列表
            └─ components/
               ├─ Common.kt    # LoadingBox/ErrorBox/ListItemRow/InfoRow/SectionCard/MiuixDialog/DialogActions
               └─ TextField.kt # 字符串版 TextField 包装（State<String> 桥接 TextFieldState）
```

---

## 5. UI 层详解

### 5.1 导航架构（v0.2.0）

**两层导航**：
1. **外层：底部三栏**（常驻）——`App.kt` 中 `var tab by remember { mutableIntStateOf(0) }`：
   - 0 = 主页（HomeScreen + 子页面栈）
   - 1 = 保存的 APK（SavedApksScreen）
   - 2 = 设置（SettingsScreen）
2. **内层：主页子页面栈**——`mutableStateListOf<Screen>`，sealed interface Screen 定义全部页面。

```kotlin
// App.kt 核心结构
Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),   // ⚠️ insets 约定见 §5.5
    bottomBar = {
        NavigationBar {
            BottomTab.entries.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = tab == index,
                    onClick = { tab = index },
                    icon = when (item) { HOME -> MiuixIcons.Home; SAVED -> MiuixIcons.Download; SETTINGS -> MiuixIcons.Settings },
                    label = item.title,   // ⚠️ 参数名是 label，不是 title！
                )
            }
        }
    },
) { innerPadding ->
    Box(Modifier.fillMaxSize().padding(innerPadding)) {
        when (tab) {
            0 -> HomeContent(current, goBack, navigate, service)
            1 -> SavedApksScreen()
            2 -> SettingsScreen()
        }
    }
}
```

系统返回键处理：
```kotlin
BackHandler {
    when {
        tab != 0 -> tab = 0
        stack.size > 1 -> goBack()
    }
}
```

### 5.2 主题系统（设置页可切换）

- `ThemeState.kt`：`var mode by mutableIntStateOf(0)`，0=跟随系统、1=浅色、2=深色。设置页直接赋值。
- `MainActivity.kt`：

```kotlin
enableEdgeToEdge()
val controller = remember(ThemeState.mode) {
    when (ThemeState.mode) {
        ThemeState.MODE_LIGHT -> ThemeController(ColorSchemeMode.Light)
        ThemeState.MODE_DARK  -> ThemeController(ColorSchemeMode.Dark)
        else                  -> ThemeController(ColorSchemeMode.System)
    }
}
MiuixTheme(controller = controller) { App() }
```

- 设置页切换 UI（miuix-preference 的 OverlayDropdownPreference）：

```kotlin
OverlayDropdownPreference(
    title = "主题",
    items = listOf("跟随系统", "浅色", "深色"),
    selectedIndex = ThemeState.mode,
    onSelectedIndexChange = { ThemeState.mode = it },
)
```

⚠️ **深色模式铁律**：`MiuixTheme {}` 无参重载**固定使用默认亮色配色，不跟随系统**（v0.1 失效根因）。必须用 `MiuixTheme(controller = ThemeController(...))`。
⚠️ **颜色铁律**：全项目颜色一律 `MiuixTheme.colorScheme.xxx`，禁止硬编码颜色。
⚠️ 已知限制：主题选择暂未持久化（重启回到系统），后续可接 DataStore/SharedPreferences。

### 5.3 各 Screen 职责速查

| Screen | 职责 | 关键实现 |
| --- | --- | --- |
| HomeScreen | 选择 APK | `ActivityResultContracts.OpenDocument`（`arrayOf("application/vnd.android.package-archive")`）→ uri → 进信息页 |
| ApkInfoScreen | 信息 + 4 个入口 + 打包按钮 | 加载 `service.loadApk(uri)`；打包用 `ActivityResultContracts.CreateDocument`（**传 String 文件名，不是 Intent**）→ `service.buildAndSign(uri)` |
| DexListScreen | dex 文件列表 | `service.listDexFiles()` |
| SmaliTreeScreen | smali 文件树 + 搜索 + 汇编按钮 | `service.listSmaliFiles(dexName)`；标题栏"汇编"按钮调 `service.assembleDex(dexName)` |
| TextEditorScreen | 通用代码编辑器 | 见 §5.4 |
| ArscTypesScreen | 资源类型列表 | `service.listResourceTypes()` |
| ArscEntriesScreen | 资源条目 + 编辑弹窗 | `service.listResources(type)`；弹窗内编辑值/重命名 |
| XmlFilesScreen | XML 文件列表 | `service.listXmlFiles()` |
| SavedApksScreen | 保存的 APK 列表 | `SavedApkStore.list()`；分享（ACTION_SEND + FLAG_GRANT_READ_URI_PERMISSION）；删除 |
| SettingsScreen | 主题切换 + 关于 | OverlayDropdownPreference + Card |

### 5.4 通用代码编辑器（TextEditorScreen.kt）

- Smali 与 XML 编辑共用，通过 `load` / `save` lambda 注入差异。
- 基于 androidx `TextFieldState` + Miuix `TextField(state = ...)`。

| 功能 | 实现要点 |
| --- | --- |
| 文本写入 | `state.edit { replace(0, length, newText) }` |
| 光标定位 | `state.edit { selection = TextRange(start, end) }` |
| 只读读取 | `state.text`（CharSequence） |
| 脏标记 | `snapshotFlow { state.text.toString() }.collect { dirty = true }` |
| 查找/替换 | 字符串 indexOf/replace + selection 高亮 |
| 跳转行 | 按 `\n` 累计 offset → 设置 selection |

⚠️ **androidx 新版文本 API**：`TextFieldState.text` 是只读；修改必须走 `edit {}`，块内 receiver 是 `TextFieldBuffer`，**用 `this`/隐式 receiver，不能用 `it`**；光标是 `selection` 属性赋值（无 setSelection 方法）。

### 5.5 insets 约定（重要）

- **外层** Scaffold 与**所有内层页面** Scaffold 一律：
  `contentWindowInsets = WindowInsets(0, 0, 0, 0)`
- 系统栏 inset 统一由各 `TopAppBar` 的 `defaultWindowInsetsPadding = true`（默认）处理。
- 原因：外层已有 bottomBar 且 edge-to-edge 生效，内层若再吃 systemBars 会**双重 padding**。

### 5.6 Miuix 0.9.3 API 速查（全部已验证）

| 组件 | 包 | 备注/坑 |
| --- | --- | --- |
| `MiuixTheme(controller, content)` | `top.yukonga.miuix.kmp.theme` | 必须带 controller |
| `ThemeController(ColorSchemeMode.X)` | 同上 | 0.9.3 无 `rememberThemeController` 顶层函数，用 `remember { ThemeController(...) }` |
| `ColorSchemeMode` | 同上 | `System/Light/Dark/MonetSystem/MonetLight/MonetDark` |
| `MiuixTheme.colorScheme` | 同上 | primary/background/surface/onSurface/onSurfaceVariantSummary/onSurfaceVariantActions/dividerLine/error/onSurfaceContainer 等 |
| `MiuixTheme.textStyles` | 同上 | main/subtitle/title2/footnote2/button 等 |
| `TopAppBar` | `top.yukonga.miuix.kmp.basic` | 0.9.3 在 basic 包（老文档 topBar 包不存在）；参数 title/subtitle/navigationIcon/actions |
| `Scaffold` | 同上 | `content: (PaddingValues) -> Unit`；`contentWindowInsets` 参数存在 |
| `NavigationBar` | 同上 | `content: RowScope.() -> Unit`；参数 color/showDivider/defaultWindowInsetsPadding/mode |
| `NavigationBarItem` | 同上 | 参数 **`label: String`**（不是 title！）；`icon: ImageVector`、`selected`、`onClick`、`enabled`、`badge` |
| `Button / Text / Card / Surface` | 同上 | Miuix 版本 |
| `HorizontalDivider / VerticalDivider` | 同上 | **0.9.3 没有 `Divider`** |
| `TextField(state: TextFieldState)` | 同上 | 见 §5.4 |
| `CircularProgressIndicator(progress: Float?)` | 同上 | null=不定转圈 |
| `MiuixIcons.Home/Download/Settings` | `top.yukonga.miuix.kmp.icon.MiuixIcons` | 扩展属性，需 `import top.yukonga.miuix.kmp.icon.extended.Home` 等；**必须依赖 `miuix-icons-android`** |
| `OverlayDropdownPreference` | `top.yukonga.miuix.kmp.preference` | title/items/selectedIndex/onSelectedIndexChange |
| `SwitchPreference` | 同上 | title/checked/onCheckedChange |
| `Card` 内放 LazyList 项 | - | **`LazyListScope.items` 必须直接写在 LazyColumn 作用域内**，不能包在 SectionCard 的 ColumnScope 里 |

### 5.7 图标库注意
- `miuix-icons-android:0.9.3` 必须显式加入依赖（v0.2.0 曾因漏加报 `Unresolved reference 'extended'`）。

---

## 6. 数据层详解

### 6.1 接口（ApkDataService.kt）—— UI 唯一数据入口

```kotlin
interface ApkDataService {
    suspend fun loadApk(uri: String): Result<ApkInfo>
    suspend fun listDexFiles(): Result<List<DexEntry>>
    suspend fun listSmaliFiles(dexName: String): Result<List<String>>
    suspend fun readSmaliFile(dexName: String, filePath: String): Result<String>
    suspend fun saveSmaliFile(dexName: String, filePath: String, content: String): Result<Unit>
    suspend fun assembleDex(dexName: String): Result<Unit>
    suspend fun listResourceTypes(): Result<List<ResourceTypeInfo>>
    suspend fun listResources(type: String): Result<List<ResourceEntryInfo>>
    suspend fun saveResourceValue(id: Int, qualifiers: String?, newValue: String): Result<Unit>
    suspend fun renameResource(id: Int, newName: String): Result<Unit>
    suspend fun listXmlFiles(): Result<List<XmlFileInfo>>
    suspend fun readXmlFile(path: String): Result<String>
    suspend fun saveXmlFile(path: String, content: String): Result<Unit>
    suspend fun buildAndSign(outputUri: String): Result<BuildResult>
}
```

> 架构铁律：**UI 只依赖此接口**。Mock → Real 替换时 UI 零改动。

### 6.2 数据模型（Models.kt）

```kotlin
data class ApkInfo(
    val fileName: String, val label: String, val packageName: String,
    val versionName: String, val versionCode: Int,
    val minSdk: Int, val targetSdk: Int,
    val permissions: List<String>, val mainActivity: String,
    val dexNames: List<String>, val fileSize: String, val resourceCount: Int,
)
data class DexEntry(val name: String, val size: String)
data class ResourceTypeInfo(val type: String, val count: Int)
data class ResourceEntryInfo(val id: Int, val hexId: String, val name: String,
                             val type: String, val value: String, val configs: List<String>)
data class XmlFileInfo(val path: String, val size: String)
data class BuildResult(val outputName: String, val signed: Boolean)
// SavedApkInfo 在 SavedApkStore.kt：
data class SavedApkInfo(val uri: String, val name: String, val size: Long = 0L, val time: Long = 0L)
```

### 6.3 MockApkDataService
- 所有方法 `delay(300)` 模拟耗时，返回演示数据（示例应用 com.example.demoapp）。
- `buildAndSign` 成功后调用 `SavedApkStore.add(uri, name, size)` 记录到"保存的 APK"。
- 真实实现（RealApkDataService）必须保持同样行为：打包成功 → 记录。

### 6.4 SavedApkStore
- SharedPreferences（`saved_apks` / `records`），每行 `uri\u0001name\u0001size\u0001time`。
- `init(context)` 必须在 MainActivity.onCreate 调用；`add/list/remove`。
- 分享用 `ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION`（apply 块内注意 `this.type = type`，直接 `type = type` 会解析到参数 val 而报错）。

---

## 7. 功能层（真实反编译引擎）接入蓝图 —— 最重要的后续工作

> **本节已实现**（v0.1 固定版）：`data/RealApkDataService.kt` 已完成并替换 Mock。以下为实际可用的实现要点与踩坑。
> 所有 API 细节以 `/home/user/refs/ARSCLib/src/main/java/com/reandroid/` 源码为准（本机可查）。

### 7.1 总体流程

```
SAF 选 APK → 复制到 App 私有缓存目录（cacheDir/apk/）
   ↓
ARSCLib ApkModule.loadApkFile(file) 打开（懒加载，不落盘展开）
   ↓
UI 操作（信息/XML/DEX/ARSC 编辑）→ 修改内存中的模块
   ↓
buildAndSign：writeApk 输出到 SAF 目标 uri → apksig 签名 v1+v2 → SavedApkStore.add
```

### 7.2 ARSCLib 关键 API（已研究确认）

**打开 APK**
```kotlin
import com.reandroid.apk.ApkModule
val apkModule = ApkModule.loadApkFile(File)  // 懒加载
```

**APK 信息（Manifest）**
```kotlin
val manifest = apkModule.getAndroidManifestBlock()
manifest.packageName / versionName / versionCode
manifest.usesPermissions / minSdkVersion / targetSdkVersion / mainActivityClassName
```

**XML 解码（二进制 → 文本）**
```kotlin
val doc = apkModule.decodeXMLFile(path)          // path 如 "AndroidManifest.xml"、"res/layout/xxx.xml"
// 输出文本：com.reandroid.xml.XMLFactory.newSerializer(Writer) 或直接写 File
```

**XML 编码（文本 → 二进制，替换回 APK）**
```kotlin
val source = XMLEncodeSource(packageBlock, XMLFileParserSource(path, textFile))
apkModule.add(source)   // 按 path 替换模块内的二进制 XML
```

**ARSC 资源编辑**
```kotlin
val table = apkModule.tableBlock  // 或 getTableBlock()
for (resource in table.resources) {              // 遍历 ResourceEntry
    resource.resourceId / resource.name / resource.setName(...) / resource.hexId
    for (entry in resource) {                    // 各配置 Entry
        entry.setValueAsString(newValue)         // 改值
    }
}
```

**framework（系统资源）**
- ARSCLib 内置 `src/main/resources/frameworks/android/android-XX.apk`（含 37）。
- `initializeAndroidFramework(version)` 自动加载，解析 @android: 引用无需额外处理。

**写回 APK**
```kotlin
apkModule.writeApk(File)  // 输出未签名 APK
```

### 7.3 smali/baksmali 关键 API（dexlib2）

```kotlin
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.DexFile
import org.jf.dexlib2.writer.io.FileDataStore
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions

val opcodes = Opcodes.forApi(35)                     // Android 15
val dexFile = DexFileFactory.loadDexFile(File(dexPath), opcodes)

// 反汇编：整 dex → smali 目录
val options = BaksmaliOptions()
Baksmali.disassembleDexFile(dexFile, smaliDirFile, 35, options)

// 汇编：smali 目录 → dex
val smaliOptions = SmaliOptions().apply { outputDexFile = outDexPath }
Smali.assemble(smaliOptions, smaliDirFile.path)

// 替换回 APK 模块（⚠️ ARSCLib 没有 addDexFile，用 add(DexFileInputSource(...)) 按同名替换）
apkModule.add(DexFileInputSource(dexName, FileInputSource(File(outDexPath), dexName)))
```

> 设计决策（已定）：**smali 编辑是整 dex 级**——baksmali 全量反汇编 → 用户改文件 → smali 全量汇编 → 替换回 APK。不做单类增量（保持简单可靠）。

### 7.4 apksig 签名

```kotlin
import com.android.apksig.ApkSigner
// 无 MinSdkVersion 类，setMinSdkVersion 直接传 int

// v1 + v2 签名，调试密钥（或用用户自定义 keystore）
val signerConfig = ApkSigner.SignerConfig.Builder(
    "CN=ApkEditorMiuix", privateKey, listOf(certificates),   // ⚠️ 第三参是 List<X509Certificate>
).build()
ApkSigner.Builder(listOf(signerConfig))                      // ⚠️ 需 List（setOf 不行）
    .setInputApk(inputFile)
    .setOutputApk(outputFile)
    .setMinSdkVersion(35)                                    // 直接 int
    .setV1SigningEnabled(true)
    .setV2SigningEnabled(true)
    .build()
    .sign()
```

- 依赖需加入：`com.android.tools.build:apksig:8.13.x`（google maven）。
- 调试密钥：可首次运行时生成并存入 App 私有目录（KeyStore），保证同一 App 的多次产物可覆盖安装。
- 签名后产物写入 SAF 目标 uri（`contentResolver.openOutputStream`）→ `SavedApkStore.add`。

### 7.4.1 签名密钥（Android 上生成自签证书）

- Android **没有** `sun.security.x509`（桌面 JDK 才有），不能像桌面端那样手动构建 X509。
- 必须用 **BouncyCastle**：`org.bouncycastle:bcprov-jdk18on` **和** `org.bouncycastle:bcpkix-jdk18on`（
  `JcaX509v3CertificateBuilder` / `JcaContentSignerBuilder` / `JcaX509CertificateConverter` 都在 **bcpkix**，
  只加 bcprov 会 Unresolved reference 'cert'/'operator'）。
- 首次运行时生成 RSA 2048 密钥 + 自签证书，存 `context.filesDir/signing.keystore`（PKCS12），之后复用，保证多次产物可覆盖安装。

### 7.5 线程模型

- 所有引擎操作是**耗时 CPU/IO 任务**，必须在 `Dispatchers.IO`（或专用单线程调度器，ARSCLib 非线程安全，同一 ApkModule 的读写要串行）。
- 建议 `RealApkDataService` 内部：
  - 一个 `Mutex` 保护当前打开的 ApkModule；
  - 每个 suspend 方法 `withContext(Dispatchers.IO) { ... }`；
  - 大文件操作（反汇编）时向 UI 暴露进度（可用 StateFlow<Float>，UI 已有 CircularProgressIndicator）。

### 7.6 文件与 Uri 处理

- **输入**：SAF uri → `contentResolver.openInputStream` → 复制到 `context.cacheDir/apk/input.apk`（可重新打开）。
- **工作目录**：`context.cacheDir/work/<apkName>/` 放 smali 目录、解出的 xml 文本。
- **输出**：先写 `cacheDir/out/unsigned.apk` → 签名 → `contentResolver.openOutputStream(uri)` 写入。
- 处理完可清理 cacheDir（Android 会自动回收，亦可主动删）。

### 7.6.1 统一输出目录（设置里配置）

- `data/OutputConfig.kt`：SharedPreferences 存 SAF tree Uri（未配置=默认目录）。
- 默认目录：`context.getExternalFilesDir(null)/output`（App 私有外部存储，无需权限）。
- 设置页：`OpenDocumentTree` 选目录 → `takePersistableUriPermission(uri, READ|WRITE)` → 存 tree Uri；可"恢复默认"。
- `buildAndSign()` **无参**：输出到配置目录。SAF tree 用 `DocumentsContract.createDocument(contentResolver, tree, mime, name)` → `openOutputStream`；默认目录直接写 File。
- 分享本地文件需 **FileProvider**：`res/xml/file_paths.xml`（`<external-files-path name="output" path="output/"/>`）+ manifest 里注册 provider（authorities=`${applicationId}.fileprovider`）。

### 7.7 RealApkDataService 实现清单（已实现，给接手 AI 参考）

1. build.gradle.kts 加入 apksig 依赖（ARSCLib/baksmali/smali 已有）。
2. 新建 `data/RealApkDataService.kt`，构造参数 `(context: Context)`。
3. 实现 `loadApk(uri)`：复制到 cache → ApkModule.loadApkFile → 读 Manifest → 填 ApkInfo（fileName、fileSize、resourceCount 等）。
4. `listDexFiles`：从 ApkModule 读取 dex 列表（classes.dex/classes2.dex…，可用 `getDexFileList` 或解析）。
5. `listSmaliFiles`：baksmali 全量反汇编到 work 目录（缓存），返回文件相对路径列表。
6. `readSmaliFile`：读文本；`saveSmaliFile`：写文本（脏标记由 UI 管）。
7. `assembleDex`：smali 全量汇编 → `apkModule.addDexFile` 替换。
8. `listResourceTypes` / `listResources`：遍历 TableBlock → ResourceType 名称 + 条目列表（id/hexId/name/value/configs）。
9. `saveResourceValue` / `renameResource`：`entry.setValueAsString` / `resource.setName`。
10. `listXmlFiles` / `readXmlFile` / `saveXmlFile`：decodeXMLFile / XMLEncodeSource 替换。
11. `buildAndSign`：writeApk → apksig 签名 → 写入 SAF uri → SavedApkStore.add → BuildResult。
12. `App.kt` 默认参数 `MockApkDataService()` 改为 `RealApkDataService(LocalContext.current)`（注意 App 无 context，可在 MainActivity 传入或在 App 内取 LocalContext）。
13. 所有引擎操作加进度/错误处理；验证每个 Result 分支。

### 7.8 已知限制（写进产品说明）
- 只能处理**未加固** APK；全部工作在 App 私有目录，无需 Root。
- ARSC 编辑直改二进制资源表，规避 aapt2 兼容问题（用户明确要求）。

---

## 8. 已踩坑记录（勿重踩）

| # | 坑 | 结论/规避 |
| --- | --- | --- |
| 1 | `MiuixTheme {}` 无参不跟随系统深色 | 必须 `MiuixTheme(controller = ThemeController(ColorSchemeMode.System))` |
| 2 | 0.9.3 没有 `Divider` | 用 `HorizontalDivider` / `VerticalDivider` |
| 3 | 0.9.3 组件包不是 `topBar` | TopAppBar 等都在 `top.yukonga.miuix.kmp.basic` |
| 4 | `LazyListScope.items` 不能嵌在 ColumnScope（SectionCard）里 | 必须直接写在 LazyColumn 作用域 |
| 5 | `TextFieldState.edit` 用 `it` 编译报错 | edit 块 receiver 是 TextFieldBuffer，用 `this`/隐式 receiver |
| 6 | TextFieldState 无 `setSelection` | 是 `selection` 属性赋值：`state.edit { selection = TextRange(s, e) }` |
| 7 | SDK 平台 `platforms;android-37.0` 找不到 | 软链 `android-37 -> android-37.0` |
| 8 | `ActivityResultContracts.CreateDocument` 传 Intent 报错 | 传 **String 文件名** |
| 9 | apktool 在 Android17 反编译 arsc 回编译炸 | 弃用，用 ARSCLib 直改（用户断言+决策） |
| 10 | miuix-icons 漏依赖 → `Unresolved reference 'extended'` | 必须加 `miuix-icons-android:0.9.3` |
| 11 | `NavigationBarItem` 参数名是 `title` 报 "No parameter" | 参数名是 **`label`** |
| 12 | apply 块内 `type = type` 报 val 重赋值 | Intent 用 `this.type = type` |
| 13 | 属性 `mode` + 自定义 `setMode()` 冲突 | JVM 签名 clash，去掉自定义 setter，直接赋值属性 |
| 14 | `/tmp` 被系统清空（已 2 次） | 持久资料放 `/home/user` |
| 15 | 深色模式硬编码白色窗口背景/状态栏 | themes.xml 只留父主题；enableEdgeToEdge 自动适配 |
| 16 | 嵌套 Scaffold 双重 padding | 所有 Scaffold `contentWindowInsets = WindowInsets(0,0,0,0)`，insets 交给 TopAppBar |
| 17 | **OverlayDropdownPreference 展开即闪退** | 真根因（logcat 实证）：miuix 0.9.3 `MiuixPopupHost` 内部用 `NavigationBackHandler`（androidx.navigationevent.compose），要求宿主提供 `LocalNavigationEventDispatcherOwner`；本项目未用 Navigation Compose → 展开任意 Overlay 弹窗抛 `IllegalStateException: No NavigationEventDispatcher was provided`。修复：依赖加 `androidx.navigationevent:navigationevent-compose-android:1.1.2`，MainActivity 根部 `CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides remember { AppNavigationEventDispatcherOwner() })`（owner 返回 `NavigationEventDispatcher()` 无参实例）。排查：root 手机 `logcat --pid=$(pidof -s com.apkeditor.miuix)` 抓 FATAL 堆栈 |
| 18 | 版本号漂移（0.2.1→0.2.2） | 用户指示“版本号不对不要紧，别浪费时间”；文件名与 versionName 允许不一致，记录以 build.gradle 实际值为准 |
| 19 | `DexFileFactory` Unresolved | 它在 `org.jf.dexlib2.DexFileFactory`，**不是** `org.jf.dexlib2.dexbacked.DexFileFactory` |
| 20 | `XMLDocument.write(XmlSerializer)` 报错 | ARSCLib 的序列化方法是 **`doc.serialize(serializer)`**；且 `XMLFactory.newSerializer` 建议传 `File` 避免 StringWriter 重载歧义 |
| 21 | `zipEntryMap.iteratorWithPath{...}` 返回元素 | 元素类型是 **`InputSource`**，要取 `it.alias` 才是路径字符串 |
| 22 | `ApkSigner.Builder(setOf(...))` 报 None of candidates | 构造需要 **`List<SignerConfig>`**，`setOf` 返回 Set 不行，用 `listOf` |
| 23 | `SignerConfig.Builder` 第三参 | 是 **`List<X509Certificate>`**，不是单个证书，传 `listOf(cert)` |
| 24 | `MinSdkVersion` Unresolved | apksig 无此公开类，`setMinSdkVersion(int)` 直接传 35 |
| 25 | Android 上 `sun.security.x509` 不存在 | 自签证书用 BouncyCastle；**必须同时加 bcpkix-jdk18on**（JcaX509v3CertificateBuilder 等都在 bcpkix） |
| 26 | `setOf`/`listOf` 传给 Java List 参数 | Kotlin `Set` 不能当 Java `List` 用；统一 `listOf(...)` |
| 27 | App 里 `val x = service ?: remember{...}` 报 @Composable | elvis/表达式右侧不能调 @Composable；拆成 `val x = if(service!=null) service else { remember{...} }` 语句块 |
| 28 | 多 dex 并行反编译 | 锁内只解 dex 字节到临时文件，锁外 `coroutineScope+async` 并行 baksmali，避免模块被 Mutex 长时间占用 |
| 29 | 回编译"未知目录" | SAF tree 目录易失效。**改为优先用 `MANAGE_EXTERNAL_STORAGE`（所有文件访问）直写公共目录** `/storage/emulated/0/ApkEditorMiuix/output`；SAF 失败自动回退默认私有目录；设置页可跳 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 授权 |
| 30 | 从 dex 返回又变慢（重复反编译） | `listSmaliFiles` 每次进入都会重新 baksmali。加模块级 `smaliCache: HashMap<String, List<String>>`，首次反编译后缓存文件列表，`loadApk` 时清空，返回时直接命中缓存 |

---

## 9. 交付与文档规范（用户强制要求）

1. **每次功能更改后必须同步更新** `docs/编译教程-小白版.md` 与 `docs/开发文档.md`（用户原话："每次更改时 都同步更新教程和小白编译和开发文档"）。
2. 交付物为**完整 zip**：源码（排除 build/.gradle/local.properties/.idea）+ docs + APK，放进 `交付/` 目录，用 `present_files` 交付。
3. 命名规范：
   - APK：`ApkEditorMiuix-vX.Y.Z-功能说明.apk`
   - 完整包：`ApkEditorMiuix-完整包.zip`（内含 `先看这个-使用说明.txt`）
   - 源码包：`ApkEditorMiuix-源码.zip`
4. 打包命令（参考）：
   ```bash
   cd /home/user/Doubao/chats/8153721457272066
   rsync -a --exclude 'build/' --exclude '.gradle/' --exclude 'local.properties' \
         --exclude '.idea/' --exclude '*.iml' ApkEditorMiuix/ /tmp/zipstage/ApkEditorMiuix/
   cp 交付/ApkEditorMiuix-vX.Y.Z-*.apk /tmp/zipstage/APK/
   cd /tmp/zipstage && zip -r -q 交付/ApkEditorMiuix-完整包.zip .
   ```
5. 版本号与 versionName 保持一致；改动功能必须升版本并写进文档"修改记录"。

---

## 10. 修改记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v0.1 | 2026-09-12 | UI 预览版：Mock 数据，全部编辑页，编译通过 |
| v0.1.1 | 2026-09-12 | 修复深色模式（ThemeController + edge-to-edge），清理写死颜色 |
| v0.2.0 | 2026-09-12 | 底部三栏导航（主页/保存的APK/设置）；设置页主题切换（OverlayDropdownPreference + ThemeState）；保存的APK（SharedPreferences + 分享/删除）；insets 规范化；versionName=0.2.0 |
| v0.2.1 | 2026-09-12 | 修复：设置页展开"主题"下拉闪退（初判双层 Scaffold，加 renderInRootScaffold=false） |
| v0.2.2 | 2026-09-12 | 真根因修复：logcat 实证为 MiuixPopupHost 缺 LocalNavigationEventDispatcherOwner；加 navigationevent-compose-android 依赖 + MainActivity 全局提供 owner；恢复 renderInRootScaffold 默认 |
| v0.3.0（规划） | - | 接入真实引擎：RealApkDataService（ARSCLib + smali/baksmali + apksig） |

| v0.1（固定版） | 2026-09-12 | 用户要求版本号固定为 0.1 不再改动；APK 内容页改为 MT 风格单排文件列表（AndroidManifest.xml / classes*.dex / resources.arsc / res/ / assets/ / lib/ / META-INF/），点击直达对应编辑页 |

| v0.1（真实引擎） | 2026-09-12 | 真实引擎接入：RealApkDataService（ARSCLib 解析/编辑 + smali/baksmali 反汇编汇编 + apksig 签名）；多 DEX 并行反编译；res 搜索；统一输出目录（设置可配置）+ FileProvider 分享；版本号保持 0.1 |

| v0.1（输出容错） | 2026-09-12 | 回编译输出改全部文件权限(MANAGE_EXTERNAL_STORAGE)直写公共目录 + SAF 失败回退；smali 反编译加缓存，返回不重复编译 |
| v0.1（可靠重打包） | 2026-09-12 | 修复"回编译产物无意义"：弃用 ARSCLib 全量 writeApk，改为 **zip 逐条拷贝 + 只替换修改过的 dex/xml/arsc**（未修改条目逐字节原样保留 → 未修改直接回编译 = 原 APK 拷贝 + 重签名，必然可用）；反编译目录改 **smali/smali_classesN 标签一一对应 classesN.dex**；APK 内容页改 **zip 直接解压预览**（打开即出文件树，快）；反汇编 Opcode 改用 Opcodes.getDefault() |


---

## 11. 可靠重打包方案（v0.1-可靠重打包版，关键）

### 背景
用户反馈「回编译产物无意义，即使未修改只反编译+回编译」。
JVM 复现结论：
- **ARSCLib writeApk（不改）对真实 APK 无损**：badging 正常、185 条目、9 个 dex 大小逐字节一致。
- **dex 往返（baksmali→smali→assemble）有效**：classes2.dex 140 类全保留、产物可再解析。
- 大 dex（9MB+）baksmali 反编译吃内存/慢；反汇编 Opcode 版本与 targetSdk 不匹配也是隐患（原先写死 forApi(35)，targetSdk 37）。

### 新策略：zip 拷贝 + 只替换修改文件（根治）
不再用 ARSCLib `writeApk()` 全量重写（会连未修改的 arsc/dex 一起重编码）。
改为遍历原始 APK 的 zip 条目，**仅把修改过的文件用新字节替换，其余条目逐字节原样拷贝**：
- 修改的 dex：smali 汇编产物文件 → `modifiedDex[dexName]=outDex`
- 修改的 xml：`XMLEncodeSource(pkg, XMLFileParserSource(path,tmp)).getBytes()` 取二进制 → `modifiedXml[path]=bytes`
- 修改的 arsc：`m.tableBlock.writeBytes(tmp)` → `modifiedArsc=true`
- `writeUnsignedApk()`：`ZipOutputStream` 逐条拷贝，命中修改记录则写新字节，否则 `zf.getInputStream(e).copyTo(zos)`
- 然后 apksig v1+v2 签名。

效果：未修改任何内容直接回编译 = 原 APK 逐字节拷贝 + 新签名，**产物必然可用**；只改某 dex 则仅该 dex 替换。

### smali 标签对应 dex（MT/apktool 风格）
`smaliDirName(dexName)`：classes.dex → `smali`，classesN.dex → `smali_classesN`。
反编译文件路径带该前缀，用户看到 smali_classes2/... 即知属于 classes2.dex。

### APK 内容页直接解压预览（快）
`listApkContents()` 用 `java.util.zip.ZipFile` 直接列全部条目（path+size），不做解析。
ApkInfoScreen 的 LaunchedEffect 并行加载 info(解析) 与 contents(zip 预览)，文件列表显示真实条目与真实大小，进入具体编辑页才做深度解码。

### 反汇编 Opcode
baksmali 反汇编统一用 `Opcodes.getDefault()`（不再写死 forApi(35)），兼容任意 targetSdk 编译的 dex。


---

## 12. 待办 / 还有要干的事情（接手清单）

按优先级排序。做完一项在 `[ ]` 打 `[x]` 并同步本节。

### 已完成的（勿重复做）
- [x] UI 层完整（Miuix，主页/保存的APK/设置三栏，主题切换含深色）
- [x] 真实引擎接入（ARSCLib 解析/编辑 + smali/baksmali 反汇编汇编 + apksig 签名）
- [x] 多 DEX 同时反编译（smali/smali_classesN 标签对应 classesN.dex）
- [x] APK 内容页 zip 直接解压预览（快）
- [x] res/ XML 搜索
- [x] 回编译产物修复：zip 拷贝 + 只替换修改过的文件（未修改=原APK拷贝+重签名）
- [x] 统一输出目录（全部文件权限 / SAF / 私有目录 三级容错）+ FileProvider 分享
- [x] smali 反编译缓存（返回不重复编译）

### 待办（下一步）
- [ ] **引入 sora-editor（Rosemoe）升级 smali/XML 编辑器**：语法高亮(smali/java/xml)、行号、代码折叠、查找替换、自动补全。NP 管理器同款，可直接对标 MT 手感。有 Compose 版本。
- [ ] **DEX 编辑升级为 MT 式导航**：类列表 → 方法列表 → 单方法编辑（当前是整份 smali 文本编辑，对应 MT 的"文件级"，非"类/方法级"）
- [ ] **zipalign 对齐**：重打包签名前对 APK 做 4 字节对齐（Android 高版本要求，当前未做，暂不影响安装但建议补）
- [ ] **R8 混淆/裁剪瘦身**：APK 51MB → 目标 20-30MB（删未用库代码）
- [ ] 包名修改 / APK 共存（改 package 需要动 manifest + dex 引用，改动大，优先级低）
- [ ] 去除签名校验（注入类/so，参考 MP 的 SignatureKillerUtil）
- [ ] 资源混淆 / 反混淆（参考 APKEditor 的 refactor/protect）
- [ ] res/ 内图片、文件级替换编辑
- [ ] 大 dex（16MB classes.dex）反编译稳定性/内存实测（baksmali 全量很吃内存，考虑分块/后台线程）

### 关键技术参考（已在打包内）
- **MP-Manager 源码**（`参考/MP-Manager`）：dexlib2 + Opcodes.getDefault() + DexPool 写回；自研 AXML 解码器；apksig 打包；SignatureKiller/PairipRemover 等逆向工具
- **ARSCLib 源码**（`参考/ARSCLib`）：resources.arsc 与二进制 XML 的底层解析/编码
- **miuix**：UI 库为 maven 依赖（io.github.yukonga），gradle 自动拉取，无需源码
- 引擎依赖坐标：ARSCLib V1.4.0 / smali baksmali dexlib2 util 2.5.2 / apksig 8.13.2 / bcprov+bcpkix 1.78.1 / navigationevent-compose-android 1.1.2
