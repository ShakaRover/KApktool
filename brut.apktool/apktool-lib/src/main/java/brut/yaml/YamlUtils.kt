/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.yaml

import brut.util.TextUtils

/**
 * apktool 自有 YAML 方言的字符串编解码工具（不依赖第三方 YAML 库）。
 *
 * encodeString 依据内容选择 原样 / 单引号 / 双引号 三种输出形式；
 * decodeString 逆向还原，支持 \x、\u、\U 转义与 '' 转义。
 */
object YamlUtils {
    /** 十六进制数字表，等价于 Character.forDigit(n, 16)。 */
    private const val HEX_DIGITS = "0123456789abcdef"

    /** 把字符串编码为单行 YAML 标量。 */
    @JvmStatic
    fun encodeString(str: String?): String? {
        if (str == null) {
            return null
        }
        val len = str.length
        if (len == 0) {
            return "''"
        }
        val first = str[0]
        // 以引号开头或首尾含空白的字符串必须加引号。
        var quote = if (first == '\'' || first == '"' || first.isWhitespace() ||
            (len > 1 && str[len - 1].isWhitespace())
        ) '\''.code else 0
        // 扫描是否存在强制双引号的字符。
        var i = 0
        while (i < len) {
            val ch = str[i]
            // NBSP(00A0)、行分隔符(2028)、段分隔符(2029)虽可打印但仍须转义。
            if (ch.code == 0xA0 || ch.code == 0x2028 || ch.code == 0x2029) {
                quote = '"'.code
                break
            }
            if (TextUtils.isPrintableChar(ch)) {
                i++
                continue
            }
            // 高代理项 + 合法低代理项 = 可保留的增补字符。
            if (ch.isHighSurrogate() && i + 1 < len && str[i + 1].isLowSurrogate()) {
                i += 2
                continue
            }
            // 不可打印字符必须转义。
            quote = '"'.code
            break
        }
        if (quote == '\''.code) {
            // 单引号形式：' 用 '' 转义。
            val sb = StringBuilder(len * 2 + 2)
            sb.append('\'')
            for (j in 0 until len) {
                val ch = str[j]
                if (ch == '\'') {
                    sb.append("''")
                } else {
                    sb.append(ch)
                }
            }
            sb.append('\'')
            return sb.toString()
        }
        if (quote == '"'.code) {
            // 双引号形式：转义反斜杠、双引号以及不可打印字符。
            val sb = StringBuilder(len * 2 + 2)
            sb.append('"')
            var j = 0
            while (j < len) {
                val ch = str[j]
                val esc: String? = when (ch.code) {
                    0x5C -> "\\" + "\\" // 反斜杠
                    0x22 -> "\\" + "\"" // 双引号
                    0x00 -> "\\0" // Null
                    0x07 -> "\\a" // Bell
                    0x08 -> "\\b" // Backspace
                    0x09 -> "\\t" // Character Tabulation
                    0x0A -> "\\n" // Line Feed
                    0x0B -> "\\v" // Line Tabulation
                    0x0C -> "\\f" // Form Feed
                    0x0D -> "\\r" // Carriage Return
                    0x1B -> "\\e" // Escape
                    0x85 -> "\\N" // Next Line
                    0xA0 -> "\\_" // No-Break Space
                    0x2028 -> "\\L" // Line Separator
                    0x2029 -> "\\P" // Paragraph Separator
                    else -> null
                }
                if (esc != null) {
                    sb.append(esc)
                    j++
                    continue
                }
                if (TextUtils.isPrintableChar(ch)) {
                    sb.append(ch)
                    j++
                    continue
                }
                // 高代理项 + 合法低代理项：原样保留代理对。
                if (ch.isHighSurrogate() && j + 1 < len) {
                    val low = str[j + 1]
                    if (low.isLowSurrogate()) {
                        sb.append(ch).append(low)
                        j += 2
                        continue
                    }
                }
                // 不可打印字符使用 Java 风格 \uXXXX 转义。
                sb.append("\\u")
                    .append(HEX_DIGITS[(ch.code ushr 12) and 0xF])
                    .append(HEX_DIGITS[(ch.code ushr 8) and 0xF])
                    .append(HEX_DIGITS[(ch.code ushr 4) and 0xF])
                    .append(HEX_DIGITS[ch.code and 0xF])
                j++
            }
            sb.append('"')
            return sb.toString()
        }
        return str
    }

