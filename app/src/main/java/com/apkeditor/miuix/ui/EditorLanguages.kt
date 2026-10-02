package com.apkeditor.miuix.ui

import android.content.Context
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * TextMate 语法与主题装载（全 app 单次初始化）。
 *
 * 资源（见 app/src/main/assets/textmate/）：
 *  - grammars：smali（source.smali）、XML（text.xml），见 languages.json
 *  - themes：app-light / app-dark（随 MiuixTheme 深浅色切换）
 *
 * 使用约定：
 *  - [init] 幂等，必须在创建任何 TextMateLanguage / TextMateColorScheme 之前调用
 *  - [applyTheme] 只切换主题名，颜色方案由编辑器重建（见 TextEditorScaffold）
 *  - 语法装载失败时 [smali]/[xml] 返回 null，编辑器退化为无高亮（不崩溃）
 */
object EditorLanguages {

    const val THEME_LIGHT = "app-light"
    const val THEME_DARK = "app-dark"

    private const val LANGUAGES_JSON = "textmate/languages.json"

    @Volatile
    private var initialized = false

    /** smali 补全关键字（dalvik 指助记符），交给 sora 的 IdentifierAutoComplete */
    private val smaliKeywords = arrayOf(
        "nop",
        "move", "move/from16", "move/16", "move-wide", "move-wide/from16", "move-object",
        "move-object/from16", "move-result", "move-result-wide", "move-result-object",
        "move-result-boolean", "move-result-byte", "move-result-char", "move-result-short",
        "move-exception",
        "return-void", "return", "return-wide", "return-object", "return-boolean",
        "return-byte", "return-char", "return-short",
        "const/4", "const/16", "const", "const/high16",
        "const-wide/16", "const-wide/32", "const-wide", "const-wide/high16",
        "const-string", "const-string/jumbo", "const-class",
        "monitor-enter", "monitor-exit", "check-cast", "instance-of",
        "array-length", "new-instance", "new-array", "filled-new-array",
        "filled-new-array/range", "fill-array-data",
        "throw", "goto", "goto/16", "goto/32", "packed-switch", "sparse-switch",
        "cmpl-float", "cmpg-float", "cmpl-double", "cmpg-double", "cmp-long",
        "if-eq", "if-ne", "if-lt", "if-ge", "if-gt", "if-le",
        "if-eqz", "if-nez", "if-ltz", "if-gez", "if-gtz", "if-lez",
        "aget", "aget-wide", "aget-object", "aget-boolean", "aget-byte", "aget-char", "aget-short",
        "aput", "aput-wide", "aput-object", "aput-boolean", "aput-byte", "aput-char", "aput-short",
        "iget", "iget-wide", "iget-object", "iget-boolean", "iget-byte", "iget-char", "iget-short",
        "iput", "iput-wide", "iput-object", "iput-boolean", "iput-byte", "iput-char", "iput-short",
        "sget", "sget-wide", "sget-object", "sget-boolean", "sget-byte", "sget-char", "sget-short",
        "sput", "sput-wide", "sput-object", "sput-boolean", "sput-byte", "sput-char", "sput-short",
        "neg-int", "not-int", "neg-long", "not-long", "neg-float", "neg-double",
        "int-to-long", "int-to-float", "int-to-double", "long-to-int", "long-to-float",
        "long-to-double", "float-to-int", "float-to-long", "float-to-double",
        "double-to-int", "double-to-long", "double-to-float",
        "int-to-byte", "int-to-char", "int-to-short",
        "add-int", "sub-int", "mul-int", "div-int", "rem-int",
        "and-int", "or-int", "xor-int", "shl-int", "shr-int", "ushr-int",
        "add-long", "sub-long", "mul-long", "div-long", "rem-long",
        "and-long", "or-long", "xor-long", "shl-long", "shr-long", "ushr-long",
        "add-float", "sub-float", "mul-float", "div-float", "rem-float",
        "add-double", "sub-double", "mul-double", "div-double", "rem-double",
        "add-int/2addr", "sub-int/2addr", "mul-int/2addr", "div-int/2addr", "rem-int/2addr",
        "rsub-int", "rsub-int/lit8",
        "add-int/lit8", "rsub-int/lit8", "mul-int/lit8", "div-int/lit8", "rem-int/lit8",
        "and-int/lit8", "or-int/lit8", "xor-int/lit8", "shl-int/lit8", "shr-int/lit8", "ushr-int/lit8",
        "add-int/lit16", "rsub-int/lit16", "mul-int/lit16", "div-int/lit16", "rem-int/lit16",
        "and-int/lit16", "or-int/lit16", "xor-int/lit16",
        "invoke-virtual", "invoke-super", "invoke-direct", "invoke-static", "invoke-interface",
        "invoke-virtual/range", "invoke-super/range", "invoke-direct/range",
        "invoke-static/range", "invoke-interface/range",
    )

    /**
     * 幂等初始化：注册 assets 文件提供器 → 装主题 → 装语法。
     * 失败不抛出（语法不可用时编辑器退化为无高亮）。
     */
    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        runCatching {
            val app = context.applicationContext
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(app.assets))

            val themeRegistry = ThemeRegistry.getInstance()
            listOf(THEME_LIGHT to false, THEME_DARK to true).forEach { (name, dark) ->
                runCatching {
                    val path = "textmate/themes/$name.json"
                    val stream = FileProviderRegistry.getInstance().tryGetInputStream(path)
                        ?: error("主题资源不存在：$path")
                    themeRegistry.loadTheme(
                        ThemeModel(
                            IThemeSource.fromInputStream(stream, path, null),
                            name,
                        ).apply { isDark = dark }
                    )
                }.onFailure { it.printStackTrace() }
            }
            themeRegistry.setTheme(THEME_LIGHT)

            GrammarRegistry.getInstance().loadGrammars(LANGUAGES_JSON)
            initialized = true
        }.onFailure { it.printStackTrace() }
    }

    /** 随 app 深浅色切换 TextMate 主题（同名重复 setTheme 无害） */
    fun applyTheme(dark: Boolean) {
        if (!initialized) return
        runCatching {
            ThemeRegistry.getInstance().setTheme(if (dark) THEME_DARK else THEME_LIGHT)
        }
    }

    /** smali 语言（带指令补全）；语法未装载成功时返回 null */
    fun smali(): Language? = runCatching {
        TextMateLanguage.create("source.smali", true).apply {
            setCompleterKeywords(smaliKeywords)
        }
    }.getOrNull()

    /** XML 语言；语法未装载成功时返回 null */
    fun xml(): Language? = runCatching {
        TextMateLanguage.create("text.xml", true)
    }.getOrNull()

    /** 缓存的颜色方案：ThemeRegistry 每注册一个监听就长期持有，避免反复创建泄漏 */
    @Volatile
    private var cachedScheme: TextMateColorScheme? = null

    /**
     * 当前主题对应的颜色方案（进程内单例）。
     *
     * ⚠️ TextMateColorScheme 的构造器**不会**应用主题（rawTheme 为空），
     * 必须显式 setTheme(model)：既让主题色生效，也顺带注册 ThemeChangeListener，
     * 之后切深浅色会自动 onChangeTheme 刷新。
     */
    fun colorScheme(): TextMateColorScheme? {
        cachedScheme?.let { return it }
        return runCatching {
            val reg = ThemeRegistry.getInstance()
            val model = reg.currentThemeModel ?: return null
            val scheme = TextMateColorScheme.create(reg)
            scheme.setTheme(model)
            cachedScheme = scheme
            scheme
        }.getOrNull()
    }
}
