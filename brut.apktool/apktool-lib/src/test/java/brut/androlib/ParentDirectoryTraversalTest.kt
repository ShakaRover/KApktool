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
 * 回归测试（issue1498）：验证解码含有父目录穿越（".." 相对路径）恶意文件名的 APK 时，
 * 输出不会逃逸到目标目录之外，整个解码流程可以正常完成。
 */
class ParentDirectoryTraversalTest : BaseTest() {

    /** 关闭资源解码后解码 APK，确认目录穿越条目被安全处理且不抛出异常。 */
    @Test
    @Throws(Exception::class)
    fun checkIfDrawableFileDecodesProperly() {
        sConfig!!.setDecodeResources(Config.DecodeResources.NONE)

        val testApk = File(sTmpDir!!, apk)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val apk = "issue1498.apk"

        /** 类级初始化：把资源目录 issue1498 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(ParentDirectoryTraversalTest::class.java, "issue1498", sTmpDir!!)
        }
    }
}
