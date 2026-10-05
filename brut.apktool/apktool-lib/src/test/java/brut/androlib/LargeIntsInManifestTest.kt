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

/**
 * 回归测试（issue767）：验证 AndroidManifest.xml 中大整数值
 * （如超大 versionCode）在解码→构建→再解码的往返过程中保持一致。
 */
class LargeIntsInManifestTest : BaseTest() {

    /** 验证往返解码后 manifest 内容一致。 */
    @Test
    fun checkIfLargeIntsAreHandledTest() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        ApkBuilder(testDir, sConfig!!).build(null)

        val newApk = File(testDir, "dist/" + testApk.name)
        val newDir = File("$testApk.out.new")
        ApkDecoder(newApk, sConfig!!).decode(newDir)

        BaseTest.compareXmlFiles(testDir, newDir, "AndroidManifest.xml")
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue767.apk"

        /** 类级初始化：把测试资源目录 issue767 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.copyResourceDir(LargeIntsInManifestTest::class.java, "issue767", BaseTest.sTmpDir!!)
        }
    }
}
