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

/**
 * 资源条目的规格：类型规格 + 条目号 + 名字（名字非法时以 APKTOOL_RENAMED_ 前缀替代）。
 */
class ResEntrySpec(
    private val mTypeSpec: ResTypeSpec,
    private val mId: Int,
    name: String,
) {
    private val mResId: ResId = ResId.of(mTypeSpec.`package`.getId(), mTypeSpec.getId(), mId)

    /** 条目名（部分应用会混淆/塌陷条目名，这里统一校正）。 */
    val name: String = if (isValidEntryName(name)) name else RENAMED_PREFIX + mResId

    /** 所属包。 */
    val `package`: ResPackage
        get() = mTypeSpec.`package`

    /** 类型规格。 */
    fun getTypeSpec(): ResTypeSpec = mTypeSpec

    /** 条目号。 */
    fun getId(): Int = mId

    /** 完整资源 ID。 */
    fun getResId(): ResId = mResId

    override fun toString(): String =
        String.format("ResEntrySpec{typeSpec=%s, id=0x%04x, name=%s}", mTypeSpec, mId, name)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResEntrySpec
        return mTypeSpec == that.mTypeSpec && mId == that.mId && name == that.name
    }

    override fun hashCode(): Int {
        var result = mTypeSpec.hashCode()
        result = 31 * result + mId
        result = 31 * result + name.hashCode()
        return result
    }

    companion object {
        /** 合成（public.xml 补齐）条目前缀。 */
        const val DUMMY_PREFIX = "APKTOOL_DUMMY_"

        /** 非法条目名的替换前缀。 */
        const val RENAMED_PREFIX = "APKTOOL_RENAMED_"

        /** 条目名须为合法 Java 标识符（另允许 '.' 与 '-'）。 */
        private fun isValidEntryName(name: String): Boolean {
            val len = name.length
            if (len == 0) {
                return false
            }
            if (!Character.isJavaIdentifierStart(name[0])) {
                return false
            }
            for (i in 1 until len) {
                val ch = name[i]
                if (!Character.isJavaIdentifierPart(ch) && ch != '.' && ch != '-') {
                    return false
                }
            }
            return true
        }
    }
}
