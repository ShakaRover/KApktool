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
package brut.androlib.meta

import brut.yaml.YamlPullParser
import brut.yaml.YamlSerializable
import brut.yaml.YamlSerializer
import java.io.IOException

/**
 * apktool.yml 的 sdkInfo 节：min/target/max SDK 版本。
 *
 * 版本既可是数字字符串也可是代号（Cupcake...CinnamonBun），
 * [toSdkVersionInt] 完成代号->数字映射；未知代号归为 [SDK_CUR_DEVELOPMENT]。
 */
class SdkInfo : YamlSerializable {
    private var mMinSdkVersion: String? = null
    private var mTargetSdkVersion: String? = null
    private var mMaxSdkVersion: String? = null

    /** 清空全部字段。 */
    fun clear() {
        mMinSdkVersion = null
        mTargetSdkVersion = null
        mMaxSdkVersion = null
    }

    /** 是否没有任何 SDK 信息。 */
    val isEmpty: Boolean
        get() = mMinSdkVersion == null && mTargetSdkVersion == null && mMaxSdkVersion == null

    @Throws(IOException::class)
    override fun onEntry(parser: YamlPullParser) {
        when (parser.getKey()) {
            "minSdkVersion" -> mMinSdkVersion = parser.getString()
            "targetSdkVersion" -> mTargetSdkVersion = parser.getString()
            "maxSdkVersion" -> mMaxSdkVersion = parser.getString()
        }
    }

    @Throws(IOException::class)
    override fun serialize(serial: YamlSerializer) {
        mMinSdkVersion?.let { serial.writeString("minSdkVersion", it) }
        mTargetSdkVersion?.let { serial.writeString("targetSdkVersion", it) }
        mMaxSdkVersion?.let { serial.writeString("maxSdkVersion", it) }
    }

    /** 原始字符串形式的 minSdkVersion。 */
    var minSdkVersion: String?
        get() = mMinSdkVersion
        set(value) {
            mMinSdkVersion = value
        }

    /** minSdkVersion 数字形式。 */
    val minSdkVersionInt: Int
        get() = toSdkVersionInt(mMinSdkVersion)

    /** 原始字符串形式的 targetSdkVersion。 */
    var targetSdkVersion: String?
        get() = mTargetSdkVersion
        set(value) {
            mTargetSdkVersion = value
        }

    /** targetSdkVersion 数字形式。 */
    val targetSdkVersionInt: Int
        get() = toSdkVersionInt(mTargetSdkVersion)

    /** 原始字符串形式的 maxSdkVersion。 */
    var maxSdkVersion: String?
        get() = mMaxSdkVersion
        set(value) {
            mMaxSdkVersion = value
        }

    /** maxSdkVersion 数字形式。 */
    val maxSdkVersionInt: Int
        get() = toSdkVersionInt(mMaxSdkVersion)

