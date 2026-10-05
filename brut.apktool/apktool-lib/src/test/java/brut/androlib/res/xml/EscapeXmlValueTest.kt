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
 * 资源文本值转义测试。
 *
 * 验证 ResStringEncoder 在生成 values XML 时正确处理引号、HTML 实体、
 * CDATA 结构以及 NUL 字符等特殊内容。
 */
class EscapeXmlValueTest : BaseTest() {

    /** 断言各类需要转义的字符串输出符合预期。 */
    @Test
    fun escapeXmlValueTest() {
        assertEquals("foo", escape("foo"))
        assertEquals("\"'foo'\"", escape("'foo'"))
        assertEquals("\\\"foo\\\"", escape("\"foo\""))
        assertEquals("foo&amp;bar", escape("foo&bar"))
        assertEquals("&lt;foo>", escape("<foo>"))
        assertEquals("&lt;![CDATA[foo]]&gt;", escape("<![CDATA[foo]]>"))
        assertEquals("", escape("\u0000"))
    }

    companion object {
        /** 调用资源编码器把文本值转为 values XML 中的安全写法。 */
        private fun escape(value: String): String = ResStringEncoder.encodeTextValue(value)
    }
}