    /** 把单行 YAML 标量还原为原始字符串；不带引号时原样返回。 */
    @JvmStatic
    fun decodeString(str: String?): String? {
        if (str == null) {
            return null
        }
        val len = str.length
        if (len == 0) {
            return str
        }
        val quote = str[0]
        if (quote != '\'' && quote != '"') {
            return str
        }
        if (len <= 1) {
            throw IllegalArgumentException("Unterminated quoted string.")
        }
        val sb = StringBuilder(len - 2)
        var i = 1
        while (i < len) {
            var ch = str[i]
            // 双引号串中反斜杠引导转义序列。
            if (quote == '"' && ch == '\\') {
                i++
                if (i == len) {
                    throw IllegalArgumentException("Unterminated escape sequence.")
                }
                ch = str[i]
                when (ch) {
                    ' ' -> sb.append(' ')
                    '"' -> sb.append('"')
                    '/' -> sb.append('/')
                    '\\' -> sb.append('\\')
                    '0' -> sb.append('\u0000')
                    'a' -> sb.append('\u0007')
                    'b' -> sb.append('\b')
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    'v' -> sb.append('\u000B')
                    'f' -> sb.append('\u000C')
                    'r' -> sb.append('\r')
                    'e' -> sb.append('\u001B')
                    'N' -> sb.append('\u0085')
                    '_' -> sb.append('\u00A0')
                    'L' -> sb.append('\u2028')
                    'P' -> sb.append('\u2029')
                    'x' -> {
                        i += 2
                        if (i >= len) {
                            throw IllegalArgumentException("Invalid \\x escape sequence.")
                        }
                        sb.append(parseHex(str, i - 1, 2).toChar())
                    }
                    'u' -> {
                        i += 4
                        if (i >= len) {
                            throw IllegalArgumentException("Invalid \\u escape sequence.")
                        }
                        sb.append(parseHex(str, i - 3, 4).toChar())
                    }
                    'U' -> {
                        i += 8
                        if (i >= len) {
                            throw IllegalArgumentException("Invalid \\U escape sequence.")
                        }
                        val codePoint = parseHex(str, i - 7, 8)
                        if (codePoint < 0 || codePoint > 0x10FFFF) {
                            throw IllegalArgumentException("Invalid Unicode code point.")
                        }
                        sb.appendCodePoint(codePoint)
                    }
                    else -> throw IllegalArgumentException("Unknown escape sequence: \\" + ch)
                }
                i++
                continue
            }
            if (ch != quote) {
                sb.append(ch)
                i++
                continue
            }
            // 单引号串中 '' 表示字面量撇号。
            if (quote == '\'' && i + 1 < len && str[i + 1] == '\'') {
                sb.append('\'')
                i += 2
                continue
            }
            if (i == len - 1) {
                return sb.toString()
            }
            throw IllegalArgumentException("Unexpected character after closing quote.")
        }
        throw IllegalArgumentException("Unterminated quoted string.")
    }

    /** 从 [start] 起解析 [len] 位十六进制数。 */
    private fun parseHex(str: String, start: Int, len: Int): Int {
        var value = 0
        val end = start + len
        for (i in start until end) {
            val digit = TextUtils.parseHex(str[i].code)
            if (digit < 0) {
                throw IllegalArgumentException("Invalid hexadecimal escape sequence.")
            }
            value = (value shl 4) or digit
        }
        return value
    }
}
