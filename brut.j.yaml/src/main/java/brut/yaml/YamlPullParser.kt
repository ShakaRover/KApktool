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

import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.function.Function

/**
 * apktool YAML 方言的行式拉取解析器（apktool.yml / frames.yaml 等配置文件读取）。
 *
 * 缩进只允许空格；序列项以 "- " 开头并计入缩进。
 * 维护 [mBlocks] 缩进块栈做层次归属判断，遇到回缩的行通过 [mLookahead] 延迟一拍处理。
 * 行尾内联注释（# 前为空白）会被剥离；空值即隐式 null（表示嵌套块的前导键）。
 */
class YamlPullParser(`in`: InputStream) : Closeable {
    private val mReader: BufferedReader =
        BufferedReader(InputStreamReader(`in`, StandardCharsets.UTF_8))
    private var mBlocks: Array<Block?> = arrayOfNulls(4)
    private var mDepth = -1
    private var mPosition = 0
    private var mCurrent: Entry? = null
    private var mLookahead: Entry? = null
    private var mClosed = false

    @Throws(IOException::class)
    override fun close() {
        mReader.close()
        mClosed = true
    }

    /** 读取并解析下一行；EOF 或已关闭时返回 false。 */
    @Throws(IOException::class)
    private fun nextLine(): Boolean {
        if (mClosed) {
            return false
        }
        val lookahead = mLookahead
        if (lookahead != null) {
            mCurrent = lookahead
            mLookahead = null
            return true
        }
        while (true) {
            val rawLine = mReader.readLine() ?: break
            mPosition++
            // 跳过空行与整行注释。
            var end = rawLine.length
            if (end == 0 || rawLine[0] == '#') {
                continue
            }
            var line = rawLine
            // 剥离引号外的内联注释。
            var quote = '\u0000'
            var i = 0
            while (i < end) {
                val ch = line[i]
                if (quote != '\u0000') {
                    if (ch == quote) {
                        quote = '\u0000'
                    }
                } else if (ch == '"' || ch == '\'') {
                    quote = ch
                } else if (i > 0 && ch == '#' && line[i - 1].isWhitespace()) {
                    end = i
                    break
                }
                i++
            }
            // 去掉行尾空白。
            while (end > 0 && line[end - 1].isWhitespace()) {
                end--
            }
            // 什么都没有剩下则跳过整行。
            if (end == 0) {
                continue
            }
            // 计算缩进（仅允许空格）并识别序列项。
            // 序列项 = 连字符 +（可选空白 + 值）；连字符及其后空白计入缩进。
            var indent = 0
            var start = 0
            var isItem = false
            j@ for (k in 0 until end) {
                val ch = line[k]
                if (ch != ' ') {
                    if (ch.isWhitespace()) {
                        throw YamlSyntaxException(mPosition, "Only spaces can be used for indentation.")
                    }
                    if (ch == '-' && (k == end - 1 || line[k + 1].isWhitespace())) {
                        indent = k + 2
                        start = Math.min(indent, end)
                        isItem = true
                    } else {
                        indent = k
                        start = k
                    }
                    break@j
                }
            }
            // 对照当前块的缩进判断行归属。
            var depth = mDepth
            var blockType: Int
            val blockIndent: Int
            if (depth == -1) {
                blockIndent = -1
                blockType = -1
            } else {
                blockIndent = mBlocks[depth]!!.indent
                blockType = mBlocks[depth]!!.type
            }
            if (indent != blockIndent) {
                if (isItem && blockType == Block.SEQ) {
                    throw YamlSyntaxException(mPosition, "Sequence item indentation is inconsistent.")
                }
                if (indent > blockIndent) {
                    // 新的嵌套块只能跟在"无值的映射键"之后。
                    val cur = mCurrent
                    if (cur != null && (cur.key == null || cur.value != null)) {
                        throw YamlSyntaxException(mPosition, "Unexpected nested block.")
                    }
                    if (isItem && indent - 2 < blockIndent) {
                        throw YamlSyntaxException(mPosition, "Sequence item is shallower than its parent.")
                    }
                    // 本行建立一个新嵌套块。
                    depth++
                    if (mBlocks.size <= depth) {
                        val newBlocks = arrayOfNulls<Block>(depth + 4)
                        System.arraycopy(mBlocks, 0, newBlocks, 0, mBlocks.size)
                        mBlocks = newBlocks
                    }
                    blockType = if (isItem) Block.SEQ else Block.MAP
                    mBlocks[depth] = Block(indent, blockType)
                } else {
                    // 本行不属于当前块：回退到缩进相同的祖先块。
                    var newDepth = -1
                    for (k in depth - 1 downTo 0) {
                        if (indent == mBlocks[k]!!.indent) {
                            newDepth = k
                            break
                        }
                    }
                    if (newDepth == -1) {
                        throw YamlSyntaxException(mPosition, "Indentation does not match any block.")
                    }
                    for (k in newDepth + 1..depth) {
                        mBlocks[k] = null
                    }
                    depth = newDepth
                    blockType = mBlocks[depth]!!.type
                }
                mDepth = depth
            }
            // 行类型必须与其所属块一致。
            if (isItem != (blockType == Block.SEQ)) {
                throw YamlSyntaxException(
                    mPosition,
                    if (isItem) "Sequence item cannot appear in a mapping block."
                    else "Mapping entry cannot appear in a sequence block."
                )
            }
            // 截去缩进。
            val content = line.substring(start, end)
            end -= start
            // 解析键与值。
            val key: String?
            var value: String?
            if (isItem) {
                key = null
                value = content.trim()
            } else {
                // 映射项 = 键 + 冒号 +（可选空白 + 值）。
                quote = '\u0000'
                var keyEnd = -1
                k2@ for (k in 0 until end) {
                    val ch = content[k]
                    if (quote != '\u0000') {
                        if (ch == quote) {
                            quote = '\u0000'
                        }
                    } else if (ch == '"' || ch == '\'') {
                        quote = ch
                    } else if (ch == ':' && (k == end - 1 || content[k + 1].isWhitespace())) {
                        if (keyEnd == -1) {
                            keyEnd = k
                        } else {
                            throw YamlSyntaxException(mPosition, "Mapping value looks like another mapping entry.")
                        }
                    }
                }
                if (keyEnd == -1) {
                    throw YamlSyntaxException(mPosition, "Missing key.")
                }
                key = content.substring(0, keyEnd).trim()
                if (key.isEmpty()) {
                    throw YamlSyntaxException(mPosition, "Empty key.")
                }
                value = content.substring(keyEnd + 1).trim()
            }
            // 空值隐式为 null。
            if (value != null && value.isEmpty()) {
                value = null
            }
            mCurrent = Entry(depth, key, value)
            return true
        }
        mClosed = true
        return false
    }

