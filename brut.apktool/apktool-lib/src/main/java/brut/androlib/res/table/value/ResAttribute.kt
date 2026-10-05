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
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResPackage
import brut.androlib.res.xml.ResStringEncoder
import brut.androlib.res.xml.ResXmlUtils
import brut.common.Log
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

/**
 * attr 资源袋：属性声明（format/min/max/localization）。
 *
 * [parse] 扫描前导元数据条目；遇到枚举/标志位类型时构造 [ResEnum] / [ResFlags] 子类；
 * 纯基础类型时 [addValueType] 允许从使用点合并缺失的类型位。
 */
open class ResAttribute(
    parent: ResReference?,
    /** ATTR_TYPE_* 位掩码（可延迟合并）。 */
    protected var mType: Int,
    protected val mMin: Int,
    protected val mMax: Int,
    protected val mL10n: Int,
) : ResBag(parent) {
    /** 属性符号：名字引用 + 整型值。 */
    open class Symbol(
        /** 符号名（生成的 ID 资源引用；生成 getKey()）。 */
        val key: ResReference,
        /** 符号值（生成 getValue()）。 */
        val value: ResPrimitive,
    ) {
        override fun toString(): String = "Symbol{key=$key, value=$value}"
    }

    /** 合并使用点观察到的值类型（当声明为 ANY 时保持 ANY 不变）。 */
    fun addValueType(valueType: Int) {
        if (mType and ATTR_TYPE_ANY == ATTR_TYPE_ANY) {
            return
        }
        when (valueType) {
            ResValue.TYPE_NULL, ResValue.TYPE_REFERENCE, ResValue.TYPE_DYNAMIC_REFERENCE,
            ResValue.TYPE_ATTRIBUTE, ResValue.TYPE_DYNAMIC_ATTRIBUTE,
            -> mType = mType or ATTR_TYPE_REFERENCE
            ResValue.TYPE_STRING -> mType = mType or ATTR_TYPE_STRING
            ResValue.TYPE_FLOAT -> mType = mType or ATTR_TYPE_FLOAT
            ResValue.TYPE_DIMENSION -> mType = mType or ATTR_TYPE_DIMENSION
            ResValue.TYPE_FRACTION -> mType = mType or ATTR_TYPE_FRACTION
            ResValue.TYPE_INT_BOOLEAN -> mType = mType or ATTR_TYPE_BOOLEAN
            else -> {
                if (valueType in ResValue.TYPE_FIRST_COLOR_INT..ResValue.TYPE_LAST_COLOR_INT) {
                    mType = mType or ATTR_TYPE_COLOR
                } else if (valueType in ResValue.TYPE_FIRST_INT..ResValue.TYPE_LAST_INT) {
                    mType = mType or ATTR_TYPE_INTEGER
                }
            }
        }
    }

    /** 值是否命中符号表。 */
    fun hasSymbolsForValue(value: ResItem?): Boolean = getSymbolsForValue(value) != null

    /** 符号查找钩子：基类无符号。 */
    protected open fun getSymbolsForValue(value: ResItem?): Array<Symbol>? = null

    /** 以文本形式格式化值。 */
    @Throws(AndrolibException::class)
    open fun formatAsTextValue(value: ResItem?): String = formatValue(value, false)

    /** 以属性值形式格式化值。 */
    @Throws(AndrolibException::class)
    fun formatAsAttributeValue(value: ResItem?): String = formatValue(value, true)

    @Throws(AndrolibException::class)
    private fun formatValue(value: ResItem?, asAttrValue: Boolean): String {
        formatValueFromSymbols(value)?.let { return it }

        // 字符串属性值需要按属性类型转义。
        if (asAttrValue && value is ResString) {
            return ResStringEncoder.encodeAttributeValue(value.value, mType)
        }

        return if (asAttrValue) value!!.toXmlAttributeValue() else value!!.toXmlTextValue()
    }

    /** 符号格式化钩子：基类返回 null。 */
    @Throws(AndrolibException::class)
    protected open fun formatValueFromSymbols(value: ResItem?): String? = null

    @Throws(AndrolibException::class, IOException::class)
    override fun serializeToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        val tagName = "attr"
        serial.startTag(null, tagName)
        serial.attribute(null, "name", entry.getName())
        renderFormat()?.let { serial.attribute(null, "format", it) }
        if (mMin != Int.MIN_VALUE) {
            serial.attribute(null, "min", mMin.toString())
        }
        if (mMax != Int.MAX_VALUE) {
            serial.attribute(null, "max", mMax.toString())
        }
        if (mL10n == ATTR_L10N_SUGGESTED) {
            serial.attribute(null, "localization", "suggested")
        }
        val flag = entry.getType().flag
        if (flag != null) {
            serial.attribute(ResXmlUtils.ANDROID_RES_NS, "featureFlag", flag.toString())
        }
        serializeSymbolsToValuesXml(serial, entry)
        serial.endTag(null, tagName)
    }

    /** 渲染 format="a|b"；ANY 或空返回 null。 */
    private fun renderFormat(): String? {
        if (mType and ATTR_TYPE_ANY == ATTR_TYPE_ANY) {
            return null
        }
        val sb = StringBuilder()
        for (i in ATTR_TYPE_MASKS.indices) {
            if (mType and ATTR_TYPE_MASKS[i] != 0) {
                if (sb.isNotEmpty()) {
                    sb.append('|')
                }
                sb.append(ATTR_TYPE_NAMES[i])
            }
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** 符号序列化钩子：基类无输出。 */
    @Throws(AndrolibException::class, IOException::class)
    protected open fun serializeSymbolsToValuesXml(serial: XmlSerializer, entry: ResEntry) {
        // 含符号的属性类型覆写。
    }

    override fun toString(): String =
        String.format("ResAttribute{type=0x%04x, min=%s, max=%s, l10n=%s}", mType, mMin, mMax, mL10n)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResAttribute
        return mType == that.mType && mMin == that.mMin && mMax == that.mMax && mL10n == that.mL10n
    }

    override fun hashCode(): Int {
        var result = mType
        result = 31 * result + mMin
        result = 31 * result + mMax
        result = 31 * result + mL10n
        return result
    }

    companion object {
        private val TAG = ResAttribute::class.java.name

        private const val ATTR_TYPE = 0x01000000
        private const val ATTR_MIN = 0x01000001
        private const val ATTR_MAX = 0x01000002
        private const val ATTR_L10N = 0x01000003

        /** 未声明任何具体格式。 */
        const val ATTR_TYPE_ANY: Int = 0x0000FFFF
        /** 允许引用。 */
        const val ATTR_TYPE_REFERENCE = 1 // 0x01
        /** 允许字符串。 */
        const val ATTR_TYPE_STRING = 2 // 0x02
        /** 允许整数。 */
        const val ATTR_TYPE_INTEGER = 4 // 0x04
        /** 允许布尔。 */
        const val ATTR_TYPE_BOOLEAN = 8 // 0x08
        /** 允许颜色。 */
        const val ATTR_TYPE_COLOR = 16 // 0x10
        /** 允许浮点。 */
        const val ATTR_TYPE_FLOAT = 32 // 0x20
        /** 允许尺寸。 */
        const val ATTR_TYPE_DIMENSION = 64 // 0x40
        /** 允许占比。 */
        const val ATTR_TYPE_FRACTION = 128 // 0x80
        /** 枚举符号表。 */
        const val ATTR_TYPE_ENUM = 65536 // 0x00010000
        /** 标志位符号表。 */
        const val ATTR_TYPE_FLAGS = 131072 // 0x00020000

        private val ATTR_TYPE_MASKS = intArrayOf(
            ATTR_TYPE_STRING, ATTR_TYPE_INTEGER, ATTR_TYPE_BOOLEAN, ATTR_TYPE_COLOR, ATTR_TYPE_FLOAT,
            ATTR_TYPE_DIMENSION, ATTR_TYPE_FRACTION, ATTR_TYPE_REFERENCE,
        )
        private val ATTR_TYPE_NAMES = arrayOf(
            "string", "integer", "boolean", "color", "float", "dimension", "fraction", "reference",
        )

        private const val ATTR_L10N_NOT_REQUIRED = 0
        private const val ATTR_L10N_SUGGESTED = 1

        /** 通配默认属性（用于合成/回退场景）。 */
        @JvmField
        val DEFAULT = ResAttribute(null, ATTR_TYPE_ANY, Int.MIN_VALUE, Int.MAX_VALUE, ATTR_L10N_NOT_REQUIRED)

        /** 解析 attr 袋：先读元数据，再收集符号并分派具体子类。 */
        @JvmStatic
        fun parse(parent: ResReference?, rawItems: Array<RawItem>): ResAttribute {
            var type = ATTR_TYPE_ANY
            var min = Int.MIN_VALUE
            var max = Int.MAX_VALUE
            var l10n = ATTR_L10N_NOT_REQUIRED

            var i = 0
            val n = rawItems.size
            while (i < n) {
                val rawItem = rawItems[i]
                val value = rawItem.value as ResPrimitive
                when (rawItem.key) {
                    ATTR_TYPE -> {
                        type = value.getData()
                        i++
                        continue
                    }
                    ATTR_MIN -> {
                        min = value.getData()
                        i++
                        continue
                    }
                    ATTR_MAX -> {
                        max = value.getData()
                        i++
                        continue
                    }
                    ATTR_L10N -> {
                        l10n = value.getData()
                        i++
                        continue
                    }
                }
                break
            }
            if (i == n) {
                // 属性不含符号表。
                return ResAttribute(parent, type, min, max, l10n)
            }

            val symbols = arrayOfNulls<Symbol>(n - i)
            val pkg = parent!!.`package`

            var j = 0
            while (i < n) {
                val rawItem = rawItems[i]
                // 符号名是指向生成的 ID 资源的引用。
                val name = ResReference(pkg, ResId.of(rawItem.key))
                val value = rawItem.value as ResPrimitive

                symbols[j++] = Symbol(name, value)
                i++
            }

            @Suppress("UNCHECKED_CAST")
            val typedSymbols = symbols as Array<Symbol>
            return when {
                type and ATTR_TYPE_ENUM != 0 -> ResEnum(parent, type, min, max, l10n, typedSymbols)
                type and ATTR_TYPE_FLAGS != 0 -> ResFlags(parent, type, min, max, l10n, typedSymbols)
                else -> {
                    Log.w(TAG, "Invalid attribute type: 0x%08x", type)
                    ResAttribute(parent, type, min, max, l10n)
                }
            }
        }
    }
}
