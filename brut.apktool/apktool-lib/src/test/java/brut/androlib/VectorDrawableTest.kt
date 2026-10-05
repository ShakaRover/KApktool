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

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 回归测试（issue1456）：验证 vector drawable（矢量图 XML 资源）能被正确解码到
 * res/drawable 目录下并生成对应的 xml 文件。
 */
class VectorDrawableTest : BaseTest() {

    /** 解码样本 APK，确认两个 vector drawable 文件均已生成。 */
    @Test
    @Throws(Exception::class)
    fun checkIfDrawableFileDecodesProperly() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/drawable/ic_arrow_drop_down_black_24dp.xml").isFile())
        assertTrue(File(testDir, "res/drawable/ic_android_black_24dp.xml").isFile())
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue1456.apk"

        /** 类级初始化：把资源目录 issue1456 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(VectorDrawableTest::class.java, "issue1456", sTmpDir!!)
        }
    }
}
