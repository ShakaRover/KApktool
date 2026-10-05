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
package brut.androlib.res.table

import brut.androlib.res.table.value.ResItem

/**
 * 资源类型规格（type-id 级别的元数据）。
 *
 * 类型名必须命中 AAPT 标准表，否则视为混淆/恶意并改名 invalid%02X；
 * 同时维护"哪些值格式与该类型兼容"的判定（[isValueCompatible]）。
 */
class ResTypeSpec(
    private val mPackage: ResPackage,
    private val mId: Int,
    name: String,
) {
    /** 类型名（非法名已校正）。 */
    val name: String = if (isValidTypeName(name)) name else String.format("invalid%02X", mId)

    /** 所属包。 */
    val `package`: ResPackage
        get() = mPackage

    /** 类型号。 */
    fun getId(): Int = mId

    /** 是否为复合"袋"类型（array/attr/plurals/style）。 */
    fun isBagType(): Boolean = when (name) {
        "array", "attr", "^attr-private", "plurals", "style" -> true
        else -> false
    }

    /** 判断某值格式是否与类型兼容（reference 格式对所有非袋类型通用）。 */
    fun isValueCompatible(value: ResItem): Boolean {
        // 袋类型不支持单值条目。
        if (isBagType()) {
            return false
        }
        val format = value.getFormat() ?: return false
        // 所有条目类型都支持引用格式。
        if (format == "reference") {
            return true
        }
        return STANDARD_TYPE_FORMATS[name]?.contains(format) ?: false
    }

    override fun toString(): String =
        String.format("ResTypeSpec{pkg=%s, id=0x%02x, name=%s}", mPackage, mId, name)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResTypeSpec
        return mPackage == that.mPackage && mId == that.mId && name == that.name
    }

    override fun hashCode(): Int {
        var result = mPackage.hashCode()
        result = 31 * result + mId
        result = 31 * result + name.hashCode()
        return result
    }

    companion object {
        /** AAPT 标准类型允许的取值格式表。 */
        private val STANDARD_TYPE_FORMATS: Map<String, Set<String>> = mapOf(
            "bool" to setOf("boolean"),
            "color" to setOf("color"),
            "dimen" to setOf("float", "fraction", "dimension"),
            "drawable" to setOf("color"),
            "fraction" to setOf("float", "fraction", "dimension"),
            "integer" to setOf("integer"),
            "string" to setOf("string"),
        )

        /** 合法类型名白名单。 */
        private fun isValidTypeName(name: String): Boolean = name in setOf(
            "anim", "animator", "array", "attr", "^attr-private", "bool", "color", "dimen",
            "drawable", "font", "fraction", "id", "integer", "interpolator", "layout", "menu",
            "mipmap", "navigation", "plurals", "raw", "string", "style", "transition", "xml",
        )
    }
}
