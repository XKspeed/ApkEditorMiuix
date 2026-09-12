# ApkEditor·Miuix

类 MT 管理器的 APK 反编译编辑工具（仅 APK 编辑模块，无文件管理器），UI 采用 **Miuix（小米 HyperOS 风格 Compose 组件库）**。支持 DEX/Smali 反编译编辑、resources.arsc 资源修改、二进制 XML 编辑、回编译签名。

> 版本号固定 **v0.1**（按用户要求不再变更）

## 功能

- [x] Miuix / HyperOS 风格 UI（主页 / 保存的 APK / 设置三栏，主题跟随系统与手动切换）
- [x] 打开 APK 直接 zip 解压预览文件树（快），进入具体编辑页才深度解析
- [x] 多 DEX 同时反编译（baksmali），目录以 `smali` / `smali_classesN` 标签一一对应 `classesN.dex`
- [x] smali 反编译缓存：返回不重复编译
- [x] DEX 汇编回编译（smali）后自动替换回 APK
- [x] resources.arsc 资源浏览 / 值修改 / 资源重命名
- [x] 二进制 XML（含 AndroidManifest.xml）解码为文本编辑后编码回写
- [x] res/ 下 XML 文件列表搜索
- [x] 回编译 + apksig 签名（v1+v2），输出统一目录（全部文件权限 / SAF / 私有目录三级容错）
- [x] 重打包采用「zip 拷贝 + 只替换修改过的文件」：未修改的内容直接回编译 = 原 APK 拷贝 + 重签名，保证产物可用

## 技术栈

| 层 | 技术 |
| --- | --- |
| 语言 | Kotlin 2.4.20 |
| UI | Jetpack Compose + Miuix 0.9.3（HyperOS 风格） |
| 引擎 | ARSCLib V1.4.0（resources.arsc / 二进制 XML） |
| 引擎 | smali / baksmali / dexlib2 2.5.2（DEX ↔ smali） |
| 签名 | apksig 8.13.2 + BouncyCastle 自签证书 |
| 最低系统 | Android 15（API 35）及以上（无需兼容旧设备） |

## 构建方法

### Android Studio（推荐）
1. Android Studio（Hedgehog 或更新版本）打开项目根目录
2. 等待 Gradle 同步完成（首次需下载依赖，约 3~8 GB 缓存，正常现象）
3. Run ▶ 安装到设备（Android 15+）

### 命令行
```bash
# 需要 JDK 17 与 Android SDK 35/36/37
export JAVA_HOME=<jdk17路径>
export ANDROID_HOME=<sdk路径>
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk（versionName 固定 0.1）
```

### GitHub Actions 自动构建
push 到 `main` 分支后自动在云端编译并上传 APK 制品（见 `.github/workflows/build.yml`），也可在 Actions 页手动触发（Workflow dispatch）。

## 界面结构

```
首页            → 选择 APK 文件（SAF 文件选择器）
  └ APK 信息    → zip 解压预览文件树（AndroidManifest / classes*.dex / resources.arsc / res/ …）
      ├ DEX / Smali 编辑 → dex 列表 → smali 文件树（smali / smali_classesN）→ 代码编辑器
      ├ ARSC 资源编辑    → 资源类型 → 资源条目 → 编辑值与重命名
      ├ XML 文件编辑     → 文件列表（可搜索）→ 文本编辑器
      └ 打包并签名       → 输出到统一目录（设置页可配置）
```

## 说明与免责声明

- 仅支持**未加固**的 APK；仅适配 Android 15+，不考虑旧设备
- 所有文件操作均在应用私有缓存目录进行，回编译产物输出到用户配置的目录
- 本工具仅供学习、研究与个人修改使用，**请勿用于侵权或盗版**；修改他人应用请遵守相关法律法规并尊重开发者权益
- 首次运行自动生成自签名密钥，签名后的 APK 与原签名不一致属正常现象

## 许可证

[Apache License 2.0](LICENSE)（第三方库版权声明见 [NOTICE](NOTICE)）
