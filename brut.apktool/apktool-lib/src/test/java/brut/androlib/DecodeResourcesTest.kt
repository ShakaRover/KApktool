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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Arrays

/**
 * 资源解码范围测试（issue1680）：分别以 DecodeResources 的
 * NONE、FULL、ONLY_MANIFEST 三种模式解码同一 APK，
 * 验证 AndroidManifest.xml 是否转为 XML 文本、resources.arsc 是否保留。
 */
class DecodeResourcesTest : BaseTest() {

    /** 验证 NONE 模式：manifest 保持二进制（不是 XML），且 resources.arsc 存在。 */
    @Test
    @Throws(Exception::class)
    fun decodeResourcesNoneTest() {
        sConfig!!.setDecodeResources(Config.DecodeResources.NONE)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.none")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        // 断言 manifest 不是 XML
        assertFalse(Arrays.equals(XML_HEADER, readHeaderOfFile(File(testDir, "AndroidManifest.xml"), 6)))

        // 断言 resources.arsc 存在
        assertTrue(File(testDir, "resources.arsc").isFile)
    }

    /** 验证 FULL 模式：manifest 解码为 XML 文本，且 resources.arsc 被完全解析掉。 */
    @Test
    @Throws(Exception::class)
    fun decodeResourcesFullTest() {
        sConfig!!.setDecodeResources(Config.DecodeResources.FULL)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.full")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        // 断言 manifest 是 XML
        assertTrue(Arrays.equals(XML_HEADER, readHeaderOfFile(File(testDir, "AndroidManifest.xml"), 6)))

        // 断言 resources.arsc 不存在
        assertFalse(File(testDir, "resources.arsc").isFile)
    }

    /** 验证 ONLY_MANIFEST 模式：仅 manifest 解码为 XML，resources.arsc 原样保留。 */
    @Test
    @Throws(Exception::class)
    fun decodeResourcesOnlyManifestTest() {
        sConfig!!.setDecodeResources(Config.DecodeResources.ONLY_MANIFEST)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.manifest")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        // 断言 manifest 是 XML
        assertTrue(Arrays.equals(XML_HEADER, readHeaderOfFile(File(testDir, "AndroidManifest.xml"), 6)))

        // 断言 resources.arsc 存在
        assertTrue(File(testDir, "resources.arsc").isFile)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue1680.apk"

        // XML 文本文件头部特征字节（即 "<?xml " 前缀）。
        private val XML_HEADER = byteArrayOf(
            0x3C, // <
            0x3F, // ?
            0x78, // x
            0x6D, // m
            0x6C, // l
            0x20, // (空格)
        )

        /** 类级初始化：把测试资源目录 issue1680 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(DecodeResourcesTest::class.java, "issue1680", sTmpDir!!)
        }
    }
}
