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
import brut.util.TextUtils
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * 回归测试（issue1244）：验证带多重扩展名的未知文件（split APK 中的 .assets.split0）
 * 能被正确识别并写入 apktool.yml 的不压缩列表，而不会被误当作普通扩展名处理。
 */
class DoubleExtensionUnknownFileTest : BaseTest() {

    /** 解码样本 APK 后遍历 doNotCompress，凡含多个点号的路径必须等于已知的 split 文件。 */
    @Test
    @Throws(Exception::class)
    fun multipleExtensionUnknownFileTest() {
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        val testInfo = ApkInfo.load(File(testDir, "apktool.yml"))
        for (path in testInfo.doNotCompress) {
            if (TextUtils.countMatches(path!!, '.') > 1) {
                assertTrue(path == "assets/bin/Data/sharedassets1.assets.split0")
            }
        }
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "issue1244.apk"

        /** 类级初始化：把资源目录 issue1244 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            copyResourceDir(DoubleExtensionUnknownFileTest::class.java, "issue1244", sTmpDir!!)
        }
    }
}
