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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.w3c.dom.Document
import java.io.File

/**
 * 引用解析策略测试（issue2836）：分别以 DecodeResolve 的
 * DEFAULT、GREEDY、LAZY 三种模式解码同一 APK，
 * 通过统计 attrs/colors/public 中各类资源元素数量验证解析范围差异。
 */
class DecodeResolveTest : BaseTest() {

    /** 验证 DEFAULT 模式解析出的资源元素数量（enum 4 / color 8 / public 22）。 */
    @Test
    @Throws(Exception::class)
    fun decodeResolveDefaultTest() {
        sConfig!!.setDecodeResolve(Config.DecodeResolve.DEFAULT)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.default")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/values/strings.xml").isFile)

        val attrDocument = XmlUtils.loadDocument(File(testDir, "res/values/attrs.xml"))
        assertEquals(4, attrDocument.getElementsByTagName("enum").length)

        val colorDocument = XmlUtils.loadDocument(File(testDir, "res/values/colors.xml"))
        assertEquals(8, colorDocument.getElementsByTagName("color").length)

        val publicDocument = XmlUtils.loadDocument(File(testDir, "res/values/public.xml"))
        assertEquals(22, publicDocument.getElementsByTagName("public").length)
    }

    /** 验证 GREEDY 模式会额外贪婪补齐引用（enum 4 / color 9 / public 23）。 */
    @Test
    @Throws(Exception::class)
    fun decodeResolveGreedyTest() {
        sConfig!!.setDecodeResolve(Config.DecodeResolve.GREEDY)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.greedy")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/values/strings.xml").isFile)

        val attrXml = File(testDir, "res/values/attrs.xml")
        val attrDocument = XmlUtils.loadDocument(attrXml)
        assertEquals(4, attrDocument.getElementsByTagName("enum").length)

        val colorXml = File(testDir, "res/values/colors.xml")
        val colorDocument = XmlUtils.loadDocument(colorXml)
        assertEquals(9, colorDocument.getElementsByTagName("color").length)

        val publicXml = File(testDir, "res/values/public.xml")
        val publicDocument = XmlUtils.loadDocument(publicXml)
        assertEquals(23, publicDocument.getElementsByTagName("public").length)
    }

    /** 验证 LAZY 模式仅按需懒解析，元素数量最少（enum 3 / color 8 / public 21）。 */
    @Test
    @Throws(Exception::class)
    fun decodeResolveLazyTest() {
        sConfig!!.setDecodeResolve(Config.DecodeResolve.LAZY)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.lazy")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/values/strings.xml").isFile)

        val attrXml = File(testDir, "res/values/attrs.xml")
        val attrDocument = XmlUtils.loadDocument(attrXml)
        assertEquals(3, attrDocument.getElementsByTagName("enum").length)

        val colorXml = File(testDir, "res/values/colors.xml")
        val colorDocument = XmlUtils.loadDocument(colorXml)
        assertEquals(8, colorDocument.getElementsByTagName("color").length)

        val publicXml = File(testDir, "res/values/public.xml")
        val publicDocument = XmlUtils.loadDocument(publicXml)
        assertEquals(21, publicDocument.getElementsByTagName("public").length)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue2836.apk"

        /** 类级初始化：把测试资源目录 issue2836 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(DecodeResolveTest::class.java, "issue2836", sTmpDir!!)
        }
    }
}
