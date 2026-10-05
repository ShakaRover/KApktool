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
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 回归测试（issue1264）：验证 AndroidManifest.xml 缺少 versionName 属性时，
 * 解码仍能正常完成并且 apktool.yml 中 versionName 解析为空值。
 */
class MissingVersionManifestTest : BaseTest() {

    /** 解码缺少版本信息的 APK，确认 versionName 为 null 而非解码失败。 */
    @Test
    @Throws(Exception::class)
    fun missingVersionParsesCorrectlyTest() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        val testInfo = ApkInfo.load(File(testDir, "apktool.yml"))
        assertNull(testInfo.versionInfo.versionName)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue1264.apk"

        /** 类级初始化：把资源目录 issue1264 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(MissingVersionManifestTest::class.java, "issue1264", sTmpDir!!)
        }
    }
}
