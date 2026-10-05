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
package brut.androlib.res.xml

import brut.androlib.res.data.StyledString
import brut.androlib.res.table.value.ResAttribute
import brut.util.TextUtils
import java.util.ArrayDeque
import java.util.Arrays
import java.util.regex.Pattern

/**
 * values XML 字符串编码器（AAPT2 兼容规则）。
 *
 * 文本值：换行/制表/引号/空白折叠按 AAPT 规则转义，必要时整体加引号或前置反斜杠
 * （[isAmbiguousString] 判定会被误读为引用/颜色/数字的"歧义串"）；
 * 样式串（StyledString）还原为内嵌 XML 标签；
 * 属性值另按 attr 类型位选择性转义；
 * [normalizeFormatSpecifiers] 给顺序占位符补 %N$ 位置索引。
 */
object ResStringEncoder {
    /** 标签属性分隔符：';' 后须紧跟合法属性名。 */
    private val TAG_SPLIT_PATTERN: Pattern = Pattern.compile(";(?=[\\p{L}_][\\p{L}\\p{N}_.-]*=)")

    /** 编码文本节点值（可含样式跨度）。 */
    @JvmStatic
    fun encodeTextValue(text: CharSequence): String =
        if (text is StyledString) encodeStyledString(text) else encodeRawString(text.toString(), 0)

    /** 编码属性值（不限定 attr 类型）。 */
    @JvmStatic
    fun encodeAttributeValue(text: CharSequence): String =
        encodeAttributeValue(text, ResAttribute.ATTR_TYPE_ANY)

    /** 按 attr 类型编码属性值。 */
    @JvmStatic
    fun encodeAttributeValue(text: CharSequence, attrType: Int): String =
        encodeRawString(text.toString(), attrType)

    /** 把样式跨度还原为嵌套 XML 标签。 */
    private fun encodeStyledString(styledStr: StyledString): String {
        val str = styledStr.value
        val spans = styledStr.spans
        val len = str.length
        if (len == 0 && spans.isEmpty()) {
            return str
        }

        val out = StringBuilder(len * 2)
        val stack = ArrayDeque<StyledString.Span>()

        var offset = 0
        var i = 0
        while (i <= spans.size) {
            var prevOffset = offset
            val span: StyledString.Span?
            if (i < spans.size) {
                span = spans[i]
                offset = span.firstChar
            } else {
                span = null
                offset = len
            }

            // 先闭合在 offset 之前结束的内层跨度。
            while (stack.isNotEmpty() && stack.peek()!!.lastChar < offset) {
                val prevSpan = stack.pop()
                val prevSpanEnd = prevSpan.lastChar + 1

                // 输出该跨度内剩余文本。
                if (prevOffset < prevSpanEnd) {
                    appendEscapedString(out, str, prevOffset, prevSpanEnd, 0, true)
                    prevOffset = prevSpanEnd
                }

                // 写闭合标签。
                val prevTag = prevSpan.tag
                var prevTagEnd = prevTag.indexOf(';')
                if (prevTagEnd == -1) {
                    prevTagEnd = prevTag.length
                }
                out.append("</").append(prevTag, 0, prevTagEnd).append('>')
            }

            // 起始位置早于已写内容的跨度直接忽略。
            if (prevOffset > offset) {
                i++
                continue
            }

            // 输出标签间的普通文本。
            if (prevOffset < offset) {
                appendEscapedString(out, str, prevOffset, offset, 0, true)
            }

            // 全部跨度处理完毕。
            if (span == null) {
                break
            }

            // 开始当前跨度。
            val spanEnd = span.lastChar + 1
            val tag = span.tag
            var tagEnd = tag.indexOf(';')
            if (tagEnd == -1) {
                tagEnd = tag.length
            }
            // 标签名为空则忽略该跨度。
            if (tagEnd == 0) {
                i++
                continue
            }

            // 写开始标签。
            out.append('<').append(tag, 0, tagEnd)

            // 追加属性（tag 中 ';' 之后的部分）。
            if (tagEnd < tag.length) {
                for (attr in TAG_SPLIT_PATTERN.split(tag.substring(tagEnd + 1))) {
                    val attrLen = attr.length
                    if (attrLen == 0) {
                        continue
                    }
                    val nameEnd = attr.indexOf('=')
                    if (nameEnd == -1) {
                        continue
                    }
                    val valueStart = nameEnd + 1
                    out.append(' ').append(attr, 0, valueStart).append('"')
                    if (valueStart < attrLen) {
                        appendTagAttributeValue(out, attr, valueStart, attrLen)
                    }
                    out.append('"')
                }
            }

            // 零宽跨度用自闭合标签。
            if (offset == spanEnd) {
                out.append("/>")
                i++
                continue
            }

            // 入栈等待输出内文与嵌套跨度。
            out.append('>')
            stack.push(span)
            i++
        }

        return out.toString()
    }

