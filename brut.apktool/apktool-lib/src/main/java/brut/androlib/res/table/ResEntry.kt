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

import brut.androlib.res.table.value.ResValue

/**
 * 资源表中的一个具体资源条目：类型上下文 + 规格（名字/ID）+ 值。
 *
 * 值可在构建阶段被替换（见 [setValue]）；相等性只看类型与规格，不含值。
 * Java 端仍按 getX()/setX() 访问，由属性自动生成。
 */
class ResEntry(
    private val mType: ResType,
    private val mSpec: ResEntrySpec,
    /** 条目值（允许延迟填充）。 */
    var value: ResValue?,
) {
    /** 所属包。 */
    val `package`: ResPackage
        get() = mType.`package`

    /** 所属类型。 */
    fun getType(): ResType = mType

    /** 条目规格。 */
    fun getSpec(): ResEntrySpec = mSpec

    /** 条目号（entryId）。 */
    fun getId(): Int = mSpec.getId()

    /** 完整资源 ID。 */
    fun getResId(): ResId = mSpec.getResId()

    /** 资源名。 */
    fun getName(): String = mSpec.name

    override fun toString(): String =
        String.format("ResEntry{type=%s, spec=%s, value=%s}", mType, mSpec, value)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResEntry
        return mType == that.mType && mSpec == that.mSpec
    }

    override fun hashCode(): Int = 31 * mType.hashCode() + mSpec.hashCode()
}
