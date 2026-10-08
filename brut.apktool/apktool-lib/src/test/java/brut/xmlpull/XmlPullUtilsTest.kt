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
package brut.xmlpull

import brut.androlib.res.xml.ResXmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.Reader

/**
 * [XmlPullUtils.copy] 属性去重回归测试。
 *
 * 部分加固 APK 的二进制 XML 会在同一元素上重复同名属性，aapt2 会以
 * "duplicate attribute" 拒绝回编；开启去重后应只保留最后一个。
 */
class XmlPullUtilsTest {

    /** 关闭去重时，重复属性原样输出。 */
    @Test
    @Throws(Exception::class)
    fun keepsDuplicateAttributesByDefault() {
        val xml = copyToXml(dedupe = false)
        assertEquals(2, occurrences(xml, "android:elevation=\"0.0dp\""))
    }

    /** 开启去重时，同名属性只保留最后一个，其它属性不受影响。 */
    @Test
    @Throws(Exception::class)
    fun dropsDuplicateAttributesWhenEnabled() {
        val xml = copyToXml(dedupe = true)
        assertEquals(1, occurrences(xml, "android:elevation=\"0.0dp\""))
        assertTrue(xml.contains("android:id=\"@attr/x\""))
    }

    private fun copyToXml(dedupe: Boolean): String {
        val out = ByteArrayOutputStream()
        val serial = ResXmlSerializer(true)
        serial.setOutput(out, null)
        XmlPullUtils.copy(FakeParser(), serial, null, dedupe)
        return out.toString("UTF-8")
    }

    private fun occurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }

    /**
     * 脚本化解析器：单个 `<AppBarLayout>` 元素，声明 android 命名空间，
     * 并带一个重复的 `android:elevation` 属性。
     */
    private class FakeParser : XmlPullParser {
        private var mIndex = 0
        private val mEvents = intArrayOf(
            XmlPullParser.START_DOCUMENT, XmlPullParser.START_TAG,
            XmlPullParser.END_TAG, XmlPullParser.END_DOCUMENT,
        )
        private val mAttributeNames = arrayOf("elevation", "elevation", "id")
        private val mAttributeValues = arrayOf("0.0dp", "0.0dp", "@attr/x")

        override fun getEventType(): Int = mEvents[mIndex]

        override fun nextToken(): Int {
            if (mIndex < mEvents.size - 1) {
                mIndex++
            }
            return getEventType()
        }

        override fun next(): Int = nextToken()

        override fun getDepth(): Int =
            if (getEventType() == XmlPullParser.START_TAG || getEventType() == XmlPullParser.END_TAG) 1 else 0

        override fun getName(): String = "AppBarLayout"

        override fun getNamespace(): String? = null

        override fun getPrefix(): String? = null

        override fun getFeature(feature: String): Boolean = false

        override fun getInputEncoding(): String = "UTF-8"

        /** 命名空间声明只在 START_TAG（depth=1）上出现一次。 */
        override fun getNamespaceCount(depth: Int): Int = if (depth == 1) 1 else 0

        override fun getNamespacePrefix(index: Int): String = "android"

        override fun getNamespaceUri(index: Int): String = "http://schemas.android.com/apk/res/android"

        override fun getAttributeCount(): Int = if (getEventType() == XmlPullParser.START_TAG) 3 else -1

        override fun getAttributeNamespace(index: Int): String = "http://schemas.android.com/apk/res/android"

        override fun getAttributePrefix(index: Int): String = "android"

        override fun getAttributeName(index: Int): String = mAttributeNames[index]

        override fun getAttributeValue(index: Int): String = mAttributeValues[index]

        override fun getAttributeValue(namespace: String?, name: String?): String? = null

        override fun getAttributeType(index: Int): String? = null

        override fun isAttributeDefault(index: Int): Boolean = false

        override fun getProperty(name: String): Any? = null

        override fun getNamespace(prefix: String?): String? = null

        override fun getPositionDescription(): String = "fake"

        override fun getLineNumber(): Int = -1

        override fun getColumnNumber(): Int = -1

        override fun isWhitespace(): Boolean = false

        override fun getText(): String? = null

        override fun getTextCharacters(holderForStartAndLength: IntArray): CharArray =
            CharArray(0)

        override fun isEmptyElementTag(): Boolean = false

        override fun setFeature(feature: String, state: Boolean): Unit =
            throw UnsupportedOperationException()

        override fun setProperty(name: String, value: Any?): Unit =
            throw UnsupportedOperationException()

        override fun setInput(reader: Reader): Unit = throw UnsupportedOperationException()

        override fun setInput(inputStream: InputStream, inputEncoding: String?): Unit =
            throw UnsupportedOperationException()

        override fun defineEntityReplacementText(entityName: String, replacementText: String): Unit =
            throw UnsupportedOperationException()

        override fun require(type: Int, namespace: String?, name: String?): Unit =
            throw UnsupportedOperationException()

        override fun nextText(): String = throw UnsupportedOperationException()

        override fun nextTag(): Int = throw UnsupportedOperationException()
    }
}
