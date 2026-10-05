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
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 回归测试（issue1234）：验证 manifest 中 versionName 引用的是资源 ID（引用值）时，
 * 解码会把它解析成真正的字面量字符串写入 apktool.yml。
 */
class ReferenceVersionCodeTest : BaseTest() {

    /** 解码样本 APK，确认 versionName 由资源引用还原为字面量 v1.0.0。 */
    @Test
    @Throws(Exception::class)
    fun referenceBecomesLiteralTest() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        val testInfo = ApkInfo.load(File(testDir, "apktool.yml"))
        assertEquals("v1.0.0", testInfo.versionInfo.versionName)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue1234.apk"

        /** 类级初始化：把资源目录 issue1234 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(ReferenceVersionCodeTest::class.java, "issue1234", sTmpDir!!)
        }
    }
}
