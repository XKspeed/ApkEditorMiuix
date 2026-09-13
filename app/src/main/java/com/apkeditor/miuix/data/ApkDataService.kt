package com.apkeditor.miuix.data

/**
 * APK 数据服务接口。
 * 当前 UI 预览版使用 [MockApkDataService]（示例数据）；
 * 后续接入真实反编译引擎后替换为 [RealApkDataService]。
 */
interface ApkDataService {

    /** 从 SAF Uri 加载 APK，返回基本信息 */
    suspend fun loadApk(uri: String): Result<ApkInfo>

    /** 列出 APK 内的 DEX 文件 */
    suspend fun listDexFiles(): Result<List<DexEntry>>

    /** 将指定 DEX 反汇编为 smali 文件列表（相对路径） */
    suspend fun listSmaliFiles(dexName: String): Result<List<String>>

    /** 读取 smali 文件文本 */
    suspend fun readSmaliFile(dexName: String, filePath: String): Result<String>

    /** 保存 smali 文件文本 */
    suspend fun saveSmaliFile(dexName: String, filePath: String, content: String): Result<Unit>

    /** 将修改后的 smali 汇编回 DEX 并替换到 APK */
    suspend fun assembleDex(dexName: String): Result<Unit>

    /** 列出 ARSC 资源类型 */
    suspend fun listResourceTypes(): Result<List<ResourceTypeInfo>>

    /** 列出指定类型的资源条目 */
    suspend fun listResources(type: String): Result<List<ResourceEntryInfo>>

    /** 修改资源字符串值 */
    suspend fun saveResourceValue(id: Int, qualifiers: String?, newValue: String): Result<Unit>

    /** 重命名资源 */
    suspend fun renameResource(id: Int, newName: String): Result<Unit>

    /** 直接解压 APK 预览全部内容（快，不做解析） */
    suspend fun listApkContents(): Result<List<ApkEntry>>

    /** 列出 APK 内可编辑的 XML 文件 */
    suspend fun listXmlFiles(): Result<List<XmlFileInfo>>

    /** 读取二进制 XML 解码后的文本 */
    suspend fun readXmlFile(path: String): Result<String>

    /** 将文本编码回二进制 XML 并替换 */
    suspend fun saveXmlFile(path: String, content: String): Result<Unit>

    /** 重新打包 APK 并签名，输出到配置的统一目录 */
    suspend fun buildAndSign(): Result<BuildResult>
}
