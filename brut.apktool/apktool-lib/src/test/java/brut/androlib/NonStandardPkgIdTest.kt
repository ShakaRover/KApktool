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

import brut.androlib.meta.ApkInfo
import brut.androlib.res.ResDecoder
import brut.androlib.res.table.ResId
import brut.androlib.res.table.ResTable
import brut.directory.ExtFile
import brut.util.OS
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 非标准包 ID（pkgId8，主包 ID 为 0x80 而非常见值）解码测试。
 *
 * 验证资源表能正确识别自定义包 ID，并保证 values/strings.xml
 * 与 AndroidManifest.xml 的解码结果与原始工程一致。
 */
class NonStandardPkgIdTest : BaseTest() {

    /** 验证解码输出目录已生成。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
    }

    /** 对比 values/strings.xml 与对照工程一致。 */
    @Test
    @Throws(Exception::class)
    fun valuesStringsTest() {
        compareValuesFiles("values/strings.xml")
    }

    /** 对比 AndroidManifest.xml 结构一致。 */
    @Test
    @Throws(Exception::class)
    fun confirmManifestStructureTest() {
        compareXmlFiles("AndroidManifest.xml")
    }

    /** 验证主包及若干资源引用解析后的包 ID 均为 0x80。 */
    @Test
    @Throws(Exception::class)
    fun confirmResourcesAreFromPkgId8() {
        assertEquals(0x80, sTable!!.mainPackage!!.getId())

        assertEquals(0x80, sTable!!.resolve(ResId.of(0x80020000.toInt())).`package`.getId())
        assertEquals(0x80, sTable!!.resolve(ResId.of(0x80020001.toInt())).`package`.getId())
        assertEquals(0x80, sTable!!.resolve(ResId.of(0x80030000.toInt())).`package`.getId())
    }

    companion object {
        // 解码流程使用的测试 APK（需保持打开的目录视图）。
        private var sTestApk: ExtFile? = null

        // 解码过程中得到的资源表。
        private var sTable: ResTable? = null

        /**
         * 类级初始化：解包 pkgid8 资源并构建 pkgid8.apk，
         * 随后仅执行资源与 manifest 解码以取得资源表。
         */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            sTestOrigDir = File(sTmpDir!!, "pkgid8-orig")
            sTestNewDir = File(sTmpDir!!, "pkgid8-new")

            log("Unpacking pkgid8...")
            copyResourceDir(
                NonStandardPkgIdTest::class.java, "pkgid8", sTestOrigDir!!
            )

            sConfig!!.isVerbose = true

            log("Building pkgid8.apk...")
            sTestApk = ExtFile(sTmpDir!!, "pkgid8.apk")
            ApkBuilder(sTestOrigDir!!, sConfig!!).build(sTestApk)

            log("Decoding pkgid8.apk...")
            val testInfo = ApkInfo()
            testInfo.apkFile = sTestApk
            val resDecoder = ResDecoder(testInfo, sConfig!!)
            OS.mkdir(sTestNewDir!!)
            resDecoder.decodeResources(sTestNewDir!!)
            resDecoder.decodeManifest(sTestNewDir!!)
            sTable = resDecoder.table
        }

        /** 类级清理：关闭测试 APK 的目录视图句柄。 */
        @AfterClass
        @JvmStatic
        @Throws(Exception::class)
        fun afterClass() {
            sTestApk!!.close()
        }
    }
}
