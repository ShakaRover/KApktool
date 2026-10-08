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
package brut.util

/**
 * 面向资源文件的高速文本/数值解析工具。
 *
 * [parseInt] / [parseFloat] / [parseColor] 复刻 AAPT 的解析语义：
 * 十进制、十六进制（0x）、科学计数法（e）与十六进制浮点（p）均手写实现，
 * 借助 10^N 查表（有效数 + 二进制移位）把十进制浮点转换为 IEEE-754 单精度位模式，
 * 避免 java.text 的开销与 locale 差异。
 */
object TextUtils {
    private const val SINGLE_PRECISION_BIAS = 127
    private const val SINGLE_PRECISION_EXP_MIN = -126
    private const val SINGLE_PRECISION_EXP_MAX = 127
    private const val FLOAT_NEGATIVE_MASK = 0x80000000.toInt()
    private const val MANTISSA_EXPONENT_ADJUST_VALUE = 0xFFFFFF
    private const val DENORMAL_EXPONENT_ADJUST_VALUE = MANTISSA_EXPONENT_ADJUST_VALUE - 1
    private const val SCALAR_SHIFT_1 = 1L shl 60
    private const val SCALAR_SHIFT_2 = 1L shl 61
    private const val SCALAR_SHIFT_3 = 1L shl 62
    private const val SCALAR_SHIFT_4 = 1L shl 63
    private const val DEC_SIGNIFICAND_LEADING_BIT = 1L shl 59
    private const val DEC_SIGNIFICAND_DOWN_SHIFT = 36
    private const val ROUND_VALUE = 1L shl 35

    /** 十六进制编码字符表。 */
    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    /** 10^N（N>=0）的 60 位归一化有效数表。 */
    private val POSITIVE_SIGNIFICANDS = longArrayOf(
        0x0800000000000000L, 0x0A00000000000000L, 0x0C80000000000000L, 0x0FA0000000000000L, 0x09C4000000000000L,
        0x0C35000000000000L, 0x0F42400000000000L, 0x0989680000000000L, 0x0BEBC20000000000L, 0x0EE6B28000000000L,
        0x09502F9000000000L, 0x0BA43B7400000000L, 0x0E8D4A5100000000L, 0x09184E72A0000000L, 0x0B5E620F48000000L,
        0x0E35FA931A000000L, 0x08E1BC9BF0400000L, 0x0B1A2BC2EC500000L, 0x0DE0B6B3A7640000L, 0x08AC7230489E8000L,
        0x0AD78EBC5AC62000L, 0x0D8D726B7177A800L, 0x0878678326EAC900L, 0x0A968163F0A57B40L, 0x0D3C21BCECCEDA10L,
        0x084595161401484AL, 0x0A56FA5B99019A5CL, 0x0CECB8F27F4200F3L, 0x0813F3978F894098L, 0x0A18F07D736B90BEL,
        0x0C9F2C9CD04674EDL, 0x0FC6F7C404581229L, 0x09DC5ADA82B70B59L, 0x0C5371912364CE30L, 0x0F684DF56C3E01BCL,
        0x09A130B963A6C115L, 0x0C097CE7BC90715BL, 0x0F0BDC21ABB48DB2L, 0x096769950B50D88FL,
    )
    /** 10^N（N>=0）有效数对应的二进制指数（2^shift 归一）。 */
    private val POSITIVE_SHIFTS = intArrayOf(
        0, 3, 6, 9, 13, 16, 19, 23, 26, 29, 33, 36, 39, 43, 46, 49, 53, 56, 59, 63, 66, 69, 73, 76, 79, 83, 86, 89,
        93, 96, 99, 102, 106, 109, 112, 116, 119, 122, 126,
    )
    /** 10^-N（N>0）的 60 位归一化有效数表。 */
    private val NEGATIVE_SIGNIFICANDS = longArrayOf(
        0x0CCCCCCCCCCCCCCCL, 0x0A3D70A3D70A3D70L, 0x083126E978D4FDF3L, 0x0D1B71758E219652L, 0x0A7C5AC471B47842L,
        0x08637BD05AF6C69BL, 0x0D6BF94D5E57A42BL, 0x0ABCC77118461CEFL, 0x089705F4136B4A59L, 0x0DBE6FECEBDEDD5BL,
        0x0AFEBFF0BCB24AAFL, 0x08CBCCC096F5088CL, 0x0E12E13424BB40E1L, 0x0B424DC35095CD80L, 0x0901D7CF73AB0ACDL,
        0x0E69594BEC44DE15L, 0x0B877AA3236A4B44L, 0x09392EE8E921D5D0L, 0x0EC1E4A7DB69561AL, 0x0BCE5086492111AEL,
        0x0971DA05074DA7BEL, 0x0F1C90080BAF72CBL, 0x0C16D9A0095928A2L, 0x09ABE14CD44753B5L, 0x0F79687AED3EEC55L,
        0x0C612062576589DDL, 0x09E74D1B791E07E4L, 0x0FD87B5F28300CA0L, 0x0CAD2F7F5359A3B3L, 0x0A2425FF75E14FC3L,
        0x081CEB32C4B43FCFL, 0x0CFB11EAD453994BL, 0x0A6274BBDD0FADD6L, 0x084EC3C97DA624ABL, 0x0D4AD2DBFC3D0778L,
        0x0AA242499697392DL, 0x0881CEA14545C757L, 0x0D9C7DCED53C7225L, 0x0AE397D8AA96C1B7L, 0x08B61313BBABCE2CL,
        0x0DF01E85F912E37AL, 0x0B267ED1940F1C61L, 0x08EB98A7A9A5B04EL, 0x0E45C10C42A2B3B0L, 0x0B6B00D69BB55C8DL,
        0x09226712162AB070L, 0x0E9D71B689DDE71AL,
    )
    /** 10^-N（N>0）有效数对应的二进制指数。 */
    private val NEGATIVE_SHIFTS = intArrayOf(
        -4, -7, -10, -14, -17, -20, -24, -27, -30, -34, -37, -40, -44, -47, -50, -54, -57, -60, -64, -67, -70, -74,
        -77, -80, -84, -87, -90, -94, -97, -100, -103, -107, -110, -113, -117, -120, -123, -127, -130, -133, -137,
        -140, -143, -147, -150, -153, -157,
    )

