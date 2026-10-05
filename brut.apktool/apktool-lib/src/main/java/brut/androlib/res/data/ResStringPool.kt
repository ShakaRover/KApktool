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
package brut.androlib.res.data

import brut.androlib.res.decoder.ResChunkPullParser
import brut.common.Log
import brut.util.BinaryDataInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.charset.Charset

/**
 * RES 资源表的字符串池（RES_STRING_POOL_TYPE chunk）。
 *
 * 支持 UTF-16LE 与 UTF-8（含 Android 偶发的 CESU-8 三字节代理序列回退解码）；
 * 解码结果按需缓存，畸形字符串以同一哨兵对象标记（引用判等）避免重复解码。
 */
class ResStringPool() {
    private var mStringOffsets = IntArray(0)
    private var mStrings = ByteArray(0)
    private var mStyleOffsets = IntArray(0)
    private var mStyles = IntArray(0)
    private var mIsUtf8 = false
    private var mIsLoaded = false
    private var mDecodedStrings: Array<String?>? = null
    private var mStringToIndex: MutableMap<String, Int>? = null

    /** 仅供测试：直接注入字符串块数据。 */
    constructor(strings: ByteArray, isUtf8: Boolean) : this() {
        mStrings = strings
        mIsUtf8 = isUtf8
        mIsLoaded = true
    }

    /** 是否已从 chunk 解析出数据。 */
    val isLoaded: Boolean get() = mIsLoaded

    /** 从字符串池 chunk 主体解析全部偏移表、字符串块与样式块。 */
    @Throws(IOException::class)
    fun parse(parser: ResChunkPullParser) {
        reset()
        val `in` = parser.stream()
        // ResStringPool_header
        val stringCount = `in`.readInt()
        val styleCount = `in`.readInt()
        val flags = `in`.readInt()
        val stringsOffset = `in`.readInt()
        val stylesOffset = `in`.readInt()

        // 某些应用会在 chunk 头部尾部塞入未使用的数据。
        val skipped = parser.skipHeader()
        if (skipped > 0) {
            Log.d(TAG, "Skipped unknown %s bytes at end of %s chunk header.", skipped, parser.chunkName())
        }

        mStringOffsets = readIntArraySafe(`in`, stringCount, parser.chunkStart() + stringsOffset)
        mStyleOffsets = readIntArraySafe(`in`, styleCount, parser.chunkStart() + stylesOffset)

        // 若既有字符串又有（哪怕撒谎的）样式偏移：只按字符串区取字节，避免误解析样式。
        var size = parser.chunkSize() - stringsOffset
        if (styleCount > 0) {
            size = stylesOffset - stringsOffset
        }

        mStrings = `in`.readBytes(size)

        // #3236 - 有些应用给出样式偏移但样式数为 0，这里做加固判断。
        if (stylesOffset > 0 && styleCount > 0) {
            size = parser.chunkSize() - stylesOffset
            mStyles = `in`.readIntArray(size / 4)
        }

        // 非 4 字节对齐时跳过填充字节。
        `in`.skipBytes(size % 4)

        mIsUtf8 = flags and UTF8_FLAG != 0
        mIsLoaded = true
    }

    /** 重置为未加载状态（清空全部缓存）。 */
    fun reset() {
        mStringOffsets = IntArray(0)
        mStrings = ByteArray(0)
        mStyleOffsets = IntArray(0)
        mStyles = IntArray(0)
        mIsUtf8 = false
        mIsLoaded = false
        mDecodedStrings = null
        mStringToIndex = null
    }

    /** 取带样式的文本；无样式时返回原始字符串。 */
    fun getText(index: Int): CharSequence? {
        val string = getString(index) ?: return null

        // 无样式则直接返回原始字符串。
        val style = getStyle(index) ?: return string

        // 样式三元组数组转换为 Span 数组。
        val len = string.length
        var spans = arrayOfNulls<StyledString.Span>(style.size / 3)
        var spansCount = 0

        var i = 0
        while (i < style.size) {
            val tag = getString(style[i])
            val firstChar = style[i + 1]
            val lastChar = style[i + 2]

            // 越界的样式直接忽略。
            if (firstChar >= 0 && firstChar <= len && lastChar <= len) {
                spans[spansCount++] = StyledString.Span(tag ?: "", firstChar, lastChar)
            }
            i += 3
        }

        if (spansCount < spans.size) {
            spans = spans.copyOf(spansCount)
        }

        return StyledString(string, spans as Array<StyledString.Span>)
    }

    /** 按下标取字符串；越界/畸形返回 null。 */
    fun getString(index: Int): String? {
        if (index < 0 || index >= mStringOffsets.size || mStrings.isEmpty()) {
            return null
        }

        val decoded = mDecodedStrings
        if (decoded != null) {
            val cached = decoded[index]
            if (cached != null) {
                // 引用判等识别畸形字符串哨兵。
                return if (cached === MALFORMED_MARKER) null else cached
            }
        }

        var offset = mStringOffsets[index]
        val val0: Long

        if (mIsUtf8) {
            val0 = getUtf8(mStrings, offset)
            offset = (val0 ushr 32).toInt()
        } else {
            val0 = getUtf16(mStrings, offset)
            offset += (val0 ushr 32).toInt()
        }

        val length = val0.toInt()
        val string = decodeString(offset, length)

        var decodedCache = mDecodedStrings
        if (decodedCache == null) {
            decodedCache = arrayOfNulls(mStringOffsets.size)
            mDecodedStrings = decodedCache
        }
        decodedCache[index] = string ?: MALFORMED_MARKER

        return string
    }

