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
import brut.common.Log
import org.xmlpull.v1.XmlSerializer
import java.io.IOException
import java.util.Locale

/**
 * 原始值资源条目：null/浮点/尺寸/占比/整数/布尔/颜色。
 *
 * 尺寸与占比使用 AAPT 复数编码（尾数 24 位 + 基数 2 位 + 单位 4 位），
 * 由 [complexToFloat] 还原；NULL/EMPTY/FALSE/TRUE 提供共享常量实例。
 */
class ResPrimitive(
    type: Int,
    /** 原始 data 字段。 */
    private val mData: Int,
) : ResItem(type) {
    /** 原始数据。 */
    fun getData(): Int = mData

    @Throws(IOException::class)
    override fun toXmlTextValue(): String = when (mType) {
        ResValue.TYPE_NULL -> if (mData == ResValue.DATA_NULL_EMPTY) "@empty" else "@null"
        ResValue.TYPE_FLOAT -> floatToString(Float.fromBits(mData))
        ResValue.TYPE_DIMENSION -> {
            var value = floatToString(complexToFloat(mData))
            value += when (mData and COMPLEX_UNIT_MASK) {
                COMPLEX_UNIT_PX -> "px"
                COMPLEX_UNIT_DIP -> "dp"
                COMPLEX_UNIT_SP -> "sp"
                COMPLEX_UNIT_PT -> "pt"
                COMPLEX_UNIT_IN -> "in"
                COMPLEX_UNIT_MM -> "mm"
                else -> {
                    Log.w(TAG, "Unexpected value unit: ${mData and COMPLEX_UNIT_MASK}")
                    "??"
                }
            }
            value
        }
        ResValue.TYPE_FRACTION -> {
            var value = floatToString(complexToFloat(mData) * 100)
            value += when (mData and COMPLEX_UNIT_MASK) {
                COMPLEX_UNIT_FRACTION -> "%"
                COMPLEX_UNIT_FRACTION_PARENT -> "%p"
                else -> {
                    Log.w(TAG, "Unexpected value unit: ${mData and COMPLEX_UNIT_MASK}")
                    "??"
                }
            }
            value
        }
        ResValue.TYPE_INT_BOOLEAN -> if (mData != 0) "true" else "false"
        else -> {
            if (mType in ResValue.TYPE_FIRST_COLOR_INT..ResValue.TYPE_LAST_COLOR_INT) {
                return when (mType) {
                    ResValue.TYPE_INT_COLOR_RGB8 -> String.format("#%06x", mData and 0xFFFFFF)
                    ResValue.TYPE_INT_COLOR_ARGB4 -> String.format(
                        "#%x%x%x%x",
                        (mData ushr 28) and 0xF, (mData ushr 20) and 0xF,
                        (mData ushr 12) and 0xF, (mData ushr 4) and 0xF,
                    )
                    ResValue.TYPE_INT_COLOR_RGB4 -> String.format(
                        "#%x%x%x",
                        (mData ushr 20) and 0xF, (mData ushr 12) and 0xF, (mData ushr 4) and 0xF,
                    )
                    else -> String.format("#%08x", mData)
                }
            }
            if (mType in ResValue.TYPE_FIRST_INT..ResValue.TYPE_LAST_INT) {
                return when (mType) {
                    ResValue.TYPE_INT_HEX -> String.format("0x%x", mData)
                    else -> mData.toString()
                }
            }
            Log.w(TAG, "Unexpected value type: 0x%02x", mType)
            ""
        }
    }

    @Throws(IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val typeName = entry.getType().getName()

        // 类型不直接支持该值格式时，序列化为通用 <item> 标签。
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
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }
        serial.text(toXmlTextValue())
        serial.endTag(null, tagName)
    }

    override fun toString(): String =
        String.format("ResPrimitive{type=0x%02x, data=0x%08x}", mType, mData)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResPrimitive
        return mType == that.mType && mData == that.mData
    }

    override fun hashCode(): Int = 31 * mType + mData

    companion object {
        private val TAG = ResPrimitive::class.java.name

        // 复数数据编码字段。
        private const val COMPLEX_UNIT_MASK = 0xF
        private const val COMPLEX_RADIX_SHIFT = 4
        private const val COMPLEX_RADIX_MASK = 0x3
        private const val COMPLEX_MANTISSA_SHIFT = 8
        private const val COMPLEX_MANTISSA_MASK = 0xFFFFFF
        private const val MANTISSA_MULT = 1.0f / (1 shl COMPLEX_MANTISSA_SHIFT)
        private val RADIX_MULTS = floatArrayOf(
            MANTISSA_MULT, 1.0f / (1 shl 7) * MANTISSA_MULT, 1.0f / (1 shl 15) * MANTISSA_MULT,
            1.0f / (1 shl 23) * MANTISSA_MULT,
        )

        // ResValue.TYPE_DIMENSION 的复数单位。
        private const val COMPLEX_UNIT_PX = 0
        private const val COMPLEX_UNIT_DIP = 1
        private const val COMPLEX_UNIT_SP = 2
        private const val COMPLEX_UNIT_PT = 3
        private const val COMPLEX_UNIT_IN = 4
        private const val COMPLEX_UNIT_MM = 5

        // ResValue.TYPE_FRACTION 的复数单位。
        private const val COMPLEX_UNIT_FRACTION = 0
        private const val COMPLEX_UNIT_FRACTION_PARENT = 1

        /** 未指定值（@null）。 */
        @JvmField
        val NULL = ResPrimitive(ResValue.TYPE_NULL, ResValue.DATA_NULL_UNDEFINED)

        /** 显式空值（@empty）。 */
        @JvmField
        val EMPTY = ResPrimitive(ResValue.TYPE_NULL, ResValue.DATA_NULL_EMPTY)

        /** 布尔 false。 */
        @JvmField
        val FALSE = ResPrimitive(ResValue.TYPE_INT_BOOLEAN, 0)

        /** 布尔 true。 */
        @JvmField
        val TRUE = ResPrimitive(ResValue.TYPE_INT_BOOLEAN, -1) // 0xFFFFFFFF

        /** 浮点转文本：整数值也保留一位小数以显式区分浮点。 */
        private fun floatToString(value: Float): String {
            if (value == value.toLong().toFloat()) {
                return String.format(Locale.ROOT, "%.1f", value)
            }
            return value.toString()
        }

        /** 复数编码还原为浮点：尾数 * 基数乘子。 */
        private fun complexToFloat(complex: Int): Float =
            (complex and (COMPLEX_MANTISSA_MASK shl COMPLEX_MANTISSA_SHIFT)) *
                RADIX_MULTS[(complex shr COMPLEX_RADIX_SHIFT) and COMPLEX_RADIX_MASK]
    }
}
