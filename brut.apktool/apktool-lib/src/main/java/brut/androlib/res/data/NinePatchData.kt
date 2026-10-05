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

import brut.util.BinaryDataInputStream
import java.io.IOException

/**
 * 从 9.png 分块数据中解析出的分割线（x/y Divs）与内容 padding。
 */
class NinePatchData(
    @JvmField val xDivs: IntArray,
    @JvmField val yDivs: IntArray,
    @JvmField val paddingLeft: Int,
    @JvmField val paddingRight: Int,
    @JvmField val paddingTop: Int,
    @JvmField val paddingBottom: Int,
) {
    companion object {
        /** 分块魔数 "npTc"。 */
        @JvmField
        val MAGIC: Int = 0x6E705463

        /** 标记分割线使用的纯黑色。 */
        @JvmField
        val COLOR_TICK: Int = 0xFF000000.toInt()

        /** 从流中读取 chunk 结构（大端字段布局）。 */
        @JvmStatic
        @Throws(IOException::class)
        fun `read`(`in`: BinaryDataInputStream): NinePatchData {
            `in`.skipByte() // wasDeserialized
            val numXDivs = `in`.readUnsignedByte()
            val numYDivs = `in`.readUnsignedByte()
            `in`.skipByte() // numColors
            `in`.skipInt() // xDivsOffset
            `in`.skipInt() // yDivsOffset
            val paddingLeft = `in`.readInt()
            val paddingRight = `in`.readInt()
            val paddingTop = `in`.readInt()
            val paddingBottom = `in`.readInt()
            `in`.skipInt() // colorsOffset
            val xDivs = `in`.readIntArray(numXDivs)
            val yDivs = `in`.readIntArray(numYDivs)

            return NinePatchData(xDivs, yDivs, paddingLeft, paddingRight, paddingTop, paddingBottom)
        }
    }
}
