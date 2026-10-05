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
import brut.androlib.res.table.ResEntry
import brut.androlib.res.xml.ResXmlUtils
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.Arrays

/**
 * array 资源袋：有序元素列表。
 *
 * [resolveFormat] 推断统一元素格式（string/integer 可输出 string-array/integer-array），
 * 混合格式则退化为通用 array 标签。
 */
class ResArray(
    parent: ResReference?,
    private val mItems: Array<ResItem?>,
) : ResBag(parent) {
    @Throws(AndrolibException::class, IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val format = resolveFormat()

        // 带类型的数组只有 string-array 与 integer-array，格式可直接用作标签名。
        var tagName = "array"
        if (format == "string" || format == "integer") {
            tagName = "$format-$tagName"
        }

        serial.startTag(null, tagName)
        serial.attribute(null, "name", entry.getName())
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }

        for (value in mItems) {
            serial.startTag(null, "item")
            serial.text(value!!.toXmlTextValue())
            serial.endTag(null, "item")
        }

        serial.endTag(null, tagName)
    }

    /** 推断全数组统一格式；混合/不可判定返回 null。 */
    private fun resolveFormat(): String? {
        var format: String? = null

        for (value in mItems) {
            // 忽略 @null 与 @empty。
            if (value!!.getType() == ResValue.TYPE_NULL) {
                continue
            }

            val itemFormat: String? = if (value is ResReference) {
                // 引用格式本身有歧义：借助被引用条目的类型名推断更具体的格式。
                try {
                    value.resolve()?.getTypeSpec()?.name
                } catch (ignored: AndrolibException) {
                    null
                }
            } else {
                value.getFormat()
            }

            if (itemFormat == null) {
                continue
            } else if (format == null) {
                format = itemFormat
            } else if (format != itemFormat) {
                // 元素格式不一致说明是泛型数组。
                format = null
                break
            }
        }

        return format
    }

    override fun toString(): String = "ResArray{items=${Arrays.toString(mItems)}}"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        return mItems.contentEquals((other as ResArray).mItems)
    }

    override fun hashCode(): Int = mItems.contentHashCode()

    companion object {
        /** 从原始条目构造数组袋。 */
        @JvmStatic
        fun parse(parent: ResReference?, rawItems: Array<RawItem>): ResArray =
            ResArray(parent, Array(rawItems.size) { rawItems[it].value })
    }
}
