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
import brut.androlib.res.xml.ResStringEncoder
import brut.androlib.res.xml.ResXmlUtils
import brut.common.Log
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.Arrays

/**
 * plurals 资源袋：key 为 android.R.plurals quantity 属性 ID（other/zero/one/two/few/many）。
 *
 * 未知 key 记 warning 并跳过；文本中的格式化占位符会做序归一。
 */
class ResPlural(
    parent: ResReference?,
    private val mItems: Array<RawItem>,
) : ResBag(parent) {
    @Throws(AndrolibException::class, IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val tagName = "plurals"
        serial.startTag(null, tagName)
        serial.attribute(null, "name", entry.getName())
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }

        for (item in mItems) {
            val key = item.key
            val quantity = when (key) {
                ATTR_OTHER -> "other"
                ATTR_ZERO -> "zero"
                ATTR_ONE -> "one"
                ATTR_TWO -> "two"
                ATTR_FEW -> "few"
                ATTR_MANY -> "many"
                else -> {
                    Log.w(TAG, "Invalid plurals key: 0x%08x", key)
                    null
                }
            } ?: continue

            val body = item.value!!.toXmlTextValue()
                .let { if (it.isNotEmpty()) ResStringEncoder.normalizeFormatSpecifiers(it) else it }

            serial.startTag(null, "item")
            serial.attribute(null, "quantity", quantity)
            serial.text(body)
            serial.endTag(null, "item")
        }

        serial.endTag(null, tagName)
    }

    override fun toString(): String = "ResPlural{items=${Arrays.toString(mItems)}}"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        return mItems.contentEquals((other as ResPlural).mItems)
    }

    override fun hashCode(): Int = mItems.contentHashCode()

    companion object {
        private val TAG = ResPlural::class.java.name

        // android.R.plurals 的 quantity 属性资源 ID。
        private const val ATTR_OTHER = 0x01000004
        private const val ATTR_ZERO = 0x01000005
        private const val ATTR_ONE = 0x01000006
        private const val ATTR_TWO = 0x01000007
        private const val ATTR_FEW = 0x01000008
        private const val ATTR_MANY = 0x01000009

        /** 直接采用原始条目构造复数袋。 */
        @JvmStatic
        fun parse(parent: ResReference?, rawItems: Array<RawItem>): ResPlural = ResPlural(parent, rawItems)
    }
}
