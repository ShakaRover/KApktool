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

/**
 * 回归测试（issue1605）：验证 assets 解码开关生效——
 * DecodeAssets 为 NONE 时 assets 内文件不解码，为 FULL 时正常解码。
 */
class SkipAssetTest : BaseTest() {

    /** 关闭 assets 解码后，确认 kotlin_builtins 相关 assets 文件未被写出。 */
    @Test
    @Throws(Exception::class)
    fun checkIfEnablingSkipAssetWorks() {
        sConfig!!.setDecodeAssets(Config.DecodeAssets.NONE)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.none")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertFalse(File(testDir, "assets/kotlin.kotlin_builtins").isFile())
        assertFalse(File(testDir, "assets/ranges/ranges.kotlin_builtins").isFile())
    }

    /** 对照组：开启完整 assets 解码后，同样的文件应当存在。 */
    @Test
    @Throws(Exception::class)
    fun checkControl() {
        sConfig!!.setDecodeAssets(Config.DecodeAssets.FULL)

        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.full")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "assets/kotlin.kotlin_builtins").isFile())
        assertTrue(File(testDir, "assets/ranges/ranges.kotlin_builtins").isFile())
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue1605.apk"

        /** 类级初始化：把资源目录 issue1605 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(SkipAssetTest::class.java, "issue1605", sTmpDir!!)
        }
    }
}
