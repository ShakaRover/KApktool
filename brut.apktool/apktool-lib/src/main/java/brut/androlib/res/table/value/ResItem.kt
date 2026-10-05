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
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResPackage
import brut.androlib.res.xml.ValuesXmlSerializable
import brut.common.Log

/**
 * 单值资源条目（非"袋"结构）基类，可直接序列化进 values XML。
 *
 * [parse] 按类型码把原始数据分派为引用值或原始值；ResValue.TYPE_STRING 走单独的 ResString 通道。
 */
abstract class ResItem protected constructor(
    /** ResValue.TYPE_* 类型码。 */
    protected val mType: Int,
) : ResValue(), ValuesXmlSerializable {
    /** 类型码。 */
    open fun getType(): Int = mType

    /** 该值对应的 AAPT 格式名（reference/string/...）；未知返回 null。 */
    open fun getFormat(): String? = when (mType) {
        ResValue.TYPE_NULL, ResValue.TYPE_REFERENCE, ResValue.TYPE_DYNAMIC_REFERENCE, ResValue.TYPE_ATTRIBUTE, ResValue.TYPE_DYNAMIC_ATTRIBUTE -> "reference"
        ResValue.TYPE_STRING -> "string"
        ResValue.TYPE_FLOAT -> "float"
        ResValue.TYPE_DIMENSION -> "dimension"
        ResValue.TYPE_FRACTION -> "fraction"
        ResValue.TYPE_INT_BOOLEAN -> "boolean"
        else -> {
            if (mType in ResValue.TYPE_FIRST_COLOR_INT..ResValue.TYPE_LAST_COLOR_INT) {
                return "color"
            }
            if (mType in ResValue.TYPE_FIRST_INT..ResValue.TYPE_LAST_INT) {
                return "integer"
            }
            Log.w(TAG, "Unexpected value type: 0x%02x", mType)
            null
        }
    }

    /** 序列化为 XML 文本节点值（永不返回 null）。 */
    @Throws(AndrolibException::class)
    abstract fun toXmlTextValue(): String

    /** 序列化为 XML 属性值（永不返回 null）。 */
    @Throws(AndrolibException::class)
    open fun toXmlAttributeValue(): String = toXmlTextValue()

    companion object {
        private val TAG = ResItem::class.java.name

        /** 把原始类型码 + data 解析成具体 [ResItem]；无法识别返回 null。 */
        @JvmStatic
        fun parse(pkg: ResPackage, type: Int, data: Int): ResItem? {
            assert(type != ResValue.TYPE_STRING)
            when (type) {
                ResValue.TYPE_NULL -> return if (data == ResValue.DATA_NULL_EMPTY) ResPrimitive.EMPTY else ResPrimitive.NULL
                ResValue.TYPE_REFERENCE, ResValue.TYPE_DYNAMIC_REFERENCE -> return ResReference(pkg, ResId.of(data))
                ResValue.TYPE_ATTRIBUTE, ResValue.TYPE_DYNAMIC_ATTRIBUTE -> return ResReference(pkg, ResId.of(data), true)
                ResValue.TYPE_FLOAT, ResValue.TYPE_DIMENSION, ResValue.TYPE_FRACTION -> return ResPrimitive(type, data)
            }
            // 整数、布尔与颜色统一落到 ResPrimitive。
            if (type in ResValue.TYPE_FIRST_INT..ResValue.TYPE_LAST_INT) {
                return ResPrimitive(type, data)
            }
            Log.w(TAG, "Invalid value type: 0x%02x", type)
            return null
        }
    }
}
