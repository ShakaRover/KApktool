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

import java.util.Arrays
import java.util.Locale

/**
 * 资源配置（限定词集合）：解析资源表后生成的目录限定词串（如 -en-rUS-xxhdpi-v26）。
 *
 * 各字段布局与 Android ResTable_config 结构体一一对应；
 * [computeQualifiers] 在构造时即生成限定词串与"非法"标记（未知枚举值/未知数据段），
 * 相等性只看限定词串。
 */
class ResConfig(
    /** 移动国家码。 */
    val mcc: Int,
    /** 移动网络码。 */
    val mnc: Int,
    /** 语言（ISO-639）。 */
    val language: String,
    /** 地区（ISO-3166）。 */
    val region: String,
    /** 屏幕方向。 */
    val orientation: Int,
    /** 触摸屏类型。 */
    val touchscreen: Int,
    /** 像素密度。 */
    val density: Int,
    /** 键盘类型。 */
    val keyboard: Int,
    /** 导航类型。 */
    val navigation: Int,
    /** 键盘/导航可见性标志位。 */
    val inputFlags: Int,
    /** 语法性别标志位。 */
    val grammaticalInflection: Int,
    /** 屏幕宽（像素）。 */
    val screenWidth: Int,
    /** 屏幕高（像素）。 */
    val screenHeight: Int,
    /** SDK 版本限定。 */
    val sdkVersion: Int,
    /** 小版本号。 */
    val minorVersion: Int,
    /** 屏幕布局标志位。 */
    val screenLayout: Int,
    /** UI 模式标志位。 */
    val uiMode: Int,
    /** 最小屏幕宽（dp）。 */
    val smallestScreenWidthDp: Int,
    /** 屏幕宽（dp）。 */
    val screenWidthDp: Int,
    /** 屏幕高（dp）。 */
    val screenHeightDp: Int,
    /** BCP-47 文字子标签。 */
    val localeScript: String,
    /** BCP-47 变体子标签。 */
    val localeVariant: String,
    /** 屏幕布局 2 标志位（圆形屏等）。 */
    val screenLayout2: Int,
    /** 色彩模式标志位（宽色域/HDR）。 */
    val colorMode: Int,
    /** 结构体尾部未知字节（存在即视为非法）。 */
    val unknown: ByteArray?,
) {
    private val mQualifiers: String

    /** 是否含无法识别的限定词值。 */
    val isInvalid: Boolean

    init {
        val invalid = BooleanArray(1)
        mQualifiers = computeQualifiers(invalid)
        isInvalid = invalid[0]
    }

    /** 全默认（空）配置。 */
    private constructor() : this(
        0, 0, "", "", ORIENTATION_ANY, TOUCHSCREEN_ANY, DENSITY_DEFAULT, KEYBOARD_ANY, NAVIGATION_ANY,
        KEYSHIDDEN_ANY or NAVHIDDEN_ANY, GRAMMATICAL_GENDER_ANY, 0, 0, 0, 0,
        SCREENSIZE_ANY or SCREENLONG_ANY, UI_MODE_TYPE_ANY or UI_MODE_NIGHT_ANY,
        0, 0, 0, "", "", 0, COLOR_MODE_WIDECG_ANY or COLOR_MODE_HDR_ANY, null,
    )

    /** 目录限定词串（以 '-' 开头，可能为空串）。 */
    fun toQualifiers(): String = mQualifiers

    override fun toString(): String =
        "[" + (if (mQualifiers.isNotEmpty()) mQualifiers.substring(1) else "DEFAULT") + "]"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        return mQualifiers == (other as ResConfig).mQualifiers
    }

    override fun hashCode(): Int = mQualifiers.hashCode()

    private fun computeQualifiers(isInvalid: BooleanArray): String {
        val sb = StringBuilder()
        if (mcc != 0) {
            sb.append("-mcc").append(String.format(Locale.ROOT, "%03d", mcc))
        }
        if (mnc != 0) {
            sb.append("-mnc").append(String.format(Locale.ROOT, "%02d", if (mnc == MNC_ZERO) 0 else mnc))
        }
        if (language.isNotEmpty()) {
            if (localeScript.isEmpty() && (region.isEmpty() || region.length == 2) && localeVariant.isEmpty()) {
                // 传统格式：-lang-rREGION
                sb.append('-').append(language)
                if (region.isNotEmpty()) {
                    sb.append("-r").append(region)
                }
            } else {
                // 改进的 BCP-47 格式：-b+lang+script+region+variant
                sb.append("-b+")
                sb.append(language)
                if (localeScript.isNotEmpty()) {
                    sb.append('+').append(localeScript)
                }
                if (region.isNotEmpty()) {
                    sb.append('+').append(region)
                }
                if (localeVariant.isNotEmpty()) {
                    sb.append('+').append(localeVariant)
                }
            }
        }
        when (grammaticalInflection and MASK_GRAMMATICAL_GENDER) {
            GRAMMATICAL_GENDER_ANY -> {}
            GRAMMATICAL_GENDER_NEUTER -> sb.append("-neuter")
            GRAMMATICAL_GENDER_FEMININE -> sb.append("-feminine")
            GRAMMATICAL_GENDER_MASCULINE -> sb.append("-masculine")
            else -> {
                sb.append("-grammaticalGender=").append(grammaticalInflection and MASK_GRAMMATICAL_GENDER)
                isInvalid[0] = true
            }
        }
        when (screenLayout and MASK_LAYOUTDIR) {
            LAYOUTDIR_ANY -> {}
            LAYOUTDIR_LTR -> sb.append("-ldltr")
            LAYOUTDIR_RTL -> sb.append("-ldrtl")
            else -> {
                sb.append("-layoutDir=").append(screenLayout and MASK_LAYOUTDIR)
                isInvalid[0] = true
            }
        }
        if (smallestScreenWidthDp != 0) {
            sb.append("-sw").append(smallestScreenWidthDp).append("dp")
        }
        if (screenWidthDp != 0) {
            sb.append("-w").append(screenWidthDp).append("dp")
        }
        if (screenHeightDp != 0) {
            sb.append("-h").append(screenHeightDp).append("dp")
        }
        when (screenLayout and MASK_SCREENSIZE) {
            SCREENSIZE_ANY -> {}
            SCREENSIZE_SMALL -> sb.append("-small")
            SCREENSIZE_NORMAL -> sb.append("-normal")
            SCREENSIZE_LARGE -> sb.append("-large")
            SCREENSIZE_XLARGE -> sb.append("-xlarge")
            else -> {
                sb.append("-screenSize=").append(screenLayout and MASK_SCREENSIZE)
                isInvalid[0] = true
            }
        }
        when (screenLayout and MASK_SCREENLONG) {
            SCREENLONG_ANY -> {}
            SCREENLONG_NO -> sb.append("-notlong")
            SCREENLONG_YES -> sb.append("-long")
            else -> {
                sb.append("-screenLong=").append(screenLayout and MASK_SCREENLONG)
                isInvalid[0] = true
            }
        }
        when (screenLayout2 and MASK_SCREENROUND) {
            SCREENROUND_ANY -> {}
            SCREENROUND_NO -> sb.append("-notround")
            SCREENROUND_YES -> sb.append("-round")
            else -> {
                sb.append("-screenRound=").append(screenLayout2 and MASK_SCREENROUND)
                isInvalid[0] = true
            }
        }
        when (colorMode and MASK_COLOR_MODE_WIDECG) {
            COLOR_MODE_WIDECG_ANY -> {}
            COLOR_MODE_WIDECG_NO -> sb.append("-nowidecg")
            COLOR_MODE_WIDECG_YES -> sb.append("-widecg")
            else -> {
                sb.append("-colorModeWideCG=").append(colorMode and MASK_COLOR_MODE_WIDECG)
                isInvalid[0] = true
            }
        }
        when (colorMode and MASK_COLOR_MODE_HDR) {
            COLOR_MODE_HDR_ANY -> {}
            COLOR_MODE_HDR_NO -> sb.append("-lowdr")
            COLOR_MODE_HDR_YES -> sb.append("-highdr")
            else -> {
                sb.append("-colorModeHdr=").append(colorMode and MASK_COLOR_MODE_HDR)
                isInvalid[0] = true
            }
        }
        when (orientation) {
            ORIENTATION_ANY -> {}
            ORIENTATION_PORT -> sb.append("-port")
            ORIENTATION_LAND -> sb.append("-land")
            ORIENTATION_SQUARE -> sb.append("-square")
            else -> {
                sb.append("-orientation=").append(orientation)
                isInvalid[0] = true
            }
        }
        when (uiMode and MASK_UI_MODE_TYPE) {
            UI_MODE_TYPE_ANY, UI_MODE_TYPE_NORMAL -> {}
            UI_MODE_TYPE_DESK -> sb.append("-desk")
            UI_MODE_TYPE_CAR -> sb.append("-car")
            UI_MODE_TYPE_TELEVISION -> sb.append("-television")
            UI_MODE_TYPE_APPLIANCE -> sb.append("-appliance")
            UI_MODE_TYPE_WATCH -> sb.append("-watch")
            UI_MODE_TYPE_VR_HEADSET -> sb.append("-vrheadset")
            UI_MODE_TYPE_GODZILLAUI -> sb.append("-godzillaui")
            UI_MODE_TYPE_SMALLUI -> sb.append("-smallui")
            UI_MODE_TYPE_MEDIUMUI -> sb.append("-mediumui")
            UI_MODE_TYPE_LARGEUI -> sb.append("-largeui")
            UI_MODE_TYPE_HUGEUI -> sb.append("-hugeui")
            else -> {
                sb.append("-uiModeType=").append(uiMode and MASK_UI_MODE_TYPE)
                isInvalid[0] = true
            }
        }
        when (uiMode and MASK_UI_MODE_NIGHT) {
            UI_MODE_NIGHT_ANY -> {}
            UI_MODE_NIGHT_NO -> sb.append("-notnight")
            UI_MODE_NIGHT_YES -> sb.append("-night")
            else -> {
                sb.append("-uiModeNight=").append(uiMode and MASK_UI_MODE_NIGHT)
                isInvalid[0] = true
            }
        }
        when (density) {
            DENSITY_DEFAULT -> {}
            DENSITY_LOW -> sb.append("-ldpi")
            DENSITY_MEDIUM -> sb.append("-mdpi")
            DENSITY_TV -> sb.append("-tvdpi")
            DENSITY_HIGH -> sb.append("-hdpi")
            DENSITY_XHIGH -> sb.append("-xhdpi")
            DENSITY_XXHIGH -> sb.append("-xxhdpi")
            DENSITY_XXXHIGH -> sb.append("-xxxhdpi")
            DENSITY_ANY -> sb.append("-anydpi")
            DENSITY_NONE -> sb.append("-nodpi")
            else -> sb.append('-').append(density).append("dpi")
        }
        when (touchscreen) {
            TOUCHSCREEN_ANY -> {}
            TOUCHSCREEN_NOTOUCH -> sb.append("-notouch")
            TOUCHSCREEN_STYLUS -> sb.append("-stylus")
            TOUCHSCREEN_FINGER -> sb.append("-finger")
            else -> {
                sb.append("-touchscreen=").append(touchscreen)
                isInvalid[0] = true
            }
        }
        when (inputFlags and MASK_KEYSHIDDEN) {
            KEYSHIDDEN_ANY -> {}
            KEYSHIDDEN_NO -> sb.append("-keysexposed")
            KEYSHIDDEN_YES -> sb.append("-keyshidden")
            KEYSHIDDEN_SOFT -> sb.append("-keyssoft")
            else -> {
                sb.append("-keysHidden=").append(inputFlags and MASK_KEYSHIDDEN)
                isInvalid[0] = true
            }
        }
        when (keyboard) {
            KEYBOARD_ANY -> {}
            KEYBOARD_NOKEYS -> sb.append("-nokeys")
            KEYBOARD_QWERTY -> sb.append("-qwerty")
            KEYBOARD_12KEY -> sb.append("-12key")
            else -> {
                sb.append("-keyboard=").append(keyboard)
                isInvalid[0] = true
            }
        }
        when (inputFlags and MASK_NAVHIDDEN) {
            NAVHIDDEN_ANY -> {}
            NAVHIDDEN_NO -> sb.append("-navexposed")
            NAVHIDDEN_YES -> sb.append("-navhidden")
            else -> {
                sb.append("-navHidden=").append(inputFlags and MASK_NAVHIDDEN)
                isInvalid[0] = true
            }
        }
        when (navigation) {
            NAVIGATION_ANY -> {}
            NAVIGATION_NONAV -> sb.append("-nonav")
            NAVIGATION_DPAD -> sb.append("-dpad")
            NAVIGATION_TRACKBALL -> sb.append("-trackball")
            NAVIGATION_WHEEL -> sb.append("-wheel")
            else -> {
                sb.append("-navigation=").append(navigation)
                isInvalid[0] = true
            }
        }
        if (screenWidth != 0 && screenHeight != 0) {
            sb.append('-').append(screenWidth).append('x').append(screenHeight)
        }
        if (sdkVersion != 0) {
            sb.append("-v").append(sdkVersion)
            if (minorVersion != 0) {
                sb.append('.').append(minorVersion)
            }
        }
        if (unknown != null) {
            // 未知数据段需生成独立后缀，避免限定词冲突。
            sb.append("-unk").append(String.format("%08X", Arrays.hashCode(unknown)))
            isInvalid[0] = true
        }
        return sb.toString()
    }

    companion object {
        const val MNC_ZERO = 0xFFFF

        const val ORIENTATION_ANY = 0x00
        const val ORIENTATION_PORT = 0x01
        const val ORIENTATION_LAND = 0x02
        const val ORIENTATION_SQUARE = 0x03

        const val TOUCHSCREEN_ANY = 0x00
        const val TOUCHSCREEN_NOTOUCH = 0x01
        const val TOUCHSCREEN_STYLUS = 0x02
        const val TOUCHSCREEN_FINGER = 0x03

        const val DENSITY_DEFAULT = 0
        const val DENSITY_LOW = 120
        const val DENSITY_MEDIUM = 160
        const val DENSITY_TV = 213
        const val DENSITY_HIGH = 240
        const val DENSITY_XHIGH = 320
        const val DENSITY_XXHIGH = 480
        const val DENSITY_XXXHIGH = 640
        const val DENSITY_ANY: Int = 0xFFFE
        const val DENSITY_NONE: Int = 0xFFFF

        const val KEYBOARD_ANY = 0x00
        const val KEYBOARD_NOKEYS = 0x01
        const val KEYBOARD_QWERTY = 0x02
        const val KEYBOARD_12KEY = 0x03

        const val NAVIGATION_ANY = 0x00
        const val NAVIGATION_NONAV = 0x01
        const val NAVIGATION_DPAD = 0x02
        const val NAVIGATION_TRACKBALL = 0x03
        const val NAVIGATION_WHEEL = 0x04

        const val MASK_KEYSHIDDEN = 0x03
        const val KEYSHIDDEN_ANY = 0x00
        const val KEYSHIDDEN_NO = 0x01
        const val KEYSHIDDEN_YES = 0x02
        const val KEYSHIDDEN_SOFT = 0x03

        const val SHIFT_NAVHIDDEN = 2
        const val MASK_NAVHIDDEN = 0x03 shl SHIFT_NAVHIDDEN // 0x0C
        const val NAVHIDDEN_ANY = 0x00 shl SHIFT_NAVHIDDEN // 0x00
        const val NAVHIDDEN_NO = 0x01 shl SHIFT_NAVHIDDEN // 0x04
        const val NAVHIDDEN_YES = 0x02 shl SHIFT_NAVHIDDEN // 0x08

        const val MASK_GRAMMATICAL_GENDER = 0x03
        const val GRAMMATICAL_GENDER_ANY = 0x00
        const val GRAMMATICAL_GENDER_NEUTER = 0x01
        const val GRAMMATICAL_GENDER_FEMININE = 0x02
        const val GRAMMATICAL_GENDER_MASCULINE = 0x03

        const val MASK_SCREENSIZE = 0x0F
        const val SCREENSIZE_ANY = 0x00
        const val SCREENSIZE_SMALL = 0x01
        const val SCREENSIZE_NORMAL = 0x02
        const val SCREENSIZE_LARGE = 0x03
        const val SCREENSIZE_XLARGE = 0x04

        const val SHIFT_SCREENLONG = 4
        const val MASK_SCREENLONG = 0x03 shl SHIFT_SCREENLONG // 0x30
        const val SCREENLONG_ANY = 0x00 shl SHIFT_SCREENLONG // 0x00
        const val SCREENLONG_NO = 0x01 shl SHIFT_SCREENLONG // 0x10
        const val SCREENLONG_YES = 0x02 shl SHIFT_SCREENLONG // 0x20

        const val SHIFT_LAYOUTDIR = 6
        const val MASK_LAYOUTDIR = 0x03 shl SHIFT_LAYOUTDIR // 0xC0
        const val LAYOUTDIR_ANY = 0x00 shl SHIFT_LAYOUTDIR // 0x00
        const val LAYOUTDIR_LTR = 0x01 shl SHIFT_LAYOUTDIR // 0x40
        const val LAYOUTDIR_RTL = 0x02 shl SHIFT_LAYOUTDIR // 0x80

        const val MASK_UI_MODE_TYPE = 0x0F
        const val UI_MODE_TYPE_ANY = 0x00
        const val UI_MODE_TYPE_NORMAL = 0x01
        const val UI_MODE_TYPE_DESK = 0x02
        const val UI_MODE_TYPE_CAR = 0x03
        const val UI_MODE_TYPE_TELEVISION = 0x04
        const val UI_MODE_TYPE_APPLIANCE = 0x05
        const val UI_MODE_TYPE_WATCH = 0x06
        const val UI_MODE_TYPE_VR_HEADSET = 0x07
        const val UI_MODE_TYPE_GODZILLAUI = 0x0B // MIUI
        const val UI_MODE_TYPE_SMALLUI = 0x0C // MIUI
        const val UI_MODE_TYPE_MEDIUMUI = 0x0D // MIUI
        const val UI_MODE_TYPE_LARGEUI = 0x0E // MIUI
        const val UI_MODE_TYPE_HUGEUI = 0x0F // MIUI

        const val SHIFT_UI_MODE_NIGHT = 4
        const val MASK_UI_MODE_NIGHT = 0x03 shl SHIFT_UI_MODE_NIGHT // 0x30
        const val UI_MODE_NIGHT_ANY = 0x00 shl SHIFT_UI_MODE_NIGHT // 0x00
        const val UI_MODE_NIGHT_NO = 0x01 shl SHIFT_UI_MODE_NIGHT // 0x10
        const val UI_MODE_NIGHT_YES = 0x02 shl SHIFT_UI_MODE_NIGHT // 0x20

        const val MASK_SCREENROUND = 0x03
        const val SCREENROUND_ANY = 0x00
        const val SCREENROUND_NO = 0x01
        const val SCREENROUND_YES = 0x02

        const val MASK_COLOR_MODE_WIDECG = 0x03
        const val COLOR_MODE_WIDECG_ANY = 0x00
        const val COLOR_MODE_WIDECG_NO = 0x01
        const val COLOR_MODE_WIDECG_YES = 0x02

        const val SHIFT_COLOR_MODE_HDR = 2
        const val MASK_COLOR_MODE_HDR = 0x03 shl SHIFT_COLOR_MODE_HDR // 0x0C
        const val COLOR_MODE_HDR_ANY = 0x00 shl SHIFT_COLOR_MODE_HDR // 0x00
        const val COLOR_MODE_HDR_NO = 0x01 shl SHIFT_COLOR_MODE_HDR // 0x04
        const val COLOR_MODE_HDR_YES = 0x02 shl SHIFT_COLOR_MODE_HDR // 0x08

        /** 默认（空）配置单例。 */
        @JvmField
        val DEFAULT = ResConfig()
    }
}
