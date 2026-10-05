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
 * 动态 dex 加载测试：对含多 dex 的 APK 分别以
 * ONLY_MAIN_CLASSES 与 FULL 两种源码解码模式执行解码→构建往返，
 * 验证两种模式下工程都能被正常处理而不抛错。
 */
class DynamicDexTest : BaseTest() {

    /** 验证仅解码主类源码的模式下，解码再构建（输出 null 即原地构建）全流程可用。 */
    @Test
    @Throws(Exception::class)
    fun decodeOnlyMainClassesTest() {
        sConfig!!.setDecodeSources(Config.DecodeSources.ONLY_MAIN_CLASSES)

        log("Decoding " + TEST_APK + "...")
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.main")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        log("Building " + TEST_APK + "...")
        ApkBuilder(testDir, sConfig!!).build(null)
    }

    /** 验证解码全部源码的模式下，解码再构建全流程可用。 */
    @Test
    @Throws(Exception::class)
    fun decodeAllSourcesTest() {
        sConfig!!.setDecodeSources(Config.DecodeSources.FULL)

        log("Decoding " + TEST_APK + "...")
        val testApk = File(sTmpDir!!, TEST_APK)
        val testDir = File("$testApk.out.full")
        ApkDecoder(testApk, sConfig!!).decode(testDir)

        log("Building " + TEST_APK + "...")
        ApkBuilder(testDir, sConfig!!).build(null)
    }

    companion object {
        // 测试用 APK 文件名。
        private const val TEST_APK = "dynamic_dex.apk"

        /** 类级初始化：把测试资源目录 dynamic_dex 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            log("Unpacking " + TEST_APK + "...")
            copyResourceDir(DynamicDexTest::class.java, "dynamic_dex", sTmpDir!!)
        }
    }
}