    /** 当前条目的键（已解码）。 */
    fun getKey(): String {
        val key = if (mClosed) null else mCurrent?.key
        if (key == null) {
            throw IllegalStateException()
        }
        return YamlUtils.decodeString(key)!!
    }

    /** 当前条目的字符串值（已解码）；字面量 null 或无值时返回 null。 */
    fun getString(): String? {
        if (mClosed) {
            throw IllegalStateException()
        }
        val value = mCurrent?.value ?: return null
        if (value == "null") {
            return null
        }
        return YamlUtils.decodeString(value)
    }

    /** 当前条目的整数值。 */
    fun getInt(): Int {
        if (mClosed) {
            throw IllegalStateException()
        }
        val value = mCurrent?.value
        if (value != null) {
            try {
                return value.toInt()
            } catch (ignored: NumberFormatException) {
            }
        }
        throw YamlSyntaxException(mPosition, "Invalid integer value: " + value)
    }

    /** 当前条目的布尔值（仅接受 true/false 字面量）。 */
    fun getBool(): Boolean {
        if (mClosed) {
            throw IllegalStateException()
        }
        val value = mCurrent?.value
        if (value != null) {
            if (value == "true") {
                return true
            }
            if (value == "false") {
                return false
            }
        }
        throw YamlSyntaxException(mPosition, "Invalid boolean value: " + value)
    }

