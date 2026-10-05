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

import brut.androlib.res.data.FeatureFlag

/**
 * 类型的一个具体配置实例：类型规格 + 资源配置（限定词）+ 可选特性标志。
 */
class ResType(
    private val mSpec: ResTypeSpec,
    private val mConfig: ResConfig,
    /** 特性标志（flagged 资源使用，可为 null）。 */
    val flag: FeatureFlag?,
) {
    /** 所属包。 */
    val `package`: ResPackage
        get() = mSpec.`package`

    /** 类型规格。 */
    fun getSpec(): ResTypeSpec = mSpec

    /** 类型号。 */
    fun getId(): Int = mSpec.getId()

    /** 类型名。 */
    fun getName(): String = mSpec.name

    /** 资源配置（限定词集合）。 */
    fun getConfig(): ResConfig = mConfig

    override fun toString(): String =
        String.format("ResType{spec=%s, config=%s, flag=%s}", mSpec, mConfig, flag)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ResType
        return mSpec == that.mSpec && mConfig == that.mConfig && flag == that.flag
    }

    override fun hashCode(): Int {
        var result = mSpec.hashCode()
        result = 31 * result + mConfig.hashCode()
        result = 31 * result + (flag?.hashCode() ?: 0)
        return result
    }
}
