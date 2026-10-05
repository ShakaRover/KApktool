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
package brut.androlib.res.table

/**
 * 32 位资源 ID（package/type/entry 三段），[Number] 子类支持直接参与数值运算，
 * 内部缓存保证同一 ID 全局唯一实例（引用相等于值相等）。
 */
class ResId private constructor(
    private val mId: Int,
) : Number(), Comparable<ResId> {
    /** package 段（高 8 位）。 */
    fun pkgId(): Int = mId ushr 24 and 0xFF

    /** type 段（中 8 位）。 */
    fun typeId(): Int = mId ushr 16 and 0xFF

    /** entry 段（低 16 位）。 */
    fun entryId(): Int = mId and 0xFFFF

    override fun toByte(): Byte = mId.toByte()
    override fun toChar(): Char = mId.toChar()
    override fun toShort(): Short = mId.toShort()
    override fun toInt(): Int = mId
    override fun toLong(): Long = mId.toLong()
    override fun toFloat(): Float = mId.toFloat()
    override fun toDouble(): Double = mId.toDouble()

    override fun toString(): String = String.format("0x%08x", mId)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        return mId == (other as ResId).mId
    }

    override fun hashCode(): Int = mId

    override fun compareTo(other: ResId): Int = mId.compareTo(other.mId)

    companion object {
        /** 资源 ID 0（无效/未引用）。 */
        @JvmField
        val NULL = ResId(0)

        /** 实例缓存（非线程安全，与既有语义一致）。 */
        private val sCache = HashMap<Int, ResId>()

        /** 取指定数值的 [ResId] 实例（0 返回 [NULL]）。 */
        @JvmStatic
        fun of(id: Int): ResId =
            if (id != 0) sCache.computeIfAbsent(id) { ResId(it) } else NULL

        /** 由三段分量组装 [ResId]。 */
        @JvmStatic
        fun of(pkgId: Int, typeId: Int, entryId: Int): ResId =
            of((pkgId shl 24) or (typeId shl 16) or entryId)
    }
}
