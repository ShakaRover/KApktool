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

import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.function.Function

/**
 * apktool YAML 方言的输出序列化器（UTF-8、两空格缩进、单行标量）。
 *
 * 写入的键值经 [YamlUtils.encodeString] 编码；嵌套对象通过 [mDepth] 记录缩进层级。
 * [close] 之后继续写入会抛 [IllegalStateException]。
 */
class YamlSerializer(out: OutputStream) : Closeable {
    private val mWriter: BufferedWriter =
        BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8))
    private var mDepth = 0
    private var mClosed = false

    @Throws(IOException::class)
    override fun close() {
        mWriter.close()
        mClosed = true
    }

    private fun encodeKey(key: String?): String {
        if (key == null) {
            throw IllegalArgumentException("Key is null.")
        }
        return YamlUtils.encodeString(key) ?: throw IllegalArgumentException("Key is null.")
    }

    private fun encodeValue(value: String?): String =
        if (value != null) YamlUtils.encodeString(value) ?: "''" else "null"

    @Throws(IOException::class)
    private fun writeIndent() {
        repeat(mDepth) { mWriter.write("  ") }
    }

    @Throws(IOException::class)
    private fun <T> writeEntry(key: String?, value: T, mapper: Function<T, String>) {
        if (mClosed) {
            throw IllegalStateException()
        }
        writeIndent()
        mWriter.write(encodeKey(key))
        mWriter.write(": ")
        mWriter.write(mapper.apply(value))
        mWriter.newLine()
    }

    /** 写出字符串键值。 */
    @Throws(IOException::class)
    fun writeString(key: String?, value: String?) {
        writeEntry(key, value, Function { v: String? -> encodeValue(v) })
    }

    /** 写出整数键值。 */
    @Throws(IOException::class)
    fun writeInt(key: String?, value: Int) {
        writeEntry(key, value, Function { v: Int -> v.toString() })
    }

    /** 写出布尔键值。 */
    @Throws(IOException::class)
    fun writeBool(key: String?, value: Boolean) {
        writeEntry(key, value, Function { v: Boolean -> if (v) "true" else "false" })
    }

    private fun interface Consumer<T> {
        @Throws(IOException::class)
        fun accept(obj: T, serial: YamlSerializer)
    }

    @Throws(IOException::class)
    private fun <T> writeObject(key: String?, obj: T, consumer: Consumer<T>) {
        if (mClosed) {
            throw IllegalStateException()
        }
        writeIndent()
        mWriter.write(encodeKey(key))
        mWriter.write(":")
        mWriter.newLine()
        mDepth++
        consumer.accept(obj, this)
        mDepth--
    }

    /** 写出嵌套对象（由其自身 [YamlSerializable.serialize] 完成）。 */
    @Throws(IOException::class)
    fun <T : YamlSerializable> writeObject(key: String?, obj: T) {
        writeObject(key, obj) { o, s -> o.serialize(s) }
    }

    @Throws(IOException::class)
    private fun <T> writeMap(key: String?, map: Map<String, T>, mapper: Function<T, String>) {
        writeObject(key, map) { obj, serial ->
            for ((entryKey, entryValue) in obj) {
                serial.writeEntry(entryKey, entryValue, mapper)
            }
        }
    }

    /** 写出字符串映射节。 */
    @Throws(IOException::class)
    fun writeStringMap(key: String?, map: Map<String, String>?) {
        writeMap(key, map ?: emptyMap(), Function { v: String? -> encodeValue(v) })
    }

    /** 写出整数映射节。 */
    @Throws(IOException::class)
    fun writeIntMap(key: String?, map: Map<String, Int>?) {
        writeMap(key, map ?: emptyMap(), Function { v: Int? -> v.toString() })
    }

    /** 写出布尔映射节。 */
    @Throws(IOException::class)
    fun writeBoolMap(key: String?, map: Map<String, Boolean>?) {
        writeMap(key, map ?: emptyMap(), Function { v: Boolean? -> if (v == true) "true" else "false" })
    }

    /** 写出序列节（每项一行，前缀 "- "）。 */
    @Throws(IOException::class)
    fun <T> writeSeq(key: String?, coll: Collection<T>, mapper: Function<T, String>) {
        if (mClosed) {
            throw IllegalStateException()
        }
        writeIndent()
        mWriter.write(encodeKey(key))
        mWriter.write(":")
        mWriter.newLine()
        for (item in coll) {
            writeIndent()
            mWriter.write("- ")
            mWriter.write(mapper.apply(item))
            mWriter.newLine()
        }
    }

    /** 写出字符串序列节。 */
    @Throws(IOException::class)
    fun writeStringSeq(key: String?, coll: Collection<String?>?) {
        writeSeq(key, coll ?: emptyList(), Function { v: String? -> encodeValue(v) })
    }

    /** 写出整数序列节。 */
    @Throws(IOException::class)
    fun writeIntSeq(key: String?, coll: Collection<Int>?) {
        writeSeq(key, coll ?: emptyList(), Function { v: Int? -> v.toString() })
    }
}
