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
package brut.androlib.res.data

/**
 * requiredFeature / supportedFeature 条目：特性名 + 是否取反（前缀 !）。
 */
class FeatureFlag(
    @JvmField val mName: String,
    @JvmField val mNegated: Boolean,
) {
    companion object {
        /** 生成特性的字符串形式（取反加 "!" 前缀）。 */
        @JvmStatic
        fun toString(name: String, negated: Boolean): String = if (negated) "!$name" else name
    }

    override fun toString(): String = toString(mName, mNegated)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as FeatureFlag
        return mName == that.mName && mNegated == that.mNegated
    }

    override fun hashCode(): Int = 31 * mName.hashCode() + mNegated.hashCode()
}
