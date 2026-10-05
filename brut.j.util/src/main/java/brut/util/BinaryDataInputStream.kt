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
package brut.util

import com.google.common.io.ByteStreams
import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import java.io.ByteArrayInputStream
import java.io.DataInput
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteOrder

/**
 * 带位置/上限追踪的二进制读取流（资源表 RES 文件解析的基础设施）。
 *
 * 默认小端字节序；[limit] 限定可读取字节数，超出即视为 EOF；
 * [jumpTo] 只允许向前跳转，用于按偏移遍历 ARSC 结构；
 * 同时实现 [DataInput]，按当前字节序解析 8/16/32/64 位数值。
 */
class BinaryDataInputStream : FilterInputStream, DataInput {
    private val mByteOrder: ByteOrder
    private val mLimit: Long
    private var mPosition: Long = 0
    private var mMark: Long = -1

    /** 以整段字节数组构造，小端字节序，上限为数组长度。 */
    constructor(`in`: ByteArray) : this(ByteArrayInputStream(`in`), `in`.size.toLong())

    /** 以整段字节数组构造，指定字节序，上限为数组长度。 */
    constructor(`in`: ByteArray, bo: ByteOrder) : this(ByteArrayInputStream(`in`), bo, `in`.size.toLong())

    /** 无上限（Long.MAX_VALUE）、小端字节序包装流。 */
    constructor(`in`: InputStream) : this(`in`, ByteOrder.LITTLE_ENDIAN, Long.MAX_VALUE)

    /** 指定读取上限，小端字节序。 */
    constructor(`in`: InputStream, limit: Long) : this(`in`, ByteOrder.LITTLE_ENDIAN, limit)

    /** 指定字节序，无上限。 */
    constructor(`in`: InputStream, bo: ByteOrder) : this(`in`, bo, Long.MAX_VALUE)

    /** 完整构造：底层流 + 字节序 + 可读上限。 */
    constructor(`in`: InputStream, bo: ByteOrder, limit: Long) : super(`in`) {
        mByteOrder = bo
        mLimit = limit
    }

    /** 当前字节序。 */
    fun order(): ByteOrder = mByteOrder

    /** 可读字节上限。 */
    fun limit(): Long = mLimit

    /** 已消费字节位置。 */
    fun position(): Long = mPosition

    /** 剩余可读字节数。 */
    fun remaining(): Long = mLimit - mPosition

    /** 向前跳转到绝对偏移 [pos]；回跳或跳不够时抛 [IOException]。 */
    @Throws(IOException::class)
    fun jumpTo(pos: Long): Long {
        val expected = pos - mPosition
        if (expected == 0L) {
            return 0
        }
        if (expected < 0) {
            throw IOException(String.format("Illegal backwards jump from %s to %s", mPosition, pos))
        }
        val skipped = skip(expected)
        if (skipped != expected) {
            throw IOException(
                String.format("Jump failed: skipped %s bytes (expected: %s)", skipped, expected)
            )
        }
        return skipped
    }

    /** 跳过一个字节。 */
    @Throws(IOException::class)
    fun skipByte() {
        readByte()
    }

    /** 跳过一个 16 位数值。 */
    @Throws(IOException::class)
    fun skipShort() {
        readShort()
    }

    /** 跳过一个 32 位数值。 */
    @Throws(IOException::class)
    fun skipInt() {
        readInt()
    }

    /** 跳过一个 64 位数值。 */
    @Throws(IOException::class)
    fun skipLong() {
        readLong()
    }

    /** 读取 len 个原始字节。 */
    @Throws(IOException::class)
    fun readBytes(len: Int): ByteArray {
        val buf = ByteArray(len)
        readFully(buf)
        return buf
    }

    /** 按当前字节序读取 len 个 16 位数值。 */
    @Throws(IOException::class)
    fun readShortArray(len: Int): ShortArray {
        val arr = ShortArray(len)
        for (i in 0 until len) {
            arr[i] = readShort()
        }
        return arr
    }

    /** 按当前字节序读取 len 个 32 位数值。 */
    @Throws(IOException::class)
    fun readIntArray(len: Int): IntArray {
        val arr = IntArray(len)
        for (i in 0 until len) {
            arr[i] = readInt()
        }
        return arr
    }

    /** 按当前字节序读取 len 个 64 位数值。 */
    @Throws(IOException::class)
    fun readLongArray(len: Int): LongArray {
        val arr = LongArray(len)
        for (i in 0 until len) {
            arr[i] = readLong()
        }
        return arr
    }

    /** 读取 ASCII 字符串（NUL 截断，多余部分跳过）。 */
    @Throws(IOException::class)
    fun readAscii(len: Int): String {
        val buf = CharArray(len)
        var pos = 0
        var n = len
        while (n-- > 0) {
            val ch = readUnsignedByte().toChar()
            if (ch.code == 0) {
                break
            }
            buf[pos++] = ch
        }
        if (n > 0) {
            skipBytes(n)
        }
        return String(buf, 0, pos)
    }

