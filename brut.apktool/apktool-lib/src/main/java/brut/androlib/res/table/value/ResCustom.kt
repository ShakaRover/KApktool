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

import brut.androlib.res.table.ResEntry
import brut.androlib.res.xml.ResXmlUtils
import brut.androlib.res.xml.ValuesXmlSerializable
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

/**
 * 自定义（非数值）资源条目：如 <id> 与框架层特殊类型。
 *
 * [asItem] 为真时输出为 <item type=...>；[value] 非空则作为文本节点。
 */
class ResCustom(
    private val mType: String,
    private val mValue: Any?,
    private val mAsItem: Boolean,
) : ResValue(), ValuesXmlSerializable {
    /** 仅声明类型（如 id）。 */
    constructor(type: String) : this(type, null, false)

    /** 声明类型并可选以 item 形式输出。 */
    constructor(type: String, asItem: Boolean) : this(type, null, asItem)

    /** 声明类型与文本值。 */
    constructor(type: String, value: Any?) : this(type, value, false)

    @Throws(IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val tagName = if (mAsItem) "item" else mType
        serial.startTag(null, tagName)
        if (mAsItem) {
            serial.attribute(null, "type", mType)
        }
        serial.attribute(null, "name", entry.getName())
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }
        mValue?.let { serial.text(it.toString()) }
        serial.endTag(null, tagName)
    }

    override fun toString(): String =
        String.format("ResCustom{type=%s, value=%s, asItem=%s}", mType, mValue, mAsItem)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResCustom
        return mType == that.mType && mValue == that.mValue && mAsItem == that.mAsItem
    }

    override fun hashCode(): Int {
        var result = mType.hashCode()
        result = 31 * result + (mValue?.hashCode() ?: 0)
        result = 31 * result + mAsItem.hashCode()
        return result
    }

    companion object {
        /** id 类型常量实例。 */
        @JvmField
        val ID = ResCustom("id")
    }
}
