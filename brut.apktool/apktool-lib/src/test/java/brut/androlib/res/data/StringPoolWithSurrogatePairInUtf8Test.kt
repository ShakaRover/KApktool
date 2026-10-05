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

import brut.androlib.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * UTF-8 字符串池解码测试，重点覆盖 Android 资源中用三字节（CESU-8 风格）
 * 编码的代理对（surrogate pair）场景。
 *
 * 验证单/双/三字节序列以及非法范围三字节代理序列都能解码为正确的 Java 字符串。
 */
class StringPoolWithSurrogatePairInUtf8Test : BaseTest() {

    /** 验证单字节 ASCII 序列解码正确。 */
    @Test
    fun decodeSingleOctet() {
        val bytes = "abcDEF123".toByteArray(StandardCharsets.UTF_8)
        val actual = ResStringPool(bytes, true).decodeString(0, 9)
        assertEquals("Incorrect decoding", "abcDEF123", actual)
    }

    /** 验证两字节 UTF-8 序列解码到 U+0080 与 U+07FF 边界。 */
    @Test
    fun decodeTwoOctets() {
        val bytes0 = byteArrayOf(0xC2.toByte(), 0x80.toByte())
        val actual0 = ResStringPool(bytes0, true).decodeString(0, 2)
        assertEquals("Incorrect decoding", "\u0080", actual0)

        val bytes1 = byteArrayOf(0xDF.toByte(), 0xBF.toByte())
        val actual1 = ResStringPool(bytes1, true).decodeString(0, 2)
        assertEquals("Incorrect decoding", "\u07FF", actual1)
    }

    /** 验证三字节 UTF-8 序列解码到 U+0800 与 U+FFFF 边界。 */
    @Test
    fun decodeThreeOctets() {
        val bytes0 = byteArrayOf(0xE0.toByte(), 0xA0.toByte(), 0x80.toByte())
        val actual0 = ResStringPool(bytes0, true).decodeString(0, 3)
        assertEquals("Incorrect decoding", "\u0800", actual0)

        val bytes1 = byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBF.toByte())
        val actual1 = ResStringPool(bytes1, true).decodeString(0, 3)
        assertEquals("Incorrect decoding", "\uFFFF", actual1)
    }

    /** 验证以三字节形式出现的非法范围代理序列也能正确还原为表情符号。 */
    @Test
    fun decodeSurrogatePair_when_givesAsThreeOctetsFromInvalidRangeOfUtf8() {
        // See: https://github.com/iBotPeaches/Apktool/issues/2299
        val bytes0 = byteArrayOf(
            0xED.toByte(), 0xA0.toByte(), 0xBD.toByte(),
            0xED.toByte(), 0xB4.toByte(), 0x86.toByte()
        )
        val actual0 = ResStringPool(bytes0, true).decodeString(0, 6)
        assertEquals("Incorrect decoding", "\uD83D\uDD06", actual0)

        // See: https://github.com/iBotPeaches/Apktool/issues/2546
        // 代理对之前还存在普通字符的字节序列
        val bytes1 = byteArrayOf(
            'G'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'd'.code.toByte(),
            ' '.code.toByte(), 'm'.code.toByte(), 'o'.code.toByte(), 'r'.code.toByte(),
            'n'.code.toByte(), 'i'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
            '!'.code.toByte(), ' '.code.toByte(),
            0xED.toByte(), 0xA0.toByte(), 0xBD.toByte(),
            0xED.toByte(), 0xB1.toByte(), 0x8B.toByte(),
            ' '.code.toByte(), 'S'.code.toByte(), 'u'.code.toByte(),
            'n'.code.toByte(), ' '.code.toByte(),
            0xED.toByte(), 0xA0.toByte(), 0xBC.toByte(),
            0xED.toByte(), 0xBC.toByte(), 0x9E.toByte()
        )
        val actual1 = ResStringPool(bytes1, true).decodeString(0, 31)
        // D83D -> 0xED 0xA0 0xBD
        // DC4B -> 0xED 0xB1 0x8B
        // D83C -> 0xED 0xA0 0xBC
        // DF1E -> 0xED 0xBC 0x9E
        assertEquals(
            "Incorrect decoding when there are valid characters before the surrogate pair",
            "Good morning! \uD83D\uDC4B Sun \uD83C\uDF1E", actual1
        )
    }

    /** 验证代理对以三字节形式出现在合法范围（U+10FFFF）时同样解码正确。 */
    @Test
    fun decodeSurrogatePair_when_givesAsThreeOctetsFromTheValidRangeOfUtf8() {
        // U+10FFFF 在标准 UTF-8 中需 4 字节编码（0xDBFF 0xDFFF 两个代理码元），
        // 但 Android 资源里的 UTF-8 字符串改用 3 字节编码，
        // 因此每个代理码元各自被编码为 3 字节
        val bytes = byteArrayOf(
            0xED.toByte(), 0xAF.toByte(), 0xBF.toByte(),
            0xED.toByte(), 0xBF.toByte(), 0xBF.toByte()
        )
        val actual = ResStringPool(bytes, true).decodeString(0, 6)
        assertEquals("Incorrect decoding", "\uDBFF\uDFFF", actual)
    }
}
