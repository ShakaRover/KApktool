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

import brut.directory.ExtFile
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals

/**
 * 回归测试：验证 APK 中“未知压缩后缀”文件的压缩方式在解码→构建往返后保持一致，
 * 包括 .pkm 保持 deflate、双扩展名文件保持 stored、json 保持 deflate、
 * 以及已压缩 png 被重新存为 stored 等场景。
 */
class UnknownCompressionTest : BaseTest() {

    /** .pkm 资源在原始包与重建包中都应保持 deflate 压缩。 */
    @Test
    @Throws(Exception::class)
    fun pkmExtensionDeflatedTest() {
        val fileName = "assets/bin/Data/test.pkm"
        val control = sTestApk.getDirectory().getCompressionLevel(fileName)
        val rebuilt = sNewApk.getDirectory().getCompressionLevel(fileName)

        // Check that control = rebuilt (both deflated)
        // Add extra check for checking not equal to 0, just in case control gets broken
        assertEquals(control, rebuilt)
        assertNotEquals(0, rebuilt)
    }

    /** 双扩展名文件在原始包与重建包中都应保持 stored（压缩级别 0）。 */
    @Test
    @Throws(Exception::class)
    fun doubleExtensionStoredTest() {
        val fileName = "assets/bin/Data/two.extension.file"
        val control = sTestApk.getDirectory().getCompressionLevel(fileName)
        val rebuilt = sNewApk.getDirectory().getCompressionLevel(fileName)

        // Check that control = rebuilt (both stored)
        // Add extra check for checking = 0 to enforce check for stored just in case control breaks
        assertEquals(control, rebuilt)
        assertEquals(0, rebuilt)
    }

    /** json 文件在往返后压缩级别应保持一致，且为 deflate（8）。 */
    @Test
    @Throws(Exception::class)
    fun confirmJsonFileIsDeflatedTest() {
        val fileName = "test.json"
        val control = sTestApk.getDirectory().getCompressionLevel(fileName)
        val rebuilt = sNewApk.getDirectory().getCompressionLevel(fileName)

        assertEquals(control, rebuilt)
        assertEquals(8, rebuilt)
    }

    /** 已压缩的 png 文件往返后压缩方式会变化，重建包中应为 stored（0）。 */
    @Test
    @Throws(Exception::class)
    fun confirmPngFileIsStoredTest() {
        val fileName = "950x150.png"
        val control = sTestApk.getDirectory().getCompressionLevel(fileName)
        val rebuilt = sNewApk.getDirectory().getCompressionLevel(fileName)

        assertNotEquals(control, rebuilt)
        assertEquals(0, rebuilt)
    }

    companion object {
        // 原始测试 APK（解包前）。
        private lateinit var sTestApk: ExtFile

        // 重新构建得到的 APK（位于 dist 目录）。
        private lateinit var sNewApk: ExtFile

        /** 类级初始化：解包、解码并重新构建 unknown_compression.apk。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(
                UnknownCompressionTest::class.java, "unknown_compression", BaseTest.sTmpDir!!
            )

            BaseTest.sConfig!!.frameworkDirectory = BaseTest.sTmpDir!!.absolutePath

            BaseTest.log("Building unknown_compression.apk...")
            sTestApk = ExtFile(BaseTest.sTmpDir!!, "unknown_compression.apk")
            val testDir = ExtFile(sTestApk.toString() + ".out")
            ApkDecoder(sTestApk, BaseTest.sConfig!!).decode(testDir)

            BaseTest.log("Decoding unknown_compression.apk...")
            ApkBuilder(testDir, BaseTest.sConfig!!).build(null)
            sNewApk = ExtFile(testDir, "dist/" + sTestApk.name)
        }

        /** 类级清理：关闭两个 ExtFile 持有的压缩包句柄。 */
        @AfterClass
        @JvmStatic
        @Throws(Exception::class)
        fun afterClass() {
            sTestApk.close()
            sNewApk.close()
        }
    }
}
