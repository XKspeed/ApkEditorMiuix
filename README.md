```markdown
<div align="center">

# ApkEditor·Miuix

一个类 MT 管理器的 **APK 反编译编辑模块**（不含文件管理器），UI 基于 [Miuix](https://github.com/compose-miuix-ui/miuix)（小米 HyperOS 风格 Compose 组件库）。

![Android](https://img.shields.io/badge/Android-15%2B%20(API%2035)-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Miuix-4285F4)
![License](https://img.shields.io/badge/License-GPL--3.0-blue)

<br>

围绕「**打开 APK → 改内容 → 回编译 → 得到可用产物**」这条主链路设计：支持 DEX/Smali 反编译编辑、`resources.arsc` 资源修改、二进制 XML 编辑、快速改版本号，以及回编译签名。

</div>

---

### 📌 基本信息

| 项目 | 说明 |
| :--- | :--- |
| **当前版本** | `0.11`（`versionCode 2`） |
| **最低系统** | **Android 15（API 35）** 及以上 |
| **支持范围** | 仅支持**未加固**的 APK |

---

## ✨ 功能

### 打开与浏览
- **内置文件浏览器**：打开 APK 后先以 zip 解压方式预览文件树（快），进入具体编辑页才做深度解析
- **「保存的 APK」栏**：集中查看回编译产物，文件被外部删除后失效记录自动清理
- **主页交互**：
  - 点顶栏 → 返回上一级
  - 长按顶栏 → 输入路径跳转
  - 返回键 → 逐级退出

### DEX / Smali
- **多 DEX 并行反编译**（baksmali），目录以 `smali` / `smali_classesN` 与 `classesN.dex` 一一对应
- **反编译结果缓存**，返回上一页不重复编译
- **MT 式类 / 方法导航**：smali 文件 → 类详情（类头 + 方法列表）→ 单方法编辑，另提供跨 DEX「所有类」列表
- **汇编回编译**（smali）后自动替换回待打包内容

### 资源与 XML
- `resources.arsc` 资源浏览、值修改、资源重命名
- 二进制 XML（含 `AndroidManifest.xml`）解码为文本编辑，再编码回写
- `res/` 下 XML 文件列表与搜索

### 快速编辑
- 就地读取 / 修改 `versionName` 与 `versionCode`，直接产出新 APK，**无需走完整反编译流程**

### 编辑体验
- smali / XML 语法高亮与查找替换（sora-editor）
- `.method` 起止行整行标记

### 日志
- **正式版同样记录日志** —— 用户遇到问题时可直接在应用内导出
- 三档级别：

| 级别 | 内容 |
| :--- | :--- |
| **详细** | 记录每一步操作，排查最有效 |
| **简略** | 关键节点与全部错误（默认） |
| **关闭** | 不记录 |

- 日志页支持：分享文件、分享文本、复制全部、清空
- 单文件循环写入（上限 **2 MB**，超出自动截断前段），后台线程写盘，不阻塞界面

---

## 🛠️ 技术栈

| 用途 | 技术 |
| :--- | :--- |
| 语言 | Kotlin 2.4.20 |
| UI | Jetpack Compose + Miuix 0.9.4-rc01（HyperOS 风格） |
| 资源 / 二进制 XML | [ARSCLib](https://github.com/REAndroid/ARSCLib)（见下方说明） |
| DEX ↔ smali | smali / baksmali / dexlib2 2.5.2 |
| 签名 | apksig 8.13.2 + BouncyCastle |
| 编辑器 | sora-editor 0.23.6 |

### 依赖说明：ARSCLib

`com.github.REAndroid:ARSCLib:31d559ff78`（锚定具体提交，非 tag）

上游 `V1.4.0` 标签缺少提交 `31d559ff78`（*XML Support DYNAMIC_REFERENCE and DYNAMIC_ATTRIBUTE data types*）。该修复之前，处理动态资源转换时不会保留动态标签，而是把它错误地转成静态资源，导致回编译产物在设备上致命报错。因此这里指向包含修复的提交以保证产物正确。

---

## 🚀 构建

### Android Studio

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle 同步完成（首次需下载依赖）
3. Run ▶ 安装到设备（Android 15+）

### 命令行

> 需要 JDK 17 与 Android SDK

```bash
export JAVA_HOME=<jdk17 路径>
export ANDROID_HOME=<sdk 路径>

./gradlew :app:assembleDebug        # 产物 app/build/outputs/apk/debug/
./gradlew :app:assembleRelease      # 产物未签名，需自行签名
```

云编译

仓库内置两条 GitHub Actions 工作流：

工作流 触发 产物
build.yml push 到 main 或手动触发 已用 debug 密钥签名的 debug APK
build_release.yml push 到 main 或手动触发 Release APK（R8 混淆 + 固定密钥 V2 签名）

Release 工作流从仓库 Secrets 读取签名密钥，密钥不进入代码库。流程为：

1. R8 构建出未签名包
2. 从 Secrets 解出密钥
3. zipalign
4. apksigner 仅启用 V2 签名
5. 校验签名方案（断言 V1/V3 为 false、V2 为 true）后上传制品

---

🔐 签名与升级

· 正式版使用固定密钥签名，因此后续版本可以直接覆盖安装升级
· 升级前提：versionCode 必须递增、applicationId 不可变更
· ⚠️ 更换签名密钥会导致已安装用户无法覆盖升级（需卸载重装），请勿随意更换

测试界面可见性

「界面测试」页仅对非官方签名的构建显示：应用在运行时读取自身签名证书指纹，与内置的官方指纹比对，命中则隐藏入口。因此 debug 包与自签名测试包可见，官方发布包不可见。

该机制用于产品体验（正式用户看不到开发入口），不是安全边界 —— 相关代码仍在包内。

---

📁 项目结构

```text
app/src/main/java/com/apkeditor/miuix/
├── ui/                  界面层
│   ├── App.kt           导航与底栏
│   ├── Route.kt         类型安全路由
│   ├── HomePage.kt      文件浏览 / 打开 APK
│   ├── ApkInfoPage.kt   APK 信息与内容树
│   ├── *Page.kt         DEX / Smali / ARSC / XML / 日志等页面
│   └── components/      通用组件
├── data/                数据与引擎层
│   ├── ApkDataService.kt      服务接口
│   ├── RealApkDataService.kt  实现：反编译 / 解析 / 打包 / 签名
│   ├── ApkVersionService.kt   快速编辑（版本号）
│   ├── AxmlVersion.kt         二进制 AXML 版本号读写
│   └── OutputConfig.kt        输出目录与签名开关
├── AppLog.kt            日志系统
└── BuildSignature.kt    签名指纹识别
```

---

📝 说明

· 仅支持未加固的 APK；仅适配 Android 15+
· 文件操作在应用私有缓存目录进行，回编译产物输出到用户配置的目录（公共目录 / SAF / 私有目录 三级容错）
· 重打包采用「zip 拷贝 + 只替换修改过的文件」策略：未修改内容的回编译结果等价于原 APK 副本 + 重签名，保证产物可用
· 打包是否签名由设置页开关决定

---

⚠️ 免责声明

本工具仅供学习、研究与个人修改使用，请勿用于侵权或盗版。修改他人应用请遵守相关法律法规，尊重开发者权益。

---

📄 许可证

GNU General Public License v3.0

第三方库版权声明见 NOTICE，各依赖沿用其自身许可证。

```
