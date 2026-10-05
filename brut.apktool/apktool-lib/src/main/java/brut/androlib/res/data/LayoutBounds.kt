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
 * 从 9.png 分块数据中解析出的布局边界（大端四个 int）。
 */
class LayoutBounds(
    @JvmField val left: Int,
    @JvmField val top: Int,
    @JvmField val right: Int,
    @JvmField val bottom: Int,
) {
    companion object {
        /** 分块魔数 "npLb"。 */
        @JvmField
        val MAGIC: Int = 0x6E704C62

        /** 标记分割线使用的纯红色。 */
        @JvmField
        val COLOR_TICK: Int = 0xFFFF0000.toInt()

        /** 从流中读取边界（大端序）。 */
        @JvmStatic
        @Throws(IOException::class)
        fun `read`(`in`: BinaryDataInputStream): LayoutBounds {
            val left = Integer.reverseBytes(`in`.readInt())
            val top = Integer.reverseBytes(`in`.readInt())
            val right = Integer.reverseBytes(`in`.readInt())
            val bottom = Integer.reverseBytes(`in`.readInt())
            return LayoutBounds(left, top, right, bottom)
        }
    }
}
