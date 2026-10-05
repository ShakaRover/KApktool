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
 * 回归测试（issue1589）：验证 APK 中存在越界（写在目标目录之外）的 zip 条目时，
 * 解码器会跳过该非法条目，仅生成合法的输出内容。
 */
class OutsideOfDirectoryEntryTest : BaseTest() {

    /** 校验解码结果为目录，且非法条目对应的 assets 目录未被创建。 */
    @Test
    fun skippedDecodingOfInvalidFileTest() {
        assertTrue(sTestNewDir!!.isDirectory())
        assertFalse(File(sTestNewDir!!, "assets").isDirectory())
    }

    companion object {
        /** 类级初始化：复制资源目录并完成解码，记录输出目录供测试断言使用。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(OutsideOfDirectoryEntryTest::class.java, "issue1589", sTmpDir!!)

            val testApk = File(sTmpDir!!, "issue1589.apk")
            val testDir = File("$testApk.out")
            ApkDecoder(testApk, sConfig!!).decode(testDir)
            sTestNewDir = testDir
        }
    }
}
