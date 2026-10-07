package com.apkeditor.miuix.data

import java.io.ByteArrayOutputStream

/**
 * Android 二进制 AXML（AndroidManifest.xml）中 versionName / versionCode 的读写与字节级修补。
 *
 * 移植自 ApkVerTool 的 AxmlPatch（纯 JDK 实现，零 Android 依赖）：
 *  - 支持 UTF-8 / UTF-16 两种字符串池编码，MUTF-8 编解码
 *  - versionCode 走 typedValue.data 原地改写（类型 0x10 十进制整数）
 *  - versionName 需要改写字符串池，整池重建为 UTF-8 池
 *
 * 该类只做「字节 → 字节」的处理，不解码成 XML 文本，因此对任意 AXML 都安全。
 */
object AxmlVersion {

    const val TYPE_STRING = 0x03
    const val TYPE_INT_DEC = 0x10
    const val TYPE_INT_BOOLEAN = 0x12

    private class Pool {
        var poolOff = 0
        var poolSize = 0
        var count = 0
        var styleCount = 0
        var flags = 0
        var stringsStart = 0
        var stylesStart = 0
        var strings: Array<String> = emptyArray()
        var utf8 = false
    }

    private class Attr {
        /** 属性结构在文件中的绝对偏移 */
        var absOff = 0

        /** 属性名（来自字符串池） */
        var name = ""

        /** 原始值字符串索引 */
        var rawValue = 0

        /** typedValue 类型 */
        var type = 0

        /** typedValue 数据 */
        var data = 0
    }

    // ---------------- 对外 API ----------------

    /**
     * 返回 [versionName, versionCode]，任一可为 null。
     * versionCode 统一用十进制字符串表示（清单里也可能存成字符串型）。
     */
    fun read(axml: ByteArray): Array<String?> {
        val pool = parsePool(axml)
        val mOff = findElement(axml, pool, "manifest")
        if (mOff < 0) throw Exception("AXML 中找不到 <manifest> 元素")
        var name: String? = null
        var code: String? = null
        for (a in readAttrs(axml, mOff, pool)) {
            if ("versionName" == a.name && a.type == TYPE_STRING && a.data >= 0 && a.data < pool.count) {
                name = pool.strings[a.data]
            } else if ("versionCode" == a.name && a.type == TYPE_INT_DEC) {
                code = a.data.toString()
            } else if ("versionCode" == a.name && a.type == TYPE_STRING && a.data >= 0 && a.data < pool.count) {
                code = pool.strings[a.data]
            }
        }
        return arrayOf(name, code)
    }

    /**
     * 修改 versionName / versionCode。
     *
     * newName 为 null 表示不改名；newCode 为 null 表示不改码。
     * 返回新的 AXML 字节（若改名，字符串池会整体重建为 UTF-8 池）。
     */
    fun patch(axml: ByteArray, newName: String?, newCode: Int?): ByteArray {
        if (newName == null && newCode == null) return axml
        val pool = parsePool(axml)
        val mOff = findElement(axml, pool, "manifest")
        if (mOff < 0) throw Exception("AXML 中找不到 <manifest> 元素")
        var nameAttr: Attr? = null
        var codeAttr: Attr? = null
        for (a in readAttrs(axml, mOff, pool)) {
            if ("versionName" == a.name) nameAttr = a
            else if ("versionCode" == a.name) codeAttr = a
        }
        var out = axml
        if (newCode != null) {
            val ca = codeAttr ?: throw Exception("清单缺少 versionCode 属性，无法修改")
            out = patchInt(out, ca.absOff + 16, newCode)
        }
        if (newName != null) {
            val na = nameAttr ?: throw Exception("清单缺少 versionName 属性（或非字符串型），无法修改")
            if (na.type != TYPE_STRING) {
                throw Exception("versionName 属性类型异常: 0x" + Integer.toHexString(na.type))
            }
            out = rebuildPool(out, na.data, newName)
        }
        return out
    }

    // ---------------- 解析 ----------------

    private fun parsePool(a: ByteArray): Pool {
        if (a.size < 40) throw Exception("AXML 太短")
        if (u16(a, 0) != 0x0003) throw Exception("不是有效的 AXML（magic 错误）")
        val p = Pool()
        p.poolOff = 8
        if (u16(a, 8) != 0x0001) throw Exception("AXML 第一个子块不是字符串池")
        p.poolSize = u32(a, 12)
        p.count = u32(a, 16)
        p.styleCount = u32(a, 20)
        p.flags = u32(a, 24)
        p.stringsStart = u32(a, 28)
        p.stylesStart = u32(a, 32)
        p.utf8 = (p.flags and 0x100) != 0
        if (p.count < 0 || p.count > 100000) throw Exception("字符串池数量异常: " + p.count)
        p.strings = Array(p.count) { "" }
        for (i in 0 until p.count) {
            val off = p.poolOff + p.stringsStart + u32(a, p.poolOff + 28 + 4 * i)
            p.strings[i] = if (p.utf8) readUtf8Str(a, off) else readUtf16Str(a, off)
        }
        return p
    }

