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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 回归测试：验证解码含有 0 字节空 9-patch 图片的 APK 不会崩溃，
 * 空文件原样落盘且大小仍为 0。
 */
class Empty9PatchTest : BaseTest() {

    /** 解码样本 APK，确认空的 9-patch 文件存在且长度为 0 字节。 */
    @Test
    @Throws(Exception::class)
    fun decodeWithEmpty9PatchFile() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        val aPng = File(testDir, "res/drawable-xhdpi/empty.9.png")
        assertTrue(aPng.isFile())
        assertEquals(0L, aPng.length())
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "empty_9patch.apk"

        /** 类级初始化：把资源目录 empty_9patch 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(Empty9PatchTest::class.java, "empty_9patch", sTmpDir!!)
        }
    }
}
