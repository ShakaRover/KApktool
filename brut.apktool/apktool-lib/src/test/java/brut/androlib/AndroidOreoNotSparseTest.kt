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
import org.junit.Assert.assertTrue

/**
 * 回归测试（issue1594）：验证非稀疏（not sparse）资源的 Android Oreo APK
 * 能够完成解码后再构建的往返流程。
 */
class AndroidOreoNotSparseTest : BaseTest() {

    /** 验证解码目录与原始对照目录均存在，说明往返流程未失败。 */
    @Test
    fun buildAndDecodeTest() {
        assertTrue(sTestNewDir!!.isDirectory)
        assertTrue(sTestOrigDir!!.isDirectory)
    }

    companion object {
        /** 类级初始化：解包、解码并重新构建 not_sparse.apk。 */
        @BeforeClass
        @JvmStatic
        @Throws(Exception::class)
        fun beforeClass() {
            BaseTest.sTestOrigDir = File(BaseTest.sTmpDir!!, "issue1594-orig")
            BaseTest.sTestNewDir = File(BaseTest.sTmpDir!!, "issue1594-new")

            BaseTest.log("Unpacking not_sparse.apk...")
            BaseTest.copyResourceDir(
                AndroidOreoNotSparseTest::class.java, "issue1594", BaseTest.sTestOrigDir!!
            )

            BaseTest.log("Decoding not_sparse.apk...")
            val testApk = File(BaseTest.sTestOrigDir!!, "not_sparse.apk")
            ApkDecoder(testApk, BaseTest.sConfig!!).decode(BaseTest.sTestNewDir!!)

            BaseTest.log("Building not_sparse.apk...")
            ApkBuilder(BaseTest.sTestNewDir!!, BaseTest.sConfig!!).build(testApk)
        }
    }
}
