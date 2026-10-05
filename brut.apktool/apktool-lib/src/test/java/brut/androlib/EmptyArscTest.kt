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
 * 回归测试（issue2701）：验证资源文件 ARSC 为空文件的 APK
 * 解码时能正常生成 public.xml 与 AndroidManifest.xml。
 */
class EmptyArscTest : BaseTest() {

    /** 解码空 ARSC 的 APK，校验关键输出文件存在。 */
    @Test
    @Throws(Exception::class)
    fun decodeWithEmptyArscFile() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/values/public.xml").isFile)
        assertTrue(File(testDir, "AndroidManifest.xml").isFile)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue2701.apk"

        /** 类级初始化：把测试资源目录 issue2701 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(EmptyArscTest::class.java, "issue2701", BaseTest.sTmpDir!!)
        }
    }
}