    /** 返回 text 末尾匹配到的第一个后缀；都不匹配返回 null。 */
    @JvmStatic
    fun matchSuffix(text: CharSequence, vararg suffixes: String): String? {
        val len = text.length
        if (len == 0 || suffixes.isEmpty()) {
            return null
        }

        for (suffix in suffixes) {
            val suffixLen = suffix.length
            if (suffixLen == 0 || suffixLen > len) {
                continue
            }
            var matched = true
            for (i in 0 until suffixLen) {
                if (text[len - suffixLen + i] != suffix[i]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return suffix
            }
        }
        return null
    }

    /** 统计字符在文本中出现的次数。 */
    @JvmStatic
    fun countMatches(text: CharSequence, ch: Char): Int {
        var count = 0
        for (i in 0 until text.length) {
            if (ch == text[i]) {
                count++
            }
        }
        return count
    }

    /** 是否为可打印字符（排除代理区与 0x7F-0x9F 控制段）。 */
    @JvmStatic
    fun isPrintableChar(ch: Char): Boolean {
        return (ch in '\u0020'..'\u007E') || (ch in '\u00A0'..'\uD7FF') ||
            (ch in '\uE000'..'\uFDCF') || (ch in '\uFDF0'..'\uFFFD')
    }

    /** 把字节数组编码为大写十六进制字符串（等价于 guava 的 BaseEncoding.base16()）。 */
    @JvmStatic
    fun encodeHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX_CHARS[v ushr 4]).append(HEX_CHARS[v and 0x0F])
        }
        return out.toString()
    }

    /** 单个十六进制字符转数值，非法字符返回 -1。 */
    @JvmStatic
    fun parseHex(codePoint: Int): Int {
        if (codePoint >= '0'.code && codePoint <= '9'.code) {
            return codePoint - '0'.code
        }
        if (codePoint >= 'A'.code && codePoint <= 'F'.code) {
            return codePoint - 'A'.code + 10
        }
        if (codePoint >= 'a'.code && codePoint <= 'f'.code) {
            return codePoint - 'a'.code + 10
        }
        return -1
    }

    /** 解析 #RGB / #ARGB / #RRGGBB / #AARRGGBB 颜色为整型 ARGB。 */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun parseColor(text: CharSequence): Int = parseColor(text, 0, text.length)

    /** 解析文本 [start, end) 区间内的颜色字面量为整型 ARGB。 */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun parseColor(text: CharSequence, start: Int, end: Int): Int {
        if (start > end || end > text.length) {
            throw IndexOutOfBoundsException()
        }
        if (start == end) {
            throw NumberFormatException()
        }

        var i = start
        if (text[i] != '#' || ++i == end) {
            throw NumberFormatException()
        }

        var value = 0

        when (end - start) {
            4 -> { // #RGB
                while (i < end) {
                    val hex = parseHex(text[i].code)
                    if (hex == -1) {
                        throw NumberFormatException()
                    }
                    value = (value shl 8) or (hex or (hex shl 4))
                    i++
                }
                value = value or 0xFF000000.toInt()
            }
            5 -> { // #ARGB
                while (i < end) {
                    val hex = parseHex(text[i].code)
                    if (hex == -1) {
                        throw NumberFormatException()
                    }
                    value = (value shl 8) or (hex or (hex shl 4))
                    i++
                }
            }
            7 -> { // #RRGGBB
                while (i < end) {
                    val hex = parseHex(text[i].code)
                    if (hex == -1) {
                        throw NumberFormatException()
                    }
                    value = (value shl 4) or hex
                    i++
                }
                value = value or 0xFF000000.toInt()
            }
            9 -> { // #AARRGGBB
                while (i < end) {
                    val hex = parseHex(text[i].code)
                    if (hex == -1) {
                        throw NumberFormatException()
                    }
                    value = (value shl 4) or hex
                    i++
                }
            }
            else -> throw NumberFormatException()
        }

        return value
    }

    /** 解析整数字面量（支持十进制与 0x 十六进制）。 */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun parseInt(text: CharSequence): Int = parseInt(text, 0, text.length)

    /** 解析文本 [start, end) 区间内的整数字面量。 */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun parseInt(text: CharSequence, start: Int, end: Int): Int {
        if (start > end || end > text.length) {
            throw IndexOutOfBoundsException()
        }
        if (start == end) {
            throw NumberFormatException()
        }

        var i = start
        val negative = text[i] == '-'
        if ((negative || text[i] == '+') && ++i == end) {
            throw NumberFormatException()
        }

        if (i + 1 < end && text[i] == '0' && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
            if (negative) {
                throw NumberFormatException()
            }
            i += 2
            if (i == end) {
                throw NumberFormatException()
            }
            return parseIntHex(text, i, end)
        }

        return parseIntDec(text, i, end, negative)
    }

    /** 十进制整数解析，边累加边做 32 位溢出检查。 */
    private fun parseIntDec(text: CharSequence, start: Int, end: Int, negative: Boolean): Int {
        var value = 0L

        for (i in start until end) {
            val ch = text[i]
            if (ch < '0' || ch > '9') {
                throw NumberFormatException()
            }

            value = value * 10 + (ch - '0')

            if (if (negative) -value < Integer.MIN_VALUE else value > Integer.MAX_VALUE) {
                throw NumberFormatException()
            }
        }

        return (if (negative) -value else value).toInt()
    }

    /** 十六进制整数解析（允许结果按 32 位环绕，同 Integer.decode 语义）。 */
    private fun parseIntHex(text: CharSequence, start: Int, end: Int): Int {
        var value = 0L

        for (i in start until end) {
            val hex = parseHex(text[i].code)
            if (hex == -1) {
                throw NumberFormatException()
            }

            value = (value shl 4) + hex

            if (value > 0xFFFFFFFFL) {
                throw NumberFormatException()
            }
        }

        return value.toInt()
    }

    /** 解析单精度浮点字面量（支持十进制 e 记法与十六进制 p 记法）。 */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun parseFloat(text: CharSequence): Float = parseFloat(text, 0, text.length)

    /** 解析文本 [start, end) 区间内的单精度浮点字面量。 */
    @JvmStatic
    @Throws(NumberFormatException::class)
    fun parseFloat(text: CharSequence, start: Int, end: Int): Float {
        if (start > end || end > text.length) {
            throw IndexOutOfBoundsException()
        }
        if (start == end) {
            throw NumberFormatException()
        }

        var i = start
        val negative = text[i] == '-'
        if ((negative || text[i] == '+') && ++i == end) {
            throw NumberFormatException()
        }

        if (i + 1 < end && text[i] == '0' && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
            i += 2
            if (i == end) {
                throw NumberFormatException()
            }
            return parseFloatHex(text, i, end, negative)
        }

        return parseFloatDec(text, i, end, negative)
    }

    /**
     * 十进制浮点解析（核心算法）。
     *
     * 思路：逐位取出有效数字，每位乘以 10^指数 的查表值（60 位归一化），
     * 累加到统一标度的定点数中；超出单精度可表达范围则中止累加；
     * 最后加舍入常量、取 24 位尾数、叠加偏置指数生成 IEEE-754 位模式。
     */
    private fun parseFloatDec(text: CharSequence, start: Int, end: Int, negative: Boolean): Float {
        var i = start
        var significandBegin = i
        var significandStart = false
        var validSignificand = false
        var subInteger = false
        var mostSignificantExponent = 0

        while (i < end && text[i] != 'e' && text[i] != 'E') {
            if (text[i] == '.') {
                if (subInteger) {
                    throw NumberFormatException()
                }

                subInteger = true
                i++
                continue
            }

            if (significandStart && !subInteger) {
                ++mostSignificantExponent
            } else if (!significandStart && subInteger) {
                --mostSignificantExponent
            }

            if (text[i] < '0' || text[i] > '9') {
                throw NumberFormatException()
            }

            validSignificand = true
            if (text[i] != '0' && !significandStart) {
                significandStart = true
                significandBegin = i
            }

            i++
        }
        if (!validSignificand) {
            throw NumberFormatException()
        }

        val significandEnd = i
        var declaredExponent = 0
        if (i < end) {
            if (i == end - 1) {
                throw NumberFormatException()
            }

            i++
            val negativeExponent = text[i] == '-'
            if (negativeExponent || text[i] == '+') {
                i++
            }

            while (i < end) {
                if (text[i] < '0' || text[i] > '9') {
                    throw NumberFormatException()
                }

                val currentValue = text[i] - '0'
                declaredExponent = declaredExponent * 10 + currentValue
                i++
            }
            if (negativeExponent) {
                declaredExponent = -declaredExponent
            }
        }

        if (!significandStart) {
            return if (negative) -0.0f else 0.0f
        }

        mostSignificantExponent += declaredExponent
        i = significandBegin

        var significandValue = getValueByExponent(mostSignificantExponent)
        if (significandValue == 0L) {
            throw NumberFormatException()
        }

        var baseBinaryShift = getShiftByExponent(mostSignificantExponent)
        significandValue *= text[i] - '0'
        var significandScalarShift = getScalarShiftByValue(significandValue)
        significandValue = significandValue ushr significandScalarShift
        baseBinaryShift += significandScalarShift

        var currentExponent = mostSignificantExponent - 1
        i++
        while (i < significandEnd) {
            if (text[i] < '1' || text[i] > '9') {
                if (text[i] == '0') {
                    --currentExponent
                }

                i++
                continue
            }

            val scalarValue = (text[i] - '0').toLong()
            var currentValue = getValueByExponent(currentExponent) * scalarValue
            var relativeDownShift = baseBinaryShift - getShiftByExponent(currentExponent)
            val scalarShift = getScalarShiftByValue(currentValue)
            currentValue = currentValue ushr scalarShift
            relativeDownShift -= scalarShift
            if (relativeDownShift > 59) {
                break
            }

            significandValue += currentValue ushr relativeDownShift
            if ((significandValue and SCALAR_SHIFT_1) != 0L) {
                significandValue = significandValue ushr 1
                baseBinaryShift += 1
            }

            --currentExponent
            i++
        }

        significandValue += ROUND_VALUE
        if ((significandValue and SCALAR_SHIFT_1) != 0L) {
            significandValue = significandValue ushr 1
            baseBinaryShift += 1
        }

        if (baseBinaryShift < SINGLE_PRECISION_EXP_MIN || baseBinaryShift > SINGLE_PRECISION_EXP_MAX) {
            throw NumberFormatException()
        }

        val mantissa = ((significandValue and DEC_SIGNIFICAND_LEADING_BIT.inv()) ushr DEC_SIGNIFICAND_DOWN_SHIFT).toInt()
        val biasedExp = baseBinaryShift + SINGLE_PRECISION_BIAS
        return Float.fromBits(
            (if (negative) FLOAT_NEGATIVE_MASK else 0) or mantissa or (biasedExp shl 23)
        )
    }

    /**
     * 十六进制浮点解析（0x 前缀已剥离，指数记法 p±N）。
     *
     * 尾数最多保留 24 位有效位；全 F 特殊值（0xFFFFFF）与
     * 最小规格化边界分别处理 denormal 舍入分支。
     */
    private fun parseFloatHex(text: CharSequence, start: Int, end: Int, negative: Boolean): Float {
        var i = start
        var currentMantissa = 0
        var currentSkew = 0
        var mantissaBits = 0
        var mantissaStart = false
        var subInteger = false
        var validMantissa = false

        while (i < end && text[i] != 'p') {
            val indexValue = parseHex(text[i].code)
            if (indexValue == -1) {
                if (text[i] != '.' || subInteger) {
                    throw NumberFormatException()
                }

                subInteger = true
                i++
                continue
            }

            validMantissa = true
            if (!mantissaStart && indexValue != 0) {
                mantissaStart = true
                currentMantissa = indexValue

                if (indexValue >= 8) {
                    currentSkew -= 1
                    mantissaBits = 3
                } else if (indexValue >= 4) {
                    currentSkew -= 2
                    mantissaBits = 2
                } else if (indexValue >= 2) {
                    currentSkew -= 3
                    mantissaBits = 1
                } else {
                    currentSkew -= 4
                    mantissaBits = 0
                }
            } else if (mantissaStart && mantissaBits < 24) {
                currentMantissa = (currentMantissa shl 4) + indexValue
                mantissaBits += 4
            }

            if (mantissaStart && !subInteger) {
                currentSkew += 4
            } else if (!mantissaStart && subInteger) {
                currentSkew -= 4
            }

            i++
        }

        if (!validMantissa) {
            throw NumberFormatException()
        }

        var declaredExponent = 0
        if (i < end) {
            if (i == end - 1) {
                throw NumberFormatException()
            }
            i++

            val negativeExponent = text[i] == '-'
            if (negativeExponent || text[i] == '+') {
                i++
            }

            while (i < end) {
                if (text[i] < '0' || text[i] > '9') {
                    throw NumberFormatException()
                }

                val currentValue = text[i] - '0'
                declaredExponent = declaredExponent * 10 + currentValue
                i++
            }

            if (negativeExponent) {
                declaredExponent = -declaredExponent
            }
        }

        if (24 - mantissaBits < 0) {
            currentMantissa = currentMantissa ushr (mantissaBits - 24)
        } else {
            currentMantissa = currentMantissa shl (24 - mantissaBits)
        }
        currentMantissa = currentMantissa and MANTISSA_EXPONENT_ADJUST_VALUE

        val exponent: Int
        if (currentMantissa == MANTISSA_EXPONENT_ADJUST_VALUE ||
            (currentMantissa == DENORMAL_EXPONENT_ADJUST_VALUE &&
                declaredExponent + currentSkew + 1 == SINGLE_PRECISION_EXP_MIN)
        ) {
            currentMantissa = 0
            exponent = declaredExponent + currentSkew + 1
        } else {
            currentMantissa = (currentMantissa + 1) ushr 1
            exponent = declaredExponent + currentSkew
        }

        if (!mantissaStart) {
            return if (negative) -0.0f else 0.0f
        }

        if (exponent < SINGLE_PRECISION_EXP_MIN || exponent > SINGLE_PRECISION_EXP_MAX) {
            throw NumberFormatException()
        }

        val biasedExp = exponent + SINGLE_PRECISION_BIAS
        return Float.fromBits(
            (if (negative) FLOAT_NEGATIVE_MASK else 0) or currentMantissa or (biasedExp shl 23)
        )
    }

    /** 查 10^exponent 的归一化有效数；超出表范围返回 0。 */
    private fun getValueByExponent(exponent: Int): Long {
        if (exponent < POSITIVE_SIGNIFICANDS.size) {
            if (exponent >= 0) {
                return POSITIVE_SIGNIFICANDS[exponent]
            }
            if (exponent >= -NEGATIVE_SIGNIFICANDS.size) {
                return NEGATIVE_SIGNIFICANDS[-exponent - 1]
            }
        }
        return 0L
    }

    /** 查 10^exponent 有效数对应的二进制移位量；超出表范围返回 0。 */
    private fun getShiftByExponent(exponent: Int): Int {
        if (exponent < POSITIVE_SIGNIFICANDS.size) {
            if (exponent >= 0) {
                return POSITIVE_SHIFTS[exponent]
            }
            if (exponent >= -NEGATIVE_SIGNIFICANDS.size) {
                return NEGATIVE_SHIFTS[-exponent - 1]
            }
        }
        return 0
    }

    /** 60-63 位是否溢出：返回把数值压回 60 位以内所需的右移量。 */
    private fun getScalarShiftByValue(valueToNormalize: Long): Int {
        if ((valueToNormalize and SCALAR_SHIFT_4) != 0L) {
            return 4
        }
        if ((valueToNormalize and SCALAR_SHIFT_3) != 0L) {
            return 3
        }
        if ((valueToNormalize and SCALAR_SHIFT_2) != 0L) {
            return 2
        }
        if ((valueToNormalize and SCALAR_SHIFT_1) != 0L) {
            return 1
        }
        return 0
    }
}
