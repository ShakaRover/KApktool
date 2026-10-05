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

import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import org.junit.Assert.assertTrue

/**
 * 回归测试（issue1170）：验证经过 AndResGuard 资源混淆的 APK
 * 在解码时能正确还原被压缩后的资源路径。
 */
class AndResGuardTest : BaseTest() {

    /** 默认解码时应把混淆后的资源路径重新映射回正常的 res 目录结构。 */
    @Test
    @Throws(Exception::class)
    fun checkifAndResDecodeRemapsRFolder() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/mipmap-hdpi-v4/a.png").isFile)
    }

    /** 资源解码关闭（NONE）时，混淆路径应保持原样落在 unknown 目录下。 */
    @Test
    @Throws(Exception::class)
    fun checkIfAndResDecodeIgnoresRFolderInRawMode() {
        sConfig!!.setDecodeResources(Config.DecodeResources.NONE)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.raw")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "unknown/r/a/a.png").isFile)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue1170.apk"

        /** 类级初始化：把测试资源目录 issue1170 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(AndResGuardTest::class.java, "issue1170", BaseTest.sTmpDir!!)
        }
    }
}