    /** 按字节区间解码字符串（UTF-16LE / UTF-8 / CESU-8 回退）。 */
    fun decodeString(offset: Int, length: Int): String? {
        if (offset < 0 || length < 0 || offset > mStrings.size - length) {
            Log.w(TAG, "String extends outside of pool at %s of length %s", offset, length)
            return null
        }

        if (!mIsUtf8) {
            return try {
                val buffer = ByteBuffer.wrap(mStrings, offset, length)
                UTF16LE_DECODER.decode(buffer).toString()
            } catch (ignored: CharacterCodingException) {
                Log.w(TAG, "Failed to decode a string at offset %s of length %s", offset, length)
                null
            }
        }

        val string: String = String(mStrings, offset, length, StandardCharsets.UTF_8)

        // 某些场景 Android 写入 3 字节代理对（CESU-8）而非 4 字节标准 UTF-8；
        // 出现替换字符(0xFFFD)说明标准解码失败，改用与 Android 行为更接近的 CESU-8 解码器。
        if (string.indexOf('\uFFFD') == -1) {
            return string
        }

        return try {
            val buffer = ByteBuffer.wrap(mStrings, offset, length)
            CESU8_DECODER.decode(buffer).toString()
        } catch (ignored: CharacterCodingException) {
            Log.w(TAG, "Failed to decode a string with CESU-8 decoder.")
            null
        }
    }

    /** 反查字符串在池中的下标（首次调用时建立索引缓存）；未找到返回 -1。 */
    fun findString(string: String?): Int {
        if (string == null || mStringOffsets.isEmpty() || mStrings.isEmpty()) {
            return -1
        }

        var map = mStringToIndex
        if (map == null) {
            map = HashMap(mStringOffsets.size, 1f)
            for (i in mStringOffsets.indices) {
                val value = getString(i)
                if (value != null) {
                    map.putIfAbsent(value, i)
                }
            }
            mStringToIndex = map
        }

        return map[string] ?: -1
    }

    /**
     * 返回样式信息三元组数组：
     * 1. 标签名（'b'、'i' 等）的字符串池下标；2. 起始下标；3. 结束下标。
     */
    private fun getStyle(index: Int): IntArray? {
        if (index < 0 || index >= mStyleOffsets.size || mStyles.isEmpty()) {
            return null
        }

        // 不统计残缺的三元组。
        val offset = mStyleOffsets[index] / 4
        var count = 0
        var i = offset
        while (i + 2 < mStyles.size) {
            if (mStyles[i] < 0) {
                break
            }
            count++
            i += 3
        }
        if (count == 0) {
            return null
        }

        val style = IntArray(count * 3)
        mStyles.copyInto(style, 0, offset, offset + style.size)
        return style
    }

    companion object {
        private val TAG = ResStringPool::class.java.name

        private val UTF16LE_DECODER = StandardCharsets.UTF_16LE.newDecoder()
        private val CESU8_DECODER = Charset.forName("CESU8").newDecoder()

        private const val UTF8_FLAG = 0x00000100

        /** 畸形字符串哨兵（引用判等）。 */
        private val MALFORMED_MARKER = ""

        /** 安全读取 int 数组：越过 [maxPosition] 立即截断返回（部分应用数据撒谎）。 */
        @Throws(IOException::class)
        private fun readIntArraySafe(`in`: BinaryDataInputStream, len: Int, maxPosition: Long): IntArray {
            val arr = IntArray(len)
            for (i in 0 until len) {
                // #3236 - 有些应用声明的字符串条目超出区块实际容量。
                if (`in`.position() >= maxPosition) {
                    Log.d(TAG, "Bad string block: string entry is at %s, past end at %s", `in`.position(), maxPosition)
                    return arr
                }

                arr[i] = `in`.readInt()
            }
            return arr
        }

        /** 返回 (新offset<<32)|utf8长度：跳过 UTF-16 长度字段后读 UTF-8 字节长度。 */
        private fun getUtf8(array: ByteArray, offset: Int): Long {
            var o = offset
            var `val` = array[o].toInt()

            // 跳过字符串的 UTF-16 长度。
            o += if (`val` and 0x80 != 0) 2 else 1

            // 读 UTF-8 字节长度。
            `val` = array[o].toInt()
            o++
            val length: Int = if (`val` and 0x80 != 0) {
                val low = array[o].toInt() and 0xFF
                o++
                ((`val` and 0x7F) shl 8) + low
            } else {
                `val`
            }

            return (o.toLong() shl 32) or (length.toLong() and 0xFFFFFFFFL)
        }

        /** 返回 (头长度<<32)|utf16字节长度（小端，可能双字长）。 */
        private fun getUtf16(array: ByteArray, offset: Int): Long {
            val `val` = ((array[offset + 1].toInt() and 0xFF) shl 8) or (array[offset].toInt() and 0xFF)

            if (`val` and 0x8000 != 0) {
                val high = (array[offset + 3].toInt() and 0xFF) shl 8
                val low = array[offset + 2].toInt() and 0xFF
                val lenValue = ((`val` and 0x7FFF) shl 16) + high + low
                return (4L shl 32) or ((lenValue * 2).toLong() and 0xFFFFFFFFL)
            }

            return (2L shl 32) or ((`val` * 2).toLong() and 0xFFFFFFFFL)
        }
    }
}
