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

/**
 * 带样式跨度（span）的字符串：字符串池条目解析结果，
 * 每个 span 表示一段 [firstChar, lastChar) 内应用的标签（如 <b>）。
 */
class StyledString(
    private val mValue: String,
    private val mSpans: Array<Span>,
) : CharSequence {
    /** 原始字符串值。 */
    val value: String get() = mValue

    /** 样式跨度数组。 */
    val spans: Array<Span> get() = mSpans

    override val length: Int get() = mValue.length

    override fun get(index: Int): Char = mValue[index]

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        mValue.subSequence(startIndex, endIndex)

    override fun toString(): String = mValue

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as StyledString
        return mValue == that.mValue && mSpans.contentEquals(that.mSpans)
    }

    override fun hashCode(): Int = 31 * mValue.hashCode() + mSpans.contentHashCode()

    /** 单段样式跨度。 */
    class Span(
        /** 标签名（如 b、i）。 */
        val tag: String,
        /** 起始字符下标（含）。 */
        val firstChar: Int,
        /** 结束字符下标（不含）。 */
        val lastChar: Int,
    ) {
        override fun toString(): String =
            String.format("Span{tag=%s, firstChar=%s, lastChar=%s}", tag, firstChar, lastChar)
    }
}
