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
import org.w3c.dom.Node
import java.io.File
import org.junit.Assert.assertNotNull

/**
 * 回归测试（issue3533）：验证资源 XML 中属性命名空间被剥离（stripped）的 APK
 * 解码后，自定义命名空间属性仍能按 XPath 正确匹配。
 */
class StrippedNamespaceTest : BaseTest() {

    /** 解码后被剥离命名空间的 drawable 属性可通过带前缀的 XPath 查到。 */
    @Test
    @Throws(Exception::class)
    fun checkAssignedNamespaceTest() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        val doc: Document = XmlUtils.loadDocument(File(testDir, "res/drawable/trap.xml"), true)
        val expression = "/selector/item/@test:is_obfuscated"
        val node: Node? = XmlUtils.evaluateXPath(doc, expression, Node::class.java)
        assertNotNull(node)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue3533.apk"

        /** 类级初始化：把测试资源目录 issue3533 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(StrippedNamespaceTest::class.java, "issue3533", BaseTest.sTmpDir!!)
        }
    }
}
