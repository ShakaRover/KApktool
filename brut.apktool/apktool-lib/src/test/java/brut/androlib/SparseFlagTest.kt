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
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * 回归测试（issue-3298）：验证资源表稀疏标志（sparse entries）的识别，
 * 即稀疏 APK 解码后标志为真、非稀疏 APK 为假，且两者都能重新构建。
 */
class SparseFlagTest : BaseTest() {

    /** 解码 sparse.apk，期望 apkInfo 中资源信息标记为稀疏条目，并能重新构建。 */
    @Test
    @Throws(Exception::class)
    fun decodeWithExpectationOfSparseEntries() {
        sConfig!!.frameworkTag = "issue-3298"

        log("Decoding sparse.apk...")
        val testApk = File(sTmpDir!!, "sparse.apk")
        val testDir = File("$testApk.out")
        val apkDecoder = ApkDecoder(testApk, sConfig!!)
        apkDecoder.decode(testDir)
        val apkInfo: ApkInfo = apkDecoder.apkInfo!!

        assertTrue("Expecting sparse entries", apkInfo.resourcesInfo.isSparseEntries())

        log("Building sparse.apk...")
        ApkBuilder(testDir, sConfig!!).build(null)
    }

    /** 解码 not-sparse.apk，期望资源信息未标记稀疏条目，并能重新构建。 */
    @Test
    @Throws(Exception::class)
    fun decodeWithExpectationOfNoSparseEntries() {
        sConfig!!.frameworkTag = "issue-3298"

        log("Decoding not-sparse.apk...")
        val testApk = File(sTmpDir!!, "not-sparse.apk")
        val testDir = File("$testApk.out")
        val apkDecoder = ApkDecoder(testApk, sConfig!!)
        apkDecoder.decode(testDir)
        val apkInfo: ApkInfo = apkDecoder.apkInfo!!

        assertFalse("Expecting not-sparse entries", apkInfo.resourcesInfo.isSparseEntries())

        log("Building not-sparse.apk...")
        ApkBuilder(testDir, sConfig!!).build(null)
    }

    companion object {
        /** 类级初始化：把 sparse 与 not-sparse 两个测试 APK 复制到临时目录。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.log("Unpacking sparse.apk && not-sparse.apk...")
            BaseTest.copyResourceDir(SparseFlagTest::class.java, "sparse", BaseTest.sTmpDir!!)
        }
    }
}