    /** 读取 UTF-16 字符串（len 为字符数，NUL 截断，多余部分跳过）。 */
    @Throws(IOException::class)
    fun readUtf16(len: Int): String {
        val buf = CharArray(len)
        var pos = 0
        var n = len
        while (n-- > 0) {
            val ch = readChar()
            if (ch.code == 0) {
                break
            }
            buf[pos++] = ch
        }
        if (n > 0) {
            skipBytes(n * 2)
        }
        return String(buf, 0, pos)
    }

    // DataInput

    @Throws(IOException::class)
    override fun readFully(b: ByteArray) {
        ByteStreams.readFully(this, b)
    }

    @Throws(IOException::class)
    override fun readFully(b: ByteArray, off: Int, len: Int) {
        ByteStreams.readFully(this, b, off, len)
    }

    @Throws(IOException::class)
    override fun skipBytes(n: Int): Int = skip(n.toLong()).toInt()

    @Throws(IOException::class)
    override fun readBoolean(): Boolean = readUnsignedByte() != 0

    @Throws(IOException::class)
    override fun readByte(): Byte = readUnsignedByte().toByte()

    @Throws(IOException::class)
    override fun readUnsignedByte(): Int {
        val b = read()
        if (b == -1) {
            throw EOFException()
        }
        return b
    }

    @Throws(IOException::class)
    override fun readShort(): Short = readUnsignedShort().toShort()

    @Throws(IOException::class)
    override fun readUnsignedShort(): Int {
        val b1 = readByte()
        val b2 = readByte()
        return if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
            Ints.fromBytes(0, 0, b2, b1)
        } else {
            Ints.fromBytes(b1, b2, 0, 0)
        }
    }

    @Throws(IOException::class)
    override fun readChar(): Char = readUnsignedShort().toChar()

    @Throws(IOException::class)
    override fun readInt(): Int {
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val b4 = readByte()
        return if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
            Ints.fromBytes(b4, b3, b2, b1)
        } else {
            Ints.fromBytes(b1, b2, b3, b4)
        }
    }

    @Throws(IOException::class)
    override fun readLong(): Long {
        val b1 = readByte()
        val b2 = readByte()
        val b3 = readByte()
        val b4 = readByte()
        val b5 = readByte()
        val b6 = readByte()
        val b7 = readByte()
        val b8 = readByte()
        return if (mByteOrder == ByteOrder.LITTLE_ENDIAN) {
            Longs.fromBytes(b8, b7, b6, b5, b4, b3, b2, b1)
        } else {
            Longs.fromBytes(b1, b2, b3, b4, b5, b6, b7, b8)
        }
    }

    @Throws(IOException::class)
    override fun readFloat(): Float = Float.fromBits(readInt())

    @Throws(IOException::class)
    override fun readDouble(): Double = Double.fromBits(readLong())

    @Throws(IOException::class)
    override fun readLine(): String = throw UnsupportedOperationException()

    @Throws(IOException::class)
    override fun readUTF(): String = throw UnsupportedOperationException()

    // InputStream

    @Throws(IOException::class)
    override fun read(): Int {
        if (remaining() == 0L) {
            return -1
        }
        val b = `in`.read()
        if (b != -1) {
            ++mPosition
        }
        return b
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val remain = remaining()
        if (remain == 0L) {
            return -1
        }
        var n = len
        if (n > remain) {
            n = remain.toInt()
        }
        val read = `in`.read(b, off, n)
        if (read > 0) {
            mPosition += read.toLong()
        }
        return read
    }

    @Throws(IOException::class)
    override fun skip(n: Long): Long {
        val remain = remaining()
        if (remain == 0L) {
            return 0
        }
        var target = n
        if (target > remain) {
            target = remain
        }
        // 底层的 skip() 可能少于请求值，这里循环补齐。
        var skipped = 0L
        while (skipped < target) {
            val s = `in`.skip(target - skipped)
            if (s <= 0) {
                break
            }
            skipped += s
        }
        mPosition += skipped
        return skipped
    }

    @Throws(IOException::class)
    override fun available(): Int = minOf(`in`.available().toLong(), remaining()).toInt()

    @Synchronized
    override fun mark(readlimit: Int) {
        // 即使底层不支持 mark 也不能抛异常，照常转发（reset 本就不会生效）。
        `in`.mark(readlimit)
        mMark = mPosition
    }

    @Synchronized
    @Throws(IOException::class)
    override fun reset() {
        if (!markSupported()) {
            throw IOException("Mark not supported")
        }
        if (mMark == -1L) {
            throw IOException("Mark not set")
        }
        `in`.reset()
        mPosition = mMark
    }
}
