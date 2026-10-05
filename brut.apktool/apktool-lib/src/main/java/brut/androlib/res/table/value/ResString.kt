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
import brut.androlib.res.xml.ResStringEncoder
import brut.androlib.res.xml.ResXmlUtils
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

/**
 * 字符串资源值（TYPE_STRING），值可以是带样式的 StyledString。
 *
 * 序列化文本/属性值分别走 [ResStringEncoder] 的两套转义规则；
 * 当存在多个顺序格式化占位符（%s 等）时必须写 formatted="false"。
 */
class ResString(
    /** 字符串值（可能带样式跨度）。 */
    val value: CharSequence,
) : ResItem(ResValue.TYPE_STRING) {
    override fun toXmlTextValue(): String = ResStringEncoder.encodeTextValue(value)

    override fun toXmlAttributeValue(): String = ResStringEncoder.encodeAttributeValue(value)

    @Throws(IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val typeName = entry.getType().getName()

        // 类型不支持 string 格式时退化为 <item type=...>。
        val asItem = !entry.getType().getSpec().isValueCompatible(this)

        val tagName = if (asItem) "item" else typeName
        serial.startTag(null, tagName)
        if (asItem) {
            serial.attribute(null, "type", typeName)
        }
        serial.attribute(null, "name", entry.getName())
        if (asItem) {
            serial.attribute(null, "format", getFormat())
        }
        if (!asItem && !isFormatted()) {
            serial.attribute(null, "formatted", "false")
        }
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }
        val body = toXmlTextValue()
        if (body.isNotEmpty()) {
            serial.text(body)
        }
        serial.endTag(null, tagName)
    }

    /** 空串/单一占位符可安全格式化；多个顺序占位符则必须关闭格式化。 */
    private fun isFormatted(): Boolean {
        if (value.length == 0) {
            return true
        }
        val specs = ResStringEncoder.findFormatSpecifiers(value.toString())
        val sequential = specs[0]
        val positional = specs[1]
        return sequential.isEmpty() || sequential.size + positional.size <= 1
    }

    override fun toString(): String = "ResString{value=$value}"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        return value == (other as ResString).value
    }

    override fun hashCode(): Int = value.hashCode()

    companion object {
        /** 空字符串常量。 */
        @JvmField
        val EMPTY = ResString("")
    }
}