    /** 在池后元素中按元素名查找 START_ELEMENT 的绝对偏移，找不到返回 -1。 */
    private fun findElement(a: ByteArray, p: Pool, elementName: String): Int {
        var pos = p.poolOff + p.poolSize
        while (pos + 8 <= a.size) {
            val type = u16(a, pos)
            val size = u32(a, pos + 4)
            if (size <= 0) return -1
            if (type == 0x0102) { // START_ELEMENT
                val nameIdx = u32(a, pos + 20)
                if (nameIdx >= 0 && nameIdx < p.count && elementName == p.strings[nameIdx]) return pos
            }
            if (type != 0x0102 && type != 0x0100 && type != 0x0101 &&
                type != 0x0180 && type != 0x0103 && type != 0x0104
            ) return -1
            pos += size
        }
        return -1
    }

    private fun readAttrs(a: ByteArray, chunkOff: Int, p: Pool): Array<Attr> {
        var attrSize = u16(a, chunkOff + 26)
        val attrStart = u16(a, chunkOff + 24) // 相对 attrExt(chunkOff+16)
        val attrCount = u16(a, chunkOff + 28)
        if (attrSize <= 0) attrSize = 20
        val out = Array(attrCount) { Attr() }
        for (i in 0 until attrCount) {
            val base = chunkOff + 16 + attrStart + i * attrSize
            val x = out[i]
            x.absOff = base
            val nameIdx = u32(a, base + 4)
            x.name = if (nameIdx >= 0 && nameIdx < p.count) p.strings[nameIdx] else ""
            x.rawValue = u32(a, base + 8)
            x.type = a[base + 15].toInt() and 0xFF
            x.data = u32(a, base + 16)
        }
        return out
    }

    // ---------------- 字符串池编解码 ----------------

    private fun readUtf8Str(a: ByteArray, off: Int): String {
        val r1 = var8(a, off)          // [utf16len, next]
        val r2 = var8(a, r1[1])        // [utf8len, next]
        val b = a.copyOfRange(r2[1], r2[1] + r2[0])
        return decodeMutf8(b)
    }

    private fun readUtf16Str(a: ByteArray, off: Int): String {
        val len32 = u32(a, off)
        var dataOff: Int
        var len: Int
        if ((len32 and -0x10000) == 0) {
            len = len32
            dataOff = off + 4
        } else {
            // 兼容旧式 u16 长度
            len = u16(a, off)
            dataOff = off + 2
        }
        if (len < 0 || dataOff + 2 * len + 2 > a.size) {
            // 长度异常则扫描 NUL
            dataOff = off + (if ((len32 and -0x10000) == 0) 4 else 2)
            var end = dataOff
            while (end + 1 < a.size && !(a[end].toInt() == 0 && a[end + 1].toInt() == 0)) end += 2
            len = (end - dataOff) / 2
        }
        val sb = StringBuilder(len)
        for (i in 0 until len) sb.append(u16(a, dataOff + 2 * i).toChar())
        return sb.toString()
    }

    private fun var8(a: ByteArray, off: Int): IntArray {
        val b0 = a[off].toInt() and 0xFF
        if ((b0 and 0x80) != 0) {
            return intArrayOf(((b0 and 0x7F) shl 8) or (a[off + 1].toInt() and 0xFF), off + 2)
        }
        return intArrayOf(b0, off + 1)
    }

    private fun decodeMutf8(b: ByteArray): String {
        val sb = StringBuilder(b.size)
        var i = 0
        while (i < b.size) {
            val x = b[i].toInt() and 0xFF
            val c: Char
            when {
                (x and 0x80) == 0 -> { c = x.toChar(); i += 1 }
                (x and 0xE0) == 0xC0 -> {
                    c = (((x and 0x1F) shl 6) or (b[i + 1].toInt() and 0x3F)).toChar(); i += 2
                }
                else -> {
                    c = (((x and 0x0F) shl 12) or ((b[i + 1].toInt() and 0x3F) shl 6) or
                        (b[i + 2].toInt() and 0x3F)).toChar()
                    i += 3
                }
            }
            sb.append(c)
        }
        return sb.toString()
    }

