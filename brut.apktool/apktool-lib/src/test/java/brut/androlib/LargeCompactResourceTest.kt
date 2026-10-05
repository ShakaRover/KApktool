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
package brut.androlib

import brut.xml.XmlUtils
import org.junit.BeforeClass
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import java.io.File
import org.junit.Assert.assertEquals

/**
 * 回归测试（issue3705）：验证超大紧凑资源表 APK 的解码流程，
 * 确认按名称片段过滤的字符串资源条目数为 0（未误产生多余条目）。
 */
class LargeCompactResourceTest : BaseTest() {

    /** 解码大型紧凑资源 APK，并校验名称含 APKTOOL 的 string 条目数为 0。 */
    @Test
    @Throws(Exception::class)
    fun checkIfDecodeSucceeds() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        val doc: Document = XmlUtils.loadDocument(File(testDir, "res/values/strings.xml"))
        val expression = "/resources/string[contains(@name, 'APKTOOL')]"
        val nodes: NodeList = XmlUtils.evaluateXPath(doc, expression, NodeList::class.java)!!
        assertEquals(0, nodes.length)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue3705.apk"

        /** 类级初始化：把测试资源目录 issue3705 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(CompactResourceTest::class.java, "issue3705", BaseTest.sTmpDir!!)
        }
    }
}
