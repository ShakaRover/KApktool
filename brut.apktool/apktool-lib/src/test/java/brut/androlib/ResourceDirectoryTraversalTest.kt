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

import brut.androlib.res.table.ResEntrySpec
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 安全回归测试（GHSA-2hqv-2xv4-5h5w）：验证资源表条目名为恶意相对路径（可造成任意写）时，
 * 解码器会把该条目重命名为带 APKTOOL_RENAMED_ 前缀的安全名称。
 */
class ResourceDirectoryTraversalTest : BaseTest() {

    /** 解码样本 APK，确认非法 raw 资源条目被改名后落盘，原危险路径未被写入。 */
    @Test
    @Throws(Exception::class)
    fun checkIfMaliciousRawFileRenamed() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        assertTrue(File(testDir, "res/raw/" + ResEntrySpec.RENAMED_PREFIX + "0x7f040000").exists())
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "GHSA-2hqv-2xv4-5h5w.apk"

        /** 类级初始化：把资源目录 arbitrary_write 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(ResourceDirectoryTraversalTest::class.java, "arbitrary_write", sTmpDir!!)
        }
    }
}
