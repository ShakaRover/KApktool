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
 * 回归测试（issue3366）：验证紧凑（compact）资源表结构的 APK
 * 能成功解码，且字符串资源条目数量完整。
 */
class CompactResourceTest : BaseTest() {

    /** 解码紧凑资源 APK，并校验解出的 string 条目数量为 1002。 */
    @Test
    @Throws(Exception::class)
    fun checkIfDecodeSucceeds() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        val doc: Document = XmlUtils.loadDocument(File(testDir, "res/values/strings.xml"))
        val expression = "/resources/string[@name]"
        val nodes: NodeList = XmlUtils.evaluateXPath(doc, expression, NodeList::class.java)!!
        assertEquals(1002, nodes.length)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue3366.apk"

        /** 类级初始化：把测试资源目录 issue3366 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(CompactResourceTest::class.java, "issue3366", BaseTest.sTmpDir!!)
        }
    }
}
