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
package brut.androlib.res.decoder

import brut.androlib.res.data.ResChunkHeader
import brut.util.BinaryDataInputStream
import java.io.EOFException
import java.io.IOException

/**
 * RES 文件 chunk 级游标解析器：在 [mOffset, mOffset+mSize) 区间内顺序遍历 chunk 头。
 *
 * [next] 前进到下一 chunk（当前 chunk 自动跳过）；越过大小界限或流 EOF 即结束。
 */
class ResChunkPullParser @JvmOverloads constructor(
    private val mIn: BinaryDataInputStream,
    size: Int = Integer.MAX_VALUE,
) {
    private val mOffset: Long = mIn.position()
    private val mSize: Int = size
    private var mChunkOffset: Long = 0
    private var mChunkHeader: ResChunkHeader? = null

    init {
        assert(mIn.order() == java.nio.ByteOrder.LITTLE_ENDIAN)
    }

    /** 底层数据流。 */
    fun stream(): BinaryDataInputStream = mIn

    /** 当前是否停在有效 chunk 上。 */
    fun isChunk(): Boolean = mChunkHeader != null

    /** chunk 起始绝对偏移。 */
    fun chunkStart(): Long = requireHeader().let { mChunkOffset }

    /** chunk 类型码。 */
    fun chunkType(): Int = requireHeader().type

    /** chunk 类型可读名。 */
    fun chunkName(): String = ResChunkHeader.nameOf(requireHeader().type)

    /** chunk 总大小。 */
    fun chunkSize(): Int = requireHeader().size

    /** chunk 结束绝对偏移。 */
    fun chunkEnd(): Long = mChunkOffset + requireHeader().size

    /** chunk 头部大小。 */
    fun headerSize(): Int = requireHeader().headerSize

    /** chunk 头部结束绝对偏移。 */
    fun headerEnd(): Long = mChunkOffset + requireHeader().headerSize

    /** chunk 数据区大小。 */
    fun dataSize(): Int = requireHeader().let { it.size - it.headerSize }

    /** 前进到下一个 chunk；无更多 chunk 返回 false。 */
    @Throws(IOException::class)
    fun next(): Boolean {
        if (mChunkOffset == OFFSET_ENDED) {
            return false
        }

        // 跳到下一 chunk。
        if (mChunkHeader != null) {
            skipChunk()
            mChunkHeader = null
        }

        if (mIn.position() >= mOffset + mSize) {
            // 因大小界限结束。
            mChunkOffset = OFFSET_ENDED
            return false
        }

        // 读取当前位置的 chunk 头。
        try {
            mChunkOffset = mIn.position()
            val chunkHeader = ResChunkHeader.read(mIn)

            if (chunkHeader.headerSize < ResChunkHeader.SIZE || chunkHeader.size < chunkHeader.headerSize) {
                throw IOException(
                    String.format(
                        "Invalid chunk header: type=0x%04x, headerSize=%s, size=%s",
                        chunkHeader.type, chunkHeader.headerSize, chunkHeader.size
                    )
                )
            }

            mChunkHeader = chunkHeader
            return true
        } catch (ignored: EOFException) {
            // 流提前结束。
            mChunkOffset = OFFSET_ENDED
            return false
        } catch (ex: IOException) {
            throw IOException("Error while reading chunk header.", ex)
        }
    }

    /** 跳到当前 chunk 末尾，返回跳过的字节数。 */
    @Throws(IOException::class)
    fun skipChunk(): Int {
        requireHeader()
        return try {
            val position = mIn.position()
            val chunkEnd = chunkEnd()
            if (position == chunkEnd) {
                return 0
            }
            if (position > chunkEnd) {
                throw IOException("Stream advanced past chunk end.")
            }
            mIn.skipBytes((chunkEnd - position).toInt())
        } catch (ignored: EOFException) {
            throw EOFException("Unexpected EOF while skipping chunk.")
        } catch (ex: IOException) {
            throw IOException("Error while skipping chunk.", ex)
        }
    }

    /** 跳到当前 chunk 头末尾，返回跳过的字节数。 */
    @Throws(IOException::class)
    fun skipHeader(): Int {
        requireHeader()
        return try {
            val position = mIn.position()
            val headerEnd = headerEnd()
            if (position == headerEnd) {
                return 0
            }
            if (position > headerEnd) {
                throw IOException("Stream advanced past chunk header end.")
            }
            mIn.skipBytes((headerEnd - position).toInt())
        } catch (ignored: EOFException) {
            throw EOFException("Unexpected EOF while skipping chunk header.")
        } catch (ex: IOException) {
            throw IOException("Error while skipping chunk header.", ex)
        }
    }

    private fun requireHeader(): ResChunkHeader =
        mChunkHeader ?: throw IllegalStateException()

    companion object {
        /** 表示已无更多 chunk 的哨兵偏移。 */
        private const val OFFSET_ENDED = -1L
    }
}
