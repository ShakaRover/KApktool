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
package brut.androlib.res.table.value

import brut.androlib.exceptions.AndrolibException
import brut.androlib.res.xml.ValuesXmlSerializable
import brut.common.Log

/**
 * "袋"型资源基类（array/attr/plurals/style）：由一组 key-value 原始条目构成。
 *
 * [resolveKeys] 供子类把整型 key 解析成 attr 引用（延迟到全表载入后）。
 */
abstract class ResBag protected constructor(
    /** 父样式引用（可为 null 表示无父级）。 */
    protected val mParent: ResReference?,
) : ResValue(), ValuesXmlSerializable {
    /** 解析 key（袋型条目引用属性）；默认空实现。 */
    @Throws(AndrolibException::class)
    open fun resolveKeys() {
        // 供可解析 key 的袋类型覆写。
    }

    /** 原始键值对：key 为整型编码（attr 资源 ID 或数组下标）。 */
    class RawItem(
        /** 键（attr 引用或序号；生成 getKey()）。 */
        val key: Int,
        /** 值条目（生成 getValue()）。 */
        val value: ResItem?,
    ) {
        override fun toString(): String =
            String.format("RawItem{key=0x%08x, value=%s}", key, value)
    }

    companion object {
        private val TAG = ResBag::class.java.name

        /** 按类型名分派构造具体袋类型；不支持的类型返回 null。 */
        @JvmStatic
        fun parse(typeName: String, parent: ResReference?, rawItems: Array<RawItem>): ResBag? =
            when (typeName) {
                "array" -> ResArray.parse(parent, rawItems)
                "attr", "^attr-private" -> ResAttribute.parse(parent, rawItems)
                "plurals" -> ResPlural.parse(parent, rawItems)
                "style" -> ResStyle.parse(parent, rawItems)
                else -> {
                    Log.w(TAG, "Unsupported type for bags: $typeName")
                    null
                }
            }
    }
}