    /** 编码普通（无样式）字符串。 */
    private fun encodeRawString(str: String, attrType: Int): String {
        val len = str.length
        if (len == 0) {
            return str
        }

        val out = StringBuilder(len * 2)
        appendEscapedString(out, str, 0, len, attrType, false)

        // 边缘情形下原始串可能被当作带类型的值；已被引号包裹的跳过。
        if (out.isNotEmpty() && out[0] != '"' && isAmbiguousString(out, attrType)) {
            out.insert(0, '\\')
        }

        return out.toString()
    }

    /** 逐字符转义写入 [out]；需要时给整段结果加引号。 */
    private fun appendEscapedString(
        out: StringBuilder,
        str: String,
        start: Int,
        end: Int,
        attrType: Int,
        styled: Boolean,
    ) {
        val len = str.length
        val offset = out.length
        var quote = false
        var ch = '\u0000'
        var prev = '\u0000'
        var prev2 = '\u0000'
        var i = start
        while (i < end) {
            ch = str[i]
            when {
                ch == '\n' ->
                    if (attrType != 0) {
                        out.append("\\n")
                    } else {
                        out.append(ch)
                        quote = true
                    }
                ch == '\t' -> out.append("\\t")
                TextUtils.isPrintableChar(ch) -> {
                    var appendChar = true
                    if (ch == '\\') {
                        out.append('\\')
                    } else if (attrType == 0) {
                        // 以下规则仅适用于 values XML；属性值由序列化器另行处理。
                        if (ch == ' ') {
                            // 普通串会折叠空白并去首尾空白，样式串只折叠：首尾空格需要引号保护。
                            if (prev == ' ' || (!styled && (i == 0 || i == len - 1))) {
                                quote = true
                            }
                        } else if (ch == '\'') {
                            quote = true
                        } else if (ch == '"') {
                            out.append('\\')
                        } else if (ch == '&') {
                            out.append("&amp;")
                            appendChar = false
                        } else if (ch == '<') {
                            out.append("&lt;")
                            appendChar = false
                        } else if (ch == '>' && prev == ']' && prev2 == ']') {
                            out.append("&gt;")
                            appendChar = false
                        }
                    }
                    if (appendChar) {
                        out.append(ch)
                    }
                }
                ch.isHighSurrogate() && i + 1 < end && str[i + 1].isLowSurrogate() -> {
                    // 合法代理对原样保留。
                    out.append(ch).append(str[i + 1])
                    i++
                }
                else -> {
                    // 结尾的 \u0000 不写出。
                    if (ch.code == 0 && i == len - 1) {
                        break
                    }
                    // 不可打印字符用 Java 风格 \uXXXX。
                    out.append("\\u")
                        .append(HEX_DIGITS[(ch.code ushr 12) and 0xF])
                        .append(HEX_DIGITS[(ch.code ushr 8) and 0xF])
                        .append(HEX_DIGITS[(ch.code ushr 4) and 0xF])
                        .append(HEX_DIGITS[ch.code and 0xF])
                }
            }
            i++
            prev2 = prev
            prev = ch
        }
        if (quote) {
            out.insert(offset, '"').append('"')
        }
    }