    private fun interface Consumer<T> {
        @Throws(IOException::class)
        fun accept(obj: T, parser: YamlPullParser)
    }

    @Throws(IOException::class)
    private fun <T> readObject(obj: T, consumer: Consumer<T>) {
        val cur = mCurrent
        if (mClosed || cur != null && cur.key == null) {
            throw IllegalStateException()
        }
        val blockDepth = mDepth + 1
        while (nextLine()) {
            val entry = mCurrent!!
            // 回缩到父块：把该行缓存为 lookahead 后退出。
            if (entry.depth < blockDepth) {
                mLookahead = entry
                return
            }
            // 跳过未被消费的更深层块。
            if (entry.depth > blockDepth) {
                continue
            }
            if (entry.key == null) {
                throw YamlSyntaxException(mPosition, "Expected a mapping entry, found a sequence item.")
            }
            consumer.accept(obj, this)
        }
    }

    /** 读取映射块并逐条回调 obj 的 [YamlSerializable.onEntry]。 */
    @Throws(IOException::class)
    fun <T : YamlSerializable> readObject(obj: T) {
        readObject(obj) { o, parser -> o.onEntry(parser) }
    }

    @Throws(IOException::class)
    private fun <T> readMap(map: MutableMap<String?, T>, mapper: Function<YamlPullParser, T>) {
        readObject(map) { obj, parser ->
            obj[parser.mCurrent!!.key] = mapper.apply(parser)
        }
    }

    /** 读取字符串映射节。 */
    @Throws(IOException::class)
    fun readStringMap(map: MutableMap<String?, String?>) {
        readMap(map) { parser -> parser.getString() }
    }

    /** 读取整数映射节。 */
    @Throws(IOException::class)
    fun readIntMap(map: MutableMap<String?, Int>) {
        readMap(map) { parser -> parser.getInt() }
    }

    /** 读取布尔映射节。 */
    @Throws(IOException::class)
    fun readBoolMap(map: MutableMap<String?, Boolean>) {
        readMap(map) { parser -> parser.getBool() }
    }

    @Throws(IOException::class)
    private fun <T> readSeq(coll: MutableCollection<T>, mapper: Function<YamlPullParser, T>) {
        val cur = mCurrent
        if (mClosed || cur == null || cur.key == null) {
            throw IllegalStateException()
        }
        val blockDepth = mDepth + 1
        while (nextLine()) {
            val entry = mCurrent!!
            // 回缩到父块：缓存后退出。
            if (entry.depth < blockDepth) {
                mLookahead = entry
                return
            }
            if (entry.depth > blockDepth) {
                continue
            }
            if (entry.key != null) {
                throw YamlSyntaxException(mPosition, "Expected a sequence item, found a mapping entry.")
            }
            coll.add(mapper.apply(this))
        }
    }

    /** 读取字符串序列节。 */
    @Throws(IOException::class)
    fun readStringSeq(coll: MutableCollection<String?>) {
        readSeq(coll) { parser -> parser.getString() }
    }

    /** 读取整数序列节。 */
    @Throws(IOException::class)
    fun readIntSeq(coll: MutableCollection<Int>) {
        readSeq(coll) { parser -> parser.getInt() }
    }

    /** 缩进块：记录块的缩进量与类型（MAP/SEQ）。 */
    private class Block(val indent: Int, val type: Int) {
        companion object {
            const val MAP = 0
            const val SEQ = 1
        }
    }

    /** 解析出的一条键值记录（序列项 key 为 null）。 */
    private class Entry(val depth: Int, val key: String?, val value: String?)
}
