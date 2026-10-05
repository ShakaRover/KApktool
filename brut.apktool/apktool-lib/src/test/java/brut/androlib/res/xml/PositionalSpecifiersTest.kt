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
package brut.androlib.res.xml

import brut.androlib.BaseTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 格式化占位符规范化测试。
 *
 * 验证 ResStringEncoder 会把字符串资源中无序的 %s / %d 占位符
 * 自动补全为带位置索引的 %1\$s、%2\$d 形式，而已带位置索引的保持不变。
 */
class PositionalSpecifiersTest : BaseTest() {

    /** 无占位符的普通文本保持原样。 */
    @Test
    fun noSpecifiersTest() {
        assertEquals("test", normalize("test"))
    }

    /** 两个 %s 占位符被补全为位置索引形式。 */
    @Test
    fun twoSpecifiersTest() {
        assertEquals("%1\$s, %2\$s, and 1 other.", normalize("%s, %s, and 1 other."))
    }

    /** 已是位置索引形式的两个占位符保持不变。 */
    @Test
    fun twoPositionalSpecifiersTest() {
        assertEquals("%1\$s, %2\$s and 1 other", normalize("%1\$s, %2\$s and 1 other"))
    }

    /** 混合 %s 与 %d 共三个占位符被依次编号。 */
    @Test
    fun threeSpecifiersTest() {
        assertEquals("%1\$s, %2\$s, and %3\$d other.", normalize("%s, %s, and %d other."))
    }

    /** 带前导空格且已编号的三个占位符保持不变。 */
    @Test
    fun threePositionalSpecifiersTest() {
        assertEquals(" %1\$s, %2\$s and %3\$d other", normalize(" %1\$s, %2\$s and %3\$d other"))
    }

    /** 四个占位符（%s 与 %d 混合）被依次编号。 */
    @Test
    fun fourSpecifiersTest() {
        assertEquals("%1\$s, %2\$s, and %3\$d other and %4\$d.", normalize("%s, %s, and %d other and %d."))
    }

    /** 带前导空格且已编号的四个占位符保持不变。 */
    @Test
    fun fourPositionalSpecifiersTest() {
        assertEquals(" %1\$s, %2\$s and %3\$d other and %4\$d.", normalize(" %1\$s, %2\$s and %3\$d other and %4\$d."))
    }

    /** 调用资源编码器规范化格式化占位符。 */
    private fun normalize(value: String): String = ResStringEncoder.normalizeFormatSpecifiers(value)
}
