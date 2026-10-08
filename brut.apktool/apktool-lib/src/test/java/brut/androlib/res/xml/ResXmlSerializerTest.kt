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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * 畸形属性名（XML 注入）回归测试。
 *
 * 加固 APK 会把某个属性的“名字”做成注入 XML 标记的字符串，例如
 * `" >\n  </manifest>\n  android:name"`（issue #3847）。若照常写出，就会提前
 * 闭合开始标签与元素，产出的 XML 结构被破坏，aapt2 回编时报 unbound prefix。
 */
class ResXmlSerializerTest {

    /** 含 XML 标记/空白的属性名被丢弃，合法属性名照常输出。 */
    @Test
    @Throws(Exception::class)
    fun dropsAttributeWithInvalidXmlName() {
        val out = ByteArrayOutputStream()
        val serial = ResXmlSerializer(true)
        serial.setOutput(out, null)
        serial.startDocument("UTF-8", null)
        serial.startTag(null, "manifest")
        serial.attribute(null, "versionCode", "172")
        serial.attribute(null, " >\n  </manifest>\n  android:name", "false")
        serial.endTag(null, "manifest")
        serial.endDocument()

        val xml = out.toString("UTF-8")
        assertTrue(xml.contains("versionCode=\"172\""))
        assertFalse("injected markup must not reach the output", xml.contains("</manifest>\""))
        // 只有一个 </manifest>：注入属性没有提前闭合元素。
        assertTrue(xml.indexOf("</manifest>") == xml.lastIndexOf("</manifest>"))
    }

    /** XML Name 校验：合法名通过，含标记/空白/数字开头的名字被拒。 */
    @Test
    fun validatesXmlNames() {
        assertTrue(ResXmlSerializer.isValidXmlName("manifest"))
        assertTrue(ResXmlSerializer.isValidXmlName("versionCode"))
        assertTrue(ResXmlSerializer.isValidXmlName("_x-1.a"))
        assertTrue(ResXmlSerializer.isValidXmlName("名字"))

        assertFalse(ResXmlSerializer.isValidXmlName(""))
        assertFalse(ResXmlSerializer.isValidXmlName("1abc"))
        assertFalse(ResXmlSerializer.isValidXmlName(" >\n  </manifest>\n  android:name"))
        assertFalse(ResXmlSerializer.isValidXmlName("a b"))
        assertFalse(ResXmlSerializer.isValidXmlName("a<b"))
    }
}
