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
 * 回归测试：验证包含受保护（未知/加固）资源块的 APK
 * 解码过程不会崩溃，并仍能产出正常的字符串资源文件。
 */
class ProtectedChunksTest : BaseTest() {

    /** 解码受保护资源块 APK，确认 res/values/strings.xml 已生成。 */
    @Test
    @Throws(Exception::class)
    fun checkIfDecodeWorksWithoutCrash() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/values/strings.xml").isFile)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "protected_chunks.apk"

        /** 类级初始化：把测试资源目录 protected_chunks 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(
                ProtectedChunksTest::class.java, "protected_chunks", BaseTest.sTmpDir!!
            )
        }
    }
}