    companion object {
        const val SDK_BASE = 1
        const val SDK_BASE_1_1 = 2
        const val SDK_CUPCAKE = 3
        const val SDK_DONUT = 4
        const val SDK_ECLAIR = 5
        const val SDK_ECLAIR_0_1 = 6
        const val SDK_ECLAIR_MR1 = 7
        const val SDK_FROYO = 8
        const val SDK_GINGERBREAD = 9
        const val SDK_GINGERBREAD_MR1 = 10
        const val SDK_HONEYCOMB = 11
        const val SDK_HONEYCOMB_MR1 = 12
        const val SDK_HONEYCOMB_MR2 = 13
        const val SDK_ICE_CREAM_SANDWICH = 14
        const val SDK_ICE_CREAM_SANDWICH_MR1 = 15
        const val SDK_JELLY_BEAN = 16
        const val SDK_JELLY_BEAN_MR1 = 17
        const val SDK_JELLY_BEAN_MR2 = 18
        const val SDK_KITKAT = 19
        const val SDK_KITKAT_WATCH = 20
        const val SDK_LOLLIPOP = 21
        const val SDK_LOLLIPOP_MR1 = 22
        const val SDK_MARSHMALLOW = 23
        const val SDK_NOUGAT = 24
        const val SDK_NOUGAT_MR1 = 25
        const val SDK_O = 26
        const val SDK_O_MR1 = 27
        const val SDK_P = 28
        const val SDK_Q = 29
        const val SDK_R = 30
        const val SDK_S = 31
        const val SDK_S_V2 = 32
        const val SDK_TIRAMISU = 33
        const val SDK_UPSIDE_DOWN_CAKE = 34
        const val SDK_VANILLA_ICE_CREAM = 35
        const val SDK_BAKLAVA = 36
        const val SDK_CINNAMON_BUN = 37
        const val SDK_CUR_DEVELOPMENT = 10000

        /** 把版本号（数字或代号）转为整数；非法数字抛异常。 */
        private fun toSdkVersionInt(sdkVersion: String?): Int {
            if (sdkVersion.isNullOrEmpty()) {
                return 0
            }
            val first = sdkVersion[0]
            if (first < 'A' || first > 'Z') {
                val versionInt: Long = try {
                    sdkVersion.toLong()
                } catch (ignored: NumberFormatException) {
                    throw IllegalArgumentException("Invalid version: $sdkVersion")
                }
                if (versionInt < 0) {
                    throw IllegalArgumentException("Negative version: $sdkVersion")
                }
                if (versionInt > Int.MAX_VALUE) {
                    throw IllegalArgumentException("Version too large: $sdkVersion")
                }
                return versionInt.toInt()
            }
            return when (sdkVersion) {
                "Base" -> SDK_BASE
                "Base11" -> SDK_BASE_1_1
                "Cupcake" -> SDK_CUPCAKE
                "Donut" -> SDK_DONUT
                "Eclair" -> SDK_ECLAIR
                "Eclair01" -> SDK_ECLAIR_0_1
                "EclairMr1" -> SDK_ECLAIR_MR1
                "Froyo" -> SDK_FROYO
                "Gingerbread" -> SDK_GINGERBREAD
                "GingerbreadMr1" -> SDK_GINGERBREAD_MR1
                "Honeycomb" -> SDK_HONEYCOMB
                "HoneycombMr1" -> SDK_HONEYCOMB_MR1
                "HoneycombMr2" -> SDK_HONEYCOMB_MR2
                "IceCreamSandwich" -> SDK_ICE_CREAM_SANDWICH
                "IceCreamSandwichMr1" -> SDK_ICE_CREAM_SANDWICH_MR1
                "JellyBean" -> SDK_JELLY_BEAN
                "JellyBeanMr1" -> SDK_JELLY_BEAN_MR1
                "JellyBeanMr2" -> SDK_JELLY_BEAN_MR2
                "Kitkat" -> SDK_KITKAT
                "KitkatWatch" -> SDK_KITKAT_WATCH
                "Lollipop" -> SDK_LOLLIPOP
                "LollipopMr1" -> SDK_LOLLIPOP_MR1
                "M" -> SDK_MARSHMALLOW
                "N" -> SDK_NOUGAT
                "NMr1" -> SDK_NOUGAT_MR1
                "O" -> SDK_O
                "OMr1" -> SDK_O_MR1
                "P" -> SDK_P
                "Q" -> SDK_Q
                "R" -> SDK_R
                "S" -> SDK_S
                "Sv2" -> SDK_S_V2
                "Tiramisu" -> SDK_TIRAMISU
                "UpsideDownCake" -> SDK_UPSIDE_DOWN_CAKE
                "VanillaIceCream" -> SDK_VANILLA_ICE_CREAM
                "Baklava" -> SDK_BAKLAVA
                "CinnamonBun" -> SDK_CINNAMON_BUN
                else -> SDK_CUR_DEVELOPMENT
            }
        }
    }
}