    /** 判断字符串是否会被 AAPT 误读为引用/颜色/数字等带类型的值。 */
    private fun isAmbiguousString(text: CharSequence, attrType: Int): Boolean {
        val len = text.length
        val ch = text[0]

        // 引用判定：任何类型下 @/? 开头都可能歧义，不按 attrType 区分。
        if (ch == '@') {
            if (len == 5) {
                if (text[1] == 'n' && text[2] == 'u' && text[3] == 'l' && text[4] == 'l') {
                    return true
                }
            } else if (len == 6) {
                if (text[1] == 'e' && text[2] == 'm' && text[3] == 'p' && text[4] == 't' && text[5] == 'y') {
                    return true
                }
            }
            for (i in 1 until len) {
                if (text[i] == '/') {
                    return true
                }
            }
            return false
        }
        if (ch == '?') {
            return len > 1
        }

        // 以下只可能发生在属性值中；布尔值无法用 \t 转义（会变成制表符），需特别小心。
        if (attrType == 0) {
            return false
        }

        // 颜色判定。
        if (ch == '#') {
            if (attrType and ResAttribute.ATTR_TYPE_COLOR != 0) {
                try {
                    TextUtils.parseColor(text, 0, len)
                    return true
                } catch (ignored: NumberFormatException) {
                }
            }
            return false
        }

        // 整数判定。
        if (attrType and ResAttribute.ATTR_TYPE_INTEGER != 0) {
            try {
                TextUtils.parseInt(text, 0, len)
                return true
            } catch (ignored: NumberFormatException) {
            }
        }

        // 浮点/尺寸/占比判定（先剥单位后缀）。
        val checkFloat = attrType and ResAttribute.ATTR_TYPE_FLOAT != 0
        val checkDimen = attrType and ResAttribute.ATTR_TYPE_DIMENSION != 0
        val checkFraction = attrType and ResAttribute.ATTR_TYPE_FRACTION != 0
        if (checkFloat || checkDimen || checkFraction) {
            var suffixLen = 0
            if (checkDimen) {
                val suffix = TextUtils.matchSuffix(text, "px", "dp", "dip", "sp", "pt", "in", "mm")
                if (suffix != null) {
                    suffixLen = suffix.length
                }
            }
            if (checkFraction && suffixLen == 0) {
                val suffix = TextUtils.matchSuffix(text, "%", "%p")
                if (suffix != null) {
                    suffixLen = suffix.length
                }
            }
            if ((checkFloat && suffixLen == 0) || suffixLen > 0) {
                try {
                    TextUtils.parseFloat(text, 0, len - suffixLen)
                    return true
                } catch (ignored: NumberFormatException) {
                }
            }
        }
        return false
    }

    /** 标签属性值内的 XML 实体转义。 */
    private fun appendTagAttributeValue(out: StringBuilder, str: String, start: Int, end: Int) {
        var ch = '\u0000'
        var prev = '\u0000'
        var prev2 = '\u0000'
        var i = start
        while (i < end) {
            ch = str[i]
            when {
                ch == '\n' -> out.append("&#xA;")
                ch == '\r' -> out.append("&#xD;")
                ch == '\t' -> out.append("&#x9;")
                ch == '"' -> out.append("&quot;")
                ch == '&' -> out.append("&amp;")
                ch == '<' -> out.append("&lt;")
                ch == '>' && prev == ']' && prev2 == ']' -> out.append("&gt;")
                else -> out.append(ch)
            }
            i++
            prev2 = prev
            prev = ch
        }
    }

    /** 给多个顺序占位符补充位置索引（%s -> %1$s）。 */
    @JvmStatic
    fun normalizeFormatSpecifiers(str: String): String {
        val len = str.length
        if (len == 0) {
            return str
        }

        val specs = findFormatSpecifiers(str)
        val sequential = specs[0]
        val positional = specs[1]
        if (sequential.isEmpty() || sequential.size + positional.size < 2) {
            return str
        }

        val out = StringBuilder(len + sequential.size * 2)
        var i = 0
        var count = 0
        for (j0 in sequential) {
            var j = j0
            out.append(str, i, ++j).append(++count).append('$')
            i = j
        }

        out.append(str, i, len)
        return out.toString()
    }

    /**
     * 返回两个下标数组：
     * 1. 顺序占位符（%s、%d 等）起始下标；2. 位置占位符（%1$s、%2$d 等）起始下标。
     */
    @JvmStatic
    fun findFormatSpecifiers(str: String): Array<IntArray> {
        var sequential = IntArray(4)
        var sequentialCount = 0
        var positional = IntArray(4)
        var positionalCount = 0

        val len = str.length
        var j = 0
        while (true) {
            val i = str.indexOf('%', j)
            if (i == -1) {
                break
            }
            j = i + 1
            if (j == len) {
                // 结尾裸 '%'。
                if (sequentialCount == sequential.size) {
                    sequential = Arrays.copyOf(sequential, sequential.size + 4)
                }
                sequential[sequentialCount++] = i - 1
                break
            }

            var ch = str[j++]
            if (ch == '%') {
                continue
            }
            if (ch in '0'..'9' && j < len) {
                while (true) {
                    ch = str[j++]
                    if (!(ch in '0'..'9' && j < len)) {
                        break
                    }
                }
                if (ch == '$') {
                    if (positionalCount == positional.size) {
                        positional = Arrays.copyOf(positional, positional.size + 4)
                    }
                    positional[positionalCount++] = i
                    continue
                }
            }

            if (sequentialCount == sequential.size) {
                sequential = Arrays.copyOf(sequential, sequential.size + 4)
            }
            sequential[sequentialCount++] = i
        }

        if (sequentialCount < sequential.size) {
            sequential = Arrays.copyOf(sequential, sequentialCount)
        }
        if (positionalCount < positional.size) {
            positional = Arrays.copyOf(positional, positionalCount)
        }

        return arrayOf(sequential, positional)
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
