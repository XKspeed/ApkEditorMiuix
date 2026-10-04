package com.apkeditor.miuix.data

/**
 * smali 文本解析器：类头信息 + 方法块定位。
 *
 * 纯字符串扫描，不依赖反编译引擎，读一次文件即可同时得到
 * 类详情（类名/访问标志/父类/接口）和全部方法块的行区间。
 *
 * 方法块定义：".method …" 行到对应 ".end method" 行（含首尾）。
 * smali 语法上方法块不会嵌套，扫描时遇到第一个 ".end method" 即闭合。
 */
object SmaliParser {

    /** 一个方法块在文件中的位置与解析结果 */
    data class MethodBlock(
        /** 起始行（".method" 行，0 起） */
        val start: Int,
        /** 结束行（".end method" 行，含） */
        val end: Int,
        val info: SmaliMethodInfo,
    )

    /** 解析结果：类详情 + 全部方法块 */
    data class Parsed(
        val detail: SmaliClassDetail,
        val blocks: List<MethodBlock>,
    )

    /** 把 "Lcom/x/Foo;" 转成点分名 "com.x.Foo"；非 L 开头原样返回 */
    fun dottedType(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("L") && t.endsWith(";") -> t.substring(1, t.length - 1).replace('/', '.')
            else -> t
        }
    }

    fun parse(text: String): Parsed {
        val lines = text.split("\n")
        var className = ""
        var access = ""
        var superName = ""
        val interfaces = mutableListOf<String>()
        val blocks = mutableListOf<MethodBlock>()

        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            when {
                t.startsWith(".class") -> {
                    // .class public final Lcom/x/Foo;
                    val toks = t.split(whitespace)
                    val typeTok = toks.lastOrNull { it.startsWith("L") && it.endsWith(";") }
                    access = toks.drop(1)
                        .filter { it != typeTok && !it.startsWith("L") }
                        .joinToString(" ")
                    if (typeTok != null) className = dottedType(typeTok)
                }
                t.startsWith(".super") -> {
                    val typeTok = t.split(whitespace).lastOrNull { it.startsWith("L") }
                    if (typeTok != null) superName = dottedType(typeTok)
                }
                t.startsWith(".implements") -> {
                    val typeTok = t.split(whitespace).lastOrNull { it.startsWith("L") }
                    if (typeTok != null) interfaces.add(dottedType(typeTok))
                }
                t.startsWith(".method") -> {
                    val start = i
                    var end = -1
                    var j = i + 1
                    while (j < lines.size) {
                        if (lines[j].trim() == ".end method") {
                            end = j
                            break
                        }
                        j++
                    }
                    if (end >= 0) {
                        // 解析声明行：.method <访问标志…> name(proto)ret
                        val body = t.removePrefix(".method").trim()
                        val toks = body.split(whitespace).filter { it.isNotEmpty() }
                        val sigIdx = toks.indexOfFirst { it.contains("(") }
                        val mAccess = if (sigIdx > 0) toks.subList(0, sigIdx).joinToString(" ") else ""
                        val sig = if (sigIdx >= 0) toks[sigIdx] else body
                        val name = sig.substringBefore("(")
                        val proto = if (sig.contains("(")) "(" + sig.substringAfter("(") else ""
                        blocks.add(
                            MethodBlock(
                                start = start,
                                end = end,
                                info = SmaliMethodInfo(
                                    index = blocks.size,
                                    header = t,
                                    access = mAccess,
                                    name = name,
                                    proto = proto,
                                ),
                            )
                        )
                        i = end + 1
                        continue
                    }
                    // 缺 .end method 的异常文件：跳过该行继续
                }
            }
            i++
        }

        return Parsed(
            detail = SmaliClassDetail(
                className = className,
                access = access,
                superName = superName,
                interfaces = interfaces,
                methods = blocks.map { it.info },
            ),
            blocks = blocks,
        )
    }

    private val whitespace = Regex("\\s+")
}