    private fun encodeMutf8(s: String): ByteArray {
        val o = ByteArrayOutputStream()
        for (i in s.indices) {
            val c = s[i].code
            when {
                c == 0 -> { o.write(0xC0); o.write(0x80) }
                c < 0x80 -> o.write(c)
                c < 0x800 -> { o.write(0xC0 or (c shr 6)); o.write(0x80 or (c and 0x3F)) }
                else -> {
                    o.write(0xE0 or (c shr 12))
                    o.write(0x80 or ((c shr 6) and 0x3F))
                    o.write(0x80 or (c and 0x3F))
                }
            }
        }
        return o.toByteArray()
    }

    // ---------------- 重建字符串池 ----------------

    /** 用 replacement 替换池中第 replaceIdx 个字符串，整池重编码为 UTF-8，返回新文件字节。 */
    private fun rebuildPool(a: ByteArray, replaceIdx: Int, replacement: String): ByteArray {
        val p = parsePool(a)
        if (replaceIdx < 0 || replaceIdx >= p.count) throw Exception("versionName 池索引越界")
        val strings = p.strings.copyOf()
        strings[replaceIdx] = replacement

        val blob = ByteArrayOutputStream()
        val offs = IntArray(p.count)
        for (i in 0 until p.count) {
            offs[i] = blob.size()
            val b8 = encodeMutf8(strings[i])
            writeVar8(blob, strings[i].length)
            writeVar8(blob, b8.size)
            blob.write(b8, 0, b8.size)
            blob.write(0)
        }
        while (blob.size() % 4 != 0) blob.write(0)
        val stringsBlob = blob.toByteArray()

        var styles = ByteArray(0)
        if (p.styleCount > 0 && p.stylesStart > 0 && p.poolSize > p.stylesStart) {
            styles = a.copyOfRange(p.poolOff + p.stylesStart, p.poolOff + p.poolSize)
        }

        val stringsStart = 28 + 4 * p.count
        val stylesStart = stringsStart + stringsBlob.size
        val poolSizeRaw = stylesStart + styles.size
        val poolSize = (poolSizeRaw + 3) and -4

        val pool = ByteArray(poolSize)
        put16(pool, 0, 0x0001)
        put16(pool, 2, 28)
        put32(pool, 4, poolSize)
        put32(pool, 8, p.count)
        put32(pool, 12, p.styleCount)
        put32(pool, 16, 0x100) // UTF-8
        put32(pool, 20, stringsStart)
        put32(pool, 24, stylesStart)
        for (i in 0 until p.count) put32(pool, 28 + 4 * i, offs[i])
        System.arraycopy(stringsBlob, 0, pool, stringsStart, stringsBlob.size)
        if (styles.isNotEmpty()) System.arraycopy(styles, 0, pool, stylesStart, styles.size)

        val prefix = p.poolOff // 池在文件中的起始（root 头 8 字节 + 可能的前置）
        val tailLen = a.size - (p.poolOff + p.poolSize)
        val out = ByteArray(prefix + poolSize + tailLen)
        System.arraycopy(a, 0, out, 0, prefix)
        System.arraycopy(pool, 0, out, prefix, poolSize)
        if (tailLen > 0) System.arraycopy(a, p.poolOff + p.poolSize, out, prefix + poolSize, tailLen)
        // 更新 root chunk 总大小
        if (u16(out, 0) == 0x0003) put32(out, 4, out.size)
        return out
    }

    private fun writeVar8(o: ByteArrayOutputStream, n: Int) {
        if (n <= 0x7F) {
            o.write(n)
        } else {
            o.write(0x80 or (n shr 8))
            o.write(n and 0xFF)
        }
    }

    // ---------------- 基础工具 ----------------

    private fun patchInt(a: ByteArray, off: Int, value: Int): ByteArray {
        val out = a.copyOf()
        put32(out, off, value)
        return out
    }

    private fun u16(a: ByteArray, off: Int): Int =
        (a[off].toInt() and 0xFF) or ((a[off + 1].toInt() and 0xFF) shl 8)

    private fun u32(a: ByteArray, off: Int): Int =
        (a[off].toInt() and 0xFF) or
            ((a[off + 1].toInt() and 0xFF) shl 8) or
            ((a[off + 2].toInt() and 0xFF) shl 16) or
            ((a[off + 3].toInt() and 0xFF) shl 24)

    private fun put16(a: ByteArray, off: Int, v: Int) {
        a[off] = v.toByte()
        a[off + 1] = (v shr 8).toByte()
    }

    private fun put32(a: ByteArray, off: Int, v: Int) {
        a[off] = v.toByte()
        a[off + 1] = (v shr 8).toByte()
        a[off + 2] = (v shr 16).toByte()
        a[off + 3] = (v shr 24).toByte()
    }
}
